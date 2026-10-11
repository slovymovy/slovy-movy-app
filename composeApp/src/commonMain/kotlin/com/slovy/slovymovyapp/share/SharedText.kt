package com.slovy.slovymovyapp.share

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.slovy.slovymovyapp.ui.reader.MAX_CHARS
import com.slovy.slovymovyapp.ui.reader.tokenize

/**
 * Text handed to the app by the platform: an Android text-selection action, a share sheet,
 * or (later) an iOS share extension.
 *
 * [serial] makes two consecutive requests with identical text distinct, so sharing the same
 * word twice still triggers delivery both times.
 */
data class SharedTextRequest(
    val text: String,
    val source: SharedTextSource,
    val serial: Long,
)

/** Where the platform got the text from; reported in analytics only. */
enum class SharedTextSource(val analyticsValue: String) {
    /** Android `ACTION_PROCESS_TEXT`: the user selected text in another app and picked OpenWords. */
    PROCESS_TEXT("process_text"),

    /** Android `ACTION_SEND` with `text/plain`: the share sheet. */
    SHARE("share"),
}

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

/**
 * Hands platform-delivered text to the composition. The platform calls [offer] from its entry
 * point (Android `onCreate`/`onNewIntent`); the app observes [pending] and calls [consume] once
 * it has navigated. Requests wait here, not in navigation state, so a share that arrives on a
 * cold start survives setup, download, and data-version screens until the main app is showing.
 */
class SharedTextReceiver {
    var pending by mutableStateOf<SharedTextRequest?>(null)
        private set

    private var nextSerial = 0L

    /** Ignores blank text; a later offer replaces an undelivered earlier one. */
    fun offer(text: String, source: SharedTextSource) {
        if (text.isBlank()) return
        pending = SharedTextRequest(text = text, source = source, serial = nextSerial++)
    }

    fun consume(request: SharedTextRequest) {
        if (pending == request) pending = null
    }
}
