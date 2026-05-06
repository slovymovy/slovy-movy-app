package com.slovy.slovymovyapp.builder

import com.slovy.slovymovyapp.ingestion.JsonIngestionBuilder
import com.slovy.slovymovyapp.translation.TranslationDatabase
import java.io.File
import java.security.MessageDigest

// Small CLI utility to prepare SQLite DB files from a folder with
// - db_extract/<lang>/*.json (raw wiktextract per-word files)
// - processed_json_files/<lang>/*.json (processed per-word files)
// - frequency files: <lang>_kaikki_words.txt
//
// Usage:
//   Run with args:
//     --db-extract <path> --processed <path> --out <path> --freq <path>
//  Example:
//     -d "C:\Dev\words\db-extract" -p "C:\Dev\words\words" -o "C:\Dev\slovy-movy-app\.db-files" -f "C:\Dev\words\freqs"
// For test files:
//     -t -d "C:\Dev\words\db-extract" -p "C:\Dev\words\words" -o "C:\Dev\slovy-movy-app\.test-db-files" -f "C:\Dev\words\freqs"
//
// It matches files by name within the same language subfolder and ingests
// pairs using JsonIngestionBuilder into output DB files under --out.
fun main(args: Array<String>) {
    if (args.isEmpty()) {
        printUsageAndExit()
    }
    val params = parseArgs(args.toList())
    val dbExtractRoot = File(params.dbExtract)
    val processedRoot = File(params.processed)
    val outRoot = File(params.out)

    require(dbExtractRoot.exists() && dbExtractRoot.isDirectory) { "db_extract path not found or not a directory: ${params.dbExtract}" }
    require(processedRoot.exists() && processedRoot.isDirectory) { "processed path not found or not a directory: ${params.processed}" }
    if (!outRoot.exists()) outRoot.mkdirs()

    val serverDbManager = ServerDbManager(outRoot)

    // Load frequency directory from params or use default
    val frequencyDir = File(params.frequencyDir)
    require(frequencyDir.exists() && frequencyDir.isDirectory) { "Frequency directory not found: ${params.frequencyDir}" }

    if (params.testMode) {
        println("[TEST MODE] Enabled. Will ingest at most 500 words per language in a deterministic order (by hash).")
    }

    // languages are subdirectories inside processed_root
    val processedLanguages = processedRoot.listFiles()?.filter { it.isDirectory }?.map { it.name }.orEmpty()
    val rawLanguages = dbExtractRoot.listFiles()?.filter { it.isDirectory }?.map { it.name }.orEmpty()
    val languages = (processedLanguages + rawLanguages).toSet().sorted()
    if (languages.isEmpty()) {
        error(
            "No language folders found. processed=${processedRoot.absolutePath}, db_extract=${dbExtractRoot.absolutePath}"
        )
    }

    languages.forEach { lang ->
        var words = 0
        var processedWords = 0
        var rawOnlyWords = 0

        val dictDb = serverDbManager.openDictionaryForBulkInsert(lang)

        // Load frequency map for this language
        val frequencyFile = File(frequencyDir, "${lang}_kaikki_words.txt")
        if (!frequencyFile.exists()) {
            error("Frequency file not found for language $lang: ${frequencyFile.absolutePath}")
        }
        println("Loading frequency data for $lang from ${frequencyFile.absolutePath}")
        val frequencyMap = loadFrequencyMap(frequencyFile)
        println("Loaded ${frequencyMap.size} frequency entries for $lang")

        val translationDatabases = mutableMapOf<String, TranslationDatabase>()
        val translationSourceByTarget = mutableMapOf<String, String>()
        val translationDbProvider: (String, String) -> TranslationDatabase =
            { from, to ->
                translationDatabases.getOrPut(to) {
                    translationSourceByTarget[to] = from
                    serverDbManager.openTranslationForBulkInsert(from, to)
                }
            }
        val builder = JsonIngestionBuilder(
            translationDbProvider = translationDbProvider,
            frequencyMap = frequencyMap,
            warningLogger = { warning -> println("Warning: $warning") }
        )

        val procDir = File(processedRoot, lang)
        val rawDir = File(dbExtractRoot, lang)
        if (!rawDir.exists()) {
            error("Raw DB folder not found for language $lang: ${rawDir.absolutePath}")
        }

        val processedFiles = procDir.listFiles()
            ?.filter { it.isFile && it.extension.equals("json", ignoreCase = true) }
            ?.sortedBy { sha256HexLower(it.name) }
            .orEmpty()
        val processedByName = processedFiles.associateBy { it.name }

        val allRawFiles = rawDir.listFiles()
            ?.filter { it.isFile && it.extension.equals("json", ignoreCase = true) }
            ?.sortedBy { it ->
                if (params.testMode && (it.name.startsWith("simul") || it.name.startsWith("concur"))) {
                    "0"
                } else {
                    sha256HexLower(it.name)
                }

            }
            .orEmpty()

        val rawFiles = if (params.testMode) {
            val curatedRawFiles = selectTestModeCuratedRawFiles(
                lang = lang,
                rawDir = rawDir,
                processedDir = procDir
            )
            val curatedNames = curatedRawFiles.map { it.name }.toSet()
            val remainingQuota = (500 - curatedRawFiles.size).coerceAtLeast(0)
            val additionalRawFiles = allRawFiles
                .asSequence()
                .filterNot { it.name in curatedNames }
                .take(remainingQuota)
                .toList()

            println(
                "[TEST MODE][$lang] selected ${curatedRawFiles.size} curated words + " +
                    "${additionalRawFiles.size} deterministic words"
            )

            curatedRawFiles + additionalRawFiles
        } else {
            allRawFiles
        }

        try {
            rawFiles.chunked(5000).forEach { batch ->
                val inputs = mutableListOf<JsonIngestionBuilder.IngestionInput>()
                batch.forEach { rawFile ->
                    val processedFile = processedByName[rawFile.name]

                    if (processedFile != null) {
                        inputs += JsonIngestionBuilder.IngestionInput(
                            rawJson = rawFile.readText(),
                            processedJson = processedFile.readText()
                        )
                        processedWords++
                    } else {
                        inputs += JsonIngestionBuilder.IngestionInput(
                            rawJson = rawFile.readText(),
                            processedJson = null
                        )
                        rawOnlyWords++
                    }
                    words++
                    if (words % 1000 == 0) println("Ingested $words ($processedWords processed) words to $lang")
                }
                builder.ingestBatch(inputs, dictDb)
            }
        } finally {
            serverDbManager.finishDictionaryBulkInsert(lang)
            translationSourceByTarget.forEach { (to, from) ->
                serverDbManager.finishTranslationBulkInsert(from, to)
            }
        }

        println("lang: $lang; ingested words: $words (processed: $processedWords, raw-only: $rawOnlyWords)")
    }
}

