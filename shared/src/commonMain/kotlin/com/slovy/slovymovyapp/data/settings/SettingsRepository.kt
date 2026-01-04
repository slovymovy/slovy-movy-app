package com.slovy.slovymovyapp.data.settings

import com.slovy.slovymovyapp.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

class SettingsRepository(private val db: AppDatabase) {

    suspend fun insert(setting: Setting) = withContext(Dispatchers.IO) {
        db.settingsQueries.insertSetting(
            id = setting.id,
            json_value = setting.value
        )
    }

    suspend fun getAll(): List<Setting> = withContext(Dispatchers.IO) {
        db.settingsQueries.selectAll().executeAsList().map { row ->
            Setting(id = row.id, value = row.json_value)
        }
    }

    suspend fun getById(id: Setting.Name): Setting? = withContext(Dispatchers.IO) {
        db.settingsQueries.selectById(id).executeAsOneOrNull()?.let { row ->
            Setting(id = row.id, value = row.json_value)
        }
    }

    suspend fun deleteById(id: Setting.Name) = withContext(Dispatchers.IO) {
        db.settingsQueries.deleteById(id)
    }
}
