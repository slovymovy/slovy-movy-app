package com.slovy.slovymovyapp.repo

import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.dictionary.DictionaryPos
import com.slovy.slovymovyapp.data.dictionary.FormSource
import com.slovy.slovymovyapp.data.dictionary.LearnerLevel
import com.slovy.slovymovyapp.data.dictionary.SenseFrequency
import com.slovy.slovymovyapp.data.favorites.FavoritesRepository
import com.slovy.slovymovyapp.data.db.DatabaseProvider
import com.slovy.slovymovyapp.data.local.LocalDbManager
import com.slovy.slovymovyapp.data.remote.DataDbManager
import com.slovy.slovymovyapp.data.remote.DictionaryRepository
import com.slovy.slovymovyapp.data.remote.PartOfSpeech
import com.slovy.slovymovyapp.data.settings.Setting
import com.slovy.slovymovyapp.data.settings.SettingsRepository
import com.slovy.slovymovyapp.test.BaseTest
import com.slovy.slovymovyapp.test.IgnoreIos
import com.slovy.slovymovyapp.test.testPlatformDbSupport
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.*
import kotlin.uuid.Uuid

// TODO: we use HTTP for now to workaround some issues with IOS emulator
// https://github.com/slovymovy/slovy-movy-app/issues/34
@IgnoreIos
class DictionaryRepositoryTest : BaseTest() {

    private fun favoritesRepository(): FavoritesRepository {
        return FavoritesRepository(testAppDatabaseHolder().database)
    }

    private fun settingsRepository(): SettingsRepository {
        return SettingsRepository(testAppDatabaseHolder().database)
    }

    @Test
    fun download_en_ru_and_search_test() {
        val platform = testPlatformDbSupport()
        val mgr = testDataDbManager()

        // Ensure a clean state
        runBlocking {
            mgr.deleteDictionary(Language.ENGLISH)
            mgr.deleteTranslation(Language.ENGLISH, Language.RUSSIAN)
        }

        // Download actual English dictionary and English->Russian translation
        val dictPath = runBlocking { mgr.ensureDictionary(Language.ENGLISH) }
        val trPath = runBlocking { mgr.ensureTranslation(Language.ENGLISH, Language.RUSSIAN) }

        try {
            assertTrue(platform.fileExists(dictPath), "Dictionary file should exist: $dictPath")
            assertTrue(platform.fileExists(trPath), "Translation file should exist: $trPath")

            val favoritesRepo = favoritesRepository()
            val localMgr = testLocalDbManager()
            val repo = DictionaryRepository(mgr, localMgr, favoritesRepo, settingsRepository())

            // Verify installed sets reflect downloads
            assertTrue(repo.installedDictionaries().contains(Language.ENGLISH), "'en' dictionary should be installed")
            assertTrue(
                repo.installedTranslationTargets(Language.ENGLISH).contains(Language.RUSSIAN),
                "'ru' should be an installed translation target for 'en'"
            )

            // Search for 'test' in the English dictionary
            val results = runBlocking { repo.search("bu", dictionaryLanguage = Language.ENGLISH) }
            assertTrue(results.isNotEmpty(), "Expected at least one search result for 'bu'")
            val first = results.first()
            assertTrue(first.display.contains("bu", ignoreCase = true), "First result display should mention 'bu'")

            // Build a language card for the first result's lemma to ensure repository wiring works on real data
            val card = runBlocking { repo.getLanguageCard(Language.ENGLISH, first.lemma) }
            assertNotNull(card, "Language card should be built for a real lemma from the English dictionary")
            assertTrue(card.entries.isNotEmpty(), "Language card should have at least one entry")
            assertEquals(first.lemma, card.lemma, "Card lemma should match search result")
            assertTrue(card.zipfFrequency >= 0.0f, "Zipf frequency should be non-negative")
        } finally {
            // Clean up downloaded files to keep test environment tidy
            runBlocking {
                mgr.deleteDictionary(Language.ENGLISH)
                mgr.deleteTranslation(Language.ENGLISH, Language.RUSSIAN)
            }
        }
    }

    @Test
    fun nl_tegengesteld_should_return_both_noun_and_verb() {
        val platform = testPlatformDbSupport()
        val mgr = testDataDbManager()

        // Ensure a clean state
        runBlocking {
            mgr.deleteDictionary(Language.DUTCH)
        }

        val dictPath = runBlocking { mgr.ensureDictionary(Language.DUTCH) }
        try {
            assertTrue(platform.fileExists(dictPath), "Dictionary file should exist: $dictPath")

            val favoritesRepo = favoritesRepository()
            val localMgr = testLocalDbManager()
            val repo = DictionaryRepository(mgr, localMgr, favoritesRepo, settingsRepository())
            assertTrue(repo.installedDictionaries().contains(Language.DUTCH), "'nl' dictionary should be installed")

            val card = runBlocking { repo.getLanguageCard(Language.DUTCH, "tegengesteld") }
            assertNotNull(card, "Language card should be built for 'tegengesteld'")
            val poses = card.entries.map { it.pos }.toSet()
            assertTrue(poses.contains(PartOfSpeech.ADJECTIVE), "Expected ADJECTIVE entry for 'tegengesteld'")
            assertTrue(poses.contains(PartOfSpeech.VERB), "Expected VERB entry for 'tegengesteld'")
            assertTrue(card.zipfFrequency >= 0.0f, "Zipf frequency should be non-negative")
        } finally {
            // Clean up
            runBlocking {
                mgr.deleteDictionary(Language.DUTCH)
            }
        }
    }

