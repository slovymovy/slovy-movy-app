package com.slovy.slovymovyapp.data.settings

import com.slovy.slovymovyapp.db.AppDatabase

class SettingsRepository(private val db: AppDatabase) {

    fun insert(setting: Setting) {
        db.settingsQueries.insertSetting(
            id = setting.id,
            json_value = setting.value
        )
    }

    fun getAll(): List<Setting> = db.settingsQueries.selectAll().executeAsList().map { row ->
        Setting(id = row.id, value = row.json_value)
    }

    fun getById(id: Setting.Name): Setting? = db.settingsQueries.selectById(id).executeAsOneOrNull()?.let { row ->
        Setting(id = row.id, value = row.json_value)
    }

    fun deleteById(id: Setting.Name) {
        db.settingsQueries.deleteById(id)
    }
}
