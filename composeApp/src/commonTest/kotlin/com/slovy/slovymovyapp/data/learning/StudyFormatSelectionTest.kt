package com.slovy.slovymovyapp.data.learning

import com.slovy.slovymovyapp.data.learning.fsrs.FsrsDefaults
import com.slovy.slovymovyapp.data.learning.fsrs.crossFamilyCreditFactor
import com.slovy.slovymovyapp.data.learning.fsrs.ladderCreditFactor
import com.slovy.slovymovyapp.data.learning.fsrs.unlockStabilityGate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StudyFormatSelectionTest {

    @Test
    fun sourceOnlyIsTheDefinitionKindsAndBothClozesFillTheGap() {
        assertEquals(
            setOf(CardKind.WORD_TO_SOURCE_DEFINITION, CardKind.SOURCE_DEFINITION_TO_WORD),
            CardKind.entries.filter { it.format == StudyFormat.SOURCE_ONLY }.toSet(),
            "Only the kinds that render without a translation target are source-only",
        )
        assertEquals(
            setOf(CardKind.CLOZE_TRANSLATION, CardKind.CLOZE_SOURCE),
            CardKind.entries.filter { it.format == StudyFormat.FILL_THE_GAP }.toSet(),
            "The source-sentence cloze needs a target for its hint and back, so it fills the gap",
        )
        listOf(StudyFormat.WORD_TO_TRANSLATION, StudyFormat.TRANSLATION_TO_WORD, StudyFormat.LISTEN).forEach { format ->
            assertEquals(1, CardKind.entries.count { it.format == format }, "One kind for $format")
        }
    }

    @Test
    fun allFormatsStudyEveryFamily() {
        assertEquals(CardFamily.entries, StudyFormatSelection.ALL.families)
        assertTrue(StudyFormatSelection.ALL.excludedFamilies.isEmpty())
    }

    @Test
    fun aFamilyIsStudiedWhenAnyOfItsKindsIs() {
        val sourceOnly = StudyFormatSelection(setOf(StudyFormat.SOURCE_ONLY))

        assertEquals(
            listOf(CardFamily.RECOGNIZE_SENSE, CardFamily.PRODUCE_WORD),
            sourceOnly.families,
            "Context and listening have no source-only kind, so they are the families left out",
        )
        assertEquals(setOf(CardFamily.PRODUCE_WORD_IN_CONTEXT, CardFamily.RECOGNIZE_VOICE), sourceOnly.excludedFamilies)
        assertTrue(sourceOnly.allows(CardKind.SOURCE_DEFINITION_TO_WORD))
        assertTrue(!sourceOnly.allows(CardKind.CLOZE_SOURCE))
    }

    @Test
    fun theLadderStepsOverSwitchedOffFamilies() {
        val selection = StudyFormatSelection(setOf(StudyFormat.WORD_TO_TRANSLATION, StudyFormat.LISTEN))

        assertEquals(CardFamily.RECOGNIZE_VOICE, selection.nextFamilyAfter(CardFamily.RECOGNIZE_SENSE))
        assertNull(selection.nextFamilyAfter(CardFamily.RECOGNIZE_VOICE))
    }

    @Test
    fun intakeStartsInThePreferredFamilyWhenStudiedElseTheFirstStudiedOne() {
        val preferred = FsrsDefaults.DEFAULT_INTAKE_FAMILIES

        assertEquals(preferred, StudyFormatSelection.ALL.intakeFamilies(preferred))
        assertEquals(
            listOf(CardFamily.PRODUCE_WORD_IN_CONTEXT),
            StudyFormatSelection(setOf(StudyFormat.FILL_THE_GAP, StudyFormat.LISTEN)).intakeFamilies(preferred),
            "With recognition off, a new word starts in the first family still studied",
        )
    }

    @Test
    fun theLastFormatCannotBeSwitchedOff() {
        val single = StudyFormatSelection(setOf(StudyFormat.LISTEN))

        assertNull(single.with(StudyFormat.LISTEN, enabled = false))
        assertEquals(
            setOf(StudyFormat.LISTEN, StudyFormat.FILL_THE_GAP),
            single.with(StudyFormat.FILL_THE_GAP, enabled = true)?.enabled,
        )
        assertFailsWith<IllegalArgumentException> { StudyFormatSelection(emptySet()) }
    }

    @Test
    fun siblingCreditStaysDirectWhileUnlockCreditChainsTheSkippedSteps() {
        val config = FsrsDefaults.config()
        val recognizeToProduce = config.crossFamilyCreditFactor(
            CardFamily.RECOGNIZE_SENSE,
            CardFamily.PRODUCE_WORD,
            Rating.GOOD,
        )
        val produceToContext = config.crossFamilyCreditFactor(
            CardFamily.PRODUCE_WORD,
            CardFamily.PRODUCE_WORD_IN_CONTEXT,
            Rating.GOOD,
        )

        assertNull(
            config.crossFamilyCreditFactor(CardFamily.RECOGNIZE_SENSE, CardFamily.PRODUCE_WORD_IN_CONTEXT, Rating.GOOD),
            "A pair without a configured credit passes nothing to an existing sibling",
        )
        assertEquals(
            recognizeToProduce!! * produceToContext!!,
            config.ladderCreditFactor(CardFamily.RECOGNIZE_SENSE, CardFamily.PRODUCE_WORD_IN_CONTEXT, Rating.GOOD)!!,
            absoluteTolerance = 1e-9,
            message = "Unlocking over a skipped family inherits no more than climbing through it would",
        )
        assertEquals(
            recognizeToProduce,
            config.ladderCreditFactor(CardFamily.RECOGNIZE_SENSE, CardFamily.PRODUCE_WORD, Rating.GOOD),
            "An adjacent step is its own credit",
        )
        assertNull(
            config.ladderCreditFactor(CardFamily.PRODUCE_WORD, CardFamily.RECOGNIZE_SENSE, Rating.GOOD),
            "The ladder only climbs",
        )
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
    fun unlockingOverSkippedFamiliesTakesTheStrictestGate() {
        val config = FsrsDefaults.config()

        assertEquals(
            config.productionUnlockStability,
            config.unlockStabilityGate(CardFamily.RECOGNIZE_SENSE, CardFamily.PRODUCE_WORD),
        )
        assertEquals(
            maxOf(config.productionUnlockStability, config.contextUnlockStability),
            config.unlockStabilityGate(CardFamily.RECOGNIZE_SENSE, CardFamily.PRODUCE_WORD_IN_CONTEXT),
            "Stepping over production must not reach context sooner than the ladder would",
        )
        assertEquals(
            config.contextUnlockStability,
            config.unlockStabilityGate(CardFamily.PRODUCE_WORD, CardFamily.RECOGNIZE_VOICE),
        )
        assertNull(config.unlockStabilityGate(CardFamily.RECOGNIZE_VOICE, CardFamily.RECOGNIZE_SENSE))
    }
}
