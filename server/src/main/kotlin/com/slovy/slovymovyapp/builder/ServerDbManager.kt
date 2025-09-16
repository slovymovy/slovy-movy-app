package com.slovy.slovymovyapp.builder

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.slovy.slovymovyapp.data.db.DatabaseProvider
import com.slovy.slovymovyapp.db.AppDatabase
import com.slovy.slovymovyapp.dictionary.DictionaryDatabase
import com.slovy.slovymovyapp.translation.TranslationDatabase
import java.io.File

/**
 * Simple DB manager that creates per-language dictionary DBs and per-translation-pair DBs.
 * Files are created under the provided outputDir.
 */
class ServerDbManager(private val outputDir: File) {
    init {
        if (!outputDir.exists()) outputDir.mkdirs()
    }

    fun dictionaryDbFile(langCode: String): File = File(outputDir, "dictionary_${langCode.lowercase()}.db")

    fun translationDbFile(sourceLang: String, targetLang: String): File =
        File(outputDir, "translation_${sourceLang.lowercase()}_${targetLang.lowercase()}.db")

    fun openDictionary(langCode: String): DictionaryDatabase {
        val file = dictionaryDbFile(langCode)
        val schema = DictionaryDatabase.Schema
        val driver = driver(file, schema)
        return DatabaseProvider.createDictionaryDatabase(driver)
    }

    fun openTranslation(sourceLang: String, targetLang: String): TranslationDatabase {
        val file = translationDbFile(sourceLang, targetLang)
        val schema = TranslationDatabase.Schema
        val driver = driver(file, schema)
        return DatabaseProvider.createTranslationDatabase(driver)
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
}
