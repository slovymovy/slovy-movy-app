package com.slovy.slovymovyapp.speech

import com.slovy.slovymovyapp.data.Language

actual class TextToSpeechManager actual constructor(androidContext: Any?) : SpeechPlayer {
    private val statusListeners = mutableMapOf<Any, (TTSStatus) -> Unit>()

    actual override fun speak(text: String) {
        statusListeners.values.forEach { it(TTSStatus.IDLE) }
    }

    actual fun speak(text: String, language: Language) {
        statusListeners.values.forEach { it(TTSStatus.IDLE) }
    }

    actual override fun stop() {
    }

    actual override suspend fun getAvailableLanguages(): List<Text2SpeechLanguage> {
        return listOf()
    }

    actual override suspend fun getVoicesForLanguage(language: Text2SpeechLanguage): List<Text2SpeechVoice> {
        return listOf()
    }

    actual override fun setVoice(voice: Text2SpeechVoice) {
    }

    actual override fun openSettings() {
    }

    actual override fun addOnStatusChangeListener(key: Any, listener: (TTSStatus) -> Unit) {
        statusListeners[key] = listener
    }

    actual override fun removeOnStatusChangeListener(key: Any) {
        statusListeners.remove(key)
    }
}
