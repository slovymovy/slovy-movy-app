package com.slovy.slovymovyapp.ui.study

import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.learning.StudyFormatSelection
import com.slovy.slovymovyapp.data.learning.session.SessionCard
import com.slovy.slovymovyapp.i18n.UiText
import slovymovyapp.composeapp.generated.resources.*

/** Builds the formats page for [language] from the learner's current [selection]. */
fun studyFormatsUiState(language: Language, selection: StudyFormatSelection): StudyFormatsUiState {
    fun row(row: StudyFormatRow, title: UiText, description: UiText, label: UiText?): StudyFormatRowUiState =
        StudyFormatRowUiState(
            row = row,
            title = title,
            description = description,
            label = label,
            enabled = row.format?.let { it in selection.enabled } ?: true,
        )
    return StudyFormatsUiState(
        intro = UiText.Resource(Res.string.study_explainer_intro),
        rows = listOf(
            row(
                StudyFormatRow.WORD_TO_TRANSLATION,
                UiText.Resource(Res.string.study_explainer_word_to_translation_title),
                UiText.Resource(Res.string.study_explainer_word_to_translation),
                UiText.Resource(Res.string.study_explainer_label_easiest),
            ),
            row(
                StudyFormatRow.TRANSLATION_TO_WORD,
                UiText.Resource(Res.string.study_explainer_translation_to_word_title),
                UiText.Resource(Res.string.study_explainer_translation_to_word),
                null,
            ),
            row(
                StudyFormatRow.FILL_THE_GAP,
                UiText.Resource(Res.string.study_explainer_fill_gap_title),
                UiText.Resource(Res.string.study_explainer_fill_gap),
                null,
            ),
            row(
                StudyFormatRow.SPELLING,
                UiText.Resource(Res.string.study_explainer_spelling_title),
                UiText.Resource(Res.string.study_explainer_spelling),
                null,
            ),
            row(
                StudyFormatRow.LISTEN,
                UiText.Resource(Res.string.study_explainer_listen_title),
                UiText.Resource(Res.string.study_explainer_listen),
                null,
            ),
            row(
                StudyFormatRow.SOURCE_ONLY,
                UiText.Resource(Res.string.study_explainer_source_only_title, listOf(language.selfName)),
                UiText.Resource(Res.string.study_explainer_source_only),
                UiText.Resource(Res.string.study_explainer_label_not_for_beginners),
            ),
        ),
    )
}

/**
 * Whether the formats page exists for this card: only with translation languages, since without
 * them there is one format and nothing to choose, and only for a known language. The menu row
 * and the page both use this, so the row never opens nothing.
 */
fun SessionCard.hasStudyFormatsPage(): Boolean =
    translationTargets.isNotEmpty() && Language.fromCodeOrNull(card.langCode) != null
