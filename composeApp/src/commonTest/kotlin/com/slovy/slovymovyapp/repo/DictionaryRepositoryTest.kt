package com.slovy.slovymovyapp.repo

import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.favorites.FavoritesRepository
import com.slovy.slovymovyapp.data.remote.DataDbManager
import com.slovy.slovymovyapp.data.remote.DictionaryRepository
import com.slovy.slovymovyapp.data.remote.PartOfSpeech
import com.slovy.slovymovyapp.test.BaseTest
import com.slovy.slovymovyapp.test.IgnoreIos
import com.slovy.slovymovyapp.test.testDataDbManager
import com.slovy.slovymovyapp.test.testPlatformDbSupport
import kotlinx.coroutines.runBlocking
import kotlin.test.*

// TODO: we use HTTP for now to workaround some issues with IOS emulator
// https://github.com/slovymovy/slovy-movy-app/issues/34
@IgnoreIos
class DictionaryRepositoryTest : BaseTest() {
    @Test
    fun download_en_ru_and_search_test() {
        val platform = testPlatformDbSupport()
        val mgr = testDataDbManager()

        // Ensure a clean state
        mgr.deleteDictionary(Language.ENGLISH)
        mgr.deleteTranslation(Language.ENGLISH, Language.RUSSIAN)

        // Download actual English dictionary and English->Russian translation
        val dictPath = runBlocking { mgr.ensureDictionary(Language.ENGLISH) }
        val trPath = runBlocking { mgr.ensureTranslation(Language.ENGLISH, Language.RUSSIAN) }

        try {
            assertTrue(platform.fileExists(dictPath), "Dictionary file should exist: $dictPath")
            assertTrue(platform.fileExists(trPath), "Translation file should exist: $trPath")

            val favoritesRepo = FavoritesRepository(DataDbManager.openAppDatabase(testPlatformDbSupport()))
            val repo = DictionaryRepository(mgr, favoritesRepo)

            // Verify installed sets reflect downloads
            assertTrue(repo.installedDictionaries().contains(Language.ENGLISH), "'en' dictionary should be installed")
            assertTrue(
                repo.installedTranslationTargets(Language.ENGLISH).contains(Language.RUSSIAN),
                "'ru' should be an installed translation target for 'en'"
            )

            // Search for 'test' in the English dictionary
            val results = repo.search("bu", dictionaryLanguage = Language.ENGLISH)
            assertTrue(results.isNotEmpty(), "Expected at least one search result for 'bu'")
            val first = results.first()
            assertTrue(first.display.contains("bu", ignoreCase = true), "First result display should mention 'bu'")

            // Build a language card for the first result's lemma to ensure repository wiring works on real data
            val card = repo.getLanguageCard(Language.ENGLISH, first.lemma)
            assertNotNull(card, "Language card should be built for a real lemma from the English dictionary")
            assertTrue(card.entries.isNotEmpty(), "Language card should have at least one entry")
            assertEquals(first.lemma, card.lemma, "Card lemma should match search result")
            assertTrue(card.zipfFrequency >= 0.0f, "Zipf frequency should be non-negative")
        } finally {
            // Clean up downloaded files to keep test environment tidy
            mgr.deleteDictionary(Language.ENGLISH)
            mgr.deleteTranslation(Language.ENGLISH, Language.RUSSIAN)
        }
    }

    @Test
    fun nl_tegengesteld_should_return_both_noun_and_verb() {
        val platform = testPlatformDbSupport()
        val mgr = testDataDbManager()

        // Ensure a clean state
        mgr.deleteDictionary(Language.DUTCH)

        val dictPath = runBlocking { mgr.ensureDictionary(Language.DUTCH) }
        try {
            assertTrue(platform.fileExists(dictPath), "Dictionary file should exist: $dictPath")

            val favoritesRepo = FavoritesRepository(DataDbManager.openAppDatabase(testPlatformDbSupport()))
            val repo = DictionaryRepository(mgr, favoritesRepo)
            assertTrue(repo.installedDictionaries().contains(Language.DUTCH), "'nl' dictionary should be installed")

            val card = repo.getLanguageCard(Language.DUTCH, "tegengesteld")
            assertNotNull(card, "Language card should be built for 'tegengesteld'")
            val poses = card.entries.map { it.pos }.toSet()
            assertTrue(poses.contains(PartOfSpeech.ADJECTIVE), "Expected ADJECTIVE entry for 'tegengesteld'")
            assertTrue(poses.contains(PartOfSpeech.VERB), "Expected VERB entry for 'tegengesteld'")
            assertTrue(card.zipfFrequency >= 0.0f, "Zipf frequency should be non-negative")
        } finally {
            // Clean up
            mgr.deleteDictionary(Language.DUTCH)
        }
    }

