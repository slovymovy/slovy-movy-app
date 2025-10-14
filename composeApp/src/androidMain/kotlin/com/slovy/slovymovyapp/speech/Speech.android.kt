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
import com.slovy.slovymovyapp.data.remote.codeToLanguageName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

// androidMain
actual class TextToSpeechManager actual constructor(androidContext: Any?) {
    private val context: Context = androidContext as Context
    private lateinit var tts: TextToSpeech

    private var onWordBoundary: ((IntRange) -> Unit)? = null
    private var onStatusChange: ((TTSStatus) -> Unit)? = null

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
                onStatusChange?.invoke(TTSStatus.SPEAKING)
            }

            override fun onDone(utteranceId: String?) {
                onStatusChange?.invoke(TTSStatus.IDLE)
            }

            @Suppress("OVERRIDE_DEPRECATION")
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                onStatusChange?.invoke(TTSStatus.IDLE)
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                onStatusChange?.invoke(TTSStatus.IDLE)
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
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, id)
    }

    actual suspend fun getAvailableLanguages(): List<Text2SpeechLanguage> = withContext(Dispatchers.IO) {
        val languages = mutableListOf<Text2SpeechLanguage>()


        codeToLanguageName.forEach { (code, _) ->
            val locale = parseLocale(code)
            val availability = tts.isLanguageAvailable(locale)

            val isAvailable = availability in listOf(
                TextToSpeech.LANG_AVAILABLE,
                TextToSpeech.LANG_COUNTRY_AVAILABLE,
                TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE
            )

            languages.add(
                Text2SpeechLanguage(
                    code = code,
                    isAvailable = isAvailable,
                    missingData = availability == TextToSpeech.LANG_MISSING_DATA
                )
            )
        }

        languages
    }

    actual suspend fun getVoicesForLanguage(language: Text2SpeechLanguage): List<Text2SpeechVoice> =
        withContext(Dispatchers.IO) {

            val locale = parseLocale(language.code)
            val voices = tts.voices?.filter { voice ->
                voice.locale.language == locale.language &&
                        (locale.country.isEmpty() || voice.locale.country == locale.country)
            } ?: emptyList()

            voices.map { voice ->
                Text2SpeechVoice(
                    id = voice.name,
                    name = voice.name.split("#").lastOrNull(),
                    langCode = language.code,
                    quality = when {
                        voice.quality >= QUALITY_VERY_HIGH -> VoiceQuality.BEST
                        voice.quality >= QUALITY_HIGH -> VoiceQuality.GOOD
                        else -> VoiceQuality.MEDIUM
                    },
                    networkConnectionRequired = !voice.isNetworkConnectionRequired
                )
            }
        }

    actual fun setVoice(voice: Text2SpeechVoice) {
        val tssVoice = tts?.voices?.find { it.name == voice.id }
        if (tssVoice == null) {
            throw IllegalStateException("Voice with id ${voice.id} not found")
        }
        tts?.voice = tssVoice
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

    private fun parseLocale(code: String): Locale {
        val builder = Locale.Builder()
        return builder.setLanguage(code).build()
    }

    actual fun stop() {
        tts.stop()
        onStatusChange?.invoke(TTSStatus.IDLE)
    }

    actual fun setOnWordBoundaryListener(listener: (wordRange: IntRange) -> Unit) {
        onWordBoundary = listener
    }

    actual fun setOnStatusChangeListener(listener: (status: TTSStatus) -> Unit) {
        onStatusChange = listener
    }
}