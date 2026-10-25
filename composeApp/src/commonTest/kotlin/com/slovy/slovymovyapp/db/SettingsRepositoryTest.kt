package com.slovy.slovymovyapp.db

import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.settings.Setting
import com.slovy.slovymovyapp.data.settings.SettingsRepository
import com.slovy.slovymovyapp.test.BaseTest
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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
    fun search_language_round_trips_and_clears() = runBlocking {
        val repo = SettingsRepository(testAppDatabaseHolder().database)
        assertNull(repo.getSearchLanguage(), "No Search language must be stored before one is picked")

        repo.setSearchLanguage(Language.DUTCH)
        assertEquals(Language.DUTCH, repo.getSearchLanguage(), "A picked Search language must be read back")

        repo.setSearchLanguage(null)
        assertNull(repo.getSearchLanguage(), "Picking \"any\" must clear the stored Search language")
    }

    @Test
    fun first_preferred_language_wins_when_installed() {
        assertEquals(
            Language.GERMAN,
            SettingsRepository.pickLanguage(listOf(Language.DUTCH, Language.GERMAN), Language.GERMAN, Language.DUTCH),
            "the first preferred language must win when its dictionary is installed",
        )
    }

    @Test
    fun language_falls_back_to_next_preferred_then_any_installed() {
        assertEquals(
            Language.DUTCH,
            SettingsRepository.pickLanguage(listOf(Language.ENGLISH, Language.DUTCH), Language.GERMAN, Language.DUTCH),
            "a preferred language without its dictionary must yield to the next one",
        )
        assertEquals(
            Language.ENGLISH,
            SettingsRepository.pickLanguage(listOf(Language.ENGLISH), Language.GERMAN, Language.DUTCH),
            "with neither installed, any installed dictionary must be used",
        )
        assertNull(
            SettingsRepository.pickLanguage(emptyList(), Language.GERMAN, Language.DUTCH),
            "without an installed dictionary no language can be picked",
        )
    }

    @Test
    fun last_picked_language_round_trips() = runBlocking {
        val repo = SettingsRepository(testAppDatabaseHolder().database)
        repo.setLastPickedLanguage(Language.DUTCH)
        assertEquals(Language.DUTCH, repo.getLastPickedLanguage(), "A picked learning language must be read back")

        repo.setLastPickedLanguage(Language.GERMAN)
        assertEquals(Language.GERMAN, repo.getLastPickedLanguage(), "A later pick must replace the earlier one")
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
