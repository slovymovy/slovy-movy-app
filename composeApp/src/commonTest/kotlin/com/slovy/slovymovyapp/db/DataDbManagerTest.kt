package com.slovy.slovymovyapp.db

import com.slovy.slovymovyapp.data.db.DatabaseProvider
import com.slovy.slovymovyapp.data.dictionary.DictionaryPos
import com.slovy.slovymovyapp.data.remote.DataDbManager
import com.slovy.slovymovyapp.test.BaseTest
import com.slovy.slovymovyapp.test.platformDbSupport
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid


class DataDbManagerTest : BaseTest() {
    @Test
    fun download_and_open_readonly() {
        val platform = platformDbSupport()
        val mgr = DataDbManager(platform, null)

        runBlocking {
            mgr.deleteDictionary("en")
            mgr.deleteTranslation(
                src = "nl",
                tgt = "en"
            )
        }

        // ensure files (use smallest remote DBs)
        val dict = runBlocking { mgr.ensureDictionary("en") }
        val tr = runBlocking { mgr.ensureTranslation("nl", "en") }

        try {
            assertTrue(platform.fileExists(dict), "Dictionary file should exist: $dict")
            assertTrue(platform.fileExists(tr), "Translation file should exist: $tr")

            // open read-only — should not throw
            mgr.openTranslationReadOnly("nl", "en")
            mgr.openDictionaryReadOnly("en")
        } finally {
            runBlocking {
                mgr.deleteDictionary("en")
                mgr.deleteTranslation(
                    src = "nl",
                    tgt = "en"
                )
            }
        }
    }

    @Test
    fun dictionary_like_and_join_queries() {
        val platform = platformDbSupport()
        val path = platform.getDatabasePath("test_dictionary_queries.db")
        if (platform.fileExists(path)) {
            platform.deleteFile(path)
        }

        val driver = platform.createDictionaryDataDriver(path, readOnly = false)
        try {
            val db = DatabaseProvider.createDictionaryDatabase(driver)
            val q = db.dictionaryQueries

            // Insert lemmas
            val beId = Uuid.random()
            val loveId = Uuid.random()
            val cafeId = Uuid.random()
            q.insertPosEntry(beId, "Be", "be", DictionaryPos.VERB)
            q.insertPosEntry(loveId, "Love", "love", DictionaryPos.VERB)
            q.insertPosEntry(cafeId, "Café", "cafe", DictionaryPos.NOUN)

            // Insert forms (mixed case to test COLLATE NOCASE)
            q.insertForm(Uuid.random(), beId, "am", "am")
            q.insertForm(Uuid.random(), beId, "ARE", "are")
            q.insertForm(Uuid.random(), beId, "being", "being")
            q.insertForm(Uuid.random(), loveId, "loved", "loved")
            q.insertForm(Uuid.random(), loveId, "loving", "loving")
            q.insertForm(Uuid.random(), cafeId, "CAFÉS", "cafes")

            // lemma LIKE
            val lemmasLo = q.selectLemmasLike("lo%", 20).executeAsList().map { it.lemma }
            assertEquals(listOf("Love"), lemmasLo, "lemma LIKE lo% should match 'Love'")

            val lemmasBe = q.selectLemmasLike("be%", 20).executeAsList().map { it.lemma }
            assertTrue(lemmasBe.contains("Be"), "lemma LIKE be% should contain 'Be'")

            // normalized LIKE
            val lemmasCaf = q.selectLemmasNormalizedLike("caf%", 20).executeAsList().map { it.lemma }
            assertEquals(listOf("Café"), lemmasCaf, "normalized LIKE caf% should match 'Café'")

            // JOIN equals (form -> lemma)
            val areEq = q.selectLemmasByFormEquals("are", 20).executeAsList()
            assertEquals(1, areEq.size, "form equals 'are' should match exactly one entry")
            assertEquals("Be", areEq[0].lemma, "'are' should belong to lemma 'Be'")
            assertEquals("ARE", areEq[0].form, "Stored form should be returned as inserted (case preserved)")

            // JOIN equals on normalized
            val cafesEq = q.selectLemmasByFormNormalizedEquals("cafes", 20).executeAsList()
            assertEquals(1, cafesEq.size, "normalized form equals 'cafes' should match one entry")
            assertEquals("Café", cafesEq[0].lemma, "'cafes' should map to lemma 'Café'")
            assertEquals("CAFÉS", cafesEq[0].form, "Original stored form should be returned")

            // JOIN LIKE (forms prefix)
            val lovLike = q.selectLemmasFromFormsLike("lov%", 20).executeAsList()
            assertEquals(2, lovLike.size, "lov% should match two forms of 'Love'")
            assertTrue(lovLike.all { it.lemma == "Love" }, "Both matches should belong to 'Love'")
            val lovForms = lovLike.map { it.form }.toSet()
            assertEquals(setOf("loved", "loving"), lovForms, "Matched forms should be 'loved' and 'loving'")

            // LIMIT parameter behavior (request only 1 row)
            val limited = q.selectLemmasFromFormsLike("l%", 1).executeAsList()
            assertEquals(1, limited.size, "LIMIT should restrict result count to 1")
        } finally {
            driver.close()
            platform.deleteFile(path)
        }
    }

    @Test
    fun download_en_ru_and_search_test_prefix() {
        val platform = platformDbSupport()
        val mgr = DataDbManager(platform, null)

        // Ensure required files exist (English dictionary, English->Russian translation)
        val dict = runBlocking { mgr.ensureDictionary("en") }
        val tr = runBlocking { mgr.ensureTranslation("en", "ru") }

        try {
            assertTrue(platform.fileExists(dict), "Dictionary file should exist: $dict")
            assertTrue(platform.fileExists(tr), "Translation file should exist: $tr")

            // Open dictionary and search for 'test%'
            val db = mgr.openDictionaryReadOnly("en")
            val q = db.dictionaryQueries

            val lemmaLike = q.selectLemmasLike("test%", 20).executeAsList().map { it.lemma }
            assertTrue(lemmaLike.isNotEmpty(), "English dictionary should contain lemmas starting with 'test'")

            val formLike = q.selectLemmasFromFormsLike("test%", 20).executeAsList().map { it.form }
            assertTrue(formLike.isNotEmpty(), "Form LIKE 'test%' should return at least one match")
        } finally {
            // Clean up to keep environment tidy
            runBlocking {
                mgr.deleteDictionary("en")
                mgr.deleteTranslation("en", "ru")
            }
        }
    }
}