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

    private fun jdbcSqliteDriver(
        path: Path,
        readOnly: Boolean,
        schema: SqlSchema<QueryResult.Value<Unit>>
    ): JdbcSqliteDriver {
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
                sql = "PRAGMA user_version",
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

    private fun setVersion(driver: SqlDriver, version: Long) {
        driver.execute(
            identifier = null,
            sql = "PRAGMA user_version = $version",
            parameters = 0
        )
    }

    private fun jdbcConnectionString(path: Path, readOnly: Boolean): String {
        val url = "jdbc:sqlite:file:${path}" + if (readOnly) "?mode=ro" else ""
        return url
    }

    actual fun createHttpClient(): HttpClient {
        return HttpClient(CIO)
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
}
