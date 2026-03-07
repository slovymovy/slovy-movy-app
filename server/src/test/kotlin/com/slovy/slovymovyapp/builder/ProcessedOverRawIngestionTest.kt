@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package com.slovy.slovymovyapp.builder

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.slovy.slovymovyapp.data.dictionary.DictionaryPos
import com.slovy.slovymovyapp.ingestion.JsonIngestionBuilder
import java.io.File
import java.nio.file.Files
import kotlin.test.*

class ProcessedOverRawIngestionTest {

    @Test
    fun processed_over_raw_updates_online_only_and_adds_senses() {
        val outDir = Files.createTempDirectory("processed_over_raw_test").toFile()
        val serverDbManager = ServerDbManager(outDir)

        val lang = "nl"
        val word = "programmamaker"

        val frequencyMap = mapOf(word to 3.5)
        val builder = JsonIngestionBuilder({ from, to -> serverDbManager.openTranslation(from, to) }, frequencyMap)

        val rawFile = resourceFile("db_extract/$lang/$word.json")
        val processedFile = resourceFile("processed_json_files/$lang/$word.json")

        // First: ingest raw only
        builder.ingestRawOnly(
            rawFile.readText(),
            serverDbManager.openDictionary(lang)
        )

        val dictDb = serverDbManager.openDictionary(lang)
        val dq = dictDb.dictionaryQueries

        // Verify it's online_only
        var lemmas = dq.selectLemmasByWord(lang, word).executeAsList()
        assertEquals(1, lemmas.size)
        assertTrue(lemmas.first().online_only, "Should be online_only after raw-only ingestion")

        // Verify no senses yet
        val lemmaPosIds = dq.selectLemmaPosIdByLemmaId(lemmas.first().id).executeAsList()
        assertTrue(lemmaPosIds.isNotEmpty(), "Should have lemma_pos entries")
        var senses = dq.selectSensesByLemmaPosId(lemmaPosIds.first()).executeAsList()
        assertTrue(senses.isEmpty(), "Should have no senses after raw-only ingestion")

        // Now: add processed data over raw
        val skippedPos = builder.ingestProcessedOverRaw(
            processedFile.readText(),
            word,
            lang,
            serverDbManager.openDictionary(lang)
        )

        assertTrue(skippedPos.isEmpty(), "No POS should be skipped for matching data")

        // Verify online_only is now false
        lemmas = dq.selectLemmasByWord(lang, word).executeAsList()
        assertFalse(lemmas.first().online_only, "Should not be online_only after processed ingestion")

        // Verify senses were added
        senses = dq.selectSensesByLemmaPosId(lemmaPosIds.first()).executeAsList()
        assertTrue(senses.isNotEmpty(), "Should have senses after processed ingestion")

        // Verify translations were added (check translation DB)
        val trDb = serverDbManager.openTranslation(lang, "en")
        val trQ = trDb.translationQueries
        val translations = trQ.selectSenseTranslationsBySense(
            senses.first().sense_id,
            lang,
            "en"
        ).executeAsList()
        assertTrue(translations.isNotEmpty(), "Should have translations after processed ingestion")
    }

    @Test
    fun processed_over_raw_skips_unmatched_pos_with_warning() {
        val outDir = Files.createTempDirectory("processed_over_raw_pos_skip_test").toFile()
        val serverDbManager = ServerDbManager(outDir)

        val lang = "nl"
        val word = "programmamaker"

        val frequencyMap = mapOf(word to 3.5)
        val builder = JsonIngestionBuilder({ from, to -> serverDbManager.openTranslation(from, to) }, frequencyMap)

        val rawFile = resourceFile("db_extract/$lang/$word.json")

        // Ingest raw only (which has only NOUN POS)
        builder.ingestRawOnly(
            rawFile.readText(),
            serverDbManager.openDictionary(lang)
        )

        // Create processed JSON with an extra VERB POS that doesn't exist in raw
        val processedWithExtraPos = """
        {
            "entries": [
                {
                    "pos": "noun",
                    "senses": [
                        {
                            "sense_id": "4e9bd9dc-07b8-482c-8b27-275190e59725",
                            "sense_definition": "Test definition",
                            "learner_level": "B1",
                            "frequency": "Middle",
                            "semantic_group_id": "test"
                        }
                    ]
                },
                {
                    "pos": "verb",
                    "senses": [
                        {
                            "sense_id": "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
                            "sense_definition": "Fake verb sense",
                            "learner_level": "B1",
                            "frequency": "Low",
                            "semantic_group_id": "test_verb"
                        }
                    ]
                }
            ]
        }
        """.trimIndent()

        val skippedPos = builder.ingestProcessedOverRaw(
            processedWithExtraPos,
            word,
            lang,
            serverDbManager.openDictionary(lang)
        )

        // Verify VERB was skipped
        assertEquals(listOf("verb"), skippedPos, "VERB POS should be skipped")

        // Verify NOUN senses were still added
        val dictDb = serverDbManager.openDictionary(lang)
        val dq = dictDb.dictionaryQueries
        val lemmas = dq.selectLemmasByWord(lang, word).executeAsList()
        assertFalse(lemmas.first().online_only, "Should be processed even with skipped POS")

        val lemmaPosIds = dq.selectLemmaPosIdByLemmaId(lemmas.first().id).executeAsList()
        val senses = dq.selectSensesByLemmaPosId(lemmaPosIds.first()).executeAsList()
        assertTrue(senses.isNotEmpty(), "NOUN senses should be added despite VERB being skipped")
    }

