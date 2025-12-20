package com.slovy.slovymovyapp.server.github

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Integration tests for GitHubClient.
 *
 * These tests require a valid GitHub token to be configured.
 * Tests are skipped if the token is not available.
 */
class GitHubClientTest {

    @Test
    fun isAvailable_returnsTrueWhenTokenExists() {
        assumeTrue(GitHubClient.isAvailable(), "Skipping: GitHub token not available")

        assertTrue(GitHubClient.isAvailable())
    }

    @Test
    fun getToken_returnsNonBlankToken() {
        assumeTrue(GitHubClient.isAvailable(), "Skipping: GitHub token not available")

        val token = GitHubClient.getToken()

        assertNotNull(token)
        assertTrue(token.isNotBlank(), "Token should not be blank")
    }

    @Test
    fun loadDbExtractContent_loadsEnTestJson() {
        assumeTrue(GitHubClient.isAvailable(), "Skipping: GitHub token not available")

        val content = GitHubClient.loadDbExtractContent("en", "test.json")

        assertNotNull(content)
        assertTrue(content.isNotBlank(), "Content should not be blank")
        assertTrue(
            content.trimStart().startsWith("{") || content.trimStart().startsWith("["),
            "Content should be valid JSON"
        )
    }

    @Test
    fun loadFileContent_loadsSpecificFile() {
        assumeTrue(GitHubClient.isAvailable(), "Skipping: GitHub token not available")

        val content = GitHubClient.loadFileContent(
            owner = "slovymovy",
            repo = "kaikki-parser",
            path = "output/db-extract/en/test.json",
            ref = "main"
        )

        assertNotNull(content)
        assertTrue(content.isNotBlank(), "Content should not be blank")
    }

    @Test
    fun loadDbExtractContent_throwsForNonExistentFile() {
        assumeTrue(GitHubClient.isAvailable(), "Skipping: GitHub token not available")

        assertThrows<Exception> {
            GitHubClient.loadDbExtractContent("en", "nonexistent-file-12345.json")
        }
    }
}
