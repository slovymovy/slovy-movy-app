package com.slovy.slovymovyapp.share

import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import com.slovy.slovymovyapp.AppDestination
import com.slovy.slovymovyapp.analytics.Analytics
import com.slovy.slovymovyapp.analytics.AnalyticsEvent
import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.remote.DictionaryRepository
import com.slovy.slovymovyapp.data.settings.SettingsRepository
import kotlin.reflect.KClass

/** A shared text ready to open: [route] in [language], addressed by its request [serial]. */
internal data class SharedTextDelivery(
    val serial: Long,
    val language: Language,
    val route: SharedTextRoute,
)

/**
 * Decides whether and where a [SharedTextRequest] opens, for `App` to navigate to. Everything
 * that does not need the nav controller lives here: which screens a share may interrupt
 * (registered by `AppNavGraphBuilder`), which language it opens in, and its analytics.
 */
internal class SharedTextDispatcher(
    private val dictionaryRepository: DictionaryRepository,
    private val settingsRepository: SettingsRepository,
) {
    /**
     * Where [request] should open while [destination] is showing, or null to drop it: the text
     * has nothing to look up, no dictionary is installed, or [destination] must not be left.
     * Dropping only means the app was brought forward. [activeLanguage] is the app's current
     * learning language.
     */
    suspend fun deliveryFor(
        request: SharedTextRequest,
        destination: NavDestination,
        activeLanguage: Language?,
    ): SharedTextDelivery? {
        if (!interruptible(destination)) return null
        val language = pickLanguage(
            saved = settingsRepository.getSearchLanguage(),
            active = activeLanguage,
            installed = dictionaryRepository.installedDictionaries(),
        ) ?: return null
        val route = SharedTextRouter.route(request.text) ?: return null
        logReceived(request, language, route)
        return SharedTextDelivery(serial = request.serial, language = language, route = route)
    }

    private val interruptibleRoutes = mutableSetOf<KClass<out AppDestination>>()

    /**
     * Marks [route] as a screen a share may navigate away from; `AppNavGraphBuilder.screen`
     * asks every screen. Welcome, setup, download, data-version, error, and a running study
     * session are not: leaving them would skip setup or tear the session down.
     */
    fun allowInterrupting(route: KClass<out AppDestination>) {
        interruptibleRoutes += route
    }

    private fun interruptible(destination: NavDestination): Boolean =
        interruptibleRoutes.any { destination.hasRoute(it) }

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
                SharedTextRouter.wordCount(request.text)?.let { put("word_count", it.toLong()) }
            },
        )
    }

    companion object {
        /**
         * The lookup runs in the language Search is set to, so this mirrors `SearchViewModel`:
         * the [saved] Search language when its dictionary is installed, else the [active]
         * learning language, else any installed dictionary. Null when none is installed.
         */
        fun pickLanguage(saved: Language?, active: Language?, installed: List<Language>): Language? =
            saved?.takeIf { it in installed }
                ?: active?.takeIf { it in installed }
                ?: installed.firstOrNull()
    }
}
