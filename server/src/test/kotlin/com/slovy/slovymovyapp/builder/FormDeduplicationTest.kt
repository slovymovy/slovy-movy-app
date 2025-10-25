@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package com.slovy.slovymovyapp.builder

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Test that forms are deduplicated when multiple raw entries for the same POS
 * have identical forms.
 *
 * Example: nl/stempel.json has two noun entries with identical forms:
 * - stempels (plural)
 * - stempeltje (diminutive, singular)
 * - stempeltjes (diminutive, plural)
 */
class FormDeduplicationTest {

    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        ignoreUnknownKeys = false
        isLenient = false
        allowTrailingComma = false
    }

    @Test
    fun stempel_forms_are_deduplicated() {
        val outDir = Files.createTempDirectory("form_dedup_test").toFile()
        val serverDbManager = ServerDbManager(outDir)

        val lang = "nl"
        val word = "stempel"

        // Build frequency map
        val frequencyMap = mapOf(word to 3.0)
        val builder = JsonIngestionBuilder(serverDbManager, frequencyMap)

        // Load test data files
        val processedFile = resourceFile("processed_json_files/$lang/$word.json")
        val rawFile = resourceFile("db_extract/$lang/$word.json")

        // Parse raw data to verify it has duplicate forms
        val raw = json.decodeFromString(ExtractedWordData.serializer(), rawFile.readText())
        val nativeKey = LANG_TO_SOURCE_FILE[lang]
        val nativeEntries = nativeKey?.let { raw.sourceFileToEntries[it] }.orEmpty()

        // Verify raw data has multiple entries with duplicate forms
        val allForms = nativeEntries.flatMap { it.forms }
        val formTexts = allForms.map { it.form }
        assertTrue(
            formTexts.size > formTexts.toSet().size,
            "Raw data should have duplicate forms. Found: ${formTexts.sorted()}"
        )

        // Expected unique forms for stempel (from nl-extract)
        val expectedUniqueForms = setOf("stempels", "stempeltje", "stempeltjes")
        assertEquals(
            expectedUniqueForms,
            formTexts.toSet(),
            "Expected forms: $expectedUniqueForms"
        )

        // Ingest the data
        builder.ingest(processedFile, rawFile)

        // Verify forms are deduplicated in database
        val dictDb = serverDbManager.openDictionary(lang)
        val dq = dictDb.dictionaryQueries

        // Get the lemma
        val lemmas = dq.selectLemmasByWord(word.lowercase()).executeAsList()
        assertEquals(1, lemmas.size, "Should have exactly one lemma for '$word'")
        val lemmaId = lemmas.first().id

        // Get all lemma_pos entries (should be just NOUN)
        val lemmaPosIds = dq.selectLemmaPosIdByLemmaId(lemmaId).executeAsList()
        assertEquals(1, lemmaPosIds.size, "Should have exactly one POS entry for '$word'")

        // Get all forms for this lemma_pos (with IDs for tag lookup)
        val formsInDb = dq.selectFormsWithIdByLemmaPosId(lemmaPosIds.first()).executeAsList()

        // Verify we have exactly 3 forms (deduplicated)
        assertEquals(
            expectedUniqueForms.size,
            formsInDb.size,
            "Should have exactly ${expectedUniqueForms.size} deduplicated forms. Found: ${formsInDb.map { it.form }}"
        )

        // Verify all expected forms are present
        val actualFormTexts = formsInDb.map { it.form }.toSet()
        assertEquals(
            expectedUniqueForms,
            actualFormTexts,
            "Form texts should match expected unique forms"
        )

        // Verify each form has the correct tags
        formsInDb.forEach { form ->
            val tags = dq.selectFormTagsByFormId(form.form_id).executeAsList().map { it.tag }
            when (form.form) {
                "stempels" -> assertTrue(
                    tags.contains("plural"),
                    "Form 'stempels' should have 'plural' tag. Found: $tags"
                )
                "stempeltje" -> assertTrue(
                    tags.containsAll(listOf("diminutive", "singular")),
                    "Form 'stempeltje' should have 'diminutive' and 'singular' tags. Found: $tags"
                )
                "stempeltjes" -> assertTrue(
                    tags.containsAll(listOf("diminutive", "plural")),
                    "Form 'stempeltjes' should have 'diminutive' and 'plural' tags. Found: $tags"
                )
            }
        }
    }

    private fun resourceFile(path: String): File {
        val cl = Thread.currentThread().contextClassLoader
        val url = cl.getResource(path) ?: error("Resource not found: $path")
        return File(url.toURI())
    }
}
