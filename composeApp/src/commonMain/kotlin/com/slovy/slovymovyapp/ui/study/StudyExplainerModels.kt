package com.slovy.slovymovyapp.ui.study

import com.slovy.slovymovyapp.i18n.UiText

/**
 * The "How studying works" page: one row per way a word can come back, built from the card the
 * learner is on so the rows talk about that word and mark the format they are looking at.
 */
data class StudyExplainerUiState(
    val intro: UiText,
    val rows: List<StudyExplainerRowUiState>,
)

data class StudyExplainerRowUiState(
    val format: StudyExplainerFormat,
    val title: UiText,
    val description: UiText,
    val status: StudyExplainerRowStatus,
)

/**
 * The formats as the learner sees them, in unlock order; the declaration order is the ladder. The
 * three source-language-only kinds share one row: to the learner they are one idea, "no
 * translation anywhere", and they unlock together on the same stability gate.
 */
enum class StudyExplainerFormat {
    WORD_TO_TRANSLATION,
    TRANSLATION_TO_WORD,
    FILL_THE_GAP,
    LISTEN,
    SOURCE_ONLY,
}

/** The word's real state per format; nothing here is a guess about what comes next. */
enum class StudyExplainerRowStatus {
    /** This word already comes back this way. */
    UNLOCKED,

    /** The format of the card on screen. */
    CURRENT,

    /** This word has not reached this format yet. */
    NOT_YET,
}
