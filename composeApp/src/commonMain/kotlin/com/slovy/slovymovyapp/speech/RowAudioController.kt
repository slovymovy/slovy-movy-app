package com.slovy.slovymovyapp.speech

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.slovy.slovymovyapp.analytics.Analytics
import com.slovy.slovymovyapp.analytics.AnalyticsEvent
import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.util.HtmlTagParser
import com.slovy.slovymovyapp.logging.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Drives the inline speakers on list-style screens (My words, list detail): a row's lemma and the
 * source sentence of each of its examples. It mirrors the Word-details playback flow
 * ([com.slovy.slovymovyapp.ui.word.WordDetailViewModel.playWord]) but is keyed by [RowAudioKeys] so
 * any number of speakers can share one player while only one plays at a time.
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

    /**
     * One play attempt. [key] identifies the speaker that owns it (so the right control shows the
     * spinner/stop glyph) and [text] is what the engine says; for a lemma the two carry the same
     * word, for an example they differ entirely.
     */
    private data class PlayRequest(val key: String, val text: String, val language: Language)

    /** What a request speaks. Reported as the `kind` analytics parameter. */
    private enum class Kind(val analyticsValue: String) {
        LEMMA("lemma"),
        EXAMPLE("example"),
    }

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

        data class Playing(val key: String) : Playback
    }

    private var playback: Playback = Playback.Idle
        set(value) {
            field = value
            uiState = uiState.copy(
                playingKey = (value as? Playback.Playing)?.key,
                preparingKey = when (value) {
                    is Playback.LoadingVoices -> value.request.key
                    is Playback.Starting -> value.request.key
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

    /** Play [lemma] for [senseId], or stop if that row's lemma is already preparing/playing. */
    fun toggleLemma(senseId: String, lemma: String, language: Language) {
        toggleKey(RowAudioKeys.lemma(senseId), lemma, language, Kind.LEMMA)
    }

    /**
     * Play the source sentence [text] of the example at [index] of [senseId], or stop if that
     * example is already preparing/playing. Tapping it while the row's lemma (or another example)
     * plays switches to this one, like switching rows does.
     */
    fun toggleExample(senseId: String, index: Int, text: String, language: Language) {
        toggleKey(RowAudioKeys.example(senseId, index), text, language, Kind.EXAMPLE)
    }

    /**
     * Play the screen's headline [lemma] (word details' hero speaker), or stop if it is active.
     * Sharing one controller with that screen's example speakers is what makes them mutually
     * exclusive: playing an example stops the word, and playing the word stops the example.
     */
    fun toggleWord(lemma: String, language: Language) {
        toggleKey(RowAudioKeys.word(lemma), lemma, language, Kind.LEMMA)
    }

    private fun toggleKey(key: String, text: String, language: Language, kind: Kind) {
        if (uiState.phaseFor(key) != RowAudioPhase.IDLE) {
            stop()
        } else {
            play(key, text, language, kind)
        }
    }

    /** Stops because the user tapped the active speaker. */
    fun stop() {
        Analytics.logEvent(AnalyticsEvent.WORD_STOP_PLAY_CLICK)
        silence()
    }

    /**
     * Stops because the hosting screen stopped being visible (tab switch, navigation, app
     * backgrounded). An example sentence runs for seconds, so leaving mid-utterance is easy and
     * audio outliving its screen — with no visible stop control anywhere — reads as a bug.
     *
     * Deliberately not [stop]: that reports a stop *click*, and a lifecycle stop is not one.
     */
    fun stopForPause() {
        // Nothing of ours is in the engine while the setup sheet is up, so there is nothing to
        // silence — and dropping the request it gates would make the user's tap vanish when they
        // come back. Leave that state alone.
        if (playback is Playback.AwaitingVoiceSetup) return
        silence()
    }

    /** Silences the engine and drops our playback state, without reporting a user action. */
    private fun silence() {
        // Invalidate any in-flight voice load so it can't speak after this stop.
        ++requestToken
        speechPlayer.stop()
        playback = Playback.Idle
    }

    /**
     * Stops if an example of [senseId] is the active speaker. Call when that sense's examples are
     * about to leave the screen — its card collapsing — because their control goes with them, and
     * audio still sounding with no stop control anywhere is the defect [stopForPause] exists to
     * prevent. The row's lemma speaker stays on screen when collapsed, so it is left alone.
     */
    fun stopExamplesOf(senseId: String) {
        val key = uiState.playingKey ?: uiState.preparingKey ?: return
        if (RowAudioKeys.isExampleOf(key, senseId)) silence()
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
        silence()
    }

    private fun play(key: String, text: String, language: Language, kind: Kind) {
        ensureListener()
        Analytics.logEvent(
            AnalyticsEvent.WORD_PLAY_CLICK,
            mapOf("lang" to language.code, "source" to analyticsSource, "kind" to kind.analyticsValue),
        )
        // Example sentences carry <w> highlight markup, which the engine would read out literally.
        // Stripping here rather than at the call sites means no caller can forget; a lemma has no
        // markup, so this is a no-op for it.
        val spoken = HtmlTagParser.plainText(text)
        startPlayback(PlayRequest(key, spoken, language), gateOnVoiceSetup = true)
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
            speechPlayer.speak(request.text)
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
                    is Playback.Starting -> playback = Playback.Playing(current.request.key)

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
