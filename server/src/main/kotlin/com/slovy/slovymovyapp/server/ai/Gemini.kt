package com.slovy.slovymovyapp.server.ai

import com.google.genai.Client
import com.google.genai.types.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class GeminiParameters(
    val parts: List<String>,
    val temperature: Float,
    val seed: Int,
    val thinkingBudget: Int?,
    val thinkingLevel: String?,
    val model: String,
    val schema: String,
    val systemPrompt: String,
    val maxOutputTokens: Int,
    val topP: Float
)

object Gemini {

    const val DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com"

    fun clientProvider(baseUrl: String = DEFAULT_BASE_URL): () -> Client = {
        val apiKey = System.getenv("AISTUDIO_KEY")?.takeIf { it.isNotBlank() } ?: run {
            val apiKeyFile = File(".aistudio_key")
            require(apiKeyFile.exists()) { "Missing .aistudio_key file and AISTUDIO_KEY environment variable" }
            apiKeyFile.readText().trim()
        }
        val httpOptions = HttpOptions.builder()
            .baseUrl(baseUrl)
            .apiVersion("v1beta")
            .build()
        Client.builder().apiKey(apiKey).httpOptions(httpOptions).build()
    }


    fun geminiCompletion(
        client: Client,
        parameters: GeminiParameters
    ): String {
        // Build the API call
        val contents = listOf(
            Content.builder()
                .role("user")
                .parts(
                    parameters.parts.map { Part.fromText(it) }
                )
                .build()
        )

        var thinking = ThinkingConfig.builder()
        if (parameters.thinkingBudget != null) {
            thinking = thinking.thinkingBudget(parameters.thinkingBudget)
        } else if (parameters.thinkingLevel != null) {
            thinking = thinking.thinkingLevel(parameters.thinkingLevel)
        } else {
            throw IllegalArgumentException("Either thinkingBudget or thinkingLevel must be provided")
        }
        val config = GenerateContentConfig.builder()
            .temperature(parameters.temperature)
            .seed(parameters.seed)
            .maxOutputTokens(parameters.maxOutputTokens)
            .topP(parameters.topP)
            .thinkingConfig(thinking.build())
            .responseMimeType("application/json")
            .responseSchema(
                Schema.fromJson(
                    parameters.schema
                )
            )
            .systemInstruction(
                Content.fromParts(
                    Part.fromText(
                        parameters.systemPrompt
                    )
                )
            )
            .build()

        val response = client.models.generateContent(parameters.model, contents, config)
        if (response.text() == null) {
            println(response)
        }
        return response.text()!!
    }
}

/**
 * Gemini AI provider implementation.
 */
class GeminiProvider : AIProvider {
    private val cacheJson = Json { prettyPrint = true }

    override fun complete(parameters: AIParameters, cache: AICache, retryStrategy: RetryStrategy): String {
        val clientProvider = Gemini.clientProvider()
        val client = clientProvider()

        var reasoningBudget: Int? = null
        var thinkingLevel: String? = null

        val model = getAvailableModels().first { it.value == parameters.model }

        if (model.reasoningType == ReasoningType.TOKENS) {
            reasoningBudget = parameters.reasoningBudget
        } else if (model.reasoningType == ReasoningType.HIGH_LOW) {
            thinkingLevel = if (parameters.reasoningBudget <= 100) "LOW" else "HIGH"
        }

        val geminiParams = GeminiParameters(
            parts = parameters.parts,
            temperature = parameters.temperature,
            seed = parameters.seed,
            thinkingBudget = reasoningBudget,
            thinkingLevel = thinkingLevel,
            model = parameters.model,
            schema = parameters.schema,
            systemPrompt = parameters.systemPrompt,
            maxOutputTokens = parameters.maxOutputTokens,
            topP = parameters.topP
        )

        // Check cache
        val parametersJson = cacheJson.encodeToString(geminiParams)
        val cachedResponse = cache.get(parametersJson)
        if (cachedResponse != null) {
            return cachedResponse
        }

        val response = client.use { cl ->
            retryStrategy.execute {
                Gemini.geminiCompletion(cl, geminiParams)
            }
        }

        // Cache the response
        cache.put(parametersJson, response)

        return response
    }

    override fun getAvailableModels(): List<ModelInfo> {
        return listOf(

            ModelInfo(
                GEMINI_FLASH_LATEST, "Gemini 3.0 Flash Latest", AIProviderType.GEMINI,
                supportsTemperature = true,
                supportsVerbosity = false,
                reasoningType = ReasoningType.HIGH_LOW,
            ),
            ModelInfo(
                GEMINI_3_1_PRO_PREVIEW, "Gemini 3.1 Pro Preview", AIProviderType.GEMINI,
                supportsTemperature = true,
                supportsVerbosity = false,
                reasoningType = ReasoningType.TOKENS
            ),
            ModelInfo(
                GEMINI_3_1_FLASH_LITE, "Gemini 3.1 Flash Light", AIProviderType.GEMINI,
                supportsTemperature = true,
                supportsVerbosity = false,
                reasoningType = ReasoningType.TOKENS
            ),
        )
    }

    override fun isAvailable(): Boolean {
        return try {
            System.getenv("AISTUDIO_KEY")?.takeIf { it.isNotBlank() } != null ||
                    File(".aistudio_key").exists()
        } catch (_: Exception) {
            false
        }
    }

    override fun countTokens(text: String, model: String): Int {
        val clientProvider = Gemini.clientProvider()
        val client = clientProvider()
        val contents = listOf(
            Content.builder()
                .role("user")
                .parts(listOf(Part.fromText(text)))
                .build()
        )
        val response = client.models.countTokens(model, contents, null)
        return response.totalTokens().orElseThrow()
    }
}

const val GEMINI_FLASH_LATEST = "gemini-flash-latest"
const val GEMINI_3_1_PRO_PREVIEW = "gemini-3.1-pro-preview"
const val GEMINI_3_1_FLASH_LITE = "gemini-3.1-flash-lite-preview"