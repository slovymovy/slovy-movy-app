package com.slovy.slovymovyapp

import com.slovy.slovymovyapp.builder.ServerDbManager
import com.slovy.slovymovyapp.data.settings.Setting
import com.slovy.slovymovyapp.data.settings.SettingsRepository
import com.slovy.slovymovyapp.db.AppDatabase
import com.slovy.slovymovyapp.ingestion.ExtractedWordData
import com.slovy.slovymovyapp.server.ai.GEMINI_3_0_FLASH_PREVIEW
import com.slovy.slovymovyapp.server.ai.GeminiProvider
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import org.kohsuke.github.GHFileNotFoundException
import org.kohsuke.github.HttpException
import org.slf4j.event.Level
import java.nio.file.Files

const val updateRepoPath = "/internal/update-repo/"

fun main() {
    embeddedServer(Netty, port = SERVER_PORT, host = "0.0.0.0", module = Application::module)
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
                val baseResult = loadBaseWordData(lang, word, json)

                // Step 2: Stream results as NDJSON (base, then translated if available)
                call.respondTextWriter(contentType = ContentType.parse("application/x-ndjson")) {
                    val baseChunk = WordStreamChunk(WordStreamStage.base, baseResult.response)
                    write(json.encodeToString(WordStreamChunk.serializer(), baseChunk))
                    flush()

                    var finalResponse = baseResult.response
                    var wasProcessed = baseResult.wasProcessed

                    if (!translationsParam.isNullOrBlank()) {
                        val translationResult = addMissingTranslations(
                            response = finalResponse,
                            lang = lang,
                            word = word,
                            translationsParam = translationsParam,
                            json = json
                        )

                        if (translationResult.updated) {
                            finalResponse = translationResult.response
                            wasProcessed = true
                            write("\n")
                            val translatedChunk = WordStreamChunk(WordStreamStage.translated, finalResponse)
                            write(json.encodeToString(WordStreamChunk.serializer(), translatedChunk))
                            flush()
                        }
                    }

                    // Step 3: Queue Cloud Tasks update if requested (only if something was processed)
                    if (!push.isNullOrBlank() && wasProcessed) {
                        val responseJson = json.encodeToString(LanguageCardResponse.serializer(), finalResponse)
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
                val incoming = jsonDecoder.decodeFromString(LanguageCardResponse.serializer(), responseJson)
                call.application.environment.log.info("Received update for $lang/$word")

                if (!GitHubClient.pushBranchExists())
                    GitHubClient.ensurePushBranch()

                // Check if file exists and handle accordingly
                try {
                    val (existingContent, sha) = GitHubClient.loadWordsContentFromPushBranch(lang, word)
                    val existing = jsonDecoder.decodeFromString(LanguageCardResponse.serializer(), existingContent)
                    val merged = WordDataMerger.merge(existing, incoming)
                    val mergedJson = jsonEncoder.encodeToString(LanguageCardResponse.serializer(), merged)

                    // Skip commit if content is identical
                    val existingPretty = jsonEncoder.encodeToString(LanguageCardResponse.serializer(), existing)
                    if (mergedJson == existingPretty) {
                        call.application.environment.log.info("No changes for $lang/$word, skipping commit")
                    } else {
                        GitHubClient.updateWordsContent(lang, word, mergedJson, sha, "Update $word ($lang)")
                        call.application.environment.log.info("Merged and updated $lang/$word")
                    }
                } catch (_: GHFileNotFoundException) {
                    // File doesn't exist - create it
                    val prettyJson = jsonEncoder.encodeToString(LanguageCardResponse.serializer(), incoming)
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

private fun enhanceWithAI(lang: String, word: String, json: Json): LanguageCardResponse {
    val geminiProvider = GeminiProvider()
    if (!geminiProvider.isAvailable()) {
        throw IllegalStateException("Gemini API key not configured")
    }

    val content = GitHubClient.loadDbExtractContent(lang, "$word.json")
    val extractedData = json.decodeFromString(ExtractedWordData.serializer(), content)
    val request = DbExtractEnhancerUtils.createLanguageCardRequest(extractedData)
        ?: throw IllegalArgumentException("No entries found for word '$word' in language '$lang'")

    return LanguageCardEnhancer().enhance(
        request = request,
        provider = geminiProvider,
        model = GEMINI_3_0_FLASH_PREVIEW,
        reasoningBudget = 1
    )
}

@Serializable
private enum class WordStreamStage { base, translated }

@Serializable
private data class WordStreamChunk(
    val stage: WordStreamStage,
    val payload: LanguageCardResponse
)

private data class WordProcessResult(
    val response: LanguageCardResponse,
    val wasProcessed: Boolean
)

private data class TranslationResult(
    val response: LanguageCardResponse,
    val updated: Boolean
)

private fun loadBaseWordData(lang: String, word: String, json: Json): WordProcessResult {
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
            enhanceWithAI(lang, word, json)
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

private suspend fun addMissingTranslations(
    response: LanguageCardResponse,
    lang: String,
    word: String,
    translationsParam: String,
    json: Json
): TranslationResult {
    val requestedLangCodes = translationsParam.split(",").map { it.trim() }.filter { it.isNotBlank() }
    if (requestedLangCodes.isEmpty()) return TranslationResult(response, updated = false)
    // ensure all languages are valid - throws in case of invalid language code
    requestedLangCodes.forEach { targetLanguageName(it) }

    val existingLanguages = getExistingTranslationLanguages(response)
    val missingLangCodes = requestedLangCodes.filter { it !in existingLanguages }
    if (missingLangCodes.isEmpty()) return TranslationResult(response, updated = false)

    val geminiProvider = GeminiProvider()
    if (!geminiProvider.isAvailable()) return TranslationResult(response, updated = false)

    val extractedData = try {
        val content = GitHubClient.loadDbExtractContent(lang, "$word.json")
        json.decodeFromString(ExtractedWordData.serializer(), content)
    } catch (_: GHFileNotFoundException) {
        return TranslationResult(response, updated = false)
    }

    val updatedResponse = enhanceWithTranslations(
        response,
        extractedData,
        word,
        lang,
        missingLangCodes,
        geminiProvider
    )
    return TranslationResult(updatedResponse, updated = true)
}

private suspend fun enhanceWithTranslations(
    response: LanguageCardResponse,
    extractedData: ExtractedWordData,
    word: String,
    lang: String,
    targetLangCodes: List<String>,
    geminiProvider: GeminiProvider
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

                val targetLanguageName = DbExtractEnhancerUtils.targetLanguageName(targetLangCode)
                val translationResponse = translationEnhancer.enhanceWithTranslations(
                    request = translationRequest,
                    provider = geminiProvider,
                    targetLanguageName = targetLanguageName,
                    model = GEMINI_3_0_FLASH_PREVIEW,
                    reasoningBudget = 1
                )

                targetLangCode to translationResponse
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
