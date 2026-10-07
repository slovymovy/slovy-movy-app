package com.slovy.slovymovyapp.ui.study

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.slovy.slovymovyapp.data.learning.spelling.SpellingChecker
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

    /**
     * The spelling card: the learner types the word from its translation. [lemma] is the expected
     * answer and the headline of [back]; [languageCode] is the studied language, handed to the
     * keyboard as a hint locale.
     */
    data class Typed(
        override val id: String,
        override val chipLabel: UiText,
        val promptText: String,
        /** A definition in the studied language cues the word instead of a translation; shown smaller. */
        val isDefinitionPrompt: Boolean = false,
        /** Shown as the field's placeholder, so the learner knows which form to type. */
        val partOfSpeech: UiText?,
        val lemma: String,
        val languageCode: String,
        val input: StudyTypedInputUiState = StudyTypedInputUiState(),
        val result: StudyTypedResultUiState? = null,
        override val senses: List<StudyCardSenseUiState> = emptyList(),
        override val activeSenseId: String? = null,
        override val back: StudyCardBackUiState,
    ) : StudyCardUiState {
        /** Check needs at least one character the learner typed; hint letters alone do not count. */
        val canCheck: Boolean
            get() = SpellingChecker.hasLearnerInput(input.value.text, input.hintedPositions)

        /** The hint control goes inert once the field holds the whole word. */
        val canHint: Boolean
            get() = SpellingChecker.canHint(input.value.text, lemma)

        /** Length slots after the caret, one per letter not yet typed; shown only after the first hint. */
        val remainingSlots: Int
            get() = if (input.hintsUsed == 0) 0 else (lemma.length - input.value.text.length).coerceAtLeast(0)

        /** The learner edited the field. Hints the edit destroyed stop being hints. */
        fun withInput(value: TextFieldValue): Typed = copy(
            input = input.copy(
                value = value,
                hintedPositions = SpellingChecker.hintedPositionsAfterEdit(value.text, lemma, input.hintedPositions),
            ),
        )

        /** Reveals the next letter, discarding a wrong tail first. Null when no hint is possible. */
        fun withNextLetterHint(): Typed? {
            val hint = SpellingChecker.applyHint(input.value.text, lemma, input.hintedPositions) ?: return null
            return copy(
                input = StudyTypedInputUiState(
                    value = TextFieldValue(text = hint.value, selection = TextRange(hint.value.length)),
                    hintedPositions = hint.hintedPositions,
                    hintsUsed = input.hintsUsed + 1,
                ),
            )
        }

        /** Compares the field with the word and records the outcome for the back. Null when Check is disabled. */
        fun checked(): Typed? {
            if (!canCheck) return null
            return copy(result = evaluate())
        }

        /**
         * The learner gives up on typing. Whatever they typed is still compared, so an attempt is
         * never thrown away. A field holding nothing of their own, empty or hint letters only,
         * shows the word alone, the same rule that keeps Check disabled.
         */
        fun answerShown(): Typed = copy(
            result = if (canCheck) evaluate() else StudyTypedResultUiState.Shown,
        )

        private fun evaluate(): StudyTypedResultUiState {
            val attempt = input.value.text
            if (SpellingChecker.isCorrect(attempt, lemma)) {
                return StudyTypedResultUiState.Correct(hintedPositions = input.hintedPositions)
            }
            val diff = SpellingChecker.diff(attempt, lemma)
            return StudyTypedResultUiState.Incorrect(
                attempt = attempt.trim(),
                missedPositions = diff.missed,
                accentOnlyPositions = diff.accentOnly,
                extraPositions = diff.extra,
            )
        }
    }
}

data class StudyTypedInputUiState(
    val value: TextFieldValue = TextFieldValue(),
    /** Indices in [value] that a hint revealed and the learner has not edited since. */
    val hintedPositions: Set<Int> = emptySet(),
    val hintsUsed: Int = 0,
)

sealed interface StudyTypedResultUiState {
    /** The attempt matched; [hintedPositions] keep their hint colour in the lemma on the back. */
    data class Correct(val hintedPositions: Set<Int>) : StudyTypedResultUiState

    /**
     * [attempt] is the field trimmed, otherwise as typed. [missedPositions] and
     * [accentOnlyPositions] index the lemma: letters to underline as wrong, and letters right but
     * for the accent. [extraPositions] index [attempt]: letters the lemma has no place for.
     */
    data class Incorrect(
        val attempt: String,
        val missedPositions: Set<Int>,
        val accentOnlyPositions: Set<Int> = emptySet(),
        val extraPositions: Set<Int> = emptySet(),
    ) : StudyTypedResultUiState

    /** The learner gave up; the back shows the lemma alone. */
    data object Shown : StudyTypedResultUiState
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
