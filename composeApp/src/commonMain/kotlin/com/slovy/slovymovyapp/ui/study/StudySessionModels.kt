package com.slovy.slovymovyapp.ui.study

import com.slovy.slovymovyapp.i18n.UiText

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
        val isPlayingAudio: Boolean = false,
        val isPreparingAudio: Boolean = false,
    ) : StudySessionUiState

    data class Complete(
        val reviewedCount: Int,
        val message: String,
    ) : StudySessionUiState
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

sealed interface StudyCardUiState {
    val id: String
    val chipLabel: UiText
    val back: StudyCardBackUiState

    data class Recognition(
        override val id: String,
        override val chipLabel: UiText,
        val promptWord: String,
        val promptAudioText: String? = promptWord,
        val mode: StudyRecognitionMode,
        override val back: StudyCardBackUiState,
    ) : StudyCardUiState

    data class Production(
        override val id: String,
        override val chipLabel: UiText,
        val promptLabel: UiText,
        val promptText: String,
        val firstLetterHint: FirstLetterHint? = null,
        val isDefinitionPrompt: Boolean = false,
        override val back: StudyCardBackUiState,
    ) : StudyCardUiState

    data class Cloze(
        override val id: String,
        override val chipLabel: UiText,
        val prompt: StudyClozeTextUiState,
        val translationHint: String? = null,
        override val back: StudyCardBackUiState,
    ) : StudyCardUiState

    data class Listening(
        override val id: String,
        override val chipLabel: UiText,
        val promptAudioText: String,
        override val back: StudyCardBackUiState,
    ) : StudyCardUiState
}

enum class StudyRecognitionMode {
    BILINGUAL,
    MONOLINGUAL,
}

data class StudyCardBackUiState(
    val headline: String,
    val isLemmaHeadline: Boolean = false,
    val secondary: String? = null,
    val definition: String? = null,
    val examples: List<StudyExampleUiState> = emptyList(),
    val cloze: StudyClozeTextUiState? = null,
    val audioText: String? = headline,
)

data class StudyExampleUiState(
    val text: String,
    val translation: String? = null,
)

data class StudyClozeTextUiState(
    val prefix: String,
    val answer: String,
    val suffix: String,
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
