package com.slovy.slovymovyapp.data.learning

import com.slovy.slovymovyapp.data.learning.fsrs.FsrsDefaults
import com.slovy.slovymovyapp.data.learning.fsrs.crossFamilyCreditFactor
import com.slovy.slovymovyapp.data.learning.fsrs.inheritedStability
import com.slovy.slovymovyapp.data.learning.fsrs.reentryScheduling
import com.slovy.slovymovyapp.data.learning.fsrs.unlockPathCreditFactor
import com.slovy.slovymovyapp.data.learning.fsrs.unlocksEventually
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StudyFormatSelectionTest {

    private val bothOff = StudyFormatSelection(emptySet(), playsTranslationKinds = true)

    @Test
    fun onlyTheEasiestAndTheHardestKindsHaveAFormat() {
        assertEquals(
            setOf(CardKind.WORD_TO_TRANSLATION),
            CardKind.entries.filter { it.format == StudyFormat.WORD_TO_TRANSLATION }.toSet(),
        )
        assertEquals(
            setOf(CardKind.WORD_TO_SOURCE_DEFINITION, CardKind.SOURCE_DEFINITION_TO_WORD),
            CardKind.entries.filter { it.format == StudyFormat.SOURCE_ONLY }.toSet(),
            "Only the kinds that render without a translation target are source-only",
        )
        assertEquals(
            setOf(
                CardKind.TRANSLATION_TO_WORD,
                CardKind.CLOZE_TRANSLATION,
                CardKind.CLOZE_SOURCE,
                CardKind.LISTENING_TRANSLATION,
                CardKind.TRANSLATION_TO_WORD_TYPED,
            ),
            CardKind.entries.filter { it.format == null }.toSet(),
            "Every other kind, the typed spelling card included, is always on",
        )
    }

    @Test
    fun allFormatsStudyEveryFamily() {
        assertEquals(CardFamily.entries, StudyFormatSelection.ALL.families)
        assertTrue(StudyFormatSelection.ALL.excludedFamilies.isEmpty())
    }

    @Test
    fun withBothFormatsOffOnlyRecognitionIsLeftOut() {
        assertEquals(
            CardFamily.entries - CardFamily.RECOGNIZE_SENSE,
            bothOff.families,
            "Recognition is the one family whose every kind can be switched off",
        )
        assertEquals(setOf(CardFamily.RECOGNIZE_SENSE), bothOff.excludedFamilies)
        assertTrue(bothOff.allows(CardKind.TRANSLATION_TO_WORD))
        assertTrue(bothOff.allows(CardKind.TRANSLATION_TO_WORD_TYPED))
        assertFalse(bothOff.allows(CardKind.WORD_TO_TRANSLATION))
        assertFalse(bothOff.allows(CardKind.SOURCE_DEFINITION_TO_WORD))
    }

    @Test
    fun switchingTheEasiestOffSkipsRecognitionAltogether() {
        val withoutEasiest = StudyFormatSelection(setOf(StudyFormat.SOURCE_ONLY), playsTranslationKinds = true)

        assertEquals(
            CardFamily.entries - CardFamily.RECOGNIZE_SENSE,
            withoutEasiest.families,
            "The first switch is the recognition step itself, not one of its cards",
        )
        assertFalse(
            withoutEasiest.allows(CardKind.WORD_TO_SOURCE_DEFINITION),
            "Source-only recognition is still recognition: a new word must not meet its hardest card first",
        )
        assertTrue(withoutEasiest.allows(CardKind.SOURCE_DEFINITION_TO_WORD), "Source-only production stays, behind its gate")
        assertTrue(withoutEasiest.allows(CardKind.TRANSLATION_TO_WORD))
    }

    @Test
    fun withoutTranslationTargetsOnlyTheSourceOnlyKindsPlay() {
        val withoutTargets = StudyFormatSelection(StudyFormat.entries.toSet(), playsTranslationKinds = false)

        assertEquals(
            listOf(CardFamily.RECOGNIZE_SENSE, CardFamily.PRODUCE_WORD),
            withoutTargets.families,
            "Context, listening and spelling have no source-only kind, so they are the families left out",
        )
        assertTrue(withoutTargets.allows(CardKind.WORD_TO_SOURCE_DEFINITION))
        assertFalse(withoutTargets.allows(CardKind.TRANSLATION_TO_WORD), "An always-on kind still needs a translation")
        assertFalse(withoutTargets.allows(CardKind.WORD_TO_TRANSLATION), "Word-to-translation cannot be played without a target")
    }

    @Test
    fun intakeStartsInThePreferredFamilyWhenStudiedElseTheFirstStudiedOne() {
        val preferred = FsrsDefaults.DEFAULT_INTAKE_FAMILIES

        assertEquals(preferred, StudyFormatSelection.ALL.intakeFamilies(preferred))
        assertEquals(
            listOf(CardFamily.PRODUCE_WORD),
            bothOff.intakeFamilies(preferred),
            "With recognition off, a new word starts in the first family still studied",
        )
    }

    @Test
    fun eitherFormatCanBeSwitchedOffAndOnAgain() {
        val withoutEasiest = StudyFormatSelection.ALL.with(StudyFormat.WORD_TO_TRANSLATION, enabled = false)

        assertEquals(setOf(StudyFormat.SOURCE_ONLY), withoutEasiest.enabled)
        assertEquals(bothOff, withoutEasiest.with(StudyFormat.SOURCE_ONLY, enabled = false), "Both may be off")
        assertEquals(StudyFormatSelection.ALL, bothOff.with(StudyFormat.WORD_TO_TRANSLATION, enabled = true).with(StudyFormat.SOURCE_ONLY, enabled = true))
    }

    @Test
    fun siblingCreditStaysDirectWhileUnlockCreditChainsTheRules() {
        val config = FsrsDefaults.config()
        val recognizeToProduce = config.crossFamilyCreditFactor(
            CardFamily.RECOGNIZE_SENSE,
            CardFamily.PRODUCE_WORD,
            Rating.GOOD,
        )!!
        val produceToContext = config.crossFamilyCreditFactor(
            CardFamily.PRODUCE_WORD,
            CardFamily.PRODUCE_WORD_IN_CONTEXT,
            Rating.GOOD,
        )!!
        val produceToSpelling = config.crossFamilyCreditFactor(
            CardFamily.PRODUCE_WORD,
            CardFamily.SPELL_WORD,
            Rating.GOOD,
        )!!

        assertNull(
            config.crossFamilyCreditFactor(CardFamily.RECOGNIZE_SENSE, CardFamily.PRODUCE_WORD_IN_CONTEXT, Rating.GOOD),
            "A pair without a configured credit passes nothing to an existing sibling",
        )
        assertEquals(
            recognizeToProduce * produceToContext,
            config.unlockPathCreditFactor(CardFamily.RECOGNIZE_SENSE, CardFamily.PRODUCE_WORD_IN_CONTEXT, Rating.GOOD)!!,
            absoluteTolerance = 1e-9,
            message = "Reaching a far family directly inherits no more than climbing through the rules would",
        )
        assertEquals(
            recognizeToProduce * produceToSpelling,
            config.unlockPathCreditFactor(CardFamily.RECOGNIZE_SENSE, CardFamily.SPELL_WORD, Rating.GOOD)!!,
            absoluteTolerance = 1e-9,
            message = "Spelling branches off production, so recognition reaches it through production",
        )
        assertEquals(
            recognizeToProduce,
            config.unlockPathCreditFactor(CardFamily.RECOGNIZE_SENSE, CardFamily.PRODUCE_WORD, Rating.GOOD),
            "A direct rule is its own credit",
        )
        assertNull(
            config.unlockPathCreditFactor(CardFamily.PRODUCE_WORD, CardFamily.RECOGNIZE_SENSE, Rating.GOOD),
            "The rules only climb",
        )
        assertNull(
            config.unlockPathCreditFactor(CardFamily.RECOGNIZE_VOICE, CardFamily.SPELL_WORD, Rating.GOOD),
            "No rule leads from listening to spelling",
        )
        assertTrue(config.unlocksEventually(CardFamily.RECOGNIZE_SENSE, CardFamily.RECOGNIZE_VOICE))
        assertFalse(config.unlocksEventually(CardFamily.SPELL_WORD, CardFamily.RECOGNIZE_VOICE))
        assertNull(
            config.crossFamilyCreditFactor(CardFamily.RECOGNIZE_SENSE, CardFamily.PRODUCE_WORD, Rating.HARD),
            "Hard carries no credit",
        )
        assertEquals(
            0.80,
            config.crossFamilyCreditFactor(CardFamily.RECOGNIZE_VOICE, CardFamily.RECOGNIZE_SENSE, Rating.GOOD),
            "A pair with its own credit uses it, whatever the direction",
        )
    }

    @Test
    fun reentryInheritsAlongTheRulesForwardAndThroughTheDirectCreditBack() {
        // Re-entry carries no delay of its own: the call sites keep the hidden card's due time.
        val config = FsrsDefaults.config()

        val forward = config.reentryScheduling(
            sourceFamily = CardFamily.RECOGNIZE_SENSE,
            sourceState = CardState.REVIEW,
            sourceStability = 10.0,
            sourceDifficulty = 5.0,
            targetFamily = CardFamily.PRODUCE_WORD_IN_CONTEXT,
        )
        val chained = config.unlockPathCreditFactor(CardFamily.RECOGNIZE_SENSE, CardFamily.PRODUCE_WORD_IN_CONTEXT, Rating.GOOD)!!
        assertEquals(CardState.LEARNING, forward.state)
        assertEquals(config.inheritedStability(10.0, chained), forward.stability, absoluteTolerance = 1e-9)
        assertEquals(5.0, forward.difficulty)

        val back = config.reentryScheduling(
            sourceFamily = CardFamily.RECOGNIZE_VOICE,
            sourceState = CardState.REVIEW,
            sourceStability = 30.0,
            sourceDifficulty = 5.0,
            targetFamily = CardFamily.RECOGNIZE_SENSE,
        )
        assertEquals(config.inheritedStability(30.0, 0.80), back.stability, absoluteTolerance = 1e-9, message = "Back down the ladder the pair's own credit applies")

        val fresh = config.reentryScheduling(
            sourceFamily = CardFamily.RECOGNIZE_SENSE,
            sourceState = CardState.NEW,
            sourceStability = 0.0,
            sourceDifficulty = 0.0,
            targetFamily = CardFamily.PRODUCE_WORD,
        )
        assertEquals(CardState.NEW, fresh.state, "Nothing earned yet, so nothing is inherited")
        assertEquals(0.0, fresh.stability)
    }
}
