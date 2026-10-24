package com.slovy.slovymovyapp.share

import com.slovy.slovymovyapp.ui.reader.MAX_CHARS
import com.slovy.slovymovyapp.ui.reader.tokenize

/** Where shared text should land inside the app. */
sealed interface SharedTextRoute {
    /** Short selection: open Search with the query prefilled so the lookup runs immediately. */
    data class Search(val query: String) : SharedTextRoute

    /** Longer selection: open the Text Reader with the passage parsed word by word. */
    data class Reader(val text: String) : SharedTextRoute
}

/**
 * Decides where a shared text goes. The rule is by word count: a single word is a lookup,
 * anything longer is a passage. Word counting reuses the reader's tokenizer so hyphenated and
 * apostrophe words ("arm-rijk", "don't") count as one, the same way the reader will later
 * split them.
 */
object SharedTextRouter {
    const val MAX_SEARCH_WORDS = 1

    /**
     * Returns null when the text has nothing to look up: blank, or without a single
     * dictionary word (digits, symbols, emoji). Such a share only brings the app forward.
     */
    fun route(rawText: String): SharedTextRoute? {
        val text = rawText.trim()
        if (text.isEmpty()) return null
        // The reader rejects oversized passages itself with a "too long" state; tokenizing
        // them here just to reach the same answer would duplicate that work on the UI thread.
        if (text.length > MAX_CHARS) return SharedTextRoute.Reader(text)
        val words = tokenize(text).filter { it.isWord }.map { it.text }
        return when {
            words.isEmpty() -> null
            words.size <= MAX_SEARCH_WORDS -> SharedTextRoute.Search(words.joinToString(" "))
            else -> SharedTextRoute.Reader(text)
        }
    }

    /**
     * Number of dictionary words in [text], as the router counts them; used for analytics.
     * Null for text over [MAX_CHARS], which [route] deliberately never tokenizes.
     */
    fun wordCount(text: String): Int? =
        if (text.trim().length > MAX_CHARS) null else tokenize(text).count { it.isWord }
}
