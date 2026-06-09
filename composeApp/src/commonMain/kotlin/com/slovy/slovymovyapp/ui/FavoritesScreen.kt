package com.slovy.slovymovyapp.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewModelScope
import com.slovy.slovymovyapp.analytics.Analytics
import com.slovy.slovymovyapp.analytics.AnalyticsEvent
import com.slovy.slovymovyapp.analytics.PerformanceMonitoring
import com.slovy.slovymovyapp.analytics.putAttributes
import com.slovy.slovymovyapp.analytics.useWithResult
import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.favorites.Favorite
import com.slovy.slovymovyapp.data.favorites.FavoritesRepository
import com.slovy.slovymovyapp.data.remote.*
import com.slovy.slovymovyapp.data.settings.Setting
import com.slovy.slovymovyapp.data.settings.SettingsRepository
import com.slovy.slovymovyapp.i18n.ShortDuration
import com.slovy.slovymovyapp.i18n.UiText
import com.slovy.slovymovyapp.i18n.resolve
import com.slovy.slovymovyapp.ui.components.AppSearchBar
import com.slovy.slovymovyapp.ui.components.EmptyState
import com.slovy.slovymovyapp.ui.icons.NoFavsImage
import com.slovy.slovymovyapp.ui.icons.SearchOtter
import com.slovy.slovymovyapp.ui.icons.SlovyIcons
import com.slovy.slovymovyapp.ui.theme.serifFontFamily
import com.slovy.slovymovyapp.ui.word.LoadingPlaceholder
import com.slovy.slovymovyapp.ui.word.SenseCard
import com.slovy.slovymovyapp.ui.word.SenseCardData
import com.slovy.slovymovyapp.ui.word.SenseUiState
import com.slovy.slovymovyapp.util.roundUpToWholeMinutes
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import slovymovyapp.composeapp.generated.resources.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.Uuid

data class FavoriteSenseItem(
    val senseId: String,
    val targetLang: Language,
    val lemma: String,
    val createdAt: Long,
    val sense: LanguageCardResponseSense? = null,
    val relatedWords: Map<String, RelatedWord> = emptyMap(),
    val pos: PartOfSpeech? = null,
    val expanded: Boolean = false,
    val loading: Boolean = false,
    val error: UiText? = null
)

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
    private val clock: Clock,
) : ViewModel() {

    var state by mutableStateOf<FavoritesUiState>(FavoritesUiState.Loading)
        private set

    val scrollState = LazyListState()
    val emptyStateScrollState = ScrollState(0)
    val snackbarHostState = SnackbarHostState()

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
        private const val PREFETCH_LIMIT = 16
    }

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
                    prefetchSenses(newState.senses.take(PREFETCH_LIMIT))
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
            favoriteLemmas = langFiltered.map { it.lemma }.toSet(),
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
            prefetchSenses(newState.senses.take(PREFETCH_LIMIT))

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

    private fun prefetchSenses(items: List<FavoriteSenseItem>) {
        val toLoad = items.filter { it.sense == null && !it.loading && it.error == null }
        toLoad.forEach { item ->
            viewModelScope.launch { loadSense(item) }
        }
    }

    fun prefetchVisibleRange(senses: List<FavoriteSenseItem>, range: IntRange) {
        if (senses.isEmpty() || range.isEmpty()) return
        val safeRange = range.first.coerceAtLeast(0)..minOf(range.last, senses.lastIndex)
        if (safeRange.isEmpty()) return
        prefetchSenses(senses.slice(safeRange).take(PREFETCH_LIMIT))
    }
}

