package com.slovy.slovymovyapp.speech

import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.logging.AppLogger
import kotlinx.coroutines.CancellationException

/**
 * Voice selection mechanics shared by row audio and Word details: resolving the engine's target
 * language, loading the enabled voices, and rotating through them across plays starting from a
 * random voice. Callers own the caching policy — Word details loads once per screen, row audio
 * calls [loadVoices] on every play so enable/disable changes made in Settings apply immediately.
 */
class RotatingVoiceSelector(
    private val speechPlayer: SpeechPlayer,
    private val voiceFilterHelper: VoiceFilterHelper,
) {
    // The engine's language lookup is stable, so it's cached. Rotation state survives voice
    // reloads so consecutive plays keep cycling voices.
    private val targetLanguageByLanguage = mutableMapOf<Language, Text2SpeechLanguage>()
    private val voiceIndexByLanguage = mutableMapOf<Language, Int>()

    /** Enabled voices for [language]; empty when the language is unsupported or the engine fails. */
    suspend fun loadVoices(language: Language): List<Text2SpeechVoice> {
        return try {
            val target = targetLanguageByLanguage[language]
                ?: speechPlayer.getAvailableLanguages().firstOrNull { it.language == language }
                    ?.also { targetLanguageByLanguage[language] = it }
                ?: return emptyList()
            val voices = voiceFilterHelper.loadEnabledVoices(speechPlayer, target)
            if (voices.isNotEmpty() && voiceIndexByLanguage[language] == null) {
                voiceIndexByLanguage[language] = voices.indices.random()
            }
            voices
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.warn(TAG, "Unable to load voices for ${language.code}", e)
            emptyList()
        }
    }

    /** Advances the rotation for [language] and returns the voice to use. [voices] must be non-empty. */
    fun nextVoice(language: Language, voices: List<Text2SpeechVoice>): Text2SpeechVoice {
        val nextIndex = ((voiceIndexByLanguage[language] ?: -1) + 1).mod(voices.size)
        voiceIndexByLanguage[language] = nextIndex
        return voices[nextIndex]
    }

    private companion object {
        const val TAG = "RotatingVoiceSelector"
    }
}
