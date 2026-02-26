@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package com.slovy.slovymovyapp.builder

import com.slovy.slovymovyapp.ingestion.JsonIngestionBuilder
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFalse

/**
 * Verifies that the lemma word is never stored as its own synonym, antonym,
 * or word-family member — for both the [JsonIngestionBuilder.ingest] and
 * [JsonIngestionBuilder.ingestProcessedOverRaw] paths.
 */
class LemmaSelfExclusionIngestionTest {

    // Minimal raw (db_extract) JSON for a word with one POS sense.
    private fun minimalRaw(word: String, lang: String, entryId: String, senseId: String) = """
        {
          "word": "$word",
          "lang_code": "$lang",
          "source_file_to_entries": {
            "raw-wiktextract-data.jsonl": [
              {
                "entry_id": "$entryId",
                "word": "$word",
                "pos": "verb",
                "lang_code": "$lang",
                "forms": [],
                "senses": [
                  {
                    "sense_id": "$senseId",
                    "entry_id": "$entryId",
                    "glosses": ["A gloss for $word."],
                    "tags": [],
                    "examples": []
                  }
                ],
                "translations": [],
                "word_linkages": []
              }
            ]
          }
        }
    """.trimIndent()

    // Processed JSON where the lemma word appears in synonyms, antonyms, and word_family.
    private fun processedWithSelfReferences(
        word: String,
        senseId: String,
        otherSynonym: String = "examine",
        otherAntonym: String = "ignore",
        otherFamily: String = "${word}ing"
    ) = """
        {
          "word_family": ["$otherFamily", "$word"],
          "entries": [
            {
              "pos": "verb",
              "senses": [
                {
                  "sense_id": "$senseId",
                  "sense_definition": "To $word something carefully.",
                  "learner_level": "B1",
                  "frequency": "Middle",
                  "semantic_group_id": "action",
                  "name_type": "no",
                  "synonyms": ["$otherSynonym", "$word"],
                  "antonyms": ["$otherAntonym", "$word"],
                  "common_phrases": [],
                  "target_lang_definitions": {},
                  "translations": {}
                }
              ]
            }
          ]
        }
    """.trimIndent()

    @Test
    fun lemma_excluded_from_synonyms_via_ingest() {
        val outDir = Files.createTempDirectory("self_exclusion_ingest").toFile()
        val dbManager = ServerDbManager(outDir)
        val lang = "en"
        val word = "probe"
        val entryId = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
        val senseId = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"

        val builder = JsonIngestionBuilder(
            { from, to -> dbManager.openTranslation(from, to) },
            mapOf(word to 4.0)
        )

        builder.ingest(
            processedJson = processedWithSelfReferences(word, senseId),
            rawJson = minimalRaw(word, lang, entryId, senseId),
            dictDb = dbManager.openDictionary(lang)
        )

        val dq = dbManager.openDictionary(lang).dictionaryQueries
        val synonyms = allSynonymsForWord(dq, lang, word)

        assertFalse(
            synonyms.any { it.equals(word, ignoreCase = true) },
            "Lemma '$word' must not appear in its own synonyms; found: $synonyms"
        )
    }

    @Test
    fun lemma_excluded_from_antonyms_via_ingest() {
        val outDir = Files.createTempDirectory("self_exclusion_antonyms_ingest").toFile()
        val dbManager = ServerDbManager(outDir)
        val lang = "en"
        val word = "probe"
        val entryId = "cccccccc-cccc-cccc-cccc-cccccccccccc"
        val senseId = "dddddddd-dddd-dddd-dddd-dddddddddddd"

        val builder = JsonIngestionBuilder(
            { from, to -> dbManager.openTranslation(from, to) },
            mapOf(word to 4.0)
        )

        builder.ingest(
            processedJson = processedWithSelfReferences(word, senseId),
            rawJson = minimalRaw(word, lang, entryId, senseId),
            dictDb = dbManager.openDictionary(lang)
        )

        val dq = dbManager.openDictionary(lang).dictionaryQueries
        val antonyms = allAntonymsForWord(dq, lang, word)

        assertFalse(
            antonyms.any { it.equals(word, ignoreCase = true) },
            "Lemma '$word' must not appear in its own antonyms; found: $antonyms"
        )
    }

