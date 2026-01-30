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
        val schemaMap = jsonElementToMap(jsonObject) as HashMap<String, Any>

        return JsonSchema.Schema.builder()
            .apply {
                schemaMap.forEach { key, value ->
                    putAdditionalProperty(key, JsonValue.from(value))
                }
            }
            .build()
    }

    private fun jsonElementToMap(element: JsonElement): Any {
        return when (element) {
            is JsonPrimitive -> {
                when {
                    element.isString -> element.content
                    element.content.equals("true", ignoreCase = true) -> true
                    element.content.equals("false", ignoreCase = true) -> false
                    element.content.toIntOrNull() != null -> element.content.toInt()
                    element.content.toDoubleOrNull() != null -> element.content.toDouble()
                    else -> element.content
                }
            }

            is JsonObject -> {
                val map = HashMap<String, Any>()
                element.forEach { (key, value) ->
                    map[key] = jsonElementToMap(value)
                }
                map
            }

            is JsonArray -> {
                element.map { jsonElementToMap(it) }
            }
        }
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

    private val cacheJson = Json { prettyPrint = true }
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
            schema = SchemaConverter.geminiToOpenAI(parameters.schema),
            systemPrompt = parameters.systemPrompt,
            maxOutputTokens = parameters.maxOutputTokens,
            topP = parameters.topP,
            verbosity = if (model.supportsVerbosity && parameters.verbosity != null) Verbosity.Known.valueOf(
                parameters.verbosity.name
            ) else null
        )

        // Check cache
        val parametersJson = cacheJson.encodeToString(openAIParams)
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
                ChatModel.GPT_5_1.asString(),
                "OpenAI gpt-5.1",
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

/**
 * Converts between Gemini Schema format and OpenAI JSON Schema format.
 *
 * Gemini uses a custom Schema format, while OpenAI uses standard JSON Schema.
 * This converter handles the transformation between these two formats.
 */
object SchemaConverter {

    /**
     * Converts a Gemini schema string to OpenAI JSON Schema format.
     *
     * @param geminiSchema The schema string in Gemini format (JSON string)
     * @return OpenAI compatible JSON Schema as a JsonObject
     */
    fun geminiToOpenAI(geminiSchema: String): JsonObject {
        val json = Json { ignoreUnknownKeys = true }
        val geminiJsonObject = json.parseToJsonElement(geminiSchema).jsonObject

        return convertJsonObjectToOpenAI(geminiJsonObject)
    }

    /**
     * Recursively converts a Gemini JSON schema object to OpenAI format.
     */
    private fun convertJsonObjectToOpenAI(geminiObj: JsonObject): JsonObject {
        val result = mutableMapOf<String, JsonElement>()

        geminiObj.forEach { (key, value) ->
            when (key) {
                "type" -> {
                    // Type mapping is generally the same
                    result[key] = value
                    if (value.jsonPrimitive.content == "object") {
                        // For objects, we need to add additional properties
                        result["additionalProperties"] = JsonPrimitive(false)
                    }
                }

                "properties" -> {
                    // Recursively convert properties
                    val propsObj = value.jsonObject
                    val convertedProps = mutableMapOf<String, JsonElement>()

                    propsObj.forEach { (propKey, propValue) ->
                        convertedProps[propKey] = convertJsonObjectToOpenAI(propValue.jsonObject)
                    }

                    result[key] = JsonObject(convertedProps)
                }

                "items" -> {
                    // Convert array items schema
                    when (value) {
                        is JsonObject -> {
                            result[key] = convertJsonObjectToOpenAI(value)
                        }

                        else -> {
                            result[key] = value
                        }
                    }
                }

                "enum" -> {
                    // Enum values are the same in both formats
                    result[key] = value
                }

                "required" -> {
                    // Required fields are the same in both formats
                    result[key] = value
                }

                else -> {
                    // Copy other properties as-is
                    result[key] = value
                }
            }
        }

        return JsonObject(result)
    }
}