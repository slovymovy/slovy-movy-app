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
 * The spellings a [stripAccents]-normalized search query has to be looked up under.
 *
 * Normalized columns keep German ß (`groß` normalizes to `groß`), and every shipped dictionary and
 * translation DB was built that way, so changing [stripAccents] to fold ß would strand them until a
 * data-version reset. Instead a query typed with "ss" also tries each "ss" as "ß": `gross` yields
 * `gross` and `groß`, `strasse` yields `strasse` and `straße`. The query itself always comes first.
 *
 * Only the first [MAX_SHARP_S_SUBSTITUTIONS] occurrences are expanded, because the variant count
 * doubles with each one.
 */
fun sharpSSearchVariants(normalized: String): List<String> {
    val positions = mutableListOf<Int>()
    var index = normalized.indexOf("ss")
    while (index >= 0 && positions.size < MAX_SHARP_S_SUBSTITUTIONS) {
        positions += index
        index = normalized.indexOf("ss", index + 2)
    }
    if (positions.isEmpty()) return listOf(normalized)

    return (0 until (1 shl positions.size)).map { mask ->
        buildString {
            var last = 0
            positions.forEachIndexed { bit, position ->
                append(normalized, last, position)
                append(if (mask and (1 shl bit) != 0) "ß" else "ss")
                last = position + 2
            }
            append(normalized, last, normalized.length)
        }
    }
}

private const val MAX_SHARP_S_SUBSTITUTIONS = 3

/**
 * Platform-specific implementation of Unicode NFD normalization and accent stripping.
 *
 * Decomposes characters to base + combining marks form (NFD), then removes
 * combining diacritical marks (Unicode range U+0300 to U+036F).
 */
expect fun normalizeAndStripAccents(s: String): String
