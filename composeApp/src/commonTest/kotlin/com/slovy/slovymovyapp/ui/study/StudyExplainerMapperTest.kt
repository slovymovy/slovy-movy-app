package com.slovy.slovymovyapp.ui.study

import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.learning.Card
import com.slovy.slovymovyapp.data.learning.CardFamily
import com.slovy.slovymovyapp.data.learning.CardKind
import com.slovy.slovymovyapp.data.learning.CardScheduling
import com.slovy.slovymovyapp.data.learning.CardState
import com.slovy.slovymovyapp.data.learning.CardVariant
import com.slovy.slovymovyapp.data.learning.session.SessionCard
import com.slovy.slovymovyapp.data.remote.LanguageCard
import com.slovy.slovymovyapp.data.remote.LanguageCardExample
import com.slovy.slovymovyapp.data.remote.LanguageCardPosEntry
import com.slovy.slovymovyapp.data.remote.LanguageCardResponseSense
import com.slovy.slovymovyapp.data.remote.LanguageCardTranslation
import com.slovy.slovymovyapp.data.remote.LearnerLevel
import com.slovy.slovymovyapp.data.remote.PartOfSpeech
import com.slovy.slovymovyapp.data.remote.SenseFrequency
import com.slovy.slovymovyapp.data.remote.WordResult
import com.slovy.slovymovyapp.i18n.UiText
import slovymovyapp.composeapp.generated.resources.Res
import slovymovyapp.composeapp.generated.resources.study_explainer_fill_gap
import slovymovyapp.composeapp.generated.resources.study_explainer_intro
import slovymovyapp.composeapp.generated.resources.study_explainer_listen
import slovymovyapp.composeapp.generated.resources.study_explainer_source_only
import slovymovyapp.composeapp.generated.resources.study_explainer_source_only_title
import slovymovyapp.composeapp.generated.resources.study_explainer_translation_to_word
import slovymovyapp.composeapp.generated.resources.study_explainer_word_to_translation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.uuid.Uuid

private const val SenseId = "00000000-0000-0000-0000-000000000101"

class StudyExplainerMapperTest {

    @Test
    fun bilingualSetupListsEveryFormatInUnlockOrder() {
        val page = explainer(kind = CardKind.WORD_TO_TRANSLATION, unlocked = null)

        assertEquals(
            listOf(
                StudyExplainerFormat.WORD_TO_TRANSLATION,
                StudyExplainerFormat.TRANSLATION_TO_WORD,
                StudyExplainerFormat.FILL_THE_GAP,
                StudyExplainerFormat.LISTEN,
                StudyExplainerFormat.SOURCE_ONLY,
            ),
            page.rows.map { it.format },
            "Rows must follow the order the families unlock in, not the order of the mockup",
        )
        val intro = assertIs<UiText.Resource>(page.intro)
        assertEquals(Res.string.study_explainer_intro, intro.key)
    }

    @Test
    fun rowsCarryTheirFixedCopyAndTheSourceLanguageName() {
        val page = explainer(kind = CardKind.WORD_TO_TRANSLATION, unlocked = null)

        assertEquals(
            listOf(
                Res.string.study_explainer_word_to_translation,
                Res.string.study_explainer_translation_to_word,
                Res.string.study_explainer_fill_gap,
                Res.string.study_explainer_listen,
                Res.string.study_explainer_source_only,
            ),
            page.rows.map { assertIs<UiText.Resource>(it.description).key },
        )
        val sourceOnlyTitle = assertIs<UiText.Resource>(page.row(StudyExplainerFormat.SOURCE_ONLY).title)
        assertEquals(Res.string.study_explainer_source_only_title, sourceOnlyTitle.key)
        assertEquals(listOf(Language.DUTCH.selfName), sourceOnlyTitle.args)
    }

    @Test
    fun targetOnlySetupHasNoPage() {
        val sessionCard = sessionCard(
            variant = CardVariant(CardKind.WORD_TO_SOURCE_DEFINITION, targetLang = null),
            translationTargets = emptyList(),
        )

        assertNull(
            sessionCard.toStudyExplainerUiState(unlockedFamilyStability = null),
            "With no translation languages there is only one format, so there is nothing to explain",
        )
    }

