package com.slovy.slovymovyapp.data.learning.spelling

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SpellingCheckerTest {

    private val lemma = "gezellig"

    @Test
    fun normalize_trimsCollapsesAndLowercases() {
        assertEquals("ik ben", SpellingChecker.normalize("  Ik   BEN \n"), "Normalisation must trim, collapse spaces and lowercase")
    }

    @Test
    fun isCorrect_ignoresCaseAndSurroundingSpace() {
        assertTrue(SpellingChecker.isCorrect(" Gezellig ", lemma), "Case and padding must not count as a mistake")
    }

    @Test
    fun isCorrect_accentOnlyMismatchIsNotCorrect() {
        assertFalse(SpellingChecker.isCorrect("cafe", "café"), "An accent-only mismatch is a typo, not a correct answer")
    }

    @Test
    fun matchingPrefixLength_stopsAtFirstDifference() {
        assertEquals(3, SpellingChecker.matchingPrefixLength("gezal", lemma), "gezal shares gez with gezellig")
        assertEquals(0, SpellingChecker.matchingPrefixLength("hez", lemma), "hez shares nothing with gezellig")
        assertEquals(2, SpellingChecker.matchingPrefixLength("GE", lemma), "Prefix matching ignores case")
    }

    @Test
    fun applyHint_specExamples() {
        assertEquals("g", SpellingChecker.applyHint("", lemma, emptySet())?.value, "'' -> g")
        assertEquals("geze", SpellingChecker.applyHint("gez", lemma, emptySet())?.value, "gez -> geze")
        assertEquals("geze", SpellingChecker.applyHint("gezal", lemma, emptySet())?.value, "gezal -> geze")
        assertEquals("g", SpellingChecker.applyHint("hez", lemma, emptySet())?.value, "hez -> g")
    }

    @Test
    fun applyHint_marksOnlyRevealedLetters() {
        val first = SpellingChecker.applyHint("", lemma, emptySet())!!
        assertEquals(setOf(0), first.hintedPositions, "The first hint reveals position 0")
        val second = SpellingChecker.applyHint(first.value, lemma, first.hintedPositions)!!
        assertEquals("ge", second.value)
        assertEquals(setOf(0, 1), second.hintedPositions, "A second hint adds position 1 and keeps position 0")
        val afterTyping = SpellingChecker.applyHint("gezal", lemma, second.hintedPositions)!!
        assertEquals("geze", afterTyping.value)
        assertEquals(setOf(0, 1, 3), afterTyping.hintedPositions, "Typed letters inside the kept prefix stay unmarked")
    }

    @Test
    fun applyHint_dropsHintsBeyondTheKeptPrefix() {
        val hint = SpellingChecker.applyHint("hez", lemma, setOf(0, 1))!!
        assertEquals(setOf(0), hint.hintedPositions, "Hints after a wrong prefix are gone with the truncated text")
    }

    @Test
    fun applyHint_keepsTheLearnersOwnCharacters() {
        assertEquals("Geze", SpellingChecker.applyHint("Gez", lemma, emptySet())?.value, "The kept prefix is the learner's text as typed")
    }

    @Test
    fun canHint_untilTheWordIsComplete() {
        assertTrue(SpellingChecker.canHint("gezell", lemma), "Two letters remain")
        assertTrue(SpellingChecker.canHint("gezelli", lemma), "The last letter may be hinted too")
        assertEquals("gezellig", SpellingChecker.applyHint("gezelli", lemma, emptySet())?.value)
        assertFalse(SpellingChecker.canHint(lemma, lemma), "Nothing remains")
        assertNull(SpellingChecker.applyHint(lemma, lemma, emptySet()), "No hint once the word is complete")
        assertFalse(SpellingChecker.canHint("Gezellig", lemma), "Case does not matter for completeness")
    }

    @Test
    fun hintedPositionsAfterEdit_keepsOnlyIntactHints() {
        assertEquals(setOf(0), SpellingChecker.hintedPositionsAfterEdit("g", lemma, setOf(0, 1)), "A deleted hint letter is no longer a hint")
        assertEquals(setOf(1), SpellingChecker.hintedPositionsAfterEdit("xe", lemma, setOf(0, 1)), "A hint letter typed over is no longer a hint")
        assertEquals(setOf(0, 1), SpellingChecker.hintedPositionsAfterEdit("gezel", lemma, setOf(0, 1)), "Typing after the hints keeps them")
    }

    @Test
    fun hasLearnerInput_ignoresHintsAndWhitespace() {
        assertFalse(SpellingChecker.hasLearnerInput("", emptySet()), "Empty field")
        assertFalse(SpellingChecker.hasLearnerInput("ge", setOf(0, 1)), "Only hint letters")
        assertFalse(SpellingChecker.hasLearnerInput("  ", emptySet()), "Only spaces")
        assertTrue(SpellingChecker.hasLearnerInput("gez", setOf(0, 1)), "One typed letter after two hints")
    }

    @Test
    fun diff_exactAnswerHasNoMarks() {
        val diff = SpellingChecker.diff("Gezellig", lemma)
        assertEquals(emptySet(), diff.missed, "An exact answer marks nothing")
        assertEquals(emptySet(), diff.extra)
        assertEquals(emptySet(), diff.accentOnly)
    }

    @Test
    fun diff_droppedLetterIsMissed() {
        assertEquals(setOf(5), SpellingChecker.diff("gezelig", lemma).missed, "gezelig misses one of the two l's")
    }

    @Test
    fun diff_changedLetterIsMissedAndItsReplacementIsExtra() {
        val diff = SpellingChecker.diff("gezallig", lemma)
        assertEquals(setOf(3), diff.missed, "gezallig changes the e at position 3")
        assertEquals(setOf(3), diff.extra, "The a that replaced it has no place in the lemma")
    }

    @Test
    fun diff_insertedLettersAreExtraAndTheLemmaIsClean() {
        val diff = SpellingChecker.diff("fieeeeets", "fiets")
        assertEquals(emptySet(), diff.missed, "Every lemma letter is present")
        assertEquals(setOf(3, 4, 5, 6), diff.extra, "The four surplus e's are marked on the attempt")
    }

    @Test
    fun diff_accentOnlyMismatchIsAccentOnlyNotMissed() {
        val diff = SpellingChecker.diff("cafe", "café")
        assertEquals(setOf(3), diff.accentOnly, "The é was typed as e")
        assertEquals(emptySet(), diff.missed)
        assertEquals(emptySet(), diff.extra, "The unaccented e is not surplus")
    }

    @Test
    fun diff_droppedAccentedLetter_marksThatLetterNotItsNeighbour() {
        val diff = SpellingChecker.diff("crée", "créée")
        assertEquals(setOf(3), diff.missed, "One é is missing; the final e the learner typed is not")
        assertEquals(emptySet(), diff.accentOnly, "The é that was typed is exact")
        assertEquals(emptySet(), diff.extra)
    }

    @Test
    fun diff_foldsCasePerCharacter() {
        val diff = SpellingChecker.diff("istanbul", "İstanbul")
        assertEquals(emptySet(), diff.missed, "Dotted capital I folds to a single i, so nothing is missed")
        assertEquals(emptySet(), diff.extra)
    }

    @Test
    fun diff_unrelatedAttemptMarksEverything() {
        val diff = SpellingChecker.diff("quok", lemma)
        assertEquals((0 until lemma.length).toSet(), diff.missed, "Nothing matched, so every lemma letter is marked")
        assertEquals(setOf(0, 1, 2, 3), diff.extra)
    }

    @Test
    fun diff_extraPositionsIndexTheTrimmedAttempt() {
        assertEquals(setOf(5), SpellingChecker.diff("  fietss ", "fiets").extra, "Positions ignore the padding")
    }
}
