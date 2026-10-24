package com.slovy.slovymovyapp.share

import com.slovy.slovymovyapp.data.Language
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SharedTextDispatcherTest {

    @Test
    fun saved_search_language_wins_when_installed() {
        assertEquals(
            Language.GERMAN,
            SharedTextDispatcher.pickLanguage(Language.GERMAN, Language.DUTCH, listOf(Language.DUTCH, Language.GERMAN)),
            "the language Search is set to must be used, like the Search screen does",
        )
    }

    @Test
    fun falls_back_to_active_then_any_installed_language() {
        assertEquals(
            Language.DUTCH,
            SharedTextDispatcher.pickLanguage(Language.GERMAN, Language.DUTCH, listOf(Language.ENGLISH, Language.DUTCH)),
            "a saved language without its dictionary must yield to the active learning language",
        )
        assertEquals(
            Language.ENGLISH,
            SharedTextDispatcher.pickLanguage(Language.GERMAN, Language.DUTCH, listOf(Language.ENGLISH)),
            "with neither installed, any installed dictionary must be used",
        )
    }

    @Test
    fun no_installed_dictionary_has_no_language() {
        assertNull(
            SharedTextDispatcher.pickLanguage(Language.GERMAN, Language.DUTCH, emptyList()),
            "without an installed dictionary a share has no language to open in",
        )
    }
}
