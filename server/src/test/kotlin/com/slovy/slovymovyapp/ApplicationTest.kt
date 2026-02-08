package com.slovy.slovymovyapp

import com.slovy.slovymovyapp.server.ai.GeminiProvider
import com.slovy.slovymovyapp.server.github.GitHubClient
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

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

    @Test
    fun testWord_skipsSelfTranslationRequest() = testApplication {
        if (!GitHubClient.isAvailable()) return@testApplication

        application {
            module()
        }
        val response = client.get("/word/en/test?translations=en")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.parse("application/x-ndjson"), response.contentType()?.withoutParameters())

        val body = response.bodyAsText()
        val json = Json { ignoreUnknownKeys = true }
        val chunks = parseNdjson(body, json)
        assertEquals(1, chunks.size, "Self-translation request should not produce a translated stage")
        assertEquals("base", chunks.first()["stage"]?.jsonPrimitive?.content)
    }

    @Test
    fun testFeedback_createsIssueAndClosesIt() = testApplication {
        if (!GitHubClient.isAvailable()) return@testApplication

        application {
            module()
        }

        var issueNumber: Int? = null
        try {
            val response = client.post("/feedback/en/test?translations=ru,pl") {
                contentType(ContentType.Application.Json)
                setBody("""{"comment":"this is a test"}""")
            }

            assertEquals(HttpStatusCode.Created, response.status)
            assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())

            val json = Json { ignoreUnknownKeys = true }
            val responseJson = json.parseToJsonElement(response.bodyAsText()).jsonObject
            issueNumber = responseJson["issueNumber"]?.jsonPrimitive?.content?.toIntOrNull()
            assertNotNull(issueNumber, "Issue number should be present in response")
            val createdIssueNumber = issueNumber

            val issueTitle = responseJson["issueTitle"]?.jsonPrimitive?.content
            assertNotNull(issueTitle, "Issue title should be present in response")
            assertTrue(issueTitle.contains("[en]"), "Issue title should contain source language")
            assertTrue(issueTitle.contains("test"), "Issue title should contain word")
            assertTrue(issueTitle.contains("ru"), "Issue title should contain 'ru' translation code")
            assertTrue(issueTitle.contains("pl"), "Issue title should contain 'pl' translation code")

            val issue = GitHubClient.client().getRepository("slovymovy/words").getIssue(createdIssueNumber)
            assertTrue(
                issue.labels.any { it.name == "feedback" },
                "Created issue should have 'feedback' label"
            )
            assertTrue(
                issue.body.contains("this is a test"),
                "Created issue body should contain the provided comment"
            )
            assertTrue(
                issue.body.contains("ru") && issue.body.contains("pl"),
                "Created issue body should contain all translation codes"
            )
        } finally {
            issueNumber?.let { GitHubClient.closeIssue(it) }
        }
    }

    @Test
    fun testGeneralFeedback_createsDiscussion() = testApplication {
        if (!GitHubClient.isAvailable()) return@testApplication

        application {
            module()
        }

        val response = client.post("/feedback") {
            contentType(ContentType.Application.Json)
            setBody("""{"comment":"General app feedback from test"}""")
        }

        assertEquals(HttpStatusCode.Created, response.status)
        assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())

        val json = Json { ignoreUnknownKeys = true }
        val responseJson = json.parseToJsonElement(response.bodyAsText()).jsonObject
        val discussionNumber = responseJson["discussionNumber"]?.jsonPrimitive?.content?.toIntOrNull()
        assertNotNull(discussionNumber, "Discussion number should be present in response")
        assertTrue(discussionNumber > 0, "Discussion number should be positive")

        val discussionUrl = responseJson["discussionUrl"]?.jsonPrimitive?.content
        assertNotNull(discussionUrl, "Discussion URL should be present in response")
        assertTrue(discussionUrl.contains("github.com"), "URL should be a GitHub URL")
    }

    @Test
    fun testGeneralFeedback_returnsBadRequestForEmptyComment() = testApplication {
        application {
            module()
        }

        val response = client.post("/feedback") {
            contentType(ContentType.Application.Json)
            setBody("""{"comment":"   "}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun testGeneralFeedback_returnsBadRequestForInvalidBody() = testApplication {
        application {
            module()
        }

        val response = client.post("/feedback") {
            contentType(ContentType.Application.Json)
            setBody("""not json""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    private fun parseNdjson(body: String, json: Json) =
        body
            .lineSequence()
            .filter { it.isNotBlank() }
            .map { json.parseToJsonElement(it).jsonObject }
            .toList()
}

class RaceWithFallbackTest {

    @Test
    fun primarySucceedsWithinTimeout() = runBlocking {
        val result = raceWithFallback(
            primaryAvailable = true,
            fallbackAvailable = true,
            primary = { "primary" },
            fallback = { "fallback" },
            timeoutMs = 1000
        )
        assertEquals("primary", result)
    }

    @Test
    fun primaryTimesOut_fallbackSucceeds() = runBlocking {
        val timeoutCalled = AtomicBoolean(false)

        val result = raceWithFallback(
            primaryAvailable = true,
            fallbackAvailable = true,
            primary = {
                delay(500) // Longer than timeout
                "primary"
            },
            fallback = { "fallback" },
            onPrimaryTimeout = { timeoutCalled.set(true) },
            timeoutMs = 100
        )

        assertEquals("fallback", result)
        assertTrue(timeoutCalled.get(), "onPrimaryTimeout should be called")
    }

    @Test
    fun primaryFails_fallbackSucceeds() = runBlocking {
        val errorCalled = AtomicBoolean(false)
        var caughtError: Throwable? = null

        val result = raceWithFallback(
            primaryAvailable = true,
            fallbackAvailable = true,
            primary = { throw RuntimeException("Primary failed") },
            fallback = { "fallback" },
            onPrimaryError = { e ->
                errorCalled.set(true)
                caughtError = e
            },
            timeoutMs = 1000
        )

        assertEquals("fallback", result)
        assertTrue(errorCalled.get(), "onPrimaryError should be called")
        assertEquals("Primary failed", caughtError?.message)
    }

    @Test
    fun primaryTimesOut_butThenWinsRace() = runBlocking {
        val primaryCalls = AtomicInteger(0)
        val fallbackCalls = AtomicInteger(0)

        val result = raceWithFallback(
            primaryAvailable = true,
            fallbackAvailable = true,
            primary = {
                primaryCalls.incrementAndGet()
                delay(150) // Takes longer than timeout but finishes before fallback
                "primary"
            },
            fallback = {
                fallbackCalls.incrementAndGet()
                delay(300) // Slower than primary
                "fallback"
            },
            timeoutMs = 100
        )

        assertEquals("primary", result)
        assertEquals(1, primaryCalls.get())
        assertEquals(1, fallbackCalls.get())
    }

    @Test
    fun primaryTimesOut_fallbackWinsRace() = runBlocking {
        val result = raceWithFallback(
            primaryAvailable = true,
            fallbackAvailable = true,
            primary = {
                delay(500) // Very slow
                "primary"
            },
            fallback = {
                delay(50) // Fast fallback
                "fallback"
            },
            timeoutMs = 100
        )

        assertEquals("fallback", result)
    }

    @Test
    fun bothFail_throwsFirstError() = runBlocking {
        val exception = assertFailsWith<RuntimeException> {
            raceWithFallback(
                primaryAvailable = true,
                fallbackAvailable = true,
                primary = { throw RuntimeException("Primary failed") },
                fallback = { throw RuntimeException("Fallback failed") },
                timeoutMs = 1000
            )
        }

        assertEquals("Primary failed", exception.message)
    }

    @Test
    fun onlyPrimaryAvailable_usesPrimary() = runBlocking {
        val fallbackCalled = AtomicBoolean(false)

        val result = raceWithFallback(
            primaryAvailable = true,
            fallbackAvailable = false,
            primary = { "primary" },
            fallback = {
                fallbackCalled.set(true)
                "fallback"
            },
            timeoutMs = 1000
        )

        assertEquals("primary", result)
        assertFalse(fallbackCalled.get(), "Fallback should not be called when unavailable")
    }

    @Test
    fun onlyFallbackAvailable_usesFallback() = runBlocking {
        val primaryCalled = AtomicBoolean(false)

        val result = raceWithFallback(
            primaryAvailable = false,
            fallbackAvailable = true,
            primary = {
                primaryCalled.set(true)
                "primary"
            },
            fallback = { "fallback" },
            timeoutMs = 1000
        )

        assertEquals("fallback", result)
        assertFalse(primaryCalled.get(), "Primary should not be called when unavailable")
    }

    @Test
    fun neitherAvailable_throwsException() = runBlocking {
        val exception = assertFailsWith<IllegalStateException> {
            raceWithFallback(
                primaryAvailable = false,
                fallbackAvailable = false,
                primary = { "primary" },
                fallback = { "fallback" },
                timeoutMs = 1000
            )
        }

        assertTrue(exception.message?.contains("No AI provider available") == true)
    }

    @Test
    fun primaryFails_fallbackAlsoFails_throwsFirstError() = runBlocking {
        val exception = assertFailsWith<IllegalArgumentException> {
            raceWithFallback(
                primaryAvailable = true,
                fallbackAvailable = true,
                primary = { throw IllegalArgumentException("Primary error") },
                fallback = { throw IllegalStateException("Fallback error") },
                timeoutMs = 1000
            )
        }

        assertEquals("Primary error", exception.message)
    }

    @Test
    fun timeoutCallbackNotCalledOnError() = runBlocking {
        val timeoutCalled = AtomicBoolean(false)
        val errorCalled = AtomicBoolean(false)

        raceWithFallback(
            primaryAvailable = true,
            fallbackAvailable = true,
            primary = { throw RuntimeException("Error") },
            fallback = { "fallback" },
            onPrimaryError = { errorCalled.set(true) },
            onPrimaryTimeout = { timeoutCalled.set(true) },
            timeoutMs = 1000
        )

        assertTrue(errorCalled.get(), "Error callback should be called")
        assertFalse(timeoutCalled.get(), "Timeout callback should NOT be called on error")
    }

    @Test
    fun primarySucceedsFast_fallbackNotStarted() = runBlocking {
        val fallbackStarted = AtomicBoolean(false)

        val result = raceWithFallback(
            primaryAvailable = true,
            fallbackAvailable = true,
            primary = { "primary" },
            fallback = {
                fallbackStarted.set(true)
                "fallback"
            },
            timeoutMs = 1000
        )

        assertEquals("primary", result)
        assertFalse(fallbackStarted.get(), "Fallback should not start when primary succeeds within timeout")
    }

    @Test
    fun fallbackFailsQuickly_primarySucceedsLater() = runBlocking {
        // Edge case: fallback fails immediately, but primary (still running) succeeds
        val result = raceWithFallback(
            primaryAvailable = true,
            fallbackAvailable = true,
            primary = {
                delay(200) // Slow but succeeds
                "primary"
            },
            fallback = {
                throw RuntimeException("Fallback failed immediately")
            },
            timeoutMs = 50 // Short timeout triggers fallback
        )

        assertEquals("primary", result, "Should return primary result even if fallback fails first")
    }

    @Test
    fun bothCompleteSimultaneously_primarySucceeds_fallbackFails() = runBlocking {
        // Both complete around the same time, primary succeeds, fallback fails
        val result = raceWithFallback(
            primaryAvailable = true,
            fallbackAvailable = true,
            primary = {
                delay(150)
                "primary"
            },
            fallback = {
                delay(100) // Fallback completes first but fails
                throw RuntimeException("Fallback failed")
            },
            timeoutMs = 50
        )

        assertEquals("primary", result, "Should return primary result when fallback fails")
    }

    @Test
    fun primaryFailsFirst_fallbackSucceedsLater() = runBlocking {
        val result = raceWithFallback(
            primaryAvailable = true,
            fallbackAvailable = true,
            primary = {
                delay(100)
                throw RuntimeException("Primary failed")
            },
            fallback = {
                delay(200)
                "fallback"
            },
            timeoutMs = 50
        )

        assertEquals("fallback", result, "Should return fallback result when primary fails first")
    }
}
