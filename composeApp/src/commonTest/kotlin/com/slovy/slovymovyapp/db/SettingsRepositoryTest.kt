package com.slovy.slovymovyapp.db

import com.slovy.slovymovyapp.data.remote.DataDbManager
import com.slovy.slovymovyapp.data.settings.Setting
import com.slovy.slovymovyapp.data.settings.SettingsRepository
import com.slovy.slovymovyapp.test.BaseTest
import com.slovy.slovymovyapp.test.platformDbSupport
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

open class SettingsRepositoryTest : BaseTest() {

    @Test
    fun insert_and_query_and_delete_setting() {
        val db: AppDatabase = DataDbManager(platformDbSupport()).openAppDatabase()
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