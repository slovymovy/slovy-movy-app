package com.slovy.slovymovyapp.share

import com.slovy.slovymovyapp.ui.reader.MAX_CHARS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class SharedTextRouterTest {

    @Test
    fun blank_text_has_no_route() {
        assertNull(SharedTextRouter.route(""), "empty text must not route anywhere")
        assertNull(SharedTextRouter.route(" \n\t "), "whitespace-only text must not route anywhere")
    }

    @Test
    fun single_word_goes_to_search_without_surrounding_punctuation() {
        val route = SharedTextRouter.route("  \"gezellig,\" ")
        assertEquals(SharedTextRoute.Search("gezellig"), route)
    }

    @Test
    fun two_words_go_to_reader_with_trimmed_text() {
        val route = SharedTextRouter.route("  New\n  York.\n")
        assertEquals(SharedTextRoute.Reader("New\n  York."), route)
    }

    @Test
    fun hyphenated_and_apostrophe_words_count_as_one() {
        assertEquals(SharedTextRoute.Search("arm-rijk"), SharedTextRouter.route("arm-rijk"))
        assertEquals(SharedTextRoute.Search("don’t"), SharedTextRouter.route("don’t"))
    }

    @Test
    fun sentence_goes_to_reader_with_trimmed_text() {
        val route = SharedTextRouter.route("  De stad was stil.\n")
        assertEquals(SharedTextRoute.Reader("De stad was stil."), route)
    }

    @Test
    fun text_without_words_has_no_route() {
        assertNull(SharedTextRouter.route("42 \n %"), "digits and symbols carry nothing to look up")
        assertNull(SharedTextRouter.route("😀"), "an emoji carries nothing to look up")
    }

    @Test
    fun oversized_text_goes_to_reader_without_tokenizing() {
        val text = "x".repeat(MAX_CHARS + 1)
        val route = SharedTextRouter.route(text)
        assertIs<SharedTextRoute.Reader>(route, "text over the reader limit must still open the reader")
        assertEquals(text, route.text)
    }

    @Test
    fun word_count_matches_reader_tokenization() {
        assertEquals(4, SharedTextRouter.wordCount("De stad, was stil."))
        assertEquals(0, SharedTextRouter.wordCount("42 %"))
    }

    @Test
    fun word_count_skips_oversized_text() {
        assertNull(
            SharedTextRouter.wordCount("x ".repeat(MAX_CHARS)),
            "text over the reader limit must not be tokenized for analytics",
        )
    }
}
