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

class SettingsRepositoryJvmTest {

    @Test
    fun insert_and_query_and_delete_setting() {
        val driver = DriverFactory(null).createDriver(IN_MEMORY)
        val db: AppDatabase = DatabaseProvider.createAppDatabase(driver)
        val repo = SettingsRepository(db)

        val setting = Setting(
            id = Setting.Name.TEST_PROPERTY,
            value = Json.parseToJsonElement("{\"mode\": \"dark\"}")
        )

        // Insert
        repo.insert(setting)

        // Find and compare
        val found = repo.getById(Setting.Name.TEST_PROPERTY)
        assertEquals(setting, found)

        // Delete and verify
        repo.deleteById(Setting.Name.TEST_PROPERTY)

        val foundAfterDelete = repo.getById(Setting.Name.TEST_PROPERTY)
        assertEquals(null, foundAfterDelete)
    }
}
