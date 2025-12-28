package com.slovy.slovymovyapp

import com.slovy.slovymovyapp.server.ai.GeminiProvider
import com.slovy.slovymovyapp.server.github.GitHubClient
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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
        assertEquals(ContentType.parse("application/x-ndjson"), response.contentType()?.withoutParameters())

        val body = response.bodyAsText()
        assertTrue(body.isNotBlank(), "Response body should not be blank")

        val json = Json { ignoreUnknownKeys = true }
        val chunks = parseNdjson(body, json)
        assertTrue(chunks.isNotEmpty(), "NDJSON stream should contain at least one chunk")
        val baseChunk = chunks.first()
        assertEquals("base", baseChunk["stage"]?.jsonPrimitive?.content)
        val payload = baseChunk["payload"]?.jsonObject
        assertNotNull(payload, "Chunk should contain 'payload' field")
        assertTrue(payload.containsKey("entries"), "Payload should contain 'entries' field")
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

    @Test
    fun testWord_withSingleTranslation() = testApplication {
        if (!GitHubClient.isAvailable()) return@testApplication
        if (!GeminiProvider().isAvailable()) return@testApplication

        application {
            module()
        }
        val response = client.get("/word/en/test?translations=ru")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.parse("application/x-ndjson"), response.contentType()?.withoutParameters())

        val body = response.bodyAsText()
        val json = Json { ignoreUnknownKeys = true }
        val chunks = parseNdjson(body, json)
        assertTrue(chunks.isNotEmpty(), "NDJSON stream should contain at least one chunk")
        val lastPayload = chunks.last()["payload"]?.jsonObject
        val entries = lastPayload?.get("entries")?.jsonArray
        assertNotNull(entries, "Response should contain 'entries' field")
        assertTrue(entries.isNotEmpty(), "Entries should not be empty")

        // Check that translations were added to senses
        val firstEntry = entries[0].jsonObject
        val senses = firstEntry["senses"]?.jsonArray
        assertNotNull(senses, "Entry should contain 'senses' field")
        assertTrue(senses.isNotEmpty(), "Senses should not be empty")

        val firstSense = senses[0].jsonObject
        val translations = firstSense["translations"]?.jsonObject
        assertNotNull(translations, "Sense should contain 'translations' field")
        assertTrue(translations.containsKey("ru"), "Translations should contain 'ru' key")
    }

    @Test
    fun testWord_withMultipleTranslations() = testApplication {
        if (!GitHubClient.isAvailable()) return@testApplication
        if (!GeminiProvider().isAvailable()) return@testApplication

        application {
            module()
        }
        val response = client.get("/word/en/test?translations=ru,pl")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.parse("application/x-ndjson"), response.contentType()?.withoutParameters())

        val body = response.bodyAsText()
        val json = Json { ignoreUnknownKeys = true }
        val chunks = parseNdjson(body, json)
        assertTrue(chunks.isNotEmpty(), "NDJSON stream should contain at least one chunk")
        val entries = chunks.last()["payload"]?.jsonObject?.get("entries")?.jsonArray
        assertNotNull(entries, "Response should contain 'entries' field")

        val firstEntry = entries[0].jsonObject
        val senses = firstEntry["senses"]?.jsonArray
        assertNotNull(senses, "Entry should contain 'senses' field")

        val firstSense = senses[0].jsonObject
        val translations = firstSense["translations"]?.jsonObject
        assertNotNull(translations, "Sense should contain 'translations' field")
        assertTrue(translations.containsKey("ru"), "Translations should contain 'ru' key")
        assertTrue(translations.containsKey("pl"), "Translations should contain 'pl' key")
    }

    private fun parseNdjson(body: String, json: Json) =
        body
            .lineSequence()
            .filter { it.isNotBlank() }
            .map { json.parseToJsonElement(it).jsonObject }
            .toList()
}
