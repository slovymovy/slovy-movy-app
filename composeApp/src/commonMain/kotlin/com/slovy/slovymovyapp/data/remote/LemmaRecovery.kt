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
) {
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
    )

    suspend fun recoverAllInstalled(
        onProgress: (LemmaRecoveryProgress) -> Unit = {},
    ) {
        PerformanceMonitoring.startTrace("lemma_recovery").useWithResult {
            try {
                val items = itemsProvider()
                putMetric("items", items.size.toLong())
                putMetric("languages", items.map { it.language.code }.distinct().size.toLong())
                val summary = recover(items, onProgress)
                putMetric("groups_total", summary.total.toLong())
                putMetric("groups_completed", summary.completed.toLong())
                putMetric("groups_failed", summary.failed.toLong())
                markResult(when {
                    summary.total == 0 -> "nothing_to_recover"
                    summary.failed > 0 -> "partial_failure"
                    else -> "success"
                })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                markResult("failed")
                AppLogger.warn(TAG, "Unable to load items for recovery", e)
                // Recovery must never block a completed database download.
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
        val groupsToRecover = groupsByDownloadedLemmaStatus(groups)
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

        val semaphore = Semaphore(MAX_PARALLEL_RECOVERY_GROUPS)

        groupsToRecover.map { (key, groupItems) ->
            async {
                semaphore.withPermit {
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

        RecoverySummary(total, completed, failed)
    }

    private suspend fun groupsByDownloadedLemmaStatus(
        groups: Map<LemmaGroupKey, List<RecoverableSense>>,
    ): Map<LemmaGroupKey, List<RecoverableSense>> {
        val byLanguage = groups.entries.groupBy({ it.key.language }) { it.key to it.value }
        val recoverableByLanguage: Map<Language, Set<String>> = byLanguage.mapValues { (language, entries) ->
            if (!hasDownloadedDictionary(language)) return@mapValues emptySet()

            val normalizedLemmas = entries.map { it.first.normalizedLemma }.toSet()
            val lemmasMissingDictionary = downloadedLemmasNeedingRecoverySafe(language, normalizedLemmas)

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
            }
            lemmasMissingDictionary + lemmasMissingTranslations
        }

        return groups.filterKeys { key ->
            key.normalizedLemma in recoverableByLanguage[key.language].orEmpty()
        }
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

    private suspend fun downloadedLemmasNeedingRecoverySafe(
        language: Language,
        normalizedLemmas: Set<String>,
    ): Set<String> {
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
            emptySet()
        }
    }

    private suspend fun downloadedSensesNeedingTranslationRecoverySafe(
        language: Language,
        senseIdsByLemma: Map<String, Set<String>>,
        translationTargets: List<Language>,
    ): Set<String> {
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
            emptySet()
        }
    }
}

data class LemmaRecoveryProgress(
    val currentLemma: String?,
    val completed: Int,
    val total: Int,
    val failed: Int,
)

private data class LemmaGroupKey(
    val language: Language,
    val normalizedLemma: String,
)

private data class RecoverySummary(
    val total: Int,
    val completed: Int,
    val failed: Int,
)

private const val TAG = "LemmaRecovery"
private const val MAX_PARALLEL_RECOVERY_GROUPS = 4
