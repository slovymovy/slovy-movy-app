package com.slovy.slovymovyapp.ui.study

import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.learning.CardFamily
import com.slovy.slovymovyapp.data.learning.CardKind
import com.slovy.slovymovyapp.data.learning.session.SessionCard
import com.slovy.slovymovyapp.i18n.UiText
import slovymovyapp.composeapp.generated.resources.*
import kotlin.time.Duration

/**
 * Builds the explainer for the card the learner is on.
 *
 * [unlockedFamilyStability] is the sense's unlocked task families with their stability, from
 * [com.slovy.slovymovyapp.data.learning.session.SessionService.unlockedFamilyStability]. With it,
 * each row shows the word's real state: unlocked or not yet. Null means it could not be read; the
 * rows then fall back to a ladder read from the current row's position, which is right for a
 * fresh word.
 *
 * Returns null when [hasStudyExplainer] is false.
 */
fun SessionCard.toStudyExplainerUiState(
    unlockedFamilyStability: Map<CardFamily, Duration>?,
): StudyExplainerUiState? {
    if (!hasStudyExplainer()) return null
    val sourceLanguage = Language.fromCodeOrNull(card.langCode) ?: return null
    val currentFormat = variant.kind.explainerFormat()
    val currentIndex = StudyExplainerFormat.entries.indexOf(currentFormat)

    fun isUnlocked(format: StudyExplainerFormat): Boolean = when {
        unlockedFamilyStability == null -> format.ordinal < currentIndex
        // The source-only kinds are variants of particular families, each served once that
        // family's own card clears the gate; a mature card of another family opens nothing.
        format == StudyExplainerFormat.SOURCE_ONLY -> SOURCE_ONLY_FAMILIES.any { family ->
            (unlockedFamilyStability[family] ?: Duration.ZERO) >= SOURCE_ONLY_GATE
        }
        else -> format.family in unlockedFamilyStability
    }

    fun status(format: StudyExplainerFormat): StudyExplainerRowStatus = when {
        format == currentFormat -> StudyExplainerRowStatus.CURRENT
        isUnlocked(format) -> StudyExplainerRowStatus.UNLOCKED
        else -> StudyExplainerRowStatus.NOT_YET
    }

    val sourceName = sourceLanguage.selfName
    val rows = buildList<StudyExplainerRowUiState> {
        add(
            StudyExplainerRowUiState(
                format = StudyExplainerFormat.WORD_TO_TRANSLATION,
                title = UiText.Resource(Res.string.study_explainer_word_to_translation_title),
                description = UiText.Resource(Res.string.study_explainer_word_to_translation),
                status = status(StudyExplainerFormat.WORD_TO_TRANSLATION),
            ),
        )
        add(
            StudyExplainerRowUiState(
                format = StudyExplainerFormat.TRANSLATION_TO_WORD,
                title = UiText.Resource(Res.string.study_explainer_translation_to_word_title),
                description = UiText.Resource(Res.string.study_explainer_translation_to_word),
                status = status(StudyExplainerFormat.TRANSLATION_TO_WORD),
            ),
        )
        add(
            StudyExplainerRowUiState(
                format = StudyExplainerFormat.FILL_THE_GAP,
                title = UiText.Resource(Res.string.study_explainer_fill_gap_title),
                description = UiText.Resource(Res.string.study_explainer_fill_gap),
                status = status(StudyExplainerFormat.FILL_THE_GAP),
            ),
        )
        add(
            StudyExplainerRowUiState(
                format = StudyExplainerFormat.LISTEN,
                title = UiText.Resource(Res.string.study_explainer_listen_title),
                description = UiText.Resource(Res.string.study_explainer_listen),
                status = status(StudyExplainerFormat.LISTEN),
            ),
        )
        add(
            StudyExplainerRowUiState(
                format = StudyExplainerFormat.SOURCE_ONLY,
                title = UiText.Resource(Res.string.study_explainer_source_only_title, listOf(sourceName)),
                description = UiText.Resource(Res.string.study_explainer_source_only),
                status = status(StudyExplainerFormat.SOURCE_ONLY),
            ),
        )
    }

    return StudyExplainerUiState(
        intro = UiText.Resource(Res.string.study_explainer_intro),
        rows = rows,
    )
}

internal fun CardKind.explainerFormat(): StudyExplainerFormat = when (this) {
    CardKind.WORD_TO_TRANSLATION -> StudyExplainerFormat.WORD_TO_TRANSLATION
    CardKind.TRANSLATION_TO_WORD -> StudyExplainerFormat.TRANSLATION_TO_WORD
    CardKind.CLOZE_TRANSLATION -> StudyExplainerFormat.FILL_THE_GAP
    CardKind.LISTENING_TRANSLATION -> StudyExplainerFormat.LISTEN
    CardKind.WORD_TO_SOURCE_DEFINITION,
    CardKind.SOURCE_DEFINITION_TO_WORD,
    CardKind.CLOZE_SOURCE,
        -> StudyExplainerFormat.SOURCE_ONLY
}

// The family whose card carries this format. The source-only row spans every family: its kinds
// are variants gated on stability, not a family of their own.
private val StudyExplainerFormat.family: CardFamily?
    get() = when (this) {
        StudyExplainerFormat.WORD_TO_TRANSLATION -> CardFamily.RECOGNIZE_SENSE
        StudyExplainerFormat.TRANSLATION_TO_WORD -> CardFamily.PRODUCE_WORD
        StudyExplainerFormat.FILL_THE_GAP -> CardFamily.PRODUCE_WORD_IN_CONTEXT
        StudyExplainerFormat.LISTEN -> CardFamily.RECOGNIZE_VOICE
        StudyExplainerFormat.SOURCE_ONLY -> null
    }

/**
 * Whether a "How studying works" page exists for this card: only with translation languages, since
 * without them there is one format and nothing to progress to, and only for a known language.
 * The menu row and the page builder both use this, so the row never opens nothing.
 */
fun SessionCard.hasStudyExplainer(): Boolean =
    translationTargets.isNotEmpty() && Language.fromCodeOrNull(card.langCode) != null

private val SOURCE_ONLY_KINDS: List<CardKind> = CardKind.entries.filter { !it.requiresTranslation }

// The families that own a source-only kind; the listening family has none.
private val SOURCE_ONLY_FAMILIES: Set<CardFamily> = SOURCE_ONLY_KINDS.map { it.family }.toSet()

// The source-only kinds all sit behind the same stability gate; the row unlocks with the lowest.
private val SOURCE_ONLY_GATE: Duration = SOURCE_ONLY_KINDS.minOf { it.minStability }
