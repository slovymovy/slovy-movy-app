package com.slovy.slovymovyapp.data.export

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import com.slovy.slovymovyapp.data.local.LocalDbManager
import com.slovy.slovymovyapp.data.remote.DataDbManager
import com.slovy.slovymovyapp.data.remote.PlatformDbSupport
import com.slovy.slovymovyapp.data.remote.PlatformFileInput
import com.slovy.slovymovyapp.data.remote.PlatformFileOutput
import com.slovy.slovymovyapp.data.remote.standardAppDataDatabaseFileNames
import com.slovy.slovymovyapp.data.remote.standardAppDataFileNamesWithSidecars
import com.slovy.slovymovyapp.data.settings.Setting
import com.slovy.slovymovyapp.data.settings.SettingsRepository
import com.slovy.slovymovyapp.db.AppDatabase
import com.slovy.slovymovyapp.dictionary.DictionaryDatabase
import com.slovy.slovymovyapp.translation.TranslationDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.io.files.Path
import kotlinx.serialization.json.JsonPrimitive

data class AppDataImportApplyResult(
    val importedFiles: List<String>,
)

object AppDataImportApplier {
    const val STAGED_IMPORT_FILENAME = "slovymovy-db-import-staged.tar"

    suspend fun applyStagedImportIfPresent(platform: PlatformDbSupport): AppDataImportApplyResult? =
        withContext(Dispatchers.IO) {
            val archivePath = stagedImportPath(platform)
            if (!platform.fileExists(archivePath)) return@withContext null

            val extracted = mutableMapOf<String, Path>()
            val backups = mutableMapOf<String, Path>()
            val restoredBackups = mutableSetOf<Path>()
            var importSucceeded = false
            try {
                extractArchive(platform, archivePath, extracted)
                require("app.db" in extracted) { "Import archive is missing app.db." }
                validateExtractedDatabases(platform, extracted)
                replaceAppDataFiles(platform, extracted, backups)
                writePostImportSettings(platform)
                platform.deleteFile(archivePath)
                importSucceeded = true
                AppDataImportApplyResult(importedFiles = extracted.keys.sorted())
            } catch (t: Throwable) {
                restoredBackups += restoreBackups(platform, backups)
                platform.deleteFile(archivePath)
                throw t
            } finally {
                extracted.values.forEach { platform.deleteFile(it) }
                val backupsSafeToDelete = if (importSucceeded) backups.values else restoredBackups
                backupsSafeToDelete.forEach { platform.deleteFile(it) }
            }
        }

    internal fun stagedImportPath(platform: PlatformDbSupport): Path =
        platform.getDatabasePath(STAGED_IMPORT_FILENAME)

    private fun extractArchive(
        platform: PlatformDbSupport,
        archivePath: Path,
        extracted: MutableMap<String, Path>,
    ) {
        val input = platform.openInput(archivePath)
        try {
            val header = ByteArray(TAR_BLOCK_SIZE)
            while (true) {
                val headerBytes = input.readFullyOrEnd(header)
                if (headerBytes == 0) break
                require(headerBytes == TAR_BLOCK_SIZE) { "Truncated tar header." }
                if (header.all { it == 0.toByte() }) break

                val name = header.readTarString(0, 100)
                require(name in standardAppDataDatabaseFileNames) {
                    "Import archive contains unsupported entry: $name"
                }
                require(name !in extracted) { "Import archive contains duplicate entry: $name" }

                val size = header.readTarOctal(124, 12)
                require(size >= 0) { "Import archive contains an invalid size for $name." }
                val tempPath = platform.getDatabasePath("slovymovy-import-${name}.part")
                if (platform.fileExists(tempPath)) {
                    platform.deleteFile(tempPath)
                }
                platform.openOutput(tempPath).useOutput { output ->
                    input.copyExactlyTo(output, size)
                }
                extracted[name] = tempPath
                input.skipTarPadding(size)
            }
        } finally {
            input.close()
        }
    }

    private fun validateExtractedDatabases(
        platform: PlatformDbSupport,
        extracted: Map<String, Path>,
    ) {
        extracted.forEach { (name, path) ->
            val validation = when (name) {
                "app.db" -> ImportedDatabaseValidation(
                    driver = platform.createAppDataDriver(path, readOnly = true),
                    maxSchemaVersion = AppDatabase.Schema.version,
                    requiredTables = listOf("settings", "favorites", "card", "review_log"),
                )
                LocalDbManager.LOCAL_DICTIONARY_FILENAME -> ImportedDatabaseValidation(
                    driver = platform.createDictionaryDataDriver(path, readOnly = true),
                    maxSchemaVersion = DictionaryDatabase.Schema.version,
                    requiredTables = listOf("lemma", "sense"),
                )
                LocalDbManager.LOCAL_TRANSLATION_FILENAME -> ImportedDatabaseValidation(
                    driver = platform.createTranslationDataDriver(path, readOnly = true),
                    maxSchemaVersion = TranslationDatabase.Schema.version,
                    requiredTables = listOf("sense_translation"),
                )
                else -> error("Unsupported imported database: $name")
            }
            try {
                validateSqliteDatabase(name, validation)
            } finally {
                validation.driver.close()
            }
        }
    }

    private fun validateSqliteDatabase(
        name: String,
        validation: ImportedDatabaseValidation,
    ) {
        val driver = validation.driver
        val integrity = driver.executeQuery(
            identifier = null,
            sql = sqliteIntegrityCheckSql(),
            mapper = { cursor ->
                cursor.next()
                QueryResult.Value(cursor.getString(0) ?: "")
            },
            parameters = 0,
        ).value
        require(integrity == "ok") { "Imported $name failed SQLite integrity check: $integrity" }

        val schemaVersion = driver.executeQuery(
            identifier = null,
            sql = sqliteUserVersionSql(),
            mapper = { cursor ->
                cursor.next()
                QueryResult.Value(cursor.getLong(0) ?: 0L)
            },
            parameters = 0,
        ).value
        require(schemaVersion in 1..validation.maxSchemaVersion) {
            "Imported $name has unsupported schema version $schemaVersion."
        }

        validation.requiredTables.forEach { table ->
            require(driver.hasTable(table)) { "Imported $name is missing required table: $table" }
        }
    }

