package com.slovy.slovymovyapp.data.remote

import app.cash.sqldelight.db.SqlDriver
import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.db.DatabaseProvider
import com.slovy.slovymovyapp.dictionary.DictionaryDatabase
import com.slovy.slovymovyapp.translation.TranslationDatabase
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Thread-safe cache for read-only dictionary and translation databases.
 * Caches SqlDriver and Database instances to avoid repeated driver creation.
 * Provides explicit lifecycle management for language switching and data cleanup.
 */
class ReadOnlyDatabaseCache(private val platform: PlatformDbSupport) {

    private data class CachedDatabase<T>(
        val driver: SqlDriver,
        val database: T
    )

    private val mutex = Mutex()
    private val dictionaries = mutableMapOf<Language, CachedDatabase<DictionaryDatabase>>()
    private val translations = mutableMapOf<Pair<Language, Language>, CachedDatabase<TranslationDatabase>>()

    /**
     * Gets a cached dictionary database, creating it if not already cached.
     */
    suspend fun getDictionary(lang: Language): DictionaryDatabase = mutex.withLock {
        dictionaries.getOrPut(lang) {
            val path = platform.getDatabasePath(DataDbManager.dictionaryFileName(lang))
            val driver = platform.createDictionaryDataDriver(path, true)
            val database = DatabaseProvider.createDictionaryDatabase(driver)
            CachedDatabase(driver, database)
        }.database
    }

    /**
     * Gets a cached translation database, creating it if not already cached.
     */
    suspend fun getTranslation(src: Language, tgt: Language): TranslationDatabase = mutex.withLock {
        val key = src to tgt
        translations.getOrPut(key) {
            val path = platform.getDatabasePath(DataDbManager.translationFileName(src, tgt))
            val driver = platform.createTranslationDataDriver(path, true)
            val database = DatabaseProvider.createTranslationDatabase(driver)
            CachedDatabase(driver, database)
        }.database
    }

    /**
     * Closes and removes a cached dictionary database.
     */
    suspend fun closeDictionary(lang: Language) = mutex.withLock {
        dictionaries.remove(lang)?.driver?.close()
    }

    /**
     * Closes and removes a cached translation database.
     */
    suspend fun closeTranslation(src: Language, tgt: Language) = mutex.withLock {
        translations.remove(src to tgt)?.driver?.close()
    }

    /**
     * Closes all cached databases for a specific language.
     * This includes the dictionary and all translations where this language is the source.
     */
    suspend fun closeAllForLanguage(lang: Language) = mutex.withLock {
        // Close dictionary
        dictionaries.remove(lang)?.driver?.close()

        // Close all translations where this language is the source
        val keysToRemove = translations.keys.filter { it.first == lang }
        keysToRemove.forEach { key ->
            translations.remove(key)?.driver?.close()
        }
    }

    /**
     * Closes all cached databases.
     */
    suspend fun closeAll() = mutex.withLock {
        dictionaries.values.forEach { it.driver.close() }
        dictionaries.clear()
        translations.values.forEach { it.driver.close() }
        translations.clear()
    }
}
