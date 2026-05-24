package com.slovy.slovymovyapp.speech

import com.slovy.slovymovyapp.data.Language
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
        if (!hasEnabledVoices(language)) return true

        val enabledVoices = getEnabledVoices(language)
        return voiceId in enabledVoices
    }

    suspend fun initializeDefaultVoices(
        language: Text2SpeechLanguage,
        allVoices: List<Text2SpeechVoice>
    ): Set<String> = withContext(Dispatchers.IO) {
        val defaultVoices = selectDefaultVoiceIds(allVoices)
        if (defaultVoices.isEmpty()) return@withContext emptySet()

        if (!hasEnabledVoices(language)) {
            setEnabledVoices(language, defaultVoices)
            return@withContext defaultVoices
        }

        getEnabledVoices(language)
    }

    @Deprecated("Temporary migration for old local-voice defaults. Remove after legacy settings are no longer expected.")
    suspend fun migrateLegacyDefaultVoiceSelection(
        language: Text2SpeechLanguage,
        allVoices: List<Text2SpeechVoice>
    ): Boolean = withContext(Dispatchers.IO) {
        if (!hasEnabledVoices(language)) return@withContext false

        val currentVoices = getEnabledVoices(language)
        val legacyDefaultVoices = selectLegacyDefaultVoiceIds(allVoices)
        val defaultVoices = selectDefaultVoiceIds(allVoices)
        if (legacyDefaultVoices.isEmpty() || defaultVoices.isEmpty()) return@withContext false
        if (currentVoices != legacyDefaultVoices || currentVoices == defaultVoices) return@withContext false

        setEnabledVoices(language, defaultVoices)
        true
    }

    suspend fun isVoiceSetupShown(language: Language): Boolean = withContext(Dispatchers.IO) {
        val repo = settingsRepo ?: return@withContext true
        val setting = repo.getById(Setting.Name.VOICE_SETUP_SHOWN) ?: return@withContext false
        val shownCodes = (setting.value as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            ?.toSet() ?: emptySet()
        language.code in shownCodes
    }

    suspend fun markVoiceSetupShown(language: Language) = withContext(Dispatchers.IO) {
        val repo = settingsRepo ?: return@withContext
        val existing = repo.getById(Setting.Name.VOICE_SETUP_SHOWN)
        val currentCodes = (existing?.value as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            ?.toMutableSet() ?: mutableSetOf()
        currentCodes.add(language.code)
        repo.insert(Setting(Setting.Name.VOICE_SETUP_SHOWN, JsonArray(currentCodes.map { JsonPrimitive(it) })))
    }

    suspend fun filterVoicesByEnabled(
        voices: List<Text2SpeechVoice>,
        language: Text2SpeechLanguage
    ): List<Text2SpeechVoice> {
        if (!hasEnabledVoices(language)) return voices

        val enabledVoiceIds = getEnabledVoices(language)
        return voices.filter { it.id in enabledVoiceIds }
    }

    suspend fun loadEnabledVoices(
        ttsManager: TextToSpeechManager,
        language: Text2SpeechLanguage,
    ): List<Text2SpeechVoice> {
        val allVoices = ttsManager.getVoicesForLanguage(language)
        initializeDefaultVoices(language, allVoices)
        return filterVoicesByEnabled(allVoices, language)
    }

    private fun selectDefaultVoiceIds(voices: List<Text2SpeechVoice>): Set<String> {
        val eligibleVoices = voices.filter { it.enabledByDefault }
        val fallbackVoices = voices.filter { !it.networkConnectionRequired }
        val sourceVoices = eligibleVoices.ifEmpty { fallbackVoices }

        return sourceVoices.map { it.id }.toSet()
    }

    private fun selectLegacyDefaultVoiceIds(voices: List<Text2SpeechVoice>): Set<String> {
        return voices
            .filter { !it.networkConnectionRequired }
            .map { it.id }
            .toSet()
    }
}
