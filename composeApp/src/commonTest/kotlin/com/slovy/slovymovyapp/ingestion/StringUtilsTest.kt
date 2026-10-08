package com.slovy.slovymovyapp.ingestion

import com.slovy.slovymovyapp.util.legacySharpSSpellings
import com.slovy.slovymovyapp.util.normalizeApostrophes
import com.slovy.slovymovyapp.util.normalizeForLookup
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
        assertEquals("creme brulee", stripAccents("Crème Brûlée"), "Common French accents should be stripped")
        assertEquals("oeuvre", stripAccents("Œuvre"), "œ ligature should map to oe")
        assertEquals("facade", stripAccents("façade"), "ç should unaccent to c")
    }

    @Test
    fun sharp_s_folds_to_ss() {
        assertEquals("grossess", stripAccents("GroßeSS"), "ß folds to ss, like a typed ss")
        assertEquals("strasse", stripAccents("STRAẞE"), "capital ẞ lowercases to ß and folds to ss")
        assertEquals(stripAccents("Strasse"), stripAccents("Straße"), "both spellings normalize alike")
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
    fun legacy_sharp_s_spellings_expand_each_ss() {
        assertEquals(listOf("haus"), legacySharpSSpellings("haus"), "A query without ss has no other spelling")
        assertEquals(listOf("gross", "groß"), legacySharpSSpellings("gross"), "ss also tries ß, query first")
        assertEquals(
            listOf("schlossstrasse", "schloßstrasse", "schlossstraße", "schloßstraße"),
            legacySharpSSpellings("schlossstrasse"),
            "Each non-overlapping ss is expanded independently"
        )
        assertEquals(
            8,
            legacySharpSSpellings("ssassassassa").size,
            "Expansion stops after three occurrences to bound the number of lookups"
        )
    }

    @Test
    fun normalizeApostrophes_maps_typographic_variants_to_ascii() {
        assertEquals("don't", normalizeApostrophes("don’t"), "Right single quote becomes ASCII")
        assertEquals("'quote'", normalizeApostrophes("‘quote’"), "Left and right single quotes become ASCII")
        assertEquals("don't", normalizeApostrophes("donʼt"), "Modifier letter apostrophe becomes ASCII")
        assertEquals("plain", normalizeApostrophes("plain"), "Text without apostrophes is unchanged")
    }

    @Test
    fun normalizeForLookup_strips_accents_and_unifies_apostrophes() {
        assertEquals("о'коннор", normalizeForLookup("О’Ко́ннор"), "Cyrillic is only lowercased; the stress mark and apostrophe are normalized")
        assertEquals("l'ete", normalizeForLookup("L’Été"), "Accents are stripped and the apostrophe is ASCII")
        assertEquals(stripAccents("can't"), normalizeForLookup("can’t"), "Curly and ASCII spellings share one lookup key")
    }
}
