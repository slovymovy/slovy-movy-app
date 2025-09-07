package com.slovy.slovymovyapp.builder

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

class DbExtractedSchemaTest {
    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        ignoreUnknownKeys = false
        isLenient = false
        allowTrailingComma = false
    }

    @Test
    fun parseAllDbExtractJsonFiles_withoutUnknownFields() {
        val files = listJsonFilesFromResourceDir("db_extract")
        assertTrue(files.isNotEmpty(), "No JSON files found in db_extract resources")
        files.forEach { file ->
            try {
                val content = file.readText()
                json.decodeFromString<ExtractedWordData>(content)
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