    private fun SqlDriver.hasTable(table: String): Boolean {
        var hasTable = false
        executeQuery(
            identifier = null,
            sql = sqliteTableExistsSql(),
            mapper = { cursor ->
                val nextResult = cursor.next()
                val hasRow = when (nextResult) {
                    is QueryResult.Value -> nextResult.value
                    else -> nextResult.value
                }
                hasTable = hasRow
                QueryResult.Value(hasRow)
            },
            parameters = 1,
            binders = { bindString(0, table) },
        )
        return hasTable
    }

    private fun replaceAppDataFiles(
        platform: PlatformDbSupport,
        extracted: Map<String, Path>,
        backups: MutableMap<String, Path>,
    ) {
        platform.ensureDatabasesDir()
        backupExistingAppDataFiles(platform, backups)

        standardAppDataDatabaseFileNames.forEach { fileName ->
            val destination = platform.getDatabasePath(fileName)
            val extractedPath = extracted[fileName]
            if (extractedPath == null) {
                return@forEach
            }

            if (!platform.moveFile(extractedPath, destination)) {
                error("Failed to install imported $fileName.")
            }
        }
    }

    private fun backupExistingAppDataFiles(
        platform: PlatformDbSupport,
        backups: MutableMap<String, Path>,
    ) {
        standardAppDataFileNamesWithSidecars.forEach { fileName ->
            val sourcePath = platform.getDatabasePath(fileName)
            if (!platform.fileExists(sourcePath)) return@forEach

            val backupPath = platform.getDatabasePath("slovymovy-import-backup-$fileName")
            if (platform.fileExists(backupPath)) {
                platform.deleteFile(backupPath)
            }
            if (!platform.moveFile(sourcePath, backupPath)) {
                error("Failed to back up existing $fileName.")
            }
            backups[fileName] = backupPath
        }
    }

    private suspend fun writePostImportSettings(platform: PlatformDbSupport) {
        DataDbManager.openAppDatabaseHolder(platform).use { holder ->
            val repository = SettingsRepository(holder.database)
            repository.setDeveloperMode(true)
            repository.insert(
                Setting(
                    id = Setting.Name.DATA_VERSION,
                    value = JsonPrimitive(DataDbManager.VERSION),
                )
            )
        }
    }

    private fun restoreBackups(
        platform: PlatformDbSupport,
        backups: Map<String, Path>,
    ): Set<Path> {
        val restored = mutableSetOf<Path>()
        standardAppDataFileNamesWithSidecars.forEach { fileName ->
            val destination = platform.getDatabasePath(fileName)
            if (platform.fileExists(destination)) {
                platform.deleteFile(destination)
            }
        }
        backups.forEach { (fileName, backupPath) ->
            val destination = platform.getDatabasePath(fileName)
            if (platform.fileExists(backupPath)) {
                if (platform.moveFile(backupPath, destination)) {
                    restored += backupPath
                }
            }
        }
        return restored
    }

    private fun PlatformFileInput.readFullyOrEnd(buffer: ByteArray): Int {
        var total = 0
        while (total < buffer.size) {
            val read = read(buffer, total, buffer.size - total)
            if (read < 0) return total
            total += read
        }
        return total
    }

    private fun PlatformFileInput.copyExactlyTo(
        output: PlatformFileOutput,
        size: Long,
    ) {
        val buffer = ByteArray(COPY_BUFFER_SIZE)
        var remaining = size
        while (remaining > 0) {
            val read = read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            require(read > 0) { "Truncated tar entry." }
            output.write(buffer, 0, read)
            remaining -= read.toLong()
        }
        output.flush()
    }

    private fun PlatformFileInput.skipTarPadding(size: Long) {
        val padding = (TAR_BLOCK_SIZE - (size % TAR_BLOCK_SIZE)).mod(TAR_BLOCK_SIZE)
        if (padding == 0) return
        val buffer = ByteArray(padding)
        val read = readFullyOrEnd(buffer)
        require(read == padding) { "Truncated tar padding." }
    }

    private inline fun PlatformFileOutput.useOutput(block: (PlatformFileOutput) -> Unit) {
        try {
            block(this)
        } finally {
            close()
        }
    }

    private fun ByteArray.readTarString(offset: Int, length: Int): String {
        val end = (offset until offset + length)
            .firstOrNull { this[it] == 0.toByte() }
            ?: (offset + length)
        return decodeToString(offset, end).trim()
    }

    private fun ByteArray.readTarOctal(offset: Int, length: Int): Long {
        val value = readTarString(offset, length).trim()
        require(value.isNotEmpty()) { "Missing tar octal value." }
        return value.toLong(radix = 8)
    }

    private fun sqliteIntegrityCheckSql(): String =
        "PRAGMA" + " integrity_check"

    private fun sqliteUserVersionSql(): String =
        "PRAGMA" + " user_version"

    private fun sqliteTableExistsSql(): String =
        "SELECT 1 FROM " + "sqlite_master WHERE type='table' AND name=? LIMIT 1"

    private data class ImportedDatabaseValidation(
        val driver: SqlDriver,
        val maxSchemaVersion: Long,
        val requiredTables: List<String>,
    )

    private const val TAR_BLOCK_SIZE = 512
    private const val COPY_BUFFER_SIZE = 8 * 1024
}
