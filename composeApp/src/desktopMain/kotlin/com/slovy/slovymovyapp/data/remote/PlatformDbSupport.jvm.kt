package com.slovy.slovymovyapp.data.remote

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File
import java.io.FileOutputStream

actual class PlatformDbSupport actual constructor(androidContext: Any?) {
    private val baseDir: File by lazy {
        val home = System.getProperty("user.home") ?: "."
        File(home, ".slovymovyapp/databases").apply { mkdirs() }
    }

    actual fun getDatabasePath(name: String): String = File(baseDir, name).absolutePath

    actual fun ensureDatabasesDir() {
        baseDir.mkdirs()
    }

    actual fun fileExists(path: String): Boolean = File(path).exists()

    actual fun openOutput(destPath: String): PlatformFileOutput {
        File(destPath).parentFile?.mkdirs()
        val fos = FileOutputStream(File(destPath))
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

    actual fun deleteFile(path: String) {
        File(path).delete()
    }

    actual fun markNoBackup(path: String) {
        // No-op on desktop JVM
    }

    actual fun createReadOnlyDictionaryDriver(dbFile: DbFile): SqlDriver {
        val url = "jdbc:sqlite:${'$'}{dbFile.path}?mode=ro"
        return JdbcSqliteDriver(url)
    }

    actual fun createReadOnlyTranslationDriver(dbFile: DbFile): SqlDriver {
        val url = "jdbc:sqlite:${'$'}{dbFile.path}?mode=ro"
        return JdbcSqliteDriver(url)
    }
}
