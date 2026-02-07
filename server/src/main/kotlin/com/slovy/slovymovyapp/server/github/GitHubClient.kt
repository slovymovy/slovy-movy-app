package com.slovy.slovymovyapp.server.github

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

    private fun loadToken(): String {
        return System.getenv(ENV_VAR_NAME)?.takeIf { it.isNotBlank() } ?: run {
            val keyFile = KEY_FILE_PATHS.map { File(it) }.firstOrNull { it.exists() }
            require(keyFile != null) {
                "Missing ${KEY_FILE_PATHS.joinToString(" or ")} file and $ENV_VAR_NAME environment variable"
            }
            keyFile.readText().trim()
        }
    }

    private fun readContentText(content: GHContent): String {
        val encoding = content.encoding
        // GitHub returns encoding "none" (empty content) for larger files; use downloadUrl to fetch raw bytes.
        if (encoding == null || encoding == "none") {
            val downloadUrl = content.downloadUrl
                ?: throw IllegalStateException("Missing download URL for content with encoding '$encoding'")
            return URI.create(downloadUrl).toURL().openStream().bufferedReader().use { it.readText() }
        }
        return content.read().bufferedReader().use { it.readText() }
    }
}
