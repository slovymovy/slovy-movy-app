@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package com.slovy.slovymovyapp.builder

import com.slovy.slovymovyapp.data.dictionary.FormSource
import com.slovy.slovymovyapp.ingestion.ExtractedWordData
import com.slovy.slovymovyapp.ingestion.ExtractedWordEntry
import com.slovy.slovymovyapp.ingestion.LANG_TO_SOURCE_FILE
import com.slovy.slovymovyapp.ingestion.JsonIngestionBuilder
import com.slovy.slovymovyapp.util.stripAccents
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

    private data class EntryWithSource(val entry: ExtractedWordEntry, val source: FormSource)
    private data class FormKey(
        val form: String,
        val formNormalized: String,
        val tags: Set<String>,
        val source: FormSource
    )

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
        val builder = JsonIngestionBuilder({ from, to -> serverDbManager.openTranslation(from, to) }, frequencyMap)

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

        val expectedNativeForms = setOf("stempels", "stempeltje", "stempeltjes")
        assertEquals(expectedNativeForms, formTexts.toSet(), "Unexpected native forms in test data")

        val entriesForForms = selectEntriesForForms(raw, lang)
        val expectedFormKeys = entriesForForms
            .filter { it.entry.pos == "noun" }
            .flatMap { (entry, source) ->
                entry.forms.map { form ->
                    FormKey(form.form, stripAccents(form.form), form.tags.toSet(), source)
                }
            }
            .toSet()

        // Ingest the data
        val dictDb = serverDbManager.openDictionary(lang)

        val dictQ = dictDb.dictionaryQueries
        builder.ingest(
            processedFile.readText(),
            rawFile.readText(),
            dictDb)

        // Verify forms are deduplicated in database.
        // Get the lemma
        val lemmas = dictQ.selectLemmasByWord(lang, word.lowercase()).executeAsList()
        assertEquals(1, lemmas.size, "Should have exactly one lemma for '$word'")
        val lemmaId = lemmas.first().id

        // Get all lemma_pos entries (should be just NOUN)
        val lemmaPosIds = dictQ.selectLemmaPosIdByLemmaId(lemmaId).executeAsList()
        assertEquals(1, lemmaPosIds.size, "Should have exactly one POS entry for '$word'")

        // Get all forms for this lemma_pos (with IDs for tag lookup)
        val formsInDb = dictQ.selectFormsWithIdByLemmaPosId(lemmaPosIds.first()).executeAsList()

        val actualFormKeys = formsInDb.map { form ->
            val tags = dictQ.selectFormTagsByFormId(form.form_id).executeAsList().map { it.tag }
            FormKey(form.form, stripAccents(form.form), tags.toSet(), form.source)
        }
        val actualFormKeySet = actualFormKeys.toSet()

        // No duplicate forms by (form, normalized, tags, source).
        assertEquals(
            actualFormKeySet.size,
            actualFormKeys.size,
            "Found duplicate forms by (form, normalized, tags, source): ${formsInDb.map { it.form }}"
        )
        assertEquals(
            expectedFormKeys,
            actualFormKeySet,
            "Form keys in DB should match source-aware expected forms"
        )

        val actualFormTexts = formsInDb.map { it.form }.toSet()
        assertTrue(
            expectedNativeForms.all { actualFormTexts.contains(it) },
            "Expected native forms missing. Expected: $expectedNativeForms, actual: $actualFormTexts"
        )
    }

    private fun selectEntriesForForms(raw: ExtractedWordData, lang: String): List<EntryWithSource> {
        val nativeKey = LANG_TO_SOURCE_FILE[lang]
        val enKey = LANG_TO_SOURCE_FILE["en"]

        val nativeEntries = nativeKey?.let { raw.sourceFileToEntries[it] }.orEmpty()
        val enEntries = if (lang == "en") {
            emptyList()
        } else {
            enKey?.let { raw.sourceFileToEntries[it] }.orEmpty()
        }

        return buildList {
            nativeEntries.forEach { add(EntryWithSource(it, FormSource.NATIVE)) }
            enEntries.forEach { add(EntryWithSource(it, FormSource.EN)) }
        }
    }

    private fun resourceFile(path: String): File {
        val cl = Thread.currentThread().contextClassLoader
        val url = cl.getResource(path) ?: error("Resource not found: $path")
        return File(url.toURI())
    }
}
