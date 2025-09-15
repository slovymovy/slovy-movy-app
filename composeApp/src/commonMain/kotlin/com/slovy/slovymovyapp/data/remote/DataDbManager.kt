package com.slovy.slovymovyapp.data.remote

import app.cash.sqldelight.db.SqlDriver
import com.slovy.slovymovyapp.data.db.DatabaseProvider
import com.slovy.slovymovyapp.data.settings.Setting
import com.slovy.slovymovyapp.data.settings.SettingsRepository
import com.slovy.slovymovyapp.dictionary.DictionaryDatabase
import com.slovy.slovymovyapp.translation.TranslationDatabase
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.utils.io.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
        const val VERSION = "v0"
        // TODO: we use HTTP for now to workaround some issues with IOS emulator
        // https://github.com/slovymovy/slovy-movy-app/issues/34
        const val BASE_URL = "http://storage.googleapis.com/slovymovy/v0/"
        fun dictionaryFileName(lang: String): String = "dictionary_${lang.lowercase()}.db"
        fun translationFileName(src: String, tgt: String): String =
            "translation_${src.lowercase()}_${tgt.lowercase()}.db"
    }

    suspend fun ensureDictionary(
        lang: String,
        onProgress: (DownloadProgress) -> Unit = {},
        cancelToken: CancelToken? = null
    ): DbFile {
        val name = dictionaryFileName(lang)
        return ensureFile(name, onProgress, cancelToken)
    }

    suspend fun ensureTranslation(
        src: String,
        tgt: String,
        onProgress: (DownloadProgress) -> Unit = {},
        cancelToken: CancelToken? = null
    ): DbFile {
        val name = translationFileName(src, tgt)
        return ensureFile(name, onProgress, cancelToken)
    }

    fun openDictionaryReadOnly(lang: String): DictionaryDatabase {
        val file = DbFile(platform.getDatabasePath(dictionaryFileName(lang)))
        val driver = platform.createDataReadonlyDriver(file)
        return DatabaseProvider.createDictionaryDatabase(driver)
    }

    fun openTranslationReadOnly(src: String, tgt: String): TranslationDatabase {
        val file = DbFile(platform.getDatabasePath(translationFileName(src, tgt)))
        val driver = platform.createDataReadonlyDriver(file)
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
    ): DbFile = withContext(Dispatchers.Default) {
        val path = platform.getDatabasePath(name)
        val file = DbFile(path)
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
        destPath: String,
        onProgress: (DownloadProgress) -> Unit,
        cancelToken: CancelToken,
    ) = withContext(Dispatchers.Default) {
        val client = platform.createHttpClient()
        try {
            client.prepareGet(url).execute { response ->
                val total = response.headers["Content-Length"]?.toLongOrNull()
                val out = platform.openOutput(destPath)
                try {
                    val channel = response.bodyAsChannel()
                    val buffer = ByteArray(1024 * 1024) // Smaller buffer for better memory efficiency
                    var downloaded = 0L

                    while (!channel.isClosedForRead) {
                        if (cancelToken.isCancelled) {
                            out.flush()
                            out.close()
                            platform.deleteFile(destPath)
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
        } finally {
            client.close()
        }
    }
}

// Simple path holder
data class DbFile(val path: String)

// Download cancellation token
class CancelToken {
    var isCancelled: Boolean = false
    fun cancel() {
        isCancelled = true
    }
}

// Progress model
class DownloadProgress(val bytesDownloaded: Long, val totalBytes: Long?) {
    val percent: Int = if (totalBytes != null && totalBytes > 0) {
        ((bytesDownloaded * 100L) / max(totalBytes, 1)).toInt()
    } else -1
}

/**
 * Platform-specific support for file locations, and read-only driver creation.
 */
expect class PlatformDbSupport(androidContext: Any? = null) {
    fun getDatabasePath(name: String): String
    fun ensureDatabasesDir()
    fun fileExists(path: String): Boolean
    fun openOutput(destPath: String): PlatformFileOutput
    fun deleteFile(path: String)
    fun markNoBackup(path: String)
    fun createDataReadonlyDriver(dbFile: DbFile): SqlDriver
    fun createHttpClient(): HttpClient
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
