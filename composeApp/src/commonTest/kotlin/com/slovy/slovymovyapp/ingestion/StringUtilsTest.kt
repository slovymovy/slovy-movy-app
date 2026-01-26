package com.slovy.slovymovyapp.ingestion

import com.slovy.slovymovyapp.util.normalizeSearchQuery
import com.slovy.slovymovyapp.util.stripAccents
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Cross-platform tests for stripAccents() and normalizeSearchQuery() functions.
 * These tests verify deterministic behavior across Android, iOS, and Desktop.
 */
class StringUtilsTest {

    @Test
    fun latin_unaccent_and_lowercase() {
        assertEquals("cafe", stripAccents("Café"), "Should remove accent and lowercase")
        assertEquals("naive", stripAccents("naïve"), "Should strip diaeresis")
        assertEquals("aero", stripAccents("Ærø"), "æ->ae, ø->o, and lowercase")
        assertEquals("großess", stripAccents("GroßeSS"), "No special transliteration for ß; only lowercase")
        assertEquals("creme brulee", stripAccents("Crème Brûlée"), "Common French accents should be stripped")
        assertEquals("oeuvre", stripAccents("Œuvre"), "œ ligature should map to oe")
        assertEquals("facade", stripAccents("façade"), "ç should unaccent to c")
    }

    @Test
    fun polish_specifics() {
        assertEquals("kamien", stripAccents("KAMIEŃ"), "Polish ń should unaccent to n")
        assertEquals("zolc", stripAccents("Żółć"), "Transliterate ł->l; strip accents; lowercase")
        assertEquals("lody", stripAccents("Łody"), "Ł -> l")
    }

    @Test
    fun cyrillic_should_remain_lowercased_only() {
        assertEquals("программа", stripAccents("Программа"), "Cyrillic should not be transliterated, only lowercased")
        assertEquals("еж", stripAccents("Ёж"), "Cyrillic should not be transliterated, only lowercased")
    }

    @Test
    fun normalizeSearchQuery_converts_apostrophe_variants_to_typographic() {
        // ASCII apostrophe (U+0027) -> typographic (U+2019)
        assertEquals("zo\u2019n", normalizeSearchQuery("zo'n"), "ASCII apostrophe should convert to typographic")
        assertEquals("it\u2019s", normalizeSearchQuery("it's"), "ASCII apostrophe should convert to typographic")

        // Left single quote (U+2018) -> typographic (U+2019)
        assertEquals("zo\u2019n", normalizeSearchQuery("zo\u2018n"), "Left single quote should convert to typographic")

        // Modifier letter apostrophe (U+02BC) -> typographic (U+2019)
        assertEquals("zo\u2019n", normalizeSearchQuery("zo\u02BCn"), "Modifier letter apostrophe should convert to typographic")

        // Backtick (U+0060) -> typographic (U+2019)
        assertEquals("zo\u2019n", normalizeSearchQuery("zo`n"), "Backtick should convert to typographic")

        // Typographic apostrophe (U+2019) passthrough - already correct
        assertEquals("zo\u2019n", normalizeSearchQuery("zo\u2019n"), "Typographic apostrophe should pass through unchanged")

        // No apostrophe - unchanged
        assertEquals("no apostrophe", normalizeSearchQuery("no apostrophe"), "Text without apostrophe unchanged")
    }
}
