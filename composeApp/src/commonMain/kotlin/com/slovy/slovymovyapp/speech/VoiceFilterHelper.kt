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

    /**
     * Re-applies defaults when the stored selection belongs to an engine that is no longer bound.
     *
     * Voice ids are engine-specific, so a stored set that shares nothing with the voices the
     * current engine reports can only have come from a different engine. Requiring zero overlap
     * keeps deliberate per-voice choices intact for the engine they were made on.
     */
    suspend fun reconcileVoicesForEngineChange(
        language: Text2SpeechLanguage,
        allVoices: List<Text2SpeechVoice>
    ): Set<String> = withContext(Dispatchers.IO) {
        if (!isSelectionFromUnboundEngine(language, allVoices)) return@withContext getEnabledVoices(language)

        val defaultVoices = selectDefaultVoiceIds(allVoices)
        setEnabledVoices(language, defaultVoices)
        defaultVoices
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

    /**
     * The enabled voices to play [language] with. Reconciling here is what keeps playback working
     * after an engine change: a selection stored for the previous engine matches none of the new
     * engine's ids, so filtering alone would leave the language silent even though voices exist.
     */
    suspend fun loadEnabledVoices(
        speechPlayer: SpeechPlayer,
        language: Text2SpeechLanguage,
    ): List<Text2SpeechVoice> {
        val allVoices = speechPlayer.getVoicesForLanguage(language)
        initializeDefaultVoices(language, allVoices)
        reconcileVoicesForEngineChange(language, allVoices)
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
     * when no enabled-voice selection is stored, otherwise at least one still-enabled voice.
     * Unlike [loadEnabledVoices] this never seeds or rewrites a selection, so it is safe to probe
     * every language.
     *
     * A selection stored for a different engine counts as playable too: it matches none of the
     * bound engine's ids, but the [loadEnabledVoices] on the next play reconciles it to that
     * engine's defaults. Reporting it as unplayable would hide the row speakers right after an
     * engine change, before playback ever gets the chance to repair the selection.
     */
    suspend fun hasPlayableVoice(speechPlayer: SpeechPlayer, language: Text2SpeechLanguage): Boolean {
        val allVoices = speechPlayer.getVoicesForLanguage(language)
        if (filterVoicesByEnabled(allVoices, language).isNotEmpty()) return true
        return isSelectionFromUnboundEngine(language, allVoices)
    }

    /**
     * Whether the stored selection for [language] can only have come from an engine other than the
     * one that reported [allVoices]: it is non-empty, shares no id with them, and the current
     * engine offers defaults to fall back to. An empty stored set is a deliberate "nothing enabled"
     * choice, not a stale one.
     */
    private suspend fun isSelectionFromUnboundEngine(
        language: Text2SpeechLanguage,
        allVoices: List<Text2SpeechVoice>
    ): Boolean {
        if (allVoices.isEmpty()) return false

        val stored = getEnabledVoices(language)
        if (stored.isEmpty()) return false

        val availableIds = allVoices.map { it.id }.toSet()
        if (stored.any { it in availableIds }) return false

        return selectDefaultVoiceIds(allVoices).isNotEmpty()
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
