package com.slovy.slovymovyapp.data.remote

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import com.slovy.slovymovyapp.analytics.PerformanceMonitoring
import com.slovy.slovymovyapp.analytics.putAttributes
import com.slovy.slovymovyapp.analytics.useWithResult
import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.db.DatabaseProvider
import com.slovy.slovymovyapp.data.settings.Setting
import com.slovy.slovymovyapp.data.settings.SettingsRepository
import com.slovy.slovymovyapp.db.AppDatabase
import com.slovy.slovymovyapp.dictionary.DictionaryDatabase
import com.slovy.slovymovyapp.translation.TranslationDatabase
import io.ktor.client.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.io.files.Path
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.max
import kotlin.uuid.Uuid

/**
 * Remote data manager for prebuilt dictionary and translation databases.
 * It can download missing DB files, persist data version, and open read-only databases.
 *
 * App-only: lives in composeApp module.
 */
class DataDbManager(
    private val platform: PlatformDbSupport,
    private val settingsRepository: SettingsRepository,
    private val remoteDataProvider: RemoteDataProvider
) {
    /**
     * Thread-safe cache for read-only dictionary and translation databases.
     */
    internal val databaseCache = ReadOnlyDatabaseCache(platform)

    companion object {
        const val VERSION = "v15"

        private const val DICTIONARY_PREFIX = "dictionary_"
        private const val TRANSLATION_PREFIX = "translation_"
        private const val DB_EXTENSION = ".db"
        private const val PART_SUFFIX = ".part"

        /**
         * Lower bound for a plausible downloaded dictionary/translation DB. Anything smaller is
         * treated as corrupt and removed at startup so the next launch will re-download.
         *
         * Why: a 200-with-empty-body download or a torn rename can leave a 0-byte file at the
         * dest path. SQLite (via Android's SupportSQLiteOpenHelper) then opens it as a fresh
         * schema-less DB and the first real query fails with "no such table: lemma". A real
         * dictionary is in the megabytes; even an empty SQLite carrying our schema is well above
         * 16 KB, so this threshold catches the corrupt case without false positives.
         */
        internal const val MIN_VALID_DOWNLOADED_DB_BYTES: Long = 16L * 1024L

        /** Table that must exist in a non-corrupt dictionary DB. */
        private const val DICTIONARY_PROBE_TABLE = "lemma"

        /** Table that must exist in a non-corrupt translation DB. */
        private const val TRANSLATION_PROBE_TABLE = "sense_translation"

        fun dictionaryFileName(lang: Language): String = "$DICTIONARY_PREFIX${lang.code.lowercase()}$DB_EXTENSION"
        fun translationFileName(src: Language, tgt: Language): String =
            "$TRANSLATION_PREFIX${src.code.lowercase()}_${tgt.code.lowercase()}$DB_EXTENSION"

        fun openAppDatabase(platform: PlatformDbSupport): AppDatabase {
            return openAppDatabaseHolder(platform).database
        }

        /**
         * Opens the app database and returns a holder that allows proper cleanup.
         * Call [AppDatabaseHolder.close] when done to release the database connection.
         */
        fun openAppDatabaseHolder(platform: PlatformDbSupport): AppDatabaseHolder {
            val file = platform.getDatabasePath("app.db")
            val driver = platform.createAppDataDriver(file)
            val database = DatabaseProvider.createAppDatabase(driver)
            return AppDatabaseHolder(driver, database)
        }
    }

    private var cachedAvailableLanguages: List<AvailableLanguageInfo>? = null

    suspend fun ensureDictionary(
        lang: Language,
        onProgress: (DownloadProgress) -> Unit = {},
        cancelToken: CancelToken? = null
    ): Path {
        val name = dictionaryFileName(lang)
        return ensureFile(name, onProgress, cancelToken)
    }

    suspend fun deleteDictionary(lang: Language) {
        // Drain active leases, close drivers, then delete files while new leases stay blocked.
        databaseCache.closeAllForLanguageGuarded(lang) {
            val name = dictionaryFileName(lang)
            platform.deleteFile(platform.getDatabasePath(name))

            // Also delete all translations that use this language as the source
            val allLanguages = Language.entries
            allLanguages.forEach { targetLang ->
                if (targetLang != lang) {
                    val transName = translationFileName(lang, targetLang)
                    val transPath = platform.getDatabasePath(transName)
                    if (platform.fileExists(transPath)) {
                        platform.deleteFile(transPath)
                    }
                }
            }
        }
    }

    suspend fun ensureTranslation(
        src: Language,
        tgt: Language,
        onProgress: (DownloadProgress) -> Unit = {},
        cancelToken: CancelToken? = null
    ): Path {
        val name = translationFileName(src, tgt)
        return ensureFile(name, onProgress, cancelToken)
    }

    suspend fun deleteTranslation(src: Language, tgt: Language) {
        // Drain active leases, close driver, then delete file while new leases stay blocked.
        databaseCache.closeTranslationGuarded(src, tgt) {
            val name = translationFileName(src, tgt)
            platform.deleteFile(platform.getDatabasePath(name))
        }
    }

    /**
     * Removes downloaded dictionary/translation DB files that are too small to be valid SQLite
     * databases with our schema, plus any `.part` orphans from interrupted downloads. Should be
     * called at app startup, before any caller consults [hasDictionary]/[hasTranslation], so
     * routing can correctly send the user back through the download flow.
     */
    suspend fun cleanupCorruptDownloadedDbs() {
        PerformanceMonitoring.startTrace("data_db_cleanup_corrupt_downloaded").useWithResult {
            var checked = 0L
            var deletedPart = 0L
            var deletedSmall = 0L
            var deletedInvalidSchema = 0L
            try {
                // Drain active leases on every cached DB, close drivers, then run the cleanup with new
                // leases still blocked so we never race with a reader probing or growing a Pool.
                databaseCache.closeAllGuarded {
                    val databasesDir = platform.getDatabasePath("")
                    if (!platform.fileExists(databasesDir)) return@closeAllGuarded

                    val files = platform.listFiles(databasesDir)
                    files.forEach { file ->
                        val fileName = file.name
                        val isDownloadedDb =
                            fileName.startsWith(DICTIONARY_PREFIX) || fileName.startsWith(TRANSLATION_PREFIX)
                        if (!isDownloadedDb) return@forEach
                        checked += 1

                        if (fileName.endsWith(PART_SUFFIX)) {
                            platform.deleteFile(file)
                            deletedPart += 1
                            return@forEach
                        }

                        val size = platform.getFileSize(file)
                        if (size != null && size < MIN_VALID_DOWNLOADED_DB_BYTES) {
                            platform.deleteFile(file)
                            deletedSmall += 1
                            return@forEach
                        }

                        // Size alone won't catch a truncated download that landed past the 16 KB mark but
                        // lost the page holding the schema entry. Open the file and confirm the must-exist
                        // table is present.
                        val schemaOk = if (fileName.startsWith(DICTIONARY_PREFIX)) {
                            probeDownloadedDb(file, isDictionary = true)
                        } else {
                            probeDownloadedDb(file, isDictionary = false)
                        }
                        if (!schemaOk) {
                            platform.deleteFile(file)
                            deletedInvalidSchema += 1
                        }
                    }
                }
                if (deletedPart + deletedSmall + deletedInvalidSchema > 0L) {
                    markResult("cleaned")
                }
            } finally {
                val deleted = deletedPart + deletedSmall + deletedInvalidSchema
                putMetric("downloaded_files_checked", checked)
                putMetric("deleted_files", deleted)
                putMetric("part_orphans_deleted", deletedPart)
                putMetric("too_small_deleted", deletedSmall)
                putMetric("invalid_schema_deleted", deletedInvalidSchema)
            }
        }
    }

    /**
     * Opens the file with a read-only SqlDelight driver and confirms the must-exist table is
     * present. Returns false if the file is not a valid SQLite DB, the driver fails to open, or
     * the schema lookup fails for any reason. Caller is responsible for deleting the file when
     * this returns false. Must only be called on files that already exist on disk — otherwise the
     * underlying SupportSQLiteOpenHelper would auto-create an empty schema-less file.
     */
    private fun probeDownloadedDb(path: Path, isDictionary: Boolean): Boolean {
        val table = if (isDictionary) DICTIONARY_PROBE_TABLE else TRANSLATION_PROBE_TABLE
        val driver = try {
            if (isDictionary) platform.createDictionaryDataDriver(path, readOnly = true)
            else platform.createTranslationDataDriver(path, readOnly = true)
        } catch (_: Throwable) {
            return false
        }
        return try {
            var hasTable = false
            driver.executeQuery(
                identifier = null,
                sql = "SELECT 1 FROM sqlite_master WHERE type='table' AND name=? LIMIT 1",
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
            hasTable
        } catch (_: Throwable) {
            false
        } finally {
            runCatching { driver.close() }
        }
    }

    /**
     * Deletes all downloaded dictionary and translation databases.
     * Also clears the stored version setting.
     * Used when data version changes.
     */
    suspend fun deleteAllDownloadedData() {
        // Drain active leases, close every cached driver, then delete files while new leases stay blocked.
        databaseCache.closeAllGuarded {
            val databasesDir = platform.getDatabasePath("")
            val files = platform.listFiles(databasesDir)
            files.forEach { file ->
                val fileName = file.name
                if (fileName.startsWith(DICTIONARY_PREFIX) || fileName.startsWith(TRANSLATION_PREFIX)) {
                    platform.deleteFile(file)
                }
            }
            clearVersion()
        }
    }

    /**
     * Clears the stored data version from settings.
     */
    suspend fun clearVersion() {
        settingsRepository.deleteById(Setting.Name.DATA_VERSION)
    }

    /**
     * Closes all cached read-only database connections.
     * Call this when switching languages or during cleanup.
     */
    fun closeAllReadOnlyDatabases() {
        runBlocking { databaseCache.closeAll() }
    }

    /**
     * Returns the subset of [candidates] that have downloadable translation DBs
     * for the given [src] language on the remote bucket.
     */
    suspend fun downloadableTranslationTargets(src: Language, candidates: List<Language>): List<Language> {
        val available = fetchAvailableLanguages()
        val targets = available.find { it.language == src }
            ?.availableTranslations?.map { it.targetLanguage } ?: emptyList()
        return candidates.filter { it != src && it in targets }
    }

    fun hasDictionary(lang: Language): Boolean {
        return platform.fileExists(platform.getDatabasePath(dictionaryFileName(lang)))
    }

    fun hasTranslation(src: Language, tgt: Language): Boolean {
        return platform.fileExists(platform.getDatabasePath(translationFileName(src, tgt)))
    }

    /**
     * Returns a short string describing the downloaded dictionary file for [lang]: path, whether
     * it exists, and its size in bytes. Used in error-log diagnostics so Crashlytics reports
     * include enough context to confirm whether a SQLite failure correlates with file deletion or
     * truncation under an in-flight reader.
     */
    fun dictionaryDiagnostics(lang: Language): String {
        val path = platform.getDatabasePath(dictionaryFileName(lang))
        val exists = platform.fileExists(path)
        val size = if (exists) platform.getFileSize(path) else null
        return "dictPath=$path exists=$exists size=$size"
    }

    /**
     * Read-mode lease on the downloaded RO dictionary for [lang]. Passes the database to [block],
     * or `null` if the file does not exist on disk. Any concurrent close (e.g.,
     * [deleteDictionary], [deleteAllDownloadedData]) waits for [block] to return before deleting
     * the file — SQLite's Pool inside the driver cannot open new connections to a file that's
     * about to be unlinked.
     *
     * Existence is checked inside the cache's read lock so it cannot race with a concurrent
     * close. Use this variant whenever a missing DB is a normal "no rows" outcome.
     */
    suspend fun <T> withDictionaryReadOnlyIfExists(
        lang: Language,
        block: suspend (DictionaryDatabase?) -> T,
    ): T = databaseCache.withDictionaryIfExists(lang, block)

    /**
     * Read-mode lease on the downloaded RO translation. See [withDictionaryReadOnlyIfExists].
     */
    suspend fun <T> withTranslationReadOnlyIfExists(
        src: Language,
        tgt: Language,
        block: suspend (TranslationDatabase?) -> T,
    ): T = databaseCache.withTranslationIfExists(src, tgt, block)

    /**
     * Like [withDictionaryReadOnlyIfExists] but throws [IllegalArgumentException] when the file
     * is absent. Convenience for callers that have already enumerated installed dictionaries and
     * want to fail fast on a stale reference.
     */
    suspend fun <T> withDictionaryReadOnly(
        lang: Language,
        block: suspend (DictionaryDatabase) -> T,
    ): T = withDictionaryReadOnlyIfExists(lang) { db ->
        if (db == null) throw IllegalArgumentException("Dictionary for language $lang does not exist")
        block(db)
    }

    /** See [withDictionaryReadOnly]. */
    suspend fun <T> withTranslationReadOnly(
        src: Language,
        tgt: Language,
        block: suspend (TranslationDatabase) -> T,
    ): T = withTranslationReadOnlyIfExists(src, tgt) { db ->
        if (db == null) throw IllegalArgumentException("Translation from $src to $tgt does not exist")
        block(db)
    }

    /**
     * Returns the subset of [normalizedLemmas] that should be re-fetched because the local dictionary
     * either does not contain them at all, or only has placeholder `online_only` rows.
     */
    suspend fun downloadedLemmasNeedingRecovery(
        language: Language,
        normalizedLemmas: Set<String>,
    ): Set<String> = withContext(Dispatchers.IO) {
        if (normalizedLemmas.isEmpty()) return@withContext emptySet()
        withDictionaryReadOnlyIfExists(language) { db ->
            if (db == null) return@withDictionaryReadOnlyIfExists emptySet()
            val queries = db.dictionaryQueries
            val rowsByLemma = normalizedLemmas.chunked(999)
                .flatMap { chunk -> queries.selectLemmasByNormalizedWords(language.code, chunk).executeAsList() }
                .groupBy { it.lemma_normalized.lowercase() }

            val missingLemmas = normalizedLemmas - rowsByLemma.keys
            val onlineOnlyLemmas = rowsByLemma
                .filterValues { rows -> rows.isNotEmpty() && rows.all { it.online_only } }
                .keys

            missingLemmas + onlineOnlyLemmas
        }
    }

    /**
     * Returns the subset of normalized lemmas (keys of [senseIdsByNormalizedLemma]) whose
     * sense_ids are missing translations in any of [translationTargets].
     *
     * A lemma is reported as needing recovery if any target language either:
     *  - has no translation database file on disk (so no senses can possibly have translations), or
     *  - has a translation database that does not contain a row for at least one sense_id.
     *
     * Sense_ids that fail to parse as a [Uuid] are treated as missing — they cannot match any stored
     * row, so the safest action is to refetch.
     */
    suspend fun downloadedSensesNeedingTranslationRecovery(
        language: Language,
        senseIdsByNormalizedLemma: Map<String, Set<String>>,
        translationTargets: List<Language>,
    ): Set<String> = withContext(Dispatchers.IO) {
        if (senseIdsByNormalizedLemma.isEmpty() || translationTargets.isEmpty()) return@withContext emptySet()
        if (!hasDictionary(language)) return@withContext emptySet()

        val targets = translationTargets.filter { it != language }.distinctBy { it.code }
        if (targets.isEmpty()) return@withContext emptySet()

        val needsRecovery = mutableSetOf<String>()
        for (target in targets) {
            val remaining = senseIdsByNormalizedLemma.keys - needsRecovery
            if (remaining.isEmpty()) break

            // Existence check happens inside the IfExists lease — no TOCTOU between
            // hasTranslation() and opening the DB.
            withTranslationReadOnlyIfExists(language, target) { db ->
                if (db == null) {
                    // No translation file → every favorite lemma in this language needs the
                    // target fetched.
                    needsRecovery += remaining
                    return@withTranslationReadOnlyIfExists
                }
                val queries = db.translationQueries
                val parsedSenseIds: List<Uuid> = remaining.flatMap { lemma ->
                    (senseIdsByNormalizedLemma[lemma] ?: emptySet()).mapNotNull { raw ->
                        runCatching { Uuid.parse(raw) }.getOrNull()
                    }
                }.distinct()

                val present: Set<Uuid> = parsedSenseIds.chunked(999)
                    .flatMap { chunk ->
                        queries.selectSenseTranslationsBySenseIds(chunk, language.code, target.code).executeAsList()
                    }
                    .map { it.sense_id }
                    .toSet()

                for (lemma in remaining) {
                    val needed = senseIdsByNormalizedLemma[lemma] ?: continue
                    val anyMissing = needed.any { raw ->
                        val parsed = runCatching { Uuid.parse(raw) }.getOrNull() ?: return@any true
                        parsed !in present
                    }
                    if (anyMissing) needsRecovery += lemma
                }
            }
        }
        needsRecovery
    }

    suspend fun hasRequiredVersion(): Boolean = withContext(Dispatchers.Default) {
        val saved = settingsRepository.getById(Setting.Name.DATA_VERSION)?.value?.jsonPrimitive?.content
        saved == VERSION
    }

    suspend fun setDownloadedVersion() {
        settingsRepository.insert(
            Setting(
                id = Setting.Name.DATA_VERSION,
                value = Json.parseToJsonElement("\"$VERSION\"")
            )
        )
    }

    /**
     * Fetches available languages with their dictionaries and translations grouped by source language.
     * Uses in-memory cache if available.
     */
    suspend fun fetchAvailableLanguages(): List<AvailableLanguageInfo> = withContext(Dispatchers.IO) {
        // Return cached value if available
        cachedAvailableLanguages?.let { return@withContext it }

        val result = fetchAvailableLanguagesFromRemote()
        cachedAvailableLanguages = result
        result
    }

    private suspend fun fetchAvailableLanguagesFromRemote(): List<AvailableLanguageInfo> = withContext(Dispatchers.IO) {
        val remoteFiles = remoteDataProvider.listFiles(platform)

        // Parse all files
        val dictionaries = mutableMapOf<Language, Long>()
        val translations = mutableMapOf<Language, MutableList<AvailableTranslationInfo>>()

        remoteFiles.forEach { rf ->
            val fileName = rf.name
            val size = rf.sizeBytes

            when {
                fileName.startsWith(DICTIONARY_PREFIX) && fileName.endsWith(DB_EXTENSION) -> {
                    val langCode = fileName.removePrefix(DICTIONARY_PREFIX).removeSuffix(DB_EXTENSION)
                    val language = Language.fromCodeOrNull(langCode)
                    if (language != null) {
                        dictionaries[language] = size
                    }
                }

                fileName.startsWith(TRANSLATION_PREFIX) && fileName.endsWith(DB_EXTENSION) -> {
                    val parts = fileName.removePrefix(TRANSLATION_PREFIX).removeSuffix(DB_EXTENSION).split("_")
                    if (parts.size == 2) {
                        val srcLang = Language.fromCodeOrNull(parts[0])
                        val tgtLang = Language.fromCodeOrNull(parts[1])
                        if (srcLang != null && tgtLang != null) {
                            translations.getOrPut(srcLang) { mutableListOf() }
                                .add(AvailableTranslationInfo(tgtLang, size))
                        }
                    }
                }
            }
        }

        // Combine into AvailableLanguageInfo
        val allLanguages = (dictionaries.keys + translations.keys).toSet()
        allLanguages.map { lang ->
            AvailableLanguageInfo(
                language = lang,
                dictionarySizeBytes = dictionaries[lang],
                availableTranslations = translations[lang] ?: emptyList()
            )
        }.sortedBy { it.language.selfName }
    }

    /**
     * Lists all downloaded dictionary and translation databases with their sizes.
     */
    fun listDownloadedDatabases(): List<DatabaseFileInfo> {
        val databasesDir = platform.getDatabasePath("")
        val files = platform.listFiles(databasesDir)
        return files.mapNotNull { file ->
            val fileName = file.name
            val size = platform.getFileSize(file) ?: return@mapNotNull null
            when {
                fileName.startsWith(DICTIONARY_PREFIX) && fileName.endsWith(DB_EXTENSION) -> {
                    val langCode = fileName.removePrefix(DICTIONARY_PREFIX).removeSuffix(DB_EXTENSION)
                    val language = Language.fromCodeOrNull(langCode) ?: return@mapNotNull null
                    DatabaseFileInfo.Dictionary(language, size)
                }

                fileName.startsWith(TRANSLATION_PREFIX) && fileName.endsWith(DB_EXTENSION) -> {
                    val parts = fileName.removePrefix(TRANSLATION_PREFIX).removeSuffix(DB_EXTENSION).split("_")
                    if (parts.size != 2) return@mapNotNull null
                    val srcLang = Language.fromCodeOrNull(parts[0]) ?: return@mapNotNull null
                    val tgtLang = Language.fromCodeOrNull(parts[1]) ?: return@mapNotNull null
                    DatabaseFileInfo.Translation(srcLang, tgtLang, size)
                }

                else -> null
            }
        }
    }

    private suspend fun ensureFile(
        name: String,
        onProgress: (DownloadProgress) -> Unit,
        cancelToken: CancelToken?,
    ): Path = withContext(Dispatchers.Default) {
        val path = platform.getDatabasePath(name)
        val file = Path(path)
        if (platform.fileExists(path)) return@withContext file

        PerformanceMonitoring.startTrace("data_db_download").useWithResult {
            putAttributes(downloadTraceAttributes(name))
            var bytesDownloaded = 0L
            try {
                val url = remoteDataProvider.downloadUrlFor(name)
                platform.ensureDatabasesDir()
                downloadToFile(
                    url = url,
                    headers = remoteDataProvider.headersForHttp(),
                    destPath = path,
                    onProgress = { progress ->
                        bytesDownloaded = max(bytesDownloaded, progress.bytesDownloaded)
                        onProgress(progress)
                    },
                    cancelToken = cancelToken ?: CancelToken(),
                )
                // Belt-and-suspenders against partial downloads that slipped past byte-count checks
                // (e.g. server response without Content-Length): verify the file actually carries our
                // schema before we stamp DATA_VERSION and report success.
                val isDictionary = name.startsWith(DICTIONARY_PREFIX)
                if (!probeDownloadedDb(file, isDictionary = isDictionary)) {
                    platform.deleteFile(file)
                    markResult("invalid_schema")
                    throw IllegalStateException("Downloaded $name is missing expected schema; deleted")
                }
                platform.markNoBackup(path)
                // After first successful download, save version
                setDownloadedVersion()
                file
            } catch (e: DownloadCancelledException) {
                markResult("cancelled")
                throw e
            } finally {
                putMetric("bytes", bytesDownloaded)
            }
        }
    }

    private fun downloadTraceAttributes(name: String): Map<String, Any> {
        if (name.startsWith(DICTIONARY_PREFIX)) {
            return mapOf(
                "kind" to "dictionary",
                "source_lang" to name.removePrefix(DICTIONARY_PREFIX).removeSuffix(DB_EXTENSION),
            )
        }
        val parts = name.removePrefix(TRANSLATION_PREFIX).removeSuffix(DB_EXTENSION).split("_", limit = 2)
        return mapOf(
            "kind" to "translation",
            "source_lang" to parts.getOrElse(0) { "unknown" },
            "target_lang" to parts.getOrElse(1) { "unknown" },
        )
    }

    class DownloadCancelledException : RuntimeException()

    private suspend fun downloadToFile(
        url: String,
        headers: Map<String, String>,
        destPath: Path,
        onProgress: (DownloadProgress) -> Unit,
        cancelToken: CancelToken,
    ) = platform.downloadFileToPath(url, headers, destPath, onProgress, cancelToken)
}

// Download cancellation token
class CancelToken {
    var isCancelled: Boolean = false
    fun cancel() {
        isCancelled = true
    }
}

// Progress model
open class DownloadProgress(val bytesDownloaded: Long, val totalBytes: Long?) {
    open val percent: Int = if (totalBytes != null && totalBytes > 0) {
        ((bytesDownloaded * 100L) / max(totalBytes, 1)).toInt()
    } else -1
    open val currentFile: String? = null
}

// Database file info
sealed class DatabaseFileInfo(
    open val sizeBytes: Long
) {
    data class Dictionary(
        val language: Language,
        override val sizeBytes: Long
    ) : DatabaseFileInfo(sizeBytes)

    data class Translation(
        val sourceLanguage: Language,
        val targetLanguage: Language,
        override val sizeBytes: Long
    ) : DatabaseFileInfo(sizeBytes)
}

// Remote data source abstraction and implementations
interface RemoteDataProvider {
    suspend fun listFiles(platform: PlatformDbSupport): List<RemoteFile>
    fun downloadUrlFor(fileName: String): String
    fun headersForHttp(): Map<String, String>
}

data class RemoteFile(
    val name: String,
    val sizeBytes: Long
)

data class AvailableLanguageInfo(
    val language: Language,
    val dictionarySizeBytes: Long?,
    val availableTranslations: List<AvailableTranslationInfo>
)

data class AvailableTranslationInfo(
    val targetLanguage: Language,
    val sizeBytes: Long
)

/**
 * Platform-specific support for file locations, and read-only driver creation.
 */
expect class PlatformDbSupport(androidContext: Any? = null) {
    fun getDatabasePath(name: String): Path
    fun ensureDatabasesDir()
    fun fileExists(path: Path): Boolean
    fun openOutput(destPath: Path): PlatformFileOutput
    fun copyFile(from: Path, to: Path): Boolean
    fun deleteFile(path: Path)
    fun moveFile(from: Path, to: Path): Boolean
    fun markNoBackup(path: Path)
    fun createAppDataDriver(path: Path): SqlDriver
    fun createDictionaryDataDriver(path: Path, readOnly: Boolean): SqlDriver
    fun createTranslationDataDriver(path: Path, readOnly: Boolean): SqlDriver
    fun createHttpClient(): HttpClient
    fun listFiles(path: Path): List<Path>

    // Returns available bytes for the filesystem containing the provided path. Null if unknown.
    fun getAvailableBytesForPath(path: Path): Long?

    // Returns file size in bytes. Null if file doesn't exist or size cannot be determined.
    fun getFileSize(path: Path): Long?

    suspend fun downloadFileToPath(
        url: String,
        headers: Map<String, String>,
        destPath: Path,
        onProgress: (DownloadProgress) -> Unit,
        cancelToken: CancelToken,
    )

    // Acquires a handle that keeps the process alive on background-aware platforms (Android,
    // iOS); desktop returns a no-op. Callers MUST [ProcessKeepAlive.release] in a finally block.
    // Acquisitions are reference-counted process-wide so nested holders are safe.
    fun acquireProcessKeepAlive(): ProcessKeepAlive
}

/**
 * Handle for an active keep-alive acquired from [PlatformDbSupport.acquireProcessKeepAlive].
 * [release] is non-suspending and idempotent so it can be called safely from cancellation paths
 * (e.g. ViewModel.onCleared) without a coroutine context.
 */
interface ProcessKeepAlive {
    fun release()
}

/**
 * Convenience wrapper that acquires a keep-alive for the duration of [block]. The release runs in
 * a finally block so it survives cancellation of [block].
 */
suspend fun PlatformDbSupport.runWithProcessKeepAlive(block: suspend () -> Unit) {
    val handle = acquireProcessKeepAlive()
    try {
        block()
    } finally {
        handle.release()
    }
}

interface PlatformFileOutput {
    fun write(buffer: ByteArray, offset: Int, length: Int)
    fun flush()
    fun close()
}

// Query-only enforcement is identical across platforms
fun enforceQueryOnly(driver: SqlDriver) {
    try {
        driver.execute(null, "PRAGMA query_only = ON", 0)
    } catch (_: Throwable) {
        // best effort
    }
}

/**
 * Holder for app database that allows proper resource cleanup.
 * Call [close] when done to release the database connection.
 */
class AppDatabaseHolder(
    private val driver: SqlDriver,
    val database: AppDatabase
) : AutoCloseable {
    override fun close() {
        driver.close()
    }
}
