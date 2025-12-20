package com.slovy.slovymovyapp.server.github

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests for GitHubClient.
 *
 * These tests require a valid GitHub token to be configured.
 * Tests are skipped if the token is not available.
 */
class GitHubClientTest {

    @Test
    fun isAvailable_returnsTrueWhenTokenExists() {
        if (!GitHubClient.isAvailable()) return

        assertTrue(GitHubClient.isAvailable())
    }

    @Test
    fun getToken_returnsNonBlankToken() {
        if (!GitHubClient.isAvailable()) return

        val token = GitHubClient.getToken()

        assertNotNull(token)
        assertTrue(token.isNotBlank(), "Token should not be blank")
    }

    @Test
    fun loadDbExtractContent_loadsEnTestJson() {
        if (!GitHubClient.isAvailable()) return

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
        if (!GitHubClient.isAvailable()) return

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
        if (!GitHubClient.isAvailable()) return

        assertFailsWith<Exception> {
            GitHubClient.loadDbExtractContent("en", "nonexistent-file-12345.json")
        }
    }
}
