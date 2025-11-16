package com.slovy.slovymovyapp.data.remote.provider

import com.slovy.slovymovyapp.data.remote.PlatformDbSupport
import com.slovy.slovymovyapp.data.remote.RemoteDataProvider
import com.slovy.slovymovyapp.data.remote.RemoteFile
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonIgnoreUnknownKeys

class GithubRepoDataProvider(
    private val owner: String = "slovymovy",
    private val repo: String = "slovy-movy-app",
    private val path: String = ".test-db-files",
    private val ref: String = "main"
) : RemoteDataProvider {
    private fun listApiUrl(): String =
        "https://api.github.com/repos/$owner/$repo/contents/${encodePath(path)}?ref=${encodeRef(ref)}"

    private fun rawBaseUrl(): String =
        "https://raw.githubusercontent.com/$owner/$repo/${encodeRef(ref)}/${trimSlashes(path)}/"

    override suspend fun listFiles(platform: PlatformDbSupport): List<RemoteFile> = withContext(Dispatchers.IO) {
        val client = platform.createHttpClient()
        try {
            val responseText = client.get(listApiUrl()).bodyAsText()
            val items = Json.decodeFromString<List<GithubContentItem>>(responseText)
            items.filter { it.type == "file" }
                .mapNotNull { item ->
                    val name = item.name
                    val size = item.size ?: return@mapNotNull null
                    RemoteFile(name = name, sizeBytes = size)
                }
        } finally {
            client.close()
        }
    }

    override fun downloadUrlFor(fileName: String): String = rawBaseUrl() + fileName

    private fun trimSlashes(p: String): String = p.trim('/', '\\')

    // Minimal encoding suitable for GitHub API usage in this project context
    private fun encodeRef(r: String): String = r.replace("/", "%2F")
    private fun encodePath(p: String): String = trimSlashes(p)
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonIgnoreUnknownKeys
private data class GithubContentItem(
    val name: String,
    val path: String? = null,
    @SerialName("download_url")
    val downloadUrl: String? = null,
    val type: String? = null,
    // GitHub returns size as a number (bytes)
    val size: Long? = null
)