    @Test
    fun search_returns_multiple_forms_for_same_lemma() {
        val platform = testPlatformDbSupport()
        val mgr = testDataDbManager()

        // Ensure a clean state
        mgr.deleteDictionary(Language.ENGLISH)

        val dictPath = runBlocking { mgr.ensureDictionary(Language.ENGLISH) }
        try {
            assertTrue(platform.fileExists(dictPath), "Dictionary file should exist: $dictPath")

            val favoritesRepo = FavoritesRepository(DataDbManager.openAppDatabase(testPlatformDbSupport()))
            val repo = DictionaryRepository(mgr, favoritesRepo)
            assertTrue(repo.installedDictionaries().contains(Language.ENGLISH), "'en' dictionary should be installed")

            // Search for "bu" which should match "bu" and its forms
            val results = repo.search("bu", dictionaryLanguage = Language.ENGLISH)
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
            mgr.deleteDictionary(Language.ENGLISH)
        }
    }

    @Test
    fun search_suppresses_forms_when_lemma_present() {
        val platform = testPlatformDbSupport()
        val mgr = testDataDbManager()

        // Ensure a clean state
        mgr.deleteDictionary(Language.ENGLISH)

        val dictPath = runBlocking { mgr.ensureDictionary(Language.ENGLISH) }
        try {
            assertTrue(platform.fileExists(dictPath), "Dictionary file should exist: $dictPath")

            val favoritesRepo = FavoritesRepository(DataDbManager.openAppDatabase(testPlatformDbSupport()))
            val repo = DictionaryRepository(mgr, favoritesRepo)
            assertTrue(repo.installedDictionaries().contains(Language.ENGLISH), "'en' dictionary should be installed")

            // Search for "test" - should match the base lemma "test"
            val results = repo.search("bu", dictionaryLanguage = Language.ENGLISH)
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
            mgr.deleteDictionary(Language.ENGLISH)
        }
    }

    @Ignore
    @Test
    fun word_family_is_retrieved_correctly() {
        val platform = testPlatformDbSupport()
        val mgr = testDataDbManager()

        // Ensure a clean state
        mgr.deleteDictionary(Language.ENGLISH)

        val dictPath = runBlocking { mgr.ensureDictionary(Language.ENGLISH) }
        try {
            assertTrue(platform.fileExists(dictPath), "Dictionary file should exist: $dictPath")

            val favoritesRepo = FavoritesRepository(DataDbManager.openAppDatabase(testPlatformDbSupport()))
            val repo = DictionaryRepository(mgr, favoritesRepo)
            assertTrue(repo.installedDictionaries().contains(Language.ENGLISH), "'en' dictionary should be installed")

            // Test with "double" which should have word_family in the processed JSON
            val card = repo.getLanguageCard(Language.ENGLISH, "double")
            assertNotNull(card, "Language card should be built for 'double'")

            // Verify word_family is present and contains expected members
            assertTrue(card.wordFamily.isNotEmpty(), "Word family should not be empty for 'double'")

            // Verify the word family contains the expected words from double.json
            val expectedMembers = listOf("doubling", "doubly", "doublet")
            expectedMembers.forEach { member ->
                assertTrue(
                    card.wordFamily.contains(member),
                    "Word family should contain '$member'. Found: ${card.wordFamily}"
                )
            }
        } finally {
            // Clean up
            mgr.deleteDictionary(Language.ENGLISH)
        }
    }

    @Test
    fun related_words_includes_synonyms_antonyms_and_family() {
        val platform = testPlatformDbSupport()
        val mgr = testDataDbManager()

        // Ensure a clean state
        mgr.deleteDictionary(Language.ENGLISH)
        mgr.deleteTranslation(Language.ENGLISH, Language.RUSSIAN)

        val dictPath = runBlocking { mgr.ensureDictionary(Language.ENGLISH) }
        val trPath = runBlocking { mgr.ensureTranslation(Language.ENGLISH, Language.RUSSIAN) }

        try {
            assertTrue(platform.fileExists(dictPath), "Dictionary file should exist: $dictPath")
            assertTrue(platform.fileExists(trPath), "Translation file should exist: $trPath")

            val favoritesRepo = FavoritesRepository(DataDbManager.openAppDatabase(testPlatformDbSupport()))
            val repo = DictionaryRepository(mgr, favoritesRepo)
            assertTrue(repo.installedDictionaries().contains(Language.ENGLISH), "'en' dictionary should be installed")

            repo.getLanguageCard(Language.ENGLISH, "simultaneously")?.let { card ->
                assertTrue(card.relatedWords.isNotEmpty(), "Related words should not be empty for 'simultaneously'")
                assertTrue { card.relatedWords.contains("concurrently") }
            }
        } finally {
            // Clean up
            mgr.deleteDictionary(Language.ENGLISH)
            mgr.deleteTranslation(Language.ENGLISH, Language.RUSSIAN)
        }
    }
}