    @Test
    fun search_returns_multiple_forms_for_same_lemma() {
        val platform = testPlatformDbSupport()
        val mgr = testDataDbManager()

        // Ensure a clean state
        runBlocking {
            mgr.deleteDictionary(Language.ENGLISH)
        }

        val dictPath = runBlocking { mgr.ensureDictionary(Language.ENGLISH) }
        try {
            assertTrue(platform.fileExists(dictPath), "Dictionary file should exist: $dictPath")

            val favoritesRepo = favoritesRepository()
            val localMgr = testLocalDbManager()
            val repo = DictionaryRepository(mgr, localMgr, favoritesRepo, settingsRepository())
            assertTrue(repo.installedDictionaries().contains(Language.ENGLISH), "'en' dictionary should be installed")

            // Search for "bu" which should match "bu" and its forms
            val results = runBlocking { repo.search("bu", dictionaryLanguage = Language.ENGLISH) }
            assertTrue(results.isNotEmpty(), "Expected at least one search result for 'bu'")

            // Find form results (any form-based results)
            val formResults = results.filter { it.display.contains("form of", ignoreCase = true) }

            // We expect at least some forms to be returned across all results
            // The key test: verify that forms have unique display strings (no duplicates due to deduplication bug)
            if (formResults.isNotEmpty()) {
                val displayStrings = formResults.map { it.display }.toSet()
                assertEquals(
                    formResults.size,
                    displayStrings.size,
                    "Expected all form results to have unique display strings (no duplicates). " +
                            "Found ${formResults.size} forms but only ${displayStrings.size} unique. " +
                            "Forms: ${formResults.map { it.display }}"
                )
            }
        } finally {
            // Clean up
            runBlocking {
                mgr.deleteDictionary(Language.ENGLISH)
            }
        }
    }

    @Test
    fun search_suppresses_forms_when_lemma_present() {
        val platform = testPlatformDbSupport()
        val mgr = testDataDbManager()

        // Ensure a clean state
        runBlocking {
            mgr.deleteDictionary(Language.ENGLISH)
        }

        val dictPath = runBlocking { mgr.ensureDictionary(Language.ENGLISH) }
        try {
            assertTrue(platform.fileExists(dictPath), "Dictionary file should exist: $dictPath")

            val favoritesRepo = favoritesRepository()
            val localMgr = testLocalDbManager()
            val repo = DictionaryRepository(mgr, localMgr, favoritesRepo, settingsRepository())
            assertTrue(repo.installedDictionaries().contains(Language.ENGLISH), "'en' dictionary should be installed")

            // Search for "test" - should match the base lemma "test"
            val results = runBlocking { repo.search("bu", dictionaryLanguage = Language.ENGLISH) }
            assertTrue(results.isNotEmpty(), "Expected at least one search result for 'bu'")

            // Find the base lemma "bu"
            val testLemma = results.firstOrNull {
                it.display.equals("bu", ignoreCase = true) &&
                        !it.display.contains("form of", ignoreCase = true)
            }

            // If the base lemma "bu present, no forms of "bu" should be shown
            if (testLemma != null) {
                val testForms = results.filter {
                    it.display.contains("form of", ignoreCase = true) &&
                            it.display.contains("\"bu\"", ignoreCase = true)
                }
                assertTrue(
                    testForms.isEmpty(),
                    "Expected no forms of 'bu' when base lemma is present, but found: ${testForms.map { it.display }}"
                )
            }
        } finally {
            // Clean up
            runBlocking {
                mgr.deleteDictionary(Language.ENGLISH)
            }
        }
    }

    @Test
    fun related_words_includes_synonyms_antonyms_and_family() {
        val platform = testPlatformDbSupport()
        val mgr = testDataDbManager()

        // Ensure a clean state
        runBlocking {
            mgr.deleteDictionary(Language.ENGLISH)
            mgr.deleteTranslation(Language.ENGLISH, Language.RUSSIAN)
        }

        val dictPath = runBlocking { mgr.ensureDictionary(Language.ENGLISH) }
        val trPath = runBlocking { mgr.ensureTranslation(Language.ENGLISH, Language.RUSSIAN) }

        try {
            assertTrue(platform.fileExists(dictPath), "Dictionary file should exist: $dictPath")
            assertTrue(platform.fileExists(trPath), "Translation file should exist: $trPath")

            val favoritesRepo = favoritesRepository()
            val localMgr = testLocalDbManager()
            val repo = DictionaryRepository(mgr, localMgr, favoritesRepo, settingsRepository())
            assertTrue(repo.installedDictionaries().contains(Language.ENGLISH), "'en' dictionary should be installed")

            runBlocking {
                val card = repo.getLanguageCard(Language.ENGLISH, "simultaneously")!!
                assertTrue(card.relatedWords.isNotEmpty(), "Related words should not be empty for 'simultaneously'")
                assertTrue { card.relatedWords.contains("concurrently") }
            }
        } finally {
            // Clean up
            runBlocking {
                mgr.deleteDictionary(Language.ENGLISH)
                mgr.deleteTranslation(Language.ENGLISH, Language.RUSSIAN)
            }
        }
    }

