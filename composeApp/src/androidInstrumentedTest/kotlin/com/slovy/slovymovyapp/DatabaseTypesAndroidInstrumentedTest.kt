package com.slovy.slovymovyapp

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.slovy.slovymovyapp.dbtest.DbTypesTestLogic
import com.slovy.slovymovyapp.dictionary.DictionaryDatabase
import com.slovy.slovymovyapp.translation.TranslationDatabase
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseTypesAndroidInstrumentedTest {

    @Test
    fun dictionary_types_round_trip() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val driver = AndroidSqliteDriver(
            schema = DictionaryDatabase.Schema,
            context = context,
            name = "dictionary_types_android.db"
        )
        val out = DbTypesTestLogic.exerciseDictionary(driver)
        DbTypesTestLogic.validateDictionaryOutcome(out)
    }

    @Test
    fun translation_types_round_trip() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val driver = AndroidSqliteDriver(
            schema = TranslationDatabase.Schema,
            context = context,
            name = "translation_types_android.db"
        )
        val out = DbTypesTestLogic.exerciseTranslation(driver)
        DbTypesTestLogic.validateTranslationOutcome(out)
    }
}
