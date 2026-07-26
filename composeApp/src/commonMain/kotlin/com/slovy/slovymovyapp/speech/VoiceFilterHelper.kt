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

    /**
     * The voices among [voices] the user enabled for [language].
     *
     * Voice ids belong to the engine that reported them, so a stored selection can match nothing in
     * [voices] — the user switched the default TTS engine, or uninstalled the voices they had
     * picked. Such a selection is ignored in favour of this engine's defaults rather than rewritten,
     * so audio keeps working and the original choice applies again as soon as its engine is back.
     * An empty stored selection is a deliberate "nothing enabled" and stays empty.
     */
    suspend fun filterVoicesByEnabled(
        voices: List<Text2SpeechVoice>,
        language: Text2SpeechLanguage
    ): List<Text2SpeechVoice> {
        if (!hasEnabledVoices(language)) return voices

        val enabledVoiceIds = getEnabledVoices(language)
        val enabled = voices.filter { it.id in enabledVoiceIds }
        if (enabled.isNotEmpty() || enabledVoiceIds.isEmpty()) return enabled

        val defaultIds = selectDefaultVoiceIds(voices)
        // An engine with only network voices offers no defaults; all of them beats silence.
        return voices.filter { it.id in defaultIds }.ifEmpty { voices }
    }

    /** The ids [filterVoicesByEnabled] would play, for UI that renders per-voice toggles. */
    suspend fun enabledVoiceIds(
        voices: List<Text2SpeechVoice>,
        language: Text2SpeechLanguage
    ): Set<String> = filterVoicesByEnabled(voices, language).map { it.id }.toSet()

    /** The enabled voices to play [language] with, seeding defaults on first use. */
    suspend fun loadEnabledVoices(
        speechPlayer: SpeechPlayer,
        language: Text2SpeechLanguage,
    ): List<Text2SpeechVoice> {
        val allVoices = speechPlayer.getVoicesForLanguage(language)
        initializeDefaultVoices(language, allVoices)
        return filterVoicesByEnabled(allVoices, language)
    }

    /**
     * Whether the first-run voice setup sheet should interrupt playback for [language]: true when
     * [voices] offers no better-than-medium quality voice and the sheet has not been shown yet.
     */
    suspend fun needsVoiceSetupPrompt(language: Language, voices: List<Text2SpeechVoice>): Boolean {
        if (voices.any { it.quality != VoiceQuality.MEDIUM }) return false
        return !isVoiceSetupShown(language)
    }

    /**
     * Whether [language] has at least one voice the user would actually hear: any installed voice
     * when no enabled-voice selection is stored, otherwise at least one enabled voice as
     * [filterVoicesByEnabled] resolves it. Unlike [loadEnabledVoices] this never seeds or rewrites a
     * selection, so it is safe to probe every language.
     */
    suspend fun hasPlayableVoice(speechPlayer: SpeechPlayer, language: Text2SpeechLanguage): Boolean {
        val allVoices = speechPlayer.getVoicesForLanguage(language)
        return filterVoicesByEnabled(allVoices, language).isNotEmpty()
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
