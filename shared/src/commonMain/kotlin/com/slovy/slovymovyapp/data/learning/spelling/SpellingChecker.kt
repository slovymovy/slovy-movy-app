package com.slovy.slovymovyapp.data.learning.spelling

import com.slovy.slovymovyapp.util.stripAccents

/** A hint applied to the learner's field: the new text and which of its positions a hint revealed. */
data class SpellingHint(
    val value: String,
    val hintedPositions: Set<Int>,
)

/**
 * Where an attempt and the expected word disagree. [missed] and [accentOnly] index the expected
 * word: letters the attempt lacks or got wrong, and letters it got right except for the accent.
 * [extra] indexes the trimmed attempt: letters the expected word has no place for.
 */
data class SpellingDiff(
    val missed: Set<Int>,
    val accentOnly: Set<Int>,
    val extra: Set<Int>,
)

/**
 * Pure comparison logic for the spelling card: what the learner typed against the word the card
 * expects. It holds no state and knows nothing about scheduling, so the UI can call it on every
 * keystroke.
 *
 * Positions are indices into the learner's text (hints) or into the expected word (misses). The
 * expected word is the lemma as the dictionary spells it; comparisons ignore case and surrounding
 * or doubled whitespace, nothing else, so an accent-only mismatch still counts as a miss.
 */
object SpellingChecker {

    private val whitespace = Regex("\\s+")

    /**
     * Trim, collapse inner whitespace and lowercase, the only normalisation applied before
     * comparing. Case is folded per character so the length never changes and every comparison
     * here agrees on what a position means.
     */
    fun normalize(text: String): String =
        text.trim().replace(whitespace, " ").map { it.lowercaseChar() }.joinToString("")

    fun isCorrect(attempt: String, expected: String): Boolean =
        normalize(attempt) == normalize(expected)

    /** Length of the longest prefix of [value] matching [expected] character by character, ignoring case. */
    fun matchingPrefixLength(value: String, expected: String): Int {
        var length = 0
        while (
            length < value.length &&
            length < expected.length &&
            value[length].equals(expected[length], ignoreCase = true)
        ) {
            length++
        }
        return length
    }

    /** False once the field already holds the whole word; the last letter may be hinted like any other. */
    fun canHint(value: String, expected: String): Boolean =
        matchingPrefixLength(value, expected) < expected.length

    /**
     * Keeps the longest correct prefix of [value], drops whatever follows it and appends the next
     * letter of [expected]. Returns null when [canHint] is false, that is once the word is complete.
     *
     * `""` becomes `g`, `gez` becomes `geze`, `gezal` becomes `geze`, `hez` becomes `g` for
     * `gezellig`. Earlier hints that survive the truncation stay marked.
     */
    fun applyHint(value: String, expected: String, hintedPositions: Set<Int>): SpellingHint? {
        if (!canHint(value, expected)) return null
        val kept = matchingPrefixLength(value, expected)
        return SpellingHint(
            value = value.take(kept) + expected[kept],
            hintedPositions = hintedPositions.filterTo(mutableSetOf()) { it < kept } + kept,
        )
    }

    /**
     * The hinted positions that still hold their revealed letter after the learner edited the
     * field. A hint the learner typed over or deleted stops being a hint.
     */
    fun hintedPositionsAfterEdit(value: String, expected: String, hintedPositions: Set<Int>): Set<Int> =
        hintedPositions.filterTo(mutableSetOf()) { index ->
            index < value.length && index < expected.length && value[index].equals(expected[index], ignoreCase = true)
        }

    /** True when the field holds at least one character the learner typed, not counting hints or spaces. */
    fun hasLearnerInput(value: String, hintedPositions: Set<Int>): Boolean =
        value.indices.any { index -> index !in hintedPositions && !value[index].isWhitespace() }

    /**
     * How the attempt differs from [expected], for the comparison on the card back. Positions in
     * [SpellingDiff.missed] and [SpellingDiff.accentOnly] index [expected]; [SpellingDiff.extra]
     * indexes the trimmed attempt. The two are aligned on as many letters as possible, exact
     * matches preferred over accent-only ones, so a dropped, swapped or accented letter is marked
     * and the rest of the word is not.
     */
    fun diff(attempt: String, expected: String): SpellingDiff {
        val typed = attempt.trim().map { it.lowercaseChar() }
        val target = expected.map { it.lowercaseChar() }
        val matched = align(typed, target)
        return SpellingDiff(
            missed = target.indices.filterTo(linkedSetOf()) { it !in matched.target },
            accentOnly = matched.pairs
                .filter { (i, j) -> typed[i] != target[j] }
                .mapTo(linkedSetOf()) { (_, j) -> j },
            extra = typed.indices.filterTo(linkedSetOf()) { it !in matched.source },
        )
    }

    /** The letter without its accent when that is a single character; ligatures stay as they are. */
    private fun baseLetter(c: Char): Char =
        stripAccents(c.toString()).singleOrNull() ?: c

    /** An alignment as (source index, target index) pairs, in order. */
    private class MatchedPositions(val pairs: List<Pair<Int, Int>>) {
        val source: Set<Int> = pairs.mapTo(HashSet()) { it.first }
        val target: Set<Int> = pairs.mapTo(HashSet()) { it.second }
    }

    /**
     * A longest common subsequence where letters match by base letter, scored so that the number
     * of matched letters decides first and, among alignments of equal length, exact matches win
     * over accent-only ones. The weights make that ordering hold: one more match is worth more
     * than turning every match from accent-only into exact.
     */
    private fun align(source: List<Char>, target: List<Char>): MatchedPositions {
        val rows = source.size
        val cols = target.size
        val accentWeight = rows + cols + 1
        val exactWeight = accentWeight + 1
        fun weight(i: Int, j: Int): Int = when {
            source[i] == target[j] -> exactWeight
            baseLetter(source[i]) == baseLetter(target[j]) -> accentWeight
            else -> 0
        }
        val table = Array(rows + 1) { IntArray(cols + 1) }
        for (i in rows - 1 downTo 0) {
            for (j in cols - 1 downTo 0) {
                val w = weight(i, j)
                val matched = if (w > 0) w + table[i + 1][j + 1] else 0
                table[i][j] = maxOf(matched, table[i + 1][j], table[i][j + 1])
            }
        }
        val pairs = mutableListOf<Pair<Int, Int>>()
        var i = 0
        var j = 0
        while (i < rows && j < cols) {
            val w = weight(i, j)
            when {
                w > 0 && table[i][j] == w + table[i + 1][j + 1] -> {
                    pairs += i to j
                    i++
                    j++
                }

                table[i + 1][j] >= table[i][j + 1] -> i++
                else -> j++
            }
        }
        return MatchedPositions(pairs)
    }
}
