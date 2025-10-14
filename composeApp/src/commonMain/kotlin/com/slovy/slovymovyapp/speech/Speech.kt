package com.slovy.slovymovyapp.speech

import com.slovy.slovymovyapp.data.Language

expect class TextToSpeechManager(androidContext: Any? = null) {
    fun speak(text: String)
    fun stop()
    suspend fun getAvailableLanguages(): List<Text2SpeechLanguage>
    suspend fun getVoicesForLanguage(language: Text2SpeechLanguage): List<Text2SpeechVoice>
    fun setVoice(voice: Text2SpeechVoice)
    fun openSettings()

    fun setOnWordBoundaryListener(listener: (wordRange: IntRange) -> Unit)
    fun setOnStatusChangeListener(listener: (status: TTSStatus) -> Unit)
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
    val quality: VoiceQuality,
    val networkConnectionRequired: Boolean
)

enum class VoiceQuality {
    MEDIUM, GOOD, BEST
}

enum class TTSStatus {
    IDLE, SPEAKING
}