    @Test
    fun lemma_excluded_from_word_family_via_ingest() {
        val outDir = Files.createTempDirectory("self_exclusion_family_ingest").toFile()
        val dbManager = ServerDbManager(outDir)
        val lang = "en"
        val word = "probe"
        val entryId = "eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee"
        val senseId = "ffffffff-ffff-ffff-ffff-ffffffffffff"

        val builder = JsonIngestionBuilder(
            { from, to -> dbManager.openTranslation(from, to) },
            mapOf(word to 4.0)
        )

        builder.ingest(
            processedJson = processedWithSelfReferences(word, senseId),
            rawJson = minimalRaw(word, lang, entryId, senseId),
            dictDb = dbManager.openDictionary(lang)
        )

        val dq = dbManager.openDictionary(lang).dictionaryQueries
        val lemmaId = dq.selectLemmasByWord(lang, word).executeAsList().first().id
        val wordFamily = dq.selectWordFamilyByLemmaId(lemmaId).executeAsList()

        assertFalse(
            wordFamily.any { it.equals(word, ignoreCase = true) },
            "Lemma '$word' must not appear in its own word family; found: $wordFamily"
        )
    }

    @Test
    fun lemma_excluded_from_synonyms_via_ingest_processed_over_raw() {
        val outDir = Files.createTempDirectory("self_exclusion_por_synonyms").toFile()
        val dbManager = ServerDbManager(outDir)
        val lang = "en"
        val word = "scan"
        val entryId = "11111111-1111-1111-1111-111111111111"
        val senseId = "22222222-2222-2222-2222-222222222222"

        val builder = JsonIngestionBuilder(
            { from, to -> dbManager.openTranslation(from, to) },
            mapOf(word to 4.0)
        )

        builder.ingestRawOnly(
            rawJson = minimalRaw(word, lang, entryId, senseId),
            dictDb = dbManager.openDictionary(lang)
        )
        builder.ingestProcessedOverRaw(
            processedJson = processedWithSelfReferences(word, senseId),
            word = word,
            langCode = lang,
            dictDb = dbManager.openDictionary(lang)
        )

        val dq = dbManager.openDictionary(lang).dictionaryQueries
        val synonyms = allSynonymsForWord(dq, lang, word)

        assertFalse(
            synonyms.any { it.equals(word, ignoreCase = true) },
            "Lemma '$word' must not appear in its own synonyms after ingestProcessedOverRaw; found: $synonyms"
        )
    }

    @Test
    fun lemma_excluded_from_antonyms_via_ingest_processed_over_raw() {
        val outDir = Files.createTempDirectory("self_exclusion_por_antonyms").toFile()
        val dbManager = ServerDbManager(outDir)
        val lang = "en"
        val word = "scan"
        val entryId = "33333333-3333-3333-3333-333333333333"
        val senseId = "44444444-4444-4444-4444-444444444444"

        val builder = JsonIngestionBuilder(
            { from, to -> dbManager.openTranslation(from, to) },
            mapOf(word to 4.0)
        )

        builder.ingestRawOnly(
            rawJson = minimalRaw(word, lang, entryId, senseId),
            dictDb = dbManager.openDictionary(lang)
        )
        builder.ingestProcessedOverRaw(
            processedJson = processedWithSelfReferences(word, senseId),
            word = word,
            langCode = lang,
            dictDb = dbManager.openDictionary(lang)
        )

        val dq = dbManager.openDictionary(lang).dictionaryQueries
        val antonyms = allAntonymsForWord(dq, lang, word)

        assertFalse(
            antonyms.any { it.equals(word, ignoreCase = true) },
            "Lemma '$word' must not appear in its own antonyms after ingestProcessedOverRaw; found: $antonyms"
        )
    }

