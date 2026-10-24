package com.slovy.slovymovyapp.share

import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.ui.reader.MAX_CHARS
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class SharedTextTest {

    private fun route(text: String) = SharedTextReceiver.route(text, Language.DUTCH, serial = 7)

    @Test
    fun offer_ignores_blank_text() = runBlocking {
        val receiver = SharedTextReceiver()
        receiver.offer("   ", SharedTextSource.PROCESS_TEXT)
        receiver.offer("woord", SharedTextSource.PROCESS_TEXT)
        assertEquals("woord", receiver.requests.first().text, "blank text must not become a request")
    }

    @Test
    fun identical_texts_produce_distinct_requests() = runBlocking {
        val receiver = SharedTextReceiver()
        receiver.offer("woord", SharedTextSource.PROCESS_TEXT)
        val first = receiver.requests.first()
        receiver.offer("woord", SharedTextSource.PROCESS_TEXT)
        val second = receiver.requests.first()
        assertNotEquals(first.serial, second.serial, "a repeated share of the same text must get its own serial")
    }

    @Test
    fun undelivered_request_is_replaced_by_a_later_one() = runBlocking {
        val receiver = SharedTextReceiver()
        receiver.offer("eerste", SharedTextSource.SHARE)
        receiver.offer("tweede", SharedTextSource.SHARE)
        assertEquals("tweede", receiver.requests.first().text, "only the latest undelivered share must be kept")
    }

    @Test
    fun saved_search_language_wins_when_installed() {
        assertEquals(
            Language.GERMAN,
            SharedTextReceiver.pickLanguage(Language.GERMAN, Language.DUTCH, listOf(Language.DUTCH, Language.GERMAN)),
            "the language Search is set to must be used, like the Search screen does",
        )
    }

    @Test
    fun language_falls_back_to_active_then_any_installed() {
        assertEquals(
            Language.DUTCH,
            SharedTextReceiver.pickLanguage(Language.GERMAN, Language.DUTCH, listOf(Language.ENGLISH, Language.DUTCH)),
            "a saved language without its dictionary must yield to the active learning language",
        )
        assertEquals(
            Language.ENGLISH,
            SharedTextReceiver.pickLanguage(Language.GERMAN, Language.DUTCH, listOf(Language.ENGLISH)),
            "with neither installed, any installed dictionary must be used",
        )
        assertNull(
            SharedTextReceiver.pickLanguage(Language.GERMAN, Language.DUTCH, emptyList()),
            "without an installed dictionary a share has no language to open in",
        )
    }

    @Test
    fun blank_text_has_no_route() {
        assertNull(route(""), "empty text must not route anywhere")
        assertNull(route(" \n\t "), "whitespace-only text must not route anywhere")
    }

    @Test
    fun single_word_goes_to_search_without_surrounding_punctuation() {
        assertEquals(SharedTextRoute.Search("gezellig"), route("  \"gezellig,\" "))
    }

    @Test
    fun hyphenated_and_apostrophe_words_count_as_one() {
        assertEquals(SharedTextRoute.Search("arm-rijk"), route("arm-rijk"))
        assertEquals(SharedTextRoute.Search("don’t"), route("don’t"))
    }

    @Test
    fun several_words_go_to_reader_with_trimmed_text() {
        assertEquals(SharedTextRoute.Reader("New\n  York.", Language.DUTCH, 7), route("  New\n  York.\n"))
        assertEquals(SharedTextRoute.Reader("De stad was stil.", Language.DUTCH, 7), route("  De stad was stil.\n"))
    }

    @Test
    fun text_without_words_has_no_route() {
        assertNull(route("42 \n %"), "digits and symbols carry nothing to look up")
        assertNull(route("😀"), "an emoji carries nothing to look up")
    }

    @Test
    fun oversized_text_goes_to_reader_without_tokenizing() {
        val text = "x".repeat(MAX_CHARS + 1)
        val reader = assertIs<SharedTextRoute.Reader>(route(text), "text over the reader limit must still open the reader")
        assertEquals(text, reader.text)
    }

    @Test
    fun word_count_matches_reader_tokenization() {
        assertEquals(4, SharedTextReceiver.wordCount("De stad, was stil."))
        assertEquals(0, SharedTextReceiver.wordCount("42 %"))
        assertNull(
            SharedTextReceiver.wordCount("x ".repeat(MAX_CHARS)),
            "text over the reader limit must not be tokenized for analytics",
        )
    }
}
