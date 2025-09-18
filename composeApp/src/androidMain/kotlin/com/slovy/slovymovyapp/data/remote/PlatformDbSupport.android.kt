package com.slovy.slovymovyapp.data.remote

import android.content.Context
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
import kotlinx.io.files.Path
import java.io.File
import java.io.FileOutputStream

actual class PlatformDbSupport actual constructor(androidContext: Any?) {
    private val ctx: Context = (androidContext as? Context)
        ?: error("Android Context is required for PlatformDbSupport on Android")

    actual fun getDatabasePath(name: String): Path = Path(ctx.getDatabasePath(name).absolutePath)

    actual fun ensureDatabasesDir() {
        ctx.getDatabasePath("placeholder.db").parentFile?.mkdirs()
    }

    actual fun fileExists(path: Path): Boolean = File(path.toString()).exists()

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

    actual fun createTranslationDataDriver(path: Path, readOnly: Boolean): SqlDriver {
        return androidSqliteDriver(path, TranslationDatabase.Schema, readOnly)
    }

    actual fun createHttpClient(): HttpClient {
        return HttpClient(OkHttp)
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
}
