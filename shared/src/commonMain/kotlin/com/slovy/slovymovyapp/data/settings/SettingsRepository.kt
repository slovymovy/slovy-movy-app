package com.slovy.slovymovyapp.data.settings

import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive

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

    suspend fun getTranslationLanguagesOrNull(): Set<Language>? {
        val json = getById(Setting.Name.LANGUAGE)?.value ?: return null
        val codes = if (json is JsonArray) {
            json.mapNotNull { it.jsonPrimitive.content }
        } else {
            listOf(json.jsonPrimitive.content)
        }
        return codes.mapNotNull { Language.fromCodeOrNull(it) }.toSet()
    }

    suspend fun getTranslationLanguages(): Set<Language> =
        getTranslationLanguagesOrNull().orEmpty()

    suspend fun deleteById(id: Setting.Name) = withContext(Dispatchers.IO) {
        db.settingsQueries.deleteById(id)
    }

    suspend fun getDeveloperMode(default: Boolean): Boolean =
        getById(Setting.Name.DEVELOPER_MODE)?.value?.jsonPrimitive?.booleanOrNull ?: default

    suspend fun setDeveloperMode(enabled: Boolean) {
        insert(Setting(Setting.Name.DEVELOPER_MODE, JsonPrimitive(enabled)))
    }

    suspend fun isLegacyVoiceMigrationDone(): Boolean =
        getById(Setting.Name.LEGACY_VOICE_MIGRATION_DONE)?.value?.jsonPrimitive?.booleanOrNull ?: false

    suspend fun setLegacyVoiceMigrationDone() {
        insert(Setting(Setting.Name.LEGACY_VOICE_MIGRATION_DONE, JsonPrimitive(true)))
    }
}
