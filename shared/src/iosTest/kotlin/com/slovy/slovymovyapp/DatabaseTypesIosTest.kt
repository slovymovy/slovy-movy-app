package com.slovy.slovymovyapp

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.slovy.slovymovyapp.dbtest.DbTypesTestLogic
import com.slovy.slovymovyapp.dictionary.DictionaryDatabase
import com.slovy.slovymovyapp.translation.TranslationDatabase
import kotlin.test.Test

class DatabaseTypesIosTest {
    @Test
    fun dictionary_types_round_trip() {
        val driver = NativeSqliteDriver(schema = DictionaryDatabase.Schema, name = "dictionary_types_ios.db")
        val out = DbTypesTestLogic.exerciseDictionary(driver)
        DbTypesTestLogic.validateDictionaryOutcome(out)
    }

    @Test
    fun translation_types_round_trip() {
        val driver = NativeSqliteDriver(schema = TranslationDatabase.Schema, name = "translation_types_ios.db")
        val out = DbTypesTestLogic.exerciseTranslation(driver)
        DbTypesTestLogic.validateTranslationOutcome(out)
    }
}
