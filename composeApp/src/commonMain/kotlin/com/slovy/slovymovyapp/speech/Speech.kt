package com.slovy.slovymovyapp.speech

import com.slovy.slovymovyapp.data.Language

expect class TextToSpeechManager(androidContext: Any? = null) {
    fun speak(text: String)
    fun speak(text: String, language: Language)
    fun stop()
    suspend fun getAvailableLanguages(): List<Text2SpeechLanguage>
    suspend fun getVoicesForLanguage(language: Text2SpeechLanguage): List<Text2SpeechVoice>
    fun setVoice(voice: Text2SpeechVoice)
    fun openSettings()

    fun addOnStatusChangeListener(key: Any, listener: (TTSStatus) -> Unit)
    fun removeOnStatusChangeListener(key: Any)
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
