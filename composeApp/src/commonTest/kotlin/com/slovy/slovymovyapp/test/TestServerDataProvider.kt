package com.slovy.slovymovyapp.test

import com.slovy.slovymovyapp.data.remote.PlatformDbSupport
import com.slovy.slovymovyapp.data.remote.RemoteDataProvider
import com.slovy.slovymovyapp.data.remote.RemoteFile
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class TestServerDataProvider(
    private val baseUrl: String
) : RemoteDataProvider {
    override suspend fun listFiles(platform: PlatformDbSupport): List<RemoteFile> = withContext(Dispatchers.IO) {
        val client = platform.createHttpClient()
        try {
            val responseText = client.get("$baseUrl/test/db/list").bodyAsText()
            val items = Json.decodeFromString<List<TestDbFileEntry>>(responseText)
            items.map { item -> RemoteFile(name = item.name, sizeBytes = item.sizeBytes) }
        } finally {
            client.close()
        }
    }

    override fun downloadUrlFor(fileName: String): String {
        val encodedName = fileName.encodeURLPath()
        return "$baseUrl/test/db/file/$encodedName"
    }

    override fun headersForHttp(): Map<String, String> = emptyMap()
}

@Serializable
private data class TestDbFileEntry(
    val name: String,
    val sizeBytes: Long
)
