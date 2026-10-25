package com.slovy.slovymovyapp.ui.study

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.slovy.slovymovyapp.i18n.UiText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StudyTypedCardTest {

    private fun card() = StudyCardUiState.Typed(
        id = "typed",
        chipLabel = UiText.Plain("Type"),
        promptText = "cosy, sociable",
        partOfSpeech = null,
        lemma = "gezellig",
        languageCode = "nl",
        back = StudyCardBackUiState(
            headline = "gezellig",
            isLemmaHeadline = true,
            synonyms = listOf(StudySynonymUiState("knus", known = true), StudySynonymUiState("prettig")),
        ),
    )

    private fun typed(text: String) = TextFieldValue(text, TextRange(text.length))

    @Test
    fun freshCard_cannotCheck_canHint_noSlots() {
        val card = card()
        assertFalse(card.canCheck, "Nothing typed")
        assertTrue(card.canHint)
        assertEquals(0, card.remainingSlots, "Slots appear only after the first hint")
        assertNull(card.checked(), "Check is a no-op while disabled")
    }

    @Test
    fun hint_thenTyping_keepsHintMarksAndShowsSlots() {
        val hinted = card().withNextLetterHint()!!.withNextLetterHint()!!
        assertEquals("ge", hinted.input.value.text)
        assertEquals(TextRange(2), hinted.input.value.selection, "The caret follows the revealed letter")
        assertEquals(setOf(0, 1), hinted.input.hintedPositions)
        assertEquals(2, hinted.input.hintsUsed)
        assertFalse(hinted.canCheck, "Hint letters alone do not enable Check")

        val typing = hinted.withInput(typed("gezel"))
        assertTrue(typing.canCheck)
        assertEquals(setOf(0, 1), typing.input.hintedPositions, "Hints survive typing after them")
        assertEquals(3, typing.remainingSlots, "gezel leaves three of eight letters")
    }

    @Test
    fun hint_afterWrongTail_truncatesAndHintsNext() {
        val card = card().withInput(typed("gezal")).withNextLetterHint()!!
        assertEquals("geze", card.input.value.text)
        assertEquals(setOf(3), card.input.hintedPositions, "Only the revealed e is a hint; gez was typed")
    }

    @Test
    fun hint_revealsTheLastLetter_thenStops() {
        val completed = card().withInput(typed("gezelli")).withNextLetterHint()!!
        assertEquals("gezellig", completed.input.value.text)
        assertFalse(completed.canHint, "Nothing left to reveal")
        assertNull(completed.withNextLetterHint())
        assertTrue(completed.canCheck, "Seven typed letters plus one hint is the learner's answer")
        val allHinted = (1..8).fold(card()) { c, _ -> c.withNextLetterHint()!! }
        assertEquals("gezellig", allHinted.input.value.text)
        assertFalse(allHinted.canCheck, "A word spelled entirely by hints is not the learner's input")
    }

    @Test
    fun cuttingAWrongTail_isNotCountedAsAHint() {
        val card = card().withInput(typed("gezelligxx")).withNextLetterHint()!!
        assertEquals("gezellig", card.input.value.text, "The wrong tail is cut")
        assertEquals(0, card.input.hintsUsed, "No letter was revealed, so no hint was used")
        assertEquals(0, card.remainingSlots, "Without a hint used there are no length slots")
    }

    @Test
    fun cuttingATailAfterALeadingSpace_isNotCountedAsAHintEither() {
        // A surviving hint shifts left when the leading space goes; that must not read as a new hint.
        val card = card().copy(lemma = "aardvark")
            .withNextLetterHint()!!
            .withInput(typed(" aa"))
            .withInput(typed(" aardvarkx"))
            .withNextLetterHint()!!
        assertEquals("aardvark", card.input.value.text, "The space and the tail are gone")
        assertEquals(1, card.input.hintsUsed, "Only the first hint revealed a letter")
    }

    @Test
    fun deletingAHintLetter_dropsItsMark() {
        val card = card().withNextLetterHint()!!.withNextLetterHint()!!.withInput(typed("g"))
        assertEquals(setOf(0), card.input.hintedPositions)
    }

    @Test
    fun checked_exact_recordsCorrectWithHints() {
        val card = card().withNextLetterHint()!!.withInput(typed("Gezellig ")).checked()!!
        val result = assertIs<StudyTypedResultUiState.Correct>(card.result)
        assertEquals(setOf(0), result.hintedPositions)
    }

    @Test
    fun checked_typo_recordsAttemptAsTypedAndMissedLetters() {
        val card = card().withInput(typed(" gezelig")).checked()!!
        val result = assertIs<StudyTypedResultUiState.Incorrect>(card.result)
        assertEquals("gezelig", result.attempt, "The attempt is trimmed, otherwise untouched")
        assertEquals(setOf(5), result.missedPositions)
    }

    @Test
    fun answerShown_emptyField_recordsShown() {
        assertEquals(StudyTypedResultUiState.Shown, card().answerShown().result)
        assertEquals(StudyTypedResultUiState.Shown, card().withInput(typed("  ")).answerShown().result, "Spaces count as empty")
    }

    @Test
    fun answerShown_withAnAttempt_keepsTheComparison() {
        val result = assertIs<StudyTypedResultUiState.Incorrect>(card().withInput(typed("gezelig")).answerShown().result)
        assertEquals("gezelig", result.attempt, "What was typed is not thrown away")
        assertEquals(setOf(5), result.missedPositions)
    }

    @Test
    fun answerShown_withOnlyHintLetters_showsTheWordAlone() {
        val result = card().withNextLetterHint()!!.answerShown().result
        assertEquals(StudyTypedResultUiState.Shown, result, "Hint letters are not the learner's attempt, as for Check")
    }

    @Test
    fun checked_unrelatedWord_isFlaggedUnrelatedWithTruthfulPositions() {
        val card = card().copy(lemma = "fiets").withInput(typed("auto")).checked()!!
        val result = assertIs<StudyTypedResultUiState.Incorrect>(card.result)
        assertEquals("auto", result.attempt, "The attempt is still shown")
        assertFalse(result.related, "One coincidental letter does not make the words related")
        assertEquals(setOf(0, 1, 2, 4), result.missedPositions, "The positions still describe the real alignment")
    }

    @Test
    fun checked_correctPrefix_isRelated() {
        val result = assertIs<StudyTypedResultUiState.Incorrect>(card().withInput(typed("gez")).checked()!!.result)
        assertTrue(result.related, "Three correct letters of a word are a start, not a different word")
        assertEquals((3..7).toSet(), result.missedPositions, "The rest of the lemma is what was missed")
        assertEquals(emptySet(), result.extraPositions, "Nothing typed was wrong")
    }

    @Test
    fun checked_twoLettersInCommon_isNotRelated() {
        val result = assertIs<StudyTypedResultUiState.Incorrect>(
            card().withNextLetterHint()!!.withInput(typed("gaz")).checked()!!.result,
        )
        assertFalse(result.related, "Two letters in common are too few to call it a version of the word")
    }

    @Test
    fun checked_synonymSharingItsFirstLetterWithAHint_isStillASynonym() {
        val card = card().copy(
            back = StudyCardBackUiState(headline = "gezellig", isLemmaHeadline = true, synonyms = listOf(StudySynonymUiState("gemoedelijk"))),
        )
        val result = card.withNextLetterHint()!!.withInput(typed("gemoedelijk")).checked()!!.result
        assertIs<StudyTypedResultUiState.Synonym>(result, "One hinted g out of eleven letters does not make the synonym a hint product")
    }

    @Test
    fun checked_synonym_isRecognised() {
        val card = card().withInput(typed(" Knus")).checked()!!
        val result = assertIs<StudyTypedResultUiState.Synonym>(card.result)
        assertEquals("Knus", result.attempt, "The synonym is shown as typed, trimmed")
    }

    @Test
    fun checked_nearMissOfASynonym_isComparedWithTheLemma() {
        val card = card().withInput(typed("knuss")).checked()!!
        assertIs<StudyTypedResultUiState.Incorrect>(card.result, "Only an exact synonym counts; otherwise the lemma is the reference")
    }

    @Test
    fun answerShown_withASynonymTyped_recognisesIt() {
        assertIs<StudyTypedResultUiState.Synonym>(
            card().withInput(typed("prettig")).answerShown().result,
            "Giving up with a synonym in the field still recognises it",
        )
    }

    @Test
    fun checked_synonymAssembledFromHints_isNotASynonym() {
        // Lemma "automobile", synonym "auto": three hinted letters and one typed one spell the synonym.
        val card = card().copy(
            lemma = "automobile",
            back = StudyCardBackUiState(headline = "automobile", isLemmaHeadline = true, synonyms = listOf(StudySynonymUiState("auto"))),
        )
        val hinted = (1..3).fold(card) { c, _ -> c.withNextLetterHint()!! }.withInput(typed("auto"))
        val result = assertIs<StudyTypedResultUiState.Incorrect>(hinted.checked()!!.result, "Hint letters are the lemma's, not a recalled synonym")
        assertTrue(result.related, "auto is the start of automobile")
        assertEquals(emptySet(), result.extraPositions, "The hinted letters are not struck through")
    }

    @Test
    fun checked_nearMiss_isRelatedAndKeepsItsMarks() {
        val card = card().withInput(typed("gezelig")).checked()!!
        val result = assertIs<StudyTypedResultUiState.Incorrect>(card.result)
        assertTrue(result.related, "A dropped letter is a near miss")
        assertEquals(setOf(5), result.missedPositions, "A near miss still points at the dropped letter")
    }

    @Test
    fun checked_insertedLetters_markTheAttemptNotTheLemma() {
        val card = card().copy(lemma = "fiets").withInput(typed("fieeeeets")).checked()!!
        val result = assertIs<StudyTypedResultUiState.Incorrect>(card.result)
        assertEquals(emptySet(), result.missedPositions)
        assertEquals(setOf(3, 4, 5, 6), result.extraPositions)
    }
}
