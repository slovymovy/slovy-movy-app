package com.slovy.slovymovyapp.speech

import com.slovy.slovymovyapp.data.Language

/**
 * Platform-neutral speech surface implemented by [TextToSpeechManager]. Playback logic
 * ([RowAudioController], [RotatingVoiceSelector], view models) depends on this interface so tests
 * can drive it with a scripted fake.
 */
interface SpeechPlayer {
    fun speak(text: String)
    fun stop()
    suspend fun getAvailableLanguages(): List<Text2SpeechLanguage>
    suspend fun getVoicesForLanguage(language: Text2SpeechLanguage): List<Text2SpeechVoice>
    fun setVoice(voice: Text2SpeechVoice)
    fun openSettings()

    /**
     * Drops the current engine binding and connects again when [openSettings] has sent the user to
     * system settings since the last call, picking up a default-engine change made there. Returns
     * whether a rebind happened, so callers can reload engine-derived state. Every screen that can
     * open system settings calls this when it resumes; the pending flag lives in the player so the
     * entry point and the return point do not have to be the same screen. Always false on platforms
     * without pluggable engines.
     */
    fun rebindEngineIfNeeded(): Boolean

    fun addOnStatusChangeListener(key: Any, listener: (TTSStatus) -> Unit)
    fun removeOnStatusChangeListener(key: Any)
}

expect class TextToSpeechManager(androidContext: Any? = null) : SpeechPlayer {
    override fun speak(text: String)
    fun speak(text: String, language: Language)
    override fun stop()
    override suspend fun getAvailableLanguages(): List<Text2SpeechLanguage>
    override suspend fun getVoicesForLanguage(language: Text2SpeechLanguage): List<Text2SpeechVoice>
    override fun setVoice(voice: Text2SpeechVoice)
    override fun openSettings()

    override fun rebindEngineIfNeeded(): Boolean

    override fun addOnStatusChangeListener(key: Any, listener: (TTSStatus) -> Unit)
    override fun removeOnStatusChangeListener(key: Any)
}

data class Text2SpeechLanguage(
    val language: Language,
    val isAvailable: Boolean,
    val missingData: Boolean
)

data class Text2SpeechVoice(
    val id: String,
    val name: String?,
    val language: Language,
    val localeTag: String?,
    val quality: VoiceQuality,
    val networkConnectionRequired: Boolean,
    val enabledByDefault: Boolean
)

enum class VoiceQuality {
    MEDIUM, GOOD, BEST
}

enum class TTSStatus {
    IDLE, SPEAKING
}
