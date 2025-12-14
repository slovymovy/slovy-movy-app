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
