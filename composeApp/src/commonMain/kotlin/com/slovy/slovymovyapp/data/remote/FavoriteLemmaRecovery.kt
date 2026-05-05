package com.slovy.slovymovyapp.data.remote

import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.favorites.Favorite
import com.slovy.slovymovyapp.data.favorites.FavoritesRepository
import com.slovy.slovymovyapp.logging.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

class FavoriteLemmaRecovery internal constructor(
    private val favoritesProvider: suspend () -> List<Favorite>,
    private val hasDownloadedDictionary: (Language) -> Boolean,
    private val downloadedLemmasNeedingRecovery: suspend (Language, Set<String>) -> Set<String>,
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
        translationTargetsProvider = { language -> dictionaryRepository.defaultTranslationTargets(language) },
        fetchLemma = { language, lemma, translationTargets ->
            wordFetchManager.getWord(
                language = language,
                lemma = lemma,
                translationTargets = translationTargets,
                pushToRepo = true,
            ).first { result ->
                !result.isWordLoading && !result.isTranslationLoading
            }
        },
    )

    suspend fun recoverAllInstalledFavorites() {
        try {
            recover(favoritesProvider())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.warn(TAG, "Unable to load favorites for recovery", e)
            // Recovery must never block a completed database download.
        }
    }

    private suspend fun recover(favorites: List<Favorite>) = coroutineScope {
        val groups = favorites
            .mapNotNull { favorite ->
                val lemma = favorite.lemma.trim()
                if (lemma.isBlank()) null else FavoriteLemmaGroupKey(favorite.language, lemma.lowercase()) to favorite
            }
            .groupBy({ it.first }, { it.second })
        val groupsToRecover = groupsByDownloadedLemmaStatus(groups)
        val semaphore = Semaphore(MAX_PARALLEL_RECOVERY_GROUPS)

        groupsToRecover.map { (key, groupFavorites) ->
            async {
                semaphore.withPermit {
                    try {
                        recoverLemmaGroup(key.language, groupFavorites)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        AppLogger.warn(
                            TAG,
                            "Unable to recover favorite lemma '${groupFavorites.firstOrNull()?.lemma}' (${key.language.code})",
                            e,
                        )
                        // Best-effort recovery: broken or unavailable remote fetches must not block downloads.
                    }
                }
            }
        }.awaitAll()
    }

    private suspend fun groupsByDownloadedLemmaStatus(
        groups: Map<FavoriteLemmaGroupKey, List<Favorite>>,
    ): Map<FavoriteLemmaGroupKey, List<Favorite>> {
        val recoverableByLanguage = groups.keys
            .groupBy { it.language }
            .mapValues { (language, keys) ->
                if (!hasDownloadedDictionary(language)) {
                    emptySet()
                } else {
                    downloadedFavoriteLemmasNeedingRecovery(
                        language = language,
                        normalizedLemmas = keys.map { it.normalizedLemma }.toSet(),
                    )
                }
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
        fetchLemma(language, lemma, translationTargets)
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
}

private data class FavoriteLemmaGroupKey(
    val language: Language,
    val normalizedLemma: String,
)

private const val TAG = "FavoriteLemmaRecovery"
private const val MAX_PARALLEL_RECOVERY_GROUPS = 4

private suspend fun DataDbManager.downloadedLemmasNeedingRecovery(
    language: Language,
    normalizedLemmas: Set<String>,
): Set<String> = withContext(Dispatchers.IO) {
    if (normalizedLemmas.isEmpty()) return@withContext emptySet()
    if (!hasDictionary(language)) return@withContext emptySet()
    val queries = openDictionaryReadOnly(language).dictionaryQueries
    val rowsByLemma = normalizedLemmas.chunked(999)
        .flatMap { chunk -> queries.selectLemmasByWords(language.code, chunk).executeAsList() }
        .groupBy { it.lemma.lowercase() }

    val missingLemmas = normalizedLemmas - rowsByLemma.keys
    val onlineOnlyLemmas = rowsByLemma
        .filterValues { rows -> rows.isNotEmpty() && rows.all { it.online_only } }
        .keys

    missingLemmas + onlineOnlyLemmas
}