private fun DictionaryRepository.FavoriteSenseMissingReason.toFavoriteSenseLoadError(language: Language): UiText {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    viewModel: FavoritesViewModel,
    onNavigateToSearch: () -> Unit = {},
    onSearchInDictionary: (String) -> Unit = {},
    onNavigateToWordDetail: (Language, String, String?) -> Unit = { _, _, _ -> },
    onNavigateToSettings: () -> Unit = {},
    onNavigateToStats: () -> Unit = {},
    onStartStudy: (Language) -> Unit = {},
    onContinueStudyingNow: (Language, FavoritesStudyDoneAction) -> Unit = { _, _ -> },
    onFavoritesChanged: (Language) -> Unit = {},
    onRefreshReviewState: () -> Unit = {},
) {
    val focusManager = LocalFocusManager.current
    val removedMessage = stringResource(Res.string.favorites_removed_message)
    val undoLabel = stringResource(Res.string.favorites_removed_undo)

    LifecycleResumeEffect(viewModel) {
        viewModel.loadFavorites()
        onRefreshReviewState()
        onPauseOrDispose { }
    }

    LaunchedEffect(viewModel.scrollState.isScrollInProgress) {
        if (viewModel.scrollState.isScrollInProgress) {
            focusManager.clearFocus()
        }
    }

    val scrollToTop = (viewModel.state as? FavoritesUiState.Content)?.scrollToTop ?: false
    LaunchedEffect(scrollToTop) {
        if (scrollToTop) {
            viewModel.scrollState.scrollToItem(0)
            viewModel.consumeScrollToTop()
        }
    }

    val isEmptyState = (viewModel.state as? FavoritesUiState.Content)?.hasAnyFavorites == false
    LaunchedEffect(isEmptyState) {
        if (isEmptyState) {
            viewModel.emptyStateScrollState.scrollTo(0)
        }
    }

    val nextReviewAtEpochMs = (viewModel.state as? FavoritesUiState.Content)
        ?.studyDone?.nextReviewAtEpochMs
    LaunchedEffect(nextReviewAtEpochMs) {
        val target = nextReviewAtEpochMs ?: return@LaunchedEffect
        val remaining = target - Clock.System.now().toEpochMilliseconds()
        if (remaining > 0) delay(remaining.milliseconds)
        onRefreshReviewState()
    }

    FavoritesScreenContent(
        state = viewModel.state,
        scrollState = viewModel.scrollState,
        emptyStateScrollState = viewModel.emptyStateScrollState,
        snackbarHostState = viewModel.snackbarHostState,
        onNavigateToSearch = onNavigateToSearch,
        onSearchInDictionary = onSearchInDictionary,
        onQueryChange = { viewModel.updateQuery(it) },
        onSenseToggle = { viewModel.toggleSense(it) },
        onFavoriteToggle = { viewModel.toggleFavorite(it, removedMessage, undoLabel, onFavoritesChanged) },
        onNavigateToWordDetail = onNavigateToWordDetail,
        onNavigateToSettings = onNavigateToSettings,
        onNavigateToStats = onNavigateToStats,
        onPrefetchVisible = { senses, range -> viewModel.prefetchVisibleRange(senses, range) },
        onLanguageSelected = { viewModel.setSelectedLanguage(it) },
        onSetLanguageDropdownExpanded = { viewModel.setLanguageDropdownExpanded(it) },
        onStartStudy = onStartStudy,
        onContinueStudyingNow = onContinueStudyingNow,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreenContent(
    state: FavoritesUiState,
    scrollState: LazyListState = LazyListState(),
    emptyStateScrollState: ScrollState? = null,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onNavigateToSearch: () -> Unit = {},
    onSearchInDictionary: (String) -> Unit = {},
    onQueryChange: (String) -> Unit = {},
    onSenseToggle: (String) -> Unit = {},
    onFavoriteToggle: (String) -> Unit = {},
    onNavigateToWordDetail: (Language, String, String?) -> Unit = { _, _, _ -> },
    onNavigateToSettings: () -> Unit = {},
    onNavigateToStats: () -> Unit = {},
    onPrefetchVisible: (List<FavoriteSenseItem>, IntRange) -> Unit = { _, _ -> },
    onLanguageSelected: (Language) -> Unit = {},
    onSetLanguageDropdownExpanded: (Boolean) -> Unit = {},
    onStartStudy: (Language) -> Unit = {},
    onContinueStudyingNow: (Language, FavoritesStudyDoneAction) -> Unit = { _, _ -> },
) {
    val focusManager = LocalFocusManager.current
    val resolvedEmptyStateScrollState = emptyStateScrollState ?: remember { ScrollState(0) }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(Res.string.favorites_title),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = MaterialTheme.serifFontFamily,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            )
        },
        bottomBar = {
            AppNavigationBar(
                currentScreen = AppScreen.FAVORITES,
                onNavigateToSearch = onNavigateToSearch,
                onNavigateToFavorites = {},
                onNavigateToStats = onNavigateToStats,
                onNavigateToSettings = onNavigateToSettings
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        when (state) {
            is FavoritesUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingPlaceholder(label = stringResource(Res.string.favorites_loading))
                }
            }

            is FavoritesUiState.Content -> {
                LaunchedEffect(state.senses, scrollState) {
                    snapshotFlow { scrollState.layoutInfo.visibleItemsInfo.map { it.index } }
                        .distinctUntilChanged()
                        .collectLatest { indices ->
                            if (indices.isEmpty()) return@collectLatest
                            val first = indices.minOrNull() ?: return@collectLatest
                            val last = indices.maxOrNull() ?: return@collectLatest
                            val lookahead = 5
                            val end = minOf(last + lookahead, state.senses.lastIndex)
                            onPrefetchVisible(state.senses, first..end)
                        }
                }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (state.hasAnyFavorites) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppSearchBar(
                                query = state.query,
                                onQueryChange = onQueryChange,
                                modifier = Modifier.weight(1f),
                                placeholder = stringResource(Res.string.favorites_search_placeholder)
                            )

                            if (state.showLanguagePicker) {
                                val currentLanguage = state.selectedLanguage
                                    ?: state.availableLanguages.firstOrNull()

                                ExposedDropdownMenuBox(
                                    expanded = state.isLanguageDropdownExpanded,
                                    onExpandedChange = onSetLanguageDropdownExpanded
                                ) {
                                    Surface(
                                        modifier = Modifier
                                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
                                            .height(56.dp)
                                            .widthIn(min = 56.dp),
                                        shape = MaterialTheme.shapes.extraLarge,
                                        tonalElevation = 1.dp,
                                        shadowElevation = 1.dp,
                                        color = MaterialTheme.colorScheme.surfaceContainerHighest
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(start = 16.dp, end = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = currentLanguage?.flag ?: "",
                                                style = MaterialTheme.typography.bodyLarge
                                            )
                                            ExposedDropdownMenuDefaults.TrailingIcon(
                                                expanded = state.isLanguageDropdownExpanded
                                            )
                                        }
                                    }
                                    ExposedDropdownMenu(
                                        expanded = state.isLanguageDropdownExpanded,
                                        onDismissRequest = { onSetLanguageDropdownExpanded(false) },
                                        modifier = Modifier.width(200.dp),
                                        shape = MaterialTheme.shapes.small,
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        shadowElevation = 2.dp
                                    ) {
                                        state.availableLanguages.forEach { language ->
                                            DropdownMenuItem(
                                                text = {
                                                    Row(
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(language.flag)
                                                        Text(language.selfName)
                                                    }
                                                },
                                                onClick = {
                                                    onLanguageSelected(language)
                                                    onSetLanguageDropdownExpanded(false)
                                                },
                                                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures(onTap = {
                                    focusManager.clearFocus()
                                })
                            }
                    ) {
                        when {
                            !state.hasAnyFavorites -> {
                                ScrollableEmptyStateContainer(
                                    scrollState = resolvedEmptyStateScrollState,
                                ) {
                                    FavoritesEmptyState(onNavigateToSearch = onNavigateToSearch)
                                }
                            }

                            state.showNoResults -> {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = BiasAlignment(horizontalBias = 0f, verticalBias = -0.3f)
                                ) {
                                    EmptyState(
                                        iconContent = {
                                            Image(
                                                imageVector = SlovyIcons.SearchOtter,
                                                contentDescription = null,
                                                modifier = Modifier.size(140.dp)
                                            )
                                        },
                                        title = stringResource(Res.string.favorites_no_results_title, state.query),
                                        description = stringResource(Res.string.favorites_no_results_description),
                                        action = {
                                            Button(
                                                onClick = { onSearchInDictionary(state.query) },
                                                shape = CircleShape,
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = MaterialTheme.colorScheme.primary,
                                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                                ),
                                                contentPadding = PaddingValues(horizontal = 28.dp),
                                                modifier = Modifier
                                                    .height(56.dp)
                                                    .widthIn(min = 220.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Search,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Text(stringResource(Res.string.favorites_no_results_action_search_dictionary))
                                            }
                                        }
                                    )
                                }
                            }

                            else -> {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    val words = state.senses.distinctBy { it.lemma }
                                    state.study?.let { study ->
                                        StudyDueCard(
                                            study = study,
                                            onStartStudy = {
                                                Analytics.logEvent(
                                                    AnalyticsEvent.FAVORITES_STUDY_DUE_CLICK,
                                                    mapOf(
                                                        "lang" to study.language.code,
                                                        "due_count" to study.dueCount.toLong(),
                                                        "delayed_due_card_count" to study.delayedDueCardCount.toLong(),
                                                    ),
                                                )
                                                onStartStudy(study.language)
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 8.dp),
                                        )
                                    } ?: state.studyDone?.let { studyDone ->
                                        StudyDoneCard(
                                            studyDone = studyDone,
                                            onContinueStudyingNow = {
                                                studyDone.action?.let { action ->
                                                    Analytics.logEvent(
                                                        when (action) {
                                                            FavoritesStudyDoneAction.REVIEW_MORE ->
                                                                AnalyticsEvent.FAVORITES_STUDY_DONE_REVIEW_MORE_CLICK

                                                            FavoritesStudyDoneAction.STUDY_NEW ->
                                                                AnalyticsEvent.FAVORITES_STUDY_DONE_STUDY_NEW_CLICK
                                                        },
                                                        mapOf(
                                                            "lang" to studyDone.language.code,
                                                            "due_count" to state.reviewDueCount.toLong(),
                                                            "delayed_due_card_count" to studyDone.delayedDueCardCount.toLong(),
                                                        ),
                                                    )
                                                    onContinueStudyingNow(studyDone.language, action)
                                                }
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 8.dp),
                                        )
                                    }
                                    Text(
                                        text = stringResource(
                                            Res.string.favorites_stats,
                                            state.senses.size,
                                            words.size
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp)
                                    )

                                    LazyColumn(
                                        state = scrollState,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 16.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        contentPadding = PaddingValues(bottom = 16.dp)
                                    ) {
                                        items(
                                            state.senses,
                                            key = { it.senseId },
                                            contentType = { "sense_card" }) { item ->
                                            FavoriteSenseCard(
                                                item = item,
                                                onToggle = { onSenseToggle(item.senseId) },
                                                onFavoriteToggle = { onFavoriteToggle(item.senseId) },
                                                onViewFullDetails = {
                                                    onNavigateToWordDetail(item.targetLang, item.lemma, item.senseId)
                                                },
                                                onWordClick = { word ->
                                                    Analytics.logEvent(AnalyticsEvent.FAVORITES_WORD_SHOW)
                                                    onNavigateToWordDetail(item.targetLang, word, null)
                                                },
                                                favoriteLemmas = state.favoriteLemmas
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScrollableEmptyStateContainer(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = maxHeight)
                .verticalScroll(scrollState),
            contentAlignment = BiasAlignment(horizontalBias = 0f, verticalBias = -0.18f),
            content = content
        )
    }
}

@Composable
private fun FavoritesEmptyState(
    onNavigateToSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val heartIconId = "favorite-heart"
    val descriptionBeforeHeart = stringResource(Res.string.favorites_empty_description_before_heart_icon)
    val descriptionAfterHeart = stringResource(Res.string.favorites_empty_description_after_heart_icon)
    val favoriteLabel = stringResource(Res.string.search_content_desc_favorite)
    val descriptionText = buildAnnotatedString {
        append(descriptionBeforeHeart)
        appendInlineContent(heartIconId, favoriteLabel)
        append(descriptionAfterHeart)
    }
    val inlineContent = mapOf(
        heartIconId to InlineTextContent(
            placeholder = Placeholder(
                width = 18.sp,
                height = 18.sp,
                placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
            ),
        ) {
            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.9f),
                modifier = Modifier.fillMaxSize()
            )
        }
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            imageVector = SlovyIcons.NoFavsImage,
            contentDescription = null,
            modifier = Modifier.size(204.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = stringResource(Res.string.favorites_empty_title),
            style = MaterialTheme.typography.headlineMedium.copy(
                fontFamily = MaterialTheme.serifFontFamily,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 34.sp,
            ),
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 340.dp)
        )

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = descriptionText,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontFamily = MaterialTheme.serifFontFamily,
                fontStyle = FontStyle.Italic,
                lineHeight = 24.sp,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            inlineContent = inlineContent,
            modifier = Modifier.widthIn(max = 320.dp)
        )

        Spacer(modifier = Modifier.height(36.dp))

        Button(
            onClick = onNavigateToSearch,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            contentPadding = PaddingValues(horizontal = 28.dp),
            modifier = Modifier
                .height(56.dp)
                .widthIn(min = 220.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = stringResource(Res.string.favorites_empty_action_start_searching),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            )
        }
    }
}

@Composable
private fun FavoriteSenseCard(
    item: FavoriteSenseItem,
    onToggle: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onViewFullDetails: () -> Unit,
    onWordClick: (String) -> Unit = {},
    favoriteLemmas: Set<String> = emptySet()
) {
    val senseState = SenseUiState(
        senseId = item.senseId,
        expanded = item.expanded,
        examplesExpanded = false,
        languageExpanded = emptyMap(),
        favorite = true,
        showFavoriteToggle = item.expanded,
        pos = item.pos
    )
    SenseCard(
        data = SenseCardData(
            senseId = item.senseId,
            lemma = item.lemma,
            showLemma = true,
            sense = item.sense,
            pos = item.pos,
            loading = item.loading,
            error = item.error?.resolve(),
            diagnosticInfoOnError = buildDeveloperDiagnosticInfo(item.senseId, item.createdAt)
        ),
        state = senseState,
        onToggle = onToggle,
        onFavoriteToggle = onFavoriteToggle,
        onViewFullDetails = onViewFullDetails,
        relatedWords = item.relatedWords,
        onWordClick = onWordClick,
        favoriteLemmas = favoriteLemmas
    )
}

@Composable
private fun StudyDoneCard(
    studyDone: FavoritesStudyDoneUiState,
    onContinueStudyingNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val regionLabel = stringResource(Res.string.favorites_study_done_region)
    val continueLabel = studyDone.action?.let { action ->
        stringResource(
            when (action) {
                FavoritesStudyDoneAction.REVIEW_MORE -> Res.string.favorites_study_done_review_more
                FavoritesStudyDoneAction.STUDY_NEW -> Res.string.favorites_study_done_study_new
            },
        )
    }
    val nextReviewAccessibilityLabel = stringResource(
        Res.string.favorites_study_done_next_review_a11y,
        studyDone.nextReviewAccessibilityValue.resolve()
    )
    val isPreview = LocalInspectionMode.current
    var visible by remember { mutableStateOf(isPreview) }
    LaunchedEffect(Unit) {
        visible = true
    }
    val cardProgress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 240),
        label = "studyDoneCard"
    )
    val checkProgress by animateFloatAsState(
        targetValue = if (visible) 1f else 0.6f,
        animationSpec = tween(durationMillis = 280, delayMillis = 40),
        label = "studyDoneCheck"
    )
    val slidePx = with(LocalDensity.current) { 6.dp.toPx() }

    Surface(
        modifier = modifier
            .graphicsLayer {
                alpha = cardProgress
                translationY = slidePx * (1f - cardProgress)
            }
            .semantics { contentDescription = regionLabel },
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.09f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.favorites_study_done_title),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.4.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Text(
                            text = studyDone.nextReviewLabel.resolve(),
                            fontFamily = MaterialTheme.serifFontFamily,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.sp,
                            lineHeight = 27.3.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .alignByBaseline()
                                .semantics { contentDescription = nextReviewAccessibilityLabel },
                        )
                        Text(
                            text = stringResource(Res.string.favorites_study_done_until_next_review),
                            fontFamily = MaterialTheme.serifFontFamily,
                            fontSize = 13.sp,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.alignByBaseline(),
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .graphicsLayer {
                            scaleX = checkProgress
                            scaleY = checkProgress
                        }
                        .background(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (continueLabel != null) {
                HorizontalDivider(
                    modifier = Modifier.padding(top = 13.dp, bottom = 11.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            onClickLabel = continueLabel,
                            role = Role.Button,
                            onClick = onContinueStudyingNow,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = continueLabel,
                        fontFamily = MaterialTheme.serifFontFamily,
                        fontSize = 13.5.sp,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
            }
        }
    }
}

@Composable
private fun StudyDueCard(
    study: FavoritesStudyUiState,
    onStartStudy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val actionLabel = stringResource(Res.string.favorites_study_due_action)
    Surface(
        onClick = onStartStudy,
        modifier = modifier.semantics {
            onClick(label = actionLabel, action = null)
        },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(Res.string.favorites_study_due_title),
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.4.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = pluralStringResource(
                            Res.plurals.favorites_study_due_count,
                            study.dueCount,
                            study.dueCount
                        ),
                        fontFamily = MaterialTheme.serifFontFamily,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = (-0.3).sp,
                        lineHeight = 27.3.sp,
                        modifier = Modifier.alignByBaseline(),
                    )
                    Text(
                        text = stringResource(Res.string.favorites_study_due_subtitle, study.estimatedMinutes),
                        fontFamily = MaterialTheme.serifFontFamily,
                        fontSize = 13.sp,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                        modifier = Modifier.alignByBaseline(),
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
            )
        }
    }
}

// Preview helpers
private fun createMockSense(
    id: String,
    definition: String,
    level: LearnerLevel = LearnerLevel.B1,
    frequency: SenseFrequency = SenseFrequency.MIDDLE,
    examples: List<LanguageCardExample> = emptyList(),
    synonyms: List<String> = emptyList(),
    antonyms: List<String> = emptyList(),
): LanguageCardResponseSense {
    return LanguageCardResponseSense(
        senseId = id,
        senseDefinition = definition,
        learnerLevel = level,
        frequency = frequency,
        semanticGroupId = "group1",
        nameType = null,
        examples = examples,
        synonyms = synonyms,
        antonyms = antonyms,
        commonPhrases = emptyList(),
        traits = emptyList(),
        targetLangDefinitions = mapOf(Language.ENGLISH to definition),
    )
}

private fun createSenseItem(
    senseId: String,
    lemma: String,
    targetLang: Language = Language.ENGLISH,
    createdAt: Long = 1704067200L,
    sense: LanguageCardResponseSense? = null,
    pos: PartOfSpeech? = null,
    expanded: Boolean = false,
    loading: Boolean = false,
    error: UiText? = null
) = FavoriteSenseItem(
    senseId = senseId,
    targetLang = targetLang,
    lemma = lemma,
    createdAt = createdAt,
    sense = sense,
    pos = pos,
    expanded = expanded,
    loading = loading,
    error = error
)

@Preview
@Composable
fun PreviewFavoritesScreenLoading(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        FavoritesScreenContent(state = FavoritesUiState.Loading)
    }
}

