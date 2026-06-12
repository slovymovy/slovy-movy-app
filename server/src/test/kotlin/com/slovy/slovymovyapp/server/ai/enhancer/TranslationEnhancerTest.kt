package com.slovy.slovymovyapp.server.ai.enhancer

import com.slovy.slovymovyapp.server.ai.AIProviderType
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TranslationEnhancerTest : BaseLLMTest() {

    @Test
    fun enhanceTranslationsFromDbExtract() {
        for (providerType in AIProviderType.entries) {
            val provider = providerFor(providerType)
            if (!provider.isAvailable()) continue

            val languageCardRequest =
                DbExtractEnhancerUtils.createLanguageCardRequest(word = "celebration", langCode = "en")
            assertNotNull(languageCardRequest, "Expected language card request from db_extract resources")

            val languageCardModel = pickFastModel(provider)
            val languageCard = skipIfOutOfQuota {
                LanguageCardEnhancer().enhance(
                    request = languageCardRequest,
                    provider = provider,
                    temperature = 0f,
                    reasoningBudget = 1,
                    seed = 42,
                    model = languageCardModel,
                    maxOutputTokens = 2048
                )
            } ?: continue

            val translationRequest = DbExtractEnhancerUtils.createTranslationRequest(
                word = "celebration",
                langCode = "en",
                targetLangCode = "ru",
                languageCardData = languageCard
            )

            val translationModel = pickFastModel(provider)
            val translationResponse = skipIfOutOfQuota {
                TranslationEnhancer().enhanceWithTranslations(
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
            } ?: continue

            assertTrue(translationResponse.senseTranslations.isNotEmpty(), "Should contain sense translations")
            assertTrue(translationResponse.exampleTranslations.isNotEmpty(), "Should contain example translations")

            val merged = TranslationEnhancer().mergeTranslationData(languageCard, translationResponse, "ru")
            val firstSense = merged.entries.first().senses.first()
            assertTrue(
                firstSense.translations.containsKey("ru"),
                "Merged card should include target language translations"
            )
        }
    }
}
