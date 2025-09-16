package com.slovy.slovymovyapp

import com.slovy.slovymovyapp.builder.ServerDbManager
import com.slovy.slovymovyapp.data.settings.Setting
import com.slovy.slovymovyapp.data.settings.SettingsRepository
import com.slovy.slovymovyapp.db.AppDatabase
import kotlinx.serialization.json.Json
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsRepositoryServerTest {
    @Test
    fun repository_works_on_server_with_in_memory_db() {
        val db: AppDatabase = ServerDbManager(Files.createTempDirectory("tmpDirPrefix").toFile()).openApp()
        val repo = SettingsRepository(db)
        val setting = Setting(Setting.Name.TEST_PROPERTY, Json.parseToJsonElement("{\"port\": 8080}"))
        repo.insert(setting)
        val byId = repo.getById(Setting.Name.TEST_PROPERTY)
        assertEquals(setting, byId)
    }
}
