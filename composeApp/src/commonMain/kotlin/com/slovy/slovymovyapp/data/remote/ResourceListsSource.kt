package com.slovy.slovymovyapp.data.remote

import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.logging.AppLogger
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.ExperimentalResourceApi
import slovymovyapp.composeapp.generated.resources.Res

/**
 * Debug-only [ListsSource] that serves curated word lists from bundles generated out of
 * the project-root `lists/` folder (see the `generateDebugLists` Gradle task) instead of
 * the backend. Each language's bundle is read from `files/debug-lists/{lang}.json`. When a
 * language has no local bundle the request is delegated to [fallback] so untouched
 * languages still resolve against the real server.
 *
 * Wired in only when `AppBuildConfig.isDebug` is true.
 */
class ResourceListsSource(
    private val fallback: ListsSource,
) : ListsSource {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun getListsVersion(language: Language): String? =
        readBundle(language)?.version ?: fallback.getListsVersion(language)

    override suspend fun getLists(language: Language): RemoteListsResponse? =
        readBundle(language) ?: fallback.getLists(language)

    @OptIn(ExperimentalResourceApi::class)
    private suspend fun readBundle(language: Language): RemoteListsResponse? = try {
        val bytes = Res.readBytes("files/debug-lists/${language.code}.json")
        json.decodeFromString(RemoteListsResponse.serializer(), bytes.decodeToString())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // Missing bundle (no local list for this language) is expected -> fall back.
        AppLogger.debug(TAG, null) { "No local debug lists bundle for ${language.code}: ${e.message}" }
        null
    }

    private companion object {
        const val TAG = "ResourceListsSource"
    }
}
