package com.slovy.slovymovyapp.data.local

import app.cash.sqldelight.db.SqlDriver
import com.slovy.slovymovyapp.data.db.DatabaseProvider
import com.slovy.slovymovyapp.data.remote.PlatformDbSupport
import com.slovy.slovymovyapp.dictionary.DictionaryDatabase
import com.slovy.slovymovyapp.translation.TranslationDatabase
import com.slovy.slovymovyapp.util.CoroutineReadWriteLock
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

/**
 * Manager for local writable dictionary and translation databases.
 * These databases store cached content and are included in backups.
 * Unlike downloaded databases, these support migrations for schema updates.
 *
 * Concurrency:
 * - The lease APIs ([leaseLocalDictionary], [leaseLocalTranslation]) and block-form helpers
 *   ([withLocalDictionary], [withLocalTranslation], [withLocalDictionaryIfExists],
 *   [withLocalTranslationIfExists]) acquire a *read lock* for the duration of their usage.
 *   Multiple readers can hold the lock concurrently.
 * - [closeAll] and [deleteAll] acquire a *write lock*: they drain all in-flight leases before
 *   closing drivers / deleting files, so a redownload cannot pull the file out from under an
 *   active query. While the write lock is held, new leases wait.
 * - Read-mode (`...IfExists`) variants pass `null` to the block if the underlying file does not
 *   exist on disk — useful for query paths that should treat "no local DB" as "no rows". The
 *   existence check happens **inside** the read lock, so a concurrent delete cannot race with it.
 * - Write-mode (`withLocalDictionary` / `withLocalTranslation`) variants lazily create the
 *   underlying file via SqlDelight bootstrap. Use these for ingestion paths that must write
 *   regardless of whether a file exists yet.
 *
 * The sync [openLocalDictionary] / [openLocalTranslation] accessors are `internal`-visibility
 * escape hatches kept for tests; production code should always go through a lease.
 */
class LocalDbManager(private val platform: PlatformDbSupport) {
    companion object {
        const val LOCAL_DICTIONARY_FILENAME = "local_dictionary.db"
        const val LOCAL_TRANSLATION_FILENAME = "local_translation.db"
    }

    private val rwLock = CoroutineReadWriteLock()
    private val holderLock = SynchronizedObject()
    private var dictionaryHolder: LocalDatabaseHolder<DictionaryDatabase>? = null
    private var translationHolder: LocalDatabaseHolder<TranslationDatabase>? = null

    // ===== Lease API — for callers in suspending contexts =====

    /**
     * AutoCloseable handle on the local dictionary database. The underlying driver is guaranteed
     * not to be closed (and the file not to be deleted) while the lease is open. Always release
     * via Kotlin's `.use { ... }`.
     */
    class LocalDictionaryLease internal constructor(
        val database: DictionaryDatabase,
        private val readLease: CoroutineReadWriteLock.ReadLease,
    ) : AutoCloseable {
        override fun close() = readLease.close()
    }

    /** As [LocalDictionaryLease] but for the local translation database. */
    class LocalTranslationLease internal constructor(
        val database: TranslationDatabase,
        private val readLease: CoroutineReadWriteLock.ReadLease,
    ) : AutoCloseable {
        override fun close() = readLease.close()
    }

    suspend fun leaseLocalDictionary(): LocalDictionaryLease {
        val readLease = rwLock.lease()
        try {
            return LocalDictionaryLease(getOrCreateDictionary(), readLease)
        } catch (e: Throwable) {
            readLease.close()
            throw e
        }
    }

    suspend fun leaseLocalTranslation(): LocalTranslationLease {
        val readLease = rwLock.lease()
        try {
            return LocalTranslationLease(getOrCreateTranslation(), readLease)
        } catch (e: Throwable) {
            readLease.close()
            throw e
        }
    }

    /**
     * Write-mode lease: passes the local dictionary to [block], creating an empty file + schema
     * via SqlDelight bootstrap if absent. Use for ingestion paths that need to write.
     */
    suspend fun <T> withLocalDictionary(block: suspend (DictionaryDatabase) -> T): T =
        rwLock.read { block(getOrCreateDictionary()) }

    /**
     * Write-mode lease for the local translation. See [withLocalDictionary].
     */
    suspend fun <T> withLocalTranslation(block: suspend (TranslationDatabase) -> T): T =
        rwLock.read { block(getOrCreateTranslation()) }

