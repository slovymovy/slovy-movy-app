package com.slovy.slovymovyapp.builder

import java.io.File

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
        procDir.listFiles().filter { it.isFile && it.extension.equals("json", ignoreCase = true) }.forEach { pFile ->
            val rawFile = File(rawDir, pFile.name)
            if (!rawFile.exists()) {
                error("Raw DB file not found for language $lang: ${rawFile.absolutePath}")
            }
            builder.ingest(pFile, rawFile)
            words++
            if (words % 100 == 0) println("Ingested $words words to $lang")
        }

        println("lang: $lang; ingested words: $words")
    }
}

private fun printUsageAndExit(): Nothing {
    val msg = buildString {
        appendLine("Usage: DbPrepTool --db-extract <path> --processed <path> --out <path> --freq <path>")
        appendLine("  db_extract folder layout: <root>/<lang>/*.json")
        appendLine("  processed folder layout:  <root>/<lang>/*.json")
        appendLine("  freq folder layout:       <root>/<lang>_kaikki_words.txt")
    }
    throw IllegalArgumentException(msg)
}

private data class Params(
    val dbExtract: String,
    val processed: String,
    val out: String,
    val frequencyDir: String
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
    return Params(db, pr, out, freq)
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
