package com.slovy.slovymovyapp.server.github

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.kohsuke.github.*
import java.io.File
import java.net.URI

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
    private const val REPO_NAME = "words"
    private const val BASE_PATH = "db-extract"
    private const val WORDS_PATH = "words"
    private const val DEFAULT_BRANCH = "main"
    private const val PUSH_BRANCH = "push"
    private const val FEEDBACK_LABEL = "feedback"
    private const val FEEDBACK_LABEL_COLOR = "0E8A16"
    private const val FEEDBACK_LABEL_DESCRIPTION = "Feedback reported from application users"

    private const val GRAPHQL_ENDPOINT = "https://api.github.com/graphql"
    private const val FEEDBACK_DISCUSSION_CATEGORY = "Feedback"
    private const val DISCUSSION_REPO_OWNER = "slovymovy"
    private const val DISCUSSION_REPO_NAME = "slovy-movy-app"

    data class CreatedDiscussion(
        val number: Int,
        val title: String,
        val url: String
    )

    data class CreatedIssue(
        val number: Int,
        val title: String,
        val htmlUrl: String
    )

    private val clientInstance: GitHub by lazy {
        val token = loadToken()
        GitHubBuilder()
            .withOAuthToken(token)
            .build()
    }

    private val httpClient by lazy {
        HttpClient(CIO) {
            install(HttpRedirect)
        }
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
     * Loads file content from the words repository.
     *
     * @param folder The folder within db-extract (e.g., "en")
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
     * Loads pre-processed word content from the words repository.
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
        return readContentText(content)
    }

    /**
     * Returns the GitHub token.
     *
     * @throws IllegalArgumentException if token is not available
     */
    fun getToken(): String = loadToken()

    /**
     * Checks if the push branch exists.
     */
    fun pushBranchExists(): Boolean {
        return branchExists(PUSH_BRANCH)
    }

    /**
     * Checks if a branch exists.
     */
    fun branchExists(branchName: String): Boolean {
        return try {
            val repository = client().getRepository("$REPO_OWNER/$REPO_NAME")
            repository.getRef("heads/$branchName")
            true
        } catch (_: GHFileNotFoundException) {
            false
        }
    }

    /**
     * Deletes a branch. Used for test cleanup.
     *
     * @throws GHFileNotFoundException if branch does not exist
     */
    fun deleteBranch(branchName: String) {
        val repository = client().getRepository("$REPO_OWNER/$REPO_NAME")
        val ref = repository.getRef("heads/$branchName")
        ref.delete()
    }

    /**
     * Ensures the push branch exists, creating it from main if necessary.
     *
     * @return The GHRef for the push branch
     */
    fun ensurePushBranch(): GHRef {
        return ensureBranch(PUSH_BRANCH)
    }

    /**
     * Ensures a branch exists, creating it from main if necessary.
     *
     * @param branchName The branch name to ensure exists
     * @return The GHRef for the branch
     */
    fun ensureBranch(branchName: String): GHRef {
        val repository = client().getRepository("$REPO_OWNER/$REPO_NAME")
        return try {
            repository.getRef("heads/$branchName")
        } catch (_: GHFileNotFoundException) {
            val mainRef = repository.getRef("heads/$DEFAULT_BRANCH")
            val mainSha = mainRef.`object`.sha
            try {
                repository.createRef("refs/heads/$branchName", mainSha)
            } catch (_: Exception) {
                repository.getRef("heads/$branchName")
            }
        }
    }

    /**
     * Loads word content from the push branch.
     *
     * @param lang The language code (e.g., "en")
     * @param word The word to load (e.g., "test")
     * @return Pair of (content, sha) where sha is needed for updates
     * @throws GHFileNotFoundException if file does not exist
     */
    fun loadWordsContentFromPushBranch(lang: String, word: String): Pair<String, String> {
        return loadWordsContentFromBranch(lang, word, PUSH_BRANCH)
    }

    /**
     * Loads word content from a specific branch.
     *
     * @param lang The language code (e.g., "en")
     * @param word The word to load (e.g., "test")
     * @param branchName The branch to load from
     * @return Pair of (content, sha) where sha is needed for updates
     * @throws GHFileNotFoundException if file does not exist
     */
    fun loadWordsContentFromBranch(lang: String, word: String, branchName: String): Pair<String, String> {
        val path = "$WORDS_PATH/$lang/$word.json"
        val repository = client().getRepository("$REPO_OWNER/$REPO_NAME")
        val content = repository.getFileContent(path, branchName)
        val text = readContentText(content)
        return text to content.sha
    }

    /**
     * Creates a new word file on the push branch.
     *
     * @param lang Language code
     * @param word Word identifier
     * @param content JSON content to write
     * @param commitMessage Commit message
     */
    fun createWordsContent(lang: String, word: String, content: String, commitMessage: String) {
        createWordsContentOnBranch(lang, word, content, commitMessage, PUSH_BRANCH)
    }

    /**
     * Creates a new word file on a specific branch.
     *
     * @param lang Language code
     * @param word Word identifier
     * @param content JSON content to write
     * @param commitMessage Commit message
     * @param branchName The branch to create the file on
     */
    fun createWordsContentOnBranch(
        lang: String,
        word: String,
        content: String,
        commitMessage: String,
        branchName: String
    ) {
        val path = "$WORDS_PATH/$lang/$word.json"
        val repository = client().getRepository("$REPO_OWNER/$REPO_NAME")
        repository.createContent()
            .content(content.toByteArray())
            .message(commitMessage)
            .path(path)
            .branch(branchName)
            .commit()
    }

    /**
     * Updates an existing word file on the push branch.
     * Requires the current SHA of the file for optimistic locking.
     *
     * @param lang Language code
     * @param word Word identifier
     * @param content JSON content to write
     * @param fileSha Current SHA of the file (for optimistic locking)
     * @param commitMessage Commit message
     * @throws org.kohsuke.github.HttpException with 409 status on conflict
     */
    fun updateWordsContent(
        lang: String,
        word: String,
        content: String,
        fileSha: String,
        commitMessage: String
    ) {
        updateWordsContentOnBranch(lang, word, content, fileSha, commitMessage, PUSH_BRANCH)
    }

    /**
     * Updates an existing word file on a specific branch.
     * Requires the current SHA of the file for optimistic locking.
     *
     * @param lang Language code
     * @param word Word identifier
     * @param content JSON content to write
     * @param fileSha Current SHA of the file (for optimistic locking)
     * @param commitMessage Commit message
     * @param branchName The branch to update the file on
     * @throws org.kohsuke.github.HttpException with 409 status on conflict
     */
    fun updateWordsContentOnBranch(
        lang: String,
        word: String,
        content: String,
        fileSha: String,
        commitMessage: String,
        branchName: String
    ) {
        val path = "$WORDS_PATH/$lang/$word.json"
        val repository = client().getRepository("$REPO_OWNER/$REPO_NAME")
        repository.createContent()
            .content(content.toByteArray())
            .message(commitMessage)
            .path(path)
            .branch(branchName)
            .sha(fileSha)
            .commit()
    }

    /**
     * Creates a feedback issue in the words repository and applies the "feedback" label.
     */
    fun createFeedbackIssue(
        lang: String,
        word: String,
        comment: String,
        translationCodes: List<String> = emptyList(),
        email: String? = null
    ): CreatedIssue {
        val repository = client().getRepository("$REPO_OWNER/$REPO_NAME")
        ensureFeedbackLabelExists(repository)
        val title = buildFeedbackIssueTitle(
            lang = lang,
            word = word,
            translationCodes = translationCodes
        )
        val body = buildFeedbackIssueBody(
            lang = lang,
            word = word,
            translationCodes = translationCodes,
            comment = comment,
            email = email
        )
        val issue = repository.createIssue(title)
            .body(body)
            .label(FEEDBACK_LABEL)
            .create()
        return CreatedIssue(
            number = issue.number,
            title = issue.title,
            htmlUrl = issue.htmlUrl.toString()
        )
    }

    /**
     * Closes an issue in the words repository.
     */
    fun closeIssue(issueNumber: Int) {
        val repository = client().getRepository("$REPO_OWNER/$REPO_NAME")
        repository.getIssue(issueNumber).close()
    }

    internal fun buildFeedbackIssueTitle(lang: String, word: String, translationCodes: List<String>): String {
        val translations = normalizeTranslationCodes(translationCodes)
        val translationSuffix = if (translations.isEmpty()) "n/a" else translations.joinToString(",")
        return "Feedback: [$lang] $word (translations: $translationSuffix)"
    }

    internal fun buildFeedbackIssueBody(
        lang: String,
        word: String,
        translationCodes: List<String>,
        comment: String,
        email: String? = null
    ): String {
        val translations = normalizeTranslationCodes(translationCodes)
        val translationLine = if (translations.isEmpty()) "n/a" else translations.joinToString(", ")
        return buildString {
            appendLine("Feedback submitted from application.")
            appendLine()
            appendLine("- Language: `$lang`")
            appendLine("- Word: `$word`")
            appendLine("- Translation codes: `$translationLine`")
            if (!email.isNullOrBlank()) {
                val obfuscated = email.trim().replace("@", " [$word] ")
                appendLine("- Email: `$obfuscated`")
            }
            appendLine()
            appendLine("Comment:")
            appendLine(comment.trim())
        }
    }

    private fun normalizeTranslationCodes(translationCodes: List<String>): List<String> {
        return translationCodes
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    private fun ensureFeedbackLabelExists(repository: GHRepository) {
        try {
            repository.getLabel(FEEDBACK_LABEL)
        } catch (_: GHFileNotFoundException) {
            repository.createLabel(
                FEEDBACK_LABEL,
                FEEDBACK_LABEL_COLOR,
                FEEDBACK_LABEL_DESCRIPTION
            )
        }
    }

    /**
     * Creates a feedback discussion in the slovy-movy-app repository.
     */
    fun createFeedbackDiscussion(
        comment: String,
        email: String? = null
    ): CreatedDiscussion {
        val (repoId, categoryId) = resolveDiscussionCategoryId()
        val title = "App feedback"
        val body = buildGeneralFeedbackBody(comment, email)

        val mutation = """
            mutation CreateDiscussion(${'$'}repoId: ID!, ${'$'}categoryId: ID!, ${'$'}title: String!, ${'$'}body: String!) {
              createDiscussion(input: {repositoryId: ${'$'}repoId, categoryId: ${'$'}categoryId, title: ${'$'}title, body: ${'$'}body}) {
                discussion {
                  number
                  title
                  url
                }
              }
            }
        """.trimIndent()

        val variables = buildJsonObject {
            put("repoId", repoId)
            put("categoryId", categoryId)
            put("title", title)
            put("body", body)
        }

        val result = executeGraphQL(mutation, variables)
        val discussion = result.jsonObject["data"]
            ?.jsonObject?.get("createDiscussion")
            ?.jsonObject?.get("discussion")
            ?.jsonObject
            ?: throw IllegalStateException(
                "Failed to create discussion: ${result.jsonObject["errors"] ?: result}"
            )

        return CreatedDiscussion(
            number = discussion["number"]!!.jsonPrimitive.int,
            title = discussion["title"]!!.jsonPrimitive.content,
            url = discussion["url"]!!.jsonPrimitive.content
        )
    }

    internal fun buildGeneralFeedbackBody(comment: String, email: String? = null): String {
        return buildString {
            appendLine("Feedback submitted from application.")
            appendLine()
            if (!email.isNullOrBlank()) {
                val obfuscated = email.trim().replace("@", " [feedback] ")
                appendLine("- Email: `$obfuscated`")
                appendLine()
            }
            appendLine("Comment:")
            appendLine(comment.trim())
        }
    }

    @Volatile
    private var cachedDiscussionIds: Pair<String, String>? = null

    private fun executeGraphQL(query: String, variables: JsonObject? = null): JsonElement {
        val requestBody = buildJsonObject {
            put("query", query)
            if (variables != null) put("variables", variables)
        }

        val (statusCode, responseBody) = runBlocking {
            val response = httpClient.post(GRAPHQL_ENDPOINT) {
                header("Authorization", "bearer ${getToken()}")
                contentType(ContentType.Application.Json)
                setBody(requestBody.toString())
            }
            response.status.value to response.bodyAsText()
        }

        val parsed = Json.parseToJsonElement(responseBody)

        if (statusCode != 200) {
            throw IllegalStateException(
                "GraphQL request failed with status $statusCode: $responseBody"
            )
        }

        val errors = parsed.jsonObject["errors"]
        if (errors != null && errors is JsonArray && errors.isNotEmpty()) {
            throw IllegalStateException("GraphQL errors: $errors")
        }

        return parsed
    }

    private fun resolveDiscussionCategoryId(): Pair<String, String> {
        cachedDiscussionIds?.let { return it }

        val query = """
            query(${'$'}owner: String!, ${'$'}name: String!) {
              repository(owner: ${'$'}owner, name: ${'$'}name) {
                id
                discussionCategories(first: 25) {
                  nodes {
                    id
                    name
                  }
                }
              }
            }
        """.trimIndent()

        val variables = buildJsonObject {
            put("owner", DISCUSSION_REPO_OWNER)
            put("name", DISCUSSION_REPO_NAME)
        }

        val result = executeGraphQL(query, variables)
        val repo = result.jsonObject["data"]?.jsonObject?.get("repository")?.jsonObject
            ?: throw IllegalStateException("Repository $DISCUSSION_REPO_OWNER/$DISCUSSION_REPO_NAME not found")

        val repoId = repo["id"]!!.jsonPrimitive.content
        val categories = repo["discussionCategories"]?.jsonObject?.get("nodes")?.jsonArray
            ?: throw IllegalStateException("No discussion categories found")

        val feedbackCategory = categories.firstOrNull {
            it.jsonObject["name"]?.jsonPrimitive?.content == FEEDBACK_DISCUSSION_CATEGORY
        }?.jsonObject ?: throw IllegalStateException(
            "'$FEEDBACK_DISCUSSION_CATEGORY' discussion category not found in $DISCUSSION_REPO_OWNER/$DISCUSSION_REPO_NAME"
        )

        val categoryId = feedbackCategory["id"]!!.jsonPrimitive.content
        val ids = repoId to categoryId
        cachedDiscussionIds = ids
        return ids
    }

    private fun loadToken(): String {
        return System.getenv(ENV_VAR_NAME)?.takeIf { it.isNotBlank() } ?: run {
            val keyFile = KEY_FILE_PATHS.map { File(it) }.firstOrNull { it.exists() }
            require(keyFile != null) {
                "Missing ${KEY_FILE_PATHS.joinToString(" or ")} file and $ENV_VAR_NAME environment variable"
            }
            keyFile.readText().trim()
        }
    }

    /**
     * Percent-encodes non-ASCII characters in a URL (e.g. Cyrillic filenames → %D0%B2).
     * [java.net.URL] parses the raw string leniently; [URI]'s 5-arg constructor then
     * re-encodes the decoded path to ASCII while leaving the query string untouched.
     */
    @Suppress("DEPRECATION")
    private fun encodeUrl(rawUrl: String): String {
        val url = java.net.URL(rawUrl)
        return URI(url.protocol, url.authority, url.path, url.query, null).toASCIIString()
    }

    private fun readContentText(content: GHContent): String {
        val encoding = content.encoding
        // GitHub returns encoding "none" (empty content) for larger files; use downloadUrl to fetch raw bytes.
        if (encoding == null || encoding == "none") {
            val downloadUrl = content.downloadUrl
                ?: throw IllegalStateException("Missing download URL for content with encoding '$encoding'")
            // Percent-encode the URL using URI's 5-arg constructor, which takes a decoded path
            // and produces proper RFC 3986 encoding (e.g. Cyrillic filenames → %D0%B2.json).
            val safeUrl = encodeUrl(downloadUrl)
            return runBlocking {
                val response = httpClient.get(safeUrl)
                if (!response.status.isSuccess()) {
                    throw IllegalStateException("Failed to download $safeUrl: HTTP ${response.status.value}")
                }
                response.bodyAsText()
            }
        }
        return content.read().bufferedReader().use { it.readText() }
    }
}
