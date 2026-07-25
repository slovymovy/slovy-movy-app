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
import com.slovy.slovymovyapp.logging.AppLogger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.*
import kotlin.time.Duration.Companion.seconds

// androidMain
actual class TextToSpeechManager actual constructor(androidContext: Any?) : SpeechPlayer {
    private val context: Context = androidContext as Context
    private lateinit var tts: TextToSpeech

    private val statusListeners = mutableMapOf<Any, (TTSStatus) -> Unit>()

    /** Completed with the engine connection result; replaced on every rebind. */
    private var engineReady = CompletableDeferred<Boolean>()

    /** Set once the user actually reaches system settings, where the default engine can change. */
    private var settingsVisited = false

    init {
        initializeTTS()
    }

    private fun initializeTTS() {
        val pending = engineReady
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                setupUtteranceProgressListener()
            } else {
                AppLogger.warn(TAG, "TTS engine failed to initialize with status $status", null)
            }
            pending.complete(status == TextToSpeech.SUCCESS)
        }
    }

    /**
     * Engine connection is asynchronous, so [tts] reports no voices until it completes.
     * Callers that read voice data await this instead of racing the binding.
     */
    private suspend fun awaitEngineReady(): Boolean {
        val ready = withTimeoutOrNull(ENGINE_INIT_TIMEOUT) { engineReady.await() }
        if (ready == null) {
            AppLogger.warn(TAG, "Timed out waiting for the TTS engine to initialize", null)
        }
        return ready == true
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

        })
    }

    actual override fun speak(text: String) {
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

    actual override suspend fun getAvailableLanguages(): List<Text2SpeechLanguage> = withContext(Dispatchers.IO) {
        rebindEngineIfNeeded()
        if (!awaitEngineReady()) return@withContext emptyList()

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

    actual override suspend fun getVoicesForLanguage(language: Text2SpeechLanguage): List<Text2SpeechVoice> =
        withContext(Dispatchers.IO) {
            rebindEngineIfNeeded()
            if (!awaitEngineReady()) return@withContext emptyList()

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
                    localeTag = voice.locale.toLanguageTag(),
                    quality = when {
                        voice.quality >= QUALITY_VERY_HIGH -> VoiceQuality.BEST
                        voice.quality >= QUALITY_HIGH -> VoiceQuality.GOOD
                        else -> VoiceQuality.MEDIUM
                    },
                    networkConnectionRequired = voice.isNetworkConnectionRequired,
                    enabledByDefault = !voice.isNetworkConnectionRequired
                )
            }
        }

    actual override fun setVoice(voice: Text2SpeechVoice) {
        val tssVoice = tts.voices?.find { it.name == voice.id }
        if (tssVoice == null) {
            throw IllegalStateException("Voice with id ${voice.id} not found")
        }
        tts.voice = tssVoice
    }

    actual override fun openSettings() {
        // The engine picker comes first: the install/check actions only open an engine's
        // voice-data screen, and resolve to a chooser when several engines are installed.
        val actions = listOf(ACTION_TTS_SETTINGS, ACTION_INSTALL_TTS_DATA, ACTION_CHECK_TTS_DATA)
        for (action in actions) {
            try {
                val intent = Intent(action)
                intent.addFlags(FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                settingsVisited = true
                return
            } catch (e: Exception) {
                AppLogger.warn(TAG, "Unable to open TTS settings via $action", e)
            }
        }
    }

    /** Refreshes the binding before any voice query, keeping engine lifecycle out of UI callers. */
    @Synchronized
    private fun rebindEngineIfNeeded() {
        if (!settingsVisited) return
        rebindEngine()
        // Clear only after the replacement binding has been created. If that throws, the next
        // query retries instead of silently considering the settings visit handled.
        settingsVisited = false
    }

    /**
     * The binding is made once and survives a default-engine change made in system settings, so it
     * is dropped and remade rather than requiring a restart.
     */
    private fun rebindEngine() {
        try {
            tts.stop()
            tts.shutdown()
        } catch (e: Exception) {
            AppLogger.warn(TAG, "Unable to shut down the previous TTS engine", e)
        }
        statusListeners.values.toList().forEach { it(TTSStatus.IDLE) }
        engineReady = CompletableDeferred()
        initializeTTS()
    }

    private fun toLocale(lang: Language): Locale {
        val builder = Locale.Builder()
        return builder.setLanguage(lang.code).build()
    }

    actual override fun stop() {
        tts.stop()
        statusListeners.values.toList().forEach { it(TTSStatus.IDLE) }
    }

    actual override fun addOnStatusChangeListener(key: Any, listener: (TTSStatus) -> Unit) {
        statusListeners[key] = listener
    }

    actual override fun removeOnStatusChangeListener(key: Any) {
        statusListeners.remove(key)
    }

    private companion object {
        const val TAG = "TextToSpeechManager"

        /** Not exposed by the SDK; the documented action for the system TTS output screen. */
        const val ACTION_TTS_SETTINGS = "com.android.settings.TTS_SETTINGS"

        val ENGINE_INIT_TIMEOUT = 5.seconds
    }
}
