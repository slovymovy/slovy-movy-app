package com.slovy.slovymovyapp.data.remote

import app.cash.sqldelight.db.SqlDriver
import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.db.DatabaseProvider
import com.slovy.slovymovyapp.data.settings.Setting
import com.slovy.slovymovyapp.data.settings.SettingsRepository
import com.slovy.slovymovyapp.db.AppDatabase
import com.slovy.slovymovyapp.dictionary.DictionaryDatabase
import com.slovy.slovymovyapp.translation.TranslationDatabase
import io.ktor.client.*
import kotlinx.coroutines.*
import kotlinx.io.files.Path
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.max

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
        const val VERSION = "v13"

        private const val DICTIONARY_PREFIX = "dictionary_"
        private const val TRANSLATION_PREFIX = "translation_"
        private const val DB_EXTENSION = ".db"

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
        cancelToken: CancelToken? = null,
        skipVersionUpdate: Boolean = false
    ): Path {
        val name = dictionaryFileName(lang)
        return ensureFile(name, onProgress, cancelToken, skipVersionUpdate)
    }

    suspend fun deleteDictionary(lang: Language) {
        // Close cached connections before deleting files
        databaseCache.closeAllForLanguage(lang)

        // Delete the dictionary
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

    suspend fun ensureTranslation(
        src: Language,
        tgt: Language,
        onProgress: (DownloadProgress) -> Unit = {},
        cancelToken: CancelToken? = null,
        skipVersionUpdate: Boolean = false
    ): Path {
        val name = translationFileName(src, tgt)
        return ensureFile(name, onProgress, cancelToken, skipVersionUpdate)
    }

    suspend fun deleteTranslation(src: Language, tgt: Language) {
        // Close cached connection before deleting file
        databaseCache.closeTranslation(src, tgt)

        val name = translationFileName(src, tgt)
        platform.deleteFile(platform.getDatabasePath(name))
    }

    /**
     * Deletes all downloaded dictionary and translation databases.
     * Also clears the stored version setting.
     * Used when data version changes.
     */
    suspend fun deleteAllDownloadedData() {
        // Close all cached connections before deleting files
        databaseCache.closeAll()

        val databasesDir = platform.getDatabasePath("")
        val files = platform.listFiles(databasesDir)
        files.forEach { file ->
            val fileName = file.name
            if (fileName.startsWith(DICTIONARY_PREFIX) || fileName.startsWith(TRANSLATION_PREFIX)) {
                platform.deleteFile(file)
            }
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

    suspend fun openDictionaryReadOnly(lang: Language): DictionaryDatabase {
        if (!hasDictionary(lang)) throw IllegalArgumentException("Dictionary for language $lang does not exist")
        return databaseCache.getDictionary(lang)
    }

    suspend fun openTranslationReadOnly(src: Language, tgt: Language): TranslationDatabase {
        if (!hasTranslation(src, tgt)) throw IllegalArgumentException("Translation from $src to $tgt does not exist")
        return databaseCache.getTranslation(src, tgt)
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
            val size = platform.getFileSize(file) ?: 0L
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
        skipVersionUpdate: Boolean = false,
    ): Path = withContext(Dispatchers.Default) {
        val path = platform.getDatabasePath(name)
        val file = Path(path)
        if (!platform.fileExists(path)) {
            val url = remoteDataProvider.downloadUrlFor(name)
            platform.ensureDatabasesDir()
            downloadToFile(url, remoteDataProvider.headersForHttp(), path, onProgress, cancelToken ?: CancelToken())
            platform.markNoBackup(path)
            if (!skipVersionUpdate) {
                setDownloadedVersion()
            }
        }
        file
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