@Preview
@Composable
fun PreviewFavoritesScreenEmpty(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        FavoritesScreenContent(
            state = FavoritesUiState.Content(senses = emptyList(), hasAnyFavorites = false)
        )
    }
}

@Preview
@Composable
fun PreviewFavoritesScreenCollapsed(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        val state = FavoritesUiState.Content(
            senses = listOf(
                createSenseItem(
                    senseId = "run-1",
                    lemma = "run",
                    sense = createMockSense("run-1", "to move swiftly on foot", LearnerLevel.A1, SenseFrequency.HIGH),
                    pos = PartOfSpeech.VERB
                ),
                createSenseItem(
                    senseId = "book-1",
                    lemma = "book",
                    sense = createMockSense(
                        "book-1",
                        "a written or printed work",
                        LearnerLevel.A1,
                        SenseFrequency.HIGH
                    ),
                    pos = PartOfSpeech.NOUN
                )
            ),
            hasAnyFavorites = true
        )
        FavoritesScreenContent(state = state)
    }
}

@Preview
@Composable
fun PreviewFavoritesScreenStudyDone(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        val state = FavoritesUiState.Content(
            senses = listOf(
                createSenseItem(
                    senseId = "sleep-1",
                    lemma = "slapen",
                    sense = createMockSense("sleep-1", "to sleep", LearnerLevel.A1, SenseFrequency.HIGH),
                    pos = PartOfSpeech.VERB
                ),
                createSenseItem(
                    senseId = "gezelligheid-1",
                    lemma = "gezelligheid",
                    sense = createMockSense(
                        "gezelligheid-1",
                        "coziness, togetherness",
                        LearnerLevel.B2,
                        SenseFrequency.MIDDLE
                    ),
                    pos = PartOfSpeech.NOUN
                ),
                createSenseItem(
                    senseId = "uitzonderlijk-1",
                    lemma = "uitzonderlijk",
                    sense = createMockSense(
                        "uitzonderlijk-1",
                        "exceptional, remarkable",
                        LearnerLevel.C1,
                        SenseFrequency.LOW
                    ),
                    pos = PartOfSpeech.ADJECTIVE
                )
            ),
            hasAnyFavorites = true,
            availableLanguages = listOf(Language.DUTCH),
            selectedLanguage = Language.DUTCH,
            studyDone = FavoritesStudyDoneUiState(
                language = Language.DUTCH,
                nextReviewLabel = ShortDuration.uiText(totalMinutes = 4 * 60),
                nextReviewAccessibilityValue = UiText.Plural(
                    Res.plurals.favorites_study_done_duration_hours,
                    quantity = 4,
                    args = listOf(4),
                ),
                action = FavoritesStudyDoneAction.REVIEW_MORE,
                nextReviewAtEpochMs = 0L,
            ),
        )
        FavoritesScreenContent(state = state)
    }
}

