package com.slovy.slovymovyapp.speech

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.slovy.slovymovyapp.analytics.Analytics
import com.slovy.slovymovyapp.analytics.AnalyticsEvent
import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.logging.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Drives the per-row speaker on list-style screens (My words, list detail). It mirrors the
 * Word-details playback flow ([com.slovy.slovymovyapp.ui.word.WordDetailViewModel.playWord]) but is
 * keyed by `senseId` so any number of rows can share one player while only one plays at a time.
 *
 * Rows live in their own per-row [Language] (My words can mix languages), so voices are loaded per
 * language via [RotatingVoiceSelector] on every play — enable/disable changes made in Settings
 * apply immediately. The low-quality-voice gate routes through the voice setup sheet, surfaced to
 * screens as [RowAudioUiState.voiceSetupLanguage].
 */
class RowAudioController(
    private val speechPlayer: SpeechPlayer,
    private val voiceFilterHelper: VoiceFilterHelper,
    private val scope: CoroutineScope,
    private val analyticsSource: String,
) {
    /** Snapshot for the hosting screen; derived from [playback] plus language availability. */
    var uiState by mutableStateOf(RowAudioUiState())
        private set

    private val voiceSelector = RotatingVoiceSelector(speechPlayer, voiceFilterHelper)

    private data class PlayRequest(val senseId: String, val lemma: String, val language: Language)

    /**
     * The playback lifecycle of the single active request. Only [Starting] and [Playing] mean an
     * utterance of ours is in (or entering) the shared [SpeechPlayer]; the status listener uses
     * that to ignore audio started by other screens sharing the player (word detail, study,
     * settings) so it can't flip a row's state.
     */
    private sealed interface Playback {
        data object Idle : Playback

        /** Voices are loading for [request]; the row shows a spinner. */
        data class LoadingVoices(val request: PlayRequest) : Playback

        /** The voice setup sheet is up; [request] resumes if the user picks "later". */
        data class AwaitingVoiceSetup(val request: PlayRequest) : Playback

        /** Handed to the engine, waiting for its SPEAKING callback; the row still shows a spinner. */
        data class Starting(val request: PlayRequest) : Playback

        data class Playing(val senseId: String) : Playback
    }

    private var playback: Playback = Playback.Idle
        set(value) {
            field = value
            uiState = uiState.copy(
                playingSenseId = (value as? Playback.Playing)?.senseId,
                preparingSenseId = when (value) {
                    is Playback.LoadingVoices -> value.request.senseId
                    is Playback.Starting -> value.request.senseId
                    else -> null
                },
                voiceSetupLanguage = (value as? Playback.AwaitingVoiceSetup)?.request?.language,
            )
        }

    // Monotonic token bumped on every play/stop request. A voice-load coroutine captures the token
    // it started with and bails if a newer request superseded it, so a slow first-time load for row
    // A can't speak or clobber row B's state after the user re-taps.
    private var requestToken = 0L
    private var availabilityLoadJob: Job? = null
    private var listenerAttached = false

    /**
     * Re-probes which languages are speakable. Call when a host screen becomes visible or resumes
     * (not at controller construction, so the shared TTS engine isn't initialised at app start for
     * the app-lifetime Favorites controller). Refreshing on every resume picks up whatever the user
     * changed while away in Settings — voices installed or disabled, or a different TTS engine,
     * which [SpeechPlayer] handles behind its voice queries. A language counts as playable only when
     * the engine supports it AND at least one enabled voice remains, so rows never show a speaker
     * that cannot produce sound.
     */
    fun refreshAvailability() {
        // A newer refresh supersedes the running probe: a slow one must not outlive the state it
        // was probing, nor publish over the result of the refresh that replaced it.
        availabilityLoadJob?.cancel()
        availabilityLoadJob = scope.launch {
            val languages = try {
                speechPlayer.getAvailableLanguages()
                    .filter { it.isAvailable }
                    .filter { voiceFilterHelper.hasPlayableVoice(speechPlayer, it) }
                    .map { it.language }
                    .toSet()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.warn(TAG, "Unable to load TTS language availability", e)
                emptySet()
            }
            if (!isActive) return@launch
            uiState = uiState.copy(availableLanguages = languages)
        }
    }

    /** Play [lemma] for [senseId], or stop if that row is already preparing/playing. */
    fun toggle(senseId: String, lemma: String, language: Language) {
        if (uiState.phaseFor(senseId) != RowAudioPhase.IDLE) {
            stop()
        } else {
            play(senseId, lemma, language)
        }
    }

    fun stop() {
        Analytics.logEvent(AnalyticsEvent.WORD_STOP_PLAY_CLICK)
        // Invalidate any in-flight voice load so it can't speak after this stop.
        ++requestToken
        speechPlayer.stop()
        playback = Playback.Idle
    }

    fun dismissVoiceSetup() {
        consumeVoiceSetup()
    }

    fun dismissVoiceSetupAndPlay() {
        val request = consumeVoiceSetup() ?: return
        startPlayback(request, gateOnVoiceSetup = false)
    }

    fun openVoiceSettings() {
        consumeVoiceSetup()
        speechPlayer.openSettings()
    }

    fun dispose() {
        if (listenerAttached) {
            speechPlayer.removeOnStatusChangeListener(this)
            listenerAttached = false
        }
        ++requestToken
        speechPlayer.stop()
        playback = Playback.Idle
    }

    private fun play(senseId: String, lemma: String, language: Language) {
        ensureListener()
        Analytics.logEvent(
            AnalyticsEvent.WORD_PLAY_CLICK,
            mapOf("lang" to language.code, "source" to analyticsSource),
        )
        startPlayback(PlayRequest(senseId, lemma, language), gateOnVoiceSetup = true)
    }

    private fun startPlayback(request: PlayRequest, gateOnVoiceSetup: Boolean) {
        val token = ++requestToken
        // Silence any current utterance before the async voice path: if this request stalls (slow
        // load, no voices, setup sheet) the previous row must not keep playing with no visible
        // stop control. The engine's IDLE for the stopped utterance lands while we are in
        // LoadingVoices, which the status listener ignores, so it cannot race the spinner.
        speechPlayer.stop()
        playback = Playback.LoadingVoices(request)
        scope.launch {
            // Voices are re-resolved on every play to pick up Settings changes.
            val voices = voiceSelector.loadVoices(request.language)
            if (token != requestToken) return@launch
            if (voices.isEmpty()) {
                playback = Playback.Idle
                return@launch
            }
            if (gateOnVoiceSetup && voiceFilterHelper.needsVoiceSetupPrompt(request.language, voices)) {
                // The gate check suspends; re-check the token so a stale load can't raise the sheet.
                if (token != requestToken) return@launch
                playback = Playback.AwaitingVoiceSetup(request)
                return@launch
            }
            if (token != requestToken) return@launch
            speak(request, voices)
        }
    }

    private fun speak(request: PlayRequest, voices: List<Text2SpeechVoice>) {
        try {
            playback = Playback.Starting(request)
            speechPlayer.setVoice(voiceSelector.nextVoice(request.language, voices))
            speechPlayer.speak(request.lemma)
        } catch (e: Exception) {
            AppLogger.warn(TAG, "Unable to play row audio for ${request.language.code}", e)
            Analytics.logEvent(
                AnalyticsEvent.TTS_PLAY_FAILED,
                mapOf(
                    "lang" to request.language.code,
                    "source" to analyticsSource,
                    "error" to (e.message ?: e::class.simpleName ?: "unknown"),
                ),
            )
            playback = Playback.Idle
        }
    }

    /** Marks the sheet as shown and closes it, returning the request it interrupted (if any). */
    private fun consumeVoiceSetup(): PlayRequest? {
        val awaiting = playback as? Playback.AwaitingVoiceSetup ?: return null
        playback = Playback.Idle
        scope.launch { voiceFilterHelper.markVoiceSetupShown(awaiting.request.language) }
        return awaiting.request
    }

    private fun ensureListener() {
        if (listenerAttached) return
        listenerAttached = true
        speechPlayer.addOnStatusChangeListener(this) { status ->
            when (status) {
                TTSStatus.SPEAKING -> when (val current = playback) {
                    is Playback.Starting -> playback = Playback.Playing(current.request.senseId)

                    // A SPEAKING we didn't start while a row shows "playing" means another owner of
                    // the shared player preempted our utterance. Android QUEUE_FLUSH / iOS
                    // stop-then-speak swallow the IDLE for the flushed utterance, so release our
                    // state here — otherwise the row stays stuck on the stop icon and a tap would
                    // stop the other feature's audio.
                    is Playback.Playing -> playback = Playback.Idle

                    // Not ours (another screen's audio) — nothing of ours is in the engine.
                    else -> Unit
                }

                TTSStatus.IDLE -> when (playback) {
                    is Playback.Starting, is Playback.Playing -> playback = Playback.Idle

                    // While a row is still loading voices, an unrelated screen's IDLE must not kill
                    // the spinner.
                    else -> Unit
                }
            }
        }
    }

    private companion object {
        const val TAG = "RowAudioController"
    }
}
