package com.slovy.slovymovyapp.data.remote

import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.logging.AppLogger
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class RemoteIconPayload(
    val mimeType: String,
    val data: String,
)

@Serializable
data class RemoteList(
    val id: String,
    val title: Map<String, String>,
    val subtitle: Map<String, String>,
    val labels: Map<String, List<String>> = emptyMap(),
    val icon: RemoteIconPayload? = null,
    val senseIds: List<String>
)

@Serializable
data class RemoteListsResponse(
    val version: String,
    val lists: List<RemoteList>
)

@Serializable
private data class RemoteListsVersionResponse(
    val version: String
)

/**
 * Thin HTTP client for the `/lists/{lang}` endpoints. Failures return null and are
 * logged; persistence and version-based cache invalidation live in `ListsService`.
 */
class ListsClient(
    private val platform: PlatformDbSupport,
    baseUrl: String = DictionaryClient.PRODUCTION_SERVER_URL
) {
    private val serverBaseUrl = baseUrl.trimEnd('/')
    private val httpClient by lazy { platform.createHttpClient() }
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getListsVersion(language: Language): String? = try {
        val response = httpClient.get("$serverBaseUrl/lists/${language.code}/version")
        if (!response.status.isSuccess()) {
            AppLogger.warn(TAG, "Lists version request for ${language.code} failed: HTTP ${response.status.value}", null)
            null
        } else {
            json.decodeFromString(RemoteListsVersionResponse.serializer(), response.bodyAsText()).version
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AppLogger.warn(TAG, "Lists version request for ${language.code} failed", e)
        null
    }

    suspend fun getLists(language: Language): RemoteListsResponse? = try {
        val response = httpClient.get("$serverBaseUrl/lists/${language.code}")
        if (!response.status.isSuccess()) {
            AppLogger.warn(TAG, "Lists request for ${language.code} failed: HTTP ${response.status.value}", null)
            null
        } else {
            json.decodeFromString(RemoteListsResponse.serializer(), response.bodyAsText())
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AppLogger.warn(TAG, "Lists request for ${language.code} failed", e)
        null
    }

    private companion object {
        const val TAG = "ListsClient"
    }
}
