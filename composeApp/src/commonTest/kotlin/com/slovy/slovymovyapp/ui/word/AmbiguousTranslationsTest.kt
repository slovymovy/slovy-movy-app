package com.slovy.slovymovyapp.ui.word

import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.remote.LanguageCardResponseSense
import com.slovy.slovymovyapp.data.remote.LanguageCardTranslation
import com.slovy.slovymovyapp.data.remote.LearnerLevel
import com.slovy.slovymovyapp.data.remote.SenseFrequency
import kotlin.test.Test
import kotlin.test.assertEquals
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
        assertEquals(setOf("marry", "wed"), result)
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
        assertEquals(setOf("wed"), computeAmbiguousTranslations(senses))
    }

    @Test
    fun empty_senses_returns_empty() {
        assertTrue(computeAmbiguousTranslations(emptyList()).isEmpty())
    }
}
