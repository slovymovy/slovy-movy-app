package com.slovy.slovymovyapp.ui.listdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slovy.slovymovyapp.analytics.Analytics
import com.slovy.slovymovyapp.analytics.AnalyticsEvent
import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.favorites.FavoritesRepository
import com.slovy.slovymovyapp.data.favorites.NewFavorite
import com.slovy.slovymovyapp.ui.favorites.toFavoriteSenseLoadError
import com.slovy.slovymovyapp.data.lists.ListsService
import com.slovy.slovymovyapp.data.lists.WordList
import com.slovy.slovymovyapp.data.lists.WordListSense
import com.slovy.slovymovyapp.data.remote.DictionaryRepository
import com.slovy.slovymovyapp.data.remote.LanguageCardResponseSense
import com.slovy.slovymovyapp.data.remote.LearnerLevel
import com.slovy.slovymovyapp.data.remote.LemmaRecovery
import com.slovy.slovymovyapp.data.remote.PartOfSpeech
import com.slovy.slovymovyapp.data.remote.RelatedWord
import com.slovy.slovymovyapp.data.remote.SenseFrequency
import com.slovy.slovymovyapp.i18n.UiText
import com.slovy.slovymovyapp.logging.AppLogger
import com.slovy.slovymovyapp.speech.RowAudioActions
import com.slovy.slovymovyapp.speech.RowAudioController
import com.slovy.slovymovyapp.speech.SpeechPlayer
import com.slovy.slovymovyapp.speech.VoiceFilterHelper
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import slovymovyapp.composeapp.generated.resources.Res
import slovymovyapp.composeapp.generated.resources.favorites_error_meaning_not_found
import com.slovy.slovymovyapp.ui.SensePrefetcher
import com.slovy.slovymovyapp.ui.PrefetchableSenseItem
import com.slovy.slovymovyapp.ui.favorites.toFavoriteSenseLoadError

data class ListWordItem(
    val senseId: String,
    val lemma: String,
    val definition: String? = null,
    val learnerLevel: LearnerLevel? = null,
    val frequency: SenseFrequency? = null,
    override val sense: LanguageCardResponseSense? = null,
    val relatedWords: Map<String, RelatedWord> = emptyMap(),
    val pos: PartOfSpeech? = null,
    val expanded: Boolean = false,
    override val loading: Boolean = false,
    // A server translation repair is in flight for this row. The row keeps rendering its local
    // content meanwhile; only the translation area shows a progress placeholder.
    val translationLoading: Boolean = false,
    override val error: UiText? = null,
    val isFavorited: Boolean = false,
) : PrefetchableSenseItem

data class ListDetailUiState(
    val items: List<ListWordItem> = emptyList(),
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val favoriteLemmas: Set<String> = emptySet(),
    val bulkActionInProgress: Boolean = false,
) {
    val inMyWordsCount: Int get() = items.count { it.isFavorited }
    val allInMyWords: Boolean get() = items.isNotEmpty() && items.all { it.isFavorited }
}

