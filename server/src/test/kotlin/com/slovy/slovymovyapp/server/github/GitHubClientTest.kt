package com.slovy.slovymovyapp.server.github

import com.google.common.util.concurrent.Uninterruptibles
import org.kohsuke.github.GHFileNotFoundException
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.test.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Integration tests for GitHubClient.
 *
 * These tests require a valid GitHub token to be configured.
 * Tests are skipped if the token is not available.
 */
class GitHubClientTest {

    /**
     * Retries the given block until it succeeds or the timeout is reached.
     * This is necessary due to GitHub's eventual consistency and caching behavior.
     *
     * @param timeout Maximum time to wait for the assertion to pass
     * @param pollInterval Time to wait between retry attempts
     * @param block The assertion block to retry
     * @throws AssertionError if the block doesn't succeed within the timeout
     */
    private fun eventually(
        timeout: Duration = 15.seconds,
        pollInterval: Duration = 500.milliseconds,
        block: () -> Unit
    ) {
        val startTime = System.currentTimeMillis()
        val timeoutMillis = timeout.inWholeMilliseconds
        var lastError: Throwable? = null

        while (System.currentTimeMillis() - startTime < timeoutMillis) {
            try {
                block()
                return // Success
            } catch (e: AssertionError) {
                lastError = e
                Thread.sleep(pollInterval.inWholeMilliseconds)
            } catch (e: Exception) {
                // For non-assertion exceptions (like GHFileNotFoundException),
                // we don't retry - rethrow immediately
                throw e
            }
        }

        // Timeout reached - throw the last error
        throw AssertionError(
            "Assertion failed after ${timeout.inWholeSeconds}s timeout. Last error: ${lastError?.message}",
            lastError
        )
    }

    @Test
    fun isAvailable_returnsTrueWhenTokenExists() {
        assertTrue(GitHubClient.isAvailable())
    }

    @Test
    fun getToken_returnsNonBlankToken() {
        val token = GitHubClient.getToken()

        assertNotNull(token)
        assertTrue(token.isNotBlank(), "Token should not be blank")
    }

    @Test
    fun loadDbExtractContent_loadsEnTestJson() {
        val content = GitHubClient.loadDbExtractContent("en", "test.json")

        assertNotNull(content)
        assertTrue(content.isNotBlank(), "Content should not be blank")
        assertTrue(
            content.trimStart().startsWith("{") || content.trimStart().startsWith("["),
            "Content should be valid JSON"
        )
    }

    @Test
    fun loadDbExtractContent_loadsEnFireJson() {
        val content = GitHubClient.loadDbExtractContent("en", "fire.json")

        assertNotNull(content)
        assertTrue(content.isNotBlank(), "Content should not be blank")
        assertTrue(
            content.trimStart().startsWith("{") || content.trimStart().startsWith("["),
            "Content should be valid JSON"
        )
    }

    @Test
    fun loadFileContent_loadsSpecificFile() {
        val content = GitHubClient.loadFileContent(
            owner = "slovymovy",
            repo = "words",
            path = "db-extract/en/test.json",
            ref = "main"
        )

        assertNotNull(content)
        assertTrue(content.isNotBlank(), "Content should not be blank")
    }

    @Test
    fun loadDbExtractContent_loadsRuCyrillicFile() {
        // Verifies that Cyrillic filenames in download URLs are correctly percent-encoded
        // (e.g. "в" → "%D0%B2") so the HTTP request succeeds.
        val content = GitHubClient.loadDbExtractContent("ru", "в.json")

        assertNotNull(content)
        assertTrue(content.isNotBlank(), "Content should not be blank")
        assertTrue(
            content.trimStart().startsWith("{") || content.trimStart().startsWith("["),
            "Content should be valid JSON"
        )
    }

    @Test
    fun loadDbExtractContent_throwsForNonExistentFile() {
        assertFailsWith<Exception> {
            GitHubClient.loadDbExtractContent("en", "nonexistent-file-12345.json")
        }
    }

    @Test
    fun loadWordsContent_loadsPreProcessedData() {
        // Test loading from words/ directory - file may or may not exist
        try {
            val content = GitHubClient.loadWordsContent("en", "test")

            assertNotNull(content)
            assertTrue(content.isNotBlank(), "Content should not be blank")
            assertTrue(
                content.trimStart().startsWith("{"),
                "Content should be valid JSON object"
            )
        } catch (_: GHFileNotFoundException) {
            // Expected if no pre-processed files exist yet
        }
    }

    @Test
    fun loadWordsContent_throwsForNonExistentFile() {
        assertFailsWith<GHFileNotFoundException> {
            GitHubClient.loadWordsContent("en", "nonexistent-word-12345")
        }
    }

    // --- Branch and File Operation Tests ---
    // These tests use a random test branch that is cleaned up after each test

