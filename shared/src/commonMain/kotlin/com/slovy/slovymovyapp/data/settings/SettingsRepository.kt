package com.slovy.slovymovyapp.data.settings

import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.learning.StudyFormat
import com.slovy.slovymovyapp.data.learning.StudyFormatSelection
import com.slovy.slovymovyapp.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
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

    suspend fun setTranslationLanguages(languages: Collection<Language>) {
        val codes = languages.distinct().sortedBy { it.ordinal }.map { JsonPrimitive(it.code) }
        insert(Setting(Setting.Name.LANGUAGE, JsonArray(codes)))
    }

    /** Adds [language] to the stored translation languages, leaving the existing ones untouched. */
    suspend fun addTranslationLanguage(language: Language) {
        setTranslationLanguages(getTranslationLanguages() + language)
    }

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

    /** Whether study sessions speak each card's word on their own. Off until the user turns it on. */
    suspend fun getStudyAutoplay(): Boolean =
        getById(Setting.Name.STUDY_AUTOPLAY)?.value?.jsonPrimitive?.booleanOrNull ?: false

    suspend fun setStudyAutoplay(enabled: Boolean) {
        insert(Setting(Setting.Name.STUDY_AUTOPLAY, JsonPrimitive(enabled)))
    }

    /**
     * The study formats chosen for [language]; every format when none has been stored. Names
     * that no longer exist are dropped, and a selection left empty by that falls back to all,
     * since a language with no format could not be studied.
     */
    suspend fun getStudyFormats(language: Language): StudyFormatSelection {
        val json = getById(Setting.Name.STUDY_FORMATS)?.value as? JsonObject ?: return StudyFormatSelection.ALL
        val names = json[language.code] as? JsonArray ?: return StudyFormatSelection.ALL
        val formats = names
            .mapNotNull { element -> (element as? JsonPrimitive)?.contentOrNull }
            .mapNotNull { name -> StudyFormat.entries.firstOrNull { it.name == name } }
            .toSet()
        return if (formats.isEmpty()) StudyFormatSelection.ALL else StudyFormatSelection(formats)
    }

    suspend fun setStudyFormats(language: Language, selection: StudyFormatSelection) {
        val existing = getById(Setting.Name.STUDY_FORMATS)?.value as? JsonObject ?: JsonObject(emptyMap())
        val names = StudyFormat.entries.filter { it in selection.enabled }.map { JsonPrimitive(it.name) }
        val updated = JsonObject(existing + (language.code to JsonArray(names)))
        insert(Setting(Setting.Name.STUDY_FORMATS, updated))
    }
}
