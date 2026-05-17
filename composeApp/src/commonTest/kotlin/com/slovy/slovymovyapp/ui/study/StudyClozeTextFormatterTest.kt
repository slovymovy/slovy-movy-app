package com.slovy.slovymovyapp.ui.study

import kotlin.test.Test
import kotlin.test.assertEquals

class StudyClozeTextFormatterTest {

    @Test
    fun frontSideReplacesEveryAnswerRangeWithBlank() {
        val state = StudyClozeTextUiState(
            text = "Vergeet niet om je paspoort mee te nemen.",
            answerRanges = listOf(28..30, 35..39),
        )

        val display = state.toDisplayText()

        assertEquals("Vergeet niet om je paspoort \u00A0\u00A0\u00A0\u00A0\u00A0\u00A0 te \u00A0\u00A0\u00A0\u00A0\u00A0\u00A0.", display.text)
        assertEquals(listOf(28..33, 38..43), display.answerRanges)
    }

    @Test
    fun backSideHighlightsEveryAnswerRange() {
        val state = StudyClozeTextUiState(
            text = "Vergeet niet om je paspoort mee te nemen.",
            answerRanges = listOf(28..30, 35..39),
            filled = true,
        )

        val display = state.toDisplayText()

        assertEquals("Vergeet niet om je paspoort mee te nemen.", display.text)
        assertEquals(listOf(28..30, 35..39), display.answerRanges)
    }

    @Test
    fun backSideStripsNestedTagsFromAnswerRange() {
        val state = StudyClozeTextUiState(
            text = "take <w>away</w> today.",
            answerRanges = listOf(0..15),
            filled = true,
        )

        val display = state.toDisplayText()

        assertEquals("take away today.", display.text)
        assertEquals(listOf(0..8), display.answerRanges)
    }

    @Test
    fun frontSideUsesPlainAnswerLengthForNestedTags() {
        val state = StudyClozeTextUiState(
            text = "take <w>away</w> today.",
            answerRanges = listOf(0..15),
        )

        val display = state.toDisplayText()

        assertEquals("\u00A0\u00A0\u00A0\u00A0\u00A0\u00A0\u00A0\u00A0\u00A0 today.", display.text)
        assertEquals(listOf(0..8), display.answerRanges)
    }
}