    @Test
    fun currentRowFollowsTheCardKind() {
        val expected = mapOf(
            CardKind.WORD_TO_TRANSLATION to StudyExplainerFormat.WORD_TO_TRANSLATION,
            CardKind.TRANSLATION_TO_WORD to StudyExplainerFormat.TRANSLATION_TO_WORD,
            CardKind.CLOZE_TRANSLATION to StudyExplainerFormat.FILL_THE_GAP,
            CardKind.LISTENING_TRANSLATION to StudyExplainerFormat.LISTEN,
            CardKind.WORD_TO_SOURCE_DEFINITION to StudyExplainerFormat.SOURCE_ONLY,
            CardKind.SOURCE_DEFINITION_TO_WORD to StudyExplainerFormat.SOURCE_ONLY,
            CardKind.CLOZE_SOURCE to StudyExplainerFormat.SOURCE_ONLY,
        )
        assertEquals(CardKind.entries.toSet(), expected.keys, "Every kind needs a row to point at")

        expected.forEach { (kind, format) ->
            val page = explainer(kind = kind, unlocked = null)
            val current = page.rows.filter { it.status == StudyExplainerRowStatus.CURRENT }
            assertEquals(listOf(format), current.map { it.format }, "Exactly one row is current for $kind")
        }
    }

    @Test
    fun withoutUnlockDataTheLadderIsReadFromTheCurrentRowsPosition() {
        assertEquals(
            listOf(
                StudyExplainerRowStatus.CURRENT,
                StudyExplainerRowStatus.NOT_YET,
                StudyExplainerRowStatus.NOT_YET,
                StudyExplainerRowStatus.NOT_YET,
                StudyExplainerRowStatus.NOT_YET,
            ),
            explainer(kind = CardKind.WORD_TO_TRANSLATION, unlocked = null).rows.map { it.status },
            "A first-format card: here, then everything still to come",
        )
        assertEquals(
            listOf(
                StudyExplainerRowStatus.UNLOCKED,
                StudyExplainerRowStatus.UNLOCKED,
                StudyExplainerRowStatus.CURRENT,
                StudyExplainerRowStatus.NOT_YET,
                StudyExplainerRowStatus.NOT_YET,
            ),
            explainer(kind = CardKind.CLOZE_TRANSLATION, unlocked = null).rows.map { it.status },
            "Rows above the current one count as unlocked when nothing better is known",
        )
    }

    @Test
    fun freshWordReadsTheSameWithAndWithoutUnlockData() {
        val fresh = mapOf(CardFamily.RECOGNIZE_SENSE to 1.hours)
        assertEquals(
            explainer(kind = CardKind.WORD_TO_TRANSLATION, unlocked = null).rows.map { it.status },
            explainer(kind = CardKind.WORD_TO_TRANSLATION, unlocked = fresh).rows.map { it.status },
        )
    }

    @Test
    fun unlockedFormatsBelowTheCurrentRowReadAsUnlocked() {
        val page = explainer(
            kind = CardKind.WORD_TO_TRANSLATION,
            unlocked = mapOf(
                CardFamily.RECOGNIZE_SENSE to 3.days,
                CardFamily.PRODUCE_WORD to 1.days,
            ),
        )

        assertEquals(
            listOf(
                StudyExplainerRowStatus.CURRENT,
                StudyExplainerRowStatus.UNLOCKED,
                StudyExplainerRowStatus.NOT_YET,
                StudyExplainerRowStatus.NOT_YET,
                StudyExplainerRowStatus.NOT_YET,
            ),
            page.rows.map { it.status },
            "An unlocked format below the current row is labelled as such, not as still to come",
        )
    }

    @Test
    fun lockedFormatsAboveTheCurrentRowAreStillLocked() {
        // A source-only card can come up before later families unlock: its kinds are variants
        // gated on stability, not a family of their own.
        val page = explainer(
            kind = CardKind.WORD_TO_SOURCE_DEFINITION,
            unlocked = mapOf(CardFamily.RECOGNIZE_SENSE to 9.days),
        )

        assertEquals(
            listOf(
                StudyExplainerRowStatus.UNLOCKED,
                StudyExplainerRowStatus.NOT_YET,
                StudyExplainerRowStatus.NOT_YET,
                StudyExplainerRowStatus.NOT_YET,
                StudyExplainerRowStatus.CURRENT,
            ),
            page.rows.map { it.status },
        )
    }

