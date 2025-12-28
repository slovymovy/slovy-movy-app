package com.slovy.slovymovyapp.data.local

import app.cash.sqldelight.db.SqlDriver
import com.slovy.slovymovyapp.data.db.DatabaseProvider
import com.slovy.slovymovyapp.data.remote.PlatformDbSupport
import com.slovy.slovymovyapp.dictionary.DictionaryDatabase
import com.slovy.slovymovyapp.translation.TranslationDatabase

/**
 * Manager for local writable dictionary and translation databases.
 * These databases store cached content and are included in backups.
 * Unlike downloaded databases, these support migrations for schema updates.
 */
class LocalDbManager(private val platform: PlatformDbSupport) {
    companion object {
        const val LOCAL_DICTIONARY_FILENAME = "local_dictionary.db"
        const val LOCAL_TRANSLATION_FILENAME = "local_translation.db"
    }

    private var dictionaryHolder: LocalDatabaseHolder<DictionaryDatabase>? = null
    private var translationHolder: LocalDatabaseHolder<TranslationDatabase>? = null

    fun openLocalDictionary(): DictionaryDatabase {
        return dictionaryHolder?.database ?: run {
            val path = platform.getDatabasePath(LOCAL_DICTIONARY_FILENAME)
            val driver = platform.createDictionaryDataDriver(path, readOnly = false)
            val database = DatabaseProvider.createDictionaryDatabase(driver)
            dictionaryHolder = LocalDatabaseHolder(driver, database)
            database
        }
    }

    fun openLocalTranslation(): TranslationDatabase {
        return translationHolder?.database ?: run {
            val path = platform.getDatabasePath(LOCAL_TRANSLATION_FILENAME)
            val driver = platform.createTranslationDataDriver(path, readOnly = false)
            val database = DatabaseProvider.createTranslationDatabase(driver)
            translationHolder = LocalDatabaseHolder(driver, database)
            database
        }
    }

    fun closeAll() {
        dictionaryHolder?.driver?.close()
        dictionaryHolder = null
        translationHolder?.driver?.close()
        translationHolder = null
    }
}

private class LocalDatabaseHolder<T>(
    val driver: SqlDriver,
    val database: T
)
