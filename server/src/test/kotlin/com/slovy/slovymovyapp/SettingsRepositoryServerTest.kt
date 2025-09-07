package com.slovy.slovymovyapp

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver.Companion.IN_MEMORY
import com.slovy.slovymovyapp.data.db.DatabaseProvider
import com.slovy.slovymovyapp.data.db.DriverFactory
import com.slovy.slovymovyapp.data.settings.Setting
import com.slovy.slovymovyapp.data.settings.SettingsRepository
import com.slovy.slovymovyapp.db.AppDatabase
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsRepositoryServerTest {
    @Test
    fun repository_works_on_server_with_in_memory_db() {

        val driver = DriverFactory(null).createDriver(IN_MEMORY)
        val db: AppDatabase = DatabaseProvider.createAppDatabase(driver)
        val repo = SettingsRepository(db)
        val setting = Setting(Setting.Name.TEST_PROPERTY, Json.parseToJsonElement("{\"port\": 8080}"))
        repo.insert(setting)
        val byId = repo.getById(Setting.Name.TEST_PROPERTY)
        assertEquals(setting, byId)
    }
}
