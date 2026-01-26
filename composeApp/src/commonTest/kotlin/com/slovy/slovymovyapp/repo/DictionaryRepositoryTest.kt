package com.slovy.slovymovyapp.repo

import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.dictionary.DictionaryPos
import com.slovy.slovymovyapp.data.dictionary.LearnerLevel
import com.slovy.slovymovyapp.data.dictionary.SenseFrequency
import com.slovy.slovymovyapp.data.favorites.FavoritesRepository
import com.slovy.slovymovyapp.data.local.LocalDbManager
import com.slovy.slovymovyapp.data.remote.DictionaryRepository
import com.slovy.slovymovyapp.data.remote.PartOfSpeech
import com.slovy.slovymovyapp.test.BaseTest
import com.slovy.slovymovyapp.test.IgnoreIos
import com.slovy.slovymovyapp.test.testPlatformDbSupport
import kotlinx.coroutines.runBlocking
import kotlin.test.*
import kotlin.uuid.Uuid

// TODO: we use HTTP for now to workaround some issues with IOS emulator
// https://github.com/slovymovy/slovy-movy-app/issues/34
@IgnoreIos
class DictionaryRepositoryTest : BaseTest() {

    private fun favoritesRepository(): FavoritesRepository {
        return FavoritesRepository(testAppDatabaseHolder().database)
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
            val repo = DictionaryRepository(mgr, localMgr, favoritesRepo)

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
            val repo = DictionaryRepository(mgr, localMgr, favoritesRepo)
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
            val repo = DictionaryRepository(mgr, localMgr, favoritesRepo)
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
            val repo = DictionaryRepository(mgr, localMgr, favoritesRepo)
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
            val repo = DictionaryRepository(mgr, localMgr, favoritesRepo)
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
            val repo = DictionaryRepository(mgr, localMgr, favoritesRepo)

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
            val repo = DictionaryRepository(mgr, localMgr, favoritesRepo)

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
            val repo = DictionaryRepository(mgr, localMgr, favoritesRepo)

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
            val repo = DictionaryRepository(mgr, localMgr, favoritesRepo)

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
        val repo = DictionaryRepository(mgr, localMgr, favoritesRepo)

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
            val repo = DictionaryRepository(mgr, localMgr, favoritesRepo)

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
            val repo = DictionaryRepository(mgr, localMgr, favoritesRepo)

            // Get initial suggestions
            val initialSuggestions = runBlocking { repo.getWordSuggestions(Language.ENGLISH, offset = 5) }
            assertTrue(initialSuggestions.isNotEmpty(), "Should have initial suggestions")

            // Add the first suggestion to favorites (using a dummy sense ID since we just need the lemma match)
            val wordToFavorite = initialSuggestions.first()
            runBlocking { favoritesRepo.add("dummy-sense-id", Language.ENGLISH, wordToFavorite) }

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
            val repo = DictionaryRepository(mgr, localMgr, favoritesRepo)

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

    @Test
    fun searchSenseIdsByTranslation_normalizes_apostrophe_variants() {
        val platform = testPlatformDbSupport()
        val mgr = testDataDbManager()
        val localMgr = testLocalDbManager()

        runBlocking {
            mgr.deleteTranslation(Language.ENGLISH, Language.DUTCH)
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

            // Insert translation with typographic apostrophe (U+2019) as stored in DB
            val localTransDb = localMgr.openLocalTranslation()
            val tq = localTransDb.translationQueries
            tq.insertSenseTranslation(
                sense_id = senseMatch,
                from_lang_code = "en",
                target_lang_code = "nl",
                idx = 0,
                target_lang_word = "zo\u2019n",  // typographic apostrophe
                target_lang_word_normalized = "zo\u2019n",
                target_lang_sense_clarification = null,
                lemma_id = lemmaId,
                lemma_pos_id = lemmaPosId
            )
            tq.insertSenseTranslation(
                sense_id = senseOther,
                from_lang_code = "en",
                target_lang_code = "nl",
                idx = 0,
                target_lang_word = "andere",
                target_lang_word_normalized = "andere",
                target_lang_sense_clarification = null,
                lemma_id = Uuid.random(),
                lemma_pos_id = Uuid.random()
            )

            val favoritesRepo = favoritesRepository()
            val repo = DictionaryRepository(mgr, localMgr, favoritesRepo)

            // Test ASCII apostrophe (U+0027) - most common from keyboards
            val resultsAscii = runBlocking {
                repo.searchSenseIdsByTranslation(
                    setOf(senseMatch.toString(), senseOther.toString()),
                    "zo'n",  // ASCII apostrophe
                    Language.ENGLISH
                )
            }
            assertTrue(
                resultsAscii.contains(senseMatch.toString()),
                "ASCII apostrophe query should match typographic apostrophe in DB"
            )

            // Test backtick (U+0060)
            val resultsBacktick = runBlocking {
                repo.searchSenseIdsByTranslation(
                    setOf(senseMatch.toString(), senseOther.toString()),
                    "zo`n",  // backtick
                    Language.ENGLISH
                )
            }
            assertTrue(
                resultsBacktick.contains(senseMatch.toString()),
                "Backtick query should match typographic apostrophe in DB"
            )

            // Verify non-matching word is not returned
            assertFalse(
                resultsAscii.contains(senseOther.toString()),
                "Non-matching sense ID should not be returned"
            )
        } finally {
            localMgr.closeAll()
            if (platform.fileExists(localTransPath)) {
                platform.deleteFile(localTransPath)
            }
        }
    }
}
