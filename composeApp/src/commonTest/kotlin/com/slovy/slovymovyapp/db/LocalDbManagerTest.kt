package com.slovy.slovymovyapp.db

import com.slovy.slovymovyapp.data.dictionary.DictionaryPos
import com.slovy.slovymovyapp.data.local.LocalDbManager
import com.slovy.slovymovyapp.test.BaseTest
import com.slovy.slovymovyapp.test.testPlatformDbSupport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

private fun prefixRange(prefix: String): Pair<String, String> {
    if (prefix.isEmpty()) return "" to "\uFFFF"
    return prefix to prefix + '\uFFFF'
}

class LocalDbManagerTest : BaseTest() {

    @Test
    fun open_and_write_local_dictionary() {
        val platform = testPlatformDbSupport()
        val mgr = LocalDbManager(platform)

        // Clean up any existing test data
        val dictPath = platform.getDatabasePath(LocalDbManager.LOCAL_DICTIONARY_FILENAME)
        if (platform.fileExists(dictPath)) {
            platform.deleteFile(dictPath)
        }

        try {
            val db = mgr.openLocalDictionary()
            val q = db.dictionaryQueries

            // Insert test data
            val lemmaId = Uuid.random()
            q.insertLemma(lemmaId, "en", "TestWord", "testword", 5.0, false)

            val lemmaPos = Uuid.random()
            q.insertLemmaPos(lemmaPos, lemmaId, DictionaryPos.NOUN)

            // Read back and verify
            val (start, end) = prefixRange("testword")
            val results = q.selectLemmasNormalizedLike("en", start, end, 10).executeAsList()
            assertEquals(1, results.size, "Should find inserted lemma")
            assertEquals("TestWord", results[0].lemma)
        } finally {
            mgr.closeAll()
            platform.deleteFile(dictPath)
        }
    }

    @Test
    fun open_and_write_local_translation() {
        val platform = testPlatformDbSupport()
        val mgr = LocalDbManager(platform)

        val transPath = platform.getDatabasePath(LocalDbManager.LOCAL_TRANSLATION_FILENAME)
        if (platform.fileExists(transPath)) {
            platform.deleteFile(transPath)
        }

        try {
            val db = mgr.openLocalTranslation()
            val q = db.translationQueries

            // Insert test translation
            val senseId = Uuid.random()
            val lemmaId = Uuid.random()
            val lemmaPosId = Uuid.random()
            q.insertSenseTranslation(
                sense_id = senseId,
                from_lang_code = "en",
                target_lang_code = "ru",
                idx = 0,
                target_lang_word = "ТестовоеСлово",
                target_lang_word_normalized = "тестовоеслово",
                target_lang_sense_clarification = null,
                lemma_id = lemmaId,
                lemma_pos_id = lemmaPosId
            )

            // Read back and verify
            val results = q.selectSenseTranslationsBySenseWithNormalized(senseId, "en", "ru").executeAsList()
            assertEquals(1, results.size, "Should find inserted translation")
            assertEquals("ТестовоеСлово", results[0].target_lang_word)
        } finally {
            mgr.closeAll()
            platform.deleteFile(transPath)
        }
    }

    @Test
    fun reopen_preserves_data() {
        val platform = testPlatformDbSupport()

        val dictPath = platform.getDatabasePath(LocalDbManager.LOCAL_DICTIONARY_FILENAME)
        if (platform.fileExists(dictPath)) {
            platform.deleteFile(dictPath)
        }

        val lemmaId = Uuid.random()

        try {
            // First open - write data
            val mgr1 = LocalDbManager(platform)
            val db1 = mgr1.openLocalDictionary()
            db1.dictionaryQueries.insertLemma(lemmaId, "en", "Persistent", "persistent", 3.0, false)
            mgr1.closeAll()

            // Second open - read data
            val mgr2 = LocalDbManager(platform)
            val db2 = mgr2.openLocalDictionary()
            val (start, end) = prefixRange("persistent")
            val results = db2.dictionaryQueries.selectLemmasNormalizedLike("en", start, end, 10).executeAsList()
            assertEquals(1, results.size, "Data should persist after reopen")
            assertEquals("Persistent", results[0].lemma)
            mgr2.closeAll()
        } finally {
            platform.deleteFile(dictPath)
        }
    }
}