    @Test
    fun sourceOnlyRowIgnoresAMatureListeningCard() {
        // No source-only kind belongs to the listening family, so its stability opens nothing.
        val page = explainer(
            kind = CardKind.LISTENING_TRANSLATION,
            unlocked = mapOf(
                CardFamily.RECOGNIZE_SENSE to 2.days,
                CardFamily.PRODUCE_WORD to 2.days,
                CardFamily.PRODUCE_WORD_IN_CONTEXT to 1.days,
                CardFamily.RECOGNIZE_VOICE to 9.days,
            ),
        )

        assertEquals(StudyExplainerRowStatus.NOT_YET, page.row(StudyExplainerFormat.SOURCE_ONLY).status)
    }

    @Test
    fun sourceOnlyRowUnlocksOnceAnyFamilyClearsItsStabilityGate() {
        val page = explainer(
            kind = CardKind.LISTENING_TRANSLATION,
            unlocked = mapOf(
                CardFamily.RECOGNIZE_SENSE to 9.days,
                CardFamily.PRODUCE_WORD to 2.days,
                CardFamily.PRODUCE_WORD_IN_CONTEXT to 1.days,
                CardFamily.RECOGNIZE_VOICE to 1.hours,
            ),
        )

        assertEquals(
            StudyExplainerRowStatus.UNLOCKED,
            page.row(StudyExplainerFormat.SOURCE_ONLY).status,
            "The source-only row opens with the first card past the seven-day gate",
        )
        assertTrue(page.rows.none { it.status == StudyExplainerRowStatus.NOT_YET })
    }

    private fun StudyExplainerUiState.row(format: StudyExplainerFormat): StudyExplainerRowUiState =
        assertNotNull(rows.firstOrNull { it.format == format }, "Missing row $format")

    private fun explainer(
        kind: CardKind,
        unlocked: Map<CardFamily, Duration>?,
    ): StudyExplainerUiState {
        val targetLang = Language.ENGLISH.code.takeIf { kind.requiresTranslation || kind == CardKind.CLOZE_SOURCE }
        val sessionCard = sessionCard(
            variant = CardVariant(kind, targetLang = targetLang),
            translationTargets = listOf(Language.ENGLISH),
        )
        return assertNotNull(sessionCard.toStudyExplainerUiState(unlocked))
    }

    private fun sessionCard(
        variant: CardVariant,
        translationTargets: List<Language>,
    ): SessionCard =
        SessionCard(
            card = Card(
                id = Uuid.parse("00000000-0000-0000-0000-000000000201"),
                senseId = Uuid.parse(SenseId),
                lemmaId = Uuid.parse("00000000-0000-0000-0000-000000000301"),
                langCode = Language.DUTCH.code,
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
            wordResult = WordResult(
                card = LanguageCard(
                    lemma = "gezellig",
                    zipfFrequency = 4.2f,
                    entries = listOf(
                        LanguageCardPosEntry(
                            pos = PartOfSpeech.ADJECTIVE,
                            formsViews = emptyList(),
                            senses = listOf(
                                LanguageCardResponseSense(
                                    senseId = SenseId,
                                    senseDefinition = "een gevoel van warmte",
                                    learnerLevel = LearnerLevel.A2,
                                    frequency = SenseFrequency.HIGH,
                                    semanticGroupId = "warmth",
                                    examples = listOf(
                                        LanguageCardExample(
                                            text = "Het was zo <w>gezellig</w>.",
                                            targetLangTranslations = mapOf(Language.ENGLISH to "It was so cosy."),
                                        ),
                                    ),
                                    targetLangDefinitions = mapOf(Language.ENGLISH to "a feeling of warmth"),
                                    translations = mapOf(
                                        Language.ENGLISH to listOf(
                                            LanguageCardTranslation(targetLangWord = "cosy"),
                                            LanguageCardTranslation(targetLangWord = "sociable"),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
            senseId = SenseId,
            example = null,
            translationTargets = translationTargets,
        )
}
