package com.slovy.slovymovyapp.db

import com.slovy.slovymovyapp.data.settings.Setting
import com.slovy.slovymovyapp.data.settings.SettingsRepository
import com.slovy.slovymovyapp.test.BaseTest
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

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

    /**
     * The pending-recovery marker is written in settings and read back at startup to decide
     * whether to route through the download/finalize flow, so the two sides must agree on the
     * stored shape — an unset marker in particular must read as "not pending".
     */
    @Test
    fun pendingLemmaRecovery_roundTrips_andDefaultsToFalse() = runBlocking {
        val repo = SettingsRepository(testAppDatabaseHolder().database)
        repo.clearPendingLemmaRecovery()

        assertEquals(false, repo.isPendingLemmaRecovery(), "Unset marker must not request recovery")

        repo.setPendingLemmaRecovery()
        assertEquals(true, repo.isPendingLemmaRecovery(), "Marker must survive a write/read round trip")

        repo.clearPendingLemmaRecovery()
        assertEquals(
            false,
            repo.isPendingLemmaRecovery(),
            "Clearing after a recovery run must stop the next startup from routing through it",
        )
    }
}
