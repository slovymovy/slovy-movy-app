package com.slovy.slovymovyapp.ui.reader

import kotlin.uuid.Uuid

// Apostrophe-like characters accepted inside words ("don’t", "l’homme"). The dictionary
// stores the ASCII apostrophe, so lookups go through [normalizeApostrophes].
internal const val APOSTROPHES = "'‘’ʼ"

private val TOKEN_REGEX = Regex("[\\p{L}\\p{M}\\-$APOSTROPHES]+|\\s+|[^\\p{L}\\p{M}\\-$APOSTROPHES\\s]+")

private val EDGE_TRIM_CHARS = ("-$APOSTROPHES").toCharArray()

// Corpus-frequency bands used to colour the passage. Derived from each lemma's Zipf
// frequency (~1 rare … ~7 extremely common). Thresholds are deliberately simple and
// meant to be tuned against real passages.
private const val HIGH_ZIPF_THRESHOLD = 4.3
private const val MID_ZIPF_THRESHOLD = 3.3

// The passage is rendered eagerly (FlowRow is not lazy), so cap the word count to keep
// composition responsive and avoid ANRs on very large pastes.
internal const val MAX_WORDS = 2000

// Cheap pre-tokenization bound checked against the raw clipboard text. Rejects pastes that
// could not fit under MAX_WORDS anyway before any regex or allocation work, and — because a
// token is at least one character — also bounds the total token count for inputs dominated
// by punctuation, digits, or whitespace, which do not count toward MAX_WORDS.
internal const val MAX_CHARS = 20_000

enum class FreqBand { HIGH, MID, LOW }

internal fun bandForZipf(zipf: Double): FreqBand = when {
    zipf >= HIGH_ZIPF_THRESHOLD -> FreqBand.HIGH
    zipf >= MID_ZIPF_THRESHOLD -> FreqBand.MID
    else -> FreqBand.LOW
}

data class TextToken(
    val text: String,
    val lemma: String? = null,
    val lemmaId: Uuid? = null,
    val band: FreqBand? = null,
    val isWord: Boolean
)

/** Replaces typographic apostrophes with the dictionary's canonical ASCII apostrophe. */
internal fun normalizeApostrophes(text: String): String =
    if (text.any { it in APOSTROPHES && it != '\'' }) {
        buildString(text.length) { text.forEach { append(if (it in APOSTROPHES) '\'' else it) } }
    } else {
        text
    }

internal fun tokenize(text: String): List<TextToken> {
    val result = mutableListOf<TextToken>()
    for (match in TOKEN_REGEX.findAll(text)) {
        val value = match.value
        if (!value.any { it.isLetter() }) {
            result.add(TextToken(text = value, isWord = false))
            continue
        }
        // Strip leading/trailing hyphens and apostrophes so that bullet-style
        // "-word" and quoted "'rijk'" / "‘rijk’" are looked up without the surrounding
        // symbol. Mid-word occurrences (you're, don’t, arm-rijk) are preserved unchanged.
        val trimmedStart = value.trimStart(*EDGE_TRIM_CHARS)
        val leading = value.length - trimmedStart.length
        val core = trimmedStart.trimEnd(*EDGE_TRIM_CHARS)
        val trailing = trimmedStart.length - core.length
        if (leading > 0) result.add(TextToken(text = value.take(leading), isWord = false))
        if (core.isNotEmpty()) result.add(TextToken(text = core, isWord = core.any { it.isLetter() }))
        if (trailing > 0) result.add(TextToken(text = trimmedStart.takeLast(trailing), isWord = false))
    }
    return result
}

// Groups tokens so that each word stays with its immediately surrounding punctuation,
// but groups never span more than one word. This prevents long runs of non-whitespace
// tokens (e.g. URLs) from forming a single oversized FlowRow item.
internal fun groupTokensForRendering(tokens: List<TextToken>): List<List<TextToken>> {
    val result = mutableListOf<List<TextToken>>()
    val current = mutableListOf<TextToken>()
    for (token in tokens) {
        when {
            !token.isWord && token.text.all { it.isWhitespace() } -> {
                if (current.isNotEmpty()) { result.add(current.toList()); current.clear() }
                result.add(listOf(token))
            }
            token.isWord && current.any { it.isWord } -> {
                // Second word in the same run — flush and start a new group
                result.add(current.toList()); current.clear()
                current.add(token)
            }
            else -> current.add(token)
        }
    }
    if (current.isNotEmpty()) result.add(current.toList())
    return result
}
