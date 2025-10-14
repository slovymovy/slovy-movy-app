package com.slovy.slovymovyapp.speech

actual class TextToSpeechManager actual constructor(androidContext: Any?) {
    actual fun speak(text: String) {
    }

    actual fun stop() {
    }

    actual suspend fun getAvailableLanguages(): List<Text2SpeechLanguage> {
        TODO("Not yet implemented")
    }

    actual suspend fun getVoicesForLanguage(language: Text2SpeechLanguage): List<Text2SpeechVoice> {
        TODO("Not yet implemented")
    }

    actual fun setVoice(voice: Text2SpeechVoice) {
    }

    actual fun openSettings() {
    }

    actual fun setOnWordBoundaryListener(listener: (wordRange: IntRange) -> Unit) {
    }

    actual fun setOnStatusChangeListener(listener: (status: TTSStatus) -> Unit) {
    }
}