package com.slovy.slovymovyapp.db

import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.db.DatabaseProvider
import com.slovy.slovymovyapp.data.dictionary.DictionaryPos
import com.slovy.slovymovyapp.data.remote.DataDbManager
import com.slovy.slovymovyapp.data.settings.Setting
import com.slovy.slovymovyapp.data.settings.SettingsRepository
import com.slovy.slovymovyapp.test.BaseTest
import com.slovy.slovymovyapp.test.platformDbSupport
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid


class DataDbManagerTest : BaseTest() {
    @Test
    fun download_and_open_readonly() {
        val platform = platformDbSupport()
        val mgr = DataDbManager(platform, null)

        runBlocking {
            mgr.deleteDictionary(Language.ENGLISH)
            mgr.deleteTranslation(
                src = Language.DUTCH,
                tgt = Language.ENGLISH
            )
        }

        // ensure files (use smallest remote DBs)
        val dict = runBlocking { mgr.ensureDictionary(Language.ENGLISH) }
        val tr = runBlocking { mgr.ensureTranslation(Language.DUTCH, Language.ENGLISH) }

        try {
            assertTrue(platform.fileExists(dict), "Dictionary file should exist: $dict")
            assertTrue(platform.fileExists(tr), "Translation file should exist: $tr")

            // open read-only — should not throw
            mgr.openTranslationReadOnly(Language.DUTCH, Language.ENGLISH)
            mgr.openDictionaryReadOnly(Language.ENGLISH)
        } finally {
            runBlocking {
                mgr.deleteDictionary(Language.ENGLISH)
                mgr.deleteTranslation(
                    src = Language.DUTCH,
                    tgt = Language.ENGLISH
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

            q.insertLemma(beId, "Be", "be", 0.0)
            q.insertLemma(loveId, "Love", "love", 5.0)
            q.insertLemma(cafeId, "Café", "cafe", 10.0)

            val beIdPos = Uuid.random()
            val loveIdPos = Uuid.random()
            val cafeIdPost = Uuid.random()

            q.insertLemmaPos(beIdPos, beId, DictionaryPos.VERB)
            q.insertLemmaPos(loveIdPos, loveId, DictionaryPos.VERB)
            q.insertLemmaPos(cafeIdPost, cafeId, DictionaryPos.NOUN)

            // Insert forms (mixed case to test COLLATE NOCASE)
            q.insertForm(Uuid.random(), beIdPos, "am", "am")
            q.insertForm(Uuid.random(), beIdPos, "ARE", "are")
            q.insertForm(Uuid.random(), beIdPos, "being", "being")
            q.insertForm(Uuid.random(), loveIdPos, "loved", "loved")
            q.insertForm(Uuid.random(), loveIdPos, "loving", "loving")
            q.insertForm(Uuid.random(), cafeIdPost, "CAFÉS", "cafes")

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
        val dict = runBlocking { mgr.ensureDictionary(Language.ENGLISH) }
        val tr = runBlocking { mgr.ensureTranslation(Language.ENGLISH, Language.RUSSIAN) }

        try {
            assertTrue(platform.fileExists(dict), "Dictionary file should exist: $dict")
            assertTrue(platform.fileExists(tr), "Translation file should exist: $tr")

            // Open dictionary and search for 'test%'
            val db = mgr.openDictionaryReadOnly(Language.ENGLISH)
            val q = db.dictionaryQueries

            val lemmaLike = q.selectLemmasLike("test%", 20).executeAsList().map { it.lemma }
            assertTrue(lemmaLike.isNotEmpty(), "English dictionary should contain lemmas starting with 'test'")

            val formLike = q.selectLemmasFromFormsLike("test%", 20).executeAsList().map { it.form }
            assertTrue(formLike.isNotEmpty(), "Form LIKE 'test%' should return at least one match")
        } finally {
            // Clean up to keep environment tidy
            runBlocking {
                mgr.deleteDictionary(Language.ENGLISH)
                mgr.deleteTranslation(Language.ENGLISH, Language.RUSSIAN)
            }
        }
    }

    @Test
    fun version_checking_returns_false_when_version_not_set() {
        val platform = platformDbSupport()
        val appDbPath = platform.getDatabasePath("test_version_not_set.db")
        if (platform.fileExists(appDbPath)) {
            platform.deleteFile(appDbPath)
        }

        val appDriver = platform.createAppDataDriver(appDbPath)
        val appDb = DatabaseProvider.createAppDatabase(appDriver)
        val settingsRepo = SettingsRepository(appDb)

        try {
            val mgr = DataDbManager(platform, settingsRepo)
            val hasRequired = runBlocking { mgr.hasRequiredVersion() }
            assertFalse(hasRequired, "hasRequiredVersion should return false when version is not set")
        } finally {
            appDriver.close()
            platform.deleteFile(appDbPath)
        }
    }

    @Test
    fun version_checking_returns_true_when_version_matches() {
        val platform = platformDbSupport()
        val appDbPath = platform.getDatabasePath("test_version_matches.db")
        if (platform.fileExists(appDbPath)) {
            platform.deleteFile(appDbPath)
        }

        val appDriver = platform.createAppDataDriver(appDbPath)
        val appDb = DatabaseProvider.createAppDatabase(appDriver)
        val settingsRepo = SettingsRepository(appDb)

        try {
            val mgr = DataDbManager(platform, settingsRepo)

            settingsRepo.insert(
                Setting(
                    id = Setting.Name.DATA_VERSION,
                    value = Json.parseToJsonElement("\"${DataDbManager.VERSION}\"")
                )
            )

            val hasRequired = runBlocking { mgr.hasRequiredVersion() }
            assertTrue(hasRequired, "hasRequiredVersion should return true when version matches")
        } finally {
            appDriver.close()
            platform.deleteFile(appDbPath)
        }
    }

    @Test
    fun version_checking_returns_false_when_version_outdated() {
        val platform = platformDbSupport()
        val appDbPath = platform.getDatabasePath("test_version_outdated.db")
        if (platform.fileExists(appDbPath)) {
            platform.deleteFile(appDbPath)
        }

        val appDriver = platform.createAppDataDriver(appDbPath)
        val appDb = DatabaseProvider.createAppDatabase(appDriver)
        val settingsRepo = SettingsRepository(appDb)

        try {
            val mgr = DataDbManager(platform, settingsRepo)

            settingsRepo.insert(
                Setting(
                    id = Setting.Name.DATA_VERSION,
                    value = Json.parseToJsonElement("\"v0\"")
                )
            )

            val hasRequired = runBlocking { mgr.hasRequiredVersion() }
            assertFalse(hasRequired, "hasRequiredVersion should return false when version is outdated")
        } finally {
            appDriver.close()
            platform.deleteFile(appDbPath)
        }
    }

    @Test
    fun deleteAllDownloadedData_removes_dictionary_and_translation_files() {
        val platform = platformDbSupport()
        val mgr = DataDbManager(platform, null)

        val dictPath = mgr.hasDictionary(Language.ENGLISH).let {
            platform.getDatabasePath(DataDbManager.dictionaryFileName(Language.ENGLISH))
        }
        val transPath = platform.getDatabasePath(DataDbManager.translationFileName(Language.DUTCH, Language.ENGLISH))

        platform.ensureDatabasesDir()

        if (!platform.fileExists(dictPath)) {
            val out = platform.openOutput(dictPath)
            out.write("test".encodeToByteArray(), 0, 4)
            out.close()
        }
        if (!platform.fileExists(transPath)) {
            val out = platform.openOutput(transPath)
            out.write("test".encodeToByteArray(), 0, 4)
            out.close()
        }

        assertTrue(platform.fileExists(dictPath), "Dictionary file should exist before deletion")
        assertTrue(platform.fileExists(transPath), "Translation file should exist before deletion")

        mgr.deleteAllDownloadedData()

        assertFalse(platform.fileExists(dictPath), "Dictionary file should be deleted")
        assertFalse(platform.fileExists(transPath), "Translation file should be deleted")
    }

    @Test
    fun deleteAllDownloadedData_clears_version_setting() {
        val platform = platformDbSupport()
        val appDbPath = platform.getDatabasePath("test_delete_clears_version.db")
        if (platform.fileExists(appDbPath)) {
            platform.deleteFile(appDbPath)
        }

        val appDriver = platform.createAppDataDriver(appDbPath)
        val appDb = DatabaseProvider.createAppDatabase(appDriver)
        val settingsRepo = SettingsRepository(appDb)

        try {
            val mgr = DataDbManager(platform, settingsRepo)

            settingsRepo.insert(
                Setting(
                    id = Setting.Name.DATA_VERSION,
                    value = Json.parseToJsonElement("\"v0\"")
                )
            )

            val versionBefore = settingsRepo.getById(Setting.Name.DATA_VERSION)
            assertTrue(versionBefore != null, "Version should exist before deletion")

            mgr.deleteAllDownloadedData()

            val versionAfter = settingsRepo.getById(Setting.Name.DATA_VERSION)
            assertTrue(versionAfter == null, "Version should be cleared after deleteAllDownloadedData")
        } finally {
            appDriver.close()
            platform.deleteFile(appDbPath)
        }
    }

    @Test
    fun getDatabasePath_with_empty_string_returns_directory() {
        val platform = platformDbSupport()

        platform.ensureDatabasesDir()
        val dirPath = platform.getDatabasePath("")

        assertTrue(dirPath.toString().isNotEmpty(), "Directory path should not be empty")
        assertTrue(platform.fileExists(dirPath), "Directory should exist after ensureDatabasesDir")

        val testFile = platform.getDatabasePath("test_file.db")
        assertTrue(testFile.toString().startsWith(dirPath.toString()), "File path should be within directory")
    }

    @Test
    fun listFiles_on_databases_directory_works() {
        val platform = platformDbSupport()

        platform.ensureDatabasesDir()

        val dictPath = platform.getDatabasePath(DataDbManager.dictionaryFileName(Language.DUTCH))
        val transPath = platform.getDatabasePath(DataDbManager.translationFileName(Language.ENGLISH, Language.RUSSIAN))

        val out1 = platform.openOutput(dictPath)
        out1.write("test1".encodeToByteArray(), 0, 5)
        out1.close()

        val out2 = platform.openOutput(transPath)
        out2.write("test2".encodeToByteArray(), 0, 5)
        out2.close()

        try {
            val dirPath = platform.getDatabasePath("")
            val files = platform.listFiles(dirPath)

            assertTrue(files.isNotEmpty(), "Should list files in directory")

            val fileNames = files.map { it.name }
            assertTrue(
                fileNames.any { it.startsWith("dictionary_") },
                "Should find dictionary files"
            )
            assertTrue(
                fileNames.any { it.startsWith("translation_") },
                "Should find translation files"
            )
        } finally {
            platform.deleteFile(dictPath)
            platform.deleteFile(transPath)
        }
    }
}