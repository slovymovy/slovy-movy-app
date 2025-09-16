package com.slovy.slovymovyapp.builder

import java.io.File

// Small CLI utility to prepare SQLite DB files from a folder with
// - db_extract/<lang>/*.json (raw wiktextract per-word files)
// - processed_json_files/<lang>/*.json (processed per-word files)
//
// Usage:
//   Run with args:
//     --db-extract <path> --processed <path> --out <path>
//  Example:
//     -d "\\wsl.localhost\Ubuntu-24.04\home\nkey\kaikki-parser\output\db-extract" -p "C:\Dev\kaikki-parser\words" -o "C:\Dev\slovy-movy-app\.db-files"
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
    val builder = JsonIngestionBuilder(serverDbManager)

    // languages are subdirectories inside processed_root
    val languages = processedRoot.listFiles()?.filter { it.isDirectory }?.map { it.name }?.sorted().orEmpty()
    if (languages.isEmpty()) {
        error("No language folders in processed path: ${processedRoot.absolutePath}")
    }


    languages.forEach { lang ->
        var words = 0

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
        appendLine("Usage: DbPrepTool --db-extract <path> --processed <path> --out <path>")
        appendLine("  db_extract folder layout: <root>/<lang>/*.json")
        appendLine("  processed folder layout:  <root>/<lang>/*.json")
    }
    throw IllegalArgumentException(msg)
}

private data class Params(val dbExtract: String, val processed: String, val out: String)

private fun parseArgs(args: List<String>): Params {
    fun readOpt(name: String): String? {
        val idx = args.indexOf(name)
        if (idx >= 0 && idx + 1 < args.size) return args[idx + 1]
        return null
    }

    val db = readOpt("--db-extract") ?: readOpt("-d")
    val pr = readOpt("--processed") ?: readOpt("-p")
    val out = readOpt("--out") ?: readOpt("-o")
    if (db == null || pr == null || out == null) printUsageAndExit()
    return Params(db, pr, out)
}