    @Test
    fun getLanguageCard_from_local_when_ro_missing() {
        val platform = testPlatformDbSupport()
        val mgr = testDataDbManager()
        val localMgr = testLocalDbManager()

        // Ensure RO dictionary does NOT exist
        runBlocking { mgr.deleteDictionary(Language.ENGLISH) }

        // Clean local DB files first
        val localDictPath = platform.getDatabasePath(LocalDbManager.LOCAL_DICTIONARY_FILENAME)
        if (platform.fileExists(localDictPath)) {
            platform.deleteFile(localDictPath)
        }

        try {
            // Insert test data into local DB
            val localDb = localMgr.openLocalDictionary()
            val q = localDb.dictionaryQueries

            val lemmaId = Uuid.random()
            val lemmaPosId = Uuid.random()
            val senseId = Uuid.random()

            q.insertLemma(lemmaId, "en", "localword", "localword", 5.0, false)
            q.insertLemmaPos(lemmaPosId, lemmaId, DictionaryPos.NOUN)
            q.insertSense(
                sense_id = senseId,
                lemma_pos_id = lemmaPosId,
                sense_definition = "A test definition from local DB",
                learner_level = LearnerLevel.B1,
                frequency = SenseFrequency.MIDDLE,
                semantic_group_id = "group1",
                name_type = null
            )

            val favoritesRepo = favoritesRepository()
            val repo = DictionaryRepository(mgr, localMgr, favoritesRepo, settingsRepository())

            // Verify RO dictionary doesn't exist
            assertFalse(mgr.hasDictionary(Language.ENGLISH), "RO dictionary should not exist")

            // Should load from local since RO doesn't exist
            val card = runBlocking { repo.getLanguageCard(Language.ENGLISH, "localword") }
            assertNotNull(card, "Should load card from local DB when RO missing")
            assertEquals("localword", card.lemma, "Card lemma should match")
            assertTrue(card.entries.any { it.pos == PartOfSpeech.NOUN }, "Should have NOUN entry")
            assertTrue(
                card.entries.flatMap { it.senses }.any { it.senseDefinition.contains("local DB") },
                "Should have sense definition from local DB"
            )
        } finally {
            localMgr.closeAll()
            if (platform.fileExists(localDictPath)) {
                platform.deleteFile(localDictPath)
            }
        }
    }

    @Test
    fun getLanguageCard_loads_translations_from_local_when_ro_translation_missing() {
        val platform = testPlatformDbSupport()
        val mgr = testDataDbManager()
        val localMgr = testLocalDbManager()

        // Ensure a clean state
        runBlocking {
            mgr.deleteDictionary(Language.ENGLISH)
            mgr.deleteTranslation(Language.ENGLISH, Language.RUSSIAN)
        }

        // Clean local translation DB
        val localTransPath = platform.getDatabasePath(LocalDbManager.LOCAL_TRANSLATION_FILENAME)
        if (platform.fileExists(localTransPath)) {
            platform.deleteFile(localTransPath)
        }

        // Download RO dictionary but NOT translation
        val dictPath = runBlocking { mgr.ensureDictionary(Language.ENGLISH) }

        try {
            assertTrue(platform.fileExists(dictPath), "Dictionary file should exist: $dictPath")

            // Verify RO translation doesn't exist
            assertFalse(mgr.hasTranslation(Language.ENGLISH, Language.RUSSIAN), "RO translation should not exist")

            // Get a real sense ID from the RO dictionary
            val roDb = runBlocking { mgr.openDictionaryReadOnly(Language.ENGLISH) }
            val lemmaRow = roDb.dictionaryQueries.selectLemmasByWord("en", "simultaneously")
                .executeAsList().firstOrNull()
            assertNotNull(lemmaRow, "Should find 'simultaneously' in RO dictionary")

            val lemmaPosIds = roDb.dictionaryQueries.selectLemmaPosIdByLemmaId(lemmaRow.id).executeAsList()
            assertTrue(lemmaPosIds.isNotEmpty(), "Should have lemma_pos entries")

            val senses = roDb.dictionaryQueries.selectSensesByLemmaPosId(lemmaPosIds.first()).executeAsList()
            assertTrue(senses.isNotEmpty(), "Should have senses")

            val senseId = senses.first().sense_id

            // Insert local definition and translation for this sense
            val localTransDb = localMgr.openLocalTranslation()
            val tq = localTransDb.translationQueries

            // Must insert definition first - code requires definition to load translations
            tq.insertSenseTargetDefinition(
                sense_id = senseId,
                from_lang_code = "en",
                target_lang_code = "ru",
                definition = "Локальное определение"
            )

            tq.insertSenseTranslation(
                sense_id = senseId,
                from_lang_code = "en",
                target_lang_code = "ru",
                idx = 0,
                target_lang_word = "ТестЛокал",
                target_lang_word_normalized = "тестлокал",
                target_lang_sense_clarification = null,
                lemma_id = lemmaRow.id,
                lemma_pos_id = lemmaPosIds.first()
            )

            val favoritesRepo = favoritesRepository()
            val repo = DictionaryRepository(mgr, localMgr, favoritesRepo, settingsRepository())

            val card = runBlocking {
                repo.getLanguageCard(Language.ENGLISH, "simultaneously", listOf(Language.RUSSIAN))
            }
            assertNotNull(card, "Should load card")

            // Find sense with Russian translation from local
            val senseWithTranslation = card.entries.flatMap { it.senses }
                .find { it.translations[Language.RUSSIAN]?.isNotEmpty() == true }
            assertNotNull(senseWithTranslation, "Should have Russian translation from local DB")
            assertEquals(
                "ТестЛокал",
                senseWithTranslation.translations[Language.RUSSIAN]?.first()?.targetLangWord,
                "Translation should come from local DB"
            )
        } finally {
            runBlocking { mgr.deleteDictionary(Language.ENGLISH) }
            localMgr.closeAll()
            if (platform.fileExists(localTransPath)) {
                platform.deleteFile(localTransPath)
            }
        }
    }

