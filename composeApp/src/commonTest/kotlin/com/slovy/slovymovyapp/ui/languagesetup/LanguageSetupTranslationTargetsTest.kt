package com.slovy.slovymovyapp.ui.languagesetup

import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.favorites.FavoritesRepository
import com.slovy.slovymovyapp.data.remote.DictionaryClient
import com.slovy.slovymovyapp.data.remote.DictionaryRepository
import com.slovy.slovymovyapp.data.settings.SettingsRepository
import com.slovy.slovymovyapp.test.BaseTest
import com.slovy.slovymovyapp.test.testPlatformDbSupport
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Onboarding keeps only the translation targets the chosen learning language is translated into
 * (issue #973), both for a saved selection and when the learning language is switched.
 */
class LanguageSetupTranslationTargetsTest : BaseTest() {

    private fun viewModel(
        learningLanguage: Language?,
        nativeLanguages: Set<Language>
    ): LanguageSetupViewModel {
        val dataDbManager = testDataDbManager()
        val localDbManager = testLocalDbManager()
        val database = testAppDatabaseHolder().database
        val repository = DictionaryRepository(
            dataDbManager,
            localDbManager,
            FavoritesRepository(database),
            SettingsRepository(database),
        )
        return LanguageSetupViewModel(
            dataDbManager = dataDbManager,
            dictionaryClient = DictionaryClient(testPlatformDbSupport(), repository, localDbManager, dataDbManager),
            initialLearningLanguage = learningLanguage,
            initialNativeLanguages = nativeLanguages
        )
    }

    @Test
    fun savedSelectionDropsTargetsTheLearningLanguageIsNotTranslatedInto() {
        val viewModel = viewModel(Language.RUSSIAN, setOf(Language.ENGLISH, Language.JAPANESE, Language.RUSSIAN))

        assertEquals(
            setOf(Language.ENGLISH),
            viewModel.state.nativeLanguages,
            "Japanese is generated for English only and Russian is the learning language itself"
        )
    }

    @Test
    fun switchingTheLearningLanguageDropsTargetsTheNewOneDoesNotSupport() {
        val viewModel = viewModel(Language.ENGLISH, setOf(Language.JAPANESE, Language.POLISH))

        viewModel.selectLearningLanguage(Language.RUSSIAN)

        assertEquals(
            setOf(Language.POLISH),
            viewModel.state.nativeLanguages,
            "Polish is translated from Russian, Japanese is not"
        )
    }

    @Test
    fun withoutALearningLanguageTheSelectionIsKeptAsIs() {
        val viewModel = viewModel(null, setOf(Language.JAPANESE, Language.POLISH))

        assertEquals(
            setOf(Language.JAPANESE, Language.POLISH),
            viewModel.state.nativeLanguages,
            "nothing can be judged unsupported before a learning language is chosen"
        )
    }
}
