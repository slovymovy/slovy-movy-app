package com.slovy.slovymovyapp.data.remote

import com.slovy.slovymovyapp.data.Language
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class RemoteList(
    val id: String,
    val title: Map<String, String>,
    val subtitle: Map<String, String>,
    val labels: Map<String, List<String>> = emptyMap(),
    val senseIds: List<String>
)

@Serializable
private data class RemoteListsResponse(
    val version: String,
    val lists: List<RemoteList>
)

class ListsClient(
    private val platform: PlatformDbSupport,
    baseUrl: String = DictionaryClient.PRODUCTION_SERVER_URL
) {
    private val serverBaseUrl = baseUrl.trimEnd('/')
    private val httpClient by lazy { platform.createHttpClient() }
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getLists(language: Language): List<RemoteList> = try {
        val response = httpClient.get("$serverBaseUrl/lists/${language.code}")
        if (!response.status.isSuccess()) return emptyList()
        json.decodeFromString(RemoteListsResponse.serializer(), response.bodyAsText()).lists
    } catch (_: Exception) {
        emptyList()
    }
}
