package com.slovy.slovymovyapp.ui.study

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FirstLetterHintTest {

    @Test
    fun normalWord() {
        val hint = "gezellig".firstLetterHint()!!
        assertEquals('g', hint.letter)
        assertEquals(8, hint.letterCount)
        assertEquals(7, hint.dotCount)
    }

    @Test
    fun longCompound() {
        val hint = "tentoonstelling".firstLetterHint()!!
        assertEquals('t', hint.letter)
        assertEquals(15, hint.letterCount)
        assertEquals(14, hint.dotCount)
    }

    @Test
    fun oneLetter_hasNoHiddenLetterDots() {
        val hint = "I".firstLetterHint()!!
        assertEquals('I', hint.letter)
        assertEquals(1, hint.letterCount)
        assertEquals(0, hint.dotCount)
    }

    @Test
    fun twoLetters_hasOneHiddenLetterDot() {
        val hint = "er".firstLetterHint()!!
        assertEquals('e', hint.letter)
        assertEquals(2, hint.letterCount)
        assertEquals(1, hint.dotCount)
    }

    @Test
    fun threeLetters_hasTwoHiddenLetterDots() {
        val hint = "het".firstLetterHint()!!
        assertEquals('h', hint.letter)
        assertEquals(3, hint.letterCount)
        assertEquals(2, hint.dotCount)
    }

    @Test
    fun fourLetters_hasThreeHiddenLetterDots() {
        val hint = "vier".firstLetterHint()!!
        assertEquals('v', hint.letter)
        assertEquals(4, hint.letterCount)
        assertEquals(3, hint.dotCount)
    }

    @Test
    fun wordWithApostrophe_onlyLettersCounted() {
        val hint = "don't".firstLetterHint()!!
        assertEquals('d', hint.letter)
        assertEquals(4, hint.letterCount)
        assertEquals(3, hint.dotCount)
    }

    @Test
    fun emptyString_returnsNull() {
        assertNull("".firstLetterHint())
    }

    @Test
    fun noLetters_returnsNull() {
        assertNull("123".firstLetterHint())
    }

    @Test
    fun leadingNonLetter_firstLetterIsCorrect() {
        val hint = "1abc".firstLetterHint()!!
        assertEquals('a', hint.letter)
        assertEquals(3, hint.letterCount)
        assertEquals(2, hint.dotCount)
    }
}
