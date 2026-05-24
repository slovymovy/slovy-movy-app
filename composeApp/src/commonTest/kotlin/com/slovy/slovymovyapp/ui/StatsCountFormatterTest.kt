package com.slovy.slovymovyapp.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StatsCountFormatterTest {
    @Test
    fun formatsCountsWithLocaleGroupingSeparator() {
        assertEquals("33,319", formatCountForLanguage(33319, "en"))
        for (language in listOf("nl", "de", "es", "it", "tr", "ru", "pl", "cs", "fr")) {
            assertGrouped(language)
        }
    }

    private fun assertGrouped(language: String) {
        val formatted = formatCountForLanguage(33319, language)
        assertEquals(6, formatted.length, "Expected 6 chars for $language, got '$formatted'")
        assertEquals("33", formatted.substring(0, 2), "Expected leading '33' for $language, got '$formatted'")
        assertEquals("319", formatted.substring(3), "Expected trailing '319' for $language, got '$formatted'")
        val separator = formatted[2]
        assertTrue(
            separator == ',' || separator == '.' ||
                separator == ' ' || separator == ' ' ||
                separator == ' ' || separator == ' ',
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