@Preview
@Composable
fun PreviewFavoritesScreenExpanded(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        val state = FavoritesUiState.Content(
            senses = listOf(
                createSenseItem(
                    senseId = "happy-1",
                    lemma = "happy",
                    sense = createMockSense(
                        "happy-1",
                        "feeling or showing pleasure",
                        LearnerLevel.A2,
                        SenseFrequency.HIGH,
                        examples = listOf(
                            LanguageCardExample(
                                "I'm happy",
                                mapOf(Language.POLISH to "Jestem szczęśliwy")
                            )
                        )
                    ),
                    pos = PartOfSpeech.ADJECTIVE,
                    expanded = true
                )
            ),
            hasAnyFavorites = true
        )
        FavoritesScreenContent(state = state)
    }
}

@Preview
@Composable
fun PreviewFavoritesScreenWithSearch(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        val state = FavoritesUiState.Content(
            senses = listOf(
                createSenseItem(
                    senseId = "run-1",
                    lemma = "run",
                    sense = createMockSense("run-1", "to move swiftly on foot"),
                    pos = PartOfSpeech.VERB
                )
            ),
            query = "run",
            hasAnyFavorites = true
        )
        FavoritesScreenContent(state = state)
    }
}

@Preview
@Composable
fun PreviewFavoritesScreenNoResults(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        FavoritesScreenContent(
            state = FavoritesUiState.Content(senses = emptyList(), query = "xyz", hasAnyFavorites = true)
        )
    }
}

