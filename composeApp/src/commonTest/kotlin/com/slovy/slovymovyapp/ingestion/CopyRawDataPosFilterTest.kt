package com.slovy.slovymovyapp.ingestion

import app.cash.sqldelight.db.SqlDriver
import com.slovy.slovymovyapp.data.db.DatabaseProvider
import com.slovy.slovymovyapp.data.dictionary.DictionaryPos
import com.slovy.slovymovyapp.data.dictionary.FormSource
import com.slovy.slovymovyapp.data.local.LocalDbManager
import com.slovy.slovymovyapp.test.BaseTest
import com.slovy.slovymovyapp.test.testPlatformDbSupport
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * Tests for POS filtering in copyRawDataToLocal.
 * Verifies that when copying raw data with a POS filter, only matching POS entries are copied.
 */
class CopyRawDataPosFilterTest : BaseTest() {

    @Test
    fun copyRawDataToLocal_copiesOnlyFilteredPos_whenPosFilterProvided() {
        val platform = testPlatformDbSupport()
        val mgr = LocalDbManager(platform)

        // Paths for source and target DBs
        val sourcePath = platform.getDatabasePath("test_source.db")
        val targetPath = platform.getDatabasePath(LocalDbManager.LOCAL_DICTIONARY_FILENAME)

        // Clean up any existing test DBs
        if (platform.fileExists(sourcePath)) platform.deleteFile(sourcePath)
        if (platform.fileExists(targetPath)) platform.deleteFile(targetPath)

        var sourceDriver: SqlDriver? = null

        try {
            // Create source DB with multiple POS for a word
            sourceDriver = platform.createDictionaryDataDriver(sourcePath, readOnly = false)
            val sourceDb = DatabaseProvider.createDictionaryDatabase(sourceDriver)
            val sourceQ = sourceDb.dictionaryQueries

            val lemmaId = JsonIngestionBuilder.generateLemmaId("test")
            val nounPosId = Uuid.random()
            val verbPosId = Uuid.random()
            val adjPosId = Uuid.random()

            // Insert lemma with 3 POS: NOUN, VERB, ADJECTIVE
            sourceQ.insertLemma(lemmaId, "en", "test", "test", 5.0, true)
            sourceQ.insertLemmaPos(nounPosId, lemmaId, DictionaryPos.NOUN)
            sourceQ.insertLemmaPos(verbPosId, lemmaId, DictionaryPos.VERB)
            sourceQ.insertLemmaPos(adjPosId, lemmaId, DictionaryPos.ADJECTIVE)

            // Insert some forms for each POS
            val nounFormId = Uuid.random()
            val verbFormId = Uuid.random()
            val adjFormId = Uuid.random()
            sourceQ.insertForm(nounFormId, nounPosId, "tests", "tests", FormSource.NATIVE)
            sourceQ.insertForm(verbFormId, verbPosId, "testing", "testing", FormSource.NATIVE)
            sourceQ.insertForm(adjFormId, adjPosId, "testy", "testy", FormSource.NATIVE)

            // Insert sense routing hints for each POS
            val nounSenseId = Uuid.random()
            val verbSenseId = Uuid.random()
            val adjSenseId = Uuid.random()
            sourceQ.insertLemmaPosHint(nounSenseId, nounPosId)
            sourceQ.insertLemmaPosHint(verbSenseId, verbPosId)
            sourceQ.insertLemmaPosHint(adjSenseId, adjPosId)

            // Create target DB (separate from source)
            val targetDb = mgr.openLocalDictionary()
            val targetQ = targetDb.dictionaryQueries

            // Create ingestion builder
            val ingestionBuilder = JsonIngestionBuilder(
                translationDbProvider = { _, _ -> mgr.openLocalTranslation() },
                frequencyMap = mapOf("test" to 5.0)
            )

            // Copy raw data with filter for only NOUN and VERB (not ADJECTIVE)
            ingestionBuilder.copyRawDataToLocal(
                word = "test",
                langCode = "en",
                sourceDb = sourceDb,
                targetDb = targetDb,
                frequency = 5.0,
                posFilter = setOf("NOUN", "VERB")
            )

            // Verify: should have lemma
            val targetLemma = targetQ.selectLemmasById(lemmaId).executeAsOneOrNull()
            assertTrue(targetLemma != null, "Lemma should be copied")

            // Verify: should have only 2 POS (NOUN and VERB, not ADJECTIVE)
            val targetPosEntries = targetQ.selectLemmaPosByLemmaId(lemmaId).executeAsList()
            assertEquals(2, targetPosEntries.size, "Should have only 2 POS entries (NOUN and VERB)")

            val targetPosSet = targetPosEntries.map { it.pos }.toSet()
            assertTrue(targetPosSet.contains(DictionaryPos.NOUN), "Should have NOUN")
            assertTrue(targetPosSet.contains(DictionaryPos.VERB), "Should have VERB")
            assertFalse(targetPosSet.contains(DictionaryPos.ADJECTIVE), "Should NOT have ADJECTIVE")

            // Verify: forms should only exist for copied POS
            val nounForms = targetQ.selectFormsWithIdByLemmaPosId(nounPosId).executeAsList()
            val verbForms = targetQ.selectFormsWithIdByLemmaPosId(verbPosId).executeAsList()
            val adjForms = targetQ.selectFormsWithIdByLemmaPosId(adjPosId).executeAsList()

            assertEquals(1, nounForms.size, "Should have NOUN forms")
            assertEquals(1, verbForms.size, "Should have VERB forms")
            assertEquals(0, adjForms.size, "Should NOT have ADJECTIVE forms")

            // Verify: sense hints should only exist for copied POS
            val nounHints = targetQ.selectLemmaPosHintsByLemmaPosId(nounPosId).executeAsList()
            val verbHints = targetQ.selectLemmaPosHintsByLemmaPosId(verbPosId).executeAsList()
            val adjHints = targetQ.selectLemmaPosHintsByLemmaPosId(adjPosId).executeAsList()

            assertEquals(1, nounHints.size, "NOUN sense hint should be copied")
            assertEquals(1, verbHints.size, "VERB sense hint should be copied")
            assertEquals(0, adjHints.size, "ADJECTIVE sense hint should NOT be copied (filtered)")
        } finally {
            sourceDriver?.close()
            runBlocking { mgr.closeAll() }
            if (platform.fileExists(sourcePath)) platform.deleteFile(sourcePath)
            if (platform.fileExists(targetPath)) platform.deleteFile(targetPath)
        }
    }

