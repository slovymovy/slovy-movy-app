package com.slovy.slovymovyapp

import com.openai.models.ChatModel
import com.slovy.slovymovyapp.api.WordStreamChunk
import com.slovy.slovymovyapp.api.WordStreamStage
import com.slovy.slovymovyapp.builder.ServerDbManager
import com.slovy.slovymovyapp.data.settings.Setting
import com.slovy.slovymovyapp.data.settings.SettingsRepository
import com.slovy.slovymovyapp.db.AppDatabase
import com.slovy.slovymovyapp.ingestion.ExtractedWordData
import com.slovy.slovymovyapp.ingestion.LanguageCardResponse
import com.slovy.slovymovyapp.server.ServerJson
import com.slovy.slovymovyapp.server.ai.GEMINI_3_1_FLASH_LITE
import com.slovy.slovymovyapp.server.ai.GeminiProvider
import com.slovy.slovymovyapp.server.ai.OpenAIProvider
import com.slovy.slovymovyapp.server.ai.enhancer.*
import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.server.ai.enhancer.DbExtractEnhancerUtils.targetLanguageName
import com.slovy.slovymovyapp.server.cloudrun.CloudTasksAuthVerifier
import com.slovy.slovymovyapp.server.github.GitHubClient
import com.slovy.slovymovyapp.server.github.WordDataMerger
import com.slovy.slovymovyapp.server.lists.LanguageListsBundle
import com.slovy.slovymovyapp.server.lists.LanguageListsLoader
import com.slovy.slovymovyapp.server.lists.LanguageListsResponse
import com.slovy.slovymovyapp.server.lists.ListsVersionResponse
import com.slovy.slovymovyapp.server.lists.TtlCache
import com.slovy.slovymovyapp.server.tasks.RepoUpdateTaskClient
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.logging.*
import kotlinx.coroutines.*
import kotlinx.coroutines.selects.select
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.jsonPrimitive
import org.kohsuke.github.GHFileNotFoundException
import org.kohsuke.github.HttpException
import org.slf4j.event.Level
import java.nio.file.Files
import kotlin.time.Duration.Companion.seconds

const val updateRepoPath = "/internal/update-repo/"
const val SERVER_PORT_ENV = "SERVER_PORT"
const val SERVER_PORT = 8080

/** Wire body shared by every feedback-style endpoint; the client posts this same shape to all. */
@Serializable
private data class FeedbackSubmissionRequest(
    val comment: String,
    val email: String? = null
)

/** Reply for the endpoints that create an issue: word feedback and list suggestions. */
@Serializable
private data class GitHubIssueResponse(
    val issueNumber: Int,
    val issueTitle: String,
    val issueUrl: String
)

/** Reply for general feedback, which creates a discussion rather than an issue. */
@Serializable
private data class GeneralFeedbackResponse(
    val discussionNumber: Int,
    val discussionTitle: String,
    val discussionUrl: String
)