    @Test
    fun getSenses_uses_settings_targets_for_local_translations_without_definition() {
        val platform = testPlatformDbSupport()
        val mgr = testDataDbManager()
        val localMgr = testLocalDbManager()

        runBlocking {
            mgr.deleteDictionary(Language.ENGLISH)
            mgr.deleteTranslation(Language.ENGLISH, Language.RUSSIAN)
        }

        val localDictPath = platform.getDatabasePath(LocalDbManager.LOCAL_DICTIONARY_FILENAME)
        val localTransPath = platform.getDatabasePath(LocalDbManager.LOCAL_TRANSLATION_FILENAME)
        if (platform.fileExists(localDictPath)) {
            platform.deleteFile(localDictPath)
        }
        if (platform.fileExists(localTransPath)) {
            platform.deleteFile(localTransPath)
        }

        try {
            val lemmaId = Uuid.random()
            val lemmaPosId = Uuid.random()
            val senseId = Uuid.random()

            val localDictDb = localMgr.openLocalDictionary()
            localDictDb.dictionaryQueries.insertLemma(lemmaId, "en", "localfavorite", "localfavorite", 5.0, false)
            localDictDb.dictionaryQueries.insertLemmaPos(lemmaPosId, lemmaId, DictionaryPos.NOUN)
            localDictDb.dictionaryQueries.insertSense(
                sense_id = senseId,
                lemma_pos_id = lemmaPosId,
                sense_definition = "A favorite stored locally",
                learner_level = LearnerLevel.B1,
                frequency = SenseFrequency.MIDDLE,
                semantic_group_id = "group1",
                name_type = null
            )

            val localTransDb = localMgr.openLocalTranslation()
            localTransDb.translationQueries.insertSenseTranslation(
                sense_id = senseId,
                from_lang_code = "en",
                target_lang_code = "ru",
                idx = 0,
                target_lang_word = "Локальный",
                target_lang_word_normalized = "локальный",
                target_lang_sense_clarification = null,
                lemma_id = lemmaId,
                lemma_pos_id = lemmaPosId
            )

            val settingsRepo = settingsRepository()
            runBlocking {
                settingsRepo.insert(
                    Setting(
                        id = Setting.Name.LANGUAGE,
                        value = JsonArray(listOf(JsonPrimitive("ru")))
                    )
                )
            }

            val repo = DictionaryRepository(mgr, localMgr, favoritesRepository(), settingsRepo)
            val loaded = runBlocking {
                repo.getSenses(Language.ENGLISH, "localfavorite", setOf(senseId.toString()))
            }

            val sense = loaded[senseId.toString()]?.sense
            assertNotNull(sense, "Should load the local favorite sense")
            assertEquals(
                "Локальный",
                sense.translations[Language.RUSSIAN]?.firstOrNull()?.targetLangWord,
                "Should load local translations using settings targets even when no target definition exists"
            )
        } finally {
            localMgr.closeAll()
            if (platform.fileExists(localDictPath)) {
                platform.deleteFile(localDictPath)
            }
            if (platform.fileExists(localTransPath)) {
                platform.deleteFile(localTransPath)
            }
        }
    }

    @Test
    fun getSenses_respects_empty_translation_language_setting() {
        val platform = testPlatformDbSupport()
        val mgr = testDataDbManager()
        val localMgr = testLocalDbManager()

        runBlocking {
            mgr.deleteDictionary(Language.ENGLISH)
            mgr.deleteTranslation(Language.ENGLISH, Language.RUSSIAN)
        }

        val localDictPath = platform.getDatabasePath(LocalDbManager.LOCAL_DICTIONARY_FILENAME)
        val localTransPath = platform.getDatabasePath(LocalDbManager.LOCAL_TRANSLATION_FILENAME)
        if (platform.fileExists(localDictPath)) {
            platform.deleteFile(localDictPath)
        }
        if (platform.fileExists(localTransPath)) {
            platform.deleteFile(localTransPath)
        }

        try {
            val lemmaId = Uuid.random()
            val lemmaPosId = Uuid.random()
            val senseId = Uuid.random()

            val localDictDb = localMgr.openLocalDictionary()
            localDictDb.dictionaryQueries.insertLemma(lemmaId, "en", "emptytargets", "emptytargets", 5.0, false)
            localDictDb.dictionaryQueries.insertLemmaPos(lemmaPosId, lemmaId, DictionaryPos.NOUN)
            localDictDb.dictionaryQueries.insertSense(
                sense_id = senseId,
                lemma_pos_id = lemmaPosId,
                sense_definition = "A favorite with disabled translations",
                learner_level = LearnerLevel.B1,
                frequency = SenseFrequency.MIDDLE,
                semantic_group_id = "group1",
                name_type = null
            )

            val localTransDb = localMgr.openLocalTranslation()
            localTransDb.translationQueries.insertSenseTranslation(
                sense_id = senseId,
                from_lang_code = "en",
                target_lang_code = "ru",
                idx = 0,
                target_lang_word = "Скрытый",
                target_lang_word_normalized = "скрытый",
                target_lang_sense_clarification = null,
                lemma_id = lemmaId,
                lemma_pos_id = lemmaPosId
            )

            val settingsRepo = settingsRepository()
            runBlocking {
                settingsRepo.insert(
                    Setting(
                        id = Setting.Name.LANGUAGE,
                        value = JsonArray(emptyList())
                    )
                )
            }

            val repo = DictionaryRepository(mgr, localMgr, favoritesRepository(), settingsRepo)
            val loaded = runBlocking {
                repo.getSenses(Language.ENGLISH, "emptytargets", setOf(senseId.toString()))
            }

            val sense = loaded[senseId.toString()]?.sense
            assertNotNull(sense, "Should still load the local favorite sense")
            assertTrue(
                sense.translations.isEmpty(),
                "Explicit empty translation-language setting should not fall back to installed or local targets"
            )
        } finally {
            localMgr.closeAll()
            if (platform.fileExists(localDictPath)) {
                platform.deleteFile(localDictPath)
            }
            if (platform.fileExists(localTransPath)) {
                platform.deleteFile(localTransPath)
            }
        }
    }