@Preview
@Composable
fun PreviewFavoritesScreenLoadingAndError(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        val state = FavoritesUiState.Content(
            senses = listOf(
                createSenseItem(
                    senseId = "ready-1",
                    lemma = "ready",
                    sense = createMockSense("ready-1", "completely prepared"),
                    pos = PartOfSpeech.ADJECTIVE
                ),
                createSenseItem(
                    senseId = "ready-2",
                    lemma = "ready",
                    loading = true
                ),
                createSenseItem(
                    senseId = "ready-3",
                    lemma = "ready",
                    error = UiText.Resource(Res.string.favorites_error_load_meaning_failed)
                )
            ),
            hasAnyFavorites = true
        )
        FavoritesScreenContent(state = state)
    }
}

@Preview
@Composable
fun PreviewFavoritesScreenMultiLanguage(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        val state = FavoritesUiState.Content(
            senses = listOf(
                createSenseItem(
                    senseId = "run-1",
                    lemma = "run",
                    targetLang = Language.ENGLISH,
                    sense = createMockSense("run-1", "to move swiftly on foot"),
                    pos = PartOfSpeech.VERB
                ),
                createSenseItem(
                    senseId = "book-1",
                    lemma = "book",
                    targetLang = Language.ENGLISH,
                    sense = createMockSense("book-1", "a written or printed work"),
                    pos = PartOfSpeech.NOUN
                )
            ),
            hasAnyFavorites = true,
            availableLanguages = listOf(Language.ENGLISH, Language.POLISH),
            selectedLanguage = Language.ENGLISH
        )
        FavoritesScreenContent(state = state)
    }
}