private fun printUsageAndExit(): Nothing {
    val msg = buildString {
        appendLine("Usage: DbPrepTool --db-extract <path> --processed <path> --out <path> --freq <path> [--test]")
        appendLine("  db_extract folder layout: <root>/<lang>/*.json")
        appendLine("  processed folder layout:  <root>/<lang>/*.json")
        appendLine("  freq folder layout:       <root>/<lang>_kaikki_words.txt")
        appendLine("  --test (-t): enable test mode to ingest only 500 words per language (deterministic by file hash)")
    }
    throw IllegalArgumentException(msg)
}

private data class Params(
    val dbExtract: String,
    val processed: String,
    val out: String,
    val frequencyDir: String,
    val testMode: Boolean
)

private fun parseArgs(args: List<String>): Params {
    fun readOpt(name: String): String? {
        val idx = args.indexOf(name)
        if (idx >= 0 && idx + 1 < args.size) return args[idx + 1]
        return null
    }

    val db = readOpt("--db-extract") ?: readOpt("-d")
    val pr = readOpt("--processed") ?: readOpt("-p")
    val out = readOpt("--out") ?: readOpt("-o")
    val freq = readOpt("--freq") ?: readOpt("-f")
    if (db == null || pr == null || out == null || freq == null) printUsageAndExit()
    val test = args.contains("--test") || args.contains("-t")
    return Params(db, pr, out, freq, test)
}

private fun loadFrequencyMap(file: File): Map<String, Double> {
    val map = mutableMapOf<String, Double>()
    file.useLines { lines ->
        lines.drop(1).forEach { line ->
            val parts = line.split('\t')
            if (parts.size >= 2) {
                val word = parts[0]
                val freq = parts[1].toDoubleOrNull()
                if (freq != null) {
                    map[word] = freq
                }
            }
        }
    }
    return map
}

// Deterministic, case-insensitive SHA-256 hex of a file name to use for ordering
private fun sha256HexLower(inputName: String): String {
    val normalized = inputName.lowercase()
    val digest = MessageDigest.getInstance("SHA-256")
    val hash = digest.digest(normalized.toByteArray(Charsets.UTF_8))
    val hexChars = CharArray(hash.size * 2)
    val hexArray = "0123456789abcdef".toCharArray()
    var i = 0
    for (b in hash) {
        val v = b.toInt() and 0xFF
        hexChars[i++] = hexArray[v ushr 4]
        hexChars[i++] = hexArray[v and 0x0F]
    }
    return String(hexChars)
}

private data class PosWordSeed(
    val pos: String,
    val candidates: List<String>
)

