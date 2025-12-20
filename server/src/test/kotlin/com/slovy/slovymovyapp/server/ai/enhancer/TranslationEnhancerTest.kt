package com.slovy.slovymovyapp.server.ai.enhancer

import com.slovy.slovymovyapp.server.ai.AIProviderType
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class TranslationEnhancerTest : BaseLLMTest() {

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
            reasoningBudget = 1,
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
            reasoningBudget = 1,
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
}
