package com.slovy.slovymovyapp

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver.Companion.IN_MEMORY
import com.slovy.slovymovyapp.dbtest.DbTypesTestLogic
import com.slovy.slovymovyapp.dictionary.DictionaryDatabase
import com.slovy.slovymovyapp.translation.TranslationDatabase
import kotlin.test.Test

class DatabaseTypesJvmTest {
    @Test
    fun dictionary_types_round_trip() {
        val driver = JdbcSqliteDriver(url = IN_MEMORY, schema = DictionaryDatabase.Schema)
        val out = DbTypesTestLogic.exerciseDictionary(driver)
        DbTypesTestLogic.validateDictionaryOutcome(out)
    }

    @Test
    fun translation_types_round_trip() {
        val driver = JdbcSqliteDriver(url = IN_MEMORY, schema = TranslationDatabase.Schema)
        val out = DbTypesTestLogic.exerciseTranslation(driver)
        DbTypesTestLogic.validateTranslationOutcome(out)
    }
}
