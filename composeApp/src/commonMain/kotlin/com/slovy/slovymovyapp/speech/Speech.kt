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

    override fun addOnStatusChangeListener(key: Any, listener: (TTSStatus) -> Unit)
    override fun removeOnStatusChangeListener(key: Any)

    /**
     * Drops the current engine binding and connects again, picking up a default-engine
     * change the user made in system settings. No-op on platforms without pluggable engines.
     */
    fun rebindEngine()
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
