package com.slovy.slovymovyapp.data.learning.session

import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.learning.CardFamily
import com.slovy.slovymovyapp.data.learning.CardKind
import com.slovy.slovymovyapp.data.learning.CardVariant
import com.slovy.slovymovyapp.data.remote.LanguageCardExample
import com.slovy.slovymovyapp.data.remote.LanguageCardResponseSense
import com.slovy.slovymovyapp.data.remote.LanguageCardTranslation
import com.slovy.slovymovyapp.data.remote.LearnerLevel
import com.slovy.slovymovyapp.data.remote.SenseFrequency
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class LearningTaskFactoryTest {

    @Test
    fun produceWord_includesSourceDefinitionToWord_whenDefinitionHasNoTaggedWord() {
        val sense = sense(definition = "a feeling of warmth and comfort")

        val variants = buildTaskVariants(
            family = CardFamily.PRODUCE_WORD,
            sense = sense,
            translationTargets = listOf(Language.ENGLISH),
        )

        assertContains(variants, CardVariant(CardKind.SOURCE_DEFINITION_TO_WORD, targetLang = null))
        assertContains(variants, CardVariant(CardKind.TRANSLATION_TO_WORD, targetLang = Language.ENGLISH.code))
    }

    @Test
    fun produceWord_omitsSourceDefinitionToWord_whenDefinitionContainsTaggedWord() {
        val sense = sense(definition = "to express <w>gezellig</w> feelings")

        val variants = buildTaskVariants(
            family = CardFamily.PRODUCE_WORD,
            sense = sense,
            translationTargets = listOf(Language.ENGLISH),
        )

        assertFalse(
            variants.any { it.kind == CardKind.SOURCE_DEFINITION_TO_WORD },
            "SOURCE_DEFINITION_TO_WORD must be skipped when the source definition reveals the answer word",
        )
        assertEquals(
            listOf(CardVariant(CardKind.TRANSLATION_TO_WORD, targetLang = Language.ENGLISH.code)),
            variants,
        )
    }

    @Test
    fun produceWord_omitsSourceDefinitionToWord_whenTaggedWordIsBlank() {
        val sense = sense(definition = "fills the gap <w></w> nicely")

        val variants = buildTaskVariants(
            family = CardFamily.PRODUCE_WORD,
            sense = sense,
            translationTargets = emptyList(),
        )

        // An empty <w></w> tag carries no answer-revealing content, so the source-definition
        // variant should still be produced.
        assertContains(variants, CardVariant(CardKind.SOURCE_DEFINITION_TO_WORD, targetLang = null))
    }

    private fun sense(definition: String): LanguageCardResponseSense =
        LanguageCardResponseSense(
            senseId = "00000000-0000-0000-0000-000000000001",
            senseDefinition = definition,
            learnerLevel = LearnerLevel.A2,
            frequency = SenseFrequency.HIGH,
            semanticGroupId = "group",
            examples = listOf(
                LanguageCardExample(
                    text = "Het was zo <w>gezellig</w>.",
                    targetLangTranslations = mapOf(Language.ENGLISH to "It was so cosy."),
                ),
            ),
            targetLangDefinitions = mapOf(Language.ENGLISH to "a feeling of warmth"),
            translations = mapOf(
                Language.ENGLISH to listOf(LanguageCardTranslation(targetLangWord = "cosy")),
            ),
        )
}
