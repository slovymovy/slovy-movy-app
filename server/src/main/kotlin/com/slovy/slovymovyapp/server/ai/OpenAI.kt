package com.slovy.slovymovyapp.server.ai

import com.knuddels.jtokkit.Encodings
import com.knuddels.jtokkit.api.EncodingType
import com.openai.client.OpenAIClient
import com.openai.client.okhttp.OpenAIOkHttpClient
import com.openai.core.JsonValue
import com.openai.models.ChatModel
import com.openai.models.ReasoningEffort
import com.openai.models.ResponseFormatJsonSchema
import com.openai.models.ResponseFormatJsonSchema.JsonSchema
import com.openai.models.chat.completions.ChatCompletionCreateParams
import com.openai.models.chat.completions.ChatCompletionCreateParams.Verbosity
import com.openai.models.chat.completions.ChatCompletionMessageParam
import com.openai.models.chat.completions.ChatCompletionSystemMessageParam
import com.openai.models.chat.completions.ChatCompletionUserMessageParam
import com.slovy.slovymovyapp.server.ServerJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.io.File

@Serializable
data class OpenAIParameters(
    val parts: List<String>,
    val temperature: Float?,
    val seed: Int,
    val reasoningEffort: ReasoningEffort.Known?,
    val verbosity: Verbosity.Known?,
    val model: String,
    val schema: JsonObject,
    val systemPrompt: String,
    val maxOutputTokens: Int,
    val topP: Float?
)

object OpenAI {

    fun clientProvider(): () -> OpenAIClient = {
        val apiKey = System.getenv("OPENAI_API_KEY")?.takeIf { it.isNotBlank() } ?: run {
            val apiKeyFile = File(".openai_api_key")
            require(apiKeyFile.exists()) { "Missing .openai_api_key file and OPENAI_API_KEY environment variable" }
            apiKeyFile.readText().trim()
        }

        OpenAIOkHttpClient.builder()
            .apiKey(apiKey)
            .build()
    }


    @Suppress("UNCHECKED_CAST")
    internal fun buildJsonSchema(jsonObject: JsonObject): JsonSchema.Schema {
        // Convert JsonObject to a map structure
        val schemaMap = SchemaConverter.toPlainMap(jsonObject) as Map<String, Any>

        return JsonSchema.Schema.builder()
            .apply {
                schemaMap.forEach { key, value ->
                    putAdditionalProperty(key, JsonValue.from(value))
                }
            }
            .build()
    }

    fun openAICompletion(
        client: OpenAIClient,
        parameters: OpenAIParameters
    ): String {
        val messages = mutableListOf<ChatCompletionMessageParam>()

        // Add system message
        messages.add(
            ChatCompletionMessageParam.ofSystem(
                ChatCompletionSystemMessageParam.builder()
                    .content(parameters.systemPrompt)
                    .build()
            )
        )

        // Add user messages from parts
        parameters.parts.forEach { part ->
            messages.add(
                ChatCompletionMessageParam.ofUser(
                    ChatCompletionUserMessageParam.builder()
                        .content(ChatCompletionUserMessageParam.Content.ofText(part))
                        .build()
                )
            )
        }

        val jsonSchema = buildJsonSchema(parameters.schema)


        val requestBuilder = ChatCompletionCreateParams.builder()
            .model(parameters.model)
            .messages(messages)
            .seed(parameters.seed.toLong())
            .maxCompletionTokens(parameters.maxOutputTokens.toLong())
            .responseFormat(
                ResponseFormatJsonSchema.builder()
                    .jsonSchema(
                        JsonSchema.builder()
                            .name("output-format")
                            .schema(jsonSchema)
                            .strict(true)
                            .build()
                    )
                    .build()
            )


        if (parameters.reasoningEffort != null) {
            requestBuilder.reasoningEffort(ReasoningEffort.of(parameters.reasoningEffort.name.lowercase()))
        }
        if (parameters.temperature != null) {
            requestBuilder.temperature(parameters.temperature.toDouble())
        }
        if (parameters.topP != null) {
            requestBuilder.topP(parameters.topP.toDouble())
        }
        if (parameters.verbosity != null) {
            requestBuilder.verbosity(Verbosity.of(parameters.verbosity.name.lowercase()))
        }

        val response = client.chat().completions().create(requestBuilder.build())

        val content = response.choices().firstOrNull()?.message()?.content()
        require(content != null) { "No response content received from OpenAI" }

        return content.get()
    }
}

/**
 * OpenAI AI provider implementation.
 */
class OpenAIProvider : AIProvider {

    private val encodingRegistry = Encodings.newDefaultEncodingRegistry()

