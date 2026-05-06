package com.slovy.slovymovyapp.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewModelScope
import com.slovy.slovymovyapp.analytics.Analytics
import com.slovy.slovymovyapp.analytics.AnalyticsEvent
import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.favorites.Favorite
import com.slovy.slovymovyapp.data.favorites.FavoritesRepository
import com.slovy.slovymovyapp.data.learning.intake.LearningIntake
import com.slovy.slovymovyapp.data.learning.stats.StatsService
import com.slovy.slovymovyapp.data.remote.*
import com.slovy.slovymovyapp.data.settings.Setting
import com.slovy.slovymovyapp.data.settings.SettingsRepository
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
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.*
import kotlinx.datetime.TimeZone.Companion.currentSystemDefault
import kotlinx.datetime.format.char
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import slovymovyapp.composeapp.generated.resources.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
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
) {
    val estimatedMinutes: Int = ((dueCount + 3) / 4).coerceAtLeast(1)
}

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
    private val statsService: StatsService,
    private val intakeService: LearningIntake,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    var state by mutableStateOf<FavoritesUiState>(FavoritesUiState.Loading)
        private set

    val scrollState = LazyListState()
    val snackbarHostState = SnackbarHostState()

    private var pendingScrollToTop: Boolean = false
    private var savedFavoritesLanguage: Language? = null

    /** Called from outside (e.g. word detail) when a favorite was just added. */
    fun requestScrollToTop() {
        pendingScrollToTop = true
    }

    /** Called after dictionary/local DB changes when loaded favorite details may be stale. */
    fun dropCachedFavoriteDetails() {
        val content = state as? FavoritesUiState.Content ?: return
        state = content.withoutCachedFavoriteDetails()
    }

    private val queryFlow = MutableStateFlow(QueryState("", Uuid.random(), runIntake = true))

    private data class QueryState(
        val query: String,
        val force: Uuid,
        val runIntake: Boolean = false,
    )

    companion object {
        private const val QUERY_DEBOUNCE_MS = 200L
        private const val PREFETCH_LIMIT = 16
    }

    init {
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
                        emit(
                            computeFavoritesState(
                                query = queryState.query,
                                currentContent = snapshot,
                                runIntake = queryState.runIntake,
                            ),
                        )
                    }
                        .flowOn(Dispatchers.Default)
                }
                .collect { newState ->
                    applyNewState(newState)
                    prefetchSenses(newState.senses.take(PREFETCH_LIMIT))
                }
        }
    }

    fun updateQuery(newQuery: String) {
        val content = state as? FavoritesUiState.Content ?: return
        state = content.copy(query = newQuery)
        queryFlow.value = QueryState(newQuery, Uuid.random(), runIntake = false)
    }

    fun loadFavorites() {
        val currentQuery = (state as? FavoritesUiState.Content)?.query ?: ""
        queryFlow.value = QueryState(
            query = currentQuery,
            force = Uuid.random(),
            runIntake = true,
        )
    }

    fun setSelectedLanguage(language: Language) {
        val content = state as? FavoritesUiState.Content ?: return
        if (content.selectedLanguage == language) return
        state = content.copy(selectedLanguage = language)
        savedFavoritesLanguage = language
        viewModelScope.launch {
            settingsRepository.insert(Setting(Setting.Name.FAVORITES_LANGUAGE, JsonPrimitive(language.code)))
        }
        queryFlow.value = QueryState(content.query, Uuid.random(), runIntake = true)
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
        runIntake: Boolean = false,
    ): FavoritesUiState.Content {
        val currentSenses = currentContent?.senses.orEmpty()
        val currentById = currentSenses.associateBy { it.senseId }

        val initialFavorites = favoritesRepository.getAllGroupedByLangAndLemma()
        val initialSelectedLanguage = selectedLanguageFor(
            availableLanguages = initialFavorites.availableLanguages(),
            currentContent = currentContent,
        )
        if (runIntake && initialSelectedLanguage != null) {
            intakeService.runIntake(initialSelectedLanguage.code)
        }

        val allFavorites = if (runIntake) {
            favoritesRepository.getAllGroupedByLangAndLemma()
        } else {
            initialFavorites
        }
        val hasAnyFavorites = allFavorites.isNotEmpty()
        val availableLanguages = allFavorites.availableLanguages()
        val selectedLanguage = selectedLanguageFor(availableLanguages, currentContent)

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
        val study = selectedLanguage
            ?.let { language ->
                statsService.globalStats(language.code)
                    .dueToday
                    .takeIf { it > 0 }
                    ?.let { dueCount -> FavoritesStudyUiState(language, dueCount) }
            }

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
        )
    }

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
        state = if (pendingScrollToTop) {
            pendingScrollToTop = false
            newState.copy(scrollToTop = true)
        } else {
            newState.copy(scrollToTop = (state as? FavoritesUiState.Content)?.scrollToTop ?: false)
        }
    }

    /** Computes and applies favorites state. Exposed for tests; production code uses the
     *  debounced flow or [toggleFavorite] which handle threading via [Dispatchers.Default]. */
    internal suspend fun loadAndApplyState(
        query: String,
        runIntake: Boolean = false,
    ) {
        applyNewState(
            computeFavoritesState(
                query = query,
                currentContent = state as? FavoritesUiState.Content,
                runIntake = runIntake,
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

    fun toggleFavorite(senseId: String, removedMessage: String, undoLabel: String) {
        val item = findSense(senseId) ?: return
        viewModelScope.launch {
            // Fetch the favorite to get its createdAt before removal
            val favorite = favoritesRepository.getOne(senseId, item.targetLang) ?: return@launch

            // Remove from repository, then remove from displayed list for immediate feedback
            favoritesRepository.remove(senseId, favorite.language)
            Analytics.logEvent(AnalyticsEvent.FAVOURITES_REMOVE)
            val content = state as? FavoritesUiState.Content ?: return@launch
            state = content.copy(senses = content.senses.filter { it.senseId != senseId })

            // Recompute languages and filtered senses from repository (handles query
            // filtering, language switches, and all edge cases correctly)
            val snapshot = state as? FavoritesUiState.Content
            val newState = withContext(Dispatchers.Default) { computeFavoritesState(content.query, snapshot) }
            state = newState
            prefetchSenses(newState.senses.take(PREFETCH_LIMIT))

            // Show snackbar with an undo option
            val result = snackbarHostState.showSnackbar(
                message = removedMessage,
                actionLabel = undoLabel,
                duration = SnackbarDuration.Short
            )

            if (result == SnackbarResult.ActionPerformed) {
                // Re-add with the original createdAt to preserve position
                favoritesRepository.add(senseId, favorite.language, favorite.lemma, favorite.createdAt)
                Analytics.logEvent(AnalyticsEvent.FAVOURITES_SAVE)
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
    wordDetailLabel: String? = null,
    onNavigateToLastWordDetail: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onStartStudy: (Language) -> Unit = {},
) {
    val focusManager = LocalFocusManager.current
    val removedMessage = stringResource(Res.string.favorites_removed_message)
    val undoLabel = stringResource(Res.string.favorites_removed_undo)

    LifecycleResumeEffect(viewModel) {
        viewModel.loadFavorites()
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

    FavoritesScreenContent(
        state = viewModel.state,
        scrollState = viewModel.scrollState,
        snackbarHostState = viewModel.snackbarHostState,
        onNavigateToSearch = onNavigateToSearch,
        onSearchInDictionary = onSearchInDictionary,
        onQueryChange = { viewModel.updateQuery(it) },
        onSenseToggle = { viewModel.toggleSense(it) },
        onFavoriteToggle = { viewModel.toggleFavorite(it, removedMessage, undoLabel) },
        onNavigateToWordDetail = onNavigateToWordDetail,
        wordDetailLabel = wordDetailLabel,
        onNavigateToLastWordDetail = onNavigateToLastWordDetail,
        onNavigateToSettings = onNavigateToSettings,
        onPrefetchVisible = { senses, range -> viewModel.prefetchVisibleRange(senses, range) },
        onLanguageSelected = { viewModel.setSelectedLanguage(it) },
        onSetLanguageDropdownExpanded = { viewModel.setLanguageDropdownExpanded(it) },
        onStartStudy = onStartStudy,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreenContent(
    state: FavoritesUiState,
    scrollState: LazyListState = LazyListState(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onNavigateToSearch: () -> Unit = {},
    onSearchInDictionary: (String) -> Unit = {},
    onQueryChange: (String) -> Unit = {},
    onSenseToggle: (String) -> Unit = {},
    onFavoriteToggle: (String) -> Unit = {},
    onNavigateToWordDetail: (Language, String, String?) -> Unit = { _, _, _ -> },
    wordDetailLabel: String? = null,
    onNavigateToLastWordDetail: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onPrefetchVisible: (List<FavoriteSenseItem>, IntRange) -> Unit = { _, _ -> },
    onLanguageSelected: (Language) -> Unit = {},
    onSetLanguageDropdownExpanded: (Boolean) -> Unit = {},
    onStartStudy: (Language) -> Unit = {},
) {
    val focusManager = LocalFocusManager.current
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
                onNavigateToWordDetail = onNavigateToLastWordDetail,
                wordDetailLabel = wordDetailLabel,
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
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = BiasAlignment(horizontalBias = 0f, verticalBias = -0.3f)
                                ) {
                                    EmptyState(
                                        iconContent = {
                                            Image(
                                                imageVector = SlovyIcons.NoFavsImage,
                                                contentDescription = null,
                                                modifier = Modifier.size(180.dp)
                                            )
                                        },
                                        title = stringResource(Res.string.favorites_empty_title),
                                        description = stringResource(Res.string.favorites_empty_description),
                                        action = {
                                            FilledTonalButton(onClick = onNavigateToSearch) {
                                                Text(stringResource(Res.string.favorites_empty_action_start_searching))
                                            }
                                        }
                                    )
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
                                            FilledTonalButton(
                                                onClick = { onSearchInDictionary(state.query) }
                                            ) {
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
                                            onStartStudy = { onStartStudy(study.language) },
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
            diagnosticInfoOnError = buildDiagnosticInfo(item.senseId, item.createdAt)
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

private val dateFormat = LocalDateTime.Format { date(LocalDate.Formats.ISO); char(' '); time(LocalTime.Formats.ISO) }

private fun buildDiagnosticInfo(senseId: String, createdAt: Long): String {
    val instant = Instant.fromEpochMilliseconds(createdAt)
    val timeZone = currentSystemDefault()
    val localDateTime: LocalDateTime = instant.toLocalDateTime(timeZone)
    val dateStr = localDateTime.format(dateFormat)
    return "ID: $senseId\nAdded: $dateStr"
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
