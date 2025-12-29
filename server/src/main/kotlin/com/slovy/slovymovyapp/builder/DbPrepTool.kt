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
        val builder = JsonIngestionBuilder(translationDbProvider, frequencyMap)

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

        var rawFiles = rawDir.listFiles()
            ?.filter { it.isFile && it.extension.equals("json", ignoreCase = true) }
            ?.sortedBy { it ->
                if (params.testMode && (it.name.startsWith("simul") || it.name.startsWith("concur"))) {
                    "0"
                } else {
                    sha256HexLower(it.name)
                }

            }
            .orEmpty()
            .let { files -> if (params.testMode) files.take(500) else files }

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
