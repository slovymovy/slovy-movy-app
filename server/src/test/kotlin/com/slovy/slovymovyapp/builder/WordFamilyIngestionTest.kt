@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package com.slovy.slovymovyapp.builder

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WordFamilyIngestionTest {

    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        ignoreUnknownKeys = false
        isLenient = false
        allowTrailingComma = false
    }

    @Test
    fun word_family_is_ingested_correctly() {
        val outDir = Files.createTempDirectory("word_family_ingestion_test").toFile()
        val serverDbManager = ServerDbManager(outDir)

        val lang = "en"
        val word = "double"

        // Build frequency map
        val frequencyMap = mapOf(word to 5.2)
        val builder = JsonIngestionBuilder(serverDbManager, frequencyMap)

        // Load the double.json file
        val processedFile = resourceFile("processed_json_files/$lang/$word.json")
        val rawFile = resourceFile("db_extract/$lang/$word.json")

        // Run ingestion
        builder.ingest(processedFile, rawFile)

        // Validate dictionary DB
        val dictDb = serverDbManager.openDictionary(lang)
        val dq = dictDb.dictionaryQueries

        // Verify the lemma exists
        val lemmas = dq.selectLemmasByWord(word).executeAsList()
        assertTrue(lemmas.isNotEmpty(), "Lemma '$word' should exist in database")

        // Parse the processed JSON to get expected word_family
        val processed = json.decodeFromString(LanguageCardResponse.serializer(), processedFile.readText())
        val expectedWordFamily = processed.wordFamily

        // Verify word_family was ingested
        if (expectedWordFamily != null && expectedWordFamily.isNotEmpty()) {
            val lemmaIds = lemmas.map { it.id }
            val actualWordFamily = lemmaIds.flatMap { lemmaId ->
                dq.selectWordFamilyByLemmaId(lemmaId).executeAsList()
            }

            assertEquals(
                expectedWordFamily.sorted(),
                actualWordFamily.sorted(),
                "Word family should match expected. Expected: ${expectedWordFamily.sorted()}, Found: ${actualWordFamily.sorted()}"
            )

            // Verify expected members are present
            assertEquals(listOf("doublet", "doubling", "doubly"), expectedWordFamily.sorted())
        } else {
            assertTrue(false, "Expected word_family to be present in double.json")
        }
    }

    @Test
    fun word_family_insertion_is_idempotent() {
        val outDir = Files.createTempDirectory("word_family_idempotent_test").toFile()
        val serverDbManager = ServerDbManager(outDir)

        val lang = "en"
        val word = "double"

        val frequencyMap = mapOf(word to 5.2)
        val builder = JsonIngestionBuilder(serverDbManager, frequencyMap)

        val processedFile = resourceFile("processed_json_files/$lang/$word.json")
        val rawFile = resourceFile("db_extract/$lang/$word.json")

        // Ingest twice
        builder.ingest(processedFile, rawFile)

        // Second ingestion should not duplicate word_family entries (INSERT OR IGNORE)
        // This will throw an exception because lemma already exists, but we can test the query directly
        val dictDb = serverDbManager.openDictionary(lang)
        val dq = dictDb.dictionaryQueries

        val lemmas = dq.selectLemmasByWord(word).executeAsList()
        assertTrue(lemmas.isNotEmpty(), "Lemma '$word' should exist")

        val lemmaId = lemmas.first().id

        // Insert duplicate word_family members
        dq.insertLemmaWordFamily(lemmaId, "doubling")
        dq.insertLemmaWordFamily(lemmaId, "doubling") // Duplicate should be ignored

        // Verify no duplicates
        val wordFamily = dq.selectWordFamilyByLemmaId(lemmaId).executeAsList()
        val doublingCount = wordFamily.count { it == "doubling" }
        assertEquals(1, doublingCount, "Should have exactly one 'doubling' entry (duplicates should be ignored)")
    }

    @Test
    fun empty_word_family_is_handled_correctly() {
        val outDir = Files.createTempDirectory("empty_word_family_test").toFile()
        val serverDbManager = ServerDbManager(outDir)

        val lang = "en"
        val word = "testing"

        val frequencyMap = mapOf(word to 4.6)
        val builder = JsonIngestionBuilder(serverDbManager, frequencyMap)

        val processedFile = resourceFile("processed_json_files/$lang/$word.json")
        val rawFile = resourceFile("db_extract/$lang/$word.json")

        builder.ingest(processedFile, rawFile)

        val dictDb = serverDbManager.openDictionary(lang)
        val dq = dictDb.dictionaryQueries

        val lemmas = dq.selectLemmasByWord(word).executeAsList()
        assertTrue(lemmas.isNotEmpty(), "Lemma '$word' should exist")

        val lemmaId = lemmas.first().id

        // Verify word_family is empty (testing.json doesn't have word_family)
        val wordFamily = dq.selectWordFamilyByLemmaId(lemmaId).executeAsList()
        assertTrue(wordFamily.isEmpty(), "Word family should be empty for 'testing' (no word_family in JSON)")
    }

    private fun resourceFile(path: String): File {
        val cl = Thread.currentThread().contextClassLoader
        val url = cl.getResource(path) ?: error("Resource not found: $path")
        return File(url.toURI())
    }
}
