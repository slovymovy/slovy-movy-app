package com.slovy.slovymovyapp.server.ai.enhancer

import com.slovy.slovymovyapp.ingestion.LanguageCardPosEntry
import com.slovy.slovymovyapp.ingestion.LanguageCardResponse
import com.slovy.slovymovyapp.ingestion.LanguageCardResponseSense
import com.slovy.slovymovyapp.server.ai.AIParameters
import com.slovy.slovymovyapp.server.ai.AIProvider
import com.slovy.slovymovyapp.server.ai.ModelInfo
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertFailsWith

class EnhancerSenseIdValidationTest {

    @Test
    fun languageCardEnhancerRejectsUnknownSenseIds() {
        val request = LanguageCardRequest(
            word = "test",
            langCode = "en",
            entries = listOf(
                LanguageCardEntry(
                    pos = "noun",
                    senses = listOf(LanguageCardSense(senseId = "sense-1", glosses = listOf("gloss")))
                )
            )
        )

        val response = LanguageCardResponse(
            wordFamily = emptyList(),
            entries = listOf(
                LanguageCardPosEntry(
                    pos = "noun",
                    senses = listOf(
                        LanguageCardResponseSense(
                            senseId = "sense-2",
                            senseDefinition = "definition",
                            learnerLevel = "A1",
                            frequency = "High",
                            semanticGroupId = "group-1",
                            nameType = "no"
                        )
                    )
                )
            )
        )

        val provider = StubProvider(Json.encodeToString(LanguageCardResponse.serializer(), response))

        assertFailsWith<IllegalArgumentException> {
            LanguageCardEnhancer().enhance(
                request = request,
                provider = provider,
                model = "test-model"
            )
        }
    }

    @Test
    fun translationEnhancerRejectsUnknownSenseIds() {
        val languageCard = LanguageCardResponse(
            wordFamily = emptyList(),
            entries = listOf(
                LanguageCardPosEntry(
                    pos = "noun",
                    senses = listOf(
                        LanguageCardResponseSense(
                            senseId = "sense-1",
                            senseDefinition = "definition",
                            learnerLevel = "A1",
                            frequency = "High",
                            semanticGroupId = "group-1",
                            nameType = "no"
                        )
                    )
                )
            )
        )

        val request = TranslationRequest(
            word = "test",
            langCode = "en",
            targetLangCode = "ru",
            languageCardData = languageCard,
            translations = emptyList()
        )

        val response = TranslationResponse(
            senseTranslations = listOf(
                SenseTranslationData(
                    senseId = "sense-2",
                    targetLangDefinition = "определение",
                    translations = emptyList()
                )
            ),
            exampleTranslations = emptyList()
        )

        val provider = StubProvider(Json.encodeToString(TranslationResponse.serializer(), response))

        assertFailsWith<IllegalArgumentException> {
            TranslationEnhancer().enhanceWithTranslations(
                request = request,
                provider = provider,
                targetLanguageName = "Russian",
                model = "test-model"
            )
        }
    }

    private class StubProvider(
        private val response: String
    ) : AIProvider {
        override fun complete(
            parameters: AIParameters,
            cache: com.slovy.slovymovyapp.server.ai.AICache,
            retryStrategy: com.slovy.slovymovyapp.server.ai.RetryStrategy
        ): String = response

        override fun getAvailableModels(): List<ModelInfo> = emptyList()

        override fun isAvailable(): Boolean = true

        override fun countTokens(text: String, model: String): Int {
            return text.split(" ").size
        }
    }
}
