package com.slovy.slovymovyapp

import com.slovy.slovymovyapp.builder.ServerDbManager
import com.slovy.slovymovyapp.data.settings.Setting
import com.slovy.slovymovyapp.data.settings.SettingsRepository
import com.slovy.slovymovyapp.db.AppDatabase
import com.slovy.slovymovyapp.ingestion.ExtractedWordData
import com.slovy.slovymovyapp.server.ai.GEMINI_3_0_FLASH_PREVIEW
import com.slovy.slovymovyapp.server.ai.GeminiProvider
import com.slovy.slovymovyapp.server.ai.enhancer.*
import com.slovy.slovymovyapp.server.cloudrun.CloudTasksAuthVerifier
import com.slovy.slovymovyapp.server.github.GitHubClient
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import org.kohsuke.github.GHFileNotFoundException
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
                // Step 1: Get enhanced word (pre-processed or AI-enhanced)
                var response = try {
                    val content = GitHubClient.loadWordsContent(lang, word)
                    json.decodeFromString(LanguageCardResponse.serializer(), content)
                } catch (_: GHFileNotFoundException) {
                    enhanceWithAI(lang, word, json)
                }

                // Step 2: Add missing translations if requested
                if (!translationsParam.isNullOrBlank()) {
                    response = addMissingTranslations(response, lang, word, translationsParam, json)
                }

                val responseJson = json.encodeToString(LanguageCardResponse.serializer(), response)

                // Step 3: Queue Cloud Tasks update if requested
                if (!push.isNullOrBlank()) {
                    RepoUpdateTaskClient.queueRepoUpdate(lang, word, responseJson)
                }

                call.respondText(responseJson, ContentType.Application.Json)
            } catch (_: GHFileNotFoundException) {
                call.respond(HttpStatusCode.NotFound, "Word '$word' not found for language '$lang'")
            } catch (e: Exception) {
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

            try {
                val responseJson = call.receiveText()
                call.application.environment.log.info("Received response $responseJson")
                // TODO: Update GitHub repository with processed data
                // GitHubClient.updateWordsContent(lang, word, responseJson)

                call.respond(HttpStatusCode.OK, "Updated $lang/$word")
            } catch (e: Exception) {
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
): LanguageCardResponse {
    val requestedLangCodes = translationsParam.split(",").map { it.trim() }.filter { it.isNotBlank() }
    if (requestedLangCodes.isEmpty()) return response

    val existingLanguages = getExistingTranslationLanguages(response)
    val missingLangCodes = requestedLangCodes.filter { it !in existingLanguages }
    if (missingLangCodes.isEmpty()) return response

    val geminiProvider = GeminiProvider()
    if (!geminiProvider.isAvailable()) return response

    val extractedData = try {
        val content = GitHubClient.loadDbExtractContent(lang, "$word.json")
        json.decodeFromString(ExtractedWordData.serializer(), content)
    } catch (_: GHFileNotFoundException) {
        return response
    }

    return enhanceWithTranslations(response, extractedData, word, lang, missingLangCodes, geminiProvider)
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
