package com.slovy.slovymovyapp.data.remote

import com.slovy.slovymovyapp.analytics.PerformanceMonitoring
import com.slovy.slovymovyapp.analytics.useWithResult
import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.recovery.RecoverableSense
import com.slovy.slovymovyapp.logging.AppLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlin.time.Duration.Companion.seconds

/**
 * Ensures every lemma referenced by a set of [RecoverableSense]s (favorites and curated word-list
 * senses) is present — with translations — in the local DB, fetching missing ones from the server.
 * Source-agnostic: callers supply the items via [itemsProvider], so favorites and lists are
 * recovered in one combined, deduped pass.
 */
class LemmaRecovery internal constructor(
    private val itemsProvider: suspend () -> List<RecoverableSense>,
    private val hasDownloadedDictionary: (Language) -> Boolean,
    private val downloadedLemmasNeedingRecovery: suspend (Language, Set<String>) -> Set<String>,
    private val downloadedSensesNeedingTranslationRecovery:
    suspend (Language, Map<String, Set<String>>, List<Language>) -> Set<String>,
    private val translationTargetsProvider: suspend (Language) -> List<Language>,
    private val fetchLemma: suspend (Language, String, List<Language>) -> Unit,
    private val resolveSenses:
    suspend (Language, String, Set<String>) -> Map<String, DictionaryRepository.SenseLookupResult>,
) {
    // Shared across recoverAllInstalled and every recoverSenses call, so concurrent recovery
    // passes (e.g. a post-download pass plus per-row list repairs) never exceed the cap in total.
    private val groupSemaphore = Semaphore(MAX_PARALLEL_RECOVERY_GROUPS)

    constructor(
        itemsProvider: suspend () -> List<RecoverableSense>,
        dataDbManager: DataDbManager,
        dictionaryRepository: DictionaryRepository,
        wordFetchManager: WordFetchManager,
    ) : this(
        itemsProvider = itemsProvider,
        hasDownloadedDictionary = { language -> dataDbManager.hasDictionary(language) },
        downloadedLemmasNeedingRecovery = { language, lemmas ->
            dataDbManager.downloadedLemmasNeedingRecovery(language, lemmas)
        },
        downloadedSensesNeedingTranslationRecovery = { language, senseIdsByLemma, targets ->
            dataDbManager.downloadedSensesNeedingTranslationRecovery(language, senseIdsByLemma, targets)
        },
        translationTargetsProvider = { language -> dictionaryRepository.defaultTranslationTargets(language) },
        fetchLemma = { language, lemma, translationTargets ->
            val terminal = wordFetchManager.getWord(
                language = language,
                lemma = lemma,
                translationTargets = translationTargets,
                pushToRepo = false,
            ).first { result ->
                !result.isWordLoading && !result.isTranslationLoading
            }
            terminal.error?.let { err ->
                if (err is DictionaryClientException.CancelledException) {
                    throw CancellationException(err.message)
                }
                throw err
            }
        },
        resolveSenses = { language, lemma, senseIds ->
            dictionaryRepository.getSenses(
                language = language,
                lemma = lemma,
                senseIds = senseIds,
                translationTargets = dictionaryRepository.defaultTranslationTargets(language),
            )
        },
    )

    suspend fun recoverAllInstalled(
        onProgress: (LemmaRecoveryProgress) -> Unit = {},
    ): LemmaRecoveryOutcome {
        return PerformanceMonitoring.startTrace("lemma_recovery").useWithResult {
            try {
                val items = itemsProvider()
                putMetric("items", items.size.toLong())
                putMetric("languages", items.map { it.language.code }.distinct().size.toLong())
                val summary = recover(items, onProgress)
                putMetric("groups_total", summary.total.toLong())
                putMetric("groups_completed", summary.completed.toLong())
                putMetric("groups_failed", summary.failed.toLong())
                markResult(when {
                    summary.checksFailed -> "check_failed"
                    summary.total == 0 -> "nothing_to_recover"
                    summary.failed > 0 -> "partial_failure"
                    else -> "success"
                })
                LemmaRecoveryOutcome(
                    total = summary.total,
                    failed = summary.failed,
                    // A swallowed pre-flight check means whole languages were never inspected, so
                    // an empty group list proves nothing about what is still missing.
                    fullyRecovered = summary.failed == 0 && !summary.checksFailed,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                markResult("failed")
                AppLogger.warn(TAG, "Unable to load items for recovery", e)
                // Recovery must never block a completed database download.
                LemmaRecoveryOutcome(total = 0, failed = 0, fullyRecovered = false)
            }
        }
    }

    private suspend fun recover(
        items: List<RecoverableSense>,
        onProgress: (LemmaRecoveryProgress) -> Unit,
    ): RecoverySummary = coroutineScope {
        val groups = items
            .mapNotNull { item ->
                val lemma = item.lemma.trim()
                if (lemma.isBlank()) null else LemmaGroupKey(item.language, lemma.lowercase()) to item
            }
            .groupBy({ it.first }, { it.second })
        val targets = groupsByDownloadedLemmaStatus(groups)
        val groupsToRecover = targets.groups
        val total = groupsToRecover.size
        val progressMutex = Mutex()
        var completed = 0
        var failed = 0
        val activeLemmas = linkedMapOf<LemmaGroupKey, String>()

        suspend fun markStarted(key: LemmaGroupKey, lemma: String) = progressMutex.withLock {
            activeLemmas[key] = lemma
            onProgress(LemmaRecoveryProgress(activeLemmas.values.firstOrNull(), completed, total, failed))
        }

        suspend fun markDone(key: LemmaGroupKey, deltaFailed: Int) = progressMutex.withLock {
            completed++
            if (deltaFailed > 0) failed += deltaFailed
            activeLemmas.remove(key)
            onProgress(LemmaRecoveryProgress(activeLemmas.values.firstOrNull(), completed, total, failed))
        }

        groupsToRecover.map { (key, groupItems) ->
            async {
                groupSemaphore.withPermit {
                    var failedGroup = false
                    val lemma = groupItems.first().lemma.trim()
                    try {
                        markStarted(key, lemma)
                        recoverLemmaGroup(key.language, groupItems)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        failedGroup = true
                        AppLogger.warn(
                            TAG,
                            "Unable to recover lemma '$lemma' (${key.language.code})",
                            e,
                        )
                        // Best-effort recovery: broken or unavailable remote fetches must not block downloads.
                    } finally {
                        markDone(key, deltaFailed = if (failedGroup) 1 else 0)
                    }
                }
            }
        }.awaitAll()

        RecoverySummary(total, completed, failed, targets.checksFailed)
    }

    /**
     * Narrows [groups] to the ones whose lemma or translations are missing from the downloaded DBs.
     *
     * A check that throws (unreadable or closed DB) is swallowed so recovery can still repair the
     * languages that are readable, but it is reported through [RecoveryTargets.checksFailed]: the
     * unchecked languages may still have gaps, so the pass must not claim it recovered everything.
     */
    private suspend fun groupsByDownloadedLemmaStatus(
        groups: Map<LemmaGroupKey, List<RecoverableSense>>,
    ): RecoveryTargets {
        var checksFailed = false
        val byLanguage = groups.entries.groupBy({ it.key.language }) { it.key to it.value }
        val recoverableByLanguage: Map<Language, Set<String>> = byLanguage.mapValues { (language, entries) ->
            if (!hasDownloadedDictionary(language)) return@mapValues emptySet()

            val normalizedLemmas = entries.map { it.first.normalizedLemma }.toSet()
            val lemmasMissingDictionary = downloadedLemmasNeedingRecoverySafe(language, normalizedLemmas)
                ?: run {
                    checksFailed = true
                    emptySet()
                }

            val remainingForTranslationCheck = normalizedLemmas - lemmasMissingDictionary
            val senseIdsByLemma = entries
                .filter { it.first.normalizedLemma in remainingForTranslationCheck }
                .associate { (key, items) -> key.normalizedLemma to items.map { it.senseId }.toSet() }

            val translationTargets = translationTargetsProvider(language)
                .filter { it != language }
                .distinctBy { it.code }

            val lemmasMissingTranslations = if (senseIdsByLemma.isEmpty() || translationTargets.isEmpty()) {
                emptySet()
            } else {
                downloadedSensesNeedingTranslationRecoverySafe(language, senseIdsByLemma, translationTargets)
                    ?: run {
                        checksFailed = true
                        emptySet()
                    }
            }
            lemmasMissingDictionary + lemmasMissingTranslations
        }

        return RecoveryTargets(
            groups = groups.filterKeys { key ->
                key.normalizedLemma in recoverableByLanguage[key.language].orEmpty()
            },
            checksFailed = checksFailed,
        )
    }

    /**
     * Fetches and resolves a specific set of [senses] — e.g. a curated list's senses that are
     * missing from the downloaded dictionary — invoking [onResolved] for each as soon as it is
     * ready. Groups by (language, lemma) so a multi-sense word is a single fetch, caps parallelism,
     * and reuses the shared fetch path (server stream + ingest, retry/backoff, cancellation).
     * Unlike [recoverAllInstalled] there is no "already present" gating: every requested sense is
     * fetched and resolved. Senses with a blank lemma are skipped (they cannot be fetched). Per-lemma
     * failures are reported through [onResolved] rather than thrown, so one broken word never aborts
     * the rest; only cancellation propagates.
     */
    suspend fun recoverSenses(
        senses: List<RecoverableSense>,
        onResolved: (RecoveredSenseResult) -> Unit,
    ): Unit = coroutineScope {
        val groups = senses
            .filter { it.lemma.isNotBlank() }
            .groupBy { LemmaGroupKey(it.language, it.lemma.trim().lowercase()) }
        groups.values.map { groupItems ->
            async {
                groupSemaphore.withPermit {
                    val language = groupItems.first().language
                    val lemma = groupItems.first().lemma.trim()
                    val senseIds = groupItems.map { it.senseId }.toSet()
                    try {
                        fetchLemmaIntoLocalDb(language, lemma)
                        val results = resolveSenses(language, lemma, senseIds)
                        senseIds.forEach { senseId ->
                            onResolved(RecoveredSenseResult(senseId, results[senseId], error = null))
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        AppLogger.warn(TAG, "Unable to recover senses for '$lemma' (${language.code})", e)
                        senseIds.forEach { senseId ->
                            onResolved(RecoveredSenseResult(senseId, result = null, error = e))
                        }
                    }
                }
            }
        }.awaitAll()
    }

    /**
     * Fetches [lemma] (with translations) into the local DBs through the shared recovery fetch
     * path — same [WordFetchManager] streaming, retry/backoff and cancellation handling used by the
     * background recovery — skipping the "is it already present" gating. Throws on a non-retryable
     * failure or cancellation.
     */
    private suspend fun fetchLemmaIntoLocalDb(language: Language, lemma: String) {
        val translationTargets = translationTargetsProvider(language)
            .filter { it != language }
            .distinctBy { it.code }
        fetchLemmaWithRetry(language, lemma, translationTargets)
    }

    private suspend fun recoverLemmaGroup(language: Language, items: List<RecoverableSense>) {
        if (items.isEmpty()) return

        val lemma = items.first().lemma.trim()
        val translationTargets = translationTargetsProvider(language)
            .filter { it != language }
            .distinctBy { it.code }
        fetchLemmaWithRetry(language, lemma, translationTargets)
    }

    private suspend fun fetchLemmaWithRetry(
        language: Language,
        lemma: String,
        translationTargets: List<Language>,
    ) {
        val delays = listOf(1.seconds, 3.seconds)
        var attempt = 0
        while (true) {
            try {
                fetchLemma(language, lemma, translationTargets)
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val classified = NetworkErrorClassifier.classify(e)
                val retryable = when (classified) {
                    is NetworkError.Offline,
                    is NetworkError.Timeout -> true
                    is NetworkError.ServerError -> classified.statusCode in 500..599 || classified.statusCode == 0
                    is NetworkError.InsufficientStorage,
                    is NetworkError.Unknown -> false
                }
                if (!retryable || attempt >= delays.size) throw e
                val retryDelay = delays[attempt]
                AppLogger.warn(
                    TAG,
                    "Retrying lemma recovery for '$lemma' (${language.code}) after ${retryDelay.inWholeSeconds}s",
                    e,
                )
                delay(retryDelay)
                attempt++
            }
        }
    }

    /** Returns null when the check itself failed, which is different from "nothing is missing". */
    private suspend fun downloadedLemmasNeedingRecoverySafe(
        language: Language,
        normalizedLemmas: Set<String>,
    ): Set<String>? {
        return try {
            downloadedLemmasNeedingRecovery(language, normalizedLemmas)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.warn(
                TAG,
                "Unable to check lemmas (${language.code})",
                e,
            )
            null
        }
    }

    /** Returns null when the check itself failed, which is different from "nothing is missing". */
    private suspend fun downloadedSensesNeedingTranslationRecoverySafe(
        language: Language,
        senseIdsByLemma: Map<String, Set<String>>,
        translationTargets: List<Language>,
    ): Set<String>? {
        return try {
            downloadedSensesNeedingTranslationRecovery(language, senseIdsByLemma, translationTargets)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.warn(
                TAG,
                "Unable to check translations (${language.code})",
                e,
            )
            null
        }
    }
}

/**
 * Result of a [LemmaRecovery.recoverAllInstalled] pass.
 *
 * Per-lemma failures are counted rather than thrown — a broken word must never block a completed
 * download — so the pass returns normally even when nothing could be fetched. [fullyRecovered] is
 * false when any lemma group failed, when a pre-flight "is it already installed?" check failed (the
 * pass then never learned what was missing, so [total] can be 0 while gaps remain), or when the pass
 * could not run at all. Callers that gate work on recovery having happened (such as the
 * pending-recovery marker) must keep it pending in that case.
 */
data class LemmaRecoveryOutcome(
    val total: Int,
    val failed: Int,
    val fullyRecovered: Boolean,
)

data class LemmaRecoveryProgress(
    val currentLemma: String?,
    val completed: Int,
    val total: Int,
    val failed: Int,
)

/**
 * Outcome of recovering a single sense via [LemmaRecovery.recoverSenses]: either a dictionary
 * [result] (which itself may carry a missing reason when the word was fetched but the sense was
 * not found) or, when the whole lemma fetch failed, the [error] that caused it. Exactly one of
 * the two is non-null.
 */
data class RecoveredSenseResult(
    val senseId: String,
    val result: DictionaryRepository.SenseLookupResult?,
    val error: Throwable?,
)

private data class LemmaGroupKey(
    val language: Language,
    val normalizedLemma: String,
)

private data class RecoverySummary(
    val total: Int,
    val completed: Int,
    val failed: Int,
    val checksFailed: Boolean,
)

/**
 * The lemma groups that still need fetching, plus whether any "is it already installed?" check
 * failed while narrowing them down. [checksFailed] means the group list is incomplete by unknown
 * amount, not that everything is present.
 */
private data class RecoveryTargets(
    val groups: Map<LemmaGroupKey, List<RecoverableSense>>,
    val checksFailed: Boolean,
)

private const val TAG = "LemmaRecovery"
private const val MAX_PARALLEL_RECOVERY_GROUPS = 4
