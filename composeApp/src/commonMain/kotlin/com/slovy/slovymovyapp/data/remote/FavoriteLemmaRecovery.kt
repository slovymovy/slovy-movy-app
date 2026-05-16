package com.slovy.slovymovyapp.data.remote

import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.favorites.Favorite
import com.slovy.slovymovyapp.data.favorites.FavoritesRepository
import com.slovy.slovymovyapp.logging.AppLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlin.time.Duration.Companion.seconds

class FavoriteLemmaRecovery internal constructor(
    private val favoritesProvider: suspend () -> List<Favorite>,
    private val hasDownloadedDictionary: (Language) -> Boolean,
    private val downloadedLemmasNeedingRecovery: suspend (Language, Set<String>) -> Set<String>,
    private val downloadedFavoritesNeedingTranslationRecovery:
    suspend (Language, Map<String, Set<String>>, List<Language>) -> Set<String>,
    private val translationTargetsProvider: suspend (Language) -> List<Language>,
    private val fetchLemma: suspend (Language, String, List<Language>) -> Unit,
) {
    constructor(
        favoritesRepository: FavoritesRepository,
        dataDbManager: DataDbManager,
        dictionaryRepository: DictionaryRepository,
        wordFetchManager: WordFetchManager,
    ) : this(
        favoritesProvider = { favoritesRepository.getAll() },
        hasDownloadedDictionary = { language -> dataDbManager.hasDictionary(language) },
        downloadedLemmasNeedingRecovery = { language, lemmas ->
            dataDbManager.downloadedLemmasNeedingRecovery(language, lemmas)
        },
        downloadedFavoritesNeedingTranslationRecovery = { language, senseIdsByLemma, targets ->
            dataDbManager.downloadedFavoritesNeedingTranslationRecovery(language, senseIdsByLemma, targets)
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

    suspend fun recoverAllInstalledFavorites(
        onProgress: (FavoriteRecoveryProgress) -> Unit = {},
    ) {
        try {
            recover(favoritesProvider(), onProgress)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.warn(TAG, "Unable to load favorites for recovery", e)
            // Recovery must never block a completed database download.
        }
    }

    private suspend fun recover(
        favorites: List<Favorite>,
        onProgress: (FavoriteRecoveryProgress) -> Unit,
    ) = coroutineScope {
        val groups = favorites
            .mapNotNull { favorite ->
                val lemma = favorite.lemma.trim()
                if (lemma.isBlank()) null else FavoriteLemmaGroupKey(favorite.language, lemma.lowercase()) to favorite
            }
            .groupBy({ it.first }, { it.second })
        val groupsToRecover = groupsByDownloadedLemmaStatus(groups)
        val total = groupsToRecover.size
        val progressMutex = Mutex()
        var completed = 0
        var failed = 0
        val activeLemmas = linkedMapOf<FavoriteLemmaGroupKey, String>()

        suspend fun markStarted(key: FavoriteLemmaGroupKey, lemma: String) = progressMutex.withLock {
            activeLemmas[key] = lemma
            onProgress(FavoriteRecoveryProgress(activeLemmas.values.firstOrNull(), completed, total, failed))
        }

        suspend fun markDone(key: FavoriteLemmaGroupKey, deltaFailed: Int) = progressMutex.withLock {
            completed++
            if (deltaFailed > 0) failed += deltaFailed
            activeLemmas.remove(key)
            onProgress(FavoriteRecoveryProgress(activeLemmas.values.firstOrNull(), completed, total, failed))
        }

        val semaphore = Semaphore(MAX_PARALLEL_RECOVERY_GROUPS)

        groupsToRecover.map { (key, groupFavorites) ->
            async {
                semaphore.withPermit {
                    var failedGroup = false
                    val lemma = groupFavorites.first().lemma.trim()
                    try {
                        markStarted(key, lemma)
                        recoverLemmaGroup(key.language, groupFavorites)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        failedGroup = true
                        AppLogger.warn(
                            TAG,
                            "Unable to recover favorite lemma '$lemma' (${key.language.code})",
                            e,
                        )
                        // Best-effort recovery: broken or unavailable remote fetches must not block downloads.
                    } finally {
                        markDone(key, deltaFailed = if (failedGroup) 1 else 0)
                    }
                }
            }
        }.awaitAll()
    }

    private suspend fun groupsByDownloadedLemmaStatus(
        groups: Map<FavoriteLemmaGroupKey, List<Favorite>>,
    ): Map<FavoriteLemmaGroupKey, List<Favorite>> {
        val byLanguage = groups.entries.groupBy({ it.key.language }) { it.key to it.value }
        val recoverableByLanguage: Map<Language, Set<String>> = byLanguage.mapValues { (language, entries) ->
            if (!hasDownloadedDictionary(language)) return@mapValues emptySet()

            val normalizedLemmas = entries.map { it.first.normalizedLemma }.toSet()
            val lemmasMissingDictionary = downloadedFavoriteLemmasNeedingRecovery(language, normalizedLemmas)

            val remainingForTranslationCheck = normalizedLemmas - lemmasMissingDictionary
            val senseIdsByLemma = entries
                .filter { it.first.normalizedLemma in remainingForTranslationCheck }
                .associate { (key, favs) -> key.normalizedLemma to favs.map { it.senseId }.toSet() }

            val translationTargets = translationTargetsProvider(language)
                .filter { it != language }
                .distinctBy { it.code }

            val lemmasMissingTranslations = if (senseIdsByLemma.isEmpty() || translationTargets.isEmpty()) {
                emptySet()
            } else {
                downloadedFavoritesNeedingTranslationRecoverySafe(language, senseIdsByLemma, translationTargets)
            }
            lemmasMissingDictionary + lemmasMissingTranslations
        }

        return groups.filterKeys { key ->
            key.normalizedLemma in recoverableByLanguage[key.language].orEmpty()
        }
    }

    private suspend fun recoverLemmaGroup(language: Language, favorites: List<Favorite>) {
        if (favorites.isEmpty()) return

        val lemma = favorites.first().lemma.trim()
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
                    "Retrying favorite lemma recovery for '$lemma' (${language.code}) after ${retryDelay.inWholeSeconds}s",
                    e,
                )
                delay(retryDelay)
                attempt++
            }
        }
    }

    private suspend fun downloadedFavoriteLemmasNeedingRecovery(
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
                "Unable to check favorite lemmas (${language.code})",
                e,
            )
            emptySet()
        }
    }

    private suspend fun downloadedFavoritesNeedingTranslationRecoverySafe(
        language: Language,
        senseIdsByLemma: Map<String, Set<String>>,
        translationTargets: List<Language>,
    ): Set<String> {
        return try {
            downloadedFavoritesNeedingTranslationRecovery(language, senseIdsByLemma, translationTargets)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.warn(
                TAG,
                "Unable to check favorite translations (${language.code})",
                e,
            )
            emptySet()
        }
    }
}

data class FavoriteRecoveryProgress(
    val currentLemma: String?,
    val completed: Int,
    val total: Int,
    val failed: Int,
)

private data class FavoriteLemmaGroupKey(
    val language: Language,
    val normalizedLemma: String,
)

private const val TAG = "FavoriteLemmaRecovery"
private const val MAX_PARALLEL_RECOVERY_GROUPS = 4
