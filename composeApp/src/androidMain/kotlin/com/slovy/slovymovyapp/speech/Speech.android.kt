package com.slovy.slovymovyapp.speech

import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeech.Engine.ACTION_CHECK_TTS_DATA
import android.speech.tts.TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice.QUALITY_HIGH
import android.speech.tts.Voice.QUALITY_VERY_HIGH
import com.slovy.slovymovyapp.data.Language
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

// androidMain
actual class TextToSpeechManager actual constructor(androidContext: Any?) {
    private val context: Context = androidContext as Context
    private lateinit var tts: TextToSpeech

    private var onWordBoundary: ((IntRange) -> Unit)? = null
    private val statusListeners = mutableMapOf<Any, (TTSStatus) -> Unit>()

    init {
        initializeTTS()
    }

    private fun initializeTTS() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                setupUtteranceProgressListener()
            }
        }
    }

    private fun setupUtteranceProgressListener() {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                statusListeners.values.toList().forEach { it(TTSStatus.SPEAKING) }
            }

            override fun onDone(utteranceId: String?) {
                statusListeners.values.toList().forEach { it(TTSStatus.IDLE) }
            }

            @Suppress("OVERRIDE_DEPRECATION")
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                statusListeners.values.toList().forEach { it(TTSStatus.IDLE) }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                statusListeners.values.toList().forEach { it(TTSStatus.IDLE) }
            }

            override fun onRangeStart(
                utteranceId: String?,
                start: Int,
                end: Int,
                frame: Int
            ) {
                onWordBoundary?.invoke(start until end)
            }
        })
    }

    actual fun speak(text: String) {
        val id = "tts_${System.currentTimeMillis()}"
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, id)
        }
        val result = tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, id)
        if (result == TextToSpeech.ERROR) {
            statusListeners.values.toList().forEach { it(TTSStatus.IDLE) }
        }
    }

    actual fun speak(text: String, language: Language) {
        tts.setLanguage(toLocale(language))
        speak(text)
    }

    actual suspend fun getAvailableLanguages(): List<Text2SpeechLanguage> = withContext(Dispatchers.IO) {
        val languages = mutableListOf<Text2SpeechLanguage>()


        Language.entries.forEach { lang ->
            val locale = toLocale(lang)
            val availability = tts.isLanguageAvailable(locale)

            val isAvailable = availability in listOf(
                TextToSpeech.LANG_AVAILABLE,
                TextToSpeech.LANG_COUNTRY_AVAILABLE,
                TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE
            )

            languages.add(
                Text2SpeechLanguage(
                    language = lang,
                    isAvailable = isAvailable,
                    missingData = availability == TextToSpeech.LANG_MISSING_DATA
                )
            )
        }

        languages
    }

    actual suspend fun getVoicesForLanguage(language: Text2SpeechLanguage): List<Text2SpeechVoice> =
        withContext(Dispatchers.IO) {
            val locale = toLocale(language.language)
            val voices = tts.voices?.filter { voice ->
                voice.locale.language == locale.language &&
                        (locale.country.isEmpty() || voice.locale.country == locale.country)
            } ?: emptyList()

            voices.map { voice ->
                Text2SpeechVoice(
                    id = voice.name,
                    name = if (voice.name.contains("#")) voice.name.split("#").lastOrNull() else null,
                    language = language.language,
                    quality = when {
                        voice.quality >= QUALITY_VERY_HIGH -> VoiceQuality.BEST
                        voice.quality >= QUALITY_HIGH -> VoiceQuality.GOOD
                        else -> VoiceQuality.MEDIUM
                    },
                    networkConnectionRequired = voice.isNetworkConnectionRequired
                )
            }
        }

    actual fun setVoice(voice: Text2SpeechVoice) {
        val tssVoice = tts.voices?.find { it.name == voice.id }
        if (tssVoice == null) {
            throw IllegalStateException("Voice with id ${voice.id} not found")
        }
        tts.voice = tssVoice
    }

    actual fun openSettings() {
        try {
            val intent = Intent(ACTION_INSTALL_TTS_DATA)
            intent.addFlags(FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (_: Exception) {
            val intent = Intent(ACTION_CHECK_TTS_DATA)
            intent.addFlags(FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    private fun toLocale(lang: Language): Locale {
        val builder = Locale.Builder()
        return builder.setLanguage(lang.code).build()
    }

    actual fun stop() {
        tts.stop()
        statusListeners.values.toList().forEach { it(TTSStatus.IDLE) }
    }

    actual fun setOnWordBoundaryListener(listener: (wordRange: IntRange) -> Unit) {
        onWordBoundary = listener
    }

    actual fun addOnStatusChangeListener(key: Any, listener: (TTSStatus) -> Unit) {
        statusListeners[key] = listener
    }

    actual fun removeOnStatusChangeListener(key: Any) {
        statusListeners.remove(key)
    }
}