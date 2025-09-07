package com.slovy.slovymovyapp.builder

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

class ProcessedSchemaTest {
    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        ignoreUnknownKeys = false
        isLenient = false
        allowTrailingComma = false
    }

    @Test
    fun parseAllProcessedJsonFiles_withoutUnknownFields() {
        val files = listJsonFilesFromResourceDir("processed_json_files")
        assertTrue(files.isNotEmpty(), "No JSON files found in processed_json_files resources")
        files.forEach { file ->
            try {
                val content = file.readText()
                json.decodeFromString<LanguageCardResponse>(content)
            } catch (t: Throwable) {
                fail("Failed to parse: ${file.path} -> ${t::class.simpleName}: ${t.message}")
            }
        }
    }

    private fun listJsonFilesFromResourceDir(resourceRoot: String): List<File> {
        val cl = Thread.currentThread().contextClassLoader
        val url = cl.getResource(resourceRoot)
            ?: return emptyList()
        val root = File(url.toURI())
        return root.walkTopDown()
            .filter { it.isFile && it.extension.equals("json", ignoreCase = true) }
            .toList()
    }
}