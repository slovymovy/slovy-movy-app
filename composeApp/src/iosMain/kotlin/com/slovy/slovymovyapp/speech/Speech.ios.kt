package com.slovy.slovymovyapp.speech


import com.slovy.slovymovyapp.data.Language
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
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

    private var onWordBoundary: ((IntRange) -> Unit)? = null
    private var onStatusChange: ((TTSStatus) -> Unit)? = null

    init {
        synthesizer.delegate = delegate
        delegate.setCallbacks(
            onStart = { onStatusChange?.invoke(TTSStatus.SPEAKING) },
            onFinish = { onStatusChange?.invoke(TTSStatus.IDLE) },
            onWordBoundary = { range -> onWordBoundary?.invoke(range) }
        )
    }

    actual fun speak(text: String) {
        val utterance = AVSpeechUtterance.speechUtteranceWithString(text)

        // Настройки скорости и тона
        utterance.rate = AVSpeechUtteranceDefaultSpeechRate
        utterance.pitchMultiplier = 1.0f
        utterance.volume = 1.0f

        currentUtterance = utterance
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
                        missingData = false // iOS автоматически скачивает голоса
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
        currentUtterance?.voice = selectedVoice
    }

    actual fun openSettings() {
        val settingsUrl = NSURL.URLWithString("App-prefs:root=General&path=ACCESSIBILITY/VOICEOVER/Speech")

        if (settingsUrl != null && UIApplication.sharedApplication.canOpenURL(settingsUrl)) {
            UIApplication.sharedApplication.openURL(settingsUrl)
        } else {
            // Fallback на общие настройки
            val generalSettingsUrl = NSURL.URLWithString("App-prefs:root=General")
            if (generalSettingsUrl != null) {
                UIApplication.sharedApplication.openURL(generalSettingsUrl)
            }
        }
    }

    actual fun stop() {
        synthesizer.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
        onStatusChange?.invoke(TTSStatus.IDLE)
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
    private var onFinish: (() -> Unit)? = null
    private var onWordBoundary: ((IntRange) -> Unit)? = null

    // TODO: see todo below
    private var started: Boolean = false

    fun setCallbacks(
        onStart: () -> Unit,
        onFinish: () -> Unit,
        onWordBoundary: (IntRange) -> Unit
    ) {
        this.onStart = onStart
        this.onFinish = onFinish
        this.onWordBoundary = onWordBoundary
    }

    // TODO: can't override it, because conflicts with didFinishSpeechUtterance
    /*override fun speechSynthesizer(
        synthesizer: AVSpeechSynthesizer,
        didStartSpeechUtterance: AVSpeechUtterance
    ) {
        onStart?.invoke()
    }*/

    override fun speechSynthesizer(
        synthesizer: AVSpeechSynthesizer,
        didFinishSpeechUtterance: AVSpeechUtterance
    ) {
        started = false
        onFinish?.invoke()
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