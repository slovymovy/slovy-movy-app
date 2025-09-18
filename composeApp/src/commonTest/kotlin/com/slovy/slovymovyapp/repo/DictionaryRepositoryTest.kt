package com.slovy.slovymovyapp.repo

import com.slovy.slovymovyapp.data.remote.DataDbManager
import com.slovy.slovymovyapp.data.remote.DictionaryRepository
import com.slovy.slovymovyapp.test.BaseTest
import com.slovy.slovymovyapp.test.platformDbSupport
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DictionaryRepositoryTest : BaseTest() {
    @Test
    fun download_en_ru_and_search_test() {
        val platform = platformDbSupport()
        val mgr = DataDbManager(platform, null)

        // Ensure a clean state
        mgr.deleteDictionary("en")
        mgr.deleteTranslation("en", "ru")

        // Download actual English dictionary and English->Russian translation
        val dictPath = runBlocking { mgr.ensureDictionary("en") }
        val trPath = runBlocking { mgr.ensureTranslation("en", "ru") }

        try {
            assertTrue(platform.fileExists(dictPath), "Dictionary file should exist: $dictPath")
            assertTrue(platform.fileExists(trPath), "Translation file should exist: $trPath")

            val repo = DictionaryRepository(mgr)

            // Verify installed sets reflect downloads
            assertTrue(repo.installedDictionaries().contains("en"), "'en' dictionary should be installed")
            assertTrue(repo.installedTranslationTargets("en").contains("ru"), "'ru' should be an installed translation target for 'en'")

            // Search for 'test' in the English dictionary
            val results = repo.search("test", dictionaryLanguage = "en")
            assertTrue(results.isNotEmpty(), "Expected at least one search result for 'test'")
            val first = results.first()
            assertTrue(first.display.contains("test", ignoreCase = true), "First result display should mention 'test'")

            // Build a language card for the first result's lemma to ensure repository wiring works on real data
            val card = repo.getLanguageCard("en", first.lemmaId)
            assertNotNull(card, "Language card should be built for a real lemma from the English dictionary")
            assertTrue(card.entries.isNotEmpty(), "Language card should have at least one entry")
        } finally {
            // Clean up downloaded files to keep test environment tidy
            mgr.deleteDictionary("en")
            mgr.deleteTranslation("en", "ru")
        }
    }
}
