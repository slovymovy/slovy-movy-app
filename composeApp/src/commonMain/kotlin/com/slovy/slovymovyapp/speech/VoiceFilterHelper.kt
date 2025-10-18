package com.slovy.slovymovyapp.speech

import com.slovy.slovymovyapp.data.settings.Setting
import com.slovy.slovymovyapp.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Helper class to manage voice filtering logic shared across platforms.
 */
class VoiceFilterHelper(private val settingsRepo: SettingsRepository?) {

    suspend fun hasEnabledVoices(language: Text2SpeechLanguage): Boolean = withContext(Dispatchers.IO) {
        val repo = settingsRepo ?: return@withContext false
        val setting = repo.getById(Setting.Name.ENABLED_VOICES) ?: return@withContext false

        val json = setting.value as? JsonObject ?: return@withContext false
        val langCode = language.language.code
        return@withContext langCode in json
    }

    suspend fun getEnabledVoices(language: Text2SpeechLanguage): Set<String> = withContext(Dispatchers.IO) {
        val repo = settingsRepo ?: return@withContext emptySet()
        val setting = repo.getById(Setting.Name.ENABLED_VOICES) ?: return@withContext emptySet()

        val json = setting.value as? JsonObject ?: return@withContext emptySet()
        val langCode = language.language.code
        val voicesArray = json[langCode] as? JsonArray ?: return@withContext emptySet()

        voicesArray.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.toSet()
    }

    suspend fun setEnabledVoices(language: Text2SpeechLanguage, voiceIds: Set<String>) = withContext(Dispatchers.IO) {
        val repo = settingsRepo ?: return@withContext
        val existing = repo.getById(Setting.Name.ENABLED_VOICES)
        val currentJson = (existing?.value as? JsonObject) ?: JsonObject(emptyMap())

        val langCode = language.language.code
        val updatedJson = JsonObject(
            currentJson.toMutableMap().apply {
                put(langCode, JsonArray(voiceIds.map { JsonPrimitive(it) }))
            }
        )

        repo.insert(Setting(Setting.Name.ENABLED_VOICES, updatedJson))
    }

    suspend fun isVoiceEnabled(voiceId: String, language: Text2SpeechLanguage): Boolean {
        val enabledVoices = getEnabledVoices(language)
        // If no voices are configured, all are considered enabled
        return enabledVoices.isEmpty() || voiceId in enabledVoices
    }

    suspend fun initializeDefaultVoices(
        language: Text2SpeechLanguage,
        allVoices: List<Text2SpeechVoice>
    ): Set<String> = withContext(Dispatchers.IO) {
        // Select voices that don't require network (local voices)
        val localVoices = allVoices.filter { !it.networkConnectionRequired }.map { it.id }.toSet()
        // Save default selection
        if (localVoices.isNotEmpty()) {
            setEnabledVoices(language, localVoices)
        }
        localVoices
    }

    suspend fun filterVoicesByEnabled(
        voices: List<Text2SpeechVoice>,
        language: Text2SpeechLanguage
    ): List<Text2SpeechVoice> {
        val enabledVoiceIds = getEnabledVoices(language)
        return if (enabledVoiceIds.isEmpty()) {
            // No filter set, return all voices
            voices
        } else {
            // Filter to only enabled voices
            voices.filter { it.id in enabledVoiceIds }
        }
    }
}