    @Test
    fun search_finds_local_lemmas_first() {
        val platform = testPlatformDbSupport()
        val mgr = testDataDbManager()
        val localMgr = testLocalDbManager()

        // Ensure RO dictionary does NOT exist
        runBlocking { mgr.deleteDictionary(Language.ENGLISH) }

        // Clean local DB files first
        val localDictPath = platform.getDatabasePath(LocalDbManager.LOCAL_DICTIONARY_FILENAME)
        if (platform.fileExists(localDictPath)) {
            platform.deleteFile(localDictPath)
        }

        try {
            // Insert test data into local DB
            val localDb = localMgr.openLocalDictionary()
            val q = localDb.dictionaryQueries

            val lemmaId = Uuid.random()
            val lemmaPosId = Uuid.random()
            val senseId = Uuid.random()

            q.insertLemma(lemmaId, "en", "localsearchword", "localsearchword", 5.0, false)
            q.insertLemmaPos(lemmaPosId, lemmaId, DictionaryPos.NOUN)
            q.insertSense(
                sense_id = senseId,
                lemma_pos_id = lemmaPosId,
                sense_definition = "A test definition from local DB",
                learner_level = LearnerLevel.B1,
                frequency = SenseFrequency.MIDDLE,
                semantic_group_id = "group1",
                name_type = null
            )

            val favoritesRepo = favoritesRepository()
            val repo = DictionaryRepository(mgr, localMgr, favoritesRepo, settingsRepository())

            // Verify RO dictionary doesn't exist
            assertFalse(mgr.hasDictionary(Language.ENGLISH), "RO dictionary should not exist")

            // Search should find the local lemma
            val results = runBlocking { repo.search("localsearch", dictionaryLanguage = Language.ENGLISH) }
            assertTrue(results.isNotEmpty(), "Should find local lemma in search")

            val found = results.first()
            assertEquals("localsearchword", found.lemma, "Should find the correct lemma")
            assertFalse(found.onlineOnly, "Local lemma should not be marked as online only")
            assertEquals(Language.ENGLISH, found.language, "Should be English language")
        } finally {
            localMgr.closeAll()
            if (platform.fileExists(localDictPath)) {
                platform.deleteFile(localDictPath)
            }
        }
    }

    @Test
    fun search_deduplicates_local_and_ro_results() {
        val platform = testPlatformDbSupport()
        val mgr = testDataDbManager()
        val localMgr = testLocalDbManager()

        // Ensure a clean state
        runBlocking { mgr.deleteDictionary(Language.ENGLISH) }

        // Clean local DB files first
        val localDictPath = platform.getDatabasePath(LocalDbManager.LOCAL_DICTIONARY_FILENAME)
        if (platform.fileExists(localDictPath)) {
            platform.deleteFile(localDictPath)
        }

        // Download RO dictionary
        val dictPath = runBlocking { mgr.ensureDictionary(Language.ENGLISH) }

        try {
            assertTrue(platform.fileExists(dictPath), "Dictionary file should exist: $dictPath")

            // Get a real lemma from RO dictionary
            val roDb = runBlocking { mgr.openDictionaryReadOnly(Language.ENGLISH) }
            val roLemma = roDb.dictionaryQueries.selectLemmasByWord("en", "simultaneously")
                .executeAsList().firstOrNull()
            assertNotNull(roLemma, "Should find 'simultaneously' in RO dictionary")

            // Insert the SAME lemma into local DB (simulating cached/offline data)
            val localDb = localMgr.openLocalDictionary()
            val q = localDb.dictionaryQueries
            val lemmaPosId = Uuid.random()
            val senseId = Uuid.random()

            // Use the same lemma ID to simulate the same word cached locally
            q.insertLemma(roLemma.id, "en", "simultaneously", "simultaneously", 6.0, false)
            q.insertLemmaPos(lemmaPosId, roLemma.id, DictionaryPos.ADVERB)
            q.insertSense(
                sense_id = senseId,
                lemma_pos_id = lemmaPosId,
                sense_definition = "Local definition",
                learner_level = LearnerLevel.B1,
                frequency = SenseFrequency.MIDDLE,
                semantic_group_id = "group1",
                name_type = null
            )

            val favoritesRepo = favoritesRepository()
            val repo = DictionaryRepository(mgr, localMgr, favoritesRepo, settingsRepository())

            // Search for "simultaneously"
            val results = runBlocking { repo.search("simultaneously", dictionaryLanguage = Language.ENGLISH) }
            assertTrue(results.isNotEmpty(), "Should find results for 'simultaneously'")

            // Count how many results have display="simultaneously" (exact lemma match)
            val exactMatches = results.filter { it.display == "simultaneously" }
            assertEquals(1, exactMatches.size, "Should have exactly one 'simultaneously' result (deduplicated)")

            // The result should be from local (not online only) since local is searched first
            val testResult = exactMatches.first()
            assertFalse(testResult.onlineOnly, "Result should come from local DB (not online only)")
        } finally {
            runBlocking { mgr.deleteDictionary(Language.ENGLISH) }
            localMgr.closeAll()
            if (platform.fileExists(localDictPath)) {
                platform.deleteFile(localDictPath)
            }
        }
    }

    @Test
    fun getWordSuggestions_returns_empty_when_no_dictionary() {
        val mgr = testDataDbManager()
        val localMgr = testLocalDbManager()

        // Ensure no dictionary exists
        runBlocking { mgr.deleteDictionary(Language.ENGLISH) }

        val favoritesRepo = favoritesRepository()
        val repo = DictionaryRepository(mgr, localMgr, favoritesRepo, settingsRepository())

        val suggestions = runBlocking { repo.getWordSuggestions(Language.ENGLISH, offset = 5) }
        assertTrue(suggestions.isEmpty(), "Should return empty list when no dictionary installed")
    }

    @Test
    fun getWordSuggestions_returns_high_frequency_words() {
        val platform = testPlatformDbSupport()
        val mgr = testDataDbManager()
        val localMgr = testLocalDbManager()

        // Ensure a clean state
        runBlocking { mgr.deleteDictionary(Language.ENGLISH) }

        val dictPath = runBlocking { mgr.ensureDictionary(Language.ENGLISH) }

        try {
            assertTrue(platform.fileExists(dictPath), "Dictionary file should exist: $dictPath")

            val favoritesRepo = favoritesRepository()
            val repo = DictionaryRepository(mgr, localMgr, favoritesRepo, settingsRepository())

            val suggestions = runBlocking { repo.getWordSuggestions(Language.ENGLISH, offset = 5) }

            assertEquals(5, suggestions.size, "Should return 5 suggestions")
            assertTrue(suggestions.all { it.isNotEmpty() }, "All suggestions should be non-empty strings")
        } finally {
            runBlocking { mgr.deleteDictionary(Language.ENGLISH) }
        }
    }

