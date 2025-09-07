package com.slovy.slovymovyapp.builder

import kotlin.test.Test
import kotlin.test.assertEquals

class UnaccentTest {

    @Test
    fun latin_unaccent_and_lowercase() {
        assertEquals("cafe", unaccent("Café"), "Should remove accent and lowercase")
        assertEquals("naive", unaccent("naïve"), "Should strip diaeresis")
        assertEquals("aero", unaccent("Ærø"), "æ->ae, ø->o, and lowercase")
        assertEquals("großess", unaccent("GroßeSS"), "No special transliteration for ß; only lowercase")
        assertEquals("creme brulee", unaccent("Crème Brûlée"), "Common French accents should be stripped")
        assertEquals("oeuvre", unaccent("Œuvre"), "œ ligature should map to oe")
        assertEquals("facade", unaccent("façade"), "ç should unaccent to c")
    }

    @Test
    fun polish_specifics() {
        assertEquals("kamien", unaccent("KAMIEŃ"), "Polish ń should unaccent to n")
        assertEquals("zolc", unaccent("Żółć"), "Transliterate ł->l; strip accents; lowercase")
        assertEquals("lody", unaccent("Łody"), "Ł -> l")
    }

    @Test
    fun cyrillic_should_remain_lowercased_only() {
        assertEquals("программа", unaccent("Программа"), "Cyrillic should not be transliterated, only lowercased")
        assertEquals("еж", unaccent("Ёж"), "Cyrillic should not be transliterated, only lowercased")
    }
}