    @Test
    fun processed_over_raw_fails_if_lemma_not_found() {
        val outDir = Files.createTempDirectory("processed_over_raw_not_found_test").toFile()
        val serverDbManager = ServerDbManager(outDir)

        val lang = "nl"
        val word = "nonexistentword"

        val frequencyMap = mapOf(word to 1.0)
        val builder = JsonIngestionBuilder({ from, to -> serverDbManager.openTranslation(from, to) }, frequencyMap)

        val processedJson = """
        {
            "entries": [
                {
                    "pos": "noun",
                    "senses": [
                        {
                            "sense_id": "11111111-2222-3333-4444-555555555555",
                            "sense_definition": "Test",
                            "learner_level": "A1",
                            "frequency": "Low",
                            "semantic_group_id": "test"
                        }
                    ]
                }
            ]
        }
        """.trimIndent()

        val exception = assertFailsWith<IllegalArgumentException> {
            builder.ingestProcessedOverRaw(
                processedJson,
                word,
                lang,
                serverDbManager.openDictionary(lang)
            )
        }

        assertEquals(exception.message?.contains("not found"), true, "Error should mention word not found")
    }

    @Test
    fun processed_over_raw_fails_if_already_processed() {
        val outDir = Files.createTempDirectory("processed_over_raw_already_processed_test").toFile()
        val serverDbManager = ServerDbManager(outDir)

        val lang = "nl"
        val word = "programmamaker"

        val frequencyMap = mapOf(word to 3.5)
        val builder = JsonIngestionBuilder({ from, to -> serverDbManager.openTranslation(from, to) }, frequencyMap)

        val rawFile = resourceFile("db_extract/$lang/$word.json")
        val processedFile = resourceFile("processed_json_files/$lang/$word.json")

        // Full ingest (raw + processed together)
        builder.ingest(
            processedFile.readText(),
            rawFile.readText(),
            serverDbManager.openDictionary(lang)
        )

        // Verify it's already processed (online_only = false)
        val dictDb = serverDbManager.openDictionary(lang)
        val dq = dictDb.dictionaryQueries
        val lemmas = dq.selectLemmasByWord(lang, word).executeAsList()
        assertFalse(lemmas.first().online_only, "Should already be processed")

        // Try to add processed over raw - should fail
        val exception = assertFailsWith<IllegalArgumentException> {
            builder.ingestProcessedOverRaw(
                processedFile.readText(),
                word,
                lang,
                serverDbManager.openDictionary(lang)
            )
        }

        assertEquals(
            exception.message?.contains("already has processed data"),
            true,
            "Error should mention already processed: ${exception.message}"
        )
    }

