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
            } catch (_: org.kohsuke.github.GHFileNotFoundException) {
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

            val geminiProvider = GeminiProvider()
            if (!geminiProvider.isAvailable()) {
                call.respond(HttpStatusCode.ServiceUnavailable, "Gemini API key not configured")
                return@get
            }

            try {
                val json = Json { ignoreUnknownKeys = true }
                val content = GitHubClient.loadDbExtractContent(lang, "$word.json")
                val extractedData = json.decodeFromString(ExtractedWordData.serializer(), content)
                val request = DbExtractEnhancerUtils.createLanguageCardRequest(extractedData)

                if (request == null) {
                    call.respond(HttpStatusCode.NotFound, "No entries found for word '$word' in language '$lang'")
                    return@get
                }

                val enhancer = LanguageCardEnhancer()
                var response = enhancer.enhance(
                    request = request,
                    provider = geminiProvider,
                    model = GEMINI_3_0_FLASH_PREVIEW,
                    reasoningBudget = 100 // LOW thinking level
                )

                // Process translations if requested
                if (!translationsParam.isNullOrBlank()) {
                    val targetLangCodes = translationsParam.split(",").map { it.trim() }.filter { it.isNotBlank() }

                    if (targetLangCodes.isNotEmpty()) {
                        val translationEnhancer = TranslationEnhancer()

                        // Run translations in parallel
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
                                        languageCardData = response,
                                        translations = targetTranslations
                                    )

                                    val targetLanguageName = DbExtractEnhancerUtils.targetLanguageName(targetLangCode)
                                    val translationResponse = translationEnhancer.enhanceWithTranslations(
                                        request = translationRequest,
                                        provider = geminiProvider,
                                        targetLanguageName = targetLanguageName,
                                        model = GEMINI_3_0_FLASH_PREVIEW,
                                        reasoningBudget = 100 // LOW thinking level
                                    )

                                    targetLangCode to translationResponse
                                }
                            }.awaitAll()
                        }

                        // Merge all translation results into the response
                        for ((targetLangCode, translationResponse) in translationResults) {
                            response = translationEnhancer.mergeTranslationData(
                                originalCard = response,
                                translationResponse = translationResponse,
                                targetLangCode = targetLangCode
                            )
                        }
                    }
                }

                call.respondText(
                    json.encodeToString(LanguageCardResponse.serializer(), response),
                    ContentType.Application.Json
                )
            } catch (_: org.kohsuke.github.GHFileNotFoundException) {
                call.respond(HttpStatusCode.NotFound, "Word '$word' not found for language '$lang'")
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, "Failed to process word: ${e.message}")
            }
        }

    }
}