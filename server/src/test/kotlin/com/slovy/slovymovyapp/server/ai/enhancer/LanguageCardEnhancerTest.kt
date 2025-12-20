package com.slovy.slovymovyapp.server.ai.enhancer

import com.slovy.slovymovyapp.server.ai.AIProviderType
import kotlin.test.Test
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
            val response = LanguageCardEnhancer().enhance(
                request = request,
                provider = provider,
                temperature = 0f,
                reasoningBudget = 1,
                seed = 42,
                model = model,
                maxOutputTokens = 2048
            )

            assertTrue(response.entries.isNotEmpty(), "Provider $providerType should return entries")
            val firstSense = response.entries.first().senses.first()
            assertTrue(firstSense.senseDefinition.isNotBlank(), "Sense definition should not be blank")
            assertTrue(firstSense.learnerLevel.isNotBlank(), "Learner level should not be blank")
            assertTrue(firstSense.frequency.isNotBlank(), "Frequency should not be blank")
        }
    }

    @Test
    fun createRequestFromDbExtractResources() {
        val request = DbExtractEnhancerUtils.createLanguageCardRequest("celebration", "en")
        assertNotNull(request, "Expected request to be created from db_extract test resources")
        assertTrue(request.entries.isNotEmpty())
        assertTrue(request.entries.first().senses.isNotEmpty())
    }
}
