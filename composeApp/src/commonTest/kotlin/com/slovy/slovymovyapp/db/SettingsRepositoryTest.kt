package com.slovy.slovymovyapp.db

import com.slovy.slovymovyapp.data.settings.Setting
import com.slovy.slovymovyapp.data.settings.SettingsRepository
import com.slovy.slovymovyapp.test.BaseTest
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

open class SettingsRepositoryTest : BaseTest() {

    @Test
    fun insert_and_query_and_delete_setting() = runBlocking {
        val repo = SettingsRepository(testAppDatabaseHolder().database)

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

    @Test
    fun study_autoplay_is_off_until_saved_and_survives_a_new_repository() = runBlocking {
        val database = testAppDatabaseHolder().database
        val repo = SettingsRepository(database)
        assertFalse(repo.getStudyAutoplay(), "Autoplay must default to off before the user turns it on")

        repo.setStudyAutoplay(true)
        assertTrue(
            SettingsRepository(database).getStudyAutoplay(),
            "A saved autoplay preference must be read back by the next session's repository",
        )

        repo.setStudyAutoplay(false)
        assertFalse(
            SettingsRepository(database).getStudyAutoplay(),
            "Turning autoplay off must be saved as well",
        )
    }
}
