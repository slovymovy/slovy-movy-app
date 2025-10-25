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
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    private val settingsRepository: SettingsRepository? = null,
) {
    companion object {
        const val VERSION = "v4"

        // TODO: we use HTTP for now to workaround some issues with IOS emulator
        // https://github.com/slovymovy/slovy-movy-app/issues/34
        const val BASE_URL = "http://storage.googleapis.com/slovymovy/$VERSION/"
        fun dictionaryFileName(lang: Language): String = "dictionary_${lang.code.lowercase()}.db"
        fun translationFileName(src: Language, tgt: Language): String =
            "translation_${src.code.lowercase()}_${tgt.code.lowercase()}.db"
    }

    suspend fun ensureDictionary(
        lang: Language,
        onProgress: (DownloadProgress) -> Unit = {},
        cancelToken: CancelToken? = null
    ): Path {
        val name = dictionaryFileName(lang)
        return ensureFile(name, onProgress, cancelToken)
    }

    fun deleteDictionary(lang: Language) {
        val name = dictionaryFileName(lang)
        platform.deleteFile(platform.getDatabasePath(name))
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

    fun deleteTranslation(src: Language, tgt: Language) {
        val name = translationFileName(src, tgt)
        platform.deleteFile(platform.getDatabasePath(name))
    }

    /**
     * Deletes all downloaded dictionary and translation databases.
     * Also clears the stored version setting.
     * Used when data version changes.
     */
    fun deleteAllDownloadedData() {
        val databasesDir = platform.getDatabasePath("")
        val files = platform.listFiles(databasesDir)
        files.forEach { file ->
            val fileName = file.name
            if (fileName.startsWith("dictionary_") || fileName.startsWith("translation_")) {
                platform.deleteFile(file)
            }
        }
        clearVersion()
    }

    /**
     * Clears the stored data version from settings.
     */
    fun clearVersion() {
        settingsRepository?.deleteById(Setting.Name.DATA_VERSION)
    }

    fun openAppDatabase(): AppDatabase {
        val file = platform.getDatabasePath("app.db")
        val driver = platform.createAppDataDriver(file)
        return DatabaseProvider.createAppDatabase(driver)
    }

    fun hasDictionary(lang: Language): Boolean {
        return platform.fileExists(platform.getDatabasePath(dictionaryFileName(lang)))
    }

    fun hasTranslation(src: Language, tgt: Language): Boolean {
        return platform.fileExists(platform.getDatabasePath(translationFileName(src, tgt)))
    }

    fun openDictionaryReadOnly(lang: Language): DictionaryDatabase {
        val file = platform.getDatabasePath(dictionaryFileName(lang))
        val driver = platform.createDictionaryDataDriver(file, true)
        return DatabaseProvider.createDictionaryDatabase(driver)
    }

    fun openTranslationReadOnly(src: Language, tgt: Language): TranslationDatabase {
        val file = platform.getDatabasePath(translationFileName(src, tgt))
        val driver = platform.createTranslationDataDriver(file, true)
        return DatabaseProvider.createTranslationDatabase(driver)
    }

    suspend fun hasRequiredVersion(): Boolean = withContext(Dispatchers.Default) {
        val saved = settingsRepository?.getById(Setting.Name.DATA_VERSION)?.value?.jsonPrimitive?.content
        saved == VERSION
    }

    fun setDownloadedVersion() {
        settingsRepository?.insert(
            Setting(
                id = Setting.Name.DATA_VERSION,
                value = Json.parseToJsonElement("\"$VERSION\"")
            )
        )
    }

    private suspend fun ensureFile(
        name: String,
        onProgress: (DownloadProgress) -> Unit,
        cancelToken: CancelToken?,
    ): Path = withContext(Dispatchers.Default) {
        val path = platform.getDatabasePath(name)
        val file = Path(path)
        if (!platform.fileExists(path)) {
            val url = BASE_URL + name
            platform.ensureDatabasesDir()
            downloadToFile(url, path, onProgress, cancelToken ?: CancelToken())
            platform.markNoBackup(path)
            // After first successful download, save version
            setDownloadedVersion()
        }
        file
    }

    private suspend fun downloadToFile(
        url: String,
        destPath: Path,
        onProgress: (DownloadProgress) -> Unit,
        cancelToken: CancelToken,
    ) = withContext(Dispatchers.Default) {
        val client = platform.createHttpClient()
        val tempPath = Path("$destPath.part")
        try {
            // Ensure no stale temp file exists
            if (platform.fileExists(tempPath)) {
                platform.deleteFile(tempPath)
            }
            client.prepareGet(url).execute { response ->
                // Fail early on non-success HTTP responses
                if (!response.status.isSuccess()) {
                    val snippet = try {
                        response.bodyAsText().take(512)
                    } catch (_: Throwable) {
                        null
                    }
                    val baseMsg =
                        "HTTP ${response.status.value} ${response.status.description} while downloading $url"
                    throw IllegalStateException(if (snippet.isNullOrBlank()) baseMsg else "$baseMsg: $snippet")
                }

                val total = response.headers["Content-Length"]?.toLongOrNull()
                // Check available disk space if total size is known
                if (total != null) {
                    val available = platform.getAvailableBytesForPath(destPath)
                    val headroom = 1024L * 1024L // 1 MiB safety margin
                    if (available != null && available < total + headroom) {
                        throw IllegalStateException("Not enough free space to download file: required=${total + headroom}, available=$available")
                    }
                }
                val out = platform.openOutput(tempPath)
                try {
                    val channel = response.bodyAsChannel()
                    val buffer = ByteArray(1024 * 1024) // Smaller buffer for better memory efficiency
                    var downloaded = 0L

                    while (!channel.isClosedForRead) {
                        if (cancelToken.isCancelled) {
                            out.flush()
                            out.close()
                            platform.deleteFile(tempPath)
                            throw CancellationException("Download cancelled")
                        }

                        val read = channel.readAvailable(buffer, 0, buffer.size)
                        if (read <= 0) break

                        out.write(buffer, 0, read)
                        out.flush() // Flush more frequently to avoid buffering
                        downloaded += read
                        onProgress(DownloadProgress(downloaded, total))
                    }
                } finally {
                    out.close()
                }
            }
            // After successful download, move temp to destination
            if (!platform.moveFile(tempPath, destPath)) {
                // Best effort cleanup
                platform.deleteFile(tempPath)
                throw IllegalStateException("Failed to move downloaded file to destination")
            }
        } catch (e: CancellationException) {
            // Ensure temp file is removed on cancel
            platform.deleteFile(tempPath)
            throw e
        } catch (t: Throwable) {
            // Ensure temp file is removed on error
            platform.deleteFile(tempPath)
            throw t
        } finally {
            client.close()
        }
    }
}

// Download cancellation token
class CancelToken {
    var isCancelled: Boolean = false
    fun cancel() {
        isCancelled = true
    }
}

// Progress model
class DownloadProgress(bytesDownloaded: Long, val totalBytes: Long?) {
    val percent: Int = if (totalBytes != null && totalBytes > 0) {
        ((bytesDownloaded * 100L) / max(totalBytes, 1)).toInt()
    } else -1
}

/**
 * Platform-specific support for file locations, and read-only driver creation.
 */
expect class PlatformDbSupport(androidContext: Any? = null) {
    fun getDatabasePath(name: String): Path
    fun ensureDatabasesDir()
    fun fileExists(path: Path): Boolean
    fun openOutput(destPath: Path): PlatformFileOutput
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