    override fun complete(parameters: AIParameters, cache: AICache, retryStrategy: RetryStrategy): String {
        val clientProvider = OpenAI.clientProvider()
        val client = clientProvider()

        val model = getAvailableModels().find { it.value == parameters.model }!!

        val openAIParams = OpenAIParameters(
            parts = parameters.parts,
            temperature = if (model.supportsTemperature) parameters.temperature else null,
            seed = parameters.seed,
            reasoningEffort = if (model.reasoningType != ReasoningType.NO) convertReasoningBudgetToEffort(parameters.reasoningBudget) else null,
            model = parameters.model,
            schema = SchemaConverter.geminiToJsonSchema(parameters.schema),
            systemPrompt = parameters.systemPrompt,
            maxOutputTokens = parameters.maxOutputTokens,
            topP = parameters.topP,
            verbosity = if (model.supportsVerbosity && parameters.verbosity != null) Verbosity.Known.valueOf(
                parameters.verbosity.name
            ) else null
        )

        // Check cache
        val parametersJson = ServerJson.pretty.encodeToString(openAIParams)
        val cachedResponse = cache.get(parametersJson)
        if (cachedResponse != null) {
            return cachedResponse
        }

        val response = retryStrategy.execute {
            OpenAI.openAICompletion(client, openAIParams)
        }

        // Cache the response
        cache.put(parametersJson, response)

        return response
    }

