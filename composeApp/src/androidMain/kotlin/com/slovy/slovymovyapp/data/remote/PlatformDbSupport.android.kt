package com.slovy.slovymovyapp.data.remote

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.slovy.slovymovyapp.db.AppDatabase
import com.slovy.slovymovyapp.dictionary.DictionaryDatabase
import com.slovy.slovymovyapp.translation.TranslationDatabase
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.CancellationException
import kotlinx.io.files.Path
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicInteger

actual class PlatformDbSupport actual constructor(androidContext: Any?) {
    private val ctx: Context = (androidContext as? Context)
        ?: error("Android Context is required for PlatformDbSupport on Android")

    actual fun getDatabasePath(name: String): Path {
        if (name.isEmpty()) {
            // Return the databases directory itself when name is empty
            return Path(
                ctx.getDatabasePath("placeholder.db").parentFile?.absolutePath
                    ?: error("Cannot get databases directory")
            )
        }
        return Path(ctx.getDatabasePath(name).absolutePath)
    }

    actual fun ensureDatabasesDir() {
        ctx.getDatabasePath("placeholder.db").parentFile?.mkdirs()
    }

    actual fun fileExists(path: Path): Boolean = File(path.toString()).exists()

    actual fun openInput(sourcePath: Path): PlatformFileInput {
        val input = File(sourcePath.toString()).inputStream()
        return object : PlatformFileInput {
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
                input.read(buffer, offset, length)

            override fun close() {
                input.close()
            }
        }
    }

    actual fun openOutput(destPath: Path): PlatformFileOutput {
        // Ensure parent dir exists
        File(destPath.toString()).parentFile?.mkdirs()
        val fos = FileOutputStream(File(destPath.toString()))
        return object : PlatformFileOutput {
            override fun write(buffer: ByteArray, offset: Int, length: Int) {
                fos.write(buffer, offset, length)
            }

            override fun flush() {
                fos.flush()
            }

            override fun close() {
                fos.close()
            }
        }
    }

    actual fun copyFile(from: Path, to: Path): Boolean {
        return try {
            val src = File(from.toString())
            val dst = File(to.toString())
            dst.parentFile?.mkdirs()
            src.inputStream().use { input ->
                dst.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            true
        } catch (_: Throwable) {
            false
        }
    }

    actual fun deleteFile(path: Path) {
        File(path.toString()).delete()
    }

    actual fun moveFile(from: Path, to: Path): Boolean {
        val src = File(from.toString())
        val dst = File(to.toString())
        dst.parentFile?.mkdirs()
        if (dst.exists()) dst.delete()
        return src.renameTo(dst)
    }

    actual fun markNoBackup(path: Path) {
        // Auto Backup rules (fullBackupContent) in the app manifest include only app.db from the
        // databases domain. All downloaded DB files are excluded from backup automatically.
        // No runtime action needed here.
    }

    actual fun createAppDataDriver(path: Path): SqlDriver {
        return androidSqliteDriver(path, AppDatabase.Schema, false)
    }

    actual fun createDictionaryDataDriver(path: Path, readOnly: Boolean): SqlDriver {
        return androidSqliteDriver(path, DictionaryDatabase.Schema, readOnly)
    }

    private fun androidSqliteDriver(
        path: Path,
        schema: SqlSchema<QueryResult.Value<Unit>>,
        readOnly: Boolean
    ): AndroidSqliteDriver {
        synchronized(this) {
            val file = File(path.toString())
            val name = file.name

            val result = AndroidSqliteDriver(
                schema = schema,
                context = ctx,
                name = name,
                callback = if (!readOnly) AndroidSqliteDriver.Callback(schema) else object :
                    AndroidSqliteDriver.Callback(schema) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        // Do nothing
                    }

                    override fun onUpgrade(
                        db: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int
                    ) {
                        // Do nothing
                    }
                }
            )
            if (readOnly) {
                enforceQueryOnly(result)
            }
            return result
        }
    }

    actual fun createTranslationDataDriver(path: Path, readOnly: Boolean): SqlDriver {
        return androidSqliteDriver(path, TranslationDatabase.Schema, readOnly)
    }

    actual fun createHttpClient(): HttpClient {
        return HttpClient(OkHttp) {
            engine {
                config {
                    retryOnConnectionFailure(true)
                }
            }
        }
    }

    actual fun getAvailableBytesForPath(path: Path): Long? {
        return try {
            val dir = File(path.toString()).parentFile ?: File(path.toString())
            val stat = android.os.StatFs(dir.absolutePath)
            stat.availableBytes
        } catch (_: Throwable) {
            null
        }
    }

    actual fun listFiles(path: Path): List<Path> {
        val dir = File(path.toString())
        if (!dir.isDirectory) {
            return dir.parentFile?.listFiles()?.map { Path(it.absolutePath) } ?: emptyList()
        }
        return dir.listFiles()?.map { Path(it.absolutePath) } ?: emptyList()
    }

    actual fun getFileSize(path: Path): Long? {
        return try {
            val file = File(path.toString())
            if (file.exists() && file.isFile) file.length() else null
        } catch (_: Throwable) {
            null
        }
    }

    actual suspend fun downloadFileToPath(
        url: String,
        headers: Map<String, String>,
        destPath: Path,
        onProgress: (DownloadProgress) -> Unit,
        cancelToken: CancelToken,
    ) {
        val handle = acquireProcessKeepAlive()
        try {
            downloadViaKtor(url, headers, destPath, onProgress, cancelToken)
        } finally {
            handle.release()
        }
    }

    actual fun acquireProcessKeepAlive(): ProcessKeepAlive {
        val intent = Intent(ctx, DownloadForegroundService::class.java)
        val startedService = if (activeBackgroundJobs.getAndIncrement() == 0) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ctx.startForegroundService(intent)
                } else {
                    ctx.startService(intent)
                }
                true
            } catch (_: Throwable) {
                // e.g. ForegroundServiceStartNotAllowedException on Android 12+ when launched
                // from background. Fall through to a foregroundless execution instead of leaking
                // the ref-count. Outer holders acquired while the app was foregrounded keep the
                // service alive across this call so this fallback is rare in practice.
                activeBackgroundJobs.decrementAndGet()
                false
            }
        } else {
            true
        }
        return AndroidProcessKeepAlive(ctx, intent, startedService)
    }

    private class AndroidProcessKeepAlive(
        private val ctx: Context,
        private val intent: Intent,
        private val startedService: Boolean,
    ) : ProcessKeepAlive {
        @Volatile
        private var released = false
        override fun release() {
            if (released) return
            released = true
            if (startedService && activeBackgroundJobs.decrementAndGet() == 0) {
                ctx.stopService(intent)
            }
        }
    }

    private suspend fun downloadViaKtor(
        url: String,
        headers: Map<String, String>,
        destPath: Path,
        onProgress: (DownloadProgress) -> Unit,
        cancelToken: CancelToken,
    ) {
        val client = createHttpClient()
        val tempPath = Path("$destPath.part")
        try {
            if (fileExists(tempPath)) {
                deleteFile(tempPath)
            }
            client.prepareGet(url) {
                headers.forEach { (key, value) -> header(key, value) }
            }.execute { response ->
                if (!response.status.isSuccess()) {
                    val snippet = try {
                        response.bodyAsText().take(512)
                    } catch (_: Throwable) {
                        null
                    }
                    val baseMsg = "HTTP ${response.status.value} ${response.status.description} while downloading $url"
                    throw IllegalStateException(if (snippet.isNullOrBlank()) baseMsg else "$baseMsg: $snippet")
                }

                val total = response.headers["Content-Length"]?.toLongOrNull()
                if (total != null) {
                    val available = getAvailableBytesForPath(destPath)
                    val headroom = 1024L * 1024L
                    if (available != null && available < total + headroom) {
                        throw IllegalStateException("Not enough free space to download file: required=${total + headroom}, available=$available")
                    }
                }
                val out = openOutput(tempPath)
                var downloaded = 0L
                try {
                    val channel = response.bodyAsChannel()
                    val buffer = ByteArray(1024 * 1024)

                    while (!channel.isClosedForRead) {
                        if (cancelToken.isCancelled) {
                            out.flush()
                            out.close()
                            deleteFile(tempPath)
                            throw DataDbManager.DownloadCancelledException()
                        }

                        val read = channel.readAvailable(buffer, 0, buffer.size)
                        if (read <= 0) break

                        out.write(buffer, 0, read)
                        out.flush()
                        downloaded += read
                        onProgress(DownloadProgress(downloaded, total))
                    }
                } finally {
                    out.close()
                }
                if (total != null && downloaded != total) {
                    throw IllegalStateException(
                        "Truncated download from $url: expected $total bytes, got $downloaded"
                    )
                }
            }
            if (!moveFile(tempPath, destPath)) {
                deleteFile(tempPath)
                throw IllegalStateException("Failed to move downloaded file to destination")
            }
        } catch (e: CancellationException) {
            deleteFile(tempPath)
            throw e
        } catch (t: Throwable) {
            deleteFile(tempPath)
            throw t
        } finally {
            client.close()
        }
    }

    companion object {
        // Process-global so concurrent foreground-service users (downloads, post-download
        // recovery, etc.) from multiple PlatformDbSupport instances cannot stop the service
        // while another job is still running.
        private val activeBackgroundJobs = AtomicInteger(0)
    }
}
