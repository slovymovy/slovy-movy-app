package com.slovy.slovymovyapp.data.remote

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.slovy.slovymovyapp.dictionary.DictionaryDatabase
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import java.io.File
import java.io.FileOutputStream

actual class PlatformDbSupport actual constructor(private val androidContext: Any?) {
    private val ctx: Context = (androidContext as? Context)
        ?: error("Android Context is required for PlatformDbSupport on Android")

    actual fun getDatabasePath(name: String): String = ctx.getDatabasePath(name).absolutePath

    actual fun ensureDatabasesDir() {
        ctx.getDatabasePath("placeholder.db").parentFile?.mkdirs()
    }

    actual fun fileExists(path: String): Boolean = File(path).exists()

    actual fun openOutput(destPath: String): PlatformFileOutput {
        // Ensure parent dir exists
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

    actual fun moveFile(from: String, to: String): Boolean {
        val src = File(from)
        val dst = File(to)
        dst.parentFile?.mkdirs()
        if (dst.exists()) dst.delete()
        return src.renameTo(dst)
    }

    actual fun markNoBackup(path: String) {
        // Auto Backup rules (fullBackupContent) in the app manifest include only app.db from the
        // databases domain. All downloaded DB files are excluded from backup automatically.
        // No runtime action needed here.
    }

    actual fun createDataReadonlyDriver(dbFile: DbFile): SqlDriver {
        val name = File(dbFile.path).name
        val result = AndroidSqliteDriver(
            schema = DictionaryDatabase.Schema,
            context = ctx,
            name = name
        )
        enforceQueryOnly(result)
        return result
    }

    actual fun createHttpClient(): HttpClient {
        return HttpClient(OkHttp)
    }

    actual fun getAvailableBytesForPath(path: String): Long? {
        return try {
            val dir = File(path).parentFile ?: File(path)
            val stat = android.os.StatFs(dir.absolutePath)
            stat.availableBytes
        } catch (_: Throwable) {
            null
        }
    }
}
