package com.slovy.slovymovyapp.server.ai.enhancer

import com.slovy.slovymovyapp.server.ai.*
import com.slovy.slovymovyapp.server.ai.AIProviderType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LanguageCardEnhancerTest : BaseLLMTest() {

    @Test
    fun enhanceFromDbExtract_withRealAI() {
        for (providerType in AIProviderType.entries) {
            val provider = providerFor(providerType)
            if (!provider.isAvailable()) continue

            val request = DbExtractEnhancerUtils.createLanguageCardRequest(word = "celebration", langCode = "en")
            assertNotNull(request, "Expected request to be built from db_extract resources")

            val model = pickFastModel(provider)
            val response = skipIfOutOfQuota {
                LanguageCardEnhancer().enhance(
                    request = request,
                    provider = provider,
                    temperature = 0f,
                    reasoningBudget = 1,
                    seed = 42,
                    model = model,
                    maxOutputTokens = 2048
                )
            } ?: continue

            assertTrue(response.entries.isNotEmpty(), "Provider $providerType should return entries")
            val firstSense = response.entries.first().senses.first()
            assertTrue(firstSense.senseDefinition.isNotBlank(), "Sense definition should not be blank")
            assertTrue(firstSense.learnerLevel.isNotBlank(), "Learner level should not be blank")
            assertTrue(firstSense.frequency.isNotBlank(), "Frequency should not be blank")
        }
    }

    @Test
    fun enhance_stripsWTagsFromAllPlainTextFields() {
        val senseId = "abc123"
        val taggedJson = """
            {
              "word_family": ["<w>runner</w>", "run"],
              "entries": [{
                "pos": "verb",
                "senses": [{
                  "sense_id": "$senseId",
                  "sense_definition": "to <w>move</w> quickly",
                  "learner_level": "A1",
                  "frequency": "High",
                  "semantic_group_id": "motion",
                  "synonyms": ["<w>sprint</w>", "dash"],
                  "antonyms": ["<w>walk</w>"],
                  "common_phrases": ["<w>run away</w>", "run wild"]
                }]
              }]
            }
        """.trimIndent()

        val fakeProvider = object : AIProvider {
            override fun complete(parameters: AIParameters, cache: AICache, retryStrategy: RetryStrategy) = taggedJson
            override fun getAvailableModels() = emptyList<ModelInfo>()
            override fun isAvailable() = true
            override fun countTokens(text: String, model: String) = 0
        }

        val request = LanguageCardRequest(
            word = "run", langCode = "en",
            entries = listOf(
                LanguageCardEntry(
                    pos = "verb",
                    senses = listOf(LanguageCardSense(senseId = senseId, glosses = listOf("to move quickly")))
                )
            )
        )

        val response = LanguageCardEnhancer().enhance(request, fakeProvider, model = "fake")

        // sense_definition and examples keep <w> markup (intentional highlighting)
        assertEquals("to <w>move</w> quickly", response.entries[0].senses[0].senseDefinition)

        // plain-text fields must have all <w> tags stripped
        val sense = response.entries[0].senses[0]
        assertEquals(listOf("sprint", "dash"), sense.synonyms)
        assertEquals(listOf("walk"), sense.antonyms)
        assertEquals(listOf("run away", "run wild"), sense.commonPhrases)
        assertEquals(listOf("runner", "run"), response.wordFamily)
    }

    @Test
    fun createRequestFromDbExtractResources() {
        val request = DbExtractEnhancerUtils.createLanguageCardRequest("celebration", "en")
        assertNotNull(request, "Expected request to be created from db_extract test resources")
        assertTrue(request.entries.isNotEmpty())
        assertTrue(request.entries.first().senses.isNotEmpty())
    }
}
