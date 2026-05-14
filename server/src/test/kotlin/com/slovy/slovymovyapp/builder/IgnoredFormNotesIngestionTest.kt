@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package com.slovy.slovymovyapp.builder

import com.slovy.slovymovyapp.data.dictionary.DictionaryPos
import com.slovy.slovymovyapp.ingestion.JsonIngestionBuilder
import com.slovy.slovymovyapp.ingestion.uuidParse
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IgnoredFormNotesIngestionTest {

    @Test
    fun raw_only_ignores_numeric_forms_marked_by_note() {
        val outDir = Files.createTempDirectory("ignored_form_notes_test").toFile()
        val serverDbManager = ServerDbManager(outDir)

        val lang = "nl"
        val word = "honderdzeventig"
        val rawJson = """
            {
              "word": "$word",
              "lang_code": "$lang",
              "source_file_to_entries": {
                "nl-extract.jsonl": [
                  {
                    "entry_id": "11111111-1111-1111-1111-111111111111",
                    "word": "$word",
                    "pos": "num",
                    "lang_code": "$lang",
                    "forms": [
                      {
                        "form_id": "22222222-2222-2222-2222-222222222222",
                        "entry_id": "11111111-1111-1111-1111-111111111111",
                        "tags": [],
                        "form": "10101010",
                        "note": "binair"
                      },
                      {
                        "form_id": "33333333-3333-3333-3333-333333333333",
                        "entry_id": "11111111-1111-1111-1111-111111111111",
                        "tags": [],
                        "form": "AA",
                        "note": "hexadecimaal"
                      },
                      {
                        "form_id": "44444444-4444-4444-4444-444444444444",
                        "entry_id": "11111111-1111-1111-1111-111111111111",
                        "tags": [],
                        "form": "CLXX",
                        "note": "Romeins"
                      }
                    ],
                    "senses": [
                      {
                        "sense_id": "55555555-5555-5555-5555-555555555555",
                        "entry_id": "11111111-1111-1111-1111-111111111111",
                        "glosses": ["hundred seventy"],
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

        val builder = JsonIngestionBuilder(
            translationDbProvider = { from, to -> serverDbManager.openTranslation(from, to) },
            frequencyMap = mapOf(word to 3.0)
        )

        val dictDb = serverDbManager.openDictionary(lang)
        val dictQ = dictDb.dictionaryQueries

        builder.ingestRawOnly(rawJson, dictDb)

        val lemmaId = JsonIngestionBuilder.generateLemmaId(word)
        val lemmaPosEntries = dictQ.selectLemmaPosByLemmaId(lemmaId).executeAsList()
        assertEquals(1, lemmaPosEntries.size, "Expected one POS entry for '$word'")
        assertEquals(DictionaryPos.NUMERAL, lemmaPosEntries.first().pos, "Expected NUMERAL POS for '$word'")

        val forms = dictQ.selectFormsByLemmaPosId(lemmaPosEntries.first().id).executeAsList()
        assertTrue(forms.isEmpty(), "Expected numeric forms marked as binary, hexadecimal, or Roman to be ignored")
    }

    @Test
    fun primary_cluster_counts_only_ingestible_forms() {
        val outDir = Files.createTempDirectory("ignored_form_notes_primary_test").toFile()
        val serverDbManager = ServerDbManager(outDir)

        val lang = "nl"
        val word = "primaircluster"
        val rawJson = """
            {
              "word": "$word",
              "lang_code": "$lang",
              "source_file_to_entries": {
                "nl-extract.jsonl": [
                  {
                    "entry_id": "11111111-1111-1111-1111-111111111111",
                    "word": "$word",
                    "pos": "num",
                    "lang_code": "$lang",
                    "forms": [
                      {
                        "form_id": "22222222-2222-2222-2222-222222222222",
                        "entry_id": "11111111-1111-1111-1111-111111111111",
                        "tags": [],
                        "form": "primary-one"
                      },
                      {
                        "form_id": "33333333-3333-3333-3333-333333333333",
                        "entry_id": "11111111-1111-1111-1111-111111111111",
                        "tags": [],
                        "form": "10101010",
                        "note": "binair"
                      },
                      {
                        "form_id": "44444444-4444-4444-4444-444444444444",
                        "entry_id": "11111111-1111-1111-1111-111111111111",
                        "tags": [],
                        "form": "AA",
                        "note": "hexadecimaal"
                      },
                      {
                        "form_id": "55555555-5555-5555-5555-555555555555",
                        "entry_id": "11111111-1111-1111-1111-111111111111",
                        "tags": [],
                        "form": "CLXX",
                        "note": "Romeins"
                      }
                    ],
                    "senses": [],
                    "translations": [],
                    "word_linkages": []
                  },
                  {
                    "entry_id": "66666666-6666-6666-6666-666666666666",
                    "word": "$word",
                    "pos": "num",
                    "lang_code": "$lang",
                    "forms": [
                      {
                        "form_id": "77777777-7777-7777-7777-777777777777",
                        "entry_id": "66666666-6666-6666-6666-666666666666",
                        "tags": [],
                        "form": "primary-two"
                      },
                      {
                        "form_id": "88888888-8888-8888-8888-888888888888",
                        "entry_id": "66666666-6666-6666-6666-666666666666",
                        "tags": [],
                        "form": "primary-two-alt"
                      }
                    ],
                    "senses": [],
                    "translations": [],
                    "word_linkages": []
                  }
                ]
              }
            }
        """.trimIndent()
        val processedJson = """
            {
              "entries": [
                {
                  "pos": "num",
                  "senses": [
                    {
                      "sense_id": "99999999-9999-9999-9999-999999999999",
                      "sense_definition": "fallback sense",
                      "learner_level": "A1",
                      "frequency": "HIGH",
                      "semantic_group_id": "number",
                      "examples": [],
                      "synonyms": [],
                      "antonyms": [],
                      "common_phrases": [],
                      "traits": [],
                      "target_lang_definitions": {},
                      "translations": {}
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val builder = JsonIngestionBuilder(
            translationDbProvider = { from, to -> serverDbManager.openTranslation(from, to) },
            frequencyMap = mapOf(word to 3.0)
        )

        val dictDb = serverDbManager.openDictionary(lang)
        val dictQ = dictDb.dictionaryQueries

        builder.ingest(processedJson, rawJson, dictDb)

        val lemmaId = JsonIngestionBuilder.generateLemmaId(word)
        val lemmaPosEntries = dictQ.selectLemmaPosByLemmaId(lemmaId).executeAsList()
        assertEquals(2, lemmaPosEntries.size, "Expected two NUMERAL clusters for '$word'")

        val expectedPrimaryLemmaPosId = lemmaPosEntries.firstOrNull { lemmaPos ->
            dictQ.selectFormsWithIdByLemmaPosId(lemmaPos.id).executeAsList().any { it.form == "primary-two" }
        }?.id ?: error("Expected to find the cluster with two ingestible forms")

        val fallbackSenseId = uuidParse("99999999-9999-9999-9999-999999999999")
        val sensesInExpectedPrimary = dictQ.selectSensesByLemmaPosId(expectedPrimaryLemmaPosId).executeAsList()
        assertTrue(
            sensesInExpectedPrimary.any { it.sense_id == fallbackSenseId },
            "Expected fallback sense to route to the cluster with the most ingestible forms"
        )
    }

}
