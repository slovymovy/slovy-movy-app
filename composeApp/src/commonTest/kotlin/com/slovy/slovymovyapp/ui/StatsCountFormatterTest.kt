package com.slovy.slovymovyapp.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class StatsCountFormatterTest {
    @Test
    fun formatsCountsWithLocaleGroupingSeparator() {
        assertEquals("3,319", formatCountForLanguage(3319, "en"))
        assertEquals("3.319", formatCountForLanguage(3319, "nl"))
        assertEquals("3.319", formatCountForLanguage(3319, "de"))
        assertEquals("3.319", formatCountForLanguage(3319, "es"))
        assertEquals("3.319", formatCountForLanguage(3319, "it"))
        assertEquals("3.319", formatCountForLanguage(3319, "tr"))
        assertEquals("3\u00A0319", formatCountForLanguage(3319, "ru"))
        assertEquals("3\u00A0319", formatCountForLanguage(3319, "pl"))
        assertEquals("3\u00A0319", formatCountForLanguage(3319, "cs"))
        assertEquals("3\u202F319", formatCountForLanguage(3319, "fr"))
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
