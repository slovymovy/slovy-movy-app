package com.slovy.slovymovyapp.util

/**
 * Removes diacritics/accents from Latin characters while preserving Cyrillic text.
 *
 * This function is deterministic and produces identical output across all platforms.
 * It handles:
 * - Latin ligatures: æ -> ae, œ -> oe
 * - Special Latin letters: ø -> o, ł -> l, ß -> ss (capital ẞ lowercases to ß first)
 * - Diacritical marks on Latin characters (café -> cafe)
 * - Cyrillic text is only lowercased, not transliterated
 *
 * Data normalized before ß was folded still holds ß; lookups reach it through
 * [legacySharpSSpellings] until the next data-version bump.
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
        .replace("ß", "ss")

    // Use NFD normalization to decompose accented characters,
    // then remove combining diacritical marks (Unicode block 0300-036F)
    // This preserves Cyrillic characters which don't decompose the same way
    return normalizeAndStripAccents(replaced)
}

/**
 * Apostrophe-like characters that appear inside words ("don’t", "l’homme", "о’коннор"). The
 * `*_normalized` columns hold the ASCII apostrophe; see [normalizeApostrophes].
 */
const val APOSTROPHES = "'‘’ʼ"

/** Replaces typographic apostrophes with the canonical ASCII apostrophe. */
fun normalizeApostrophes(text: String): String =
    if (text.any { it in APOSTROPHES && it != '\'' }) {
        buildString(text.length) { text.forEach { append(if (it in APOSTROPHES) '\'' else it) } }
    } else {
        text
    }

/**
 * The value stored in, and queried against, the `*_normalized` dictionary/translation columns:
 * [stripAccents] plus [normalizeApostrophes], so "can’t", "can't" and "canʼt" share one key.
 * Ingestion and lookups must both go through this function.
 *
 * Lemma IDs keep hashing plain [stripAccents] (see `JsonIngestionBuilder.generateLemmaId`), so
 * persisted `card.lemma_id` values stay valid.
 *
 * Ship this only together with a DataDbManager.VERSION bump: v15 DBs and the local caches filled
 * from them still hold the source apostrophe (`о’коннор`, `let’s`) in their normalized columns, so
 * ASCII lookup keys miss those rows until every dictionary and translation DB is rebuilt with this
 * function and the version bump wipes the local caches.
 */
fun normalizeForLookup(s: String): String = stripAccents(normalizeApostrophes(s))

/**
 * Platform-specific implementation of Unicode NFD normalization and accent stripping.
 *
 * Decomposes characters to base + combining marks form (NFD), then removes
 * combining diacritical marks (Unicode range U+0300 to U+036F).
 */
expect fun normalizeAndStripAccents(s: String): String
