package com.slovy.slovymovyapp.speech


import com.slovy.slovymovyapp.data.Language
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import platform.AVFAudio.*
import platform.Foundation.NSRange
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.darwin.NSObject

actual class TextToSpeechManager actual constructor(androidContext: Any?) {

    private val synthesizer = AVSpeechSynthesizer()
    private val delegate = TTSDelegate()
    private var currentVoice: AVSpeechSynthesisVoice? = null

    private var onWordBoundary: ((IntRange) -> Unit)? = null
    private val statusListeners = mutableMapOf<Any, (TTSStatus) -> Unit>()

    // Generation counter to track speech requests and ignore stale callbacks
    private var speechGeneration: Long = 0

    init {
        synthesizer.delegate = delegate
        delegate.setCallbacks(
            onStart = { statusListeners.values.forEach { it(TTSStatus.SPEAKING) } },
            onSpeechEnded = { generation ->
                // Only deactivate if this callback is for the current generation
                if (generation == speechGeneration) {
                    deactivateAudioSession()
                    statusListeners.values.forEach { it(TTSStatus.IDLE) }
                }
            },
            onWordBoundary = { range -> onWordBoundary?.invoke(range) }
        )
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun activateAudioSession() {
        val audioSession = AVAudioSession.sharedInstance()
        try {
            // Reapply category each time to ensure ducking is configured
            // (another component may have changed the session category)
            audioSession.setCategory(
                AVAudioSessionCategoryPlayback,
                AVAudioSessionCategoryOptionDuckOthers,
                null
            )
            audioSession.setActive(true, null)
        } catch (e: Exception) {
            // Audio session configuration/activation failed
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun deactivateAudioSession() {
        val audioSession = AVAudioSession.sharedInstance()
        try {
            audioSession.setActive(
                false,
                AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation,
                null
            )
        } catch (e: Exception) {
            // Audio session deactivation failed
        }
    }

    actual fun speak(text: String, language: Language) {
        val voices = AVSpeechSynthesisVoice.speechVoices()
        val voiceForLanguage = (voices as List<*>)
            .filterIsInstance<AVSpeechSynthesisVoice>()
            .firstOrNull { it.language.startsWith(language.code) }
        if (voiceForLanguage != null) {
            currentVoice = voiceForLanguage
        }
        speak(text)
    }

    actual fun speak(text: String) {
        // Stop any current speech first - maintains consistent behavior regardless of text/voice state
        synthesizer.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
        if (text.isEmpty()) return
        val voice = currentVoice ?: return
        // Increment generation for the new utterance
        speechGeneration++
        activateAudioSession()
        val utterance = AVSpeechUtterance.speechUtteranceWithString(text)
        //TODO maybe we need to make speed configurable
        utterance.rate = 0.3f
        utterance.pitchMultiplier = 1.0f
        utterance.volume = 1.0f
        utterance.voice = voice

        // Register this utterance with its generation before speaking
        delegate.registerUtterance(utterance, speechGeneration)
        synthesizer.speakUtterance(utterance)
    }

    actual suspend fun getAvailableLanguages(): List<Text2SpeechLanguage> =
        withContext(Dispatchers.IO) {
            val languages = mutableListOf<Text2SpeechLanguage>()
            val availableVoices = AVSpeechSynthesisVoice.speechVoices()

            val availableLanguage = availableVoices
                .map { (it as AVSpeechSynthesisVoice).language }
                .toSet()

            Language.entries.forEach { language ->
                val isAvailable = availableLanguage.any {
                    it.startsWith(language.code) || language.code.startsWith(it)
                }

                languages.add(
                    Text2SpeechLanguage(
                        language = language,
                        isAvailable = isAvailable,
                        missingData = false
                    )
                )
            }

            languages
        }

    actual suspend fun getVoicesForLanguage(language: Text2SpeechLanguage): List<Text2SpeechVoice> =
        withContext(Dispatchers.IO) {
            val allVoices = AVSpeechSynthesisVoice.speechVoices()

            allVoices
                .map { it as AVSpeechSynthesisVoice }
                .filter { voice ->
                    voice.language.startsWith(language.language.code) ||
                            language.language.code.startsWith(voice.language)
                }
                .map { voice ->
                    Text2SpeechVoice(
                        id = voice.identifier,
                        name = voice.name,
                        language = language.language,
                        quality = when (voice.quality) {
                            AVSpeechSynthesisVoiceQualityPremium -> VoiceQuality.BEST
                            AVSpeechSynthesisVoiceQualityEnhanced -> VoiceQuality.GOOD
                            else -> VoiceQuality.MEDIUM
                        },
                        networkConnectionRequired = false // TODO
                    )
                }
        }

    actual fun setVoice(voice: Text2SpeechVoice) {
        val selectedVoice = AVSpeechSynthesisVoice.voiceWithIdentifier(voice.id)
        currentVoice = selectedVoice
    }

    actual fun openSettings() {
        //TODO will work not for all iOS, in general access to device settings can be restricted grom app.
        val settingsUrl = NSURL.URLWithString("App-prefs:root=General&path=ACCESSIBILITY/VOICEOVER/Speech")
        if (settingsUrl != null && UIApplication.sharedApplication.canOpenURL(settingsUrl)) {
            UIApplication.sharedApplication.openURL(settingsUrl, emptyMap<Any?, Any>(), null)
        }
    }

    actual fun stop() {
        // Clear map BEFORE stopping so any sync callback is ignored (generation won't match)
        delegate.clearUtterances()
        // Stop synthesizer to cancel any queued utterances
        synthesizer.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
        // Deactivate session and set idle state
        deactivateAudioSession()
        statusListeners.values.forEach { it(TTSStatus.IDLE) }
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

@OptIn(ExperimentalForeignApi::class)
private class TTSDelegate : NSObject(), AVSpeechSynthesizerDelegateProtocol {

    private var onStart: (() -> Unit)? = null
    private var onSpeechEnded: ((Long) -> Unit)? = null
    private var onWordBoundary: ((IntRange) -> Unit)? = null

    private var started: Boolean = false

    // Map utterance to its generation - allows correct generation lookup in async callbacks
    private val utteranceGenerations = mutableMapOf<AVSpeechUtterance, Long>()
    private var currentGeneration: Long = 0

    fun setCallbacks(
        onStart: () -> Unit,
        onSpeechEnded: (Long) -> Unit,
        onWordBoundary: (IntRange) -> Unit
    ) {
        this.onStart = onStart
        this.onSpeechEnded = onSpeechEnded
        this.onWordBoundary = onWordBoundary
    }

    fun registerUtterance(utterance: AVSpeechUtterance, generation: Long) {
        // Clear old entries to prevent memory leak if callbacks never fire
        // (we use queue-flush behavior, so only one utterance at a time)
        utteranceGenerations.clear()
        currentGeneration = generation
        utteranceGenerations[utterance] = generation
    }

    fun clearUtterances() {
        // Clear map and reset generation so any pending callbacks are ignored
        utteranceGenerations.clear()
        currentGeneration = -1
    }

    private fun getAndRemoveGeneration(utterance: AVSpeechUtterance): Long {
        return utteranceGenerations.remove(utterance) ?: -1
    }

    @ObjCSignatureOverride
    override fun speechSynthesizer(
        synthesizer: AVSpeechSynthesizer,
        didFinishSpeechUtterance: AVSpeechUtterance
    ) {
        started = false
        val utteranceGeneration = getAndRemoveGeneration(didFinishSpeechUtterance)
        // Only notify if this utterance's generation matches current, is valid (>0), and nothing else queued
        if (utteranceGeneration > 0 && utteranceGeneration == currentGeneration && !synthesizer.isSpeaking()) {
            onSpeechEnded?.invoke(utteranceGeneration)
        }
    }

    @ObjCSignatureOverride
    override fun speechSynthesizer(
        synthesizer: AVSpeechSynthesizer,
        didCancelSpeechUtterance: AVSpeechUtterance
    ) {
        started = false
        val utteranceGeneration = getAndRemoveGeneration(didCancelSpeechUtterance)
        // Only notify if this utterance's generation matches current, is valid (>0), and nothing else queued
        if (utteranceGeneration > 0 && utteranceGeneration == currentGeneration && !synthesizer.isSpeaking()) {
            onSpeechEnded?.invoke(utteranceGeneration)
        }
    }

    override fun speechSynthesizer(
        synthesizer: AVSpeechSynthesizer,
        willSpeakRangeOfSpeechString: CValue<NSRange>,
        utterance: AVSpeechUtterance
    ) {
        if (!started) {
            started = true
            onStart?.invoke()
        }
        willSpeakRangeOfSpeechString.useContents {
            val range = location.toInt() until (location + length).toInt()
            onWordBoundary?.invoke(range)
        }
    }
}