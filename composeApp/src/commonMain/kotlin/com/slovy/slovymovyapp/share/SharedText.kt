package com.slovy.slovymovyapp.share

import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import com.slovy.slovymovyapp.AppDestination
import com.slovy.slovymovyapp.analytics.Analytics
import com.slovy.slovymovyapp.analytics.AnalyticsEvent
import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.ui.reader.MAX_CHARS
import com.slovy.slovymovyapp.ui.reader.tokenize
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlin.reflect.KClass

/**
 * Text handed to the app by the platform: an Android text-selection action, a share sheet,
 * or (later) an iOS share extension.
 */
data class SharedTextRequest(
    val text: String,
    val source: SharedTextSource,
)

/** Where the platform got the text from; reported in analytics only. */
enum class SharedTextSource(val analyticsValue: String) {
    /** Android `ACTION_PROCESS_TEXT`: the user selected text in another app and picked OpenWords. */
    PROCESS_TEXT("process_text"),

    /** Android `ACTION_SEND` with `text/plain`: the share sheet. */
    SHARE("share"),
}

/** Where shared text opens inside the app. */
sealed interface SharedTextRoute {
    /** A single word: Search with the query prefilled, in the language Search is set to. */
    data class Search(val query: String) : SharedTextRoute

    /** A passage: the Text Reader in [language]. */
    data class Reader(val text: String, val language: Language) : SharedTextRoute
}

/**
 * Hands platform-delivered text to the app and decides where it opens.
 *
 * The platform calls [offer] from its entry point (Android `onCreate`/`onNewIntent`); `App`
 * collects [requests] and asks [routeFor] where each one goes. Only the latest request is
 * buffered, so a share that cold-starts the app survives until `App` starts collecting.
 */
class SharedTextReceiver {
    private val channel = Channel<SharedTextRequest>(Channel.CONFLATED)

    val requests: Flow<SharedTextRequest> = channel.receiveAsFlow()

    private val interruptibleRoutes = mutableSetOf<KClass<out AppDestination>>()

    /** Ignores blank text; a later offer replaces an undelivered earlier one. */
    fun offer(text: String, source: SharedTextSource) {
        if (text.isBlank()) return
        channel.trySend(SharedTextRequest(text = text, source = source))
    }

    /**
     * Marks [route] as a screen a share may navigate away from; `AppNavGraphBuilder.screen`
     * asks every screen. Welcome, setup, download, data-version, error, and a running study
     * session are not: leaving them would skip setup or tear the session down.
     */
    internal fun allowInterrupting(route: KClass<out AppDestination>) {
        interruptibleRoutes += route
    }

    /**
     * Where [request] opens while [destination] is showing, or null to drop it: [destination]
     * must not be left, no dictionary is [installed], or the text has nothing to look up.
     * Dropping only means the app was brought forward.
     */
    internal fun routeFor(
        request: SharedTextRequest,
        destination: NavDestination,
        savedSearchLanguage: Language?,
        activeLanguage: Language?,
        installed: List<Language>,
    ): SharedTextRoute? {
        if (interruptibleRoutes.none { destination.hasRoute(it) }) return null
        val language = pickLanguage(savedSearchLanguage, activeLanguage, installed) ?: return null
        val route = route(request.text, language) ?: return null
        logReceived(request, language, route)
        return route
    }

    private fun logReceived(request: SharedTextRequest, language: Language, route: SharedTextRoute) {
        Analytics.logEvent(
            AnalyticsEvent.SHARED_TEXT_RECEIVED,
            buildMap {
                put("lang", language.code)
                put("source", request.source.analyticsValue)
                put(
                    "route",
                    when (route) {
                        is SharedTextRoute.Search -> "search"
                        is SharedTextRoute.Reader -> "reader"
                    },
                )
                put("char_count", request.text.length.toLong())
                // Absent for oversized passages, which are never tokenized.
                wordCount(request.text)?.let { put("word_count", it.toLong()) }
            },
        )
    }

    internal companion object {
        /**
         * Mirrors `SearchViewModel`: the [saved] Search language when its dictionary is
         * installed, else the [active] learning language, else any installed dictionary.
         */
        fun pickLanguage(saved: Language?, active: Language?, installed: List<Language>): Language? =
            saved?.takeIf { it in installed }
                ?: active?.takeIf { it in installed }
                ?: installed.firstOrNull()

        /**
         * A single word is a lookup, anything longer is a passage. Words are counted with the
         * reader's tokenizer, so "arm-rijk" and "don't" count as one. Null for blank text or
         * text without a dictionary word (digits, symbols, emoji).
         */
        fun route(rawText: String, language: Language): SharedTextRoute? {
            val text = rawText.trim()
            if (text.isEmpty()) return null
            // The reader rejects oversized passages itself with a "too long" state; tokenizing
            // them here just to reach the same answer would duplicate that work on the UI thread.
            if (text.length > MAX_CHARS) return SharedTextRoute.Reader(text, language)
            val words = tokenize(text).filter { it.isWord }.map { it.text }
            return when {
                words.isEmpty() -> null
                words.size == 1 -> SharedTextRoute.Search(words.single())
                else -> SharedTextRoute.Reader(text, language)
            }
        }

        /** Dictionary words in [text], as [route] counts them; null over [MAX_CHARS]. */
        fun wordCount(text: String): Int? =
            if (text.trim().length > MAX_CHARS) null else tokenize(text).count { it.isWord }
    }
}