    @Test
    fun translations_only_adds_translations_to_processed_word() {
        val outDir = Files.createTempDirectory("translations_only_test").toFile()
        val serverDbManager = ServerDbManager(outDir)

        val lang = "nl"
        val word = "programmamaker"

        val frequencyMap = mapOf(word to 3.5)
        val builder = JsonIngestionBuilder({ from, to -> serverDbManager.openTranslation(from, to) }, frequencyMap)

        val rawFile = resourceFile("db_extract/$lang/$word.json")
        val processedFile = resourceFile("processed_json_files/$lang/$word.json")

        // Full ingest first
        builder.ingest(
            processedFile.readText(),
            rawFile.readText(),
            serverDbManager.openDictionary(lang)
        )

        val dictDb = serverDbManager.openDictionary(lang)
        val dq = dictDb.dictionaryQueries
        val lemmas = dq.selectLemmasByWord(lang, word).executeAsList()
        val lemmaPosIds = dq.selectLemmaPosIdByLemmaId(lemmas.first().id).executeAsList()
        val senses = dq.selectSensesByLemmaPosId(lemmaPosIds.first()).executeAsList()

        // Create processed JSON with new translations for a new target language (e.g., "uk" for Ukrainian)
        val processedWithNewTranslations = """
        {
            "entries": [
                {
                    "pos": "noun",
                    "senses": [
                        {
                            "sense_id": "${senses.first().sense_id}",
                            "sense_definition": "Test",
                            "learner_level": "B1",
                            "frequency": "Middle",
                            "semantic_group_id": "test",
                            "target_lang_definitions": {
                                "uk": "Людина, яка створює теле- або радіопрограми."
                            },
                            "translations": {
                                "uk": [
                                    {
                                        "target_lang_word": "програмовиробник"
                                    }
                                ]
                            }
                        }
                    ]
                }
            ]
        }
        """.trimIndent()

        // Add translations only
        builder.ingestTranslationsOnly(
            processedWithNewTranslations,
            word,
            lang,
            serverDbManager.openDictionary(lang)
        )

        // Verify new translations were added
        val trDbUk = serverDbManager.openTranslation(lang, "uk")
        val trQUk = trDbUk.translationQueries
        val translationsUk = trQUk.selectSenseTranslationsBySense(
            senses.first().sense_id,
            lang,
            "uk"
        ).executeAsList()

        assertTrue(translationsUk.isNotEmpty(), "Should have Ukrainian translations")
        assertEquals("програмовиробник", translationsUk.first().target_lang_word)

        // Verify definition was added
        val definitions = trQUk.selectDefinitionsBySense(
            senses.first().sense_id,
            lang,
            "uk"
        ).executeAsList()
        assertTrue(definitions.isNotEmpty(), "Should have Ukrainian definition")
    }

    @Test
    fun translations_only_fails_if_word_not_processed() {
        val outDir = Files.createTempDirectory("translations_only_not_processed_test").toFile()
        val serverDbManager = ServerDbManager(outDir)

        val lang = "nl"
        val word = "programmamaker"

        val frequencyMap = mapOf(word to 3.5)
        val builder = JsonIngestionBuilder({ from, to -> serverDbManager.openTranslation(from, to) }, frequencyMap)

        val rawFile = resourceFile("db_extract/$lang/$word.json")

        // Ingest raw only (online_only = true)
        builder.ingestRawOnly(
            rawFile.readText(),
            serverDbManager.openDictionary(lang)
        )

        val processedJson = """
        {
            "entries": [
                {
                    "pos": "noun",
                    "senses": [
                        {
                            "sense_id": "4e9bd9dc-07b8-482c-8b27-275190e59725",
                            "sense_definition": "Test",
                            "learner_level": "B1",
                            "frequency": "Middle",
                            "semantic_group_id": "test",
                            "translations": {
                                "uk": [{"target_lang_word": "test"}]
                            }
                        }
                    ]
                }
            ]
        }
        """.trimIndent()

        val exception = assertFailsWith<IllegalArgumentException> {
            builder.ingestTranslationsOnly(
                processedJson,
                word,
                lang,
                serverDbManager.openDictionary(lang)
            )
        }

        assertEquals(
            exception.message?.contains("online_only"),
            true,
            "Error should mention online_only: ${exception.message}"
        )
    }

    @Test
    fun translations_only_fails_if_lemma_not_found() {
        val outDir = Files.createTempDirectory("translations_only_not_found_test").toFile()
        val serverDbManager = ServerDbManager(outDir)

        val lang = "nl"
        val word = "nonexistentword"

        val frequencyMap = mapOf(word to 1.0)
        val builder = JsonIngestionBuilder({ from, to -> serverDbManager.openTranslation(from, to) }, frequencyMap)

        val processedJson = """
        {
            "entries": [
                {
                    "pos": "noun",
                    "senses": [
                        {
                            "sense_id": "11111111-2222-3333-4444-555555555555",
                            "sense_definition": "Test",
                            "learner_level": "A1",
                            "frequency": "Low",
                            "semantic_group_id": "test"
                        }
                    ]
                }
            ]
        }
        """.trimIndent()

        val exception = assertFailsWith<IllegalArgumentException> {
            builder.ingestTranslationsOnly(
                processedJson,
                word,
                lang,
                serverDbManager.openDictionary(lang)
            )
        }

        assertEquals(exception.message?.contains("not found"), true, "Error should mention word not found")
    }

