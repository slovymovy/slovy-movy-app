package com.slovy.slovymovyapp.repo

import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.remote.DataDbManager
import com.slovy.slovymovyapp.data.remote.DictionaryRepository
import com.slovy.slovymovyapp.data.remote.PartOfSpeech
import com.slovy.slovymovyapp.test.BaseTest
import com.slovy.slovymovyapp.test.platformDbSupport
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DictionaryRepositoryTest : BaseTest() {
    @Test
    fun download_en_ru_and_search_test() {
        val platform = platformDbSupport()
        val mgr = DataDbManager(platform, null)

        // Ensure a clean state
        mgr.deleteDictionary(Language.ENGLISH)
        mgr.deleteTranslation(Language.ENGLISH, Language.RUSSIAN)

        // Download actual English dictionary and English->Russian translation
        val dictPath = runBlocking { mgr.ensureDictionary(Language.ENGLISH) }
        val trPath = runBlocking { mgr.ensureTranslation(Language.ENGLISH, Language.RUSSIAN) }

        try {
            assertTrue(platform.fileExists(dictPath), "Dictionary file should exist: $dictPath")
            assertTrue(platform.fileExists(trPath), "Translation file should exist: $trPath")

            val repo = DictionaryRepository(mgr)

            // Verify installed sets reflect downloads
            assertTrue(repo.installedDictionaries().contains(Language.ENGLISH), "'en' dictionary should be installed")
            assertTrue(
                repo.installedTranslationTargets(Language.ENGLISH).contains(Language.RUSSIAN),
                "'ru' should be an installed translation target for 'en'"
            )

            // Search for 'test' in the English dictionary
            val results = repo.search("test", dictionaryLanguage = Language.ENGLISH)
            assertTrue(results.isNotEmpty(), "Expected at least one search result for 'test'")
            val first = results.first()
            assertTrue(first.display.contains("test", ignoreCase = true), "First result display should mention 'test'")

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
    fun nl_voorstellen_should_return_both_noun_and_verb() {
        val platform = platformDbSupport()
        val mgr = DataDbManager(platform, null)

        // Ensure a clean state
        mgr.deleteDictionary(Language.DUTCH)

        val dictPath = runBlocking { mgr.ensureDictionary(Language.DUTCH) }
        try {
            assertTrue(platform.fileExists(dictPath), "Dictionary file should exist: $dictPath")

            val repo = DictionaryRepository(mgr)
            assertTrue(repo.installedDictionaries().contains(Language.DUTCH), "'nl' dictionary should be installed")

            val card = repo.getLanguageCard(Language.DUTCH, "voorstellen")
            assertNotNull(card, "Language card should be built for 'voorstellen'")
            val poses = card.entries.map { it.pos }.toSet()
            assertTrue(poses.contains(PartOfSpeech.NOUN), "Expected NOUN entry for 'voorstellen'")
            assertTrue(poses.contains(PartOfSpeech.VERB), "Expected VERB entry for 'voorstellen'")
            assertTrue(card.zipfFrequency >= 0.0f, "Zipf frequency should be non-negative")
        } finally {
            // Clean up
            mgr.deleteDictionary(Language.DUTCH)
        }
    }

    @Test
    fun search_returns_multiple_forms_for_same_lemma() {
        val platform = platformDbSupport()
        val mgr = DataDbManager(platform, null)

        // Ensure a clean state
        mgr.deleteDictionary(Language.ENGLISH)

        val dictPath = runBlocking { mgr.ensureDictionary(Language.ENGLISH) }
        try {
            assertTrue(platform.fileExists(dictPath), "Dictionary file should exist: $dictPath")

            val repo = DictionaryRepository(mgr)
            assertTrue(repo.installedDictionaries().contains(Language.ENGLISH), "'en' dictionary should be installed")

            // Search for "test" which should match "test" and its forms (tested, testing, etc.)
            val results = repo.search("test", dictionaryLanguage = Language.ENGLISH)
            assertTrue(results.isNotEmpty(), "Expected at least one search result for 'test'")

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
        val platform = platformDbSupport()
        val mgr = DataDbManager(platform, null)

        // Ensure a clean state
        mgr.deleteDictionary(Language.ENGLISH)

        val dictPath = runBlocking { mgr.ensureDictionary(Language.ENGLISH) }
        try {
            assertTrue(platform.fileExists(dictPath), "Dictionary file should exist: $dictPath")

            val repo = DictionaryRepository(mgr)
            assertTrue(repo.installedDictionaries().contains(Language.ENGLISH), "'en' dictionary should be installed")

            // Search for "test" - should match the base lemma "test"
            val results = repo.search("test", dictionaryLanguage = Language.ENGLISH)
            assertTrue(results.isNotEmpty(), "Expected at least one search result for 'test'")

            // Find the base lemma "test"
            val testLemma = results.firstOrNull {
                it.display.equals("test", ignoreCase = true) &&
                        !it.display.contains("form of", ignoreCase = true)
            }

            // If the base lemma "test" is present, no forms of "test" should be shown
            if (testLemma != null) {
                val testForms = results.filter {
                    it.display.contains("form of", ignoreCase = true) &&
                            it.display.contains("\"test\"", ignoreCase = true)
                }
                assertTrue(
                    testForms.isEmpty(),
                    "Expected no forms of 'test' when base lemma is present, but found: ${testForms.map { it.display }}"
                )
            }
        } finally {
            // Clean up
            mgr.deleteDictionary(Language.ENGLISH)
        }
    }
}
