package com.slovy.slovymovyapp.server.github

import com.google.common.util.concurrent.Uninterruptibles
import org.kohsuke.github.GHFileNotFoundException
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.test.*

/**
 * Integration tests for GitHubClient.
 *
 * These tests require a valid GitHub token to be configured.
 * Tests are skipped if the token is not available.
 */
class GitHubClientTest {

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

            // Branch should now exist
            assertTrue(GitHubClient.branchExists(testBranch), "Test branch should exist after creation")

            // Calling ensureBranch again should return existing branch
            val existingRef = GitHubClient.ensureBranch(testBranch)
            assertNotNull(existingRef, "Existing branch ref should not be null")
        } finally {
            // Cleanup
            cleanUpBranch(testBranch)
        }
    }

    private fun cleanUpBranch(testBranch: String) {
        if (GitHubClient.branchExists(testBranch)) {
            try {
                GitHubClient.deleteBranch(testBranch)
            } catch (_: Exception) {
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

            // Create initial content
            val initialContent = """{"entries": [], "word_family": null}"""
            GitHubClient.createWordsContentOnBranch(
                lang = testLang,
                word = testWord,
                content = initialContent,
                commitMessage = "Test: Create $testWord",
                branchName = testBranch
            )

            // Read content back
            val (readContent, sha) = GitHubClient.loadWordsContentFromBranch(testLang, testWord, testBranch)
            assertEquals(initialContent, readContent, "Read content should match initial content")
            assertNotNull(sha, "SHA should not be null")
            assertTrue(sha.isNotBlank(), "SHA should not be blank")

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

            // Read updated content
            val (finalContent, _) = GitHubClient.loadWordsContentFromBranch(testLang, testWord, testBranch)
            assertEquals(updatedContent, finalContent, "Read content should match updated content")
        } finally {
            cleanUpBranch(testBranch)
        }
    }

    @Test
    fun fileOperations_throwsForNonExistentFileOnBranch() {
        val testBranch = "test-${UUID.randomUUID()}"

        try {
            // Create test branch
            GitHubClient.ensureBranch(testBranch)

            // Try to load non-existent file
            assertFailsWith<GHFileNotFoundException> {
                GitHubClient.loadWordsContentFromBranch("test", "nonexistent-word", testBranch)
            }
        } finally {
            cleanUpBranch(testBranch)
        }
    }
}