    @Test
    fun translations_only_routes_multi_cluster_same_pos_senses_by_hint() {
        val outDir = Files.createTempDirectory("translations_only_multi_cluster_test").toFile()
        val serverDbManager = ServerDbManager(outDir)

        val lang = "nl"
        val word = "voorkomen"
        val builder = JsonIngestionBuilder({ from, to -> serverDbManager.openTranslation(from, to) }, mapOf(word to 3.0))

        val processedFile = resourceFile("processed_json_files/$lang/$word.json")
        val rawFile = resourceFile("db_extract/$lang/$word.json")
        val dictDb = serverDbManager.openDictionary(lang)
        val dictQ = dictDb.dictionaryQueries

        builder.ingest(processedFile.readText(), rawFile.readText(), dictDb)

        val lemmaId = dictQ.selectLemmasByWord(lang, word).executeAsList().first().id
        val verbLemmaPos = dictQ.selectLemmaPosByLemmaId(lemmaId).executeAsList()
            .filter { it.pos == DictionaryPos.VERB }
        assertEquals(2, verbLemmaPos.size, "Expected 2 verb lemma_pos rows for '$word'")

        val occurLemmaPosId = verbLemmaPos.firstOrNull { lemmaPos ->
            dictQ.selectFormsWithIdByLemmaPosId(lemmaPos.id).executeAsList().any { it.form == "vóórkomen" }
        }?.id
        val preventLemmaPosId = verbLemmaPos.firstOrNull { lemmaPos ->
            dictQ.selectFormsWithIdByLemmaPosId(lemmaPos.id).executeAsList().any { it.form == "voorkómen" }
        }?.id

        assertNotNull(occurLemmaPosId, "Could not find the 'vóórkomen' verb cluster")
        assertNotNull(preventLemmaPosId, "Could not find the 'voorkómen' verb cluster")

        val occurSenseId = com.slovy.slovymovyapp.ingestion.uuidParse("9a1a8666-81d1-3048-b400-08710a782d3f")
        val preventSenseId = com.slovy.slovymovyapp.ingestion.uuidParse("8b8a7239-f048-3aa6-9f01-0db625accec2")

        assertTrue(
            dictQ.selectSensesByLemmaPosId(occurLemmaPosId).executeAsList().any { it.sense_id == occurSenseId },
            "Expected occur sense to belong to the 'vóórkomen' cluster"
        )
        assertTrue(
            dictQ.selectSensesByLemmaPosId(preventLemmaPosId).executeAsList().any { it.sense_id == preventSenseId },
            "Expected prevent sense to belong to the 'voorkómen' cluster"
        )

        val translationsOnlyJson = """
        {
          "entries": [
            {
              "pos": "verb",
              "senses": [
                {
                  "sense_id": "$occurSenseId",
                  "sense_definition": "test",
                  "learner_level": "A1",
                  "frequency": "High",
                  "semantic_group_id": "occur",
                  "target_lang_definitions": {
                    "uk": "траплятися"
                  },
                  "translations": {
                    "uk": [
                      {
                        "target_lang_word": "траплятися"
                      }
                    ]
                  }
                },
                {
                  "sense_id": "$preventSenseId",
                  "sense_definition": "test",
                  "learner_level": "A1",
                  "frequency": "High",
                  "semantic_group_id": "prevent",
                  "target_lang_definitions": {
                    "uk": "запобігати"
                  },
                  "translations": {
                    "uk": [
                      {
                        "target_lang_word": "запобігати"
                      }
                    ]
                  }
                }
              ]
            }
          ]
        }
        """.trimIndent()

        builder.ingestTranslationsOnly(
            translationsOnlyJson,
            word,
            lang,
            serverDbManager.openDictionary(lang)
        )

        val trDbUk = serverDbManager.openTranslation(lang, "uk")
        val trQUk = trDbUk.translationQueries
        val occurTranslations = trQUk.selectSenseTranslationsBySense(occurSenseId, lang, "uk").executeAsList()
        val preventTranslations = trQUk.selectSenseTranslationsBySense(preventSenseId, lang, "uk").executeAsList()

        assertEquals(1, occurTranslations.size, "Expected one Ukrainian translation for occur sense")
        assertEquals(1, preventTranslations.size, "Expected one Ukrainian translation for prevent sense")
        assertEquals(occurLemmaPosId, occurTranslations.single().lemma_pos_id)
        assertEquals(preventLemmaPosId, preventTranslations.single().lemma_pos_id)
    }