    @Test
    fun getWordSuggestions_excludes_favorites() {
        val platform = testPlatformDbSupport()
        val mgr = testDataDbManager()
        val localMgr = testLocalDbManager()

        // Ensure a clean state
        runBlocking { mgr.deleteDictionary(Language.ENGLISH) }

        val dictPath = runBlocking { mgr.ensureDictionary(Language.ENGLISH) }

        try {
            assertTrue(platform.fileExists(dictPath), "Dictionary file should exist: $dictPath")

            val favoritesRepo = favoritesRepository()
            val repo = DictionaryRepository(mgr, localMgr, favoritesRepo, settingsRepository())

            // Get initial suggestions
            val initialSuggestions = runBlocking { repo.getWordSuggestions(Language.ENGLISH, offset = 5) }
            assertTrue(initialSuggestions.isNotEmpty(), "Should have initial suggestions")

            // Add the first suggestion to favorites (using a stable UUID since FavoritesRepository syncs cards by ID)
            val wordToFavorite = initialSuggestions.first()
            runBlocking { favoritesRepo.add("00000000-0000-0000-0000-000000000999", Language.ENGLISH, wordToFavorite) }

            // Get suggestions again
            val newSuggestions = runBlocking { repo.getWordSuggestions(Language.ENGLISH, offset = 5) }

            assertFalse(
                newSuggestions.any { it.equals(wordToFavorite, ignoreCase = true) },
                "Favorited word '$wordToFavorite' should not appear in suggestions"
            )
            assertEquals(5, newSuggestions.size, "Should still return 5 suggestions after excluding favorite")
        } finally {
            runBlocking { mgr.deleteDictionary(Language.ENGLISH) }
        }
    }

    @Test
    fun loadRelatedWords_does_not_redirect_lemma_to_parent_when_also_a_form() {
        // "vergieten" is both a standalone lemma (verb, online-only) and a form of "vergiet" (noun plural).
        // When viewing "vergiet", the family chip for "vergieten" must navigate to the verb lemma
        // "vergieten", not be hijacked by the form-fallback into navigating back to "vergiet".
        val platform = testPlatformDbSupport()
        val mgr = testDataDbManager()
        val localMgr = testLocalDbManager()

        runBlocking { mgr.deleteDictionary(Language.DUTCH) }

        val localDictPath = platform.getDatabasePath(LocalDbManager.LOCAL_DICTIONARY_FILENAME)
        if (platform.fileExists(localDictPath)) platform.deleteFile(localDictPath)

        try {
            val q = localMgr.openLocalDictionary().dictionaryQueries

            // "vergiet" (noun, offline) — the card we request; "vergieten" is its plural form
            // and also its family member.  Higher zipf so it wins the form query ORDER BY.
            val nounId = Uuid.random()
            val nounPosId = Uuid.random()
            q.insertLemma(nounId, "nl", "vergiet", "vergiet", 5.0, false)
            q.insertLemmaPos(nounPosId, nounId, DictionaryPos.NOUN)
            q.insertSense(
                sense_id = Uuid.random(),
                lemma_pos_id = nounPosId,
                sense_definition = "colander",
                learner_level = LearnerLevel.A2,
                frequency = SenseFrequency.MIDDLE,
                semantic_group_id = "g1",
                name_type = null
            )
            q.insertForm(Uuid.random(), nounPosId, "vergieten", "vergieten", FormSource.NATIVE)
            q.insertLemmaWordFamily(nounId, "vergieten")

            // "vergieten" (verb, online-only) — a standalone lemma that shares its text with the
            // noun's plural form.  Online-only so it would previously trigger the fallback.
            val verbId = Uuid.random()
            val verbPosId = Uuid.random()
            q.insertLemma(verbId, "nl", "vergieten", "vergieten", 4.0, true)
            q.insertLemmaPos(verbPosId, verbId, DictionaryPos.VERB)
            q.insertSense(
                sense_id = Uuid.random(),
                lemma_pos_id = verbPosId,
                sense_definition = "to spill",
                learner_level = LearnerLevel.B1,
                frequency = SenseFrequency.MIDDLE,
                semantic_group_id = "g2",
                name_type = null
            )

            val repo = DictionaryRepository(mgr, localMgr, favoritesRepository(), settingsRepository())
            val card = runBlocking { repo.getLanguageCard(Language.DUTCH, "vergiet") }
            assertNotNull(card, "Card should be built for 'vergiet'")

            val resolved = card.relatedWords["vergieten"]
            assertNotNull(resolved, "'vergieten' must be present in relatedWords of 'vergiet'")
            assertEquals(
                "vergieten", resolved.lemma,
                "Online-only lemma 'vergieten' must not be redirected by form-fallback to parent 'vergiet'"
            )
        } finally {
            localMgr.closeAll()
            if (platform.fileExists(localDictPath)) platform.deleteFile(localDictPath)
        }
    }

