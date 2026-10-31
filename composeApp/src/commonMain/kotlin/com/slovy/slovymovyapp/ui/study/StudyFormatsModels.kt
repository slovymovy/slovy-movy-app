package com.slovy.slovymovyapp.ui.study

import com.slovy.slovymovyapp.data.learning.StudyFormat
import com.slovy.slovymovyapp.i18n.UiText

/**
 * The "How studying works" page: one row per way a word can come back, each with a switch, so the
 * page that explains the formats is also where the learner picks them.
 */
data class StudyFormatsUiState(
    val intro: UiText,
    val rows: List<StudyFormatRowUiState>,
)

data class StudyFormatRowUiState(
    val format: StudyFormat,
    val title: UiText,
    val description: UiText,
    /** A short difficulty hint shown under the title, on the easiest and the hardest format only. */
    val label: UiText?,
    val enabled: Boolean,
    /** False on the last enabled row: a language needs at least one format to be studied in. */
    val canDisable: Boolean,
)
