package com.slovy.slovymovyapp.speech

import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeech.Engine.ACTION_CHECK_TTS_DATA
import android.speech.tts.TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA
import android.speech.tts.TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED
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

    private val statusListeners = mutableMapOf<Any, (TTSStatus) -> Unit>()

    private val progressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            notifyStatus(TTSStatus.SPEAKING)
        }

        override fun onDone(utteranceId: String?) {
            notifyStatus(TTSStatus.IDLE)
        }

        @Suppress("OVERRIDE_DEPRECATION")
        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            notifyStatus(TTSStatus.IDLE)
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            // The engine reports a refused utterance only here, so the failing voice has to be named
            // for a report like "this voice is silent" to be actionable.
            AppLogger.warn(
                TAG,
                "TTS utterance failed with error $errorCode on voice ${currentVoiceName()}",
                null
            )
            notifyStatus(TTSStatus.IDLE)
        }
    }

    /** Completed with the engine connection result; replaced on every rebind. */
    private var engineReady = CompletableDeferred<Boolean>()

    private var tts: TextToSpeech = bindEngine()

    /**
     * Engine package [tts] is bound to. `TextToSpeech` picks the system default when it connects and
     * keeps that engine for its whole life, so this is what a later default change is compared to.
     */
    private var boundEngine: String? = tts.defaultEngine

    private fun bindEngine(): TextToSpeech {
        val pending = CompletableDeferred<Boolean>()
        engineReady = pending
        val engine = TextToSpeech(context) { status ->
            if (status != TextToSpeech.SUCCESS) {
                AppLogger.warn(TAG, "TTS engine failed to initialize with status $status", null)
            }
            pending.complete(status == TextToSpeech.SUCCESS)
        }
        // Only stores the listener, so it does not have to wait for the connection.
        engine.setOnUtteranceProgressListener(progressListener)
        return engine
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

    private fun notifyStatus(status: TTSStatus) {
        statusListeners.values.toList().forEach { it(status) }
    }

    actual override fun speak(text: String) {
        val id = "tts_${System.currentTimeMillis()}"
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, id)
        }
        val result = tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, id)
        if (result == TextToSpeech.ERROR) {
            notifyStatus(TTSStatus.IDLE)
        }
    }

    actual fun speak(text: String, language: Language) {
        tts.setLanguage(toLocale(language))
        speak(text)
    }

    actual override suspend fun getAvailableLanguages(): List<Text2SpeechLanguage> = withContext(Dispatchers.IO) {
        rebindIfDefaultEngineChanged()
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
            rebindIfDefaultEngineChanged()
            if (!awaitEngineReady()) return@withContext emptyList()

            val locale = toLocale(language.language)
            val matching = tts.voices?.filter { voice ->
                voice.locale.language == locale.language &&
                        (locale.country.isEmpty() || voice.locale.country == locale.country)
            } ?: emptyList()

            // Engines advertise voices whose data was never downloaded (Google lists a large set of
            // `<locale>-x-<variant>-local` names this way). They can be selected, but every utterance
            // fails with ERROR_NOT_INSTALLED_YET and the user just hears silence, so they are not
            // offered. Dropping them all would leave a language mute; an engine that flags its whole
            // set is not trusted and everything is kept.
            val installed = matching.filterNot { it.features?.contains(KEY_FEATURE_NOT_INSTALLED) == true }
            val voices = if (installed.isEmpty()) matching else installed
            if (voices.size != matching.size) {
                AppLogger.info(
                    TAG,
                    "Hiding ${matching.size - voices.size} not-installed ${language.language.code} voices",
                    null
                )
            }

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
        // Rejected here the engine keeps the previous voice, which would silently play the wrong one.
        if (tts.setVoice(tssVoice) == TextToSpeech.ERROR) {
            throw IllegalStateException("Engine rejected voice ${voice.id}")
        }
    }

    private fun currentVoiceName(): String =
        runCatching { tts.voice?.name }.getOrNull() ?: "unknown"

    actual override fun openSettings() {
        // The engine picker comes first: the install/check actions only open an engine's
        // voice-data screen, and resolve to a chooser when several engines are installed.
        val actions = listOf(ACTION_TTS_SETTINGS, ACTION_INSTALL_TTS_DATA, ACTION_CHECK_TTS_DATA)
        for (action in actions) {
            try {
                val intent = Intent(action)
                intent.addFlags(FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return
            } catch (e: Exception) {
                AppLogger.warn(TAG, "Unable to open TTS settings via $action", e)
            }
        }
    }

    /**
     * A [TextToSpeech] keeps the engine it connected to, so a default-engine change in system
     * settings would otherwise be ignored until the app restarts. Comparing the current default
     * against [boundEngine] before every voice query keeps that entirely inside this class: callers
     * just query voices, and a change made anywhere — our settings link, the Settings app, another
     * app — is picked up the same way.
     */
    @Synchronized
    private fun rebindIfDefaultEngineChanged() {
        val defaultEngine = tts.defaultEngine ?: return
        if (defaultEngine == boundEngine) return

        AppLogger.info(TAG, "Default TTS engine changed from $boundEngine to $defaultEngine", null)
        val previous = tts
        // An utterance dying with the old engine gets no callback, so it has to be reported. Nothing
        // speaking means nothing to report: the rebind runs inside a voice query, and a caller that
        // is resolving voices for a play it has not started yet must not be told one just finished.
        val interruptedSpeech = previous.isSpeaking
        // Bind the replacement first, so no concurrent query can reach a shut-down engine.
        tts = bindEngine()
        boundEngine = defaultEngine
        try {
            previous.stop()
            previous.shutdown()
        } catch (e: Exception) {
            AppLogger.warn(TAG, "Unable to shut down the previous TTS engine", e)
        }
        if (interruptedSpeech) notifyStatus(TTSStatus.IDLE)
    }

    // Parses the full tag rather than seeding a builder with it: Locale.Builder.setLanguage rejects
    // anything beyond a bare language subtag, so a code carrying a script (zh-Hans) would throw here
    // and abort the Language.entries loop in getAvailableLanguages, leaving every language voiceless.
    private fun toLocale(lang: Language): Locale = Locale.forLanguageTag(lang.code)

    actual override fun stop() {
        tts.stop()
        notifyStatus(TTSStatus.IDLE)
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
