package com.slovy.slovymovyapp.data.remote

import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.recovery.RecoverableSense
import com.slovy.slovymovyapp.test.BaseTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class LemmaRecoveryTest : BaseTest() {
    private val sense1 = "00000000-0000-0000-0000-000000000001"
    private val sense2 = "00000000-0000-0000-0000-000000000002"

    @Test
    fun recoverAllInstalled_groups_items_by_language_and_lemma() = runBlocking {
        val fetches = mutableListOf<FetchCall>()
        val lemmaChecks = mutableListOf<LemmaCheck>()
        val recovery = recovery(
            items = listOf(
                RecoverableSense(Language.ENGLISH, "test", sense1),
                RecoverableSense(Language.ENGLISH, "test", sense2),
            ),
            installedDictionaries = setOf(Language.ENGLISH),
            lemmasNeedingRecovery = setOf("test"),
            fetches = fetches,
            lemmaChecks = lemmaChecks,
        )

        recovery.recoverAllInstalled()

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
    fun recoverAllInstalled_dedups_shared_lemma_across_sources() = runBlocking {
        val fetches = mutableListOf<FetchCall>()
        // Combined provider: "shared" comes from two sources (e.g. a favorite and a word-list
        // sense, different sense ids); "listonly" from one. Recovery groups by (language, lemma),
        // so the shared lemma is fetched once.
        val recovery = recovery(
            items = listOf(
                RecoverableSense(Language.ENGLISH, "shared", sense1),
                RecoverableSense(Language.ENGLISH, "shared", sense2),
                RecoverableSense(Language.ENGLISH, "listonly", sense2),
            ),
            installedDictionaries = setOf(Language.ENGLISH),
            lemmasNeedingRecovery = setOf("shared", "listonly"),
            fetches = fetches,
        )

        recovery.recoverAllInstalled()

        assertEquals(
            setOf(
                FetchCall(Language.ENGLISH, "shared", listOf(Language.RUSSIAN)),
                FetchCall(Language.ENGLISH, "listonly", listOf(Language.RUSSIAN)),
            ),
            fetches.toSet(),
        )
        assertEquals(2, fetches.size, "shared lemma must be fetched once despite two source items")
    }

    @Test
    fun recoverAllInstalled_skips_lemmas_already_covered_by_downloaded_dictionary() = runBlocking {
        val fetches = mutableListOf<FetchCall>()
        val recovery = recovery(
            items = listOf(
                RecoverableSense(Language.ENGLISH, "test", sense1),
                RecoverableSense(Language.ENGLISH, "test", sense2),
            ),
            installedDictionaries = setOf(Language.ENGLISH),
            lemmasNeedingRecovery = emptySet(),
            fetches = fetches,
        )

        recovery.recoverAllInstalled()

        assertEquals(emptyList(), fetches)
    }

    @Test
    fun recoverAllInstalled_skips_languages_without_downloaded_dictionary() = runBlocking {
        val fetches = mutableListOf<FetchCall>()
        val translationChecks = mutableListOf<TranslationCheck>()
        val recovery = recovery(
            items = listOf(RecoverableSense(Language.ENGLISH, "test", sense1)),
            installedDictionaries = emptySet(),
            lemmasNeedingRecovery = emptySet(),
            fetches = fetches,
            translationChecks = translationChecks,
        )

        recovery.recoverAllInstalled()

        assertEquals(emptyList(), fetches)
        assertTrue(
            translationChecks.isEmpty(),
            "Translation check must be skipped when the dictionary is not downloaded",
        )
    }

    @Test
    fun recoverAllInstalled_ignores_fetch_failures_and_continues() = runBlocking {
        val fetches = mutableListOf<FetchCall>()
        val lemmaChecks = mutableListOf<LemmaCheck>()
        val recovery = recovery(
            items = listOf(
                RecoverableSense(Language.ENGLISH, "alpha", sense1),
                RecoverableSense(Language.ENGLISH, "beta", sense2),
            ),
            installedDictionaries = setOf(Language.ENGLISH),
            lemmasNeedingRecovery = setOf("alpha", "beta"),
            fetches = fetches,
            lemmaChecks = lemmaChecks,
            failLemma = "alpha",
        )

        recovery.recoverAllInstalled()

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
    fun recoverAllInstalled_fetches_only_lemmas_that_need_recovery() = runBlocking {
        val fetches = mutableListOf<FetchCall>()
        val recovery = recovery(
            items = listOf(
                RecoverableSense(Language.ENGLISH, "alpha", sense1),
                RecoverableSense(Language.ENGLISH, "beta", sense2),
            ),
            installedDictionaries = setOf(Language.ENGLISH),
            lemmasNeedingRecovery = setOf("alpha"),
            fetches = fetches,
        )

        recovery.recoverAllInstalled()

        assertEquals(
            listOf(FetchCall(Language.ENGLISH, "alpha", listOf(Language.RUSSIAN))),
            fetches,
        )
    }

    @Test
    fun recoverAllInstalled_recovers_lemma_when_translation_missing() = runBlocking {
        val fetches = mutableListOf<FetchCall>()
        val translationChecks = mutableListOf<TranslationCheck>()
        val recovery = recovery(
            items = listOf(RecoverableSense(Language.ENGLISH, "test", sense1)),
            installedDictionaries = setOf(Language.ENGLISH),
            lemmasNeedingRecovery = emptySet(),
            translationsMissingFor = setOf("test"),
            fetches = fetches,
            translationChecks = translationChecks,
        )

        recovery.recoverAllInstalled()

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
    fun recoverAllInstalled_unions_dictionary_and_translation_recovery_sets() = runBlocking {
        val fetches = mutableListOf<FetchCall>()
        val translationChecks = mutableListOf<TranslationCheck>()
        val recovery = recovery(
            items = listOf(
                RecoverableSense(Language.ENGLISH, "alpha", sense1),
                RecoverableSense(Language.ENGLISH, "beta", sense2),
            ),
            installedDictionaries = setOf(Language.ENGLISH),
            lemmasNeedingRecovery = setOf("alpha"),
            translationsMissingFor = setOf("beta"),
            fetches = fetches,
            translationChecks = translationChecks,
        )

        recovery.recoverAllInstalled()

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
    fun recoverAllInstalled_continues_when_translation_check_throws() = runBlocking {
        val fetches = mutableListOf<FetchCall>()
        val recovery = recovery(
            items = listOf(
                RecoverableSense(Language.ENGLISH, "alpha", sense1),
                RecoverableSense(Language.ENGLISH, "beta", sense2),
            ),
            installedDictionaries = setOf(Language.ENGLISH),
            lemmasNeedingRecovery = setOf("alpha"),
            translationsMissingFor = emptySet(),
            translationCheckThrows = true,
            fetches = fetches,
        )

        recovery.recoverAllInstalled()

        // Dictionary recovery still fetches "alpha"; translation failure is swallowed.
        assertEquals(
            listOf(FetchCall(Language.ENGLISH, "alpha", listOf(Language.RUSSIAN))),
            fetches,
        )
    }

    @Test
    fun recoverAllInstalled_reports_progress_for_each_lemma() = runBlocking {
        val progressEvents = mutableListOf<LemmaRecoveryProgress>()
        val recovery = recovery(
            items = listOf(
                RecoverableSense(Language.ENGLISH, "alpha", sense1),
                RecoverableSense(Language.ENGLISH, "beta", sense2),
            ),
            installedDictionaries = setOf(Language.ENGLISH),
            lemmasNeedingRecovery = setOf("alpha", "beta"),
            fetches = mutableListOf(),
        )

        recovery.recoverAllInstalled { progressEvents += it }

        assertTrue(
            progressEvents.any { it.total == 2 && it.completed == 0 },
            "Recovery should report initial total before completing lemmas",
        )
        val finalProgress = progressEvents.lastOrNull()
        assertNotNull(finalProgress, "Recovery should emit at least one progress event")
        assertEquals(2, finalProgress.total)
        assertEquals(finalProgress.total, finalProgress.completed)
        assertEquals(null, finalProgress.currentLemma)
    }

    @Test
    fun recoverAllInstalled_counts_failure_when_fetch_throws() = runBlocking {
        val progressEvents = mutableListOf<LemmaRecoveryProgress>()
        val recovery = recovery(
            items = listOf(
                RecoverableSense(Language.ENGLISH, "alpha", sense1),
                RecoverableSense(Language.ENGLISH, "beta", sense2),
            ),
            installedDictionaries = setOf(Language.ENGLISH),
            lemmasNeedingRecovery = setOf("alpha", "beta"),
            fetches = mutableListOf(),
            failLemma = "alpha",
        )

        recovery.recoverAllInstalled { progressEvents += it }

        val finalProgress = progressEvents.lastOrNull()
        assertNotNull(finalProgress, "Recovery should emit a final progress event")
        assertEquals(2, finalProgress.total)
        assertEquals(finalProgress.total, finalProgress.completed)
        assertEquals(1, finalProgress.failed)
        assertEquals(null, finalProgress.currentLemma)
    }

    @Test
    fun recoverAllInstalled_keeps_current_lemma_while_parallel_group_is_active() = runBlocking {
        val progressEvents = mutableListOf<LemmaRecoveryProgress>()
        val alphaStarted = CompletableDeferred<Unit>()
        val betaStarted = CompletableDeferred<Unit>()
        val allowBetaFinish = CompletableDeferred<Unit>()
        val betaStillActive = CompletableDeferred<LemmaRecoveryProgress>()
        val recovery = LemmaRecovery(
            itemsProvider = {
                listOf(
                    RecoverableSense(Language.ENGLISH, "alpha", sense1),
                    RecoverableSense(Language.ENGLISH, "beta", sense2),
                )
            },
            hasDownloadedDictionary = { language -> language == Language.ENGLISH },
            downloadedLemmasNeedingRecovery = { _, lemmas -> lemmas },
            downloadedSensesNeedingTranslationRecovery = { _, _, _ -> emptySet() },
            translationTargetsProvider = { listOf(Language.RUSSIAN) },
            fetchLemma = { _, lemma, _ ->
                when (lemma) {
                    "alpha" -> {
                        alphaStarted.complete(Unit)
                        betaStarted.await()
                    }

                    "beta" -> {
                        betaStarted.complete(Unit)
                        allowBetaFinish.await()
                    }
                }
            },
        )

        val recoveryJob = launch {
            recovery.recoverAllInstalled { progress ->
                progressEvents += progress
                if (progress.completed == 1 && progress.currentLemma == "beta") {
                    betaStillActive.complete(progress)
                }
            }
        }

        withTimeout(1.seconds.inWholeMilliseconds) {
            alphaStarted.await()
            betaStarted.await()
        }
        withTimeout(1.seconds.inWholeMilliseconds) { betaStillActive.await() }
        allowBetaFinish.complete(Unit)
        withTimeout(1.seconds.inWholeMilliseconds) { recoveryJob.join() }

        val finalProgress = progressEvents.lastOrNull()
        assertNotNull(finalProgress, "Recovery should emit a final progress event")
        assertEquals(finalProgress.total, finalProgress.completed)
        assertEquals(null, finalProgress.currentLemma)
    }

    @Test
    fun recoverAllInstalled_retries_transient_fetch_failure() = runTest {
        var attempts = 0
        val fetches = mutableListOf<FetchCall>()
        val recovery = LemmaRecovery(
            itemsProvider = { listOf(RecoverableSense(Language.ENGLISH, "test", sense1)) },
            hasDownloadedDictionary = { language -> language == Language.ENGLISH },
            downloadedLemmasNeedingRecovery = { _, lemmas -> lemmas },
            downloadedSensesNeedingTranslationRecovery = { _, _, _ -> emptySet() },
            translationTargetsProvider = { listOf(Language.RUSSIAN) },
            fetchLemma = { language, lemma, translationTargets ->
                attempts++
                if (attempts == 1) throw RuntimeException("HTTP 500")
                fetches += FetchCall(language, lemma, translationTargets)
            },
        )

        recovery.recoverAllInstalled()

        assertEquals(2, attempts)
        assertEquals(
            listOf(FetchCall(Language.ENGLISH, "test", listOf(Language.RUSSIAN))),
            fetches,
        )
    }

    @Test
    fun recoverAllInstalled_fetches_missing_lemmas_in_parallel() = runBlocking {
        val startedTogether = CompletableDeferred<Unit>()
        val activeFetches = mutableSetOf<String>()
        var maxActiveFetches = 0
        val recovery = LemmaRecovery(
            itemsProvider = {
                listOf(
                    RecoverableSense(Language.ENGLISH, "alpha", sense1),
                    RecoverableSense(Language.ENGLISH, "beta", sense2),
                )
            },
            hasDownloadedDictionary = { language -> language == Language.ENGLISH },
            downloadedLemmasNeedingRecovery = { _, lemmas -> lemmas },
            downloadedSensesNeedingTranslationRecovery = { _, _, _ -> emptySet() },
            translationTargetsProvider = { listOf(Language.RUSSIAN) },
            fetchLemma = { _, lemma, _ ->
                activeFetches += lemma
                maxActiveFetches = maxOf(maxActiveFetches, activeFetches.size)
                if (activeFetches.size == 2) {
                    startedTogether.complete(Unit)
                }
                withTimeout(1.seconds.inWholeMilliseconds) {
                    startedTogether.await()
                }
                activeFetches -= lemma
            },
        )

        recovery.recoverAllInstalled()

        assertEquals(2, maxActiveFetches)
    }

    private fun recovery(
        items: List<RecoverableSense>,
        installedDictionaries: Set<Language>,
        lemmasNeedingRecovery: Set<String>,
        fetches: MutableList<FetchCall>,
        lemmaChecks: MutableList<LemmaCheck> = mutableListOf(),
        translationChecks: MutableList<TranslationCheck> = mutableListOf(),
        translationsMissingFor: Set<String> = emptySet(),
        translationCheckThrows: Boolean = false,
        failLemma: String? = null,
    ): LemmaRecovery {
        return LemmaRecovery(
            itemsProvider = { items },
            hasDownloadedDictionary = { language -> language in installedDictionaries },
            downloadedLemmasNeedingRecovery = { language, lemmas ->
                lemmaChecks += LemmaCheck(language, lemmas)
                lemmas.intersect(lemmasNeedingRecovery)
            },
            downloadedSensesNeedingTranslationRecovery = { language, senseIdsByLemma, targets ->
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
