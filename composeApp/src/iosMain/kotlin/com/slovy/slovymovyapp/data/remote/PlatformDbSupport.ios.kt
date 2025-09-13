package com.slovy.slovymovyapp.data.remote

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.slovy.slovymovyapp.dictionary.DictionaryDatabase
import com.slovy.slovymovyapp.translation.TranslationDatabase
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

    actual fun getDatabasePath(name: String): String = "$baseDir/$name"

    actual fun ensureDatabasesDir() {
        ensureDir(baseDir)
    }

    actual fun fileExists(path: String): Boolean = NSFileManager.defaultManager.fileExistsAtPath(path)

    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    actual fun openOutput(destPath: String): PlatformFileOutput {
        val fileManager = NSFileManager.defaultManager
        val parent = Path(destPath).parent?.toString() ?: error("Invalid path: $destPath")
        if (!fileManager.fileExistsAtPath(parent)) {
            fileManager.createDirectoryAtPath(parent, true, null, null)
        }
        fileManager.createFileAtPath(destPath, contents = null, attributes = null)
        val handle = NSFileHandle.fileHandleForWritingAtPath(destPath)
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
    actual fun deleteFile(path: String) {
        NSFileManager.defaultManager.removeItemAtPath(path, error = null)
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun markNoBackup(path: String) {
        // Exclude from iCloud and iTunes backups
        val url = NSURL.fileURLWithPath(path)
        url.setResourceValue(true, forKey = NSURLIsExcludedFromBackupKey, error = null)
    }

    actual fun createReadOnlyDictionaryDriver(dbFile: DbFile): SqlDriver {
        return NativeSqliteDriver(DictionaryDatabase.Schema, dbFile.path)
    }

    actual fun createReadOnlyTranslationDriver(dbFile: DbFile): SqlDriver {
        return NativeSqliteDriver(TranslationDatabase.Schema, dbFile.path)
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun ensureDir(path: String) {
        val fileManager = NSFileManager.defaultManager
        if (!fileManager.fileExistsAtPath(path)) {
            fileManager.createDirectoryAtPath(path, true, null, null)
        }
    }
}
