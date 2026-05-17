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
}
