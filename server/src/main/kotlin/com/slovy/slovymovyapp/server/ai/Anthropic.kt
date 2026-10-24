package com.slovy.slovymovyapp.server.ai

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.helpers.BetaMessageAccumulator
import com.anthropic.models.beta.AnthropicBeta
import com.anthropic.models.beta.messages.BetaJsonOutputFormat
import com.anthropic.models.beta.messages.BetaMessage
import com.anthropic.models.beta.messages.BetaOutputConfig
import com.anthropic.models.beta.messages.BetaStopReason
import com.anthropic.models.beta.messages.BetaThinkingConfigAdaptive
import com.anthropic.models.beta.messages.MessageCountTokensParams
import com.anthropic.models.beta.messages.MessageCreateParams
import com.slovy.slovymovyapp.server.ServerJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import java.io.File

/**
 * How hard a Claude model is asked to think, and how much room that thinking needs.
 *
 * Adaptive thinking is billed to `max_tokens` along with the answer, so a request has to ask for
 * the caller's output ceiling *plus* [thinkingAllowance] - otherwise thinking eats the ceiling and
 * the structured output comes back truncated.
 */
@Serializable
enum class AnthropicEffort(val thinkingAllowance: Int) {
    LOW(8_192),
    MEDIUM(16_384),
    HIGH(32_768),
    XHIGH(65_536),
    MAX(96_000);

    companion object {
        /**
         * The effort level a Gemini-style thinking budget asks for.
         *
         * Anthropic takes no token budget - effort is the only dial - so the budget only has to
         * land in the right band:
         * - 0-1000: [LOW]
         * - 1001-3000: [MEDIUM]
         * - 3001-10000: [HIGH]
         * - 10001-20000: [XHIGH]
         * - 20001+: [MAX]
         */
        fun forReasoningBudget(budget: Int): AnthropicEffort = when {
            budget <= 1_000 -> LOW
            budget <= 3_000 -> MEDIUM
            budget <= 10_000 -> HIGH
            budget <= 20_000 -> XHIGH
            else -> MAX
        }
    }
}

/**
 * Request shape for the Anthropic Messages API, and the cache key of a completion.
 *
 * The current Claude models reject `temperature`, `top_p` and `seed` outright - the SDK no longer
 * even exposes them - so none of those reach the wire and none belong in the cache key. Thinking is
 * always adaptive; [effort] is what steers how much of it happens.
 */
@Serializable
data class AnthropicParameters(
    val parts: List<String>,
    val effort: AnthropicEffort?,
    val model: String,
    val schema: JsonObject,
    val systemPrompt: String,
    val maxOutputTokens: Int
)

object Anthropic {

    const val API_KEY_ENV = "CLAUDE_API_KEY"

    /** Synchronous Messages API output ceiling of every current Claude model. */
    internal const val MAX_TOKENS_LIMIT = 128_000

    /** The SDK's own variable, honoured second so an `ant`/SDK setup keeps working as it is. */
    private const val SDK_API_KEY_ENV = "ANTHROPIC_API_KEY"
    private const val API_KEY_FILE = ".claude_api_key"

    fun clientProvider(): () -> AnthropicClient = {
        AnthropicOkHttpClient.builder()
            .apiKey(resolveApiKey())
            .build()
    }

    internal fun resolveApiKey(): String =
        System.getenv(API_KEY_ENV)?.takeIf { it.isNotBlank() }
            ?: System.getenv(SDK_API_KEY_ENV)?.takeIf { it.isNotBlank() }
            ?: run {
                val apiKeyFile = File(API_KEY_FILE)
                require(apiKeyFile.exists()) {
                    "Missing $API_KEY_FILE file and $API_KEY_ENV environment variable"
                }
                apiKeyFile.readText().trim()
            }

    /** [block] against a fresh client, closed afterwards: `AnthropicClient` is not [AutoCloseable]. */
    internal fun <T> withClient(block: (AnthropicClient) -> T): T {
        val client = clientProvider()()
        try {
            return block(client)
        } finally {
            client.close()
        }
    }

    internal fun hasApiKey(): Boolean =
        System.getenv(API_KEY_ENV)?.isNotBlank() == true ||
                System.getenv(SDK_API_KEY_ENV)?.isNotBlank() == true ||
                File(API_KEY_FILE).exists()

    @Suppress("UNCHECKED_CAST")
    internal fun buildJsonSchema(jsonObject: JsonObject): BetaJsonOutputFormat.Schema {
        val schemaMap = SchemaConverter.toPlainMap(jsonObject) as Map<String, Any>

        return BetaJsonOutputFormat.Schema.builder()
            .apply {
                schemaMap.forEach { (key, value) ->
                    putAdditionalProperty(key, JsonValue.from(value))
                }
            }
            .build()
    }

