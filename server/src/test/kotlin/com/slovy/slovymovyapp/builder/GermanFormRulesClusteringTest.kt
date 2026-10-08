@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package com.slovy.slovymovyapp.builder

import com.slovy.slovymovyapp.data.dictionary.DictionaryPos
import com.slovy.slovymovyapp.ingestion.JsonIngestionBuilder
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * POS clustering must see the same forms that are stored. Two native German entries that differ
 * only in forms [com.slovy.slovymovyapp.ingestion.GermanFormRules] drops or rewrites have the same
 * paradigm once filtered, so they form one lemma_pos rather than two.
 */
class GermanFormRulesClusteringTest {

    private fun form(id: String, entryId: String, text: String, vararg tags: String) = """
        {
          "form_id": "$id",
          "entry_id": "$entryId",
          "tags": [${tags.joinToString { "\"$it\"" }}],
          "form": "$text"
        }
    """.trimIndent()

    private fun entry(entryId: String, senseId: String, forms: List<String>) = """
        {
          "entry_id": "$entryId",
          "word": "kaufen",
          "pos": "verb",
          "lang_code": "de",
          "forms": [${forms.joinToString(",")}],
          "senses": [
            {
              "sense_id": "$senseId",
              "entry_id": "$entryId",
              "glosses": ["to buy"],
              "tags": [],
              "examples": [],
              "sense_index_json": null
            }
          ],
          "translations": [],
          "word_linkages": []
        }
    """.trimIndent()

    @Test
    fun entries_differing_only_in_dropped_forms_share_one_lemma_pos() {
        val outDir = Files.createTempDirectory("german_form_rules_clustering_test").toFile()
        val serverDbManager = ServerDbManager(outDir)

        val first = "11111111-1111-1111-1111-111111111111"
        val second = "22222222-2222-2222-2222-222222222222"
        val rawJson = """
            {
              "word": "kaufen",
              "lang_code": "de",
              "source_file_to_entries": {
                "de-extract.jsonl": [
                  ${entry(first, "aaaaaaaa-1111-1111-1111-111111111111", listOf(
                      form("31111111-1111-1111-1111-111111111111", first, "ich kaufe", "first-person", "present", "singular"),
                      form("41111111-1111-1111-1111-111111111111", first, "kaufte", "past", "singular"),
                      form("51111111-1111-1111-1111-111111111111", first, "habe gekauft", "perfect", "first-person")
                  ))},
                  ${entry(second, "bbbbbbbb-2222-2222-2222-222222222222", listOf(
                      form("32222222-2222-2222-2222-222222222222", second, "kaufe", "first-person", "present", "singular"),
                      form("42222222-2222-2222-2222-222222222222", second, "kaufte", "past", "singular"),
                      form("52222222-2222-2222-2222-222222222222", second, "werde kaufen", "future", "first-person")
                  ))}
                ]
              }
            }
        """.trimIndent()

        val builder = JsonIngestionBuilder(
            translationDbProvider = { from, to -> serverDbManager.openTranslation(from, to) },
            frequencyMap = mapOf("kaufen" to 4.0)
        )
        val dictDb = serverDbManager.openDictionary("de")
        builder.ingestRawOnly(rawJson, dictDb)

        val dictQ = dictDb.dictionaryQueries
        val lemmaPos = dictQ.selectLemmaPosByLemmaId(JsonIngestionBuilder.generateLemmaId("kaufen")).executeAsList()
        assertEquals(
            listOf(DictionaryPos.VERB),
            lemmaPos.map { it.pos },
            "both entries filter to {kaufe, kaufte}, so they are one paradigm and one lemma_pos"
        )
        assertEquals(
            listOf("kaufe", "kaufte"),
            dictQ.selectFormsByLemmaPosId(lemmaPos.single().id).executeAsList().map { it.form }.sorted(),
            "the stored forms are the filtered ones, without pronouns or compound tenses"
        )
    }
}
