package com.slovy.slovymovyapp.ui.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderTokensTest {

    private fun words(tokens: List<TextToken>): List<String> =
        tokens.filter { it.isWord }.map { it.text }

    @Test
    fun tokenize_splits_words_whitespace_and_punctuation() {
        val tokens = tokenize("De stad, was stil.")
        assertEquals(listOf("De", "stad", "was", "stil"), words(tokens))
        assertEquals("De stad, was stil.", tokens.joinToString("") { it.text }, "tokens must reassemble the input")
        assertFalse(tokens.first { it.text == "," }.isWord, "comma must not be a word")
    }

    @Test
    fun tokenize_strips_leading_and_trailing_symbols_but_keeps_them_as_tokens() {
        val tokens = tokenize("-woord 'rijk'")
        assertEquals(listOf("woord", "rijk"), words(tokens))
        assertEquals("-woord 'rijk'", tokens.joinToString("") { it.text }, "tokens must reassemble the input")
    }

    @Test
    fun tokenize_keeps_typographic_apostrophe_inside_word() {
        val tokens = tokenize("don’t l’homme")
        assertEquals(listOf("don’t", "l’homme"), words(tokens))
    }

    @Test
    fun tokenize_trims_typographic_quotes_around_word() {
        val tokens = tokenize("‘rijk’")
        assertEquals(listOf("rijk"), words(tokens))
        assertEquals("‘rijk’", tokens.joinToString("") { it.text }, "tokens must reassemble the input")
    }

    @Test
    fun tokenize_preserves_midword_hyphen_and_ascii_apostrophe() {
        val tokens = tokenize("you're arm-rijk")
        assertEquals(listOf("you're", "arm-rijk"), words(tokens))
    }

    @Test
    fun tokenize_never_emits_empty_tokens() {
        val tokens = tokenize("a ʼʼ b -- ''")
        assertTrue(tokens.none { it.text.isEmpty() }, "no token may have empty text")
        assertEquals(listOf("a", "b"), words(tokens))
    }

    @Test
    fun normalizeApostrophes_maps_typographic_variants_to_ascii() {
        assertEquals("don't", normalizeApostrophes("don’t"))
        assertEquals("l'homme", normalizeApostrophes("l’homme"))
        assertEquals("'quote'", normalizeApostrophes("‘quote’"))
        assertEquals("don't", normalizeApostrophes("donʼt"))
        assertEquals("plain", normalizeApostrophes("plain"))
    }

    @Test
    fun groupTokensForRendering_puts_at_most_one_word_per_group() {
        val tokens = tokenize("https://example.com/some/long/path stad")
        val groups = groupTokensForRendering(tokens)
        for (group in groups) {
            assertTrue(group.count { it.isWord } <= 1, "group ${group.map { it.text }} contains more than one word")
        }
        assertEquals(tokens.joinToString("") { it.text }, groups.flatten().joinToString("") { it.text }, "grouping must preserve all tokens in order")
    }

    @Test
    fun groupTokensForRendering_keeps_word_with_adjacent_punctuation() {
        val tokens = tokenize("stad,")
        val groups = groupTokensForRendering(tokens)
        assertEquals(1, groups.size, "word plus trailing comma must stay in one group")
        assertEquals(listOf("stad", ","), groups.single().map { it.text })
    }

    @Test
    fun bandForZipf_maps_thresholds_to_bands() {
        assertEquals(FreqBand.HIGH, bandForZipf(5.0))
        assertEquals(FreqBand.MID, bandForZipf(3.5))
        assertEquals(FreqBand.LOW, bandForZipf(2.0))
    }
}
