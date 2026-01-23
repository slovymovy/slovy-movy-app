package com.slovy.slovymovyapp.ingestion

import com.slovy.slovymovyapp.util.lowercaseInvariant
import com.slovy.slovymovyapp.util.stripAccents
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Cross-platform tests for stripAccents() function.
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
    fun locale_invariant_lowercase() {
        // Turkish 'I' should lowercase to 'i' in ROOT locale, not 'ı'
        assertEquals("i", "I".lowercaseInvariant(), "Turkish I should lowercase to dotless i in ROOT locale")
        assertEquals("i", stripAccents("I"), "stripAccents should lowercase I to i regardless of locale")
    }
}
