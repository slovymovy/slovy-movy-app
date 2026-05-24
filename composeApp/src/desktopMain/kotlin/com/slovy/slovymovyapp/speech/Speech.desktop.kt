package com.slovy.slovymovyapp.speech

import com.slovy.slovymovyapp.data.Language

actual class TextToSpeechManager actual constructor(androidContext: Any?) {
    private val statusListeners = mutableMapOf<Any, (TTSStatus) -> Unit>()

    actual fun speak(text: String) {
        statusListeners.values.forEach { it(TTSStatus.IDLE) }
    }

    actual fun speak(text: String, language: Language) {
        statusListeners.values.forEach { it(TTSStatus.IDLE) }
    }

    actual fun stop() {
    }

    actual suspend fun getAvailableLanguages(): List<Text2SpeechLanguage> {
        return listOf()
    }

    actual suspend fun getVoicesForLanguage(language: Text2SpeechLanguage): List<Text2SpeechVoice> {
        return listOf()
    }

    actual fun setVoice(voice: Text2SpeechVoice) {
    }

    actual fun openSettings() {
    }

    actual fun addOnStatusChangeListener(key: Any, listener: (TTSStatus) -> Unit) {
        statusListeners[key] = listener
    }

    actual fun removeOnStatusChangeListener(key: Any) {
        statusListeners.remove(key)
    }
}
