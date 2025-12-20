package com.slovy.slovymovyapp.server.ai.enhancer

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class LanguageCardEnhancerTest {

    @Test
    fun testBuildSchemaWithSynonymsAndAntonyms() {
        val enhancer = LanguageCardEnhancer()

        val request = LanguageCardRequest(
            word = "happy",
            langCode = "en",
            entries = listOf(
                LanguageCardEntry(
                    pos = "adjective",
                    senses = listOf(
                        LanguageCardSense(
                            senseId = "sense-1",
                            glosses = listOf("feeling or showing pleasure or contentment")
                        )
                    ),
                    wordLinkages = listOf(
                        LanguageCardWordLinkage(
                            word = "joyful",
                            linkageType = LinkageType.SYNONYMS
                        ),
                        LanguageCardWordLinkage(
                            word = "cheerful",
                            linkageType = LinkageType.SYNONYMS
                        ),
                        LanguageCardWordLinkage(
                            word = "sad",
                            linkageType = LinkageType.ANTONYMS
                        ),
                        LanguageCardWordLinkage(
                            word = "unhappy",
                            linkageType = LinkageType.ANTONYMS
                        )
                    )
                )
            )
        )

        val schema = enhancer.buildSchema(request)

        // Verify schema contains expected structure
        Assertions.assertTrue(schema.contains("\"type\": \"object\""))
        Assertions.assertTrue(schema.contains("\"entries\""))
        Assertions.assertTrue(schema.contains("\"pos\""))
        Assertions.assertTrue(schema.contains("\"senses\""))
        Assertions.assertTrue(schema.contains("\"sense_definition\""))
        Assertions.assertTrue(schema.contains("\"learner_level\""))

        // Verify synonyms are in schema enum
        Assertions.assertTrue(schema.contains("\"joyful\""))
        Assertions.assertTrue(schema.contains("\"cheerful\""))

        // Verify antonyms are in schema enum
        Assertions.assertTrue(schema.contains("\"sad\""))
        Assertions.assertTrue(schema.contains("\"unhappy\""))

        // Verify sense_id enum
        Assertions.assertTrue(schema.contains("\"sense-1\""))
    }

    @Test
    fun testBuildSchemaWithNoLinkages() {
        val enhancer = LanguageCardEnhancer()

        val request = LanguageCardRequest(
            word = "test",
            langCode = "en",
            entries = listOf(
                LanguageCardEntry(
                    pos = "noun",
                    senses = listOf(
                        LanguageCardSense(
                            senseId = "sense-1",
                            glosses = listOf("a procedure intended to establish quality")
                        )
                    ),
                    wordLinkages = emptyList()
                )
            )
        )

        val schema = enhancer.buildSchema(request)

        // Schema should still be valid even without synonyms/antonyms
        Assertions.assertTrue(schema.contains("\"type\": \"object\""))
        Assertions.assertTrue(schema.contains("\"synonyms\""))
        Assertions.assertTrue(schema.contains("\"antonyms\""))
    }

    @Test
    fun testBuildSchemaWithMultipleSenses() {
        val enhancer = LanguageCardEnhancer()

        val request = LanguageCardRequest(
            word = "bank",
            langCode = "en",
            entries = listOf(
                LanguageCardEntry(
                    pos = "noun",
                    senses = listOf(
                        LanguageCardSense(
                            senseId = "sense-financial",
                            glosses = listOf("a financial institution")
                        ),
                        LanguageCardSense(
                            senseId = "sense-river",
                            glosses = listOf("the land alongside a river")
                        )
                    ),
                    wordLinkages = emptyList()
                ),
                LanguageCardEntry(
                    pos = "verb",
                    senses = listOf(
                        LanguageCardSense(
                            senseId = "sense-deposit",
                            glosses = listOf("to deposit money in a bank")
                        )
                    ),
                    wordLinkages = emptyList()
                )
            )
        )

        val schema = enhancer.buildSchema(request)

        // Verify all sense IDs are in the schema
        Assertions.assertTrue(schema.contains("\"sense-financial\""))
        Assertions.assertTrue(schema.contains("\"sense-river\""))
        Assertions.assertTrue(schema.contains("\"sense-deposit\""))
    }

    @Test
    fun testBuildSchemaContainsTraitTypes() {
        val enhancer = LanguageCardEnhancer()

        val request = LanguageCardRequest(
            word = "test",
            langCode = "en",
            entries = listOf(
                LanguageCardEntry(
                    pos = "noun",
                    senses = listOf(
                        LanguageCardSense(
                            senseId = "sense-1",
                            glosses = listOf("test")
                        )
                    )
                )
            )
        )

        val schema = enhancer.buildSchema(request)

        // Verify trait types are in schema
        Assertions.assertTrue(schema.contains("\"archaic\""))
        Assertions.assertTrue(schema.contains("\"informal\""))
        Assertions.assertTrue(schema.contains("\"technical\""))
        Assertions.assertTrue(schema.contains("\"regional\""))
    }

    @Test
    fun testBuildSchemaContainsNameTypes() {
        val enhancer = LanguageCardEnhancer()

        val request = LanguageCardRequest(
            word = "London",
            langCode = "en",
            entries = listOf(
                LanguageCardEntry(
                    pos = "name",
                    senses = listOf(
                        LanguageCardSense(
                            senseId = "sense-1",
                            glosses = listOf("capital city of England")
                        )
                    )
                )
            )
        )

        val schema = enhancer.buildSchema(request)

        // Verify name types are in schema
        Assertions.assertTrue(schema.contains("\"person_name\""))
        Assertions.assertTrue(schema.contains("\"place_name\""))
        Assertions.assertTrue(schema.contains("\"organization_name\""))
    }
}