private val TEST_MODE_POS_SEEDS_BY_LANG: Map<String, List<PosWordSeed>> = mapOf(
    "en" to listOf(
        PosWordSeed("article", listOf("the", "a", "an")),
        PosWordSeed("noun", listOf("book", "state", "amazon")),
        PosWordSeed("name", listOf("amazon", "john", "anna")),
        PosWordSeed("verb", listOf("test", "double", "state")),
        PosWordSeed("adjective", listOf("double", "last", "state")),
        PosWordSeed("adverb", listOf("well", "there", "in")),
        PosWordSeed("pronoun", listOf("he", "she", "it")),
        PosWordSeed("preposition", listOf("in", "on", "at")),
        PosWordSeed("conjunction", listOf("and", "or", "but")),
        PosWordSeed("interjection", listOf("wow", "oh", "ah")),
        PosWordSeed("determiner", listOf("this", "that", "these")),
        PosWordSeed("numeral", listOf("one", "two", "three"))
    ),
    "nl" to listOf(
        PosWordSeed("article", listOf("de", "het", "een")),
        PosWordSeed("noun", listOf("kwartier", "stempel", "boek")),
        PosWordSeed("name", listOf("amsterdam", "jan", "anna")),
        PosWordSeed("verb", listOf("zeggen", "afspreken", "zijn")),
        PosWordSeed("adjective", listOf("volslagen", "blauw", "laatste")),
        PosWordSeed("adverb", listOf("wel", "niet", "hier")),
        PosWordSeed("pronoun", listOf("ik", "jij", "hij")),
        PosWordSeed("preposition", listOf("in", "op", "voor")),
        PosWordSeed("conjunction", listOf("en", "of", "maar")),
        PosWordSeed("interjection", listOf("hoi", "oh", "ach")),
        PosWordSeed("determiner", listOf("deze", "die", "dit")),
        PosWordSeed("numeral", listOf("één", "twee", "drie"))
    ),
    "pl" to listOf(
        PosWordSeed("article", listOf("ten", "ta", "to")),
        PosWordSeed("noun", listOf("testowanie", "dom", "książka")),
        PosWordSeed("name", listOf("warszawa", "jan", "adam")),
        PosWordSeed("verb", listOf("podawać", "być", "mieć")),
        PosWordSeed("adjective", listOf("dobry", "ostatni", "polski")),
        PosWordSeed("adverb", listOf("dobrze", "szybko", "tu")),
        PosWordSeed("pronoun", listOf("on", "ja", "to")),
        PosWordSeed("preposition", listOf("w", "na", "o")),
        PosWordSeed("conjunction", listOf("i", "albo", "ale")),
        PosWordSeed("interjection", listOf("o", "hej", "ach")),
        PosWordSeed("determiner", listOf("ten", "ta", "to")),
        PosWordSeed("numeral", listOf("jeden", "dwa", "trzy"))
    ),
    "ru" to listOf(
        PosWordSeed("article", listOf("это", "тот", "этот")),
        PosWordSeed("noun", listOf("книга", "дом", "человек")),
        PosWordSeed("name", listOf("иван", "анна", "москва")),
        PosWordSeed("verb", listOf("читать", "сказать", "быть")),
        PosWordSeed("adjective", listOf("красивый", "русский", "последний")),
        PosWordSeed("adverb", listOf("хорошо", "быстро", "там")),
        PosWordSeed("pronoun", listOf("он", "она", "это")),
        PosWordSeed("preposition", listOf("в", "на", "о")),
        PosWordSeed("conjunction", listOf("и", "или", "но")),
        PosWordSeed("interjection", listOf("о", "ах", "эй")),
        PosWordSeed("determiner", listOf("этот", "тот", "это")),
        PosWordSeed("numeral", listOf("один", "два", "три"))
    )
)

private fun fileContainsPos(file: File, pos: String): Boolean {
    if (!file.exists() || !file.isFile) return false
    val marker = "\"pos\": \"$pos\""
    return try {
        file.useLines { lines -> lines.any { marker in it } }
    } catch (_: Throwable) {
        false
    }
}

private fun selectTestModeCuratedRawFiles(
    lang: String,
    rawDir: File,
    processedDir: File
): List<File> {
    val seeds = TEST_MODE_POS_SEEDS_BY_LANG[lang].orEmpty()
    if (seeds.isEmpty()) return emptyList()

    val selected = mutableListOf<File>()
    val selectedNames = mutableSetOf<String>()

    seeds.forEach { seed ->
        val preferredWords = seed.candidates.filter { word ->
            val fileName = "$word.json"
            if (fileName in selectedNames) return@filter false
            val rawFile = File(rawDir, fileName)
            if (!rawFile.exists()) return@filter false
            val processedFile = File(processedDir, fileName)
            fileContainsPos(processedFile, seed.pos) || fileContainsPos(rawFile, seed.pos)
        }

        if (preferredWords.isNotEmpty()) {
            preferredWords.forEach { chosenWord ->
                val fileName = "$chosenWord.json"
                selectedNames += fileName
                selected += File(rawDir, fileName)
            }
            return@forEach
        }

        val fallbackWord = seed.candidates.firstOrNull { word ->
            val fileName = "$word.json"
            fileName !in selectedNames && File(rawDir, fileName).exists()
        }

        if (fallbackWord == null) {
            println("[TEST MODE][$lang] No curated candidate found for POS '${seed.pos}'")
            return@forEach
        }

        println("[TEST MODE][$lang] Using fallback word '$fallbackWord' for POS '${seed.pos}'")
        val fileName = "$fallbackWord.json"
        selectedNames += fileName
        selected += File(rawDir, fileName)
    }

    return selected
}