    @Test
    fun lemma_excluded_from_word_family_via_ingest_processed_over_raw() {
        val outDir = Files.createTempDirectory("self_exclusion_por_family").toFile()
        val dbManager = ServerDbManager(outDir)
        val lang = "en"
        val word = "scan"
        val entryId = "55555555-5555-5555-5555-555555555555"
        val senseId = "66666666-6666-6666-6666-666666666666"

        val builder = JsonIngestionBuilder(
            { from, to -> dbManager.openTranslation(from, to) },
            mapOf(word to 4.0)
        )

        builder.ingestRawOnly(
            rawJson = minimalRaw(word, lang, entryId, senseId),
            dictDb = dbManager.openDictionary(lang)
        )
        builder.ingestProcessedOverRaw(
            processedJson = processedWithSelfReferences(word, senseId),
            word = word,
            langCode = lang,
            dictDb = dbManager.openDictionary(lang)
        )

        val dq = dbManager.openDictionary(lang).dictionaryQueries
        val lemmaId = dq.selectLemmasByWord(lang, word).executeAsList().first().id
        val wordFamily = dq.selectWordFamilyByLemmaId(lemmaId).executeAsList()

        assertFalse(
            wordFamily.any { it.equals(word, ignoreCase = true) },
            "Lemma '$word' must not appear in its own word family after ingestProcessedOverRaw; found: $wordFamily"
        )
    }

    @Test
    fun lemma_exclusion_is_case_insensitive() {
        val outDir = Files.createTempDirectory("self_exclusion_case").toFile()
        val dbManager = ServerDbManager(outDir)
        val lang = "en"
        val word = "run"
        val entryId = "77777777-7777-7777-7777-777777777777"
        val senseId = "88888888-8888-8888-8888-888888888888"

        val builder = JsonIngestionBuilder(
            { from, to -> dbManager.openTranslation(from, to) },
            mapOf(word to 4.5)
        )

        // Processed JSON with capitalized self-references ("Run", "RUN")
        val processedJson = """
            {
              "word_family": ["running", "Run"],
              "entries": [
                {
                  "pos": "verb",
                  "senses": [
                    {
                      "sense_id": "$senseId",
                      "sense_definition": "To move quickly.",
                      "learner_level": "A1",
                      "frequency": "High",
                      "semantic_group_id": "motion",
                      "name_type": "no",
                      "synonyms": ["sprint", "RUN"],
                      "antonyms": ["walk", "Run"],
                      "common_phrases": [],
                      "target_lang_definitions": {},
                      "translations": {}
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        builder.ingest(
            processedJson = processedJson,
            rawJson = minimalRaw(word, lang, entryId, senseId),
            dictDb = dbManager.openDictionary(lang)
        )

        val dq = dbManager.openDictionary(lang).dictionaryQueries
        val lemmaId = dq.selectLemmasByWord(lang, word).executeAsList().first().id

        val synonyms = allSynonymsForWord(dq, lang, word)
        val antonyms = allAntonymsForWord(dq, lang, word)
        val wordFamily = dq.selectWordFamilyByLemmaId(lemmaId).executeAsList()

        assertFalse(
            synonyms.any { it.equals(word, ignoreCase = true) },
            "Case-insensitive synonym self-exclusion failed; found: $synonyms"
        )
        assertFalse(
            antonyms.any { it.equals(word, ignoreCase = true) },
            "Case-insensitive antonym self-exclusion failed; found: $antonyms"
        )
        assertFalse(
            wordFamily.any { it.equals(word, ignoreCase = true) },
            "Case-insensitive word-family self-exclusion failed; found: $wordFamily"
        )
    }

    // --- helpers ---

    private fun allSynonymsForWord(
        dq: com.slovy.slovymovyapp.dictionary.DictionaryQueries,
        lang: String,
        word: String
    ): List<String> {
        val lemmas = dq.selectLemmasByWord(lang, word).executeAsList()
        return lemmas.flatMap { lemma ->
            dq.selectLemmaPosIdByLemmaId(lemma.id).executeAsList().flatMap { lemmaPosId ->
                dq.selectSensesByLemmaPosId(lemmaPosId).executeAsList().flatMap { sense ->
                    dq.selectSenseSynonyms(sense.sense_id).executeAsList().map { it.synonym }
                }
            }
        }
    }

    private fun allAntonymsForWord(
        dq: com.slovy.slovymovyapp.dictionary.DictionaryQueries,
        lang: String,
        word: String
    ): List<String> {
        val lemmas = dq.selectLemmasByWord(lang, word).executeAsList()
        return lemmas.flatMap { lemma ->
            dq.selectLemmaPosIdByLemmaId(lemma.id).executeAsList().flatMap { lemmaPosId ->
                dq.selectSensesByLemmaPosId(lemmaPosId).executeAsList().flatMap { sense ->
                    dq.selectSenseAntonyms(sense.sense_id).executeAsList().map { it.antonym }
                }
            }
        }
    }
}
