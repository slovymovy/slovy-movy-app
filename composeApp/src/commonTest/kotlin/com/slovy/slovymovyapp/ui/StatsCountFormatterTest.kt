package com.slovy.slovymovyapp.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StatsCountFormatterTest {
    @Test
    fun formatsCountsWithLocaleGroupingSeparator() {
        assertEquals("3,319", formatCountForLanguage(3319, "en"))
        for (language in listOf("nl", "de", "es", "it", "tr", "ru", "pl", "cs", "fr")) {
            assertGrouped(language)
        }
    }

    private fun assertGrouped(language: String) {
        val formatted = formatCountForLanguage(3319, language)
        assertEquals(5, formatted.length, "Expected 5 chars for $language, got '$formatted'")
        assertEquals('3', formatted[0], "Expected leading '3' for $language, got '$formatted'")
        assertEquals("319", formatted.substring(2), "Expected trailing '319' for $language, got '$formatted'")
        val separator = formatted[1]
        assertTrue(
            separator == ',' || separator == '.' ||
                separator == ' ' || separator == '\u00A0' ||
                separator == '\u202F' || separator == '\u2009',
            "Expected locale grouping separator for $language, got U+${separator.code.toString(16).uppercase()}",
        )
    }

    @Test
    fun doesNotGroupSmallCounts() {
        assertEquals("999", formatCountForLanguage(999, "nl"))
    }

    @Test
    fun adjustsPipelineLabelFontSizeForLocalizedLabels() {
        assertEquals(11f, statsPipelineLabelFontSizeSp(listOf("QUEUE", "FRESH", "SOLID")))
        assertEquals(11f, statsPipelineLabelFontSizeSp(listOf("RECALLING", "LEARNED")))
        assertEquals(10.2f, statsPipelineLabelFontSizeSp(listOf("EXTRALONGT", "LEARNED")))
        assertEquals(9.8f, statsPipelineLabelFontSizeSp(listOf("В ОЧЕРЕДИ", "НОВЫЕ")))
        assertEquals(9.8f, statsPipelineLabelFontSizeSp(listOf("ПОВТОРЕНИЕ", "ЗАКРЕПЛЕНО")))
    }
}
