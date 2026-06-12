package com.slovy.slovymovyapp.server.ai.enhancer

import com.google.genai.errors.ApiException
import com.openai.errors.RateLimitException
import com.openai.models.ChatModel
import com.slovy.slovymovyapp.server.ai.*

abstract class BaseLLMTest {
    fun providerFor(type: AIProviderType): AIProvider =
        when (type) {
            AIProviderType.GEMINI -> GeminiProvider()
            AIProviderType.OPENAI -> OpenAIProvider()
        }

    /**
     * Runs [block] and returns its result, or null when the provider rejected the call because
     * the project is out of quota (HTTP 429, e.g. the monthly spend cap is exhausted). Callers
     * should skip such providers, the same way providers without configured credentials are
     * skipped, instead of failing the test.
     */
    fun <T> skipIfOutOfQuota(block: () -> T): T? =
        try {
            block()
        } catch (e: Exception) {
            if (isOutOfQuota(e)) null else throw e
        }

    private fun isOutOfQuota(e: Throwable): Boolean {
        if (e is ApiException && e.code() == 429) return true
        if (e is RateLimitException) return true
        val cause = e.cause
        return cause != null && cause !== e && isOutOfQuota(cause)
    }

    fun pickFastModel(provider: AIProvider): String {
        val models = provider.getAvailableModels()
        val preferred = when (provider) {
            is GeminiProvider -> listOf(GEMINI_FLASH_LATEST)
            is OpenAIProvider -> listOf(
                ChatModel.GPT_5_4.asString()
            )

            else -> emptyList()
        }
        return preferred.firstOrNull { candidate -> models.any { it.value == candidate } }
            ?: models.first().value
    }
}