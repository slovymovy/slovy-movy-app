package com.slovy.slovymovyapp.util

/**
 * Removes diacritics/accents from Latin characters while preserving Cyrillic text.
 *
 * This function is deterministic and produces identical output across all platforms.
 * It handles:
 * - Latin ligatures: æ -> ae, œ -> oe
 * - Special Latin letters: ø -> o, ł -> l
 * - Diacritical marks on Latin characters (café -> cafe)
 * - Cyrillic text is only lowercased, not transliterated
 *
 * @param s The input string
 * @return The normalized string with accents stripped and lowercase
 */
fun stripAccents(s: String): String {
    // Normalize to lowercase first
    val lower = s.lowercase()

    // Handle specific Latin ligatures/letters before NFD normalization
    val replaced = lower
        .replace("æ", "ae")
        .replace("œ", "oe")
        .replace("ø", "o")
        .replace("ł", "l")

    // Use NFD normalization to decompose accented characters,
    // then remove combining diacritical marks (Unicode block 0300-036F)
    // This preserves Cyrillic characters which don't decompose the same way
    return normalizeAndStripAccents(replaced)
}

/**
 * Platform-specific implementation of Unicode NFD normalization and accent stripping.
 *
 * Decomposes characters to base + combining marks form (NFD), then removes
 * combining diacritical marks (Unicode range U+0300 to U+036F).
 */
expect fun normalizeAndStripAccents(s: String): String

/**
 * Normalizes a search query to match the format stored in the database.
 *
 * The database stores text with typographic apostrophes (U+2019 '), but users
 * may type various apostrophe-like characters. This function converts all common
 * apostrophe variants to typographic ones so search queries match the stored data.
 *
 * Handled variants:
 * - U+0027 ' ASCII apostrophe (most common from keyboards)
 * - U+2018 ' left single quotation mark
 * - U+02BC ʼ modifier letter apostrophe
 * - U+0060 ` grave accent / backtick
 *
 * @param query The search query entered by the user
 * @return The query with apostrophes normalized to match DB format
 */
fun normalizeSearchQuery(query: String): String {
    return query
        .replace('\'', '\u2019')   // U+0027 ASCII apostrophe
        .replace('\u2018', '\u2019') // U+2018 left single quote
        .replace('\u02BC', '\u2019') // U+02BC modifier letter apostrophe
        .replace('`', '\u2019')      // U+0060 backtick
}
