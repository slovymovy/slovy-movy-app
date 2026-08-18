package com.slovy.slovymovyapp.ui.favorites

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slovy.slovymovyapp.analytics.Analytics
import com.slovy.slovymovyapp.analytics.AnalyticsEvent
import com.slovy.slovymovyapp.analytics.PerformanceMonitoring
import com.slovy.slovymovyapp.analytics.putAttributes
import com.slovy.slovymovyapp.analytics.useWithResult
import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.favorites.Favorite
import com.slovy.slovymovyapp.data.favorites.FavoritesRepository.Companion.normalizeLemma
import com.slovy.slovymovyapp.data.favorites.FavoritesRepository
import com.slovy.slovymovyapp.data.remote.*
import com.slovy.slovymovyapp.data.settings.Setting
import com.slovy.slovymovyapp.data.settings.SettingsRepository
import com.slovy.slovymovyapp.i18n.ShortDuration
import com.slovy.slovymovyapp.i18n.UiText
import com.slovy.slovymovyapp.speech.RowAudioActions
import com.slovy.slovymovyapp.speech.RowAudioController
import com.slovy.slovymovyapp.speech.SpeechPlayer
import com.slovy.slovymovyapp.speech.VoiceFilterHelper
import com.slovy.slovymovyapp.util.roundUpToWholeMinutes
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import slovymovyapp.composeapp.generated.resources.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.Uuid
import com.slovy.slovymovyapp.ui.PrefetchableSenseItem
import com.slovy.slovymovyapp.ui.SensePrefetcher

data class FavoriteSenseItem(
    val senseId: String,
    val targetLang: Language,
    val lemma: String,
    val createdAt: Long,
    override val sense: LanguageCardResponseSense? = null,
    val relatedWords: Map<String, RelatedWord> = emptyMap(),
    val pos: PartOfSpeech? = null,
    val expanded: Boolean = false,
    override val loading: Boolean = false,
    override val error: UiText? = null
) : PrefetchableSenseItem

data class FavoritesStudyUiState(
    val language: Language,
    val dueCount: Int,
    val delayedDueCardCount: Int = 0,
) {
    val estimatedMinutes: Int = ((dueCount + 3) / 4).coerceAtLeast(1)
}

data class FavoritesStudyDoneUiState(
    val language: Language,
    val nextReviewLabel: UiText,
    val nextReviewAccessibilityValue: UiText,
    val action: FavoritesStudyDoneAction?,
    val nextReviewAtEpochMs: Long,
    val delayedDueCardCount: Int = 0,
) {
    val canContinueNow: Boolean get() = action != null
}

enum class FavoritesStudyDoneAction {
    REVIEW_MORE,
    STUDY_NEW,
}

data class FavoriteLanguageReviewUiState(
    val dueCount: Int = 0,
    val activeCardCount: Int = 0,
    val delayedDueLemmaCount: Int = 0,
    val delayedDueCardCount: Int = 0,
    val pendingFavoriteLemmaCount: Int = 0,
    val canStudyPendingFavoritesNow: Boolean = true,
    val nextReviewAtEpochMs: Long? = null,
)

sealed interface FavoritesUiState {
    data object Loading : FavoritesUiState

    data class Content(
        val senses: List<FavoriteSenseItem>,
        val query: String = "",
        val hasAnyFavorites: Boolean = false,
        val favoriteLemmas: Set<String> = emptySet(),
        val availableLanguages: List<Language> = emptyList(),
        val selectedLanguage: Language? = null,
        val isLanguageDropdownExpanded: Boolean = false,
        val study: FavoritesStudyUiState? = null,
        val studyDone: FavoritesStudyDoneUiState? = null,
        val reviewDueCount: Int = 0,
        val scrollToTop: Boolean = false
    ) : FavoritesUiState {
        val showNoResults: Boolean get() = senses.isEmpty() && query.isNotBlank()
        val showLanguagePicker: Boolean get() = availableLanguages.size > 1
    }
}

internal fun FavoritesUiState.Content.withoutCachedFavoriteDetails(): FavoritesUiState.Content =
    copy(senses = senses.map { it.withoutCachedDetails() })

private fun FavoriteSenseItem.withoutCachedDetails(): FavoriteSenseItem =
    copy(
        sense = null,
        relatedWords = emptyMap(),
        pos = null,
        expanded = false,
        loading = false,
        error = null,
    )

