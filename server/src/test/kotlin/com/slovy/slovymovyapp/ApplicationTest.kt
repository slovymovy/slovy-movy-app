package com.slovy.slovymovyapp

import com.slovy.slovymovyapp.server.ai.GeminiProvider
import com.slovy.slovymovyapp.server.github.GitHubClient
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApplicationTest {

    @Test
    fun testRoot() = testApplication {
        application {
            module()
        }
        val response = client.get("/")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("Hello, world!", response.bodyAsText())
    }

    @Test
    fun testExtract_returnsJsonContent() = testApplication {
        if (!GitHubClient.isAvailable()) return@testApplication

        application {
            module()
        }
        val response = client.get("/extract/en/test")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())
        assertTrue(response.bodyAsText().isNotBlank(), "Response body should not be blank")
    }

    @Test
    fun testExtract_returnsNotFoundForNonExistentWord() = testApplication {
        if (!GitHubClient.isAvailable()) return@testApplication

        application {
            module()
        }
        val response = client.get("/extract/en/nonexistent-word-12345")
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun testWord_returnsEnhancedJsonContent() = testApplication {
        if (!GitHubClient.isAvailable()) return@testApplication
        if (!GeminiProvider().isAvailable()) return@testApplication

        application {
            module()
        }
        val response = client.get("/word/en/test")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())

        val body = response.bodyAsText()
        assertTrue(body.isNotBlank(), "Response body should not be blank")

        val json = Json { ignoreUnknownKeys = true }
        val jsonElement = json.parseToJsonElement(body)
        assertTrue(jsonElement.jsonObject.containsKey("entries"), "Response should contain 'entries' field")
    }

    @Test
    fun testWord_returnsNotFoundForNonExistentWord() = testApplication {
        if (!GitHubClient.isAvailable()) return@testApplication
        if (!GeminiProvider().isAvailable()) return@testApplication

        application {
            module()
        }
        val response = client.get("/word/en/nonexistent-word-12345")
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun testWord_returnsBadRequestForMissingParameters() = testApplication {
        application {
            module()
        }
        val response = client.get("/word/en/")
        assertEquals(HttpStatusCode.NotFound, response.status)
    }
}