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
import com.slovy.slovymovyapp.server.ai.GEMINI_3_0_FLASH_PREVIEW
import com.slovy.slovymovyapp.server.ai.GeminiProvider
import com.slovy.slovymovyapp.server.ai.OpenAIProvider
import com.slovy.slovymovyapp.server.ai.enhancer.*
import com.slovy.slovymovyapp.server.ai.enhancer.DbExtractEnhancerUtils.targetLanguageName
import com.slovy.slovymovyapp.server.cloudrun.CloudTasksAuthVerifier
import com.slovy.slovymovyapp.server.github.GitHubClient
import com.slovy.slovymovyapp.server.github.WordDataMerger
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import org.kohsuke.github.GHFileNotFoundException
import org.kohsuke.github.HttpException
import org.slf4j.event.Level
import java.nio.file.Files

const val updateRepoPath = "/internal/update-repo/"
const val SERVER_PORT_ENV = "SERVER_PORT"
const val SERVER_PORT = 8080

@Serializable
private data class FeedbackIssueRequest(
    val comment: String
)

@Serializable
private data class FeedbackIssueResponse(
    val issueNumber: Int,
    val issueTitle: String,
    val issueUrl: String
)

fun main() {
    val port = System.getenv(SERVER_PORT_ENV)?.toIntOrNull() ?: SERVER_PORT
    embeddedServer(Netty, port = port, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {

    val db: AppDatabase = ServerDbManager(Files.createTempDirectory("openwords").toFile()).openApp()
    val repo = SettingsRepository(db)

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

            if (!GitHubClient.isAvailable()) {
                call.respond(HttpStatusCode.ServiceUnavailable, "GitHub token not configured")
                return@get
            }

            val json = Json { ignoreUnknownKeys = true }

            try {
                // Step 1: Prepare base data before starting the stream so we can still return errors
                val baseResult = loadBaseWordData(lang, word, json, logger = call.application.environment.log)

                // Step 2: Stream results as NDJSON (base, then translated if available)
                call.respondTextWriter(contentType = ContentType.parse("application/x-ndjson")) {
                    // Parse requested language codes for filtering.
                    // If no translations parameter provided, return empty list (no translations in response).
                    val requestedLangCodes = parseTranslationCodes(translationsParam)

                    // Send filtered base response to client (always filter based on requested codes)
                    val baseResponseToClient = filterTranslations(baseResult.response, requestedLangCodes)
                    val baseChunk = WordStreamChunk(WordStreamStage.BASE, baseResponseToClient)
                    write(json.encodeToString(WordStreamChunk.serializer(), baseChunk))
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
                            json = json,
                            logger = call.application.environment.log
                        )

                        if (translationResult.updated) {
                            fullResponse = translationResult.response
                            wasProcessed = true
                            // Send filtered translated response to client
                            val translatedResponseToClient = filterTranslations(fullResponse, requestedLangCodes)
                            val translatedChunk =
                                WordStreamChunk(WordStreamStage.TRANSLATED, translatedResponseToClient)
                            write(json.encodeToString(WordStreamChunk.serializer(), translatedChunk))
                            write("\n")
                            flush()
                        }
                    }

                    // Step 3: Queue Cloud Tasks update if requested (only if something was processed)
                    // IMPORTANT: Use fullResponse (unfiltered) to ensure nothing is lost in repo
                    if (!push.isNullOrBlank() && wasProcessed) {
                        val responseJson = json.encodeToString(
                            LanguageCardResponse.serializer(),
                            fullResponse
                        )
                        RepoUpdateTaskClient.queueRepoUpdate(lang, word, responseJson)
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

        post("/feedback/{lang}/{word}") {
            val lang = call.parameters["lang"]?.trim()
            val word = call.parameters["word"]?.trim()

            if (lang.isNullOrBlank() || word.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, "Missing lang or word parameter")
                return@post
            }

            if (!GitHubClient.isAvailable()) {
                call.respond(HttpStatusCode.ServiceUnavailable, "GitHub token not configured")
                return@post
            }

            val json = Json { ignoreUnknownKeys = true }
            val feedbackRequest = try {
                json.decodeFromString(
                    FeedbackIssueRequest.serializer(),
                    call.receiveText()
                )
            } catch (_: Exception) {
                call.respond(HttpStatusCode.BadRequest, "Invalid request body")
                return@post
            }

            val comment = feedbackRequest.comment.trim()
            if (comment.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, "Missing comment")
                return@post
            }

            val translationCodes = parseTranslationCodes(call.request.queryParameters["translations"])

            try {
                val createdIssue = GitHubClient.createFeedbackIssue(
                    lang = lang,
                    word = word,
                    translationCodes = translationCodes,
                    comment = comment
                )
                val response = FeedbackIssueResponse(
                    issueNumber = createdIssue.number,
                    issueTitle = createdIssue.title,
                    issueUrl = createdIssue.htmlUrl
                )
                call.respondText(
                    text = json.encodeToString(FeedbackIssueResponse.serializer(), response),
                    contentType = ContentType.Application.Json,
                    status = HttpStatusCode.Created
                )
            } catch (e: Exception) {
                call.application.environment.log.error(
                    "Failed to create feedback issue for $lang/$word: ${e.message}",
                    e
                )
                call.respond(HttpStatusCode.InternalServerError, "Failed to create feedback issue: ${e.message}")
            }
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

            val jsonDecoder = Json { ignoreUnknownKeys = false }
            val jsonEncoder = Json { prettyPrint = true }

            try {
                val responseJson = call.receiveText()
                val incoming = jsonDecoder.decodeFromString(
                    LanguageCardResponse.serializer(),
                    responseJson
                )
                call.application.environment.log.info("Received update for $lang/$word")

                if (!GitHubClient.pushBranchExists())
                    GitHubClient.ensurePushBranch()

                // Check if file exists and handle accordingly
                try {
                    val (existingContent, sha) = GitHubClient.loadWordsContentFromPushBranch(lang, word)
                    val existing = jsonDecoder.decodeFromString(
                        LanguageCardResponse.serializer(),
                        existingContent
                    )
                    val merged = WordDataMerger.merge(existing, incoming)
                    val mergedJson = jsonEncoder.encodeToString(
                        LanguageCardResponse.serializer(),
                        merged
                    )

                    // Skip commit if content is identical
                    val existingPretty = jsonEncoder.encodeToString(
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
                    val prettyJson = jsonEncoder.encodeToString(
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
                    call.application.environment.log.error("GitHub API error for $lang/$word: ${e.message}")
                    call.respond(HttpStatusCode.InternalServerError, "GitHub API error: ${e.message}")
                }
            } catch (e: Exception) {
                call.application.environment.log.error("Failed to update $lang/$word: ${e.message}")
                call.respond(HttpStatusCode.InternalServerError, "Failed to update: ${e.message}")
            }
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

internal const val AI_FALLBACK_TIMEOUT_MS = 20_000L

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
    json: Json,
    logger: Logger
): LanguageCardResponse {
    val geminiProvider = GeminiProvider()
    val openAIProvider = OpenAIProvider()

    val content = GitHubClient.loadDbExtractContent(lang, "$word.json")
    val extractedData = json.decodeFromString(ExtractedWordData.serializer(), content)
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
                model = GEMINI_3_0_FLASH_PREVIEW,
                reasoningBudget = 1
            )
        },
        fallback = {
            enhancer.enhance(
                request = request,
                provider = openAIProvider,
                model = ChatModel.GPT_5_2.asString(),
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

private suspend fun loadBaseWordData(lang: String, word: String, json: Json, logger: Logger): WordProcessResult {
    var wasProcessed = false
    val response = try {
        // Try push branch first (contains latest updates)
        val (content, _) = GitHubClient.loadWordsContentFromPushBranch(lang, word)
        json.decodeFromString(LanguageCardResponse.serializer(), content)
    } catch (_: GHFileNotFoundException) {
        // Fall back to main branch
        try {
            val content = GitHubClient.loadWordsContent(lang, word)
            json.decodeFromString(LanguageCardResponse.serializer(), content)
        } catch (_: GHFileNotFoundException) {
            wasProcessed = true
            enhanceWithAI(lang, word, json, logger = logger)
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
    json: Json,
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

    val geminiProvider = GeminiProvider()
    val openAIProvider = OpenAIProvider()
    if (!geminiProvider.isAvailable() && !openAIProvider.isAvailable()) {
        return TranslationResult(response, updated = false)
    }

    val extractedData = try {
        val content = GitHubClient.loadDbExtractContent(lang, "$word.json")
        json.decodeFromString(ExtractedWordData.serializer(), content)
    } catch (_: GHFileNotFoundException) {
        return TranslationResult(response, updated = false)
    }

    val updatedResponse = enhanceWithTranslations(
        response = response,
        extractedData = extractedData,
        word = word,
        lang = lang,
        targetLangCodes = missingLangCodes,
        geminiProvider = geminiProvider,
        openAIProvider = openAIProvider,
        logger = logger
    )
    return TranslationResult(updatedResponse, updated = true)
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
): LanguageCardResponse {
    val translationEnhancer = TranslationEnhancer()
    var updatedResponse = response

    val translationResults: List<Pair<String, TranslationResponse>> = coroutineScope {
        targetLangCodes.map { targetLangCode ->
            async {
                val targetTranslations = extractedData.sourceFileToEntries.values
                    .flatten()
                    .flatMap { it.translations }
                    .filter { it.targetLangCode == targetLangCode }

                val translationRequest = TranslationRequest(
                    word = word,
                    langCode = lang,
                    targetLangCode = targetLangCode,
                    languageCardData = updatedResponse,
                    translations = targetTranslations
                )

                val targetLangName = targetLanguageName(targetLangCode)

                raceWithFallback(
                    primaryAvailable = geminiProvider.isAvailable(),
                    fallbackAvailable = openAIProvider.isAvailable(),
                    primary = {
                        translationEnhancer.enhanceWithTranslations(
                            request = translationRequest,
                            provider = geminiProvider,
                            targetLanguageName = targetLangName,
                            model = GEMINI_3_0_FLASH_PREVIEW,
                            reasoningBudget = 1
                        )
                    },
                    fallback = {
                        translationEnhancer.enhanceWithTranslations(
                            request = translationRequest,
                            provider = openAIProvider,
                            targetLanguageName = targetLangName,
                            model = ChatModel.GPT_5_2.asString(),
                            reasoningBudget = 900
                        )
                    },
                    onPrimaryError = { e ->
                        logger.error("Gemini translation failed for $lang/$word -> $targetLangCode: ${e.message}", e)
                    },
                    onPrimaryTimeout = {
                        logger.warn("Gemini translation timed out for $lang/$word -> $targetLangCode after ${AI_FALLBACK_TIMEOUT_MS}ms")
                    }
                ).let { targetLangCode to it }
            }
        }.awaitAll()
    }

    for ((targetLangCode, translationResponse) in translationResults) {
        updatedResponse = translationEnhancer.mergeTranslationData(
            originalCard = updatedResponse,
            translationResponse = translationResponse,
            targetLangCode = targetLangCode
        )
    }

    return updatedResponse
}
