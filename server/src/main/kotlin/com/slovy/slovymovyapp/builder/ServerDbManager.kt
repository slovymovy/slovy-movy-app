package com.slovy.slovymovyapp.builder

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.slovy.slovymovyapp.data.db.DatabaseProvider
import com.slovy.slovymovyapp.db.AppDatabase
import com.slovy.slovymovyapp.dictionary.DictionaryDatabase
import com.slovy.slovymovyapp.ingestion.IngestionDbManager
import com.slovy.slovymovyapp.translation.TranslationDatabase
import java.io.File

/**
 * Simple DB manager that creates per-language dictionary DBs and per-translation-pair DBs.
 * Files are created under the provided outputDir.
 *
 * Implements [IngestionDbManager] for use with [com.slovy.slovymovyapp.ingestion.JsonIngestionBuilder].
 */
class ServerDbManager(private val outputDir: File) : IngestionDbManager {
    // Cache index SQL for recreation after bulk insert
    private val dictionaryIndexCache = mutableMapOf<String, List<String>>()
    private val translationIndexCache = mutableMapOf<String, List<String>>()

    init {
        if (!outputDir.exists()) outputDir.mkdirs()
    }

    fun dictionaryDbFile(langCode: String): File = File(outputDir, "dictionary_${langCode.lowercase()}.db")

    fun translationDbFile(sourceLang: String, targetLang: String): File =
        File(outputDir, "translation_${sourceLang.lowercase()}_${targetLang.lowercase()}.db")

    override fun openDictionary(langCode: String): DictionaryDatabase {
        val driver = dictionaryDriver(langCode)
        return DatabaseProvider.createDictionaryDatabase(driver)
    }

    private fun dictionaryDriver(langCode: String): JdbcSqliteDriver {
        val file = dictionaryDbFile(langCode)
        val schema = DictionaryDatabase.Schema
        val driver = driver(file, schema)
        return driver
    }

    override fun openTranslation(sourceLang: String, targetLang: String): TranslationDatabase {
        val driver = translationDriver(sourceLang, targetLang)
        return DatabaseProvider.createTranslationDatabase(driver)
    }

    private fun translationDriver(
        sourceLang: String,
        targetLang: String
    ): JdbcSqliteDriver {
        val file = translationDbFile(sourceLang, targetLang)
        val schema = TranslationDatabase.Schema
        val driver = driver(file, schema)
        return driver
    }

    fun openApp(): AppDatabase {
        val file = File(outputDir, "app.db")
        val schema = AppDatabase.Schema
        val driver = driver(file, schema)
        return DatabaseProvider.createAppDatabase(driver)
    }

    private fun driver(
        file: File,
        schema: SqlSchema<QueryResult.Value<Unit>>
    ): JdbcSqliteDriver {
        val isNew = !file.exists()
        val url = "jdbc:sqlite:${file.absolutePath}"
        val driver = JdbcSqliteDriver(url)
        if (isNew) {
            schema.create(driver)
        }
        return driver
    }

    private fun applyBulkInsertPragmas(driver: JdbcSqliteDriver) {
        driver.execute(null, "PRAGMA journal_mode=WAL", 0)
        driver.execute(null, "PRAGMA synchronous=OFF", 0)
        driver.execute(null, "PRAGMA temp_store=MEMORY", 0)
        driver.execute(null, "PRAGMA cache_size=120000", 0)
        driver.execute(null, "PRAGMA wal_autocheckpoint=0", 0)
    }

    private fun finishBulkInsertPragmas(driver: JdbcSqliteDriver) {
        driver.execute(null, "PRAGMA wal_checkpoint(TRUNCATE)", 0)
        driver.execute(null, "PRAGMA wal_autocheckpoint=1000", 0)
        driver.execute(null, "PRAGMA synchronous=FULL", 0)
    }

    fun openDictionaryForBulkInsert(langCode: String): DictionaryDatabase {
        val driver = dictionaryDriver(langCode)
        val db = DatabaseProvider.createDictionaryDatabase(driver)
        applyBulkInsertPragmas(driver)
        prepareDictionaryForBulkInsert(driver, langCode)
        return db
    }

    fun finishDictionaryBulkInsert(langCode: String) {
        val driver = dictionaryDriver(langCode)
        finishDictionaryBulkInsert(driver, langCode)
        finishBulkInsertPragmas(driver)
    }

    fun openTranslationForBulkInsert(sourceLang: String, targetLang: String): TranslationDatabase {
        val driver = translationDriver(sourceLang, targetLang)
        val db = DatabaseProvider.createTranslationDatabase(driver)
        applyBulkInsertPragmas(driver)
        val pairKey = "${sourceLang}_${targetLang}"
        prepareTranslationForBulkInsert(driver, pairKey)
        return db
    }

    fun finishTranslationBulkInsert(sourceLang: String, targetLang: String) {
        val pairKey = "${sourceLang}_${targetLang}"
        val driver = translationDriver(sourceLang, targetLang)
        finishTranslationBulkInsert(driver, pairKey)
        finishBulkInsertPragmas(driver)
    }

    private fun getIndexesForTables(driver: JdbcSqliteDriver): List<String> {
        val indexes = mutableListOf<String>()
        val sql = "SELECT sql FROM sqlite_master WHERE type='index' AND sql IS NOT NULL"
        driver.executeQuery(null, sql, { cursor ->
            while (cursor.next().value) {
                cursor.getString(0)?.let { indexes.add(it) }
            }
            QueryResult.Value(indexes)
        }, 0)
        return indexes
    }

    private fun dropIndexesForTables(driver: JdbcSqliteDriver) {
        val sql = "SELECT name FROM sqlite_master WHERE type='index' AND sql IS NOT NULL"
        val indexNames = mutableListOf<String>()
        driver.executeQuery(null, sql, { cursor ->
            while (cursor.next().value) {
                cursor.getString(0)?.let { indexNames.add(it) }
            }
            QueryResult.Value(indexNames)
        }, 0)
        for (indexName in indexNames) {
            driver.execute(null, "DROP INDEX \"$indexName\"", 0)
        }
    }

    private fun prepareDictionaryForBulkInsert(driver: JdbcSqliteDriver, langCode: String) {
        driver.execute(null, "PRAGMA foreign_keys=OFF", 0)
        dictionaryIndexCache[langCode] = getIndexesForTables(driver)
        dropIndexesForTables(driver)
    }

    private fun finishDictionaryBulkInsert(driver: JdbcSqliteDriver, langCode: String) {
        val indexes = dictionaryIndexCache.remove(langCode) ?: emptyList()
        for (indexSql in indexes) {
            driver.execute(null, indexSql, 0)
        }
        driver.execute(null, "PRAGMA foreign_keys=ON", 0)
    }

    private fun prepareTranslationForBulkInsert(driver: JdbcSqliteDriver, pairKey: String) {
        driver.execute(null, "PRAGMA foreign_keys=OFF", 0)
        translationIndexCache[pairKey] = getIndexesForTables(driver)
        dropIndexesForTables(driver)
    }

    private fun finishTranslationBulkInsert(driver: JdbcSqliteDriver, pairKey: String) {
        val indexes = translationIndexCache.remove(pairKey) ?: emptyList()
        for (indexSql in indexes) {
            driver.execute(null, indexSql, 0)
        }
        driver.execute(null, "PRAGMA foreign_keys=ON", 0)
    }
}
