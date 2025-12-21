package com.slovy.slovymovyapp.server.github

import org.kohsuke.github.GitHub
import org.kohsuke.github.GitHubBuilder
import java.io.File

/**
 * GitHub client provider for accessing private repository content.
 *
 * Token is loaded from:
 * 1. Environment variable: ACCESS_TO_GH_TOKEN
 * 2. Fallback file: server/.github_key (relative to working directory)
 */
object GitHubClient {

    private const val ENV_VAR_NAME = "ACCESS_TO_GH_TOKEN"
    private val KEY_FILE_PATHS = listOf("server/.github_key", ".github_key")

    private const val REPO_OWNER = "slovymovy"
    private const val REPO_NAME = "kaikki-parser"
    private const val BASE_PATH = "output/db-extract"
    private const val WORDS_PATH = "words"
    private const val DEFAULT_BRANCH = "main"

    private val clientInstance: GitHub by lazy {
        val token = loadToken()
        GitHubBuilder()
            .withOAuthToken(token)
            .build()
    }

    /**
     * Checks if the GitHub token is available.
     * Does not validate the token, only checks for presence.
     */
    fun isAvailable(): Boolean {
        return try {
            System.getenv(ENV_VAR_NAME)?.takeIf { it.isNotBlank() } != null ||
                KEY_FILE_PATHS.any { File(it).exists() }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Returns the GitHub client instance.
     * Creates the client lazily on first access.
     *
     * @throws IllegalStateException if token is not available
     */
    fun client(): GitHub = clientInstance

    /**
     * Loads file content from the kaikki-parser repository.
     *
     * @param folder The folder within output/db-extract (e.g., "en")
     * @param file The filename (e.g., "test.json")
     * @return The raw file content as a String
     * @throws IllegalStateException if token is not available
     * @throws org.kohsuke.github.GHFileNotFoundException if file does not exist
     */
    fun loadDbExtractContent(folder: String, file: String): String {
        val path = "$BASE_PATH/$folder/$file"
        return loadFileContent(REPO_OWNER, REPO_NAME, path, DEFAULT_BRANCH)
    }

    /**
     * Loads pre-processed word content from the kaikki-parser repository.
     * Pre-processed files are already in LanguageCardResponse format.
     *
     * @param lang The language code (e.g., "en")
     * @param word The word to load (e.g., "test")
     * @return The raw file content as a String
     * @throws IllegalStateException if token is not available
     * @throws org.kohsuke.github.GHFileNotFoundException if file does not exist
     */
    fun loadWordsContent(lang: String, word: String): String {
        val path = "$WORDS_PATH/$lang/$word.json"
        return loadFileContent(REPO_OWNER, REPO_NAME, path, DEFAULT_BRANCH)
    }

    /**
     * Loads file content from any repository.
     *
     * @param owner Repository owner
     * @param repo Repository name
     * @param path File path within the repository
     * @param ref Branch, tag, or commit SHA (defaults to main)
     * @return The raw file content as a String
     */
    fun loadFileContent(
        owner: String,
        repo: String,
        path: String,
        ref: String = DEFAULT_BRANCH
    ): String {
        val repository = client().getRepository("$owner/$repo")
        val content = repository.getFileContent(path, ref)
        return content.read().bufferedReader().use { it.readText() }
    }

    /**
     * Returns the GitHub token.
     *
     * @throws IllegalArgumentException if token is not available
     */
    fun getToken(): String = loadToken()

    private fun loadToken(): String {
        return System.getenv(ENV_VAR_NAME)?.takeIf { it.isNotBlank() } ?: run {
            val keyFile = KEY_FILE_PATHS.map { File(it) }.firstOrNull { it.exists() }
            require(keyFile != null) {
                "Missing ${KEY_FILE_PATHS.joinToString(" or ")} file and $ENV_VAR_NAME environment variable"
            }
            keyFile.readText().trim()
        }
    }
}
