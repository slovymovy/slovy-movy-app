package com.slovy.slovymovyapp

import com.slovy.slovymovyapp.server.lists.LanguageListsResponse
import com.slovy.slovymovyapp.server.lists.ListsVersionResponse
import com.slovy.slovymovyapp.server.lists.LocalDirectoryListsLoader
import io.ktor.http.*
import io.ktor.server.request.receiveText
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.isRegularFile

private const val TEST_DB_DIR_ENV = "TEST_DB_DIR"
private const val TEST_LISTS_DIR_ENV = "TEST_LISTS_DIR"

fun isTestMode(): Boolean =
    System.getenv("IS_TEST")?.equals("true", ignoreCase = true) == true

private fun testDbDir(): Path? {
    val dir = System.getenv(TEST_DB_DIR_ENV)?.takeIf { it.isNotBlank() } ?: ".test-db-files"
    val path = Paths.get(dir)
    return if (Files.exists(path) && Files.isDirectory(path)) path else null
}

private fun testListsDir(): Path? {
    val dir = System.getenv(TEST_LISTS_DIR_ENV)?.takeIf { it.isNotBlank() } ?: ".test-lists-files"
    val path = Paths.get(dir)
    return if (Files.exists(path) && Files.isDirectory(path)) path else null
}

/**
 * Lists committed under the test lists directory (env `TEST_LISTS_DIR`, default
 * `.test-lists-files/{lang}/`), in the same `{id}.json` + `{id}.svg` layout as the
 * GitHub `lists/{lang}/` folder. Loaded per request so file edits show up live.
 */
private fun localListsBundle(lang: String): LanguageListsResponse? {
    val dir = testListsDir() ?: return null
    val bundle = LocalDirectoryListsLoader(dir).load(lang) ?: return null
    return LanguageListsResponse(version = bundle.version, lists = bundle.lists)
}

private fun isSafeFileName(name: String): Boolean {
    if (name.contains("..") || name.contains('/') || name.contains('\\')) return false
    return Paths.get(name).fileName.toString() == name
}

@Serializable
private data class TestDbFileEntry(
    val name: String,
    val sizeBytes: Long
)

/**
 * In-memory replacement for the GitHub-backed lists data. Tests populate it via
 * `PUT /test/lists/{lang}` and the test-mode `/lists/{lang}` endpoints serve from it,
 * so client/server lists tests stay hermetic (no GitHub token or network needed).
 * Languages that are not staged fall back to the committed test lists directory
 * (see [localListsBundle]).
 */
private object TestLanguageListsStore {
    private val bundles = ConcurrentHashMap<String, LanguageListsResponse>()

    fun get(lang: String): LanguageListsResponse? = bundles[lang]

    fun put(lang: String, bundle: LanguageListsResponse) {
        bundles[lang] = bundle
    }

    fun remove(lang: String) {
        bundles.remove(lang)
    }
}

fun Routing.testListsEndpoints() {
    // explicitNulls=false lets test payloads omit nullable fields like `icon`.
    val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    route("/test/lists") {
        put("/{lang}") {
            val lang = call.parameters["lang"]?.trim()
            if (lang.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, "Missing lang parameter")
                return@put
            }
            val bundle = try {
                json.decodeFromString(LanguageListsResponse.serializer(), call.receiveText())
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, "Invalid lists payload: ${e.message}")
                return@put
            }
            TestLanguageListsStore.put(lang, bundle)
            call.respond(HttpStatusCode.NoContent)
        }
        delete("/{lang}") {
            val lang = call.parameters["lang"]?.trim()
            if (lang.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, "Missing lang parameter")
                return@delete
            }
            TestLanguageListsStore.remove(lang)
            call.respond(HttpStatusCode.NoContent)
        }
    }

    get("/lists/{lang}/version") {
        val lang = call.parameters["lang"]?.trim()
        if (lang.isNullOrBlank()) {
            call.respond(HttpStatusCode.BadRequest, "Missing lang parameter")
            return@get
        }
        val bundle = TestLanguageListsStore.get(lang) ?: localListsBundle(lang)
        if (bundle == null) {
            call.respond(HttpStatusCode.NotFound, "Lists for language '$lang' not found")
            return@get
        }
        call.respondText(
            json.encodeToString(ListsVersionResponse.serializer(), ListsVersionResponse(bundle.version)),
            ContentType.Application.Json
        )
    }

    get("/lists/{lang}") {
        val lang = call.parameters["lang"]?.trim()
        if (lang.isNullOrBlank()) {
            call.respond(HttpStatusCode.BadRequest, "Missing lang parameter")
            return@get
        }
        val bundle = TestLanguageListsStore.get(lang) ?: localListsBundle(lang)
        if (bundle == null) {
            call.respond(HttpStatusCode.NotFound, "Lists for language '$lang' not found")
            return@get
        }
        call.respondText(
            json.encodeToString(LanguageListsResponse.serializer(), bundle),
            ContentType.Application.Json
        )
    }
}

fun Routing.testDataEndpoints() {
    route("/test/db") {
        get("/list") {
            val json = Json
            val dir = testDbDir()
            if (dir == null) {
                call.respond(HttpStatusCode.ServiceUnavailable, "Missing test DB directory")
                return@get
            }

            val files = Files.list(dir)
                .use { stream ->
                    stream.filter { it.isRegularFile() }
                        .map { path ->
                            TestDbFileEntry(
                                name = path.fileName.toString(),
                                sizeBytes = Files.size(path)
                            )
                        }.toList()
                }
            call.respondText(
                json.encodeToString(ListSerializer(TestDbFileEntry.serializer()), files),
                ContentType.Application.Json
            )
        }
        get("/file/{name}") {
            val name = call.parameters["name"]?.trim()
            if (name.isNullOrEmpty() || !isSafeFileName(name)) {
                call.respond(HttpStatusCode.BadRequest, "Invalid file name")
                return@get
            }
            val dir = testDbDir()
            if (dir == null) {
                call.respond(HttpStatusCode.ServiceUnavailable, "Missing test DB directory")
                return@get
            }
            val file = dir.resolve(name).toFile()
            if (!file.exists() || !file.isFile) {
                call.respond(HttpStatusCode.NotFound, "File not found")
                return@get
            }
            call.respondFile(file)
        }
    }
}