package com.slovy.slovymovyapp

import com.slovy.slovymovyapp.data.db.DatabaseProvider
import com.slovy.slovymovyapp.data.db.DriverFactory
import com.slovy.slovymovyapp.data.settings.Setting
import com.slovy.slovymovyapp.data.settings.SettingsRepository
import com.slovy.slovymovyapp.db.AppDatabase
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsRepositoryIosTest {
    @Test
    fun repository_works_on_ios_with_native_db() {
        val driver = DriverFactory(null).createDriver(":memory:")
        val db: AppDatabase = DatabaseProvider.createDatabase(driver)
        val repo = SettingsRepository(db)
        val setting = Setting("platform", "{\"name\": \"iOS\"}")
        repo.insert(setting)
        val all = repo.getAll()
        assertEquals(1, all.size)
        assertEquals("platform", all.first().id)
    }
}
