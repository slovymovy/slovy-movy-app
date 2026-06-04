package com.slovy.slovymovyapp.data.remote

import app.cash.sqldelight.db.AfterVersion
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.slovy.slovymovyapp.db.AppDatabase
import com.slovy.slovymovyapp.dictionary.DictionaryDatabase
import com.slovy.slovymovyapp.translation.TranslationDatabase
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.CancellationException
import kotlinx.io.files.Path
import java.io.File
import java.io.FileOutputStream

actual class PlatformDbSupport actual constructor(androidContext: Any?) {
    private val baseDir: File by lazy {
        val home = System.getProperty("user.home") ?: "."
        File(home, ".slovymovyapp/databases").apply { mkdirs() }
    }

    actual fun getDatabasePath(name: String): Path = Path(File(baseDir, name).absolutePath)

    actual fun ensureDatabasesDir() {
        baseDir.mkdirs()
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
        // No-op on desktop JVM
    }

    actual fun createAppDataDriver(path: Path): SqlDriver {
        return jdbcSqliteDriver(path, false, AppDatabase.Schema)
    }

    actual fun createDictionaryDataDriver(path: Path, readOnly: Boolean): SqlDriver {
        return jdbcSqliteDriver(path, readOnly, DictionaryDatabase.Schema)
    }

    actual fun createTranslationDataDriver(path: Path, readOnly: Boolean): SqlDriver {
        return jdbcSqliteDriver(path, readOnly, TranslationDatabase.Schema)
    }

    @Suppress("SqlNoDataSourceInspection")
    private fun jdbcSqliteDriver(
        path: Path,
        readOnly: Boolean,
        schema: SqlSchema<QueryResult.Value<Unit>>
    ): JdbcSqliteDriver {
        synchronized(this) {
            val url = jdbcConnectionString(path, readOnly)
            val driver = JdbcSqliteDriver(url)
            if (readOnly) {
                return driver
            }
            val isNew = !fileExists(path)

            if (isNew) {
                schema.create(driver)
                setVersion(driver, schema.version)
            } else {
                val currentVersion = driver.executeQuery(
                    identifier = null,
                    sql = sqliteUserVersionSql(),
                    mapper = { cursor ->
                        QueryResult.Value(cursor.getLong(0) ?: 0)
                    },
                    parameters = 0
                ).value

                schema.migrate(
                    driver,
                    currentVersion,
                    schema.version,
                    *(1..schema.version).map {
                        AfterVersion(it) { d ->
                            setVersion(d, it)
                        }
                    }.toTypedArray()
                )

                setVersion(driver, schema.version)
            }
            return driver
        }
    }

    private fun setVersion(driver: SqlDriver, version: Long) {
        driver.execute(
            identifier = null,
            sql = sqliteSetUserVersionSql(version),
            parameters = 0
        )
    }

    private fun sqliteUserVersionSql(): String =
        "PRAGMA" + " user_version"

    private fun sqliteSetUserVersionSql(version: Long): String =
        "PRAGMA" + " user_version = $version"

    private fun jdbcConnectionString(path: Path, readOnly: Boolean): String {
        val url = "jdbc:sqlite:file:${path}" + if (readOnly) "?mode=ro" else ""
        return url
    }

    actual fun createHttpClient(): HttpClient {
        return HttpClient(CIO) {
            engine {
                requestTimeout = 0
                endpoint {
                    connectTimeout = 10000
                    connectAttempts = 5
                }
            }
        }
    }

    @Suppress("UsableSpace")
    actual fun getAvailableBytesForPath(path: Path): Long? {
        return try {
            val dir = File(path.toString()).parentFile ?: File(path.toString())
            dir.usableSpace
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

    actual fun acquireProcessKeepAlive(): ProcessKeepAlive {
        // Desktop processes are not subject to OS-imposed background suspension.
        return NoopProcessKeepAlive
    }

    private object NoopProcessKeepAlive : ProcessKeepAlive {
        override fun release() {}
    }
}
