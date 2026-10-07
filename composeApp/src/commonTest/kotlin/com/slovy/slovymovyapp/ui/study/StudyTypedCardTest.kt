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
        back = StudyCardBackUiState(headline = "gezellig", isLemmaHeadline = true),
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
    fun checked_insertedLetters_markTheAttemptNotTheLemma() {
        val card = card().copy(lemma = "fiets").withInput(typed("fieeeeets")).checked()!!
        val result = assertIs<StudyTypedResultUiState.Incorrect>(card.result)
        assertEquals(emptySet(), result.missedPositions)
        assertEquals(setOf(3, 4, 5, 6), result.extraPositions)
    }
}