    @Test
    fun copyRawDataToLocal_copiesAllPos_whenNoFilterProvided() {
        val platform = testPlatformDbSupport()
        val mgr = LocalDbManager(platform)

        // Paths for source and target DBs
        val sourcePath = platform.getDatabasePath("test_source2.db")
        val targetPath = platform.getDatabasePath(LocalDbManager.LOCAL_DICTIONARY_FILENAME)

        // Clean up any existing test DBs
        if (platform.fileExists(sourcePath)) platform.deleteFile(sourcePath)
        if (platform.fileExists(targetPath)) platform.deleteFile(targetPath)

        var sourceDriver: SqlDriver? = null

        try {
            // Create source DB with multiple POS
            sourceDriver = platform.createDictionaryDataDriver(sourcePath, readOnly = false)
            val sourceDb = DatabaseProvider.createDictionaryDatabase(sourceDriver)
            val sourceQ = sourceDb.dictionaryQueries

            val lemmaId = JsonIngestionBuilder.generateLemmaId("word")
            val nounPosId = Uuid.random()
            val verbPosId = Uuid.random()

            sourceQ.insertLemma(lemmaId, "en", "word", "word", 5.0, true)
            sourceQ.insertLemmaPos(nounPosId, lemmaId, DictionaryPos.NOUN)
            sourceQ.insertLemmaPos(verbPosId, lemmaId, DictionaryPos.VERB)

            // Create target DB
            val targetDb = mgr.openLocalDictionary()
            val targetQ = targetDb.dictionaryQueries

            val ingestionBuilder = JsonIngestionBuilder(
                translationDbProvider = { _, _ -> mgr.openLocalTranslation() },
                frequencyMap = mapOf("word" to 5.0)
            )

            // Copy raw data WITHOUT filter (null)
            ingestionBuilder.copyRawDataToLocal(
                word = "word",
                langCode = "en",
                sourceDb = sourceDb,
                targetDb = targetDb,
                frequency = 5.0,
                posFilter = null
            )

            // Verify: should have all POS
            val targetPosEntries = targetQ.selectLemmaPosByLemmaId(lemmaId).executeAsList()
            assertEquals(2, targetPosEntries.size, "Should have all 2 POS entries when no filter")
        } finally {
            sourceDriver?.close()
            runBlocking { mgr.closeAll() }
            if (platform.fileExists(sourcePath)) platform.deleteFile(sourcePath)
            if (platform.fileExists(targetPath)) platform.deleteFile(targetPath)
        }
    }
}
