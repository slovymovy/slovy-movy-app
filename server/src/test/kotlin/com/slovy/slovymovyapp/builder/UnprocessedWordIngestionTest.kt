@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package com.slovy.slovymovyapp.builder

import com.slovy.slovymovyapp.ingestion.JsonIngestionBuilder
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UnprocessedWordIngestionTest {

    @Test
    fun raw_only_words_are_marked_online_only_and_have_forms() {
        val outDir = Files.createTempDirectory("raw_only_ingestion_test").toFile()
        val serverDbManager = ServerDbManager(outDir)

        val lang = "nl"
        val word = "aanpassingsprobleem"

        val frequencyMap = mapOf(word to 2.5)
        val builder = JsonIngestionBuilder({ from, to -> serverDbManager.openTranslation(from, to) }, frequencyMap)

        val rawFile = resourceFile("db_extract/$lang/$word.json")

        builder.ingestRawOnly(
            rawFile.readText(),
            serverDbManager.openDictionary(lang)
        )

        val dictDb = serverDbManager.openDictionary(lang)
        val dq = dictDb.dictionaryQueries

        val lemmas = dq.selectLemmasByWord(lang, word).executeAsList()
        assertEquals(1, lemmas.size, "Should ingest lemma for raw-only word")
        val lemmaRow = lemmas.first()
        assertTrue(lemmaRow.online_only, "Raw-only lemma must be marked online_only")
        assertEquals(
            2.5,
            lemmaRow.zipf_frequency,
            "Should store provided Zipf frequency for raw-only lemma"
        )

        val lemmaPosIds = dq.selectLemmaPosIdByLemmaId(lemmaRow.id).executeAsList()
        assertEquals(1, lemmaPosIds.size, "Should insert lemma_pos from raw data")

        val forms = dq.selectFormsWithIdByLemmaPosId(lemmaPosIds.first()).executeAsList()
        val expectedForms = setOf("aanpassingsproblemen", "aanpassingsprobleempje", "aanpassingsprobleempjes")
        assertEquals(expectedForms, forms.map { it.form }.toSet(), "Forms should come from raw entries")

        forms.forEach { form ->
            val tags = dq.selectFormTagsByFormId(form.form_id).executeAsList().map { it.tag }.toSet()
            when (form.form) {
                "aanpassingsproblemen" -> assertEquals(setOf("plural"), tags, "Expected plural tag")
                "aanpassingsprobleempje" -> assertEquals(
                    setOf("diminutive", "singular"),
                    tags,
                    "Expected diminutive + singular tags"
                )

                "aanpassingsprobleempjes" -> assertEquals(
                    setOf("diminutive", "plural"),
                    tags,
                    "Expected diminutive + plural tags"
                )
            }
        }

        val senses = dq.selectSensesByLemmaPosId(lemmaPosIds.first()).executeAsList()
        assertTrue(senses.isEmpty(), "Raw-only ingestion should not insert senses")
    }

    private fun resourceFile(path: String): File {
        val cl = Thread.currentThread().contextClassLoader
        val url = cl.getResource(path) ?: error("Resource not found: $path")
        return File(url.toURI())
    }
}
