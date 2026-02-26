@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package com.slovy.slovymovyapp.builder

import com.slovy.slovymovyapp.data.dictionary.DictionaryPos
import com.slovy.slovymovyapp.data.dictionary.FormSource
import com.slovy.slovymovyapp.ingestion.*
import com.slovy.slovymovyapp.util.stripAccents
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FormsMappingByPosTest {

    private data class EntryWithSource(val entry: ExtractedWordEntry, val source: FormSource)

    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        ignoreUnknownKeys = false
        isLenient = false
        allowTrailingComma = false
    }

    @Test
    fun afspreken_prefers_native_forms_and_maps_by_pos() {
        val outDir = Files.createTempDirectory("forms_pos_test").toFile()
        val serverDbManager = ServerDbManager(outDir)

        val lang = "nl"
        val word = "afspreken"
        val rawFile = resourceFile("db_extract/$lang/$word.json")
        val processedFile = resourceFile("processed_json_files/$lang/$word.json")

        val raw = json.decodeFromString(ExtractedWordData.serializer(), rawFile.readText())
        val processed = json.decodeFromString(LanguageCardResponse.serializer(), processedFile.readText())

        val entriesForForms = selectEntriesForForms(raw, lang)

        val expectedNativeCount = countUniqueForms(entriesForForms, "verb")
        val mappedBySenseCount = countUniqueForms(mappedEntriesBySense(entriesForForms, processed), "verb")

        val builder = JsonIngestionBuilder(
            translationDbProvider = { from, to -> serverDbManager.openTranslation(from, to) },
            frequencyMap = mapOf(word to 3.0)
        )

        val dictDb = serverDbManager.openDictionary(lang)
        val dictQ = dictDb.dictionaryQueries

        builder.ingest(processedFile.readText(), rawFile.readText(), dictDb)

        val lemma = dictQ.selectLemmasByWord(lang, word.lowercase()).executeAsList().firstOrNull()
        assertNotNull(lemma, "Lemma should exist for '$word'")

        val lemmaPosEntries = dictQ.selectLemmaPosByLemmaId(lemma.id).executeAsList()
        val verbLemmaPos = lemmaPosEntries.firstOrNull { it.pos == DictionaryPos.VERB }
        assertNotNull(verbLemmaPos, "Expected verb lemma_pos for '$word'")

        val formsInDb = dictQ.selectFormsWithIdByLemmaPosId(verbLemmaPos.id).executeAsList()
        val dbFormKeys = formsInDb.map { form ->
            val tags = dictQ.selectFormTagsByFormId(form.form_id).executeAsList().map { it.tag }.toSet()
            FormKey(form.form, stripAccents(form.form), tags, form.source)
        }.toSet()

        assertEquals(
            expectedNativeCount,
            dbFormKeys.size,
            "Should ingest source-aware forms for '$word' (POS mapping only)"
        )
        assertTrue(
            expectedNativeCount > mappedBySenseCount,
            "Native forms should exceed sense-mapped forms for '$word'"
        )
    }

    private data class FormKey(
        val form: String,
        val formNormalized: String,
        val tags: Set<String>,
        val source: FormSource
    )

    private fun countUniqueForms(entries: List<EntryWithSource>, pos: String): Int {
        val forms = linkedMapOf<FormKey, ExtractedWordForm>()
        entries.filter { it.entry.pos == pos }.forEach { (entry, source) ->
            entry.forms.forEach { f ->
                val key = FormKey(f.form, stripAccents(f.form), f.tags.toSet(), source)
                if (!forms.containsKey(key)) {
                    forms[key] = f
                }
            }
        }
        return forms.size
    }

    private fun mappedEntriesBySense(
        entries: List<EntryWithSource>,
        processed: LanguageCardResponse
    ): List<EntryWithSource> {
        val processedSenseIds = processed.entries
            .flatMap { it.senses }
            .map { it.senseId }
            .toSet()
        return entries.filter { (entry, _) ->
            entry.senses.any { sense -> processedSenseIds.contains(sense.senseId.toString()) }
        }
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
