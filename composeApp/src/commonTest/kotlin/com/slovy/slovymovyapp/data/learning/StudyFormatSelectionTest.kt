package com.slovy.slovymovyapp.data.learning

import com.slovy.slovymovyapp.data.learning.fsrs.FsrsDefaults
import com.slovy.slovymovyapp.data.learning.fsrs.crossFamilyCreditFactor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StudyFormatSelectionTest {

    @Test
    fun everyKindMapsToOneFormatAndSourceOnlyGathersTheUntranslatedKinds() {
        val sourceOnly = CardKind.entries.filter { it.format == StudyFormat.SOURCE_ONLY }
        assertEquals(
            CardKind.entries.filter { !it.requiresTranslation }.toSet(),
            sourceOnly.toSet(),
            "The source-only format is exactly the kinds that need no translation",
        )
        StudyFormat.entries.filter { it != StudyFormat.SOURCE_ONLY }.forEach { format ->
            assertEquals(1, CardKind.entries.count { it.format == format }, "One kind per translated format: $format")
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
            listOf(CardFamily.RECOGNIZE_SENSE, CardFamily.PRODUCE_WORD, CardFamily.PRODUCE_WORD_IN_CONTEXT),
            sourceOnly.families,
            "Listening has no source-only kind, so it is the one family left out",
        )
        assertEquals(setOf(CardFamily.RECOGNIZE_VOICE), sourceOnly.excludedFamilies)
        assertTrue(sourceOnly.allows(CardKind.CLOZE_SOURCE))
        assertTrue(!sourceOnly.allows(CardKind.CLOZE_TRANSLATION))
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
    fun creditToAFarFamilyChainsTheSkippedSteps() {
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
        val chained = config.crossFamilyCreditFactor(
            CardFamily.RECOGNIZE_SENSE,
            CardFamily.PRODUCE_WORD_IN_CONTEXT,
            Rating.GOOD,
        )

        assertEquals(
            recognizeToProduce!! * produceToContext!!,
            chained!!,
            absoluteTolerance = 1e-9,
            message = "Skipping a family must not grant more than climbing through it would",
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
}
