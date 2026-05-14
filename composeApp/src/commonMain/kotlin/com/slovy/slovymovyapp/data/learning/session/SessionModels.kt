package com.slovy.slovymovyapp.data.learning.session

import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.learning.Card
import com.slovy.slovymovyapp.data.learning.CardVariant
import com.slovy.slovymovyapp.data.remote.DictionaryClientException
import com.slovy.slovymovyapp.data.remote.LanguageCardResponseSense
import com.slovy.slovymovyapp.data.remote.WordResult

data class SessionCard(
    val card: Card,
    val variant: CardVariant,
    val wordResult: WordResult,
    val senseId: String,
    val example: ExamplePair?,
    val studiedSenseIds: Set<String> = emptySet(),
) {
    fun loadState(): SessionCardLoadState {
        if (wordResult.isWordLoading) return SessionCardLoadState.LOADING
        if (variant.kind.requiresTranslation && wordResult.isTranslationLoading) return SessionCardLoadState.LOADING
        if (loadError() != null) return SessionCardLoadState.ERROR
        val sense = sense() ?: return SessionCardLoadState.ERROR
        if (!variant.kind.requiresTranslation) return SessionCardLoadState.READY

        val targetLanguage = variant.targetLang?.let(Language::fromCodeOrNull) ?: return SessionCardLoadState.ERROR
        return if (
            sense.targetLangDefinitions[targetLanguage] != null ||
            sense.translations[targetLanguage].orEmpty().isNotEmpty()
        ) {
            SessionCardLoadState.READY
        } else {
            SessionCardLoadState.ERROR
        }
    }

    fun isReady(): Boolean = loadState() == SessionCardLoadState.READY

    fun canRetry(): Boolean = loadState() == SessionCardLoadState.ERROR

    fun loadError(): SessionCardLoadError? {
        wordResult.error?.let {
            return SessionCardLoadError(SessionCardLoadErrorReason.WORD_LOAD_FAILED, it)
        }
        val sense = sense()
            ?: return SessionCardLoadError(SessionCardLoadErrorReason.SENSE_MISSING)
        if (variant.kind.isCloze && example == null) {
            return SessionCardLoadError(SessionCardLoadErrorReason.EXAMPLE_MISSING)
        }
        if (variant.kind.requiresTranslation) {
            val targetLanguage = variant.targetLang?.let(Language::fromCodeOrNull)
                ?: return SessionCardLoadError(SessionCardLoadErrorReason.TARGET_LANGUAGE_MISSING)
            if (
                sense.targetLangDefinitions[targetLanguage] == null &&
                sense.translations[targetLanguage].orEmpty().isEmpty()
            ) {
                return SessionCardLoadError(SessionCardLoadErrorReason.TRANSLATION_MISSING)
            }
        }
        return null
    }

    private fun sense(): LanguageCardResponseSense? =
        wordResult.card?.entries
            ?.flatMap { it.senses }
            ?.firstOrNull { it.senseId == senseId }
}

enum class SessionCardLoadState {
    LOADING,
    READY,
    ERROR,
}

data class SessionCardLoadError(
    val reason: SessionCardLoadErrorReason,
    val cause: DictionaryClientException? = null,
)

enum class SessionCardLoadErrorReason {
    WORD_LOAD_FAILED,
    SENSE_MISSING,
    EXAMPLE_MISSING,
    TARGET_LANGUAGE_MISSING,
    TRANSLATION_MISSING,
}

data class ExamplePair(
    val exampleIndex: Long,
    val text: String,
    val clozeRange: IntRange,
)
