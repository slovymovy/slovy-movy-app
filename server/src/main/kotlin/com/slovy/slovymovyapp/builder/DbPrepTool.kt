package com.slovy.slovymovyapp.builder

import com.slovy.slovymovyapp.ingestion.JsonIngestionBuilder
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
//     -d "\\wsl.localhost\Ubuntu-24.04\home\nkey\kaikki-parser\output\db-extract" -p "C:\Dev\kaikki-parser\words" -o "C:\Dev\slovy-movy-app\.db-files" -f "\\wsl.localhost\Ubuntu-24.04\home\nkey\wordfreq-extract\output"
// For test files:
//     -t -d "\\wsl.localhost\Ubuntu-24.04\home\nkey\kaikki-parser\output\db-extract" -p "C:\Dev\kaikki-parser\words" -o "C:\Dev\slovy-movy-app\.test-db-files" -f "\\wsl.localhost\Ubuntu-24.04\home\nkey\wordfreq-extract\output"
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
        println("[TEST MODE] Enabled. Will ingest at most 50 words per language in a deterministic order (by hash).")
    }

    // languages are subdirectories inside processed_root
    val languages = processedRoot.listFiles()?.filter { it.isDirectory }?.map { it.name }?.sorted().orEmpty()
    if (languages.isEmpty()) {
        error("No language folders in processed path: ${processedRoot.absolutePath}")
    }


    languages.forEach { lang ->
        var words = 0

        // Load frequency map for this language
        val frequencyFile = File(frequencyDir, "${lang}_kaikki_words.txt")
        if (!frequencyFile.exists()) {
            error("Frequency file not found for language $lang: ${frequencyFile.absolutePath}")
        }
        println("Loading frequency data for $lang from ${frequencyFile.absolutePath}")
        val frequencyMap = loadFrequencyMap(frequencyFile)
        println("Loaded ${frequencyMap.size} frequency entries for $lang")

        val builder = JsonIngestionBuilder(serverDbManager, frequencyMap)

        val procDir = File(processedRoot, lang)
        val rawDir = File(dbExtractRoot, lang)
        if (!rawDir.exists()) {
            error("Raw DB folder not found for language $lang: ${rawDir.absolutePath}")
        }
        procDir.listFiles()
            .orEmpty()
            .filter { it.isFile && it.extension.equals("json", ignoreCase = true) }
            .sortedBy { sha256HexLower(it.name) }
            .let { files -> if (params.testMode) files.take(50) else files }
            .forEach { pFile ->
                val rawFile = File(rawDir, pFile.name)
                if (!rawFile.exists()) {
                    error("Raw DB file not found for language $lang: ${rawFile.absolutePath}")
                }
                builder.ingest(pFile.readText(), rawFile.readText())
                words++
                if (words % 100 == 0) println("Ingested $words words to $lang")
            }

        println("lang: $lang; ingested words: $words")
    }
}

private fun printUsageAndExit(): Nothing {
    val msg = buildString {
        appendLine("Usage: DbPrepTool --db-extract <path> --processed <path> --out <path> --freq <path> [--test]")
        appendLine("  db_extract folder layout: <root>/<lang>/*.json")
        appendLine("  processed folder layout:  <root>/<lang>/*.json")
        appendLine("  freq folder layout:       <root>/<lang>_kaikki_words.txt")
        appendLine("  --test (-t): enable test mode to ingest only 50 words per language (deterministic by file hash)")
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