    /**
     * One structured-output completion.
     *
     * Streaming is not an optional nicety here: with a 32k output ceiling plus adaptive thinking a
     * single call can outlive the non-streaming HTTP timeout, so the response is accumulated from
     * the event stream instead.
     *
     * `fallbacks` is on because the ingested words include vulgar and otherwise sensitive entries:
     * a safety refusal is a realistic answer for a dictionary, and server-side routing retries it
     * on a model picked for the refusal category instead of failing the word.
     */
    fun anthropicCompletion(
        client: AnthropicClient,
        parameters: AnthropicParameters
    ): String {
        val outputConfig = BetaOutputConfig.builder()
            .apply { parameters.effort?.let { effort(BetaOutputConfig.Effort.of(it.name.lowercase())) } }
            .format(
                BetaJsonOutputFormat.builder()
                    .schema(buildJsonSchema(parameters.schema))
                    .build()
            )
            .build()

        val requestBuilder = MessageCreateParams.builder()
            .model(parameters.model)
            .maxTokens(maxTokensOf(parameters))
            .system(parameters.systemPrompt)
            .thinking(BetaThinkingConfigAdaptive.builder().build())
            .outputConfig(outputConfig)
            .addBeta(AnthropicBeta.SERVER_SIDE_FALLBACK_2026_07_01)
            .fallbacksDefault()

        parameters.parts.forEach { requestBuilder.addUserMessage(it) }

        val message = client.beta().messages().createStreaming(requestBuilder.build()).use { stream ->
            val accumulator = BetaMessageAccumulator.create()
            stream.stream().forEach { accumulator.accumulate(it) }
            accumulator.message()
        }

        return textOf(message, maxTokensOf(parameters))
    }

    /**
     * The caller's output ceiling plus the thinking headroom of its effort level, clamped to
     * [MAX_TOKENS_LIMIT]. A model with no effort level still thinks, so it gets the smallest
     * allowance rather than none.
     */
    internal fun maxTokensOf(parameters: AnthropicParameters): Long {
        val allowance = (parameters.effort ?: AnthropicEffort.LOW).thinkingAllowance
        return minOf(parameters.maxOutputTokens.toLong() + allowance, MAX_TOKENS_LIMIT.toLong())
    }

    /**
     * The JSON of a structured-output response, or an error naming why there is none. Thinking
     * blocks are skipped: only text blocks carry the structured output.
     */
    private fun textOf(message: BetaMessage, maxTokens: Long): String {
        when (message.stopReason().orElse(null)) {
            BetaStopReason.REFUSAL -> {
                val details = message.stopDetails().orElse(null)
                error(
                    "Anthropic refused the request" +
                            " (category=${details?.category()?.orElse(null)}," +
                            " explanation=${details?.explanation()?.orElse(null)})"
                )
            }

            BetaStopReason.MAX_TOKENS -> error(
                "Anthropic response was truncated at $maxTokens output tokens"
            )

            else -> Unit
        }

        val text = message.content()
            .mapNotNull { block -> block.text().orElse(null)?.text() }
            .joinToString("")
        require(text.isNotBlank()) { "No response content received from Anthropic" }
        return text
    }
}

/**
 * Anthropic AI provider implementation.
 */
class AnthropicProvider : AIProvider {

    override fun complete(parameters: AIParameters, cache: AICache, retryStrategy: RetryStrategy): String {
        val model = getAvailableModels().first { it.value == parameters.model }

        val anthropicParams = AnthropicParameters(
            parts = parameters.parts,
            effort = if (model.reasoningType == ReasoningType.EFFORT_LOW_TO_MAX) {
                AnthropicEffort.forReasoningBudget(parameters.reasoningBudget)
            } else {
                null
            },
            model = parameters.model,
            schema = SchemaConverter.geminiToJsonSchema(parameters.schema),
            systemPrompt = parameters.systemPrompt,
            maxOutputTokens = parameters.maxOutputTokens
        )

        // Check cache
        val parametersJson = ServerJson.pretty.encodeToString(anthropicParams)
        val cachedResponse = cache.get(parametersJson)
        if (cachedResponse != null) {
            return cachedResponse
        }

        val response = Anthropic.withClient { client ->
            retryStrategy.execute {
                Anthropic.anthropicCompletion(client, anthropicParams)
            }
        }

        // Cache the response
        cache.put(parametersJson, response)

        return response
    }

    override fun getAvailableModels(): List<ModelInfo> {
        return listOf(
            ModelInfo(
                CLAUDE_OPUS_5_5, "Claude Opus 5.5", AIProviderType.ANTHROPIC,
                supportsTemperature = false,
                supportsVerbosity = false,
                reasoningType = ReasoningType.EFFORT_LOW_TO_MAX
            ),
            ModelInfo(
                CLAUDE_SONNET_5_5, "Claude Sonnet 5.5", AIProviderType.ANTHROPIC,
                supportsTemperature = false,
                supportsVerbosity = false,
                reasoningType = ReasoningType.EFFORT_LOW_TO_MAX
            ),
            ModelInfo(
                CLAUDE_HAIKU_5_5, "Claude Haiku 5.5", AIProviderType.ANTHROPIC,
                supportsTemperature = false,
                supportsVerbosity = false,
                reasoningType = ReasoningType.EFFORT_LOW_TO_MAX
            ),
            ModelInfo(
                CLAUDE_FABLE_5_1, "Claude Fable 5.1", AIProviderType.ANTHROPIC,
                supportsTemperature = false,
                supportsVerbosity = false,
                reasoningType = ReasoningType.EFFORT_LOW_TO_MAX
            )
        )
    }

    override fun isAvailable(): Boolean {
        return try {
            Anthropic.hasApiKey()
        } catch (_: Exception) {
            false
        }
    }

    override fun countTokens(text: String, model: String): Int {
        return Anthropic.withClient { client ->
            client.beta().messages().countTokens(
                MessageCountTokensParams.builder()
                    .model(model)
                    .addUserMessage(text)
                    .build()
            ).inputTokens().toInt()
        }
    }
}

const val CLAUDE_OPUS_5_5 = "claude-opus-5-5"
const val CLAUDE_SONNET_5_5 = "claude-sonnet-5-5"
const val CLAUDE_HAIKU_5_5 = "claude-haiku-5-5"
const val CLAUDE_FABLE_5_1 = "claude-fable-5-1"