    /**
     * Read-mode lease: passes the local dictionary to [block], or `null` if the file does not
     * exist on disk. Does **not** auto-create. Existence is checked inside the read lock, so a
     * concurrent [deleteAll] cannot race with the check. Use for query paths.
     */
    suspend fun <T> withLocalDictionaryIfExists(block: suspend (DictionaryDatabase?) -> T): T =
        rwLock.read {
            val db = if (hasLocalDictionary()) getOrCreateDictionary() else null
            block(db)
        }

    /** Read-mode lease for the local translation. See [withLocalDictionaryIfExists]. */
    suspend fun <T> withLocalTranslationIfExists(block: suspend (TranslationDatabase?) -> T): T =
        rwLock.read {
            val db = if (hasLocalTranslation()) getOrCreateTranslation() else null
            block(db)
        }

    // ===== Legacy sync API — `internal` so tests can still drive the write side without going
    // through a lease. Production code must use one of the suspending lease APIs above so that
    // closeAll/deleteAll can properly drain readers. =====

    /**
     * Returns the cached local dictionary database, creating it if absent. Non-suspending —
     * thread-safe for driver creation (synchronized holder access) but does **not** participate
     * in read-lease accounting, so a concurrent [closeAll] / [deleteAll] could tear down the
     * driver while a caller still holds the reference. Intended only for tests; production code
     * should use [withLocalDictionary] (write) or [withLocalDictionaryIfExists] (read).
     */
    internal fun openLocalDictionary(): DictionaryDatabase = getOrCreateDictionary()

    /** See [openLocalDictionary]. */
    internal fun openLocalTranslation(): TranslationDatabase = getOrCreateTranslation()

    fun hasLocalDictionary(): Boolean {
        val path = platform.getDatabasePath(LOCAL_DICTIONARY_FILENAME)
        return platform.fileExists(path) && (platform.getFileSize(path) ?: 0L) > 0L
    }

    fun hasLocalTranslation(): Boolean =
        platform.getDatabasePath(LOCAL_TRANSLATION_FILENAME).let { path ->
            platform.fileExists(path) && (platform.getFileSize(path) ?: 0L) > 0L
        }

    // ===== Close / delete — drains lease holders, then closes / deletes =====

    suspend fun closeAll() = rwLock.write {
        synchronized(holderLock) {
            dictionaryHolder?.driver?.let { runCatching { it.close() } }
            dictionaryHolder = null
            translationHolder?.driver?.let { runCatching { it.close() } }
            translationHolder = null
        }
    }

    suspend fun deleteAll() = rwLock.write {
        synchronized(holderLock) {
            dictionaryHolder?.driver?.let { runCatching { it.close() } }
            dictionaryHolder = null
            translationHolder?.driver?.let { runCatching { it.close() } }
            translationHolder = null
        }

        val dictPath = platform.getDatabasePath(LOCAL_DICTIONARY_FILENAME)
        if (platform.fileExists(dictPath)) {
            platform.deleteFile(dictPath)
        }

        val transPath = platform.getDatabasePath(LOCAL_TRANSLATION_FILENAME)
        if (platform.fileExists(transPath)) {
            platform.deleteFile(transPath)
        }
    }

    // ===== Internal helpers =====

    private fun getOrCreateDictionary(): DictionaryDatabase = synchronized(holderLock) {
        dictionaryHolder?.database ?: run {
            val path = platform.getDatabasePath(LOCAL_DICTIONARY_FILENAME)
            val driver = platform.createDictionaryDataDriver(path, readOnly = false)
            val database = DatabaseProvider.createDictionaryDatabase(driver)
            dictionaryHolder = LocalDatabaseHolder(driver, database)
            database
        }
    }

    private fun getOrCreateTranslation(): TranslationDatabase = synchronized(holderLock) {
        translationHolder?.database ?: run {
            val path = platform.getDatabasePath(LOCAL_TRANSLATION_FILENAME)
            val driver = platform.createTranslationDataDriver(path, readOnly = false)
            val database = DatabaseProvider.createTranslationDatabase(driver)
            translationHolder = LocalDatabaseHolder(driver, database)
            database
        }
    }
}

private class LocalDatabaseHolder<T>(
    val driver: SqlDriver,
    val database: T
)