fun main() {
    val port = System.getenv(SERVER_PORT_ENV)?.toIntOrNull() ?: SERVER_PORT
    embeddedServer(Netty, port = port, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {

    val db: AppDatabase = ServerDbManager(Files.createTempDirectory("openwords").toFile()).openApp()
    val repo = SettingsRepository(db)
    val listsLoader = LanguageListsLoader()
    val bundleCache = TtlCache<LanguageListsBundle>(loader = listsLoader::load)
    val versionCache = TtlCache<String>(loader = listsLoader::loadVersion)

    install(CallLogging) {
        level = Level.INFO
    }

    routing {
        get("/") {
            call.respondText(repo.run {
                getById(Setting.Name.WELCOME_MESSAGE)?.value?.jsonPrimitive?.content.toString()
            })
        }
        get("/health") {
            call.respondText(repo.run {
                "ok"
            })
        }

        if (isTestMode()) {
            testDataEndpoints()
            // Replaces the GitHub-backed /lists/{lang} endpoints below with an
            // in-memory store that tests populate via PUT /test/lists/{lang}.
            testListsEndpoints()
        } else {
            get("/lists/{lang}/version") {
                val lang = call.parameters["lang"]?.trim()
                if (lang.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest, "Missing lang parameter")
                    return@get
                }
                if (!GitHubClient.isAvailable()) {
                    call.respond(HttpStatusCode.ServiceUnavailable, "GitHub token not configured")
                    return@get
                }
                try {
                    // Prefer a fresh bundle's version (zero extra GitHub calls). Fall back to the
                    // dedicated version cache, which only does the cheap tree-walk and is unaffected
                    // by malformed list JSON or icon download failures.
                    val version = bundleCache.peekFresh(lang)?.version ?: versionCache.get(lang)
                    call.respondText(
                        ServerJson.lenient.encodeToString(
                            ListsVersionResponse.serializer(),
                            ListsVersionResponse(version)
                        ),
                        ContentType.Application.Json
                    )
                } catch (_: GHFileNotFoundException) {
                    call.respond(HttpStatusCode.NotFound, "Lists for language '$lang' not found")
                } catch (e: Exception) {
                    call.application.environment.log.error("Failed to fetch lists version for $lang: ${e.message}", e)
                    call.respond(HttpStatusCode.InternalServerError, "Failed to fetch lists version: ${e.message}")
                }
            }

            get("/lists/{lang}") {
                val lang = call.parameters["lang"]?.trim()
                if (lang.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest, "Missing lang parameter")
                    return@get
                }
                if (!GitHubClient.isAvailable()) {
                    call.respond(HttpStatusCode.ServiceUnavailable, "GitHub token not configured")
                    return@get
                }
                try {
                    val bundle = bundleCache.get(lang)
                    val response = LanguageListsResponse(version = bundle.version, lists = bundle.lists)
                    call.respondText(
                        ServerJson.lenient.encodeToString(LanguageListsResponse.serializer(), response),
                        ContentType.Application.Json
                    )
                } catch (_: GHFileNotFoundException) {
                    call.respond(HttpStatusCode.NotFound, "Lists for language '$lang' not found")
                } catch (e: Exception) {
                    call.application.environment.log.error("Failed to fetch lists for $lang: ${e.message}", e)
                    call.respond(HttpStatusCode.InternalServerError, "Failed to fetch lists: ${e.message}")
                }
            }
        }

        get("/extract/{lang}/{word}") {
            val lang = call.parameters["lang"]
            val word = call.parameters["word"]

            if (lang.isNullOrBlank() || word.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, "Missing lang or word parameter")
                return@get
            }

            if (!GitHubClient.isAvailable()) {
                call.respond(HttpStatusCode.ServiceUnavailable, "GitHub token not configured")
                return@get
            }

            try {
                val content = GitHubClient.loadDbExtractContent(lang, "$word.json")
                call.respondText(content, ContentType.Application.Json)
            } catch (_: GHFileNotFoundException) {
                call.respond(HttpStatusCode.NotFound, "Word '$word' not found for language '$lang'")
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, "Failed to fetch content: ${e.message}")
            }
        }

        get("/word/{lang}/{word}") {
            val lang = call.parameters["lang"]
            val word = call.parameters["word"]
            val push = call.parameters["push"]
            val translationsParam = call.request.queryParameters["translations"]

            if (lang.isNullOrBlank() || word.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, "Missing lang or word parameter")
                return@get
            }

            // The corpus this route reads and generates only exists for languages that are
            // studied, so reject the rest here rather than letting the words-repo lookup fail
            // obscurely further in. Requested translation targets are validated separately, by
            // targetLanguageName.
            if (Language.fromCodeOrNull(lang)?.supportedForLearning != true) {
                call.respond(HttpStatusCode.BadRequest, "Unsupported learning language: $lang")
                return@get
            }

            if (!GitHubClient.isAvailable()) {
                call.respond(HttpStatusCode.ServiceUnavailable, "GitHub token not configured")
                return@get
            }

            try {
                // Step 1: Prepare base data before starting the stream so we can still return errors
                val baseResult = loadBaseWordData(lang, word, logger = call.application.environment.log)

                // Step 2: Stream results as NDJSON (base, then translated if available)
                //
                // respondTextWriter swallows whatever this block throws: the writer runs outside the
                // handler's try/catch, so the client just sees the stream stop at whatever chunk was
                // already flushed and nothing reaches the logs. Everything in here has to report its
                // own failures, or a broken translation stage is invisible in production.
                call.respondTextWriter(contentType = ContentType.parse("application/x-ndjson")) {
                    val streamLogger = call.application.environment.log
                    try {
                        // Parse requested language codes for filtering.
                        // If no translations parameter provided, return empty list (no translations in response).
                        val requestedLangCodes = parseTranslationCodes(translationsParam)

                        // Send filtered base response to client (always filter based on requested codes)
                        val baseResponseToClient = filterTranslations(baseResult.response, requestedLangCodes)
                        val baseChunk = WordStreamChunk(WordStreamStage.BASE, baseResponseToClient)
                        write(ServerJson.lenient.encodeToString(WordStreamChunk.serializer(), baseChunk))
                        write("\n")
                        flush()

                        // Track full response for repo updates (unfiltered)
                        var fullResponse = baseResult.response
                        var wasProcessed = baseResult.wasProcessed

                        if (requestedLangCodes.isNotEmpty()) {
                            val translationResult = addMissingTranslations(
                                response = fullResponse,
                                lang = lang,
                                word = word,
                                requestedLangCodes = requestedLangCodes,
                                logger = streamLogger
                            )

                            if (translationResult.updated) {
                                fullResponse = translationResult.response
                                wasProcessed = true
                                // Send filtered translated response to client
                                val translatedResponseToClient = filterTranslations(fullResponse, requestedLangCodes)
                                val translatedChunk =
                                    WordStreamChunk(WordStreamStage.TRANSLATED, translatedResponseToClient)
                                write(ServerJson.lenient.encodeToString(WordStreamChunk.serializer(), translatedChunk))
                                write("\n")
                                flush()
                            }
                        }

                        // Step 3: Queue Cloud Tasks update if requested (only if something was processed)
                        // IMPORTANT: Use fullResponse (unfiltered) to ensure nothing is lost in repo
                        if (!push.isNullOrBlank() && wasProcessed) {
                            val responseJson = ServerJson.lenient.encodeToString(
                                LanguageCardResponse.serializer(),
                                fullResponse
                            )
                            RepoUpdateTaskClient.queueRepoUpdate(lang, word, responseJson)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        // The status line is already sent, so this cannot become an error response;
                        // logging it here is the only record that the stream ended early.
                        streamLogger.error("Failed to stream $lang/$word: ${e.message}", e)
                    }
                }
            } catch (e: GHFileNotFoundException) {
                call.application.environment.log.error("GitHub API error for $lang/$word: ${e.message}", e)
                call.respond(HttpStatusCode.NotFound, "Word '$word' not found for language '$lang'")
            } catch (e: Exception) {
                call.application.environment.log.error("Failed to process $lang/$word: ${e.message}", e)
                call.respond(HttpStatusCode.InternalServerError, "Failed to process word: ${e.message}")
            }
        }

        feedbackSubmission(
            path = "/feedback",
            artifactName = "feedback discussion"
        ) { _, comment, email ->
            val discussion = GitHubClient.createFeedbackDiscussion(comment = comment, email = email)
            ServerJson.lenient.encodeToString(
                GeneralFeedbackResponse.serializer(),
                GeneralFeedbackResponse(
                    discussionNumber = discussion.number,
                    discussionTitle = discussion.title,
                    discussionUrl = discussion.url
                )
            )
        }

        feedbackSubmission(
            path = "/list-suggestion/{lang}",
            requiredParams = listOf("lang"),
            artifactName = "list suggestion issue"
        ) { params, comment, email ->
            val createdIssue = GitHubClient.createListSuggestionIssue(
                lang = params.getValue("lang"),
                comment = comment,
                email = email
            )
            ServerJson.lenient.encodeToString(
                GitHubIssueResponse.serializer(),
                GitHubIssueResponse(
                    issueNumber = createdIssue.number,
                    issueTitle = createdIssue.title,
                    issueUrl = createdIssue.htmlUrl
                )
            )
        }

        feedbackSubmission(
            path = "/feedback/{lang}/{word}",
            requiredParams = listOf("lang", "word"),
            artifactName = "feedback issue"
        ) { params, comment, email ->
            val createdIssue = GitHubClient.createFeedbackIssue(
                lang = params.getValue("lang"),
                word = params.getValue("word"),
                translationCodes = parseTranslationCodes(call.request.queryParameters["translations"]),
                comment = comment,
                email = email
            )
            ServerJson.lenient.encodeToString(
                GitHubIssueResponse.serializer(),
                GitHubIssueResponse(
                    issueNumber = createdIssue.number,
                    issueTitle = createdIssue.title,
                    issueUrl = createdIssue.htmlUrl
                )
            )
        }

        // Internal endpoint for Cloud Tasks callbacks
        post("$updateRepoPath{lang}/{word}") {
            // Verify OIDC token from Cloud Tasks
            if (!CloudTasksAuthVerifier.verify(call.request.headers["Authorization"])) {
                call.respond(HttpStatusCode.Forbidden, "Not authorized")
                return@post
            }

            val lang = call.parameters["lang"]
            val word = call.parameters["word"]

            if (lang.isNullOrBlank() || word.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, "Missing lang or word parameter")
                return@post
            }

            if (!GitHubClient.isAvailable()) {
                call.respond(HttpStatusCode.ServiceUnavailable, "GitHub token not configured")
                return@post
            }

            try {
                val responseJson = call.receiveText()
                val incoming = ServerJson.strict.decodeFromString(
                    LanguageCardResponse.serializer(),
                    responseJson
                )
                call.application.environment.log.info("Received update for $lang/$word")

                if (!GitHubClient.pushBranchExists())
                    GitHubClient.ensurePushBranch()

                // Check if file exists and handle accordingly
                try {
                    val (existingContent, sha) = GitHubClient.loadWordsContentFromPushBranch(lang, word)
                    val existing = ServerJson.strict.decodeFromString(
                        LanguageCardResponse.serializer(),
                        existingContent
                    )
                    val merged = WordDataMerger.merge(existing, incoming)
                    val mergedJson = ServerJson.pretty.encodeToString(
                        LanguageCardResponse.serializer(),
                        merged
                    )

                    // Skip commit if content is identical
                    val existingPretty = ServerJson.pretty.encodeToString(
                        LanguageCardResponse.serializer(),
                        existing
                    )
                    if (mergedJson == existingPretty) {
                        call.application.environment.log.info("No changes for $lang/$word, skipping commit")
                    } else {
                        GitHubClient.updateWordsContent(lang, word, mergedJson, sha, "Update $word ($lang)")
                        call.application.environment.log.info("Merged and updated $lang/$word")
                    }
                } catch (_: GHFileNotFoundException) {
                    // File doesn't exist - create it
                    val prettyJson = ServerJson.pretty.encodeToString(
                        LanguageCardResponse.serializer(),
                        incoming
                    )
                    GitHubClient.createWordsContent(lang, word, prettyJson, "Add $word ($lang)")
                    call.application.environment.log.info("Created new file for $lang/$word")
                }

                call.respond(HttpStatusCode.OK, "Updated $lang/$word")
            } catch (e: HttpException) {
                if (e.responseCode == 409) {
                    // Conflict - Cloud Tasks will retry
                    call.application.environment.log.warn("Conflict detected for $lang/$word, will retry")
                    call.respond(HttpStatusCode.Conflict, "Concurrent modification detected, please retry")
                } else {
                    call.application.environment.log.error("GitHub API error for $lang/$word: ${e.message}", e)
                    call.respond(HttpStatusCode.InternalServerError, "GitHub API error: ${e.message}")
                }
            } catch (e: Exception) {
                call.application.environment.log.error("Failed to update $lang/$word: ${e.message}", e)
                call.respond(HttpStatusCode.InternalServerError, "Failed to update: ${e.message}")
            }
        }
    }
}

