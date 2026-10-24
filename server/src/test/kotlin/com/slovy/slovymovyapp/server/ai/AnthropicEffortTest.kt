package com.slovy.slovymovyapp.server.ai

import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AnthropicEffortTest {

    @Test
    fun forReasoningBudget_mapsEachBandToItsEffortLevel() {
        val expected = mapOf(
            1 to AnthropicEffort.LOW,
            900 to AnthropicEffort.LOW,
            1_000 to AnthropicEffort.LOW,
            1_001 to AnthropicEffort.MEDIUM,
            3_000 to AnthropicEffort.MEDIUM,
            3_001 to AnthropicEffort.HIGH,
            10_000 to AnthropicEffort.HIGH,
            10_001 to AnthropicEffort.XHIGH,
            20_000 to AnthropicEffort.XHIGH,
            20_001 to AnthropicEffort.MAX
        )
        for ((budget, effort) in expected) {
            assertEquals(effort, AnthropicEffort.forReasoningBudget(budget), "Budget $budget")
        }
    }

    @Test
    fun thinkingAllowance_growsWithEffort() {
        AnthropicEffort.entries.zipWithNext { lower, higher ->
            assertTrue(
                higher.thinkingAllowance > lower.thinkingAllowance,
                "${higher.name} should leave more thinking room than ${lower.name}"
            )
        }
    }

    @Test
    fun maxTokensOf_addsThinkingRoomOnTopOfTheRequestedOutput() {
        val maxTokens = Anthropic.maxTokensOf(parametersWith(maxOutputTokens = 2_048, effort = AnthropicEffort.LOW))
        assertEquals(2_048L + AnthropicEffort.LOW.thinkingAllowance, maxTokens)
    }

    @Test
    fun maxTokensOf_clampsToTheModelCeiling() {
        val maxTokens = Anthropic.maxTokensOf(parametersWith(maxOutputTokens = 32_768, effort = AnthropicEffort.MAX))
        assertEquals(Anthropic.MAX_TOKENS_LIMIT.toLong(), maxTokens)
    }

    @Test
    fun maxTokensOf_stillLeavesThinkingRoomWithoutAnEffortLevel() {
        val maxTokens = Anthropic.maxTokensOf(parametersWith(maxOutputTokens = 2_048, effort = null))
        assertEquals(2_048L + AnthropicEffort.LOW.thinkingAllowance, maxTokens)
    }

    private fun parametersWith(maxOutputTokens: Int, effort: AnthropicEffort?) = AnthropicParameters(
        parts = listOf("{}"),
        effort = effort,
        model = CLAUDE_HAIKU_5_5,
        schema = JsonObject(emptyMap()),
        systemPrompt = "system",
        maxOutputTokens = maxOutputTokens
    )
}
