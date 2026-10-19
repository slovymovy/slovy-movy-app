package com.slovy.slovymovyapp.data.export

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import com.slovy.slovymovyapp.data.db.DatabaseProvider
import com.slovy.slovymovyapp.data.local.LocalDbManager
import com.slovy.slovymovyapp.data.remote.DataDbManager
import com.slovy.slovymovyapp.data.remote.PlatformDbSupport
import com.slovy.slovymovyapp.data.remote.copyTo
import com.slovy.slovymovyapp.data.remote.use
import com.slovy.slovymovyapp.data.settings.Setting
import com.slovy.slovymovyapp.data.settings.SettingsRepository
import com.slovy.slovymovyapp.db.AppDatabase
import com.slovy.slovymovyapp.test.BaseTest
import com.slovy.slovymovyapp.test.TestContext
import com.slovy.slovymovyapp.test.testPlatformDbSupport
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppDataImportManagerTest : BaseTest() {

    private val appDb = "app.db"
    private val localDict = LocalDbManager.LOCAL_DICTIONARY_FILENAME
    private val localTrans = LocalDbManager.LOCAL_TRANSLATION_FILENAME
    private val staged = AppDataImportManager.STAGED_ARCHIVE_NAME

    @Test
    fun appliesArchiveAndCarriesDeviceSettingsOver() = runTest {
        TestContext.runInExportTestEnvironment {
            val platform = testPlatformDbSupport()
            createAppDb(
                platform, appDb,
                Setting.Name.TEST_PROPERTY to JsonPrimitive("device"),
                Setting.Name.DATA_VERSION to JsonPrimitive("device-version"),
                Setting.Name.DEVELOPER_MODE to JsonPrimitive(true),
            )
            createAppDb(
                platform, "source-app.db",
                Setting.Name.TEST_PROPERTY to JsonPrimitive("imported"),
                Setting.Name.DATA_VERSION to JsonPrimitive("imported-version"),
                Setting.Name.DEVELOPER_MODE to JsonPrimitive(false),
                Setting.Name.STUDY_AUTOPLAY to JsonPrimitive(true),
            )
            createLocalDictionary(platform, "source-dict.db")
            createLocalTranslation(platform, "source-trans.db")
            stageArchive(platform, appDb to "source-app.db", localDict to "source-dict.db", localTrans to "source-trans.db")
            writeText(platform, "$appDb-journal", "hot rollback journal of the old database")

            val result = manager(platform).applyStagedImportIfPresent()

            assertNotNull(result, "A staged archive should be applied")
            assertEquals(listOf(appDb, localDict, localTrans).sorted(), result.installedFiles, "All entries should install")
            assertFalse(
                platform.fileExists(platform.getDatabasePath("$appDb-journal")),
                "The old database's rollback journal must not be replayed into the imported one",
            )
            assertEquals(emptyList(), result.removedFiles, "Nothing was absent from the archive")
            val settings = readSettings(platform, appDb)
            assertEquals("imported", settings[Setting.Name.TEST_PROPERTY]?.jsonPrimitive?.content, "User data comes from the archive")
            assertEquals(true, settings[Setting.Name.STUDY_AUTOPLAY]?.jsonPrimitive?.content?.toBoolean(), "User settings come from the archive")
            assertEquals("device-version", settings[Setting.Name.DATA_VERSION]?.jsonPrimitive?.content, "DATA_VERSION is device-bound")
            assertEquals("true", settings[Setting.Name.DEVELOPER_MODE]?.jsonPrimitive?.content, "DEVELOPER_MODE is device-bound")
            assertTrue(platform.fileExists(platform.getDatabasePath(localDict)), "Local dictionary should be installed")
            assertTrue(platform.fileExists(platform.getDatabasePath(localTrans)), "Local translation should be installed")
            assertFalse(AppDataImportManager.hasStagedImport(platform), "Staged archive should be consumed")
            assertNoImportLeftovers(platform)
        }
    }

    @Test
    fun removesLocalCachesAbsentFromArchive() = runTest {
        TestContext.runInExportTestEnvironment {
            val platform = testPlatformDbSupport()
            createAppDb(platform, appDb, Setting.Name.TEST_PROPERTY to JsonPrimitive("device"))
            createLocalDictionary(platform, localDict)
            createAppDb(platform, "source-app.db", Setting.Name.TEST_PROPERTY to JsonPrimitive("imported"))
            stageArchive(platform, appDb to "source-app.db")

            val result = manager(platform).applyStagedImportIfPresent()

            assertNotNull(result, "A staged archive should be applied")
            assertEquals(listOf(localDict), result.removedFiles, "The device-only cache should be reported as removed")
            assertFalse(platform.fileExists(platform.getDatabasePath(localDict)), "The archive is the whole state")
            assertNoImportLeftovers(platform)
        }
    }

    @Test
    fun freshDeviceDropsImportedDeviceBoundSettings() = runTest {
        TestContext.runInExportTestEnvironment {
            val platform = testPlatformDbSupport()
            createAppDb(
                platform, "source-app.db",
                Setting.Name.TEST_PROPERTY to JsonPrimitive("imported"),
                Setting.Name.DATA_VERSION to JsonPrimitive("imported-version"),
            )
            stageArchive(platform, appDb to "source-app.db")

            val result = manager(platform).applyStagedImportIfPresent()

            assertNotNull(result, "A staged archive should be applied")
            val settings = readSettings(platform, appDb)
            assertEquals("imported", settings[Setting.Name.TEST_PROPERTY]?.jsonPrimitive?.content, "User data comes from the archive")
            assertNull(settings[Setting.Name.DATA_VERSION], "A device without downloads must not inherit a data version")
            assertNoImportLeftovers(platform)
        }
    }

    @Test
    fun rejectsArchiveWithoutAppDb() = runTest {
        TestContext.runInExportTestEnvironment {
            val platform = testPlatformDbSupport()
            createAppDb(platform, appDb, Setting.Name.TEST_PROPERTY to JsonPrimitive("device"))
            createLocalDictionary(platform, "source-dict.db")
            stageArchive(platform, localDict to "source-dict.db")

            assertFailsWith<IllegalArgumentException> { manager(platform).applyStagedImportIfPresent() }

            assertDeviceUntouched(platform)
        }
    }

    @Test
    fun rejectsNewerSchemaVersion() = runTest {
        TestContext.runInExportTestEnvironment {
            val platform = testPlatformDbSupport()
            createAppDb(platform, appDb, Setting.Name.TEST_PROPERTY to JsonPrimitive("device"))
            createAppDb(platform, "source-app.db", Setting.Name.TEST_PROPERTY to JsonPrimitive("imported"))
            val futureVersion = AppDatabase.Schema.version + 1
            platform.createAppDataDriver(platform.getDatabasePath("source-app.db")).also { driver ->
                driver.execute(null, "PRAGMA user_version = $futureVersion", 0)
                driver.close()
            }
            stageArchive(platform, appDb to "source-app.db")

            val failure = assertFailsWith<IllegalArgumentException> { manager(platform).applyStagedImportIfPresent() }

            assertTrue(failure.message.orEmpty().contains("schema version"), "Failure should name the schema version: ${failure.message}")
            assertDeviceUntouched(platform)
        }
    }

    @Test
    fun rejectsPayloadThatIsNotSqlite() = runTest {
        TestContext.runInExportTestEnvironment {
            val platform = testPlatformDbSupport()
            createAppDb(platform, appDb, Setting.Name.TEST_PROPERTY to JsonPrimitive("device"))
            platform.openOutput(platform.getDatabasePath("garbage.db")).use { output ->
                val bytes = ByteArray(4096) { 0x41 }
                output.write(bytes, 0, bytes.size)
                output.flush()
            }
            stageArchive(platform, appDb to "garbage.db")

            assertFailsWith<IllegalArgumentException> { manager(platform).applyStagedImportIfPresent() }

            assertDeviceUntouched(platform)
        }
    }

    @Test
    fun rejectsWrongDatabaseUnderAppDbName() = runTest {
        TestContext.runInExportTestEnvironment {
            val platform = testPlatformDbSupport()
            createAppDb(platform, appDb, Setting.Name.TEST_PROPERTY to JsonPrimitive("device"))
            createLocalDictionary(platform, "source-dict.db")
            stageArchive(platform, appDb to "source-dict.db")

            assertFailsWith<IllegalArgumentException> { manager(platform).applyStagedImportIfPresent() }

            assertDeviceUntouched(platform)
        }
    }

    @Test
    fun restoresOriginalsWhenInstallFails() = runTest {
        TestContext.runInExportTestEnvironment {
            val platform = testPlatformDbSupport()
            createAppDb(platform, appDb, Setting.Name.TEST_PROPERTY to JsonPrimitive("device"))
            createLocalDictionary(platform, localDict)
            val originalDictSize = platform.getFileSize(platform.getDatabasePath(localDict))
            createAppDb(platform, "source-app.db", Setting.Name.TEST_PROPERTY to JsonPrimitive("imported"))
            createLocalDictionary(platform, "source-dict.db")
            createLocalTranslation(platform, "source-trans.db")
            stageArchive(platform, appDb to "source-app.db", localDict to "source-dict.db", localTrans to "source-trans.db")
            val failingStore = FailingMoveStore(PlatformAppDataFileStore(platform), failWhenMovingTo = localTrans)

            val failure = assertFailsWith<IllegalStateException> {
                AppDataImportManager(platform, failingStore).applyStagedImportIfPresent()
            }

            assertTrue(failure.message.orEmpty().contains(localTrans), "Failure should name the file: ${failure.message}")
            assertEquals(
                "device",
                readSettings(platform, appDb)[Setting.Name.TEST_PROPERTY]?.jsonPrimitive?.content,
                "The original app.db must be restored",
            )
            assertEquals(originalDictSize, platform.getFileSize(platform.getDatabasePath(localDict)), "The original local dictionary must be restored")
            assertFalse(platform.fileExists(platform.getDatabasePath(localTrans)), "The failed install must not leave a translation DB")
            assertFalse(AppDataImportManager.hasStagedImport(platform), "A failed archive must not be retried on every launch")
            assertNoImportLeftovers(platform)
        }
    }

    @Test
    fun nothingStagedIsNoOp() = runTest {
        TestContext.runInExportTestEnvironment {
            val platform = testPlatformDbSupport()
            createAppDb(platform, appDb, Setting.Name.TEST_PROPERTY to JsonPrimitive("device"))
            platform.openOutput(platform.getDatabasePath("$staged.part")).use { output ->
                output.write(ByteArray(10), 0, 10)
                output.flush()
            }

            assertFalse(AppDataImportManager.hasStagedImport(platform), "A partial archive is not staged")
            assertNull(manager(platform).applyStagedImportIfPresent(), "Nothing staged means nothing applied")
            assertEquals(
                "device",
                readSettings(platform, appDb)[Setting.Name.TEST_PROPERTY]?.jsonPrimitive?.content,
                "The app.db must be untouched",
            )
        }
    }

    @Test
    fun stageValidatesAndLeavesArchiveReadyForNextLaunch() = runTest {
        TestContext.runInExportTestEnvironment {
            val platform = testPlatformDbSupport()
            createAppDb(platform, appDb, Setting.Name.TEST_PROPERTY to JsonPrimitive("device"))
            createAppDb(platform, "source-app.db", Setting.Name.TEST_PROPERTY to JsonPrimitive("imported"))
            createLocalDictionary(platform, "source-dict.db")
            writeArchive(platform, "picked.tar", appDb to "source-app.db", localDict to "source-dict.db")

            val pickedPath = platform.getDatabasePath("picked.tar")
            val stagedImport = manager(platform).stage(platform.openInput(pickedPath), platform.getFileSize(pickedPath))

            assertEquals(listOf(appDb, localDict), stagedImport.entries, "Staging should report the archive entries")
            assertEquals(platform.getFileSize(platform.getDatabasePath("picked.tar")), stagedImport.sizeBytes, "Staged size is the archive size")
            assertEquals(stagedImport.sizeBytes, manager(platform).stagedImportSizeBytes(), "The staged archive should be discoverable")
            assertTrue(AppDataImportManager.hasStagedImport(platform), "A valid archive should be staged")
            assertEquals(
                "device",
                readSettings(platform, appDb)[Setting.Name.TEST_PROPERTY]?.jsonPrimitive?.content,
                "Staging must not touch the live app.db",
            )
            assertTrue(
                platform.fileExists(platform.getDatabasePath(AppDataImportManager.STAGE_MARKER_NAME)),
                "Staging should record its validation for the next launch",
            )

            manager(platform).discardStagedImport()
            assertFalse(AppDataImportManager.hasStagedImport(platform), "Discarding removes the staged archive")
            assertNoImportLeftovers(platform)
        }
    }

    @Test
    fun stageRejectsInvalidArchiveWithoutStagingIt() = runTest {
        TestContext.runInExportTestEnvironment {
            val platform = testPlatformDbSupport()
            createLocalDictionary(platform, "source-dict.db")
            writeArchive(platform, "picked.tar", localDict to "source-dict.db")

            assertFailsWith<IllegalArgumentException> {
                manager(platform).stage(platform.openInput(platform.getDatabasePath("picked.tar")), sizeBytes = null)
            }

            assertFalse(AppDataImportManager.hasStagedImport(platform), "An invalid archive must not be staged")
            assertNull(manager(platform).stagedImportSizeBytes(), "Nothing is staged after a rejection")
            assertNoImportLeftovers(platform)
        }
    }


    @Test
    fun retriesInterruptedAttemptFromDeviceSettingsSnapshot() = runTest {
        TestContext.runInExportTestEnvironment {
            val platform = testPlatformDbSupport()
            // A previous launch backed the device app.db up, installed the imported one, then died
            // before deleting the archive. Its settings snapshot still names the device values.
            createAppDb(
                platform, "${AppDataImportManager.BACKUP_PREFIX}$appDb",
                Setting.Name.TEST_PROPERTY to JsonPrimitive("device"),
                Setting.Name.DATA_VERSION to JsonPrimitive("device-version"),
            )
            createAppDb(
                platform, appDb,
                Setting.Name.TEST_PROPERTY to JsonPrimitive("imported"),
                Setting.Name.DATA_VERSION to JsonPrimitive("imported-version"),
            )
            createAppDb(
                platform, "source-app.db",
                Setting.Name.TEST_PROPERTY to JsonPrimitive("imported"),
                Setting.Name.DATA_VERSION to JsonPrimitive("imported-version"),
            )
            stageArchive(platform, appDb to "source-app.db")
            writeText(
                platform, AppDataImportManager.CARRIED_SETTINGS_NAME,
                """{"settings":{"DATA_VERSION":"device-version","DEVELOPER_MODE":null},"databases":["app.db"]}""",
            )
            assertTrue(AppDataImportManager.hasPendingStartupWork(platform), "An interrupted attempt needs the gate")

            val result = manager(platform).applyStagedImportIfPresent()

            assertNotNull(result, "The retry should apply the archive")
            val settings = readSettings(platform, appDb)
            assertEquals("imported", settings[Setting.Name.TEST_PROPERTY]?.jsonPrimitive?.content, "User data comes from the archive")
            assertEquals(
                "device-version",
                settings[Setting.Name.DATA_VERSION]?.jsonPrimitive?.content,
                "DATA_VERSION must come from the snapshot, not from the half-installed app.db",
            )
            assertFalse(AppDataImportManager.hasPendingStartupWork(platform), "The retry should finish the attempt")
            assertNoImportLeftovers(platform)
        }
    }

    @Test
    fun retryDropsDatabasesTheFirstAttemptInstalledOnAFreshDevice() = runTest {
        TestContext.runInExportTestEnvironment {
            val platform = testPlatformDbSupport()
            // The device only had app.db. The interrupted attempt backed it up and installed app.db
            // and the local dictionary; the dictionary has no backup because the device never had one.
            createAppDb(platform, "${AppDataImportManager.BACKUP_PREFIX}$appDb", Setting.Name.TEST_PROPERTY to JsonPrimitive("device"))
            createAppDb(platform, appDb, Setting.Name.TEST_PROPERTY to JsonPrimitive("imported"))
            createLocalDictionary(platform, localDict)
            createAppDb(platform, "source-app.db", Setting.Name.TEST_PROPERTY to JsonPrimitive("imported"))
            createLocalDictionary(platform, "source-dict.db")
            stageArchive(platform, appDb to "source-app.db", localDict to "source-dict.db")
            writeText(
                platform, AppDataImportManager.CARRIED_SETTINGS_NAME,
                """{"settings":{"DATA_VERSION":null},"databases":["app.db"]}""",
            )
            val failingStore = FailingMoveStore(PlatformAppDataFileStore(platform), failWhenMovingTo = localDict)

            assertFailsWith<IllegalStateException> {
                AppDataImportManager(platform, failingStore).applyStagedImportIfPresent()
            }

            assertEquals(
                "device",
                readSettings(platform, appDb)[Setting.Name.TEST_PROPERTY]?.jsonPrimitive?.content,
                "The device app.db must be restored",
            )
            assertFalse(
                platform.fileExists(platform.getDatabasePath(localDict)),
                "A database the device never had must not survive the rolled-back attempt",
            )
            assertFalse(AppDataImportManager.hasPendingStartupWork(platform), "A failed retry must not run again")
            assertNoImportLeftovers(platform)
        }
    }

    @Test
    fun importsOverAnUnreadableDeviceAppDb() = runTest {
        TestContext.runInExportTestEnvironment {
            val platform = testPlatformDbSupport()
            writeText(platform, appDb, "this used to be a database")
            createAppDb(
                platform, "source-app.db",
                Setting.Name.TEST_PROPERTY to JsonPrimitive("imported"),
                Setting.Name.DATA_VERSION to JsonPrimitive("imported-version"),
            )
            stageArchive(platform, appDb to "source-app.db")

            val result = manager(platform).applyStagedImportIfPresent()

            assertNotNull(result, "A corrupt app.db is a reason to restore, not a reason to refuse")
            val settings = readSettings(platform, appDb)
            assertEquals("imported", settings[Setting.Name.TEST_PROPERTY]?.jsonPrimitive?.content, "User data comes from the archive")
            assertNull(settings[Setting.Name.DATA_VERSION], "An unreadable device app.db contributes no device-bound settings")
            assertNoImportLeftovers(platform)
        }
    }

    @Test
    fun stageRefusesAnArchiveThatCannotFit() = runTest {
        TestContext.runInExportTestEnvironment {
            val platform = testPlatformDbSupport()
            createAppDb(platform, appDb, Setting.Name.TEST_PROPERTY to JsonPrimitive("device"))
            createAppDb(platform, "source-app.db", Setting.Name.TEST_PROPERTY to JsonPrimitive("imported"))
            writeArchive(platform, "picked.tar", appDb to "source-app.db")

            val failure = assertFailsWith<IllegalStateException> {
                manager(platform).stage(platform.openInput(platform.getDatabasePath("picked.tar")), sizeBytes = Long.MAX_VALUE / 4)
            }

            assertTrue(failure.message.orEmpty().contains("free space"), "Failure should name free space: ${failure.message}")
            assertDeviceUntouched(platform)
        }
    }

    @Test
    fun finishesCleanupOfCommittedAttempt() = runTest {
        TestContext.runInExportTestEnvironment {
            val platform = testPlatformDbSupport()
            // The archive is gone, so the previous attempt committed; only backups and the snapshot remain.
            createAppDb(platform, "${AppDataImportManager.BACKUP_PREFIX}$appDb", Setting.Name.TEST_PROPERTY to JsonPrimitive("device"))
            createAppDb(platform, appDb, Setting.Name.TEST_PROPERTY to JsonPrimitive("imported"))
            writeText(
                platform, AppDataImportManager.CARRIED_SETTINGS_NAME,
                """{"settings":{"DATA_VERSION":"device-version"},"databases":["app.db"]}""",
            )
            assertTrue(AppDataImportManager.hasPendingStartupWork(platform), "Leftovers need the gate")

            assertNull(manager(platform).applyStagedImportIfPresent(), "Nothing is applied after a commit")

            assertEquals(
                "imported",
                readSettings(platform, appDb)[Setting.Name.TEST_PROPERTY]?.jsonPrimitive?.content,
                "A committed import must not be rolled back",
            )
            assertFalse(AppDataImportManager.hasPendingStartupWork(platform), "Cleanup should finish the attempt")
            assertNoImportLeftovers(platform)
        }
    }

    private fun manager(platform: PlatformDbSupport) =
        AppDataImportManager(platform, PlatformAppDataFileStore(platform))

    private suspend fun createAppDb(platform: PlatformDbSupport, name: String, vararg settings: Pair<Setting.Name, JsonElement>) {
        val driver = platform.createAppDataDriver(platform.getDatabasePath(name))
        try {
            val repository = SettingsRepository(DatabaseProvider.createAppDatabase(driver))
            settings.forEach { (id, value) -> repository.insert(Setting(id, value)) }
        } finally {
            driver.close()
        }
    }

    private fun createLocalDictionary(platform: PlatformDbSupport, name: String) {
        materialize(platform.createDictionaryDataDriver(platform.getDatabasePath(name), readOnly = false))
    }

    private fun createLocalTranslation(platform: PlatformDbSupport, name: String) {
        materialize(platform.createTranslationDataDriver(platform.getDatabasePath(name), readOnly = false))
    }

    /** The Android driver opens lazily, so touch the database before closing or no file is written. */
    private fun materialize(driver: SqlDriver) {
        try {
            driver.executeQuery(
                identifier = null,
                sql = "PRAGMA user_version",
                mapper = { cursor -> cursor.next(); QueryResult.Unit },
                parameters = 0,
            )
        } finally {
            driver.close()
        }
    }

    private suspend fun readSettings(platform: PlatformDbSupport, name: String): Map<Setting.Name, JsonElement> {
        require(name == appDb) { "Only the installed app.db is read through the app holder." }
        return DataDbManager.openAppDatabaseHolder(platform).use { holder ->
            SettingsRepository(holder.database).getAll().associate { it.id to it.value }
        }
    }

    /** Writes the staged archive from (entry name -> source file in the databases directory) pairs. */
    private fun stageArchive(platform: PlatformDbSupport, vararg entries: Pair<String, String>) {
        writeArchive(platform, staged, *entries)
    }

    /** Writes a tar at [destinationName] from (entry name -> source file) pairs and deletes the sources. */
    private fun writeArchive(platform: PlatformDbSupport, destinationName: String, vararg entries: Pair<String, String>) {
        writeTarArchiveToPath(
            platform = platform,
            destinationPath = platform.getDatabasePath(destinationName),
            entries = entries.map { (entryName, sourceName) ->
                val sourcePath = platform.getDatabasePath(sourceName)
                AppDataArchiveEntry(
                    name = entryName,
                    sizeBytes = platform.getFileSize(sourcePath) ?: error("Missing source $sourceName"),
                    writeContentsTo = { output -> platform.openInput(sourcePath).use { it.copyTo(output) } },
                )
            },
            lastModifiedEpochSeconds = 1_700_000_000L,
        )
        entries.forEach { (_, sourceName) -> platform.deleteFile(platform.getDatabasePath(sourceName)) }
    }

    private fun writeText(platform: PlatformDbSupport, name: String, text: String) {
        platform.openOutput(platform.getDatabasePath(name)).use { output ->
            val bytes = text.encodeToByteArray()
            output.write(bytes, 0, bytes.size)
            output.flush()
        }
    }

    private suspend fun assertDeviceUntouched(platform: PlatformDbSupport) {
        assertEquals(
            "device",
            readSettings(platform, appDb)[Setting.Name.TEST_PROPERTY]?.jsonPrimitive?.content,
            "A rejected archive must leave app.db alone",
        )
        assertFalse(AppDataImportManager.hasStagedImport(platform), "A rejected archive must be discarded")
        assertNoImportLeftovers(platform)
    }

    private fun assertNoImportLeftovers(platform: PlatformDbSupport) {
        val leftovers = platform.listFiles(platform.getDatabasePath(""))
            .map { it.name }
            .filter { it.startsWith("openwords-import-") && it != "$staged.part" && it != staged }
        assertEquals(emptyList(), leftovers, "Import must not leave scratch, extracted or backup files")
    }

    private class FailingMoveStore(
        private val delegate: AppDataFileStore,
        private val failWhenMovingTo: String,
    ) : AppDataFileStore by delegate {
        override fun move(from: String, to: String): Boolean =
            if (to == failWhenMovingTo) false else delegate.move(from, to)
    }
}
