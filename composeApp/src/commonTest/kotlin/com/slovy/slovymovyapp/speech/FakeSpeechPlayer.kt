package com.slovy.slovymovyapp.speech

import com.slovy.slovymovyapp.data.Language
import kotlinx.coroutines.CompletableDeferred

/**
 * Scripted [SpeechPlayer] for tests. Records calls, serves configured languages/voices, and lets a
 * test emit engine status events and hold voice loading open via [voiceLoadGate] to exercise races.
 */
class FakeSpeechPlayer : SpeechPlayer {
    val spokenTexts = mutableListOf<String>()
    val setVoices = mutableListOf<Text2SpeechVoice>()
    var stopCount = 0
        private set
    var openSettingsCount = 0
        private set

    /** Mirrors the Android engine: [openSettings] arms a rebind that the next check consumes. */
    var rebindCount = 0
        private set
    private var settingsVisited = false

    var languages: List<Text2SpeechLanguage> = emptyList()
    var voicesByLanguage: Map<Language, List<Text2SpeechVoice>> = emptyMap()

    /** When set, [getVoicesForLanguage] suspends until the deferred completes. */
    var voiceLoadGate: CompletableDeferred<Unit>? = null

    private val statusListeners = mutableMapOf<Any, (TTSStatus) -> Unit>()

    val hasStatusListeners: Boolean get() = statusListeners.isNotEmpty()

    /** Delivers an engine status event to every registered listener, like the real platform does. */
    fun emitStatus(status: TTSStatus) {
        statusListeners.values.toList().forEach { it(status) }
    }

    override fun speak(text: String) {
        spokenTexts.add(text)
    }

    override fun stop() {
        stopCount++
    }

    override suspend fun getAvailableLanguages(): List<Text2SpeechLanguage> = languages

    override suspend fun getVoicesForLanguage(language: Text2SpeechLanguage): List<Text2SpeechVoice> {
        voiceLoadGate?.await()
        return voicesByLanguage[language.language] ?: emptyList()
    }

    override fun setVoice(voice: Text2SpeechVoice) {
        setVoices.add(voice)
    }

    override fun openSettings() {
        openSettingsCount++
        settingsVisited = true
    }

    override fun rebindEngineIfNeeded(): Boolean {
        if (!settingsVisited) return false
        settingsVisited = false
        rebindCount++
        return true
    }

    override fun addOnStatusChangeListener(key: Any, listener: (TTSStatus) -> Unit) {
        statusListeners[key] = listener
    }

    override fun removeOnStatusChangeListener(key: Any) {
        statusListeners.remove(key)
    }
}
