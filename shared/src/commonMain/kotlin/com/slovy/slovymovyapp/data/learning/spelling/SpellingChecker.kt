package com.slovy.slovymovyapp.data.learning.spelling

import com.slovy.slovymovyapp.util.stripAccents

/**
 * A hint applied to the learner's field: the new text, which of its positions a hint revealed, and
 * the position revealed by this hint, null when it only cut a wrong tail.
 */
data class SpellingHint(
    val value: String,
    val hintedPositions: Set<Int>,
    val revealedPosition: Int?,
)

/**
 * Where an attempt and the expected word disagree. [missed] and [accentOnly] index the expected
 * word: letters the attempt lacks or got wrong, and letters it got right except for the accent.
 * [extra] indexes the trimmed attempt: letters the expected word has no place for. [related] says
 * whether the attempt is a version of the expected word at all: at least three letters in common,
 * covering at least half of the shorter word. A correct prefix or a correct word with a tail is
 * related; a different word, or one or two stray letters, is not.
 */
data class SpellingDiff(
    val missed: Set<Int>,
    val accentOnly: Set<Int>,
    val extra: Set<Int>,
    val related: Boolean,
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
     * Trim, collapse inner whitespace and fold each character, the only normalisation applied
     * before comparing. Folding is per character so the length never changes and every comparison
     * here agrees on what a position means.
     */
    fun normalize(text: String): String =
        text.trim().replace(whitespace, " ").map(::fold).joinToString("")

    /**
     * Lowercases and maps the typographic apostrophes and dashes that smart keyboards insert onto
     * the plain ones dictionaries use, so "zo’n" is the same spelling as "zo'n".
     */
    private fun fold(c: Char): Char = when (c) {
        '\u2018', '\u2019', '\u02BC', '\u00B4', '`' -> '\''
        '\u2010', '\u2011', '\u2012', '\u2013', '\u2014', '\u2212' -> '-'
        else -> c.lowercaseChar()
    }

    fun isCorrect(attempt: String, expected: String): Boolean =
        normalize(attempt) == normalize(expected)

    /** Length of the longest prefix of [value] matching [expected] character by character, after folding. */
    fun matchingPrefixLength(value: String, expected: String): Int {
        var length = 0
        while (
            length < value.length &&
            length < expected.length &&
            fold(value[length]) == fold(expected[length])
        ) {
            length++
        }
        return length
    }

    /**
     * False once the field already holds the word as [isCorrect] sees it, so a hint can never
     * destroy a correct answer. Otherwise there is always something to do: reveal the next letter
     * or cut a wrong tail.
     */
    fun canHint(value: String, expected: String): Boolean =
        !isCorrect(value, expected)

    /**
     * Keeps the longest correct prefix of [value], drops whatever follows it and appends the next
     * letter of [expected]. Returns null when [canHint] is false, that is once the word is complete.
     *
     * `""` becomes `g`, `gez` becomes `geze`, `gezal` becomes `geze`, `hez` becomes `g` for
     * `gezellig`; `gezelligxx` becomes `gezellig`, with nothing left to reveal. Earlier hints that
     * survive the truncation stay marked.
     */
    fun applyHint(value: String, expected: String, hintedPositions: Set<Int>): SpellingHint? {
        if (!canHint(value, expected)) return null
        // Stray or doubled spaces are not the learner's spelling; tidy them rather than reset to
        // one letter. Earlier hints keep their mark only where their letter still sits.
        val base = value.trimStart().replace(whitespace, " ")
        val shift = value.length - value.trimStart().length
        val kept = matchingPrefixLength(base, expected)
        val survivingHints = hintedPositions
            .map { it - shift }
            .filterTo(mutableSetOf()) { it in 0 until kept && fold(base[it]) == fold(expected[it]) }
        if (kept == expected.length) {
            return SpellingHint(value = base.take(kept), hintedPositions = survivingHints, revealedPosition = null)
        }
        return SpellingHint(
            value = base.take(kept) + expected[kept],
            hintedPositions = survivingHints + kept,
            revealedPosition = kept,
        )
    }

    /**
     * The hinted positions that still hold their revealed letter after the learner edited the
     * field. A hint the learner typed over or deleted stops being a hint.
     */
    fun hintedPositionsAfterEdit(value: String, expected: String, hintedPositions: Set<Int>): Set<Int> =
        hintedPositions.filterTo(mutableSetOf()) { index ->
            index < value.length && index < expected.length && fold(value[index]) == fold(expected[index])
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
        val typed = attempt.trim().map(::fold)
        val target = expected.map(::fold)
        val matched = align(typed, target)
        val shared = matched.pairs.size
        val shorter = minOf(typed.size, target.size)
        return SpellingDiff(
            missed = target.indices.filterTo(linkedSetOf()) { it !in matched.target },
            accentOnly = matched.pairs
                .filter { (i, j) -> typed[i] != target[j] }
                .mapTo(linkedSetOf()) { (_, j) -> j },
            extra = typed.indices.filterTo(linkedSetOf()) { it !in matched.source },
            related = shared >= RELATED_MIN_SHARED_LETTERS && shared >= shorter * RELATED_MIN_SHARE,
        )
    }

    /** Fewer letters in common than this say nothing about the word, whatever the ratio. */
    private const val RELATED_MIN_SHARED_LETTERS = 3

    /** Share of the shorter word's letters that must be in common for the two to be related. */
    private const val RELATED_MIN_SHARE = 0.5

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
