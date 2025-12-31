package com.slovy.slovymovyapp

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.isRegularFile

private const val TEST_DB_DIR_ENV = "TEST_DB_DIR"

fun isTestMode(): Boolean =
    System.getenv("IS_TEST")?.equals("true", ignoreCase = true) == true

private fun testDbDir(): Path? {
    val dir = System.getenv(TEST_DB_DIR_ENV)?.takeIf { it.isNotBlank() } ?: ".test-db-files"
    val path = Paths.get(dir)
    return if (Files.exists(path) && Files.isDirectory(path)) path else null
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