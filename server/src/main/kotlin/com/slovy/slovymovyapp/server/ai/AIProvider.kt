package com.slovy.slovymovyapp.server.ai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class Verbosity {
    LOW, MEDIUM, HIGH
}

/**
 * Unified parameters for AI providers.
 * Maps between different AI provider parameter formats.
 */
@Serializable
data class AIParameters(
    val parts: List<String>,
    val temperature: Float = 0f,
    val seed: Int = 1234567890,
    val reasoningBudget: Int = 2000, // For Gemini: thinkingBudget/thinkingMode, For OpenAI: converted to reasoningEffort
    val model: String,
    val schema: String, // JSON schema string
    val systemPrompt: String,
    val maxOutputTokens: Int = 32768,
    val topP: Float = 1.0f,
    val verbosity: Verbosity? // Supported on GPT-5 series now only
)

enum class AIProviderType {
    GEMINI,
    OPENAI
}

/**
 * Cache interface for AI responses.
 * Implementations should handle serialization and cache key generation internally.
 */
interface AICache {
    /**
     * Gets a cached response for the given parameters.
     * @param parametersJson JSON representation of the parameters (used for cache key generation)
     * @return cached response or null if not found
     */
    fun get(parametersJson: String): String?

    /**
     * Stores a response in the cache.
     * @param parametersJson JSON representation of the parameters
     * @param response the response to cache
     */
    fun put(parametersJson: String, response: String)

    /**
     * Clears all cached responses.
     */
    fun clear()
}

/**
 * No-op cache implementation that doesn't cache anything.
 */
object NoOpAICache : AICache {
    override fun get(parametersJson: String): String? = null
    override fun put(parametersJson: String, response: String) {}
    override fun clear() {}
}

/**
 * Retry strategy interface for handling transient failures.
 */
interface RetryStrategy {
    /**k
     * Executes a block with retry logic.
     * @param block the operation to execute
     * @return the result of the block
     */
    fun <T> execute(block: () -> T): T
}

/**
 * No-op retry strategy that executes the block once without retries.
 */
object NoRetryStrategy : RetryStrategy {
    override fun <T> execute(block: () -> T): T = block()
}

/**
 * Abstract AI provider interface for unified AI completion.
 */
interface AIProvider {
    fun complete(
        parameters: AIParameters,
        cache: AICache = NoOpAICache,
        retryStrategy: RetryStrategy = NoRetryStrategy
    ): String

    fun getAvailableModels(): List<ModelInfo>
    fun isAvailable(): Boolean

    /**
     * Counts tokens for the given text using the specified model.
     * @param text The text to count tokens for
     * @param model The model identifier (used for model-specific tokenization)
     * @return Token count
     */
    fun countTokens(text: String, model: String): Int
}

/**
 * Calculates max output tokens as a multiple of input tokens, clamped to a maximum cap.
 * @param inputTokens The number of input tokens
 * @param multiplier The multiplier to apply (default 5x)
 * @param maxCap The maximum cap for output tokens (default 32768)
 * @return The calculated max output tokens
 */
fun calculateMaxOutputTokens(
    inputTokens: Int,
    multiplier: Int = 3,
    maxCap: Int = 32768
): Int {
    return minOf(inputTokens * multiplier, maxCap)
}


enum class ReasoningType {
    NO,
    HIGH_MEDIUM_LOW_MINIMAL_NONE,
    TOKENS,
    HIGH_LOW,
}

/**
 * Model information for UI display.
 */
@Serializable
data class ModelInfo(
    val value: String,
    @SerialName("display_name") val displayName: String,
    val provider: AIProviderType,
    @SerialName("support_temperature") val supportsTemperature: Boolean,
    @SerialName("support_verbosity") val supportsVerbosity: Boolean,
    @SerialName("reasoning_type") val reasoningType: ReasoningType,
)