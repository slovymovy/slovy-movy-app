package com.slovy.slovymovyapp.data.export

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import com.slovy.slovymovyapp.data.local.LocalDbManager
import com.slovy.slovymovyapp.data.remote.DataDbManager
import com.slovy.slovymovyapp.data.remote.PlatformDbSupport
import com.slovy.slovymovyapp.data.remote.PlatformFileInput
import com.slovy.slovymovyapp.data.remote.appDataFileNameWithSidecars
import com.slovy.slovymovyapp.data.remote.copyTo
import com.slovy.slovymovyapp.data.remote.readFully
import com.slovy.slovymovyapp.data.remote.standardAppDataDatabaseFileNames
import com.slovy.slovymovyapp.data.remote.use
import com.slovy.slovymovyapp.data.settings.Setting
import com.slovy.slovymovyapp.data.settings.SettingsRepository
import com.slovy.slovymovyapp.db.AppDatabase
import com.slovy.slovymovyapp.dictionary.DictionaryDatabase
import com.slovy.slovymovyapp.logging.AppLogger
import com.slovy.slovymovyapp.translation.TranslationDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

data class AppDataImportResult(
    /** Archive entries that now live in the databases directory. */
    val installedFiles: List<String>,
    /** Databases that existed on the device but were absent from the archive, so the import removed them. */
    val removedFiles: List<String>,
)

/** What the startup gate did with a staged archive, for the developer screen to report. */
sealed interface AppDataImportOutcome {
    data class Applied(val result: AppDataImportResult) : AppDataImportOutcome
    data class Failed(val message: String) : AppDataImportOutcome
}

data class StagedAppDataImport(
    val sizeBytes: Long,
    /** Archive entries, sorted. */
    val entries: List<String>,
)

/**
 * Stages and applies app-data export archives.
 *
 * [stage] copies a picked archive into the databases directory, validates it fully and leaves it
 * under [STAGED_ARCHIVE_NAME] with a marker recording that validation. [applyStagedImportIfPresent]
 * runs on the next launch, before any app database connection is opened for the process, because
 * it swaps `app.db` and the writable local caches on disk.
 *
 * The archive is the whole state. The device-bound settings in [carriedOverSettings] and the names
 * of the databases the device has are first snapshotted to a sidecar file, then every standard
 * database (with its `-wal`/`-shm`/`-journal` sidecars) is moved to a backup name, the archive's
 * databases are installed, and the snapshot's settings are written into the new `app.db`. Deleting
 * the archive is the commit point: an attempt interrupted before it is rolled back on the next
 * launch (backups restored, databases the device never had deleted) and retried from the snapshot;
 * leftovers after it are simply cleaned up. A backup that cannot be restored is kept on disk and
 * logged.
 */