class FavoritesViewModel(
    private val favoritesRepository: FavoritesRepository,
    private val dictionaryRepository: DictionaryRepository,
    private val settingsRepository: SettingsRepository,
    speechPlayer: SpeechPlayer,
    voiceFilterHelper: VoiceFilterHelper,
    private val clock: Clock,
) : ViewModel() {

    var state by mutableStateOf<FavoritesUiState>(FavoritesUiState.Loading)
        private set

    val scrollState = LazyListState()
    val emptyStateScrollState = ScrollState(0)
    val snackbarHostState = SnackbarHostState()

    val rowAudio = RowAudioController(
        speechPlayer = speechPlayer,
        voiceFilterHelper = voiceFilterHelper,
        scope = viewModelScope,
        analyticsSource = "favorites",
    )

    val rowAudioActions = RowAudioActions(
        onToggle = ::toggleAudio,
        onOpenVoiceSettings = rowAudio::openVoiceSettings,
        onDismissVoiceSetup = rowAudio::dismissVoiceSetup,
        onDismissVoiceSetupAndPlay = rowAudio::dismissVoiceSetupAndPlay,
    )

    fun toggleAudio(senseId: String) {
        val content = state as? FavoritesUiState.Content ?: return
        val item = content.senses.firstOrNull { it.senseId == senseId } ?: return
        rowAudio.toggle(senseId, item.lemma, item.targetLang)
    }

    override fun onCleared() {
        super.onCleared()
        rowAudio.dispose()
    }

    private var pendingScrollToTop: Boolean = false
    private var savedFavoritesLanguage: Language? = null
    private var reviewStateByLanguage: Map<Language, FavoriteLanguageReviewUiState> = emptyMap()

    /** Called from outside (e.g. word detail) when a favorite was just added. */
    fun requestScrollToTop() {
        pendingScrollToTop = true
    }

    /** Called after dictionary/local DB changes when loaded favorite details may be stale. */
    fun dropCachedFavoriteDetails() {
        val content = state as? FavoritesUiState.Content ?: return
        state = content.withoutCachedFavoriteDetails()
    }

    fun updateReviewDueCounts(updatedDueCountByLanguage: Map<Language, Int>) {
        val updatedReviewStateByLanguage = updatedDueCountByLanguage.mapValues { (language, dueCount) ->
            reviewStateByLanguage[language]?.copy(dueCount = dueCount)
                ?: FavoriteLanguageReviewUiState(dueCount = dueCount)
        }
        updateReviewState(updatedReviewStateByLanguage)
    }

    fun updateReviewState(updatedReviewStateByLanguage: Map<Language, FavoriteLanguageReviewUiState>) {
        reviewStateByLanguage = updatedReviewStateByLanguage
        val content = state as? FavoritesUiState.Content ?: return
        state = content.withReviewState()
    }

    private val queryFlow = MutableStateFlow(QueryState("", Uuid.random()))

    private data class QueryState(
        val query: String,
        val force: Uuid,
    )

    companion object {
        private const val QUERY_DEBOUNCE_MS = 200L
    }

    private val prefetcher = SensePrefetcher(viewModelScope, ::loadSense)

    private var started = false

    /**
     * Starts the debounced query collector. Call once after construction from the UI layer.
     * Tests omit this so viewModelScope stays empty and there's no async DB work racing teardown.
     */
    fun start() {
        if (started) return
        started = true
        viewModelScope.launch {
            // Restore saved language before observing queryFlow so the first load uses the right language.
            val savedCode = settingsRepository.getById(Setting.Name.FAVORITES_LANGUAGE)
                ?.value?.jsonPrimitive?.content
            savedFavoritesLanguage = savedCode?.let { Language.fromCodeOrNull(it) }

            @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
            queryFlow
                .debounce(QUERY_DEBOUNCE_MS.milliseconds)
                .flatMapLatest { queryState ->
                    val snapshot = state as? FavoritesUiState.Content
                    flow {
                        val trimmedQuery = queryState.query.trim()
                        val newState = if (trimmedQuery.isEmpty()) {
                            computeFavoritesState(
                                query = queryState.query,
                                currentContent = snapshot,
                            )
                        } else {
                            PerformanceMonitoring.startTrace("favorites_search").useWithResult {
                                putMetric("query_length", trimmedQuery.length.toLong())
                                computeFavoritesState(
                                    query = queryState.query,
                                    currentContent = snapshot,
                                ).also { content ->
                                    putAttributes(
                                        mapOf(
                                            "lang" to (content.selectedLanguage?.code ?: "any"),
                                            "has_results" to content.senses.isNotEmpty(),
                                        ),
                                    )
                                    putMetric("result_count", content.senses.size.toLong())
                                    putMetric("available_languages", content.availableLanguages.size.toLong())
                                }
                            }
                        }
                        emit(newState)
                    }
                        .flowOn(Dispatchers.Default)
                }
                .collect { newState ->
                    val query = newState.query.trim()
                    if (query.isNotEmpty()) {
                        Analytics.logEvent(
                            AnalyticsEvent.FAVORITES_SEARCH_QUERY,
                            mapOf(
                                "lang" to (newState.selectedLanguage?.code ?: "any"),
                                "query_length" to query.length.toLong(),
                                "result_count" to newState.senses.size.toLong(),
                                "has_results" to newState.senses.isNotEmpty().toString(),
                            ),
                        )
                    }
                    applyNewState(newState)
                    prefetcher.prefetchHead(newState.senses)
                }
        }
    }

    fun updateQuery(newQuery: String) {
        val content = state as? FavoritesUiState.Content ?: return
        state = content.copy(query = newQuery)
        queryFlow.value = QueryState(newQuery, Uuid.random())
    }

    fun loadFavorites() {
        val currentQuery = (state as? FavoritesUiState.Content)?.query ?: ""
        queryFlow.value = QueryState(
            query = currentQuery,
            force = Uuid.random(),
        )
    }

    fun setSelectedLanguage(language: Language) {
        val content = state as? FavoritesUiState.Content ?: return
        if (content.selectedLanguage == language) return
        state = content.copy(selectedLanguage = language)
        savedFavoritesLanguage = language
        if (started) {
            viewModelScope.launch {
                settingsRepository.insert(Setting(Setting.Name.FAVORITES_LANGUAGE, JsonPrimitive(language.code)))
                Analytics.logEvent(
                    AnalyticsEvent.SETTING_CHANGED,
                    mapOf("setting" to "favorites_language", "value" to language.code),
                )
            }
        }
        queryFlow.value = QueryState(content.query, Uuid.random())
    }

    fun setLanguageDropdownExpanded(expanded: Boolean) {
        val content = state as? FavoritesUiState.Content ?: return
        state = content.copy(isLanguageDropdownExpanded = expanded)
    }

    /**
     * Computes the new favorites state from the repository. Safe to call from any dispatcher.
     * Returns the new [FavoritesUiState.Content] without mutating [state].
     *
     * @param currentContent snapshot of the current UI state, captured on Main before dispatching.
     */
    internal suspend fun computeFavoritesState(
        query: String,
        currentContent: FavoritesUiState.Content?,
    ): FavoritesUiState.Content {
        val currentSenses = currentContent?.senses.orEmpty()
        val currentById = currentSenses.associateBy { it.senseId }

        val allFavorites = favoritesRepository.getAllGroupedByLangAndLemma()
        val hasAnyFavorites = allFavorites.isNotEmpty()
        val availableLanguages = allFavorites.availableLanguages()
        val selectedLanguage = selectedLanguageFor(availableLanguages, currentContent)
        val visibleReviewStateByLanguage = availableLanguages.associateWith { language ->
            reviewStateByLanguage[language] ?: FavoriteLanguageReviewUiState()
        }

        // Filter favorites to selected language when multi-language
        val langFiltered = if (availableLanguages.size > 1 && selectedLanguage != null) {
            allFavorites.filter { it.language == selectedLanguage }
        } else {
            allFavorites
        }

        val trimmedQuery = query.trim()
        val favorites = if (trimmedQuery.isEmpty()) {
            langFiltered
        } else {
            // Search by lemma — scope to language-filtered favorites
            val lemmaMatches = favoritesRepository.searchByLemma(trimmedQuery)
            val langFilteredSenseIds = langFiltered.map { it.senseId }.toSet()
            val lemmaMatchIds = lemmaMatches.filter { it.senseId in langFilteredSenseIds }
                .map { it.senseId }.toSet()

            // Search by translation — scoped to selected language
            val searchLanguages = if (selectedLanguage != null) listOf(selectedLanguage)
            else langFiltered.map { it.language }.distinct()

            val translationMatchIds = searchLanguages.flatMap { lang ->
                dictionaryRepository.searchSenseIdsByTranslation(
                    langFiltered.filter { it.language == lang }.map { it.senseId }.toSet(),
                    trimmedQuery, lang
                )
            }.toSet()

            // Prioritize lemma matches first, then translation-only matches
            val lemmaMatchFavorites = langFiltered.filter { it.senseId in lemmaMatchIds }
            val translationOnlyIds = translationMatchIds - lemmaMatchIds
            val translationOnlyFavorites = langFiltered.filter { it.senseId in translationOnlyIds }

            lemmaMatchFavorites + translationOnlyFavorites
        }

        val senses = favorites.map { favorite ->
            val existing = currentById[favorite.senseId]
            val cached = dictionaryRepository.getCachedSense(favorite.senseId)
            buildSenseItem(favorite, cached, existing)
        }
        val selectedReviewState = selectedLanguage?.let { visibleReviewStateByLanguage[it] }
        val study = selectedLanguage
            ?.let { language ->
                selectedReviewState?.dueCount
                    ?.takeIf { dueCount -> dueCount > 0 }
                    ?.let { dueCount ->
                        FavoritesStudyUiState(
                            language = language,
                            dueCount = dueCount,
                            delayedDueCardCount = selectedReviewState.delayedDueCardCount,
                        )
                    }
            }
        val studyDone = studyDoneState(
            study = study,
            selectedLanguage = selectedLanguage,
            reviewState = selectedReviewState,
            query = trimmedQuery,
        )

        return FavoritesUiState.Content(
            senses = senses,
            query = query,
            hasAnyFavorites = hasAnyFavorites,
            favoriteLemmas = langFiltered.mapTo(HashSet()) { normalizeLemma(it.lemma) },
            availableLanguages = availableLanguages,
            selectedLanguage = selectedLanguage,
            isLanguageDropdownExpanded = if (availableLanguages.size > 1)
                currentContent?.isLanguageDropdownExpanded ?: false else false,
            study = study,
            studyDone = studyDone,
            reviewDueCount = visibleReviewStateByLanguage.values.sumOf { it.dueCount },
        )
    }

    private fun FavoritesUiState.Content.withReviewState(): FavoritesUiState.Content {
        val visibleReviewStateByLanguage = availableLanguages.associateWith { language ->
            reviewStateByLanguage[language] ?: FavoriteLanguageReviewUiState()
        }
        val selectedReviewState = selectedLanguage?.let { visibleReviewStateByLanguage[it] }
        val updatedStudy = selectedLanguage
            ?.let { language ->
                selectedReviewState?.dueCount
                    ?.takeIf { dueCount -> dueCount > 0 }
                    ?.let { dueCount ->
                        FavoritesStudyUiState(
                            language = language,
                            dueCount = dueCount,
                            delayedDueCardCount = selectedReviewState.delayedDueCardCount,
                        )
                    }
            }
        val updatedStudyDone = studyDoneState(
            study = updatedStudy,
            selectedLanguage = selectedLanguage,
            reviewState = selectedReviewState,
            query = query.trim(),
        )
        return copy(
            study = updatedStudy,
            studyDone = updatedStudyDone,
            reviewDueCount = visibleReviewStateByLanguage.values.sumOf { it.dueCount },
        )
    }

    private fun studyDoneState(
        study: FavoritesStudyUiState?,
        selectedLanguage: Language?,
        reviewState: FavoriteLanguageReviewUiState?,
        query: String,
    ): FavoritesStudyDoneUiState? {
        val language = selectedLanguage ?: return null
        if (study != null || query.isNotEmpty()) return null
        val nextReviewAt = reviewState?.nextReviewAtEpochMs ?: return null
        if (reviewState.activeCardCount <= 0 || nextReviewAt <= clock.now().toEpochMilliseconds()) return null

        val timeLabel = nextReviewTimeLabel(nextReviewAt)
        return FavoritesStudyDoneUiState(
            language = language,
            nextReviewLabel = timeLabel.label,
            nextReviewAccessibilityValue = timeLabel.accessibilityValue,
            action = when {
                reviewState.delayedDueLemmaCount > 0 -> FavoritesStudyDoneAction.REVIEW_MORE
                reviewState.pendingFavoriteLemmaCount > 0 && reviewState.canStudyPendingFavoritesNow ->
                    FavoritesStudyDoneAction.STUDY_NEW

                else -> null
            },
            nextReviewAtEpochMs = nextReviewAt,
            delayedDueCardCount = reviewState.delayedDueCardCount,
        )
    }

    private fun nextReviewTimeLabel(nextReviewAtEpochMs: Long): ReviewTimeLabel {
        val delay = (nextReviewAtEpochMs - clock.now().toEpochMilliseconds())
            .coerceAtLeast(0L)
            .milliseconds
        val totalMinutes = delay.roundUpToWholeMinutes(minimumMinutes = 1).toInt()
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        val accessibilityValue = when {
            hours == 0 -> UiText.Plural(
                Res.plurals.favorites_study_done_duration_minutes,
                quantity = minutes,
                args = listOf(minutes),
            )

            minutes == 0 -> UiText.Plural(
                Res.plurals.favorites_study_done_duration_hours,
                quantity = hours,
                args = listOf(hours),
            )

            else -> UiText.Resource(
                Res.string.favorites_study_done_duration_hours_minutes,
                args = listOf(hours, minutes),
            )
        }
        return ReviewTimeLabel(
            label = ShortDuration.uiText(totalMinutes),
            accessibilityValue = accessibilityValue,
        )
    }

    private data class ReviewTimeLabel(
        val label: UiText,
        val accessibilityValue: UiText,
    )

    private fun List<Favorite>.availableLanguages(): List<Language> =
        map { it.language }.distinct().sorted()

    private fun selectedLanguageFor(
        availableLanguages: List<Language>,
        currentContent: FavoritesUiState.Content?,
    ): Language? =
        when {
            availableLanguages.size <= 1 -> availableLanguages.firstOrNull()
            savedFavoritesLanguage in availableLanguages -> savedFavoritesLanguage
            currentContent?.selectedLanguage in availableLanguages -> currentContent?.selectedLanguage
            else -> availableLanguages.firstOrNull()
        }

    /** Called by the composable after scrollToItem(0) completes to reset the scroll trigger. */
    fun consumeScrollToTop() {
        val content = state as? FavoritesUiState.Content ?: return
        state = content.copy(scrollToTop = false)
    }

    private fun applyNewState(newState: FavoritesUiState.Content) {
        val current = state as? FavoritesUiState.Content
        val preservedQuery = current?.query ?: newState.query
        val scrollToTop = if (pendingScrollToTop) {
            pendingScrollToTop = false
            true
        } else {
            current?.scrollToTop ?: false
        }
        state = newState.copy(query = preservedQuery, scrollToTop = scrollToTop)
    }

    /** Computes and applies favorites state. Exposed for tests; production code uses the
     *  debounced flow or [toggleFavorite] which handle threading via [Dispatchers.Default]. */
    internal suspend fun loadAndApplyState(query: String) {
        applyNewState(
            computeFavoritesState(
                query = query,
                currentContent = state as? FavoritesUiState.Content,
            ),
        )
    }


    private fun buildSenseItem(
        favorite: Favorite,
        cached: DictionaryRepository.SenseWithPos?,
        existing: FavoriteSenseItem?
    ): FavoriteSenseItem {
        return FavoriteSenseItem(
            senseId = favorite.senseId,
            targetLang = favorite.language,
            lemma = favorite.lemma,
            createdAt = favorite.createdAt,
            sense = cached?.sense ?: existing?.sense,
            relatedWords = cached?.relatedWords ?: existing?.relatedWords.orEmpty(),
            pos = cached?.pos ?: existing?.pos,
            expanded = existing?.expanded ?: false,
            loading = existing?.loading == true && cached == null,
            error = if (cached != null) null else existing?.error
        )
    }

    private fun updateSense(senseId: String, transform: (FavoriteSenseItem) -> FavoriteSenseItem) {
        val content = state as? FavoritesUiState.Content ?: return
        state = content.copy(
            senses = content.senses.map { if (it.senseId == senseId) transform(it) else it }
        )
    }

    private fun findSense(senseId: String): FavoriteSenseItem? {
        val content = state as? FavoritesUiState.Content ?: return null
        return content.senses.find { it.senseId == senseId }
    }

    fun toggleSense(senseId: String) {
        val item = findSense(senseId) ?: return
        val wasExpanded = item.expanded
        val shouldLoad = !wasExpanded && item.sense == null && !item.loading && item.error == null

        updateSense(senseId) { it.copy(expanded = !wasExpanded, error = null) }

        if (shouldLoad) {
            viewModelScope.launch { loadSense(item) }
        }
    }

    fun toggleFavorite(
        senseId: String,
        removedMessage: String,
        undoLabel: String,
        onFavoritesChanged: (Language) -> Unit = {},
    ) {
        val item = findSense(senseId) ?: return
        viewModelScope.launch {
            // Fetch the favorite to get its createdAt before removal
            val favorite = favoritesRepository.getOne(senseId, item.targetLang) ?: return@launch

            // Remove from repository, then remove from displayed list for immediate feedback
            val removedSnapshot = favoritesRepository.remove(senseId, favorite.language)
            Analytics.logEvent(
                AnalyticsEvent.FAVORITES_REMOVE,
                mapOf("lang" to favorite.language.code, "source" to "favorites_list"),
            )
            onFavoritesChanged(favorite.language)
            val content = state as? FavoritesUiState.Content ?: return@launch
            state = content.copy(senses = content.senses.filter { it.senseId != senseId })

            // Recompute languages and filtered senses from repository (handles query
            // filtering, language switches, and all edge cases correctly)
            val uiSnapshot = state as? FavoritesUiState.Content
            val newState = withContext(Dispatchers.Default) { computeFavoritesState(content.query, uiSnapshot) }
            applyNewState(newState)
            prefetcher.prefetchHead(newState.senses)

            // Show snackbar with an undo option
            val result = snackbarHostState.showSnackbar(
                message = removedMessage,
                actionLabel = if (removedSnapshot != null) undoLabel else null,
                duration = SnackbarDuration.Short
            )

            if (result == SnackbarResult.ActionPerformed && removedSnapshot != null) {
                // Replay the per-card snapshot to restore scheduling exactly as it was
                favoritesRepository.restoreForUndo(removedSnapshot)
                Analytics.logEvent(
                    AnalyticsEvent.FAVORITES_SAVE,
                    mapOf("lang" to favorite.language.code, "source" to "favorites_undo"),
                )
                onFavoritesChanged(favorite.language)
                loadFavorites()
            }
        }
    }

    private suspend fun loadSense(item: FavoriteSenseItem) {
        updateSense(item.senseId) { it.copy(loading = true, error = null) }
        try {
            val loaded = dictionaryRepository.getSenses(
                item.targetLang,
                item.lemma,
                setOf(item.senseId),
            )
            val result = loaded[item.senseId]
            val sense = result?.sense
            val error = if (sense == null) {
                result?.missingReason?.toFavoriteSenseLoadError(item.targetLang)
                    ?: UiText.Resource(Res.string.favorites_error_meaning_not_found)
            } else null
            updateSense(item.senseId) {
                it.copy(
                    sense = sense?.sense,
                    relatedWords = sense?.relatedWords ?: it.relatedWords,
                    pos = sense?.pos,
                    loading = false,
                    error = error
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            updateSense(item.senseId) {
                it.copy(
                    loading = false,
                    error = e.message?.let(UiText::Plain)
                        ?: UiText.Resource(Res.string.favorites_error_load_meaning_failed)
                )
            }
        }
    }

    fun prefetchVisibleRange(senses: List<FavoriteSenseItem>, range: IntRange) {
        prefetcher.prefetchVisibleRange(senses, range)
    }
}

internal fun DictionaryRepository.FavoriteSenseMissingReason.toFavoriteSenseLoadError(language: Language): UiText {
    return when (this) {
        DictionaryRepository.FavoriteSenseMissingReason.DICTIONARY_NOT_DOWNLOADED ->
            UiText.Resource(
                Res.string.favorites_error_dictionary_not_downloaded,
                args = listOf(language.selfName)
            )

        DictionaryRepository.FavoriteSenseMissingReason.MEANING_NOT_FOUND ->
            UiText.Resource(Res.string.favorites_error_meaning_not_found)

        DictionaryRepository.FavoriteSenseMissingReason.ONLINE_ONLY ->
            UiText.Resource(Res.string.favorites_error_word_needs_download)
    }
}