    @Test
    fun processed_over_raw_fails_when_multi_cluster_sense_hint_is_missing() {
        val outDir = Files.createTempDirectory("processed_over_raw_missing_hint_test").toFile()
        val serverDbManager = ServerDbManager(outDir)

        val lang = "nl"
        val word = "voorkomen"
        val builder = JsonIngestionBuilder({ from, to -> serverDbManager.openTranslation(from, to) }, mapOf(word to 3.0))

        val processedFile = resourceFile("processed_json_files/$lang/$word.json")
        val rawFile = resourceFile("db_extract/$lang/$word.json")

        builder.ingestRawOnly(
            rawFile.readText(),
            serverDbManager.openDictionary(lang)
        )

        val occurSenseId = com.slovy.slovymovyapp.ingestion.uuidParse("9a1a8666-81d1-3048-b400-08710a782d3f")
        deleteLemmaPosHint(serverDbManager, lang, occurSenseId)

        val exception = assertFailsWith<IllegalStateException> {
            builder.ingestProcessedOverRaw(
                processedFile.readText(),
                word,
                lang,
                serverDbManager.openDictionary(lang)
            )
        }

        assertEquals(
            true,
            exception.message?.contains("Missing lemma_pos hint") == true,
            "Error should explain that the multi-cluster sense hint is missing"
        )
    }

    @Test
    fun translations_only_fails_when_multi_cluster_sense_hint_is_missing() {
        val outDir = Files.createTempDirectory("translations_only_missing_hint_test").toFile()
        val serverDbManager = ServerDbManager(outDir)

        val lang = "nl"
        val word = "voorkomen"
        val builder = JsonIngestionBuilder({ from, to -> serverDbManager.openTranslation(from, to) }, mapOf(word to 3.0))

        val processedFile = resourceFile("processed_json_files/$lang/$word.json")
        val rawFile = resourceFile("db_extract/$lang/$word.json")
        val dictDb = serverDbManager.openDictionary(lang)

        builder.ingest(processedFile.readText(), rawFile.readText(), dictDb)

        val occurSenseId = com.slovy.slovymovyapp.ingestion.uuidParse("9a1a8666-81d1-3048-b400-08710a782d3f")
        val preventSenseId = com.slovy.slovymovyapp.ingestion.uuidParse("8b8a7239-f048-3aa6-9f01-0db625accec2")
        deleteLemmaPosHint(serverDbManager, lang, occurSenseId)

        val exception = assertFailsWith<IllegalStateException> {
            builder.ingestTranslationsOnly(
                translationsOnlyJson(occurSenseId.toString(), preventSenseId.toString()),
                word,
                lang,
                serverDbManager.openDictionary(lang)
            )
        }

        assertEquals(
            true,
            exception.message?.contains("Missing lemma_pos hint") == true,
            "Error should explain that the multi-cluster sense hint is missing"
        )
    }

    private fun resourceFile(path: String): File {
        val cl = Thread.currentThread().contextClassLoader
        val url = cl.getResource(path) ?: error("Resource not found: $path")
        return File(url.toURI())
    }

    private fun deleteLemmaPosHint(serverDbManager: ServerDbManager, lang: String, senseId: kotlin.uuid.Uuid) {
        val hexSenseId = senseId.toByteArray().joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        JdbcSqliteDriver("jdbc:sqlite:${serverDbManager.dictionaryDbFile(lang).absolutePath}").use { driver ->
            driver.execute(
                identifier = null,
                sql = "DELETE FROM lemma_pos_sense_hint WHERE sense_id = X'$hexSenseId'",
                parameters = 0
            )
        }
    }

    private fun translationsOnlyJson(occurSenseId: String, preventSenseId: String): String = """
        {
          "entries": [
            {
              "pos": "verb",
              "senses": [
                {
                  "sense_id": "$occurSenseId",
                  "sense_definition": "test",
                  "learner_level": "A1",
                  "frequency": "High",
                  "semantic_group_id": "occur",
                  "target_lang_definitions": {
                    "uk": "траплятися"
                  },
                  "translations": {
                    "uk": [
                      {
                        "target_lang_word": "траплятися"
                      }
                    ]
                  }
                },
                {
                  "sense_id": "$preventSenseId",
                  "sense_definition": "test",
                  "learner_level": "A1",
                  "frequency": "High",
                  "semantic_group_id": "prevent",
                  "target_lang_definitions": {
                    "uk": "запобігати"
                  },
                  "translations": {
                    "uk": [
                      {
                        "target_lang_word": "запобігати"
                      }
                    ]
                  }
                }
              ]
            }
          ]
        }
    """.trimIndent()
}
