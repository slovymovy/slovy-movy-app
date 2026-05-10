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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

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

    @Test
    fun selectVariantsForReview_filtersHardVariants_belowMinStability() {
        val sense = sense(definition = "a feeling of warmth and comfort")

        val variants = selectVariantsForReview(
            family = CardFamily.PRODUCE_WORD,
            sense = sense,
            translationTargets = listOf(Language.ENGLISH),
            cardStability = 1.days,
            lastVariant = null,
        )

        assertEquals(
            listOf(CardVariant(CardKind.TRANSLATION_TO_WORD, targetLang = Language.ENGLISH.code)),
            variants,
            "SOURCE_DEFINITION_TO_WORD must be hidden until card stability >= 7 days",
        )
    }

    @Test
    fun selectVariantsForReview_includesHardVariants_whenStabilityCrossesGate() {
        val sense = sense(definition = "a feeling of warmth and comfort")

        val variants = selectVariantsForReview(
            family = CardFamily.PRODUCE_WORD,
            sense = sense,
            translationTargets = listOf(Language.ENGLISH),
            cardStability = 8.days,
            lastVariant = null,
        )

        // Translation-cued variants (priority = 0) sort ahead of same-language variants (priority = 1).
        assertEquals(
            listOf(
                CardVariant(CardKind.TRANSLATION_TO_WORD, targetLang = Language.ENGLISH.code),
                CardVariant(CardKind.SOURCE_DEFINITION_TO_WORD, targetLang = null),
            ),
            variants,
        )
    }

    @Test
    fun selectVariantsForReview_filtersClozeSource_belowMinStability() {
        // Sense with a tagged word in BOTH the source example and the English translation, so
        // both CLOZE_SOURCE and CLOZE_TRANSLATION are eligible from buildTaskVariants.
        val sense = LanguageCardResponseSense(
            senseId = "00000000-0000-0000-0000-000000000003",
            senseDefinition = "a feeling of warmth and comfort",
            learnerLevel = LearnerLevel.A2,
            frequency = SenseFrequency.HIGH,
            semanticGroupId = "group",
            examples = listOf(
                LanguageCardExample(
                    text = "Het was zo <w>gezellig</w>.",
                    targetLangTranslations = mapOf(Language.ENGLISH to "It was so <w>cosy</w>."),
                ),
            ),
            targetLangDefinitions = mapOf(Language.ENGLISH to "a feeling of warmth"),
            translations = mapOf(
                Language.ENGLISH to listOf(LanguageCardTranslation(targetLangWord = "cosy")),
            ),
        )

        val variants = selectVariantsForReview(
            family = CardFamily.PRODUCE_WORD_IN_CONTEXT,
            sense = sense,
            translationTargets = listOf(Language.ENGLISH),
            cardStability = 1.days,
            lastVariant = null,
        )

        assertEquals(
            listOf(CardVariant(CardKind.CLOZE_TRANSLATION, targetLang = Language.ENGLISH.code)),
            variants,
            "CLOZE_SOURCE must be hidden until card stability >= 7 days",
        )
    }

    @Test
    fun selectVariantsForReview_fallsBackToGatedVariants_whenNoEasyAlternativesExist() {
        val sense = senseWithoutTranslations(definition = "a feeling of warmth and comfort")

        val variants = selectVariantsForReview(
            family = CardFamily.RECOGNIZE_SENSE,
            sense = sense,
            translationTargets = emptyList(),
            cardStability = Duration.ZERO,
            lastVariant = null,
        )

        // No translations available, so RECOGNIZE_SENSE has only the gated WORD_TO_SOURCE_DEFINITION.
        // Without a fallback the user would never see this card, so we surface the gated variant anyway.
        assertEquals(
            listOf(CardVariant(CardKind.WORD_TO_SOURCE_DEFINITION, targetLang = null)),
            variants,
        )
    }

    @Test
    fun selectVariantsForReview_pushesLastReviewedVariantToBack() {
        val sense = sense(definition = "a feeling of warmth and comfort")
        val lastVariant = CardVariant(CardKind.TRANSLATION_TO_WORD, targetLang = Language.ENGLISH.code)

        val variants = selectVariantsForReview(
            family = CardFamily.PRODUCE_WORD,
            sense = sense,
            translationTargets = listOf(Language.ENGLISH),
            cardStability = 30.days,
            lastVariant = lastVariant,
        )

        // Without lastVariant, TRANSLATION_TO_WORD (priority 0) would lead. Marking it as last-reviewed
        // bumps it to the end so the user is not shown the same variant twice in a row, leaving the
        // same-language SOURCE_DEFINITION_TO_WORD ahead of it.
        assertEquals(
            listOf(
                CardVariant(CardKind.SOURCE_DEFINITION_TO_WORD, targetLang = null),
                CardVariant(CardKind.TRANSLATION_TO_WORD, targetLang = Language.ENGLISH.code),
            ),
            variants,
        )
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

    private fun senseWithoutTranslations(definition: String): LanguageCardResponseSense =
        LanguageCardResponseSense(
            senseId = "00000000-0000-0000-0000-000000000002",
            senseDefinition = definition,
            learnerLevel = LearnerLevel.A2,
            frequency = SenseFrequency.HIGH,
            semanticGroupId = "group",
            examples = listOf(
                LanguageCardExample(
                    text = "Het was zo gezellig.",
                    targetLangTranslations = emptyMap(),
                ),
            ),
            targetLangDefinitions = emptyMap(),
            translations = emptyMap(),
        )
}
