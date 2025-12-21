package com.slovy.slovymovyapp

import com.slovy.slovymovyapp.builder.ServerDbManager
import com.slovy.slovymovyapp.data.settings.Setting
import com.slovy.slovymovyapp.data.settings.SettingsRepository
import com.slovy.slovymovyapp.db.AppDatabase
import com.slovy.slovymovyapp.ingestion.ExtractedWordData
import com.slovy.slovymovyapp.server.ai.GEMINI_3_0_FLASH_PREVIEW
import com.slovy.slovymovyapp.server.ai.GeminiProvider
import com.slovy.slovymovyapp.server.ai.enhancer.DbExtractEnhancerUtils
import com.slovy.slovymovyapp.server.ai.enhancer.LanguageCardEnhancer
import com.slovy.slovymovyapp.server.ai.enhancer.LanguageCardResponse
import com.slovy.slovymovyapp.server.ai.enhancer.TranslationEnhancer
import com.slovy.slovymovyapp.server.ai.enhancer.TranslationRequest
import com.slovy.slovymovyapp.server.ai.enhancer.TranslationResponse
import com.slovy.slovymovyapp.server.github.GitHubClient
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import org.kohsuke.github.GHFileNotFoundException
import java.nio.file.Files

fun main() {
    embeddedServer(Netty, port = SERVER_PORT, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {

    val db: AppDatabase = ServerDbManager(Files.createTempDirectory("openwords").toFile()).openApp()
    val repo = SettingsRepository(db)

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

                call.respondText(
                    json.encodeToString(LanguageCardResponse.serializer(), response),
                    ContentType.Application.Json
                )
            } catch (_: GHFileNotFoundException) {
                call.respond(HttpStatusCode.NotFound, "Word '$word' not found for language '$lang'")
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, "Failed to process word: ${e.message}")
            }
        }

    }
}

private suspend fun enhanceWithAI(lang: String, word: String, json: Json): LanguageCardResponse {
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
        reasoningBudget = 100
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
                    reasoningBudget = 100
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