/**
 * Installs a POST route for one of the feedback-style endpoints, which all turn a comment plus an
 * optional email into a GitHub artifact and differ only in what they create.
 *
 * The shared pipeline, in order: every name in [requiredParams] must be present and non-blank,
 * a GitHub token must be configured, the body must decode as [FeedbackSubmissionRequest], and the
 * comment must be non-blank once trimmed. Only then does [createArtifact] run; whatever JSON it
 * returns is sent back as `201 Created`.
 *
 * @param requiredParams Path parameter names to validate, in the order they should be reported.
 * @param artifactName What the endpoint creates, e.g. "feedback issue". Names the operation in
 *   both the error log and the 500 response body.
 * @param createArtifact Creates the artifact and returns the response JSON. Receives the validated
 *   path parameters, the trimmed comment, and the raw email. Runs with the route's [RoutingContext]
 *   as receiver so it can still reach `call` for query parameters.
 */
private fun Route.feedbackSubmission(
    path: String,
    requiredParams: List<String> = emptyList(),
    artifactName: String,
    createArtifact: suspend RoutingContext.(
        params: Map<String, String>,
        comment: String,
        email: String?
    ) -> String
) {
    post(path) {
        val params = LinkedHashMap<String, String>(requiredParams.size)
        for (name in requiredParams) {
            val value = call.parameters[name]?.trim()
            if (value.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, "Missing $name parameter")
                return@post
            }
            params[name] = value
        }

        if (!GitHubClient.isAvailable()) {
            call.respond(HttpStatusCode.ServiceUnavailable, "GitHub token not configured")
            return@post
        }

        val submission = try {
            ServerJson.lenient.decodeFromString(
                FeedbackSubmissionRequest.serializer(),
                call.receiveText()
            )
        } catch (_: Exception) {
            call.respond(HttpStatusCode.BadRequest, "Invalid request body")
            return@post
        }

        val comment = submission.comment.trim()
        if (comment.isBlank()) {
            call.respond(HttpStatusCode.BadRequest, "Missing comment")
            return@post
        }

        try {
            val responseJson = createArtifact(params, comment, submission.email)
            call.respondText(
                text = responseJson,
                contentType = ContentType.Application.Json,
                status = HttpStatusCode.Created
            )
        } catch (e: Exception) {
            val context = if (params.isEmpty()) "" else " for ${params.values.joinToString("/")}"
            call.application.environment.log.error(
                "Failed to create $artifactName$context: ${e.message}",
                e
            )
            call.respond(HttpStatusCode.InternalServerError, "Failed to create $artifactName")
        }
    }
}

