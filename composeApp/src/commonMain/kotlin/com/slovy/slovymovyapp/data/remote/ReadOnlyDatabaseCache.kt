package com.slovy.slovymovyapp.data.remote

import app.cash.sqldelight.db.SqlDriver
import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.db.DatabaseProvider
import com.slovy.slovymovyapp.dictionary.DictionaryDatabase
import com.slovy.slovymovyapp.translation.TranslationDatabase
import com.slovy.slovymovyapp.util.CoroutineReadWriteLock
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

/**
 * Thread-safe cache for read-only dictionary and translation databases.
 *
 * Concurrency model:
 * - Reads ([leaseDictionary], [withDictionary], etc.) acquire a *read lock*. Multiple readers
 *   run concurrently against the same cached driver — the SqlDelight `Pool` inside the driver
 *   can grow connections freely while the lock is held.
 * - Closes ([closeDictionaryGuarded] etc.) acquire the *write lock*. They wait for every
 *   in-flight reader to release before closing the driver and running the caller's
 *   `postCloseAction` (typically `platform.deleteFile`). New readers wait until the close
 *   completes.
 *
 * This eliminates the iOS Pool-growth race where SqlDelight tried to open a new connection just
 * as another thread unlinked the underlying file.
 *
 * The lease APIs return [AutoCloseable] handles, so callers can use Kotlin's standard `.use`:
 * ```
 * cache.leaseDictionary(lang).use { lease ->
 *     lease.database.dictionaryQueries.someQuery().executeAsList()
 * }
 * ```
 *
 * For block-style usage, [withDictionary] / [withTranslation] are equivalent helpers.
 */
class ReadOnlyDatabaseCache(private val platform: PlatformDbSupport) {

    private val rwLock = CoroutineReadWriteLock()
    private val mapLock = SynchronizedObject()
    private val dictionaries = mutableMapOf<Language, CachedDictionary>()
    private val translations = mutableMapOf<Pair<Language, Language>, CachedTranslation>()

    private class CachedDictionary(val driver: SqlDriver, val database: DictionaryDatabase)
    private class CachedTranslation(val driver: SqlDriver, val database: TranslationDatabase)

    // ===== Block-form lease API — all `internal` so the cache stays an implementation detail of
    // DataDbManager. Production code goes through DataDbManager.withDictionaryReadOnly* /
    // withTranslationReadOnly* which delegate here. =====

    /**
     * Read-mode lease: passes the dictionary to [block], or `null` if the file does not exist on
     * disk. Existence is checked inside the read lock so it cannot race with a concurrent close.
     */
    internal suspend fun <T> withDictionaryIfExists(
        lang: Language,
        block: suspend (DictionaryDatabase?) -> T,
    ): T = rwLock.read {
        val path = platform.getDatabasePath(DataDbManager.dictionaryFileName(lang))
        val db = if (platform.fileExists(path)) getOrCreateDictionary(lang).database else null
        block(db)
    }

    /** Read-mode lease for the translation database. See [withDictionaryIfExists]. */
    internal suspend fun <T> withTranslationIfExists(
        src: Language,
        tgt: Language,
        block: suspend (TranslationDatabase?) -> T,
    ): T = rwLock.read {
        val path = platform.getDatabasePath(DataDbManager.translationFileName(src, tgt))
        val db = if (platform.fileExists(path)) getOrCreateTranslation(src, tgt).database else null
        block(db)
    }

    // ===== Close API (drains readers, then closes drivers + runs postAction) =====

    internal suspend fun <T> closeDictionaryGuarded(
        lang: Language,
        postCloseAction: suspend () -> T,
    ): T = rwLock.write {
        val removed = synchronized(mapLock) { dictionaries.remove(lang) }
        removed?.let { runCatching { it.driver.close() } }
        postCloseAction()
    }

    internal suspend fun <T> closeTranslationGuarded(
        src: Language,
        tgt: Language,
        postCloseAction: suspend () -> T,
    ): T = rwLock.write {
        val removed = synchronized(mapLock) { translations.remove(src to tgt) }
        removed?.let { runCatching { it.driver.close() } }
        postCloseAction()
    }

    internal suspend fun <T> closeAllForLanguageGuarded(
        lang: Language,
        postCloseAction: suspend () -> T,
    ): T = rwLock.write {
        val drivers = synchronized(mapLock) {
            val list = mutableListOf<SqlDriver>()
            dictionaries.remove(lang)?.let { list.add(it.driver) }
            val translationKeys = translations.keys.filter { it.first == lang }.toList()
            translationKeys.forEach { key ->
                translations.remove(key)?.let { list.add(it.driver) }
            }
            list
        }
        drivers.forEach { runCatching { it.close() } }
        postCloseAction()
    }

    internal suspend fun closeAll() = closeAllGuarded { }

    internal suspend fun <T> closeAllGuarded(postCloseAction: suspend () -> T): T = rwLock.write {
        val drivers = synchronized(mapLock) {
            val list = dictionaries.values.map { it.driver } + translations.values.map { it.driver }
            dictionaries.clear()
            translations.clear()
            list
        }
        drivers.forEach { runCatching { it.close() } }
        postCloseAction()
    }

    // ===== Internal helpers =====

    private fun getOrCreateDictionary(lang: Language): CachedDictionary = synchronized(mapLock) {
        dictionaries.getOrPut(lang) {
            val path = platform.getDatabasePath(DataDbManager.dictionaryFileName(lang))
            val driver = platform.createDictionaryDataDriver(path, true)
            val database = DatabaseProvider.createDictionaryDatabase(driver)
            CachedDictionary(driver, database)
        }
    }

    private fun getOrCreateTranslation(src: Language, tgt: Language): CachedTranslation =
        synchronized(mapLock) {
            translations.getOrPut(src to tgt) {
                val path = platform.getDatabasePath(DataDbManager.translationFileName(src, tgt))
                val driver = platform.createTranslationDataDriver(path, true)
                val database = DatabaseProvider.createTranslationDatabase(driver)
                CachedTranslation(driver, database)
            }
        }
}