    @Test
    fun branchOperations_createCheckAndDeleteBranch() {
        val testBranch = "test-${UUID.randomUUID()}"

        try {
            // Branch should not exist initially
            assertFalse(GitHubClient.branchExists(testBranch), "Test branch should not exist initially")

            // Create branch
            val ref = GitHubClient.ensureBranch(testBranch)
            assertNotNull(ref, "Created branch ref should not be null")

            // Branch should now exist (retry due to GitHub caching)
            eventually {
                assertTrue(GitHubClient.branchExists(testBranch), "Test branch should exist after creation")
            }

            // Calling ensureBranch again should return existing branch
            val existingRef = GitHubClient.ensureBranch(testBranch)
            assertNotNull(existingRef, "Existing branch ref should not be null")
        } finally {
            // Cleanup
            cleanUpBranch(testBranch)
        }
    }

    private fun cleanUpBranch(testBranch: String) {
        // Use retry logic to check if branch exists before deletion (handles caching)
        var branchExists = false
        try {
            eventually(timeout = 10.seconds) {
                branchExists = GitHubClient.branchExists(testBranch)
                assertTrue(branchExists, "Branch should be visible for cleanup")
            }
        } catch (_: AssertionError) {
            // Branch doesn't exist, nothing to clean up
            return
        }

        if (branchExists) {
            try {
                GitHubClient.deleteBranch(testBranch)
            } catch (_: Exception) {
                // Retry deletion after delay if it fails
                Uninterruptibles.sleepUninterruptibly(10, TimeUnit.SECONDS)
                GitHubClient.deleteBranch(testBranch)
            }
        }
    }

    @Test
    fun fileOperations_createReadAndUpdateFile() {
        val testBranch = "test-${UUID.randomUUID()}"
        val testWord = "test-word-${UUID.randomUUID()}"
        val testLang = "test"

        try {
            // Create test branch
            GitHubClient.ensureBranch(testBranch)

            // Wait for branch to exist (retry due to GitHub caching)
            eventually {
                assertTrue(GitHubClient.branchExists(testBranch), "Test branch should exist after creation")
            }

            // Create initial content
            val initialContent = """{"entries": [], "word_family": null}"""
            GitHubClient.createWordsContentOnBranch(
                lang = testLang,
                word = testWord,
                content = initialContent,
                commitMessage = "Test: Create $testWord",
                branchName = testBranch
            )

            // Read content back (retry due to GitHub caching)
            var readContent: String = ""
            var sha: String = ""
            eventually {
                val (content, fileSha) = GitHubClient.loadWordsContentFromBranch(testLang, testWord, testBranch)
                readContent = content
                sha = fileSha
                assertEquals(initialContent, readContent, "Read content should match initial content")
                assertNotNull(sha, "SHA should not be null")
                assertTrue(sha.isNotBlank(), "SHA should not be blank")
            }

            // Update content
            val updatedContent = """{"entries": [{"pos": "noun", "senses": []}], "word_family": ["test"]}"""
            GitHubClient.updateWordsContentOnBranch(
                lang = testLang,
                word = testWord,
                content = updatedContent,
                fileSha = sha,
                commitMessage = "Test: Update $testWord",
                branchName = testBranch
            )

            // Read updated content (retry due to GitHub caching)
            eventually {
                val (finalContent, _) = GitHubClient.loadWordsContentFromBranch(testLang, testWord, testBranch)
                assertEquals(updatedContent, finalContent, "Read content should match updated content")
            }
        } finally {
            cleanUpBranch(testBranch)
        }
    }

    @Test
    fun buildGeneralFeedbackBody_withEmail() {
        val body = GitHubClient.buildGeneralFeedbackBody("Great app!", "user@example.com")
        assertTrue(body.contains("Feedback submitted from application."), "Body should contain header")
        assertTrue(body.contains("Great app!"), "Body should contain comment")
        assertTrue(body.contains("[feedback]"), "Email should be obfuscated with [feedback]")
        assertFalse(body.contains("@example"), "Email @ should be replaced")
    }

    @Test
    fun buildGeneralFeedbackBody_withoutEmail() {
        val body = GitHubClient.buildGeneralFeedbackBody("Bug report")
        assertTrue(body.contains("Bug report"), "Body should contain comment")
        assertFalse(body.contains("Email"), "Body should not contain Email line")
    }

    @Test
    @Ignore("prevent repo pollution")
    fun createFeedbackDiscussion_createsAndReturns() {
        if (!GitHubClient.isAvailable()) return

        val discussion = GitHubClient.createFeedbackDiscussion(
            comment = "Integration test feedback - please ignore",
            email = null
        )

        assertTrue(discussion.number > 0, "Discussion number should be positive")
        assertEquals("App feedback", discussion.title)
        assertTrue(discussion.url.contains("github.com"), "URL should be a GitHub URL")
    }

    @Test
    fun fileOperations_throwsForNonExistentFileOnBranch() {
        val testBranch = "test-${UUID.randomUUID()}"

        try {
            // Create test branch
            GitHubClient.ensureBranch(testBranch)

            // Wait for branch to exist (retry due to GitHub caching)
            eventually {
                assertTrue(GitHubClient.branchExists(testBranch), "Test branch should exist after creation")
            }

            // Try to load non-existent file
            assertFailsWith<GHFileNotFoundException> {
                GitHubClient.loadWordsContentFromBranch("test", "nonexistent-word", testBranch)
            }
        } finally {
            cleanUpBranch(testBranch)
        }
    }
}
