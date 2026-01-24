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
    private var currentUtterance: AVSpeechUtterance? = null
    private var currentVoice: AVSpeechSynthesisVoice? = null

    private var onWordBoundary: ((IntRange) -> Unit)? = null
    private var onStatusChange: ((TTSStatus) -> Unit)? = null

    // Generation counter to track speech requests and ignore stale callbacks
    private var speechGeneration: Long = 0

    init {
        synthesizer.delegate = delegate
        delegate.setCallbacks(
            onStart = { onStatusChange?.invoke(TTSStatus.SPEAKING) },
            onSpeechEnded = { generation ->
                // Only deactivate if this callback is for the current generation
                if (generation == speechGeneration) {
                    deactivateAudioSession()
                    onStatusChange?.invoke(TTSStatus.IDLE)
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

    actual fun speak(text: String) {
        val voice = currentVoice ?: return
        // Stop any current speech - its cancel callback will have the OLD generation from the map
        synthesizer.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
        // Increment generation for the new utterance
        speechGeneration++
        activateAudioSession()
        val utterance = AVSpeechUtterance.speechUtteranceWithString(text)
        //TODO maybe we need to make speed configurable
        utterance.rate = 0.3f
        utterance.pitchMultiplier = 1.0f
        utterance.volume = 1.0f
        utterance.voice = voice

        currentUtterance = utterance
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
        if (!synthesizer.isSpeaking()) {
            // Not speaking - but session might be active if synth failed to start
            // Ensure clean state by deactivating and setting IDLE
            deactivateAudioSession()
            onStatusChange?.invoke(TTSStatus.IDLE)
            return
        }
        // Stop synthesizer - cancel callback will fire and handle deactivation/status
        // Generation stays the same so callback knows this is a valid stop
        synthesizer.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
    }

    actual fun setOnWordBoundaryListener(listener: (wordRange: IntRange) -> Unit) {
        onWordBoundary = listener
    }

    actual fun setOnStatusChangeListener(listener: (status: TTSStatus) -> Unit) {
        onStatusChange = listener
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
        currentGeneration = generation
        utteranceGenerations[utterance] = generation
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
        // Only notify if this utterance's generation matches current and nothing else queued
        if (utteranceGeneration == currentGeneration && !synthesizer.isSpeaking()) {
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
        // Only notify if this utterance's generation matches current and nothing else queued
        if (utteranceGeneration == currentGeneration && !synthesizer.isSpeaking()) {
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