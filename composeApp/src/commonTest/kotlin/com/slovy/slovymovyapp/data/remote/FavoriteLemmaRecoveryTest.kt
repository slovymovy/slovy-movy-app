package com.slovy.slovymovyapp.data.remote

import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.favorites.Favorite
import com.slovy.slovymovyapp.test.BaseTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FavoriteLemmaRecoveryTest : BaseTest() {
    private val sense1 = "00000000-0000-0000-0000-000000000001"
    private val sense2 = "00000000-0000-0000-0000-000000000002"

    @Test
    fun recoverAllInstalledFavorites_groups_favorites_by_language_and_lemma() = runBlocking {
        val fetches = mutableListOf<FetchCall>()
        val lemmaChecks = mutableListOf<LemmaCheck>()
        val recovery = recovery(
            favorites = listOf(
                Favorite(sense1, Language.ENGLISH, "test"),
                Favorite(sense2, Language.ENGLISH, "test"),
            ),
            installedDictionaries = setOf(Language.ENGLISH),
            lemmasNeedingRecovery = setOf("test"),
            fetches = fetches,
            lemmaChecks = lemmaChecks,
        )

        recovery.recoverAllInstalledFavorites()

        assertEquals(
            listOf(LemmaCheck(Language.ENGLISH, setOf("test"))),
            lemmaChecks,
        )
        assertEquals(
            listOf(FetchCall(Language.ENGLISH, "test", listOf(Language.RUSSIAN))),
            fetches,
        )
    }

    @Test
    fun recoverAllInstalledFavorites_skips_lemmas_already_covered_by_downloaded_dictionary() = runBlocking {
        val fetches = mutableListOf<FetchCall>()
        val recovery = recovery(
            favorites = listOf(
                Favorite(sense1, Language.ENGLISH, "test"),
                Favorite(sense2, Language.ENGLISH, "test"),
            ),
            installedDictionaries = setOf(Language.ENGLISH),
            lemmasNeedingRecovery = emptySet(),
            fetches = fetches,
        )

        recovery.recoverAllInstalledFavorites()

        assertEquals(emptyList(), fetches)
    }

    @Test
    fun recoverAllInstalledFavorites_skips_languages_without_downloaded_dictionary() = runBlocking {
        val fetches = mutableListOf<FetchCall>()
        val translationChecks = mutableListOf<TranslationCheck>()
        val recovery = recovery(
            favorites = listOf(Favorite(sense1, Language.ENGLISH, "test")),
            installedDictionaries = emptySet(),
            lemmasNeedingRecovery = emptySet(),
            fetches = fetches,
            translationChecks = translationChecks,
        )

        recovery.recoverAllInstalledFavorites()

        assertEquals(emptyList(), fetches)
        assertTrue(
            translationChecks.isEmpty(),
            "Translation check must be skipped when the dictionary is not downloaded",
        )
    }

    @Test
    fun recoverAllInstalledFavorites_ignores_fetch_failures_and_continues() = runBlocking {
        val fetches = mutableListOf<FetchCall>()
        val lemmaChecks = mutableListOf<LemmaCheck>()
        val recovery = recovery(
            favorites = listOf(
                Favorite(sense1, Language.ENGLISH, "alpha"),
                Favorite(sense2, Language.ENGLISH, "beta"),
            ),
            installedDictionaries = setOf(Language.ENGLISH),
            lemmasNeedingRecovery = setOf("alpha", "beta"),
            fetches = fetches,
            lemmaChecks = lemmaChecks,
            failLemma = "alpha",
        )

        recovery.recoverAllInstalledFavorites()

        assertEquals(
            listOf(LemmaCheck(Language.ENGLISH, setOf("alpha", "beta"))),
            lemmaChecks,
        )
        assertEquals(
            listOf(FetchCall(Language.ENGLISH, "beta", listOf(Language.RUSSIAN))),
            fetches,
        )
    }

    @Test
    fun recoverAllInstalledFavorites_fetches_only_lemmas_that_need_recovery() = runBlocking {
        val fetches = mutableListOf<FetchCall>()
        val recovery = recovery(
            favorites = listOf(
                Favorite(sense1, Language.ENGLISH, "alpha"),
                Favorite(sense2, Language.ENGLISH, "beta"),
            ),
            installedDictionaries = setOf(Language.ENGLISH),
            lemmasNeedingRecovery = setOf("alpha"),
            fetches = fetches,
        )

        recovery.recoverAllInstalledFavorites()

        assertEquals(
            listOf(FetchCall(Language.ENGLISH, "alpha", listOf(Language.RUSSIAN))),
            fetches,
        )
    }

    @Test
    fun recoverAllInstalledFavorites_recovers_lemma_when_translation_missing() = runBlocking {
        val fetches = mutableListOf<FetchCall>()
        val translationChecks = mutableListOf<TranslationCheck>()
        val recovery = recovery(
            favorites = listOf(Favorite(sense1, Language.ENGLISH, "test")),
            installedDictionaries = setOf(Language.ENGLISH),
            lemmasNeedingRecovery = emptySet(),
            translationsMissingFor = setOf("test"),
            fetches = fetches,
            translationChecks = translationChecks,
        )

        recovery.recoverAllInstalledFavorites()

        assertEquals(
            listOf(
                TranslationCheck(
                    language = Language.ENGLISH,
                    senseIdsByLemma = mapOf("test" to setOf(sense1)),
                    translationTargets = listOf(Language.RUSSIAN),
                )
            ),
            translationChecks,
        )
        assertEquals(
            listOf(FetchCall(Language.ENGLISH, "test", listOf(Language.RUSSIAN))),
            fetches,
        )
    }

    @Test
    fun recoverAllInstalledFavorites_unions_dictionary_and_translation_recovery_sets() = runBlocking {
        val fetches = mutableListOf<FetchCall>()
        val translationChecks = mutableListOf<TranslationCheck>()
        val recovery = recovery(
            favorites = listOf(
                Favorite(sense1, Language.ENGLISH, "alpha"),
                Favorite(sense2, Language.ENGLISH, "beta"),
            ),
            installedDictionaries = setOf(Language.ENGLISH),
            lemmasNeedingRecovery = setOf("alpha"),
            translationsMissingFor = setOf("beta"),
            fetches = fetches,
            translationChecks = translationChecks,
        )

        recovery.recoverAllInstalledFavorites()

        assertEquals(1, translationChecks.size, "Translation check should fire exactly once")
        // The lemma already flagged for dictionary recovery must not be re-checked for translations.
        assertEquals(
            mapOf("beta" to setOf(sense2)),
            translationChecks.single().senseIdsByLemma,
        )
        assertEquals(
            setOf(
                FetchCall(Language.ENGLISH, "alpha", listOf(Language.RUSSIAN)),
                FetchCall(Language.ENGLISH, "beta", listOf(Language.RUSSIAN)),
            ),
            fetches.toSet(),
        )
    }

    @Test
    fun recoverAllInstalledFavorites_continues_when_translation_check_throws() = runBlocking {
        val fetches = mutableListOf<FetchCall>()
        val recovery = recovery(
            favorites = listOf(
                Favorite(sense1, Language.ENGLISH, "alpha"),
                Favorite(sense2, Language.ENGLISH, "beta"),
            ),
            installedDictionaries = setOf(Language.ENGLISH),
            lemmasNeedingRecovery = setOf("alpha"),
            translationsMissingFor = emptySet(),
            translationCheckThrows = true,
            fetches = fetches,
        )

        recovery.recoverAllInstalledFavorites()

        // Dictionary recovery still fetches "alpha"; translation failure is swallowed.
        assertEquals(
            listOf(FetchCall(Language.ENGLISH, "alpha", listOf(Language.RUSSIAN))),
            fetches,
        )
    }

    @Test
    fun recoverAllInstalledFavorites_fetches_missing_lemmas_in_parallel() = runBlocking {
        val startedTogether = CompletableDeferred<Unit>()
        val activeFetches = mutableSetOf<String>()
        var maxActiveFetches = 0
        val recovery = FavoriteLemmaRecovery(
            favoritesProvider = {
                listOf(
                    Favorite(sense1, Language.ENGLISH, "alpha"),
                    Favorite(sense2, Language.ENGLISH, "beta"),
                )
            },
            hasDownloadedDictionary = { language -> language == Language.ENGLISH },
            downloadedLemmasNeedingRecovery = { _, lemmas -> lemmas },
            downloadedFavoritesNeedingTranslationRecovery = { _, _, _ -> emptySet() },
            translationTargetsProvider = { listOf(Language.RUSSIAN) },
            fetchLemma = { _, lemma, _ ->
                activeFetches += lemma
                maxActiveFetches = maxOf(maxActiveFetches, activeFetches.size)
                if (activeFetches.size == 2) {
                    startedTogether.complete(Unit)
                }
                withTimeout(1_000) {
                    startedTogether.await()
                }
                activeFetches -= lemma
            },
        )

        recovery.recoverAllInstalledFavorites()

        assertEquals(2, maxActiveFetches)
    }

    private fun recovery(
        favorites: List<Favorite>,
        installedDictionaries: Set<Language>,
        lemmasNeedingRecovery: Set<String>,
        fetches: MutableList<FetchCall>,
        lemmaChecks: MutableList<LemmaCheck> = mutableListOf(),
        translationChecks: MutableList<TranslationCheck> = mutableListOf(),
        translationsMissingFor: Set<String> = emptySet(),
        translationCheckThrows: Boolean = false,
        failLemma: String? = null,
    ): FavoriteLemmaRecovery {
        return FavoriteLemmaRecovery(
            favoritesProvider = { favorites },
            hasDownloadedDictionary = { language -> language in installedDictionaries },
            downloadedLemmasNeedingRecovery = { language, lemmas ->
                lemmaChecks += LemmaCheck(language, lemmas)
                lemmas.intersect(lemmasNeedingRecovery)
            },
            downloadedFavoritesNeedingTranslationRecovery = { language, senseIdsByLemma, targets ->
                translationChecks += TranslationCheck(language, senseIdsByLemma, targets)
                if (translationCheckThrows) throw RuntimeException("Translation check failed")
                senseIdsByLemma.keys.intersect(translationsMissingFor)
            },
            translationTargetsProvider = { listOf(Language.RUSSIAN, it, Language.RUSSIAN) },
            fetchLemma = { language, lemma, translationTargets ->
                if (lemma == failLemma) throw RuntimeException("Fetch failed")
                fetches += FetchCall(language, lemma, translationTargets)
            },
        )
    }

    private data class FetchCall(
        val language: Language,
        val lemma: String,
        val translationTargets: List<Language>,
    )

    private data class LemmaCheck(
        val language: Language,
        val lemmas: Set<String>,
    )

    private data class TranslationCheck(
        val language: Language,
        val senseIdsByLemma: Map<String, Set<String>>,
        val translationTargets: List<Language>,
    )
}
