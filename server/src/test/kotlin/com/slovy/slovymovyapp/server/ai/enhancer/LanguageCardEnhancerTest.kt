package com.slovy.slovymovyapp.server.ai.enhancer

import com.openai.models.ChatModel
import com.slovy.slovymovyapp.server.ai.AIProvider
import com.slovy.slovymovyapp.server.ai.AIProviderType
import com.slovy.slovymovyapp.server.ai.GEMINI_2_5_FLASH
import com.slovy.slovymovyapp.server.ai.GEMINI_2_5_FLASH_LITE
import com.slovy.slovymovyapp.server.ai.GEMINI_3_0_FLASH_PREVIEW
import com.slovy.slovymovyapp.server.ai.GeminiProvider
import com.slovy.slovymovyapp.server.ai.OpenAIProvider
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class LanguageCardEnhancerTest {

    @ParameterizedTest
    @EnumSource(AIProviderType::class)
    fun enhanceFromDbExtract_withRealAI(providerType: AIProviderType) {
        val provider = providerFor(providerType)
        assertTrue(provider.isAvailable(), "Provider $providerType must be available for real AI call")

        val request = DbExtractEnhancerUtils.createLanguageCardRequest(word = "celebration", langCode = "en")
        assertNotNull(request, "Expected request to be built from db_extract resources")

        val model = pickFastModel(provider)
        val response = LanguageCardEnhancer().enhance(
            request = request!!,
            provider = provider,
            temperature = 0f,
            reasoningBudget = 200,
            seed = 42,
            model = model,
            maxOutputTokens = 2048
        )

        assertTrue(response.entries.isNotEmpty())
        val firstSense = response.entries.first().senses.first()
        assertTrue(firstSense.senseDefinition.isNotBlank(), "Sense definition should not be blank")
        assertTrue(firstSense.learnerLevel.isNotBlank(), "Learner level should not be blank")
        assertTrue(firstSense.frequency.isNotBlank(), "Frequency should not be blank")
    }

    @Test
    fun createRequestFromDbExtractResources() {
        val request = DbExtractEnhancerUtils.createLanguageCardRequest("celebration", "en")
        assertNotNull(request, "Expected request to be created from db_extract test resources")
        assertTrue(request!!.entries.isNotEmpty())
        assertTrue(request.entries.first().senses.isNotEmpty())
    }

    private fun providerFor(type: AIProviderType): AIProvider =
        when (type) {
            AIProviderType.GEMINI -> GeminiProvider()
            AIProviderType.OPENAI -> OpenAIProvider()
        }

    private fun pickFastModel(provider: AIProvider): String {
        val models = provider.getAvailableModels()
        val preferred = when (provider) {
            is GeminiProvider -> listOf(GEMINI_2_5_FLASH, GEMINI_2_5_FLASH_LITE, GEMINI_3_0_FLASH_PREVIEW)
            is OpenAIProvider -> listOf(
                ChatModel.GPT_5_NANO.asString(),
                ChatModel.O4_MINI.asString(),
                ChatModel.GPT_4O.asString()
            )
            else -> emptyList()
        }
        return preferred.firstOrNull { candidate -> models.any { it.value == candidate } }
            ?: models.first().value
    }
}
