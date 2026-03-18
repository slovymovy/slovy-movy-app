package com.slovy.slovymovyapp.ui.word

import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.remote.LanguageCardResponseSense
import com.slovy.slovymovyapp.data.remote.LanguageCardTranslation
import com.slovy.slovymovyapp.data.remote.LearnerLevel
import com.slovy.slovymovyapp.data.remote.SenseFrequency
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AmbiguousTranslationsTest {

    private fun sense(
        id: String,
        translations: Map<Language, List<LanguageCardTranslation>>
    ) = LanguageCardResponseSense(
        senseId = id,
        senseDefinition = "",
        learnerLevel = LearnerLevel.A1,
        frequency = SenseFrequency.HIGH,
        semanticGroupId = "",
        translations = translations
    )

    private fun trans(vararg words: String) =
        words.map { LanguageCardTranslation(targetLangWord = it) }

    @Test
    fun identical_translations_flagged() {
        val senses = listOf(
            sense("1", mapOf(Language.ENGLISH to trans("marry", "wed"))),
            sense("2", mapOf(Language.ENGLISH to trans("marry", "wed")))
        )
        val result = computeAmbiguousTranslations(senses)
        assertEquals(setOf("marry", "wed"), result["1"])
        assertEquals(setOf("marry", "wed"), result["2"])
    }

    @Test
    fun different_translations_not_flagged() {
        val senses = listOf(
            sense("1", mapOf(Language.ENGLISH to trans("get married", "marry", "wed"))),
            sense("2", mapOf(Language.ENGLISH to trans("marry", "wed")))
        )
        assertTrue(computeAmbiguousTranslations(senses).isEmpty())
    }

    @Test
    fun single_sense_never_flagged() {
        val senses = listOf(
            sense("1", mapOf(Language.ENGLISH to trans("marry")))
        )
        assertTrue(computeAmbiguousTranslations(senses).isEmpty())
    }

    @Test
    fun same_words_different_languages_not_flagged() {
        val senses = listOf(
            sense("1", mapOf(Language.ENGLISH to trans("bank"), Language.DUTCH to trans("bank"))),
            sense("2", mapOf(Language.ENGLISH to trans("bank"), Language.DUTCH to trans("oever")))
        )
        assertTrue(computeAmbiguousTranslations(senses).isEmpty())
    }

    @Test
    fun multi_word_translations_excluded_from_result() {
        val senses = listOf(
            sense("1", mapOf(Language.ENGLISH to trans("get married", "wed"))),
            sense("2", mapOf(Language.ENGLISH to trans("get married", "wed")))
        )
        // fingerprints match → ambiguous, but "get married" is multi-word so excluded from result
        assertEquals(setOf("wed"), computeAmbiguousTranslations(senses)["1"])
        assertEquals(setOf("wed"), computeAmbiguousTranslations(senses)["2"])
    }

    @Test
    fun empty_senses_returns_empty() {
        assertTrue(computeAmbiguousTranslations(emptyList()).isEmpty())
    }

    @Test
    fun non_duplicated_sense_not_flagged_when_sharing_word_with_duplicated_group() {
        // senses "1" and "2" are duplicated; sense "3" shares "bank" but has a different set
        val senses = listOf(
            sense("1", mapOf(Language.ENGLISH to trans("bank"))),
            sense("2", mapOf(Language.ENGLISH to trans("bank"))),
            sense("3", mapOf(Language.ENGLISH to trans("bank", "shore")))
        )
        val result = computeAmbiguousTranslations(senses)
        assertEquals(setOf("bank"), result["1"])
        assertEquals(setOf("bank"), result["2"])
        assertFalse(result.containsKey("3"), "sense 3 must not be flagged")
    }

    @Test
    fun duplicate_entries_within_sense_treated_as_single_word() {
        // same word listed twice in one sense — should still match a sense with it once
        val senses = listOf(
            sense("1", mapOf(Language.ENGLISH to trans("bank", "bank"))),
            sense("2", mapOf(Language.ENGLISH to trans("bank")))
        )
        val result = computeAmbiguousTranslations(senses)
        assertEquals(setOf("bank"), result["1"])
        assertEquals(setOf("bank"), result["2"])
    }
}