class AppDataImportManager internal constructor(
    private val platform: PlatformDbSupport,
    private val files: AppDataFileStore,
) {
    constructor(platform: PlatformDbSupport) : this(platform, PlatformAppDataFileStore(platform))

    companion object {
        const val STAGED_ARCHIVE_NAME = "openwords-import-staged.tar"

        /**
         * Settings that describe this device rather than the user's data: which dictionary version
         * is downloaded here, which voices exist here, and whether the developer screen that staged
         * the import stays reachable. The device's value wins, including its absence.
         */
        val carriedOverSettings: List<Setting.Name> = listOf(
            Setting.Name.DATA_VERSION,
            Setting.Name.DEVELOPER_MODE,
            Setting.Name.ENABLED_VOICES,
            Setting.Name.VOICE_SETUP_SHOWN,
            Setting.Name.LEGACY_VOICE_MIGRATION_DONE,
        )

        /** Written by [stage] after full validation; lets the startup apply skip the expensive scratch checks. */
        internal const val STAGE_MARKER_NAME = "openwords-import-staged.json"

        /**
         * Snapshot of the device's [carriedOverSettings] and database names, taken before the first
         * file is touched: `{"settings": {NAME: value | null}, "databases": [name]}`.
         */
        internal const val CARRIED_SETTINGS_NAME = "openwords-import-carried.json"

        internal const val BACKUP_PREFIX = "openwords-import-backup-"

        private const val TAG = "AppDataImport"
        private const val APP_DB_NAME = "app.db"
        private const val STAGING_PART_NAME = "$STAGED_ARCHIVE_NAME.part"
        private const val EXTRACTED_PREFIX = "openwords-import-extracted-"
        private const val SCRATCH_PREFIX = "openwords-import-scratch-"
        private const val SQLITE_HEADER_SIZE = 100
        private const val FREE_SPACE_HEADROOM_BYTES = 16L * 1024 * 1024
        private val SQLITE_MAGIC = "SQLite format 3\u0000".encodeToByteArray()

        fun hasStagedImport(platform: PlatformDbSupport): Boolean =
            platform.fileExists(platform.getDatabasePath(STAGED_ARCHIVE_NAME))

        /** True when the next launch has to run the gate: an archive is staged or an attempt was interrupted. */
        fun hasPendingStartupWork(platform: PlatformDbSupport): Boolean =
            platform.fileExists(platform.getDatabasePath(STAGED_ARCHIVE_NAME)) ||
                platform.fileExists(platform.getDatabasePath(CARRIED_SETTINGS_NAME))
    }

    /**
     * Copies [input] into the databases directory, validates every database in it, and leaves it
     * staged for the next launch. Closes [input]. Nothing is staged when validation fails.
     * [sizeBytes] is the archive size when the picker knows it, so the free-space check can run
     * before the copy fills the disk; the copy, the extracted databases and their scratch copies
     * each need that much.
     */
    suspend fun stage(input: PlatformFileInput, sizeBytes: Long?): StagedAppDataImport = withContext(Dispatchers.IO) {
        files.delete(STAGING_PART_NAME)
        try {
            if (sizeBytes != null) requireFreeSpace(sizeBytes * 3)
            input.use { source ->
                files.openOutput(STAGING_PART_NAME).use { output -> source.copyTo(output) }
            }
            val sizeBytes = files.size(STAGING_PART_NAME) ?: 0L
            requireFreeSpace(sizeBytes * 2)
            val entries = extractAndValidate(STAGING_PART_NAME, trustedByStageMarker = false) { }
            files.delete(STAGED_ARCHIVE_NAME)
            files.delete(STAGE_MARKER_NAME)
            if (!files.move(STAGING_PART_NAME, STAGED_ARCHIVE_NAME)) {
                error("Failed to move the archive into place.")
            }
            val staged = StagedAppDataImport(sizeBytes = sizeBytes, entries = entries)
            writeJson(STAGE_MARKER_NAME, stageMarkerJson(staged))
            staged
        } catch (t: Throwable) {
            files.delete(STAGING_PART_NAME)
            throw t
        }
    }

    /** Size of the staged archive, or `null` when nothing is staged. */
    fun stagedImportSizeBytes(): Long? =
        if (files.exists(STAGED_ARCHIVE_NAME)) files.size(STAGED_ARCHIVE_NAME) else null

    fun discardStagedImport() {
        files.delete(STAGED_ARCHIVE_NAME)
        files.delete(STAGE_MARKER_NAME)
    }

    /**
     * Returns `null` when nothing is staged (after cleaning up a previously committed attempt).
     * Throws after rolling back when the archive cannot be applied.
     */
    suspend fun applyStagedImportIfPresent(): AppDataImportResult? = withContext(Dispatchers.IO) {
        val interrupted = files.exists(CARRIED_SETTINGS_NAME)
        if (!files.exists(STAGED_ARCHIVE_NAME)) {
            if (interrupted) {
                // The archive was deleted, so the previous attempt had committed; only its cleanup was lost.
                AppLogger.warn(TAG, "Finishing cleanup of an import that committed before the last exit", null)
                deleteBackups()
                files.delete(STAGE_MARKER_NAME)
                files.delete(CARRIED_SETTINGS_NAME)
            }
            return@withContext null
        }
        val recorded = readJson(CARRIED_SETTINGS_NAME)?.let { parseDeviceSnapshot(it) }
        if (interrupted) {
            AppLogger.warn(TAG, "Retrying an import that was interrupted before it committed", null)
            // Without a readable snapshot every database is treated as the device's own and kept.
            val deviceDatabases = recorded?.databases ?: standardAppDataDatabaseFileNames
            restoreBackups(installedWithoutBackup = standardAppDataDatabaseFileNames.filter { it !in deviceDatabases })
        }

        val backups = mutableListOf<Backup>()
        val installed = mutableListOf<String>()
        try {
            val trusted = stageMarkerMatchesArchive()
            requireFreeSpace((files.size(STAGED_ARCHIVE_NAME) ?: 0L) * if (trusted) 1 else 2)
            val snapshot = recorded
                ?: readDeviceSnapshot().also { writeJson(CARRIED_SETTINGS_NAME, deviceSnapshotJson(it)) }
            val installedEntries = extractAndValidate(STAGED_ARCHIVE_NAME, trustedByStageMarker = trusted) { extracted ->
                backupExistingDatabases(backups)
                extracted.forEach { (name, extractedName) ->
                    if (!files.move(extractedName, name)) {
                        error("Failed to install imported $name.")
                    }
                    installed += name
                }
                writeCarriedSettings(snapshot.settings)
            }
            // Commit point: from here on the import is final and only cleanup remains.
            files.delete(STAGED_ARCHIVE_NAME)
            files.delete(STAGE_MARKER_NAME)
            deleteBackups()
            files.delete(CARRIED_SETTINGS_NAME)
            val removed = backups.map { it.originalName }
                .filter { it in standardAppDataDatabaseFileNames && it !in installedEntries }
            AppDataImportResult(
                installedFiles = installedEntries,
                removedFiles = removed.sorted(),
            )
        } catch (t: Throwable) {
            restoreBackups(installedWithoutBackup = installed.filter { name -> backups.none { it.originalName == name } })
            files.delete(STAGED_ARCHIVE_NAME)
            files.delete(STAGE_MARKER_NAME)
            files.delete(CARRIED_SETTINGS_NAME)
            throw t
        }
    }

    /**
     * Extracts [archiveName] to temporary files, validates each database, runs [onValidated] with
     * the (entry name -> extracted file name) map, and removes whatever temporary files remain.
     * Returns the sorted entry names.
     */
    private suspend fun extractAndValidate(
        archiveName: String,
        trustedByStageMarker: Boolean,
        onValidated: suspend (Map<String, String>) -> Unit,
    ): List<String> {
        val extracted = linkedMapOf<String, String>()
        try {
            deleteScratchFiles()
            extractArchive(archiveName, extracted)
            require(APP_DB_NAME in extracted) { "Import archive does not contain $APP_DB_NAME." }
            extracted.forEach { (name, extractedName) ->
                validateDatabase(name, extractedName, checkScratchCopy = !trustedByStageMarker)
            }
            onValidated(extracted)
            return extracted.keys.sorted()
        } finally {
            extracted.values.forEach { files.delete(it) }
            deleteScratchFiles()
        }
    }

    private fun deleteScratchFiles() {
        files.listNames()
            .filter { it.startsWith(EXTRACTED_PREFIX) || it.startsWith(SCRATCH_PREFIX) }
            .forEach { files.delete(it) }
    }

    private fun deleteBackups() {
        files.listNames()
            .filter { it.startsWith(BACKUP_PREFIX) }
            .forEach { files.delete(it) }
    }

    private fun requireFreeSpace(requiredBytes: Long) {
        val available = platform.getAvailableBytesForPath(files.path(STAGED_ARCHIVE_NAME)) ?: return
        val needed = requiredBytes + FREE_SPACE_HEADROOM_BYTES
        check(available >= needed) {
            "Not enough free space to import: need ${needed / (1024 * 1024)} MB, have ${available / (1024 * 1024)} MB."
        }
    }

    private fun stageMarkerMatchesArchive(): Boolean {
        val marker = readJson(STAGE_MARKER_NAME) ?: return false
        val recordedSize = marker["sizeBytes"]?.jsonPrimitive?.longOrNull ?: return false
        return recordedSize == files.size(STAGED_ARCHIVE_NAME)
    }

    private fun extractArchive(archiveName: String, extracted: MutableMap<String, String>) {
        files.openInput(archiveName).use { input ->
            TarArchive.readEntries(input) { name, content ->
                require(name in standardAppDataDatabaseFileNames) {
                    "Import archive contains an unsupported entry: $name"
                }
                require(name !in extracted) { "Import archive contains $name twice." }
                val extractedName = "$EXTRACTED_PREFIX$name"
                files.openOutput(extractedName).use { output -> content.copyTo(output) }
                extracted[name] = extractedName
            }
        }
    }

    /**
     * Checks the SQLite header of the extracted file and, unless [stage] already did so for this
     * exact archive, opens a scratch copy with the normal writable driver so the real migrations
     * and `integrity_check` run against a throwaway file. The extracted file itself stays
     * untouched until it is installed.
     */
    private fun validateDatabase(name: String, extractedName: String, checkScratchCopy: Boolean) {
        val schema = schemaFor(name)
        val header = readSqliteHeader(extractedName)
        require(header.copyOfRange(0, SQLITE_MAGIC.size).contentEquals(SQLITE_MAGIC)) {
            "Imported $name is not a SQLite database."
        }
        val userVersion = readBigEndianInt(header, offset = 60).toLong()
        require(userVersion in 1..schema.version) {
            "Imported $name has schema version $userVersion; this app supports 1..${schema.version}."
        }
        if (!checkScratchCopy) return

        val scratchName = "$SCRATCH_PREFIX$name"
        if (!files.copy(extractedName, scratchName)) {
            error("Failed to copy imported $name for validation.")
        }
        try {
            checkScratchCopy(name, scratchName, schema)
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (t: Throwable) {
            // Opening runs the real migrations; a database of the wrong kind or with damaged
            // pages fails there with a driver-specific exception.
            throw IllegalArgumentException("Imported $name could not be opened or migrated: ${t.message}", t)
        } finally {
            appDataFileNameWithSidecars(scratchName).forEach { files.delete(it) }
        }
    }

    private fun checkScratchCopy(name: String, scratchName: String, schema: ImportedSchema) {
        val driver = openDriver(name, scratchName)
        try {
            val integrity = driver.scalarString("PRAGMA integrity_check")
            require(integrity == "ok") { "Imported $name failed the SQLite integrity check: $integrity" }
            schema.requiredTables.forEach { table ->
                require(driver.hasTable(table)) { "Imported $name has no '$table' table." }
            }
        } finally {
            driver.close()
        }
    }

    private fun readSqliteHeader(name: String): ByteArray {
        val header = ByteArray(SQLITE_HEADER_SIZE)
        val total = files.openInput(name).use { input -> input.readFully(header) }
        require(total == header.size) { "Imported $name is too small to be a SQLite database." }
        return header
    }

    private fun readBigEndianInt(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF shl 24) or
            (bytes[offset + 1].toInt() and 0xFF shl 16) or
            (bytes[offset + 2].toInt() and 0xFF shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)

    private fun openDriver(name: String, fileName: String): SqlDriver {
        val path = files.path(fileName)
        return when (name) {
            APP_DB_NAME -> platform.createAppDataDriver(path)
            LocalDbManager.LOCAL_DICTIONARY_FILENAME -> platform.createDictionaryDataDriver(path, readOnly = false)
            LocalDbManager.LOCAL_TRANSLATION_FILENAME -> platform.createTranslationDataDriver(path, readOnly = false)
            else -> error("Unsupported imported database: $name")
        }
    }

    private fun schemaFor(name: String): ImportedSchema = when (name) {
        APP_DB_NAME -> ImportedSchema(
            version = AppDatabase.Schema.version,
            requiredTables = listOf("settings", "favorites", "card", "review_log"),
        )
        LocalDbManager.LOCAL_DICTIONARY_FILENAME -> ImportedSchema(
            version = DictionaryDatabase.Schema.version,
            requiredTables = listOf("lemma"),
        )
        LocalDbManager.LOCAL_TRANSLATION_FILENAME -> ImportedSchema(
            version = TranslationDatabase.Schema.version,
            requiredTables = listOf("sense_translation"),
        )
        else -> error("Unsupported imported database: $name")
    }

    private fun SqlDriver.scalarString(sql: String): String? = executeQuery(
        identifier = null,
        sql = sql,
        mapper = { cursor ->
            QueryResult.Value(if (cursor.next().value) cursor.getString(0) else null)
        },
        parameters = 0,
    ).value

    private fun SqlDriver.hasTable(table: String): Boolean = executeQuery(
        identifier = null,
        sql = "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ? LIMIT 1",
        mapper = { cursor -> QueryResult.Value(cursor.next().value) },
        parameters = 1,
        binders = { bindString(0, table) },
    ).value

    /**
     * The device's values for [carriedOverSettings] (`null` means the device has no such row) and
     * the standard databases it has. An `app.db` that cannot be opened is about to be replaced
     * anyway, so it contributes no settings instead of blocking the import that would fix it.
     */
    private suspend fun readDeviceSnapshot(): DeviceSnapshot {
        val databases = standardAppDataDatabaseFileNames.filter { files.exists(it) }
        val absent = carriedOverSettings.associateWith { null }
        if (APP_DB_NAME !in databases) {
            return DeviceSnapshot(settings = absent, databases = databases)
        }
        val settings = try {
            DataDbManager.openAppDatabaseHolder(platform).use { holder ->
                val repository = SettingsRepository(holder.database)
                carriedOverSettings.associateWith { repository.getById(it)?.value }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            AppLogger.warn(TAG, "The device's $APP_DB_NAME is unreadable; importing without its device-bound settings", e)
            absent
        }
        return DeviceSnapshot(settings = settings, databases = databases)
    }

    private suspend fun writeCarriedSettings(carried: Map<Setting.Name, JsonElement?>) {
        DataDbManager.openAppDatabaseHolder(platform).use { holder ->
            val repository = SettingsRepository(holder.database)
            carried.forEach { (name, value) ->
                if (value == null) {
                    repository.deleteById(name)
                } else {
                    repository.insert(Setting(id = name, value = value))
                }
            }
        }
    }

    private fun deviceSnapshotJson(snapshot: DeviceSnapshot): JsonObject = JsonObject(
        mapOf(
            "settings" to JsonObject(snapshot.settings.entries.associate { (name, value) -> name.name to (value ?: JsonNull) }),
            "databases" to JsonArray(snapshot.databases.map { JsonPrimitive(it) }),
        )
    )

    /** `null` when [json] is not a snapshot this version wrote. */
    private fun parseDeviceSnapshot(json: JsonObject): DeviceSnapshot? {
        val settings = (json["settings"] as? JsonObject) ?: return null
        val databases = (json["databases"] as? JsonArray) ?: return null
        return DeviceSnapshot(
            settings = carriedOverSettings.associateWith { name -> settings[name.name]?.takeUnless { it is JsonNull } },
            databases = databases.mapNotNull { it.jsonPrimitive.contentOrNull },
        )
    }

    private fun stageMarkerJson(staged: StagedAppDataImport): JsonObject = JsonObject(
        mapOf(
            "sizeBytes" to JsonPrimitive(staged.sizeBytes),
            "entries" to JsonArray(staged.entries.map { JsonPrimitive(it) }),
        )
    )

    private fun writeJson(name: String, json: JsonObject) {
        val bytes = json.toString().encodeToByteArray()
        files.openOutput(name).use { output ->
            output.write(bytes, 0, bytes.size)
            output.flush()
        }
    }

    private fun readJson(name: String): JsonObject? {
        if (!files.exists(name)) return null
        val size = files.size(name)?.toInt() ?: return null
        val bytes = ByteArray(size)
        val read = files.openInput(name).use { input -> input.readFully(bytes) }
        return try {
            Json.parseToJsonElement(bytes.decodeToString(0, read)).jsonObject
        } catch (e: IllegalArgumentException) {
            AppLogger.warn(TAG, "Ignoring unreadable $name", e)
            null
        }
    }

    private fun backupExistingDatabases(backups: MutableList<Backup>) {
        standardAppDataDatabaseFileNames.flatMap { appDataFileNameWithSidecars(it) }.forEach { fileName ->
            if (!files.exists(fileName)) return@forEach
            val backupName = "$BACKUP_PREFIX$fileName"
            if (!files.move(fileName, backupName)) {
                error("Failed to back up existing $fileName.")
            }
            backups += Backup(originalName = fileName, backupName = backupName)
        }
    }

    /**
     * Puts every backed-up database back, dropping whatever currently sits under its name first,
     * including sidecars the installed copy may have grown, so a restored database never pairs
     * with a foreign `-wal`, `-shm` or `-journal`. [installedWithoutBackup] names databases the
     * device did not have before the attempt; those are deleted outright.
     */
    private fun restoreBackups(installedWithoutBackup: List<String>) {
        installedWithoutBackup.forEach { name ->
            appDataFileNameWithSidecars(name).forEach { files.delete(it) }
        }
        val unrestored = mutableListOf<String>()
        standardAppDataDatabaseFileNames.forEach { name ->
            val fileNames = appDataFileNameWithSidecars(name)
            if (fileNames.none { files.exists("$BACKUP_PREFIX$it") }) return@forEach
            fileNames.forEach { files.delete(it) }
            fileNames.forEach { fileName ->
                val backupName = "$BACKUP_PREFIX$fileName"
                if (files.exists(backupName) && !files.move(backupName, fileName)) {
                    unrestored += "$fileName (kept at ${files.path(backupName)})"
                }
            }
        }
        if (unrestored.isNotEmpty()) {
            AppLogger.error(TAG, "Import rollback could not restore: ${unrestored.joinToString()}", null)
        }
    }

    private data class Backup(val originalName: String, val backupName: String)

    private data class DeviceSnapshot(val settings: Map<Setting.Name, JsonElement?>, val databases: List<String>)

    private data class ImportedSchema(val version: Long, val requiredTables: List<String>)
}
