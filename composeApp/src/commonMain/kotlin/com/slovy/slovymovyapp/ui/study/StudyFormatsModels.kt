package com.slovy.slovymovyapp.ui.study

import com.slovy.slovymovyapp.data.learning.StudyFormat
import com.slovy.slovymovyapp.i18n.UiText

/**
 * The "How studying works" page: one row per way a word can come back. The easiest and the
 * hardest row carry a switch, so the page that explains the formats is also where the learner
 * picks them; the rows in between only explain.
 */
data class StudyFormatsUiState(
    val intro: UiText,
    val rows: List<StudyFormatRowUiState>,
)

/** The rows of the page in ladder order, the order the formats unlock in. */
enum class StudyFormatRow(
    /** The format the row switches, or null on a row that explains an always-on format. */
    val format: StudyFormat?,
) {
    WORD_TO_TRANSLATION(StudyFormat.WORD_TO_TRANSLATION),
    TRANSLATION_TO_WORD(null),
    FILL_THE_GAP(null),
    SPELLING(null),
    LISTEN(null),
    SOURCE_ONLY(StudyFormat.SOURCE_ONLY),
}

data class StudyFormatRowUiState(
    val row: StudyFormatRow,
    val title: UiText,
    val description: UiText,
    /** A short difficulty hint shown after the title, on the easiest and the hardest format only. */
    val label: UiText?,
    /** False only on a switchable row whose format is off. */
    val enabled: Boolean,
)
