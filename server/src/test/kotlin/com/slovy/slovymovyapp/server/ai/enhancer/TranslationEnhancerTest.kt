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
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class TranslationEnhancerTest {

    @ParameterizedTest
    @EnumSource(AIProviderType::class)
    fun enhanceTranslationsFromDbExtract(providerType: AIProviderType) {
        val provider = providerFor(providerType)
        assertTrue(provider.isAvailable(), "Provider $providerType must be available for real AI call")

        val languageCardRequest =
            DbExtractEnhancerUtils.createLanguageCardRequest(word = "celebration", langCode = "en")
        assertNotNull(languageCardRequest, "Expected language card request from db_extract resources")

        val languageCardModel = pickFastModel(provider)
        val languageCard = LanguageCardEnhancer().enhance(
            request = languageCardRequest!!,
            provider = provider,
            temperature = 0f,
            reasoningBudget = 200,
            seed = 42,
            model = languageCardModel,
            maxOutputTokens = 2048
        )

        val translationRequest = DbExtractEnhancerUtils.createTranslationRequest(
            word = "celebration",
            langCode = "en",
            targetLangCode = "ru",
            languageCardData = languageCard
        )

        val translationModel = pickFastModel(provider)
        val translationResponse = TranslationEnhancer().enhanceWithTranslations(
            request = translationRequest,
            provider = provider,
            targetLanguageName = DbExtractEnhancerUtils.targetLanguageName("ru"),
            systemPrompt = EnhancerPrompts.TRANSLATION_SYSTEM_PROMPT,
            temperature = 0f,
            reasoningBudget = 200,
            model = translationModel,
            seed = 42,
            maxOutputTokens = 2048
        )

        assertTrue(translationResponse.senseTranslations.isNotEmpty(), "Should contain sense translations")
        assertTrue(translationResponse.exampleTranslations.isNotEmpty(), "Should contain example translations")

        val merged = TranslationEnhancer().mergeTranslationData(languageCard, translationResponse, "ru")
        val firstSense = merged.entries.first().senses.first()
        assertTrue(firstSense.translations.containsKey("ru"), "Merged card should include target language translations")
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
