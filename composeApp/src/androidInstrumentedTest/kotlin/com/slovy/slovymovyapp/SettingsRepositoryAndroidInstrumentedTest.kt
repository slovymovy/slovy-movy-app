package com.slovy.slovymovyapp

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.slovy.slovymovyapp.data.db.DatabaseProvider
import com.slovy.slovymovyapp.data.db.DriverFactory
import com.slovy.slovymovyapp.data.settings.Setting
import com.slovy.slovymovyapp.data.settings.SettingsRepository
import com.slovy.slovymovyapp.db.AppDatabase
import junit.framework.TestCase.assertEquals
import kotlinx.serialization.json.Json
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsRepositoryAndroidInstrumentedTest {
    @Test
    fun repository_works_on_android_with_real_db() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        val driver = DriverFactory(context).createDriver("test.db")
        val db: AppDatabase = DatabaseProvider.createDatabase(driver)
        val repo = SettingsRepository(db)
        val setting = Setting(Setting.Name.TEST_PROPERTY, Json.parseToJsonElement("{\"version\": \"1.0\"}"))
        repo.insert(setting)
        val byId = repo.getById(Setting.Name.TEST_PROPERTY)
        assertEquals(setting, byId)
    }
}