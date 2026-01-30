package com.slovy.slovymovyapp.server.ai.enhancer

import com.slovy.slovymovyapp.server.ai.AIProviderType
import kotlin.test.Test
import kotlin.test.assertTrue

class TokenCountingTest : BaseLLMTest() {

    @Test
    fun countTokens_returnsPositiveCountForSimpleText() {
        for (providerType in AIProviderType.entries) {
            val provider = providerFor(providerType)
            if (!provider.isAvailable()) continue

            for (modelInfo in provider.getAvailableModels()) {
                val unicodeText = "Hello мир 世界"
                val count = provider.countTokens(unicodeText, modelInfo.value)
                assertTrue(count > 0, "Unicode text should have positive token count")
            }
        }
    }
}