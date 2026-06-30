package com.slovy.slovymovyapp.speech

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.slovy.slovymovyapp.analytics.Analytics
import com.slovy.slovymovyapp.analytics.AnalyticsEvent
import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.logging.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Drives the per-row speaker on list-style screens (My words, list detail). It mirrors the
 * Word-details playback flow ([com.slovy.slovymovyapp.ui.word.WordDetailViewModel.playWord]) but is
 * keyed by `senseId` so any number of rows can share one player while only one plays at a time.
 *
 * Rows live in their own per-row [Language] (My words can mix languages), so voices are loaded and
 * cached per language. The low-quality-voice gate still routes through [VoiceSetupBottomSheet] via
 * [voiceSetupLanguage]; the screen renders the sheet from that state.
 */
class RowAudioController(
    private val ttsManager: TextToSpeechManager,
    private val voiceFilterHelper: VoiceFilterHelper,
    private val scope: CoroutineScope,
    private val analyticsSource: String,
) {
    /** Sense whose audio is loading (spinner). Null when nothing is preparing. */
    var preparingSenseId by mutableStateOf<String?>(null)
        private set

    /** Sense currently speaking (stop glyph). Null when nothing is playing. */
    var playingSenseId by mutableStateOf<String?>(null)
        private set

    /** Non-null while the first-run voice setup sheet should be shown for this language. */
    var voiceSetupLanguage by mutableStateOf<Language?>(null)
        private set

    // Platform language lookup is stable, so it's cached. The enabled-voice filter is NOT cached:
    // this controller is as long-lived as the (remembered) view model, so voices are re-resolved on
    // every play to pick up enable/disable changes made in Settings.
    private val targetLanguageByLanguage = mutableMapOf<Language, Text2SpeechLanguage>()
    private val voiceIndexByLanguage = mutableMapOf<Language, Int>()
    private var pendingPlay: PendingPlay? = null
    private var listenerAttached = false

    // Monotonic token bumped on every play/stop request. A voice-load coroutine captures the token
    // it started with and bails if a newer request superseded it, so a slow first-time load for row
    // A can't speak or clobber row B's state after the user re-taps (P1).
    private var requestToken: Long = 0L

    // The sense whose utterance *this* controller just handed to the shared TextToSpeechManager. The
    // status listener only adopts SPEAKING/IDLE while this is set, so audio started by other screens
    // sharing the manager (word detail, study, settings) can't flip a row's state (P2).
    private var pendingSpeakSenseId: String? = null

    private data class PendingPlay(val senseId: String, val lemma: String, val language: Language)

    /** Play [lemma] for [senseId], or stop if that row is already preparing/playing. */
    fun toggle(senseId: String, lemma: String, language: Language) {
        if (playingSenseId == senseId || preparingSenseId == senseId) {
            stop()
        } else {
            play(senseId, lemma, language)
        }
    }

    private fun play(senseId: String, lemma: String, language: Language) {
        ensureListener()
        Analytics.logEvent(
            AnalyticsEvent.WORD_PLAY_CLICK,
            mapOf("lang" to language.code, "source" to analyticsSource),
        )
        // A new utterance replaces any in-flight one; no explicit stop (mirrors Word details and
        // avoids the IDLE callback racing the preparing state we set below).
        val token = ++requestToken
        pendingSpeakSenseId = null
        playingSenseId = null
        preparingSenseId = senseId
        scope.launch {
            val voices = loadVoices(language)
            if (token != requestToken) return@launch
            if (voices.isEmpty()) {
                preparingSenseId = null
                return@launch
            }
            val hasHighQualityVoice = voices.any { it.quality != VoiceQuality.MEDIUM }
            if (!hasHighQualityVoice && !voiceFilterHelper.isVoiceSetupShown(language)) {
                pendingPlay = PendingPlay(senseId, lemma, language)
                preparingSenseId = null
                voiceSetupLanguage = language
                return@launch
            }
            doPlay(senseId, lemma, language, voices, token)
        }
    }

    private fun doPlay(
        senseId: String,
        lemma: String,
        language: Language,
        voices: List<Text2SpeechVoice>,
        token: Long,
    ) {
        if (token != requestToken) return
        try {
            // Rotate to the next voice for this language, matching Word details.
            val nextIndex = ((voiceIndexByLanguage[language] ?: -1) + 1).mod(voices.size)
            voiceIndexByLanguage[language] = nextIndex
            preparingSenseId = senseId
            playingSenseId = null
            pendingSpeakSenseId = senseId
            ttsManager.setVoice(voices[nextIndex])
            ttsManager.speak(lemma)
        } catch (e: Exception) {
            AppLogger.warn(TAG, "Unable to play row audio for ${language.code}", e)
            Analytics.logEvent(
                AnalyticsEvent.TTS_PLAY_FAILED,
                mapOf(
                    "lang" to language.code,
                    "source" to analyticsSource,
                    "error" to (e.message ?: e::class.simpleName ?: "unknown"),
                ),
            )
            pendingSpeakSenseId = null
            preparingSenseId = null
        }
    }

    fun stop() {
        Analytics.logEvent(AnalyticsEvent.WORD_STOP_PLAY_CLICK)
        // Invalidate any in-flight voice load so it can't speak after this stop.
        ++requestToken
        pendingSpeakSenseId = null
        ttsManager.stop()
        preparingSenseId = null
        playingSenseId = null
    }

    fun dismissVoiceSetup() {
        markSetupShownAndClear()
    }

    fun dismissVoiceSetupAndPlay() {
        val pending = pendingPlay
        markSetupShownAndClear()
        if (pending != null) {
            val token = ++requestToken
            pendingSpeakSenseId = null
            playingSenseId = null
            preparingSenseId = pending.senseId
            scope.launch {
                val voices = loadVoices(pending.language)
                if (token != requestToken) return@launch
                if (voices.isEmpty()) {
                    preparingSenseId = null
                    return@launch
                }
                doPlay(pending.senseId, pending.lemma, pending.language, voices, token)
            }
        }
    }

    fun openVoiceSettings() {
        markSetupShownAndClear()
        ttsManager.openSettings()
    }

    private fun markSetupShownAndClear() {
        val language = voiceSetupLanguage
        voiceSetupLanguage = null
        pendingPlay = null
        if (language != null) {
            scope.launch { voiceFilterHelper.markVoiceSetupShown(language) }
        }
    }

    private suspend fun loadVoices(language: Language): List<Text2SpeechVoice> {
        return try {
            val target = targetLanguageByLanguage[language]
                ?: ttsManager.getAvailableLanguages().firstOrNull { it.language == language }
                    ?.also { targetLanguageByLanguage[language] = it }
                ?: return emptyList()
            // Re-resolve enabled voices each play so Settings changes are reflected immediately.
            val voices = voiceFilterHelper.loadEnabledVoices(ttsManager, target)
            if (voices.isNotEmpty() && voiceIndexByLanguage[language] == null) {
                voiceIndexByLanguage[language] = voices.indices.random()
            }
            voices
        } catch (e: Exception) {
            AppLogger.warn(TAG, "Unable to load row audio voices for ${language.code}", e)
            emptyList()
        }
    }

    private fun ensureListener() {
        if (listenerAttached) return
        listenerAttached = true
        ttsManager.addOnStatusChangeListener(this) { status ->
            when (status) {
                TTSStatus.SPEAKING -> {
                    // Only adopt SPEAKING for the utterance we just started; events from other
                    // screens sharing this manager arrive while pendingSpeakSenseId is null.
                    val target = pendingSpeakSenseId
                    if (target != null) {
                        pendingSpeakSenseId = null
                        preparingSenseId = null
                        playingSenseId = target
                    } else if (playingSenseId != null) {
                        // A SPEAKING we didn't start while a row shows "playing" means another owner
                        // of the shared manager preempted our utterance. Android QUEUE_FLUSH / iOS
                        // stop-then-speak swallow the IDLE for the flushed utterance, so release our
                        // state here — otherwise the row stays stuck on the stop icon and a tap
                        // would stop the other feature's audio.
                        playingSenseId = null
                    }
                }

                TTSStatus.IDLE -> {
                    // Clear only once our utterance is in flight (speaking or handed off). While a
                    // row is still loading voices, an unrelated screen's IDLE must not kill the
                    // spinner.
                    if (playingSenseId != null || pendingSpeakSenseId != null) {
                        pendingSpeakSenseId = null
                        preparingSenseId = null
                        playingSenseId = null
                    }
                }
            }
        }
    }

    fun dispose() {
        if (listenerAttached) {
            ttsManager.removeOnStatusChangeListener(this)
            listenerAttached = false
        }
        ++requestToken
        ttsManager.stop()
        pendingSpeakSenseId = null
        preparingSenseId = null
        playingSenseId = null
        voiceSetupLanguage = null
        pendingPlay = null
    }

    private companion object {
        const val TAG = "RowAudioController"
    }
}
