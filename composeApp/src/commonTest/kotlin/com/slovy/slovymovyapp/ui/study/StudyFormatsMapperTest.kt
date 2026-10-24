package com.slovy.slovymovyapp.ui.study

import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.learning.Card
import com.slovy.slovymovyapp.data.learning.CardKind
import com.slovy.slovymovyapp.data.learning.CardScheduling
import com.slovy.slovymovyapp.data.learning.CardState
import com.slovy.slovymovyapp.data.learning.CardVariant
import com.slovy.slovymovyapp.data.learning.StudyFormat
import com.slovy.slovymovyapp.data.learning.StudyFormatSelection
import com.slovy.slovymovyapp.data.learning.session.SessionCard
import com.slovy.slovymovyapp.data.remote.WordResult
import com.slovy.slovymovyapp.i18n.UiText
import slovymovyapp.composeapp.generated.resources.Res
import slovymovyapp.composeapp.generated.resources.study_explainer_fill_gap
import slovymovyapp.composeapp.generated.resources.study_explainer_intro
import slovymovyapp.composeapp.generated.resources.study_explainer_label_easiest
import slovymovyapp.composeapp.generated.resources.study_explainer_label_not_for_beginners
import slovymovyapp.composeapp.generated.resources.study_explainer_listen
import slovymovyapp.composeapp.generated.resources.study_explainer_source_only
import slovymovyapp.composeapp.generated.resources.study_explainer_source_only_title
import slovymovyapp.composeapp.generated.resources.study_explainer_translation_to_word
import slovymovyapp.composeapp.generated.resources.study_explainer_word_to_translation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class StudyFormatsMapperTest {

    @Test
    fun pageListsEveryFormatInLadderOrder() {
        val page = studyFormatsUiState(Language.DUTCH, StudyFormatSelection.ALL)

        assertEquals(
            StudyFormat.entries,
            page.rows.map { it.format },
            "Rows must follow the ladder, the order the families unlock in",
        )
        val intro = assertIs<UiText.Resource>(page.intro)
        assertEquals(Res.string.study_explainer_intro, intro.key)
    }

    @Test
    fun rowsCarryTheirFixedCopyAndTheSourceLanguageName() {
        val page = studyFormatsUiState(Language.DUTCH, StudyFormatSelection.ALL)

        val expectedDescriptions = mapOf(
            StudyFormat.WORD_TO_TRANSLATION to Res.string.study_explainer_word_to_translation,
            StudyFormat.TRANSLATION_TO_WORD to Res.string.study_explainer_translation_to_word,
            StudyFormat.FILL_THE_GAP to Res.string.study_explainer_fill_gap,
            StudyFormat.LISTEN to Res.string.study_explainer_listen,
            StudyFormat.SOURCE_ONLY to Res.string.study_explainer_source_only,
        )
        expectedDescriptions.forEach { (format, key) ->
            val description = assertIs<UiText.Resource>(page.row(format).description, "Description of $format")
            assertEquals(key, description.key, "Description of $format")
        }
        val sourceOnlyTitle = assertIs<UiText.Resource>(page.row(StudyFormat.SOURCE_ONLY).title)
        assertEquals(Res.string.study_explainer_source_only_title, sourceOnlyTitle.key)
        assertEquals(
            listOf(Language.DUTCH.selfName),
            sourceOnlyTitle.args,
            "The source-only row must name the studied language as it names itself",
        )
    }

    @Test
    fun onlyTheEasiestAndTheHardestRowCarryADifficultyLabel() {
        val page = studyFormatsUiState(Language.DUTCH, StudyFormatSelection.ALL)

        val easiest = assertIs<UiText.Resource>(page.row(StudyFormat.WORD_TO_TRANSLATION).label)
        assertEquals(Res.string.study_explainer_label_easiest, easiest.key)
        val hardest = assertIs<UiText.Resource>(page.row(StudyFormat.SOURCE_ONLY).label)
        assertEquals(Res.string.study_explainer_label_not_for_beginners, hardest.key)
        listOf(StudyFormat.TRANSLATION_TO_WORD, StudyFormat.FILL_THE_GAP, StudyFormat.LISTEN).forEach { format ->
            assertEquals(null, page.row(format).label, "$format carries no label")
        }
    }

    @Test
    fun everyRowIsOnAndSwitchableWhenAllFormatsAreEnabled() {
        val page = studyFormatsUiState(Language.DUTCH, StudyFormatSelection.ALL)

        assertTrue(page.rows.all { it.enabled }, "All formats are enabled")
        assertTrue(page.rows.all { it.canDisable }, "With several formats on, any of them may be switched off")
    }

    @Test
    fun theLastEnabledRowCannotBeSwitchedOff() {
        val page = studyFormatsUiState(
            Language.DUTCH,
            StudyFormatSelection(setOf(StudyFormat.TRANSLATION_TO_WORD)),
        )

        val last = page.row(StudyFormat.TRANSLATION_TO_WORD)
        assertTrue(last.enabled)
        assertFalse(last.canDisable, "A language needs at least one format, so the last one stays on")
        page.rows.filter { it.format != StudyFormat.TRANSLATION_TO_WORD }.forEach { row ->
            assertFalse(row.enabled, "${row.format} is off")
            assertTrue(row.canDisable, "${row.format} may be switched on")
        }
    }

    @Test
    fun targetOnlySetupHasNoPage() {
        val card = sessionCard(
            variant = CardVariant(CardKind.WORD_TO_SOURCE_DEFINITION, targetLang = null),
            translationTargets = emptyList(),
        )

        assertFalse(card.hasStudyFormatsPage(), "Without translation languages there is one format and nothing to choose")
    }

    @Test
    fun unknownLanguageHasNoPage() {
        val card = sessionCard(
            variant = CardVariant(CardKind.WORD_TO_TRANSLATION, targetLang = Language.ENGLISH.code),
            translationTargets = listOf(Language.ENGLISH),
            langCode = "xx",
        )

        assertFalse(card.hasStudyFormatsPage(), "A language the app does not know cannot name itself on the page")
    }

    @Test
    fun bilingualSetupHasAPage() {
        val card = sessionCard(
            variant = CardVariant(CardKind.WORD_TO_TRANSLATION, targetLang = Language.ENGLISH.code),
            translationTargets = listOf(Language.ENGLISH),
        )

        assertTrue(card.hasStudyFormatsPage())
    }

    private fun StudyFormatsUiState.row(format: StudyFormat): StudyFormatRowUiState =
        assertNotNull(rows.firstOrNull { it.format == format }, "Missing row $format")

    private fun sessionCard(
        variant: CardVariant,
        translationTargets: List<Language>,
        langCode: String = Language.DUTCH.code,
    ): SessionCard =
        SessionCard(
            card = Card(
                id = Uuid.parse("00000000-0000-0000-0000-000000000201"),
                senseId = Uuid.parse("00000000-0000-0000-0000-000000000101"),
                lemmaId = Uuid.parse("00000000-0000-0000-0000-000000000301"),
                langCode = langCode,
                family = variant.kind.family,
                answerKey = "gezellig",
                scheduling = CardScheduling(
                    state = CardState.REVIEW,
                    stability = 1.0,
                    difficulty = 1.0,
                    dueEpochMs = 0L,
                    lastReviewEpochMs = null,
                    reps = 0,
                    lapses = 0,
                    createdAtEpochMs = 0L,
                    availableAfterEpochMs = null,
                    suspended = false,
                ),
            ),
            variant = variant,
            wordResult = WordResult(card = null),
            senseId = "00000000-0000-0000-0000-000000000101",
            example = null,
            translationTargets = translationTargets,
        )
}
