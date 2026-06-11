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
import com.slovy.slovymovyapp.test.BaseTest
import com.slovy.slovymovyapp.test.TestContext
import com.slovy.slovymovyapp.test.testPlatformDbSupport
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppDataImportApplierTest : BaseTest() {

    @Test
    fun stagedImport_replacesArchivedDatabases() = runTest {
        TestContext.runInExportTestEnvironment {
            val platform = testPlatformDbSupport()
            seedCurrentAppDb("current-app")

            val importedAppDb = buildAppDb(platform, "import-src-app.db", "imported-app")
            val importedDictDb = buildDb(platform, "import-src-dict.db") { path ->
                touchDatabase(platform.createDictionaryDataDriver(path, readOnly = false))
            }
            val importedTransDb = buildDb(platform, "import-src-trans.db") { path ->
                touchDatabase(platform.createTranslationDataDriver(path, readOnly = false))
            }
            writeArchive(
                platform,
                "app.db" to importedAppDb,
                LocalDbManager.LOCAL_DICTIONARY_FILENAME to importedDictDb,
                LocalDbManager.LOCAL_TRANSLATION_FILENAME to importedTransDb,
            )

            val result = AppDataImportApplier.applyStagedImportIfPresent(platform)

            assertNotNull(result, "Import should be applied when an archive is staged")
            assertEquals(
                listOf(
                    "app.db",
                    LocalDbManager.LOCAL_DICTIONARY_FILENAME,
                    LocalDbManager.LOCAL_TRANSLATION_FILENAME,
                ).sorted(),
                result.importedFiles,
            )

            val repository = SettingsRepository(testAppDatabaseHolder().database)
            assertEquals(
                "imported-app",
                repository.getById(Setting.Name.TEST_PROPERTY)?.value?.jsonPrimitive?.content,
                "Imported app.db should replace the existing one",
            )
            assertEquals(
                DataDbManager.VERSION,
                repository.getById(Setting.Name.DATA_VERSION)?.value?.jsonPrimitive?.content,
                "Import should pin DATA_VERSION to the current version",
            )
            assertTrue(
                repository.getDeveloperMode(default = false),
                "Import should keep developer mode enabled",
            )

            assertFalse(
                platform.fileExists(AppDataImportApplier.stagedImportPath(platform)),
                "Staged archive should be deleted after a successful import",
            )
            assertNoImportLeftovers(platform)
        }
    }

    @Test
    fun stagedImport_preservesDatabasesAbsentFromArchive() = runTest {
        TestContext.runInExportTestEnvironment {
            val platform = testPlatformDbSupport()
            val dictPath = platform.getDatabasePath(LocalDbManager.LOCAL_DICTIONARY_FILENAME)
            val marker = "do-not-touch".encodeToByteArray()
            platform.openOutput(dictPath).use { output ->
                output.write(marker, 0, marker.size)
                output.flush()
            }

            val importedAppDb = buildAppDb(platform, "import-src-app.db", "imported-app")
            writeArchive(platform, "app.db" to importedAppDb)

            val result = AppDataImportApplier.applyStagedImportIfPresent(platform)

            assertNotNull(result, "Import should be applied when an archive is staged")
            assertEquals(listOf("app.db"), result.importedFiles)
            assertTrue(
                platform.fileExists(dictPath),
                "A database absent from the archive must survive the import",
            )
            assertContentEquals(
                marker,
                readAllBytes(platform, dictPath),
                "A database absent from the archive must not be modified",
            )
            assertNoImportLeftovers(platform)
        }
    }

    @Test
    fun stagedImport_rollsBackWhenArchivedDatabaseIsInvalid() = runTest {
        TestContext.runInExportTestEnvironment {
            val platform = testPlatformDbSupport()
            seedCurrentAppDb("current-app")

            val garbagePath = buildDb(platform, "import-src-garbage.bin") { path ->
                val garbage = ByteArray(2048) { (it % 251).toByte() }
                platform.openOutput(path).use { output ->
                    output.write(garbage, 0, garbage.size)
                    output.flush()
                }
            }
            writeArchive(platform, "app.db" to garbagePath)

            assertFails("Importing a non-database file must fail validation") {
                AppDataImportApplier.applyStagedImportIfPresent(platform)
            }

            val repository = SettingsRepository(testAppDatabaseHolder().database)
            assertEquals(
                "current-app",
                repository.getById(Setting.Name.TEST_PROPERTY)?.value?.jsonPrimitive?.content,
                "Original app.db must be intact after a failed import",
            )
            assertFalse(
                platform.fileExists(AppDataImportApplier.stagedImportPath(platform)),
                "Staged archive should be deleted after a failed import so it is not retried",
            )
            assertNoImportLeftovers(platform)
        }
    }

    @Test
    fun stagedImport_rejectsArchiveWithoutAppDb() = runTest {
        TestContext.runInExportTestEnvironment {
            val platform = testPlatformDbSupport()
            val importedDictDb = buildDb(platform, "import-src-dict.db") { path ->
                touchDatabase(platform.createDictionaryDataDriver(path, readOnly = false))
            }
            writeArchive(platform, LocalDbManager.LOCAL_DICTIONARY_FILENAME to importedDictDb)

            val failure = assertFails("An archive without app.db must be rejected") {
                AppDataImportApplier.applyStagedImportIfPresent(platform)
            }
            assertTrue(
                failure.message.orEmpty().contains("app.db"),
                "Failure should explain the missing app.db, was: ${failure.message}",
            )
            assertFalse(
                platform.fileExists(platform.getDatabasePath(LocalDbManager.LOCAL_DICTIONARY_FILENAME)),
                "The dictionary from the rejected archive must not be installed",
            )
            assertNoImportLeftovers(platform)
        }
    }

    @Test
    fun stagedImport_sweepsStaleTempFilesWhenNoArchiveStaged() = runTest {
        TestContext.runInExportTestEnvironment {
            val platform = testPlatformDbSupport()
            platform.ensureDatabasesDir()
            val stalePath = platform.getDatabasePath("slovymovy-import-app.db.part")
            val stale = "stale".encodeToByteArray()
            platform.openOutput(stalePath).use { output ->
                output.write(stale, 0, stale.size)
                output.flush()
            }

            assertNull(
                AppDataImportApplier.applyStagedImportIfPresent(platform),
                "Nothing should be applied without a staged archive",
            )
            assertFalse(
                platform.fileExists(stalePath),
                "Stale import temp files should be swept at startup",
            )
        }
    }

    private suspend fun seedCurrentAppDb(testValue: String) {
        testAppDatabaseHolder().database.settingsQueries.insertSetting(
            id = Setting.Name.TEST_PROPERTY,
            json_value = JsonPrimitive(testValue),
        )
        // Release the connection so the applier can swap the file underneath.
        appDbHolder?.close()
        appDbHolder = null
    }

    private fun buildAppDb(platform: PlatformDbSupport, fileName: String, testValue: String): Path =
        buildDb(platform, fileName) { path ->
            val driver = platform.createAppDataDriver(path)
            try {
                DatabaseProvider.createAppDatabase(driver).settingsQueries.insertSetting(
                    id = Setting.Name.TEST_PROPERTY,
                    json_value = JsonPrimitive(testValue),
                )
            } finally {
                driver.close()
            }
        }

    private fun buildDb(
        platform: PlatformDbSupport,
        fileName: String,
        create: (Path) -> Unit,
    ): Path {
        val path = platform.getDatabasePath(fileName)
        platform.deleteFile(path)
        create(path)
        return path
    }

    // AndroidSqliteDriver opens lazily; run a query so the schema is created before closing.
    private fun touchDatabase(driver: SqlDriver) {
        try {
            driver.executeQuery(
                identifier = null,
                sql = "SELECT 1",
                mapper = { cursor ->
                    cursor.next()
                    QueryResult.Value(Unit)
                },
                parameters = 0,
            ).value
        } finally {
            driver.close()
        }
    }

    private fun writeArchive(platform: PlatformDbSupport, vararg entries: Pair<String, Path>) {
        val archiveEntries = entries.map { (name, path) ->
            val size = platform.getFileSize(path)
            assertNotNull(size, "Archive source $name must exist")
            AppDataArchiveEntry(
                name = name,
                sizeBytes = size,
                writeContentsTo = { output ->
                    platform.openInput(path).use { input -> input.copyTo(output) }
                },
            )
        }
        writeTarArchive(
            output = platform.openOutput(AppDataImportApplier.stagedImportPath(platform)),
            entries = archiveEntries,
            lastModifiedEpochSeconds = 0L,
        )
    }

    private fun readAllBytes(platform: PlatformDbSupport, path: Path): ByteArray {
        var bytes = ByteArray(0)
        platform.openInput(path).use { input ->
            val buffer = ByteArray(1024)
            while (true) {
                val read = input.read(buffer, 0, buffer.size)
                if (read <= 0) break
                bytes += buffer.copyOfRange(0, read)
            }
        }
        return bytes
    }

    private fun assertNoImportLeftovers(platform: PlatformDbSupport) {
        val databasesDir = platform.getDatabasePath("")
        if (!platform.fileExists(databasesDir)) return
        platform.listFiles(databasesDir).forEach { file ->
            assertFalse(
                file.name.startsWith("slovymovy-import-"),
                "Unexpected import temp/backup leftover: ${file.name}",
            )
        }
    }
}
