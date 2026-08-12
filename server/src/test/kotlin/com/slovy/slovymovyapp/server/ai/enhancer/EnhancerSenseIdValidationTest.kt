package com.slovy.slovymovyapp.server.ai.enhancer

import com.slovy.slovymovyapp.ingestion.LanguageCardExample
import com.slovy.slovymovyapp.ingestion.LanguageCardPosEntry
import com.slovy.slovymovyapp.ingestion.LanguageCardResponse
import com.slovy.slovymovyapp.ingestion.LanguageCardResponseSense
import com.slovy.slovymovyapp.server.ai.AIParameters
import com.slovy.slovymovyapp.server.ai.AIProvider
import com.slovy.slovymovyapp.server.ai.ModelInfo
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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

    @Test
    fun translationEnhancerRejectsResponseThatSkipsAnExample() {
        val request = translationRequestWithExamples("She saw a bright <w>star</w>.", "Call me later, <w>star</w>.")

        val response = TranslationResponse(
            senseTranslations = listOf(
                SenseTranslationData(
                    senseId = "sense-1",
                    targetLangDefinition = "夜空中的亮点",
                    translations = emptyList()
                )
            ),
            exampleTranslations = listOf(
                ExampleTranslationData(
                    originalText = "She saw a bright <w>star</w>.",
                    targetLangTranslation = "她看到一颗明亮的<w>星星</w>。"
                )
            )
        )

        val provider = StubProvider(Json.encodeToString(TranslationResponse.serializer(), response))

        // Rejecting here is what turns a model that drops examples into a provider failure, so the
        // caller's fallback provider can produce the complete response.
        val error = assertFailsWith<IllegalArgumentException> {
            TranslationEnhancer().enhanceWithTranslations(
                request = request,
                provider = provider,
                targetLanguageName = "Simplified Chinese",
                model = "test-model"
            )
        }
        assertTrue(
            error.message?.contains("Call me later") == true,
            "Error should name the untranslated example, was: ${error.message}"
        )
    }

    @Test
    fun translationEnhancerAcceptsExampleTranslationsThatOnlyFuzzyMatch() {
        val request = translationRequestWithExamples("She saw a bright <w>star</w>.")

        // The model echoes the example back with the tags stripped and spacing changed; merging
        // resolves that through the fuzzy fallback, so validation has to accept it too.
        val response = TranslationResponse(
            senseTranslations = listOf(
                SenseTranslationData(
                    senseId = "sense-1",
                    targetLangDefinition = "夜空中的亮点",
                    translations = emptyList()
                )
            ),
            exampleTranslations = listOf(
                ExampleTranslationData(
                    originalText = "She saw a  bright star!",
                    targetLangTranslation = "她看到一颗明亮的<w>星星</w>。"
                )
            )
        )

        val provider = StubProvider(Json.encodeToString(TranslationResponse.serializer(), response))

        val enhanced = TranslationEnhancer().enhanceWithTranslations(
            request = request,
            provider = provider,
            targetLanguageName = "Simplified Chinese",
            model = "test-model"
        )
        assertEquals(1, enhanced.exampleTranslations.size)
    }

    @Test
    fun mergeKeepsSenseTranslationsWhenAnExampleHasNoMatch() {
        val request = translationRequestWithExamples("She saw a bright <w>star</w>.", "Call me later, <w>star</w>.")

        val response = TranslationResponse(
            senseTranslations = listOf(
                SenseTranslationData(
                    senseId = "sense-1",
                    targetLangDefinition = "夜空中的亮点",
                    translations = emptyList()
                )
            ),
            exampleTranslations = listOf(
                ExampleTranslationData(
                    originalText = "She saw a bright <w>star</w>.",
                    targetLangTranslation = "她看到一颗明亮的<w>星星</w>。"
                )
            )
        )

        val merged = TranslationEnhancer().mergeTranslationData(
            originalCard = request.languageCardData,
            translationResponse = response,
            targetLangCode = "zh"
        )

        val sense = merged.entries.single().senses.single()
        assertTrue(sense.targetLangDefinitions.containsKey("zh"), "Sense definition should still be merged")
        assertTrue(
            sense.examples[0].targetLangTranslations.containsKey("zh"),
            "Matched example should carry the translation"
        )
        assertFalse(
            sense.examples[1].targetLangTranslations.containsKey("zh"),
            "Unmatched example should be left untranslated rather than failing the whole language"
        )
    }

    private fun translationRequestWithExamples(vararg exampleTexts: String): TranslationRequest {
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
                            nameType = "no",
                            examples = exampleTexts.map { LanguageCardExample(text = it) }
                        )
                    )
                )
            )
        )
        return TranslationRequest(
            word = "star",
            langCode = "en",
            targetLangCode = "zh",
            languageCardData = languageCard,
            translations = emptyList()
        )
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
