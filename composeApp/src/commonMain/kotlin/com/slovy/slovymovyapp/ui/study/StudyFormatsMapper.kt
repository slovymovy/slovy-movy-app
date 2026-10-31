package com.slovy.slovymovyapp.ui.study

import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.learning.StudyFormat
import com.slovy.slovymovyapp.data.learning.StudyFormatSelection
import com.slovy.slovymovyapp.data.learning.session.SessionCard
import com.slovy.slovymovyapp.i18n.UiText
import slovymovyapp.composeapp.generated.resources.*

/** Builds the formats page for [language] from the learner's current [selection]. */
fun studyFormatsUiState(language: Language, selection: StudyFormatSelection): StudyFormatsUiState {
    val canDisable = selection.enabled.size > 1
    fun row(format: StudyFormat, title: UiText, description: UiText, label: UiText?): StudyFormatRowUiState {
        val enabled = format in selection.enabled
        return StudyFormatRowUiState(
            format = format,
            title = title,
            description = description,
            label = label,
            enabled = enabled,
            canDisable = !enabled || canDisable,
        )
    }
    return StudyFormatsUiState(
        intro = UiText.Resource(Res.string.study_explainer_intro),
        rows = listOf(
            row(
                StudyFormat.WORD_TO_TRANSLATION,
                UiText.Resource(Res.string.study_explainer_word_to_translation_title),
                UiText.Resource(Res.string.study_explainer_word_to_translation),
                UiText.Resource(Res.string.study_explainer_label_easiest),
            ),
            row(
                StudyFormat.TRANSLATION_TO_WORD,
                UiText.Resource(Res.string.study_explainer_translation_to_word_title),
                UiText.Resource(Res.string.study_explainer_translation_to_word),
                null,
            ),
            row(
                StudyFormat.FILL_THE_GAP,
                UiText.Resource(Res.string.study_explainer_fill_gap_title),
                UiText.Resource(Res.string.study_explainer_fill_gap),
                null,
            ),
            row(
                StudyFormat.LISTEN,
                UiText.Resource(Res.string.study_explainer_listen_title),
                UiText.Resource(Res.string.study_explainer_listen),
                null,
            ),
            row(
                StudyFormat.SOURCE_ONLY,
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