    override fun getAvailableModels(): List<ModelInfo> {
        return listOf(
            ModelInfo(
                ChatModel.O3.asString(),
                "OpenAI o3",
                AIProviderType.OPENAI,
                supportsTemperature = false,
                supportsVerbosity = false,
                reasoningType = ReasoningType.HIGH_MEDIUM_LOW_MINIMAL_NONE
            ),
            ModelInfo(
                ChatModel.O4_MINI.asString(),
                "OpenAI o4-mini",
                AIProviderType.OPENAI,
                supportsTemperature = false,
                supportsVerbosity = false,
                reasoningType = ReasoningType.HIGH_MEDIUM_LOW_MINIMAL_NONE
            ),
            ModelInfo(
                ChatModel.GPT_4_1.asString(),
                "OpenAI 4.1",
                AIProviderType.OPENAI,
                supportsTemperature = true,
                supportsVerbosity = false,
                reasoningType = ReasoningType.NO
            ),
            ModelInfo(
                ChatModel.GPT_4O.asString(),
                "OpenAI 4o",
                AIProviderType.OPENAI,
                supportsTemperature = true,
                supportsVerbosity = false,
                reasoningType = ReasoningType.NO
            ),
            ModelInfo(
                ChatModel.GPT_5_2.asString(),
                "OpenAI gpt-5.2",
                AIProviderType.OPENAI,
                supportsTemperature = false,
                supportsVerbosity = true,
                reasoningType = ReasoningType.HIGH_MEDIUM_LOW_MINIMAL_NONE
            ),
            ModelInfo(
                ChatModel.GPT_5_4.asString(),
                "OpenAI gpt-5.4",
                AIProviderType.OPENAI,
                supportsTemperature = false,
                supportsVerbosity = true,
                reasoningType = ReasoningType.HIGH_MEDIUM_LOW_MINIMAL_NONE
            ),
            ModelInfo(
                ChatModel.GPT_5_4_MINI.asString(),
                "OpenAI gpt-5.4-mini",
                AIProviderType.OPENAI,
                supportsTemperature = false,
                supportsVerbosity = true,
                reasoningType = ReasoningType.HIGH_MEDIUM_LOW_MINIMAL_NONE
            ),
            ModelInfo(
                ChatModel.GPT_5_4_NANO.asString(),
                "OpenAI gpt-5.4-nano",
                AIProviderType.OPENAI,
                supportsTemperature = false,
                supportsVerbosity = true,
                reasoningType = ReasoningType.HIGH_MEDIUM_LOW_MINIMAL_NONE
            ),
            ModelInfo(
                ChatModel.GPT_5_5.asString(),
                "OpenAI gpt-5.5",
                AIProviderType.OPENAI,
                supportsTemperature = false,
                supportsVerbosity = true,
                reasoningType = ReasoningType.HIGH_MEDIUM_LOW_MINIMAL_NONE
            ),
            ModelInfo(
                ChatModel.GPT_5_6_LUNA.asString(),
                "OpenAI gpt-5.6 luna",
                AIProviderType.OPENAI,
                supportsTemperature = false,
                supportsVerbosity = true,
                reasoningType = ReasoningType.HIGH_MEDIUM_LOW_MINIMAL_NONE
            ),
            ModelInfo(
                ChatModel.GPT_5_6_SOL.asString(),
                "OpenAI gpt-5.6 sol",
                AIProviderType.OPENAI,
                supportsTemperature = false,
                supportsVerbosity = true,
                reasoningType = ReasoningType.HIGH_MEDIUM_LOW_MINIMAL_NONE
            ),
            ModelInfo(
                ChatModel.GPT_5_6_TERRA.asString(),
                "OpenAI gpt-5.6 terra",
                AIProviderType.OPENAI,
                supportsTemperature = false,
                supportsVerbosity = true,
                reasoningType = ReasoningType.HIGH_MEDIUM_LOW_MINIMAL_NONE

            ),
            ModelInfo(
                ChatModel.GPT_6_1_SOL.asString(),
                "OpenAI gpt-6.1 sol",
                AIProviderType.OPENAI,
                supportsTemperature = false,
                supportsVerbosity = true,
                reasoningType = ReasoningType.HIGH_MEDIUM_LOW_MINIMAL_NONE
            ),
            ModelInfo(
                ChatModel.GPT_6_SOL.asString(),
                "OpenAI gpt-6 sol",
                AIProviderType.OPENAI,
                supportsTemperature = false,
                supportsVerbosity = true,
                reasoningType = ReasoningType.HIGH_MEDIUM_LOW_MINIMAL_NONE
            ),
            ModelInfo(
                ChatModel.GPT_6_LUNA.asString(),
                "OpenAI gpt-6 luna",
                AIProviderType.OPENAI,
                supportsTemperature = false,
                supportsVerbosity = true,
                reasoningType = ReasoningType.HIGH_MEDIUM_LOW_MINIMAL_NONE
            ),
            ModelInfo(
                ChatModel.GPT_6_ASTRA.asString(),
                "OpenAI gpt-6 astra",
                AIProviderType.OPENAI,
                supportsTemperature = false,
                supportsVerbosity = true,
                reasoningType = ReasoningType.HIGH_MEDIUM_LOW_MINIMAL_NONE
            ),
            ModelInfo(
                ChatModel.GPT_5_1.asString(),
                "OpenAI gpt-5.1",
                AIProviderType.OPENAI,
                supportsTemperature = false,
                supportsVerbosity = true,
                reasoningType = ReasoningType.HIGH_MEDIUM_LOW_MINIMAL_NONE
            ),
            ModelInfo(
                ChatModel.GPT_5_1_MINI.asString(),
                "OpenAI gpt-5.1-mini",
                AIProviderType.OPENAI,
                supportsTemperature = false,
                supportsVerbosity = true,
                reasoningType = ReasoningType.HIGH_MEDIUM_LOW_MINIMAL_NONE
            ),
            ModelInfo(
                ChatModel.GPT_5.asString(),
                "OpenAI gpt-5",
                AIProviderType.OPENAI,
                supportsTemperature = false,
                supportsVerbosity = true,
                reasoningType = ReasoningType.HIGH_MEDIUM_LOW_MINIMAL_NONE
            ),
            ModelInfo(
                ChatModel.GPT_5_MINI.asString(),
                "OpenAI gpt-5-mini",
                AIProviderType.OPENAI,
                supportsTemperature = false,
                supportsVerbosity = true,
                reasoningType = ReasoningType.HIGH_MEDIUM_LOW_MINIMAL_NONE
            ),
            ModelInfo(
                ChatModel.GPT_5_NANO.asString(),
                "OpenAI gpt-5-nano",
                AIProviderType.OPENAI,
                supportsTemperature = false,
                supportsVerbosity = true,
                reasoningType = ReasoningType.HIGH_MEDIUM_LOW_MINIMAL_NONE
            )
        )
    }

    override fun isAvailable(): Boolean {
        return try {
            System.getenv("OPENAI_API_KEY")?.takeIf { it.isNotBlank() } != null ||
                    File(".openai_api_key").exists()
        } catch (_: Exception) {
            false
        }
    }

    override fun countTokens(text: String, model: String): Int {
        return encodingRegistry.getEncoding(EncodingType.O200K_BASE).countTokens(text)
    }


    /**
     * Converts Gemini thinkingBudget (int) to OpenAI reasoningEffort (string).
     *
     * Mapping:
     * - 0-100: "none"
     * - 0-500: "minimal"
     * - 501-1000: "low"
     * - 1001-3000: "medium"
     * - 3001+: "high"
     */
    private fun convertReasoningBudgetToEffort(budget: Int): ReasoningEffort.Known {
        return when {
            budget <= 100 -> ReasoningEffort.Known.NONE
            budget <= 500 -> ReasoningEffort.Known.MINIMAL
            budget <= 1000 -> ReasoningEffort.Known.LOW
            budget <= 3000 -> ReasoningEffort.Known.MEDIUM
            else -> ReasoningEffort.Known.HIGH
        }
    }
}
