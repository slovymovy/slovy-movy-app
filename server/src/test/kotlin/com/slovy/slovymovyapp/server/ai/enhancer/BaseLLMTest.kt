package com.slovy.slovymovyapp.server.ai.enhancer

import com.openai.models.ChatModel
import com.slovy.slovymovyapp.server.ai.*

abstract class BaseLLMTest {
    fun providerFor(type: AIProviderType): AIProvider =
        when (type) {
            AIProviderType.GEMINI -> GeminiProvider()
            AIProviderType.OPENAI -> OpenAIProvider()
        }

    fun pickFastModel(provider: AIProvider): String {
        val models = provider.getAvailableModels()
        val preferred = when (provider) {
            is GeminiProvider -> listOf(GEMINI_3_0_FLASH_PREVIEW)
            is OpenAIProvider -> listOf(
                ChatModel.GPT_5_2.asString()
            )

            else -> emptyList()
        }
        return preferred.firstOrNull { candidate -> models.any { it.value == candidate } }
            ?: models.first().value
    }
}