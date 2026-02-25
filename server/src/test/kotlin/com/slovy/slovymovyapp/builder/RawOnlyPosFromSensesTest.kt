@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package com.slovy.slovymovyapp.builder

import com.slovy.slovymovyapp.data.dictionary.DictionaryPos
import com.slovy.slovymovyapp.ingestion.JsonIngestionBuilder
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RawOnlyPosFromSensesTest {

    @Test
    fun raw_only_inserts_pos_even_when_forms_missing() {
        val outDir = Files.createTempDirectory("raw_only_pos_test").toFile()
        val serverDbManager = ServerDbManager(outDir)

        val lang = "en"
        val word = "testword"
        val rawJson = """
            {
              "word": "$word",
              "lang_code": "$lang",
              "source_file_to_entries": {
                "raw-wiktextract-data.jsonl": [
                  {
                    "entry_id": "11111111-1111-1111-1111-111111111111",
                    "word": "$word",
                    "pos": "verb",
                    "lang_code": "$lang",
                    "forms": [],
                    "senses": [
                      {
                        "sense_id": "22222222-2222-2222-2222-222222222222",
                        "entry_id": "11111111-1111-1111-1111-111111111111",
                        "glosses": ["to test"],
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
        assertEquals(DictionaryPos.VERB, lemmaPosEntries.first().pos, "Expected VERB POS for '$word'")

        val forms = dictQ.selectFormsByLemmaPosId(lemmaPosEntries.first().id).executeAsList()
        assertTrue(forms.isEmpty(), "Expected no forms for '$word'")
    }
}