    @Test
    fun loadRelatedWords_local_standalone_lemma_overrides_ro_form_fallback() {
        // "vergieten" appears only as a form of "vergiet" in the RO database (no standalone lemma
        // there), and as a standalone offline lemma in the local database.
        // The RO-first pass fires the form-fallback and writes an offline result pointing to
        // "vergiet". The subsequent local pass must replace it with the direct lemma hit
        // pointing to "vergieten", even though both results are offline.
        val platform = testPlatformDbSupport()
        val mgr = testDataDbManager()
        val localMgr = testLocalDbManager()

        runBlocking { mgr.deleteDictionary(Language.DUTCH) }

        val localDictPath = platform.getDatabasePath(LocalDbManager.LOCAL_DICTIONARY_FILENAME)
        if (platform.fileExists(localDictPath)) platform.deleteFile(localDictPath)

        val roDictPath = platform.getDatabasePath(DataDbManager.dictionaryFileName(Language.DUTCH))

        try {
            // Fake RO dictionary: "vergiet" noun with form "vergieten", no standalone "vergieten".
            val roDriver = platform.createDictionaryDataDriver(roDictPath, readOnly = false)
            val roQ = DatabaseProvider.createDictionaryDatabase(roDriver).dictionaryQueries
            val nounId = Uuid.random()
            val nounPosId = Uuid.random()
            roQ.insertLemma(nounId, "nl", "vergiet", "vergiet", 5.0, false)
            roQ.insertLemmaPos(nounPosId, nounId, DictionaryPos.NOUN)
            roQ.insertSense(
                sense_id = Uuid.random(),
                lemma_pos_id = nounPosId,
                sense_definition = "colander",
                learner_level = LearnerLevel.A2,
                frequency = SenseFrequency.MIDDLE,
                semantic_group_id = "g1",
                name_type = null
            )
            roQ.insertForm(Uuid.random(), nounPosId, "vergieten", "vergieten", FormSource.NATIVE)
            roQ.insertLemmaWordFamily(nounId, "vergieten")
            roDriver.close()

            // Local dictionary: "vergieten" verb as a standalone offline lemma only.
            val localQ = localMgr.openLocalDictionary().dictionaryQueries
            val verbId = Uuid.random()
            val verbPosId = Uuid.random()
            localQ.insertLemma(verbId, "nl", "vergieten", "vergieten", 4.0, false)
            localQ.insertLemmaPos(verbPosId, verbId, DictionaryPos.VERB)
            localQ.insertSense(
                sense_id = Uuid.random(),
                lemma_pos_id = verbPosId,
                sense_definition = "to spill",
                learner_level = LearnerLevel.B1,
                frequency = SenseFrequency.MIDDLE,
                semantic_group_id = "g2",
                name_type = null
            )

            val repo = DictionaryRepository(mgr, localMgr, favoritesRepository(), settingsRepository())
            val card = runBlocking { repo.getLanguageCard(Language.DUTCH, "vergiet") }
            assertNotNull(card, "Card should be built for 'vergiet'")

            val resolved = card.relatedWords["vergieten"]
            assertNotNull(resolved, "'vergieten' must be present in relatedWords of 'vergiet'")
            assertEquals(
                "vergieten", resolved.lemma,
                "Standalone offline lemma in local DB must override the RO form-fallback result"
            )
        } finally {
            localMgr.closeAll()
            runBlocking { mgr.deleteDictionary(Language.DUTCH) }
            if (platform.fileExists(localDictPath)) platform.deleteFile(localDictPath)
        }
    }

    @Test
    fun loadRelatedWords_resolves_inflected_form_to_parent_lemma() {
        val platform = testPlatformDbSupport()
        val mgr = testDataDbManager()
        val localMgr = testLocalDbManager()

        // No RO Dutch dictionary — only local DB is queried
        runBlocking { mgr.deleteDictionary(Language.DUTCH) }

        val localDictPath = platform.getDatabasePath(LocalDbManager.LOCAL_DICTIONARY_FILENAME)
        if (platform.fileExists(localDictPath)) {
            platform.deleteFile(localDictPath)
        }

        try {
            val q = localMgr.openLocalDictionary().dictionaryQueries

            val lemmaId = Uuid.random()
            val lemmaPosId = Uuid.random()
            val senseId = Uuid.random()
            val formId = Uuid.random()

            q.insertLemma(lemmaId, "nl", "buigen", "buigen", 5.0, false)
            q.insertLemmaPos(lemmaPosId, lemmaId, DictionaryPos.VERB)
            q.insertSense(
                sense_id = senseId,
                lemma_pos_id = lemmaPosId,
                sense_definition = "to bend",
                learner_level = LearnerLevel.B1,
                frequency = SenseFrequency.MIDDLE,
                semantic_group_id = "group1",
                name_type = null
            )
            // "gebogen" is an inflected form, not a standalone lemma
            q.insertForm(formId, lemmaPosId, "gebogen", "gebogen", FormSource.NATIVE)
            // Word family stores original JSON casing — may be capitalised
            q.insertLemmaWordFamily(lemmaId, "Gebogen")

            val repo = DictionaryRepository(mgr, localMgr, favoritesRepository(), settingsRepository())

            val card = runBlocking { repo.getLanguageCard(Language.DUTCH, "buigen") }
            assertNotNull(card, "Card should be built for 'buigen'")

            // The form "gebogen" is not a lemma, so the form-fallback in loadRelatedWords must
            // resolve it to its parent lemma "buigen" — enabling chip navigation without a 404.
            val resolved = card.relatedWords["gebogen"]
            assertNotNull(resolved, "Form 'gebogen' must be present in relatedWords (keyed lowercase)")
            assertEquals(
                "buigen", resolved.lemma,
                "RelatedWord.lemma for form chip must point to the parent lemma so navigation lands correctly"
            )
        } finally {
            localMgr.closeAll()
            if (platform.fileExists(localDictPath)) {
                platform.deleteFile(localDictPath)
            }
        }
    }