private fun parseTranslationCodes(source: String?): List<String> {
    val raw = source ?: return emptyList()
    return raw.split(',')
        .asSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
        .toList()
}

internal val AI_FALLBACK_TIMEOUT_MS = 20.seconds.inWholeMilliseconds

/**
 * Races between primary (Gemini) and fallback (OpenAI) AI providers.
 * - Starts primary immediately
 * - If primary doesn't respond within timeout or fails, starts fallback
 * - Returns first successful result, cancels the other
 */
internal suspend fun <T> raceWithFallback(
    primaryAvailable: Boolean,
    fallbackAvailable: Boolean,
    primary: suspend () -> T,
    fallback: suspend () -> T,
    onPrimaryError: (Throwable) -> Unit = {},
    onPrimaryTimeout: () -> Unit = {},
    timeoutMs: Long = AI_FALLBACK_TIMEOUT_MS
): T {
    if (!primaryAvailable && !fallbackAvailable) {
        throw IllegalStateException("No AI provider available (Gemini or OpenAI)")
    }

    // If only one provider is available, use it directly
    if (!primaryAvailable) {
        return fallback()
    }
    if (!fallbackAvailable) {
        return primary()
    }

    // Both providers available - race with fallback
    // Use supervisorScope so primary failure doesn't cancel the whole scope
    return supervisorScope {
        val primaryJob = async(Dispatchers.IO) { primary() }

        // Wait for primary with timeout
        var primaryFailed = false
        val primaryResult = try {
            withTimeoutOrNull(timeoutMs) {
                primaryJob.await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            primaryFailed = true
            onPrimaryError(e)
            null // Primary failed, will try fallback
        }

        // If primary succeeded within timeout, return it
        if (primaryResult != null) {
            return@supervisorScope primaryResult
        }

        // Log timeout if primary is still running (didn't fail)
        if (!primaryFailed && primaryJob.isActive) {
            onPrimaryTimeout()
        }

        // Primary timed out or failed - start fallback and race
        val fallbackJob = async(Dispatchers.IO) { fallback() }

        // Race between primary (still running) and fallback - take first success
        try {
            select {
                primaryJob.onAwait { result ->
                    fallbackJob.cancel()
                    result
                }
                fallbackJob.onAwait { result ->
                    primaryJob.cancel()
                    result
                }
            }
        } catch (firstError: Throwable) {
            // One job failed. Try to get a successful result from the other.
            try {
                when {
                    !primaryJob.isCompleted -> primaryJob.await()
                    !fallbackJob.isCompleted -> fallbackJob.await()
                    else -> {
                        // Both completed - try primary first (preferred), then fallback
                        try {
                            primaryJob.await()
                        } catch (_: Throwable) {
                            fallbackJob.await()
                        }
                    }
                }
            } catch (_: Throwable) {
                // Both failed - throw the first error
                throw firstError
            }
        }
    }
}

private suspend fun enhanceWithAI(
    lang: String,
    word: String,
    logger: Logger
): LanguageCardResponse {
    val geminiProvider = GeminiProvider()
    val openAIProvider = OpenAIProvider()

    val content = GitHubClient.loadDbExtractContent(lang, "$word.json")
    val extractedData = ServerJson.lenient.decodeFromString(ExtractedWordData.serializer(), content)
    val request = DbExtractEnhancerUtils.createLanguageCardRequest(extractedData)
        ?: throw IllegalArgumentException("No entries found for word '$word' in language '$lang'")

    val enhancer = LanguageCardEnhancer()

    return raceWithFallback(
        primaryAvailable = geminiProvider.isAvailable(),
        fallbackAvailable = openAIProvider.isAvailable(),
        primary = {
            enhancer.enhance(
                request = request,
                provider = geminiProvider,
                model = GEMINI_3_1_FLASH_LITE,
                reasoningBudget = 1
            )
        },
        fallback = {
            enhancer.enhance(
                request = request,
                provider = openAIProvider,
                model = ChatModel.GPT_5_4.asString(),
                reasoningBudget = 900
            )
        },
        onPrimaryError = { e ->
            logger.error("Gemini failed for $lang/$word: ${e.message}", e)
        },
        onPrimaryTimeout = {
            logger.warn("Gemini timed out for $lang/$word after ${AI_FALLBACK_TIMEOUT_MS}ms, starting OpenAI fallback")
        }
    )
}

private data class WordProcessResult(
    val response: LanguageCardResponse,
    val wasProcessed: Boolean
)

private data class TranslationResult(
    val response: LanguageCardResponse,
    val updated: Boolean
)

/**
 * [response] with [mergedLangCodes] merged into it. A requested language is absent from
 * [mergedLangCodes] when every provider failed to translate it, which leaves [response] carrying
 * whatever the other languages produced.
 */
private data class EnhancedTranslations(
    val response: LanguageCardResponse,
    val mergedLangCodes: List<String>
)

private suspend fun loadBaseWordData(lang: String, word: String, logger: Logger): WordProcessResult {
    var wasProcessed = false
    val response = try {
        // Try push branch first (contains latest updates)
        val (content, _) = GitHubClient.loadWordsContentFromPushBranch(lang, word)
        ServerJson.lenient.decodeFromString(LanguageCardResponse.serializer(), content)
    } catch (_: GHFileNotFoundException) {
        // Fall back to main branch
        try {
            val content = GitHubClient.loadWordsContent(lang, word)
            ServerJson.lenient.decodeFromString(LanguageCardResponse.serializer(), content)
        } catch (_: GHFileNotFoundException) {
            wasProcessed = true
            enhanceWithAI(lang, word, logger = logger)
        }
    }
    return WordProcessResult(response = response, wasProcessed = wasProcessed)
}

private fun getExistingTranslationLanguages(response: LanguageCardResponse): Set<String> {
    return response.entries
        .flatMap { it.senses }
        .flatMap { sense -> sense.translations.keys + sense.targetLangDefinitions.keys }
        .toSet()
}

private fun filterTranslations(
    response: LanguageCardResponse,
    requestedLangCodes: List<String>
): LanguageCardResponse {
    return response.copy(
        entries = response.entries.map { entry ->
            entry.copy(
                senses = entry.senses.map { sense ->
                    sense.copy(
                        translations = sense.translations.filterKeys { it in requestedLangCodes },
                        targetLangDefinitions = sense.targetLangDefinitions.filterKeys { it in requestedLangCodes }
                    )
                }
            )
        }
    )
}

private suspend fun addMissingTranslations(
    response: LanguageCardResponse,
    lang: String,
    word: String,
    requestedLangCodes: List<String>,
    logger: Logger
): TranslationResult {
    if (requestedLangCodes.isEmpty()) return TranslationResult(response, updated = false)

    // Skip self-translation requests (e.g. en -> en).
    val targetLangCodes = requestedLangCodes.filterNot { it.equals(lang, ignoreCase = true) }
    if (targetLangCodes.isEmpty()) return TranslationResult(response, updated = false)

    // ensure all languages are valid - throws in case of invalid language code
    targetLangCodes.forEach { targetLanguageName(it) }

    val existingLanguages = getExistingTranslationLanguages(response)
    val missingLangCodes = targetLangCodes.filter { it !in existingLanguages }
    if (missingLangCodes.isEmpty()) return TranslationResult(response, updated = false)

    // The two bail-outs below look exactly like a successful "nothing to translate" from the
    // client's side - base chunk, no translated chunk - so each has to say why it gave up.
    val geminiProvider = GeminiProvider()
    val openAIProvider = OpenAIProvider()
    if (!geminiProvider.isAvailable() && !openAIProvider.isAvailable()) {
        logger.error("No AI provider configured; cannot translate $lang/$word into $missingLangCodes")
        return TranslationResult(response, updated = false)
    }

    val extractedData = try {
        val content = GitHubClient.loadDbExtractContent(lang, "$word.json")
        ServerJson.lenient.decodeFromString(ExtractedWordData.serializer(), content)
    } catch (_: GHFileNotFoundException) {
        logger.warn("No db-extract for $lang/$word; cannot translate it into $missingLangCodes")
        return TranslationResult(response, updated = false)
    }

    val enhanced = enhanceWithTranslations(
        response = response,
        extractedData = extractedData,
        word = word,
        lang = lang,
        targetLangCodes = missingLangCodes,
        geminiProvider = geminiProvider,
        openAIProvider = openAIProvider,
        logger = logger
    )
    // A language that produced nothing must not be reported as an update: that would stream a
    // translated chunk identical to the base one and queue a repo update with no new content.
    return TranslationResult(enhanced.response, updated = enhanced.mergedLangCodes.isNotEmpty())
}

private suspend fun enhanceWithTranslations(
    response: LanguageCardResponse,
    extractedData: ExtractedWordData,
    word: String,
    lang: String,
    targetLangCodes: List<String>,
    geminiProvider: GeminiProvider,
    openAIProvider: OpenAIProvider,
    logger: Logger
): EnhancedTranslations {
    val translationEnhancer = TranslationEnhancer()
    var updatedResponse = response

    // supervisorScope, and a catch per language, so one target language that both providers fail
    // to translate is logged and dropped instead of cancelling its siblings.
    val translationResults: List<Pair<String, TranslationResponse>> = supervisorScope {
        targetLangCodes.map { targetLangCode ->
            async {
                try {
                    translateInto(
                        targetLangCode = targetLangCode,
                        extractedData = extractedData,
                        card = updatedResponse,
                        word = word,
                        lang = lang,
                        translationEnhancer = translationEnhancer,
                        geminiProvider = geminiProvider,
                        openAIProvider = openAIProvider,
                        logger = logger
                    ).let { targetLangCode to it }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    logger.error("Translation of $lang/$word -> $targetLangCode failed: ${e.message}", e)
                    null
                }
            }
        }.awaitAll().filterNotNull()
    }

    val mergedLangCodes = mutableListOf<String>()
    for ((targetLangCode, translationResponse) in translationResults) {
        updatedResponse = translationEnhancer.mergeTranslationData(
            originalCard = updatedResponse,
            translationResponse = translationResponse,
            targetLangCode = targetLangCode
        )
        mergedLangCodes += targetLangCode
    }

    return EnhancedTranslations(response = updatedResponse, mergedLangCodes = mergedLangCodes)
}

private suspend fun translateInto(
    targetLangCode: String,
    extractedData: ExtractedWordData,
    card: LanguageCardResponse,
    word: String,
    lang: String,
    translationEnhancer: TranslationEnhancer,
    geminiProvider: GeminiProvider,
    openAIProvider: OpenAIProvider,
    logger: Logger
): TranslationResponse {
    val targetTranslations = extractedData.sourceFileToEntries.values
        .flatten()
        .flatMap { it.translations }
        .filter { it.targetLangCode == targetLangCode }

    val translationRequest = TranslationRequest(
        word = word,
        langCode = lang,
        targetLangCode = targetLangCode,
        languageCardData = card,
        translations = targetTranslations
    )

    val targetLangName = targetLanguageName(targetLangCode)
    val targetLangNotes = DbExtractEnhancerUtils.targetLanguageNotes(targetLangCode)

    return raceWithFallback(
        primaryAvailable = geminiProvider.isAvailable(),
        fallbackAvailable = openAIProvider.isAvailable(),
        primary = {
            translationEnhancer.enhanceWithTranslations(
                request = translationRequest,
                provider = geminiProvider,
                targetLanguageName = targetLangName,
                targetLanguageNotes = targetLangNotes,
                model = GEMINI_3_1_FLASH_LITE,
                reasoningBudget = 1
            )
        },
        fallback = {
            translationEnhancer.enhanceWithTranslations(
                request = translationRequest,
                provider = openAIProvider,
                targetLanguageName = targetLangName,
                targetLanguageNotes = targetLangNotes,
                model = ChatModel.GPT_5_4.asString(),
                reasoningBudget = 900
            )
        },
        onPrimaryError = { e ->
            logger.error("Gemini translation failed for $lang/$word -> $targetLangCode: ${e.message}", e)
        },
        onPrimaryTimeout = {
            logger.warn("Gemini translation timed out for $lang/$word -> $targetLangCode after ${AI_FALLBACK_TIMEOUT_MS}ms")
        }
    )
}
