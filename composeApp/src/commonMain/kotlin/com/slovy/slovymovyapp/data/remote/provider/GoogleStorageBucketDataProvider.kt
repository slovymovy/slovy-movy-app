package com.slovy.slovymovyapp.data.remote.provider

import com.slovy.slovymovyapp.data.remote.DataDbManager
import com.slovy.slovymovyapp.data.remote.PlatformDbSupport
import com.slovy.slovymovyapp.data.remote.RemoteDataProvider
import com.slovy.slovymovyapp.data.remote.RemoteFile
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonIgnoreUnknownKeys

class GoogleStorageBucketDataProvider : RemoteDataProvider {
    companion object {
        private const val BASE_URL = "https://storage.googleapis.com/slovymovy/"
        private const val LIST_API_URL = "https://storage.googleapis.com/storage/v1/b/slovymovy/o?prefix="
    }

    override suspend fun listFiles(platform: PlatformDbSupport): List<RemoteFile> = withContext(Dispatchers.IO) {
        val client = platform.createHttpClient()
        try {
            val response = client.get(LIST_API_URL + DataDbManager.VERSION + "/") {
                headersForHttp().forEach { (key, value) -> header(key, value) }
            }.bodyAsText()
            val storageResponse = Json.decodeFromString<GcsStorageListResponse>(response)
            storageResponse.items.mapNotNull { obj ->
                val fileName = obj.name.removePrefix(DataDbManager.VERSION + "/")
                if (fileName.isEmpty() || fileName == DataDbManager.VERSION + "/") {
                    null
                } else {
                    val size = obj.size.toLongOrNull() ?: return@mapNotNull null
                    RemoteFile(name = fileName, sizeBytes = size)
                }
            }
        } finally {
            client.close()
        }
    }

    override fun headersForHttp(): Map<String, String> {
        return emptyMap()
    }

    override fun downloadUrlFor(fileName: String): String = BASE_URL + DataDbManager.VERSION + "/" + fileName
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonIgnoreUnknownKeys
private data class GcsStorageListResponse(
    val items: List<GcsStorageObject> = emptyList()
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonIgnoreUnknownKeys
private data class GcsStorageObject(
    val name: String,
    val size: String
)

