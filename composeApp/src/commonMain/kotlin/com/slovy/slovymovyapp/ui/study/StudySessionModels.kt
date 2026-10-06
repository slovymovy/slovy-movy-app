package com.slovy.slovymovyapp.ui.study

import com.slovy.slovymovyapp.i18n.UiText
import com.slovy.slovymovyapp.speech.RowAudioPhase

data class FirstLetterHint(val letter: Char, val letterCount: Int, val dotCount: Int)

sealed interface StudySessionUiState {
    data class Loading(
        val progress: StudySessionProgressUiState? = null,
    ) : StudySessionUiState

    data object Empty : StudySessionUiState

    data class Error(
        val message: UiText,
        val canRetry: Boolean = true,
    ) : StudySessionUiState

    data class Active(
        val progress: StudySessionProgressUiState,
        val card: StudyCardUiState,
        val side: StudyCardSide,
        val ratingOptions: List<StudyRatingUiState> = emptyList(),
        val isSubmittingReview: Boolean = false,
        /** Speaker currently sounding, by [StudyAudioKeys]. Null when nothing plays. */
        val playingAudioKey: String? = null,
        /** Speaker whose audio is being prepared. Null when nothing is preparing. */
        val preparingAudioKey: String? = null,
        val viewedSenseId: String? = null,
        val isAutoplayEnabled: Boolean = false,
        val isOverflowMenuOpen: Boolean = false,
        val removeConfirmation: StudyRemoveConfirmationUiState? = null,
    ) : StudySessionUiState

    data class Complete(
        val reward: StudySessionCompleteUiState,
    ) : StudySessionUiState
}

/**
 * Addresses of the speakers one study card can show. Only one sounds at a time, so a single key on
 * [StudySessionUiState.Active] makes them mutually exclusive: starting one releases the other's
 * control, the same way the sense cards behave.
 */
object StudyAudioKeys {
    /** The card's word: the front prompt and the back headline are the same speaker. */
    const val WORD: String = "word"

    fun example(senseAudioKey: String, index: Int): String = "$senseAudioKey#ex$index"

    /**
     * The filled cloze sentence on a back. It is the studied word in context and, on a
     * CLOZE_SOURCE back, the only sentence there — that back carries no examples.
     */
    fun cloze(senseAudioKey: String): String = "$senseAudioKey#cloze"
}

fun StudySessionUiState.Active.audioPhase(key: String): RowAudioPhase = when (key) {
    playingAudioKey -> RowAudioPhase.PLAYING
    preparingAudioKey -> RowAudioPhase.PREPARING
    else -> RowAudioPhase.IDLE
}

data class StudySessionProgressUiState(
    val current: Int,
    val total: Int,
) {
    val safeCurrent: Int = current.coerceIn(0, total.coerceAtLeast(0))
    val safeTotal: Int = total.coerceAtLeast(0)
}

enum class StudyCardSide {
    FRONT,
    BACK,
}

data class StudyRemoveConfirmationUiState(
    val lemma: String,
)

sealed interface StudyCardUiState {
    val id: String
    val chipLabel: UiText
    val back: StudyCardBackUiState
    val senses: List<StudyCardSenseUiState>
        get() = emptyList()
    val activeSenseId: String?
        get() = null

    data class Recognition(
        override val id: String,
        override val chipLabel: UiText,
        val promptWord: String,
        val promptAudioText: String? = promptWord,
        val mode: StudyRecognitionMode,
        override val senses: List<StudyCardSenseUiState> = emptyList(),
        override val activeSenseId: String? = null,
        override val back: StudyCardBackUiState,
    ) : StudyCardUiState

    data class Production(
        override val id: String,
        override val chipLabel: UiText,
        val promptLabel: UiText,
        val promptText: String,
        val firstLetterHint: FirstLetterHint? = null,
        val firstLetterHintRevealed: Boolean = false,
        val isDefinitionPrompt: Boolean = false,
        override val senses: List<StudyCardSenseUiState> = emptyList(),
        override val activeSenseId: String? = null,
        override val back: StudyCardBackUiState,
    ) : StudyCardUiState

    data class Cloze(
        override val id: String,
        override val chipLabel: UiText,
        val prompt: StudyClozeTextUiState,
        val firstLetterHint: FirstLetterHint? = null,
        val firstLetterHintRevealed: Boolean = false,
        val translationHint: StudyClozeTextUiState? = null,
        val translationHintRevealed: Boolean = false,
        override val senses: List<StudyCardSenseUiState> = emptyList(),
        override val activeSenseId: String? = null,
        override val back: StudyCardBackUiState,
    ) : StudyCardUiState

    data class Listening(
        override val id: String,
        override val chipLabel: UiText,
        val promptAudioText: String,
        override val senses: List<StudyCardSenseUiState> = emptyList(),
        override val activeSenseId: String? = null,
        override val back: StudyCardBackUiState,
    ) : StudyCardUiState
}

enum class StudyRecognitionMode {
    BILINGUAL,
    MONOLINGUAL,
}

// `translations` and `definitionTranslation` are pre-formatted multi-language blocks in the same
// bullet-per-language format the word-details screen uses (see translationsHeader in SenseCard);
// every language renders identically.
data class StudyCardBackUiState(
    val headline: String,
    val isLemmaHeadline: Boolean = false,
    // The studied-language word, shown under the headline when the headline is not the word itself
    // (bilingual recognition backs). Backs whose headline is already the lemma leave this null so
    // the word never appears twice.
    val sourceWord: String? = null,
    // True when the headline is a bullet block covering several translation languages; the UI
    // steps the headline typography down one size so the block doesn't overwhelm the card.
    val isMultiLanguageHeadline: Boolean = false,
    val translations: String? = null,
    val definition: String? = null,
    val definitionTranslation: String? = null,
    val examples: List<StudyExampleUiState> = emptyList(),
    val synonyms: List<StudySynonymUiState> = emptyList(),
    val cloze: StudyClozeTextUiState? = null,
    val clozeTranslation: StudyClozeTextUiState? = null,
    val audioText: String? = headline,
)

data class StudyCardSenseUiState(
    val id: String,
    val num: Int,
    val back: StudyCardBackUiState,
)

data class StudyExampleUiState(
    val text: String,
    val translation: String? = null,
)

data class StudySynonymUiState(
    val word: String,
    val known: Boolean = false,
)

data class StudyClozeTextUiState(
    val text: String,
    val answerRanges: List<IntRange>,
    val filled: Boolean = false,
)

data class StudyRatingUiState(
    val rating: StudyRating,
    val intervalLabel: String,
    val enabled: Boolean = true,
)

enum class StudyRating {
    AGAIN,
    HARD,
    GOOD,
    EASY,
}
