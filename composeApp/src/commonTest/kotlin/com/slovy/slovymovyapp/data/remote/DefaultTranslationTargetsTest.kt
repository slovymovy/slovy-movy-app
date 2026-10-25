package com.slovy.slovymovyapp.data.remote

import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.favorites.FavoritesRepository
import com.slovy.slovymovyapp.data.settings.SettingsRepository
import com.slovy.slovymovyapp.test.BaseTest
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Every word fetch - search, word details, list detail, lemma recovery - takes its targets from
 * [DictionaryRepository.defaultTranslationTargets], so this is where a configured target the
 * learning language is not translated into has to disappear (issue #973).
 */
class DefaultTranslationTargetsTest : BaseTest() {

    @Test
    fun configuredTargetsAreFilteredByTheSourceLanguage() = runBlocking {
        val database = testAppDatabaseHolder().database
        val settings = SettingsRepository(database)
        val repository = DictionaryRepository(
            testDataDbManager(),
            testLocalDbManager(),
            FavoritesRepository(database),
            settings,
        )
        settings.setTranslationLanguages(listOf(Language.ENGLISH, Language.JAPANESE, Language.POLISH))

        assertEquals(
            setOf(Language.JAPANESE, Language.POLISH),
            repository.defaultTranslationTargets(Language.ENGLISH).toSet(),
            "an English word is requested with everything configured except English itself"
        )
        assertEquals(
            setOf(Language.ENGLISH, Language.POLISH),
            repository.defaultTranslationTargets(Language.RUSSIAN).toSet(),
            "a Russian word is never requested with Japanese, which is generated for English only"
        )
    }
}