    @Test
    fun loadRelatedWords_resolves_accented_form_via_normalized_fallback() {
        val platform = testPlatformDbSupport()
        val mgr = testDataDbManager()
        val localMgr = testLocalDbManager()

        runBlocking { mgr.deleteDictionary(Language.DUTCH) }

        val localDictPath = platform.getDatabasePath(LocalDbManager.LOCAL_DICTIONARY_FILENAME)
        if (platform.fileExists(localDictPath)) {
            platform.deleteFile(localDictPath)
        }

        try {
            val q = localMgr.openLocalDictionary().dictionaryQueries

            val lemmaId = Uuid.random()
            val lemmaPosId = Uuid.random()
            val senseId = Uuid.random()
            val formId = Uuid.random()

            q.insertLemma(lemmaId, "nl", "loden", "loden", 4.0, false)
            q.insertLemmaPos(lemmaPosId, lemmaId, DictionaryPos.VERB)
            q.insertSense(
                sense_id = senseId,
                lemma_pos_id = lemmaPosId,
                sense_definition = "to lead",
                learner_level = LearnerLevel.B1,
                frequency = SenseFrequency.MIDDLE,
                semantic_group_id = "group1",
                name_type = null
            )
            // Form stored with accent; form_normalized strips it to "lody"
            q.insertForm(formId, lemmaPosId, "łody", "lody", FormSource.NATIVE)
            // Word family entry with original JSON casing
            q.insertLemmaWordFamily(lemmaId, "Łody")

            val repo = DictionaryRepository(mgr, localMgr, favoritesRepository(), settingsRepository())

            val card = runBlocking { repo.getLanguageCard(Language.DUTCH, "loden") }
            assertNotNull(card, "Card should be built for 'loden'")

            // "łody" exact-form lookup may miss if collation is case-sensitive;
            // the normalized fallback must resolve it via form_normalized = "lody".
            val resolved = card.relatedWords["łody"]
            assertNotNull(resolved, "Accented form 'łody' must be resolved in relatedWords")
            assertEquals("loden", resolved.lemma, "Accented form chip must navigate to parent lemma 'loden'")
        } finally {
            localMgr.closeAll()
            if (platform.fileExists(localDictPath)) {
                platform.deleteFile(localDictPath)
            }
        }
    }

    @Test
    fun getWordSuggestions_excludes_name_and_article_pos() {
        val platform = testPlatformDbSupport()
        val mgr = testDataDbManager()
        val localMgr = testLocalDbManager()

        // Ensure a clean state
        runBlocking { mgr.deleteDictionary(Language.ENGLISH) }

        val dictPath = runBlocking { mgr.ensureDictionary(Language.ENGLISH) }

        try {
            assertTrue(platform.fileExists(dictPath), "Dictionary file should exist: $dictPath")

            val favoritesRepo = favoritesRepository()
            val repo = DictionaryRepository(mgr, localMgr, favoritesRepo, settingsRepository())

            val suggestions = runBlocking { repo.getWordSuggestions(Language.ENGLISH, count = 10, offset = 0) }
            assertTrue(suggestions.isNotEmpty(), "Should return at least one suggestion")

            val roDb = runBlocking { mgr.openDictionaryReadOnly(Language.ENGLISH) }
            val q = roDb.dictionaryQueries

            suggestions.forEach { lemma ->
                val lemmaRow = q.selectLemmasByWord("en", lemma).executeAsList().firstOrNull()
                assertNotNull(lemmaRow, "Expected lemma '$lemma' to exist in dictionary")
                val posRows = q.selectLemmaPosByLemmaId(lemmaRow.id).executeAsList()
                assertTrue(posRows.isNotEmpty(), "Expected lemma '$lemma' to have POS entries")
                assertTrue(
                    posRows.none { it.pos == DictionaryPos.NAME },
                    "Lemma '$lemma' should not have NAME POS"
                )
            }
        } finally {
            runBlocking { mgr.deleteDictionary(Language.ENGLISH) }
            mgr.closeAllReadOnlyDatabases()
        }
    }

    @Test
    fun searchSenseIdsByTranslation_matches_local_translation_prefix() {
        val platform = testPlatformDbSupport()
        val mgr = testDataDbManager()
        val localMgr = testLocalDbManager()

        runBlocking {
            mgr.deleteTranslation(Language.ENGLISH, Language.RUSSIAN)
        }

        val localTransPath = platform.getDatabasePath(LocalDbManager.LOCAL_TRANSLATION_FILENAME)
        if (platform.fileExists(localTransPath)) {
            platform.deleteFile(localTransPath)
        }

        try {
            val senseMatch = Uuid.random()
            val senseOther = Uuid.random()
            val lemmaId = Uuid.random()
            val lemmaPosId = Uuid.random()

            val localTransDb = localMgr.openLocalTranslation()
            val tq = localTransDb.translationQueries
            tq.insertSenseTranslation(
                sense_id = senseMatch,
                from_lang_code = "en",
                target_lang_code = "ru",
                idx = 0,
                target_lang_word = "Привет",
                target_lang_word_normalized = "привет",
                target_lang_sense_clarification = null,
                lemma_id = lemmaId,
                lemma_pos_id = lemmaPosId
            )
            tq.insertSenseTranslation(
                sense_id = senseOther,
                from_lang_code = "en",
                target_lang_code = "ru",
                idx = 0,
                target_lang_word = "Пока",
                target_lang_word_normalized = "пока",
                target_lang_sense_clarification = null,
                lemma_id = Uuid.random(),
                lemma_pos_id = Uuid.random()
            )

            val favoritesRepo = favoritesRepository()
            val repo = DictionaryRepository(mgr, localMgr, favoritesRepo, settingsRepository())

            val results = runBlocking {
                repo.searchSenseIdsByTranslation(
                    setOf(senseMatch.toString(), senseOther.toString()),
                    "пр",
                    Language.ENGLISH
                )
            }

            assertTrue(results.contains(senseMatch.toString()), "Expected matching sense ID to be returned")
            assertFalse(results.contains(senseOther.toString()), "Non-matching sense ID should not be returned")
        } finally {
            localMgr.closeAll()
            if (platform.fileExists(localTransPath)) {
                platform.deleteFile(localTransPath)
            }
        }
    }
}