class ListDetailViewModel(
    val listId: String,
    val language: Language,
    private val repository: DictionaryRepository,
    private val favoritesRepository: FavoritesRepository,
    private val listsService: ListsService,
    private val lemmaRecovery: LemmaRecovery,
    speechPlayer: SpeechPlayer,
    voiceFilterHelper: VoiceFilterHelper,
    private val onFavoriteChanged: (added: Boolean) -> Unit,
) : ViewModel() {
    var list by mutableStateOf<WordList?>(null)
        private set
    var state by mutableStateOf(ListDetailUiState())
        private set
    val scrollState = LazyListState()
    private var loadJob: Job? = null
    private val prefetcher = SensePrefetcher(viewModelScope, ::loadSense)

    val rowAudio = RowAudioController(
        speechPlayer = speechPlayer,
        voiceFilterHelper = voiceFilterHelper,
        scope = viewModelScope,
        analyticsSource = "list_detail",
    )

    val rowAudioActions = RowAudioActions(
        onToggle = ::toggleAudio,
        onOpenVoiceSettings = rowAudio::openVoiceSettings,
        onDismissVoiceSetup = rowAudio::dismissVoiceSetup,
        onDismissVoiceSetupAndPlay = rowAudio::dismissVoiceSetupAndPlay,
    )

    fun toggleAudio(senseId: String) {
        val item = findItem(senseId) ?: return
        rowAudio.toggle(senseId, item.lemma, language)
    }

    override fun onCleared() {
        super.onCleared()
        rowAudio.dispose()
    }

    // SenseIds added by "Add all" in this session; "Remove all" only removes these so
    // favorites that existed before the bulk add survive. When empty (e.g. the screen
    // was opened with everything already favorited), "Remove all" removes all items.
    private var sessionBulkAddedSenseIds: Set<String> = emptySet()

    // Default translation targets resolved at load(); used to detect senses that resolved
    // locally but lack a requested translation language (gap in the downloaded translation DB).
    private var translationTargets: List<Language> = emptyList()

    // SenseIds already handed to translation repair this session, so a fetch that yields no
    // translations (or fails) is not retried on every prefetch/expand of the same row.
    // Parallelism is capped inside LemmaRecovery, shared across all recovery callers.
    private val translationRecoveryAttempted = mutableSetOf<String>()

    // Lemmas whose repair completed without filling the gap (fetch failed or the server has no
    // translations for them): further senses of the same lemma skip the pointless refetch.
    // Successful repairs need no lemma-level dedup — concurrent same-lemma fetches are shared
    // by WordFetchManager, and once ingested the next repair resolves locally without a server
    // round trip.
    private val translationRepairFailedLemmas = mutableSetOf<String>()

    private fun lemmaRepairKey(lemma: String) = lemma.trim().lowercase()

    init {
        load()
    }

    fun retry() {
        load()
    }

    private fun load() {
        loadJob?.cancel()
        state = state.copy(isLoading = true, loadFailed = false)
        loadJob = viewModelScope.launch {
            // Lists are served from the DB; fall back to a server sync when the id is
            // not stored yet (e.g. the stored feed predates this list).
            val resolvedList = listsService.getList(language, listId)
                ?: run {
                    listsService.sync(language)
                    listsService.getList(language, listId)
                }
            if (resolvedList == null) {
                state = state.copy(isLoading = false, loadFailed = true)
                return@launch
            }
            list = resolvedList
            translationTargets = repository.defaultTranslationTargets(language)
                .filter { it != language }
                .distinctBy { it.code }
            val resolved = repository.getListSenses(language, resolvedList.senseIds)
                .associateBy { it.senseId }
            val allFavorites = favoritesRepository.getAll().filter { it.language == language }
            val favoritedIds = allFavorites.map { it.senseId }.toSet()
            val favoriteLemmas = allFavorites.map { it.lemma }.toSet()
            // Build an item for every list sense in server order. Senses present in the
            // local dictionary render immediately; senses missing from it become loading
            // placeholders carrying the bundle lemma so they can be fetched + favorited.
            // A sense missing locally with a blank lemma (legacy senseIds-only bundle or a
            // pre-lemma cached row) is skipped: it can be neither fetched nor turned into a
            // usable favorite (all fetch/recovery/intake paths are keyed by lemma), so it must
            // not reach "Add all". This matches the pre-lemma behavior of dropping such senses.
            val items = resolvedList.senses.mapNotNull { listSense ->
                val sense = resolved[listSense.senseId]
                when {
                    sense != null -> {
                        // Seed from cache so already-loaded senses render their translation
                        // immediately, mirroring FavoritesViewModel.buildSenseItem.
                        val cached = repository.getCachedSense(sense.senseId)
                        ListWordItem(
                            senseId = sense.senseId,
                            lemma = sense.lemma,
                            definition = sense.definition,
                            learnerLevel = sense.learnerLevel,
                            frequency = sense.frequency,
                            sense = cached?.sense,
                            relatedWords = cached?.relatedWords ?: emptyMap(),
                            pos = cached?.pos,
                            isFavorited = sense.senseId in favoritedIds,
                        )
                    }

                    listSense.lemma.isNotBlank() -> ListWordItem(
                        senseId = listSense.senseId,
                        lemma = listSense.lemma,
                        isFavorited = listSense.senseId in favoritedIds,
                        loading = true,
                    )

                    else -> null
                }
            }
            state = ListDetailUiState(
                items = items,
                isLoading = false,
                favoriteLemmas = favoriteLemmas,
            )
            // Prefetch the first screenful so translations show without expanding,
            // like the favorites list. The rest is prefetched on scroll.
            prefetcher.prefetchHead(items)
            // Fetch the senses missing from the local dictionary from the server in the
            // background; resolved items are already on screen, so this never blocks the UI.
            // Only senses with a usable lemma are fetchable.
            val missing = resolvedList.senses.filter { it.senseId !in resolved && it.lemma.isNotBlank() }
            // Senses seeded from the cache may lack a translation language (an earlier repair
            // failed or never ran); hand them to the same recovery pass so reopening the screen
            // retries the fetch.
            val translationGaps = items.mapNotNull { item ->
                val sense = item.sense ?: return@mapNotNull null
                if (senseMissingTranslations(sense) &&
                    lemmaRepairKey(item.lemma) !in translationRepairFailedLemmas &&
                    translationRecoveryAttempted.add(item.senseId)
                ) {
                    WordListSense(senseId = item.senseId, lemma = item.lemma, language = language)
                } else null
            }
            if (missing.isNotEmpty() || translationGaps.isNotEmpty()) {
                fetchMissingSenses(missing, translationGaps)
            }
        }
    }

    // True when the sense resolved locally but the downloaded/local translation DBs have no data
    // for one of the requested target languages — the case the server fetch can repair.
    private fun senseMissingTranslations(sense: LanguageCardResponseSense): Boolean =
        translationTargets.any { it !in sense.translations && it !in sense.targetLangDefinitions }

    private suspend fun fetchMissingSenses(
        missing: List<WordListSense>,
        translationGaps: List<WordListSense>,
    ) {
        // Hand the missing senses to LemmaRecovery, which fetches (server stream + ingest, with
        // retry/backoff, parallelism cap and cancellation) and resolves each one, calling back as
        // results arrive. WordListSense is itself a RecoverableSense, so no mapping is needed; the
        // screen only maps each outcome to its per-item UI state.
        val gapLemmaKeysBySenseId = translationGaps.associate { it.senseId to lemmaRepairKey(it.lemma) }
        // Show progress on the rows being repaired. Both call sites route through here, so the
        // flag is set once the fetch is actually enqueued and cleared per sense as results land.
        gapLemmaKeysBySenseId.keys.forEach { senseId ->
            updateItem(senseId) { it.copy(translationLoading = true) }
        }
        lemmaRecovery.recoverSenses(missing + translationGaps) { recovered ->
            val lookup = recovered.result
            val senseWithPos = lookup?.sense
            val gapLemmaKey = gapLemmaKeysBySenseId[recovered.senseId]
            if (gapLemmaKey != null) {
                // Translation repair: the row already renders a locally resolved sense, so a
                // failed or still-translation-less fetch keeps the current content instead of
                // surfacing an error. A repair that didn't fill the gap marks the lemma so
                // other senses of the same word don't refetch what the server cannot provide.
                if (senseWithPos == null || senseMissingTranslations(senseWithPos.sense)) {
                    translationRepairFailedLemmas += gapLemmaKey
                    // The row silently keeps its untranslated content, so this log is the only
                    // trace that a repair ran and could not fill the gap.
                    AppLogger.debug(TAG, recovered.error) {
                        "Translation repair did not fill the gap for '$gapLemmaKey' " +
                            "(${language.code}, sense=${recovered.senseId}); " +
                            "further senses of this lemma will not refetch"
                    }
                }
                updateItem(recovered.senseId) {
                    if (senseWithPos == null) {
                        it.copy(translationLoading = false)
                    } else {
                        it.copy(
                            sense = senseWithPos.sense,
                            relatedWords = senseWithPos.relatedWords.ifEmpty { it.relatedWords },
                            pos = senseWithPos.pos,
                            translationLoading = false,
                        )
                    }
                }
            } else {
                val error = when {
                    senseWithPos != null -> null
                    lookup?.missingReason != null -> lookup.missingReason.toFavoriteSenseLoadError(language)
                    else -> recovered.error?.message?.let(UiText::Plain)
                        ?: UiText.Resource(Res.string.favorites_error_meaning_not_found)
                }
                updateItem(recovered.senseId) {
                    it.copy(
                        sense = senseWithPos?.sense,
                        relatedWords = senseWithPos?.relatedWords ?: it.relatedWords,
                        pos = senseWithPos?.pos,
                        loading = false,
                        error = error,
                    )
                }
            }
        }
    }

    fun prefetchVisibleRange(items: List<ListWordItem>, range: IntRange) {
        prefetcher.prefetchVisibleRange(items, range)
    }

    private fun updateItem(senseId: String, transform: (ListWordItem) -> ListWordItem) {
        state = state.copy(items = state.items.map { if (it.senseId == senseId) transform(it) else it })
    }

    private fun findItem(senseId: String): ListWordItem? = state.items.find { it.senseId == senseId }

    fun toggleSense(senseId: String) {
        val item = findItem(senseId) ?: return
        val wasExpanded = item.expanded
        val shouldLoad = !wasExpanded && item.sense == null && !item.loading && item.error == null
        updateItem(senseId) { it.copy(expanded = !wasExpanded, error = null) }
        if (shouldLoad) {
            viewModelScope.launch { loadSense(item) }
        }
    }

    private suspend fun loadSense(item: ListWordItem) {
        if (item.sense != null || item.loading) return
        updateItem(item.senseId) { it.copy(loading = true, error = null) }
        try {
            val loaded = repository.getSenses(language, item.lemma, setOf(item.senseId))
            val result = loaded[item.senseId]
            val senseWithPos = result?.sense
            val error = if (senseWithPos == null) {
                result?.missingReason?.toFavoriteSenseLoadError(language)
                    ?: UiText.Resource(Res.string.favorites_error_meaning_not_found)
            } else null
            updateItem(item.senseId) {
                it.copy(
                    sense = senseWithPos?.sense,
                    relatedWords = senseWithPos?.relatedWords ?: it.relatedWords,
                    pos = senseWithPos?.pos,
                    loading = false,
                    error = error,
                )
            }
            // The row is already rendering the local sense; if a requested translation language
            // is absent from the local DBs, fetch it from the server (same path the word-detail
            // screen uses) and swap the row content in when it arrives.
            if (senseWithPos != null &&
                senseMissingTranslations(senseWithPos.sense) &&
                lemmaRepairKey(item.lemma) !in translationRepairFailedLemmas &&
                translationRecoveryAttempted.add(item.senseId)
            ) {
                fetchMissingSenses(
                    missing = emptyList(),
                    translationGaps = listOf(
                        WordListSense(senseId = item.senseId, lemma = item.lemma, language = language)
                    ),
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            updateItem(item.senseId) {
                it.copy(
                    loading = false,
                    error = e.message?.let(UiText::Plain)
                        ?: UiText.Resource(Res.string.favorites_error_meaning_not_found),
                )
            }
        }
    }

    fun toggleFavorite(senseId: String) {
        val item = findItem(senseId) ?: return
        viewModelScope.launch {
            val added = if (item.isFavorited) {
                favoritesRepository.remove(senseId, language)
                updateItem(senseId) { it.copy(isFavorited = false) }
                state = state.copy(favoriteLemmas = state.favoriteLemmas - item.lemma)
                false
            } else {
                favoritesRepository.add(senseId, language, item.lemma)
                updateItem(senseId) { it.copy(isFavorited = true) }
                state = state.copy(favoriteLemmas = state.favoriteLemmas + item.lemma)
                true
            }
            onFavoriteChanged(added)
        }
    }

    fun reloadFavorites() {
        viewModelScope.launch {
            refreshFavoriteFlags()
        }
    }

    fun addAllToMyWords() {
        if (state.isLoading || state.bulkActionInProgress) return
        val toAdd = state.items.filter { !it.isFavorited }
        if (toAdd.isEmpty()) return
        Analytics.logEvent(
            AnalyticsEvent.LIST_ADD_ALL,
            mapOf("lang" to language.code, "list_id" to listId)
        )
        state = state.copy(bulkActionInProgress = true)
        viewModelScope.launch {
            favoritesRepository.addAll(language, toAdd.map { NewFavorite(senseId = it.senseId, lemma = it.lemma) })
            sessionBulkAddedSenseIds = sessionBulkAddedSenseIds + toAdd.map { it.senseId }
            refreshFavoriteFlags()
            state = state.copy(bulkActionInProgress = false)
            onFavoriteChanged(true)
        }
    }

    fun removeAllFromMyWords() {
        if (state.isLoading || state.bulkActionInProgress) return
        val favorited = state.items.filter { it.isFavorited }
        val targets = if (sessionBulkAddedSenseIds.isNotEmpty()) {
            favorited.filter { it.senseId in sessionBulkAddedSenseIds }
        } else {
            favorited
        }
        if (targets.isEmpty()) return
        Analytics.logEvent(
            AnalyticsEvent.LIST_REMOVE_ALL,
            mapOf("lang" to language.code, "list_id" to listId)
        )
        state = state.copy(bulkActionInProgress = true)
        viewModelScope.launch {
            favoritesRepository.removeAll(targets.map { it.senseId }, language)
            sessionBulkAddedSenseIds = emptySet()
            refreshFavoriteFlags()
            state = state.copy(bulkActionInProgress = false)
            onFavoriteChanged(false)
        }
    }

    private suspend fun refreshFavoriteFlags() {
        val allFavorites = favoritesRepository.getAll().filter { it.language == language }
        val favoritedIds = allFavorites.map { it.senseId }.toSet()
        val favoriteLemmas = allFavorites.map { it.lemma }.toSet()
        state = state.copy(
            items = state.items.map { it.copy(isFavorited = it.senseId in favoritedIds) },
            favoriteLemmas = favoriteLemmas,
        )
    }

    private companion object {
        private const val TAG = "ListDetailViewModel"
    }
}


