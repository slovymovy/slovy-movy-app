package com.slovy.slovymovyapp.ui.settings

import com.slovy.slovymovyapp.data.Language
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The Settings picker is derived from the installed learning languages and the current selection
 * (issue #973): one learning language shows its own targets, several show their union, and a row
 * names the learning languages it does nothing for.
 */
class TranslationLanguageOptionsTest {

    private fun learning(vararg languages: Language) = languages.map {
        LearningLanguageUiState(language = it, dictionarySizeBytes = 1)
    }

    @Test
    fun oneLearningLanguageShowsExactlyItsOwnTargets() {
        val state = SettingsUiState(learningLanguages = learning(Language.ENGLISH))

        assertEquals(
            Language.translationTargetsFor(Language.ENGLISH).sortedBy { it.selfName },
            state.translationLanguageOptions.map { it.language },
            "an English learner sees every target English is translated into, Japanese and Portuguese included"
        )
        assertEquals(
            emptyList(),
            state.translationLanguageOptions.flatMap { it.unsupportedFor },
            "nothing is unsupported when every row comes from the single learning language"
        )
    }

    @Test
    fun russianLearnerIsNotOfferedAnEnglishOnlyTarget() {
        val state = SettingsUiState(learningLanguages = learning(Language.RUSSIAN))

        val offered = state.translationLanguageOptions.map { it.language }
        assertEquals(false, Language.JAPANESE in offered, "Japanese is generated for English only")
        assertEquals(false, Language.PORTUGUESE in offered, "Portuguese is generated for English only")
        assertEquals(true, Language.ENGLISH in offered, "English is a target for a Russian learner")
    }

    @Test
    fun severalLearningLanguagesShowTheUnionAndMarkTheGaps() {
        val state = SettingsUiState(
            learningLanguages = learning(Language.ENGLISH, Language.RUSSIAN),
            translationLanguages = setOf(Language.JAPANESE, Language.POLISH)
        )

        val byLanguage = state.translationLanguageOptions.associateBy { it.language }
        assertEquals(
            listOf(Language.RUSSIAN),
            byLanguage.getValue(Language.JAPANESE).unsupportedFor,
            "Japanese is in the union through English and is marked as doing nothing for Russian"
        )
        assertEquals(emptyList(), byLanguage.getValue(Language.POLISH).unsupportedFor, "Polish serves both")
        assertEquals(
            emptyList(),
            byLanguage.getValue(Language.ENGLISH).unsupportedFor,
            "English is in the union through Russian; being the learner's own language is not a gap"
        )
        assertEquals(
            state.translationLanguageOptions.map { it.language.selfName },
            state.translationLanguageOptions.map { it.language.selfName }.sorted(),
            "rows are sorted by self name like the old flat list"
        )
    }

    @Test
    fun aSelectionNoLearningLanguageSupportsStaysVisibleSoItCanBeDropped() {
        val state = SettingsUiState(
            learningLanguages = learning(Language.RUSSIAN),
            translationLanguages = setOf(Language.JAPANESE)
        )

        val japanese = state.translationLanguageOptions.single { it.language == Language.JAPANESE }
        assertEquals(
            listOf(Language.RUSSIAN),
            japanese.unsupportedFor,
            "the stale selection is listed with the learning language it does nothing for"
        )
    }
}
