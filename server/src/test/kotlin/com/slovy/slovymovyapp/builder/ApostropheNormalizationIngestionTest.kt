@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package com.slovy.slovymovyapp.builder

import com.slovy.slovymovyapp.ingestion.JsonIngestionBuilder
import com.slovy.slovymovyapp.util.stripAccents
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The `*_normalized` columns hold the ASCII apostrophe whatever the source used, so lookups of
 * "baby’s" and "baby's" share one key, while lemma IDs keep hashing the plain [stripAccents] form.
 */
class ApostropheNormalizationIngestionTest {

    private fun rawJson(word: String, forms: List<String>): String {
        val formsJson = forms.mapIndexed { i, form ->
            """
                {
                  "form_id": "2222222${i}-2222-2222-2222-222222222222",
                  "entry_id": "11111111-1111-1111-1111-111111111111",
                  "tags": ["plural"],
                  "form": "$form"
                }
            """.trimIndent()
        }.joinToString(",")
        return """
            {
              "word": "$word",
              "lang_code": "nl",
              "source_file_to_entries": {
                "nl-extract.jsonl": [
                  {
                    "entry_id": "11111111-1111-1111-1111-111111111111",
                    "word": "$word",
                    "pos": "noun",
                    "lang_code": "nl",
                    "forms": [$formsJson],
                    "senses": [
                      {
                        "sense_id": "55555555-5555-5555-5555-555555555555",
                        "entry_id": "11111111-1111-1111-1111-111111111111",
                        "glosses": ["gloss"],
                        "tags": [],
                        "examples": [],
                        "sense_index_json": null
                      }
                    ],
                    "translations": [],
                    "word_linkages": []
                  }
                ]
              }
            }
        """.trimIndent()
    }

    @Test
    fun typographic_apostrophe_in_a_form_is_stored_as_ascii_in_form_normalized() {
        val serverDbManager = ServerDbManager(Files.createTempDirectory("apostrophe_form_test").toFile())
        val builder = JsonIngestionBuilder(
            translationDbProvider = { from, to -> serverDbManager.openTranslation(from, to) },
            frequencyMap = mapOf("baby" to 3.0)
        )
        val dictDb = serverDbManager.openDictionary("nl")

        builder.ingestRawOnly(rawJson("baby", listOf("baby’s")), dictDb)

        val dictQ = dictDb.dictionaryQueries
        assertEquals(
            "baby",
            dictQ.selectLemmasByFormNormalizedEquals("nl", "baby's", 1L).executeAsOneOrNull()?.lemma,
            "the curly-apostrophe form must be reachable through the ASCII lookup key"
        )
        val forms = dictQ.selectLemmaPosByLemmaId(JsonIngestionBuilder.generateLemmaId("baby")).executeAsList()
            .flatMap { lp -> dictQ.selectFormsWithIdByLemmaPosId(lp.id).executeAsList().map { it.form } }
        assertEquals(listOf("baby’s"), forms, "the displayed form must keep its source apostrophe")
    }

    @Test
    fun typographic_apostrophe_in_a_lemma_normalizes_the_column_but_not_the_lemma_id() {
        val word = "zo’n"
        val serverDbManager = ServerDbManager(Files.createTempDirectory("apostrophe_lemma_test").toFile())
        val builder = JsonIngestionBuilder(
            translationDbProvider = { from, to -> serverDbManager.openTranslation(from, to) },
            frequencyMap = mapOf(word to 3.0)
        )
        val dictDb = serverDbManager.openDictionary("nl")

        builder.ingestRawOnly(rawJson(word, emptyList()), dictDb)

        val lemmaId = JsonIngestionBuilder.generateLemmaId(word, stripAccents(word))
        val lemma = dictDb.dictionaryQueries.selectLemmasById(lemmaId).executeAsOneOrNull()
        assertEquals(word, lemma?.lemma, "the lemma ID must still hash stripAccents, so persisted card.lemma_id stays valid")
        assertEquals("zo'n", lemma?.lemma_normalized, "lemma_normalized must hold the ASCII apostrophe")
    }
}
