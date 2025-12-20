package com.slovy.slovymovyapp.server.ai.enhancer

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class TranslationEnhancerTest {

    @Test
    fun testMergeTranslationData() {
        val enhancer = TranslationEnhancer()

        // Create original language card
        val originalCard = LanguageCardResponse(
            entries = listOf(
                LanguageCardPosEntry(
                    pos = "noun",
                    senses = listOf(
                        LanguageCardResponseSense(
                            senseId = "test-sense-1",
                            senseDefinition = "A financial institution",
                            learnerLevel = "A2",
                            frequency = "High",
                            semanticGroupId = "financial",
                            examples = listOf(
                                LanguageCardExample(text = "I went to the bank")
                            )
                        )
                    )
                )
            )
        )

        // Create translation response
        val translationResponse = TranslationResponse(
            senseTranslations = listOf(
                SenseTranslationData(
                    senseId = "test-sense-1",
                    targetLangDefinition = "Финансовое учреждение",
                    translations = listOf(
                        LanguageCardTranslation(
                            targetLangWord = "банк",
                            targetLangSenseClarification = "финансовое учреждение"
                        )
                    )
                )
            ),
            exampleTranslations = listOf(
                ExampleTranslationData(
                    originalText = "I went to the bank",
                    targetLangTranslation = "Я пошёл в банк"
                )
            )
        )

        // Merge translation data
        val mergedCard = enhancer.mergeTranslationData(originalCard, translationResponse, "ru")

        // Verify translations were merged correctly
        Assertions.assertEquals(1, mergedCard.entries.size)
        val sense = mergedCard.entries[0].senses[0]
        Assertions.assertEquals(1, sense.translations.keys.size)
        Assertions.assertTrue(sense.translations.containsKey("ru"))
        Assertions.assertEquals(1, sense.translations["ru"]!!.size)
        Assertions.assertEquals("банк", sense.translations["ru"]!![0].targetLangWord)
        Assertions.assertEquals("финансовое учреждение", sense.translations["ru"]!![0].targetLangSenseClarification)

        // Verify example translations were merged
        val example = sense.examples[0]
        Assertions.assertTrue(example.targetLangTranslations.containsKey("ru"))
        Assertions.assertEquals("Я пошёл в банк", example.targetLangTranslations["ru"])
    }

    @Test
    fun testMergeTranslationDataWithWTags() {
        val enhancer = TranslationEnhancer()

        // Create original language card with <w> tags in example
        val originalCard = LanguageCardResponse(
            entries = listOf(
                LanguageCardPosEntry(
                    pos = "noun",
                    senses = listOf(
                        LanguageCardResponseSense(
                            senseId = "test-sense-1",
                            senseDefinition = "A financial institution",
                            learnerLevel = "A2",
                            frequency = "High",
                            semanticGroupId = "financial",
                            examples = listOf(
                                LanguageCardExample(text = "I went to the <w>bank</w>")
                            )
                        )
                    )
                )
            )
        )

        // Create translation response - note the original text doesn't have <w> tags
        val translationResponse = TranslationResponse(
            senseTranslations = listOf(
                SenseTranslationData(
                    senseId = "test-sense-1",
                    targetLangDefinition = "Финансовое учреждение",
                    translations = listOf(
                        LanguageCardTranslation(
                            targetLangWord = "банк",
                            targetLangSenseClarification = "финансовое учреждение"
                        )
                    )
                )
            ),
            exampleTranslations = listOf(
                ExampleTranslationData(
                    originalText = "I went to the bank",
                    targetLangTranslation = "Я пошёл в <w>банк</w>"
                )
            )
        )

        // Merge translation data - should match despite <w> tags
        val mergedCard = enhancer.mergeTranslationData(originalCard, translationResponse, "ru")

        // Verify example translations were merged correctly
        val example = mergedCard.entries[0].senses[0].examples[0]
        Assertions.assertTrue(example.targetLangTranslations.containsKey("ru"))
        Assertions.assertEquals("Я пошёл в <w>банк</w>", example.targetLangTranslations["ru"])
    }

    @Test
    fun testBuildTranslationSchema() {
        val enhancer = TranslationEnhancer()

        val languageCardData = LanguageCardResponse(
            entries = listOf(
                LanguageCardPosEntry(
                    pos = "noun",
                    senses = listOf(
                        LanguageCardResponseSense(
                            senseId = "sense-1",
                            senseDefinition = "Definition 1",
                            learnerLevel = "A1",
                            frequency = "High",
                            semanticGroupId = "group1"
                        ),
                        LanguageCardResponseSense(
                            senseId = "sense-2",
                            senseDefinition = "Definition 2",
                            learnerLevel = "B1",
                            frequency = "Middle",
                            semanticGroupId = "group2"
                        )
                    )
                )
            )
        )

        val request = TranslationRequest(
            word = "test",
            langCode = "en",
            targetLangCode = "ru",
            languageCardData = languageCardData,
            translations = emptyList()
        )

        val schema = enhancer.buildTranslationSchema(request)

        // Verify schema contains expected elements
        Assertions.assertTrue(schema.contains("\"sense_id\""))
        Assertions.assertTrue(schema.contains("\"sense-1\""))
        Assertions.assertTrue(schema.contains("\"sense-2\""))
        Assertions.assertTrue(schema.contains("\"target_lang_definition\""))
        Assertions.assertTrue(schema.contains("\"translations\""))
        Assertions.assertTrue(schema.contains("\"example_translations\""))
    }
}
