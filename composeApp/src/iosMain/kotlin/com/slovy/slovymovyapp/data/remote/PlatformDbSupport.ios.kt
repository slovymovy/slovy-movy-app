package com.slovy.slovymovyapp.data.remote

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.slovy.slovymovyapp.db.AppDatabase
import com.slovy.slovymovyapp.dictionary.DictionaryDatabase
import com.slovy.slovymovyapp.translation.TranslationDatabase
import io.ktor.client.*
import io.ktor.client.engine.darwin.*
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.io.files.Path
import platform.Foundation.*

actual class PlatformDbSupport actual constructor(androidContext: Any?) {
    private val baseDir: String by lazy {
        val paths = NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true)
        val dir = (paths as NSArray).objectAtIndex(0u) as? String ?: NSTemporaryDirectory()
        val full = "$dir/databases"
        ensureDir(full)
        full
    }

    actual fun getDatabasePath(name: String): Path = Path("$baseDir/$name")

    actual fun ensureDatabasesDir() {
        ensureDir(baseDir)
    }

    actual fun fileExists(path: Path): Boolean = NSFileManager.defaultManager.fileExistsAtPath(path.toString())

    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    actual fun openOutput(destPath: Path): PlatformFileOutput {
        val fileManager = NSFileManager.defaultManager
        val parent = destPath.parent?.toString() ?: error("Invalid path: $destPath")
        if (!fileManager.fileExistsAtPath(parent)) {
            fileManager.createDirectoryAtPath(parent, true, null, null)
        }
        fileManager.createFileAtPath(destPath.toString(), contents = null, attributes = null)
        val handle = NSFileHandle.fileHandleForWritingAtPath(destPath.toString())
            ?: error("Unable to open file for writing")
        handle.seekToEndOfFile()
        return object : PlatformFileOutput {
            override fun write(buffer: ByteArray, offset: Int, length: Int) {
                buffer.usePinned { pinned ->
                    val startPtr = pinned.addressOf(offset)
                    val data = NSData.create(bytes = startPtr, length = length.toULong())
                    handle.writeData(data)
                }
            }

            override fun flush() { /* NSFileHandle writes immediately */
            }

            override fun close() {
                handle.closeFile()
            }
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun deleteFile(path: Path) {
        NSFileManager.defaultManager.removeItemAtPath(path.toString(), error = null)
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun moveFile(from: Path, to: Path): Boolean {
        val fm = NSFileManager.defaultManager
        // Ensure destination directory exists
        val toUrl = NSURL.fileURLWithPath(to.toString())
        val parent = toUrl.URLByDeletingLastPathComponent?.path
        if (parent != null && !fm.fileExistsAtPath(parent)) {
            fm.createDirectoryAtPath(parent, true, null, null)
        }
        // Remove destination if present
        if (fm.fileExistsAtPath(to.toString())) {
            fm.removeItemAtPath(to.toString(), error = null)
        }
        return fm.moveItemAtPath(from.toString(), toPath = to.toString(), error = null)
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun markNoBackup(path: Path) {
        // Exclude from iCloud and iTunes backups
        val url = NSURL.fileURLWithPath(path.toString())
        url.setResourceValue(true, forKey = NSURLIsExcludedFromBackupKey, error = null)
    }

    actual fun createDictionaryDataDriver(path: Path, readOnly: Boolean): SqlDriver {
        return nativeSqliteDriver(DictionaryDatabase.Schema, path, readOnly)
    }

    actual fun createTranslationDataDriver(path: Path, readOnly: Boolean): SqlDriver {
        return nativeSqliteDriver(TranslationDatabase.Schema, path, readOnly)
    }

    actual fun createAppDataDriver(path: Path): SqlDriver {
        return nativeSqliteDriver(AppDatabase.Schema, path, false)
    }

    private fun nativeSqliteDriver(
        schema: SqlSchema<QueryResult.Value<Unit>>,
        path: Path,
        readOnly: Boolean
    ): NativeSqliteDriver {
        // SQLiter/NativeSqliteDriver expects a filename without path; provide basePath separately.
        val name = NSURL.fileURLWithPath(path.toString()).lastPathComponent ?: path.toString()

        // When opening in read-only mode, avoid running schema create/migrate.
        // We wrap the schema with a no-op implementation (same version) so the driver doesn't try
        // to perform any migrations or DDL. Queries are still allowed via enforceQueryOnly.
        val effectiveSchema = if (readOnly) object : SqlSchema<QueryResult.Value<Unit>> {
            override val version: Long = schema.version
            override fun create(driver: SqlDriver): QueryResult.Value<Unit> {
                return QueryResult.Unit
            }

            override fun migrate(
                driver: SqlDriver,
                oldVersion: Long,
                newVersion: Long,
                vararg callbacks: app.cash.sqldelight.db.AfterVersion
            ): QueryResult.Value<Unit> {
                return QueryResult.Unit
            }
        } else schema

        val result = NativeSqliteDriver(
            schema = effectiveSchema,
            name = name,
            onConfiguration = { cfg ->
                val ext = cfg.extendedConfig.copy(basePath = baseDir)
                cfg.copy(extendedConfig = ext)
            }
        )
        if (readOnly) {
            enforceQueryOnly(result)
        }
        return result
    }

    actual fun createHttpClient(): HttpClient {
        return HttpClient(Darwin)
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun getAvailableBytesForPath(path: Path): Long? {
        return try {
            val fm = NSFileManager.defaultManager
            val dir = Path(path.toString()).parent?.toString() ?: path.toString()
            val attrs = fm.attributesOfFileSystemForPath(dir, error = null)
            val free = attrs?.get(NSFileSystemFreeSize) as? NSNumber
            free?.longLongValue
        } catch (_: Throwable) {
            null
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun ensureDir(path: String) {
        val fileManager = NSFileManager.defaultManager
        if (!fileManager.fileExistsAtPath(path)) {
            fileManager.createDirectoryAtPath(path, true, null, null)
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun listFiles(path: Path): List<Path> {
        val fm = NSFileManager.defaultManager
        val pathStr = path.toString()
        val dir = if (fm.fileExistsAtPath(pathStr)) {
            val url = NSURL.fileURLWithPath(pathStr)
            if (url.hasDirectoryPath) pathStr else (Path(pathStr).parent?.toString() ?: pathStr)
        } else {
            Path(pathStr).parent?.toString() ?: pathStr
        }

        val contents = fm.contentsOfDirectoryAtPath(dir, error = null) as? List<*>
        return contents?.mapNotNull { filename ->
            (filename as? String)?.let { Path("$dir/$it") }
        } ?: emptyList()
    }
}
