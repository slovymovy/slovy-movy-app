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
            val languageCard = LanguageCardEnhancer().enhance(
                request = languageCardRequest,
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

            assertTrue(
                translationResponse.senseTranslations.isNotEmpty(),
                "Provider $providerType ($translationModel) returned no sense translations"
            )
            // Nothing to translate when the card itself carried no examples, so the count says
            // whether the card or the translation step is at fault.
            val cardExamples = languageCard.entries.sumOf { entry -> entry.senses.sumOf { it.examples.size } }
            assertTrue(
                translationResponse.exampleTranslations.isNotEmpty(),
                "Provider $providerType ($translationModel) returned no example translations" +
                        " for a card carrying $cardExamples examples"
            )

            val merged = TranslationEnhancer().mergeTranslationData(languageCard, translationResponse, "ru")
            val firstSense = merged.entries.first().senses.first()
            assertTrue(
                firstSense.translations.containsKey("ru"),
                "Provider $providerType ($translationModel) merged card should include" +
                        " target language translations"
            )
        }
    }
}
