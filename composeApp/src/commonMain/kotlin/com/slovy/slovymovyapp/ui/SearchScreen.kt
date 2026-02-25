package com.slovy.slovymovyapp.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.Image
import androidx.compose.material3.*
import androidx.compose.material3.ExposedDropdownMenuAnchorType.Companion.PrimaryEditable
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.slovy.slovymovyapp.data.lists.ListsService
import com.slovy.slovymovyapp.data.lists.WordList
import com.slovy.slovymovyapp.data.lists.WordListSense
import com.slovy.slovymovyapp.ui.word.DownloadVector
import com.slovy.slovymovyapp.ui.word.FavoriteAccentColor
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slovy.slovymovyapp.analytics.Analytics
import com.slovy.slovymovyapp.analytics.AnalyticsEvent
import com.slovy.slovymovyapp.analytics.PerformanceMonitoring
import com.slovy.slovymovyapp.analytics.putAttributes
import com.slovy.slovymovyapp.analytics.useWithResult
import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.remote.DictionaryClient
import com.slovy.slovymovyapp.data.remote.DictionaryRepository
import com.slovy.slovymovyapp.data.remote.NetworkErrorClassifier
import com.slovy.slovymovyapp.data.settings.Setting
import com.slovy.slovymovyapp.data.settings.SettingsRepository
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import com.slovy.slovymovyapp.data.remote.PartOfSpeech
import com.slovy.slovymovyapp.ui.components.AppSearchBar
import com.slovy.slovymovyapp.ui.components.EmptyState
import com.slovy.slovymovyapp.ui.icons.NoDictionaryIcon
import com.slovy.slovymovyapp.ui.icons.SearchOtter
import com.slovy.slovymovyapp.ui.icons.SlovyIcons
import com.slovy.slovymovyapp.ui.theme.LocalIsDarkTheme
import com.slovy.slovymovyapp.ui.theme.serifFontFamily
import com.slovy.slovymovyapp.ui.word.Badge
import com.slovy.slovymovyapp.ui.word.colorForLemma
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import slovymovyapp.composeapp.generated.resources.*
import kotlin.uuid.Uuid

data class SearchUiState(
    val query: String,
    val results: List<DictionaryRepository.SearchItem>,
    val showNoResults: Boolean,
    val showLanguageIndicators: Boolean = false,
    val scrollState: LazyListState = LazyListState(),
    val availableLanguages: List<Language> = emptyList(),
    val selectedLanguage: Language? = null,
    val isLanguageDropdownExpanded: Boolean = false,
    val isSuggestionsRefreshing: Boolean = false,
    val wordSuggestions: List<String> = emptyList(),
    val favoriteLemmas: List<String> = emptyList(),
    val curatedLists: List<WordList> = emptyList(),
    // Gates the empty-state body so it renders once fully populated instead of flashing
    // the favorites/"start typing" fallback before curated lists finish loading.
    val isEmptyStateLoading: Boolean = false,
    val listSuggestionDialogVisible: Boolean = false,
    val listSuggestionComment: String = "",
    val listSuggestionEmail: String = "",
    val listSuggestionSubmitting: Boolean = false,
    val listSuggestionError: String? = null,
    val listSuggestionIssueUrl: String? = null
)

class SearchViewModel(
    private val repository: DictionaryRepository,
    private val settingsRepository: SettingsRepository,
    private val listsService: ListsService,
    private val dictionaryClient: DictionaryClient,
) : ViewModel() {

    data class Search(
        val query: String,
        val language: Language? = null,
        val force: Uuid,
        val resetFocus: Boolean = false
    )

    var state by mutableStateOf(
        run {
            val installed = repository.installedDictionaries()
            SearchUiState(
                query = "",
                results = emptyList(),
                showNoResults = false,
                availableLanguages = installed,
                selectedLanguage = installed.firstOrNull(),
                isEmptyStateLoading = installed.isNotEmpty()
            )
        }
    )
        private set

    private val queryFlow = MutableStateFlow(Search("", state.selectedLanguage, Uuid.random()))
    val noDictionaryScrollState = ScrollState(0)
    private var suggestionsInitialized = false
    private var savedSearchLanguage: Language? = null
    private var listsJob: Job? = null

    private val suggestionsLoadedForLanguage = MutableStateFlow<Language?>(null)

    init {
        viewModelScope.launch {
            @OptIn(kotlinx.coroutines.FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
            queryFlow
                .debounce(200.milliseconds) // Wait ms after last keystroke
                .flatMapLatest { queryState ->
                    flow {
                        val query = queryState.query
                        val results = if (query.isEmpty()) {
                            emptyList()
                        } else {
                            PerformanceMonitoring.startTrace("word_search").useWithResult {
                                putAttributes(
                                    mapOf(
                                        "lang" to (queryState.language?.code ?: "any"),
                                    ),
                                )
                                putMetric("query_length", query.length.toLong())
                                repository.search(query, queryState.language).also { searchResults ->
                                    putMetric("result_count", searchResults.size.toLong())
                                    putAttribute("has_results", searchResults.isNotEmpty().toString())
                                }
                            }
                        }
                        emit(results)
                    }.flowOn(Dispatchers.Default) // Run search on background thread
                }
                .collect { results ->
                    val query = queryFlow.value.query
                    if (query.isNotEmpty()) {
                        Analytics.logEvent(
                            AnalyticsEvent.WORD_SEARCH_QUERY,
                            mapOf(
                                "lang" to (queryFlow.value.language?.code ?: ""),
                                "query_length" to query.length.toLong(),
                                "result_count" to results.size.toLong(),
                                "has_results" to (results.isNotEmpty()).toString(),
                            ),
                        )
                    }
                    state = state.copy(
                        results = results,
                        showNoResults = results.isEmpty() && query.isNotEmpty()
                    )
                    // Reset scroll to top when results change
                    if ((results.isNotEmpty() || query.isEmpty()) && queryFlow.value.resetFocus) {
                        state.scrollState.scrollToItem(0)
                    }
                }
        }
        viewModelScope.launch {
            // Restore saved language before loading suggestions so they load for the right language.
            val savedCode = settingsRepository.getById(Setting.Name.SEARCH_LANGUAGE)
                ?.value?.jsonPrimitive?.content
            val savedLang = savedCode?.let { Language.fromCodeOrNull(it) }
            savedSearchLanguage = savedLang
            if (savedLang != null && savedLang in state.availableLanguages && savedLang != state.selectedLanguage) {
                // This bypasses setSelectedLanguage (the preference must not be re-saved),
                // so drop anything the screen-open refresh loaded for the pre-restore
                // language and reload lists for the restored one.
                state = state.copy(
                    selectedLanguage = savedLang,
                    curatedLists = emptyList(),
                    isEmptyStateLoading = true
                )
                queryFlow.value = queryFlow.value.copy(language = savedLang)
                refreshLists()
            }
            loadSuggestionsForCurrentLanguage()
            suggestionsInitialized = true
        }
    }

    private suspend fun loadSuggestionsForCurrentLanguage() {
        val language = state.selectedLanguage ?: return
        try {
            val (suggestions, favorites) = repository.getSearchEmptyStateData(language)
            state = state.copy(wordSuggestions = suggestions, favoriteLemmas = favorites)
        } finally {
            // Mark loaded even on empty/failed results so lists still render afterwards.
            suggestionsLoadedForLanguage.value = language
        }
    }

    fun refreshRecentFavorites() {
        if (!suggestionsInitialized) return
        viewModelScope.launch {
            val language = state.selectedLanguage ?: return@launch
            val favorites = repository.getRecentFavoriteLemmas(language)
            state = state.copy(favoriteLemmas = favorites)
        }
    }

    fun refreshSuggestionsFromPull() {
        // Skip until initial data is loaded and avoid concurrent refresh jobs.
        if (!suggestionsInitialized || state.isSuggestionsRefreshing) return
        viewModelScope.launch {
            state = state.copy(isSuggestionsRefreshing = true)
            try {
                loadSuggestionsForCurrentLanguage()
            } finally {
                state = state.copy(isSuggestionsRefreshing = false)
            }
        }
    }

    fun refreshLists() {
        listsJob?.cancel()
        listsJob = viewModelScope.launch {
            val language = state.selectedLanguage ?: return@launch
            // Serve whatever the DB already holds, then sync with the server and
            // re-read only when new content was actually stored.
            val cached = listsService.getLists(language)
            // Render suggestions for this language first, then lists below them.
            suggestionsLoadedForLanguage.first { it == language }
            if (state.selectedLanguage == language) {
                // Lift the loading gate as soon as we have cached lists so the empty
                // state renders once, fully populated. With an empty cache (first launch)
                // keep waiting through the server sync so the favorites/"start typing"
                // fallback does not flash before lists arrive.
                state = if (cached.isNotEmpty()) {
                    state.copy(curatedLists = cached, isEmptyStateLoading = false)
                } else {
                    state.copy(curatedLists = cached)
                }
            }
            if (listsService.sync(language)) {
                val updated = listsService.getLists(language)
                if (state.selectedLanguage == language) {
                    state = state.copy(curatedLists = updated)
                }
            }
            if (state.selectedLanguage == language) {
                state = state.copy(isEmptyStateLoading = false)
            }
        }
    }

    fun openListSuggestionDialog() {
        state = state.copy(
            listSuggestionDialogVisible = true,
            listSuggestionComment = "",
            listSuggestionEmail = "",
            listSuggestionSubmitting = false,
            listSuggestionError = null,
            listSuggestionIssueUrl = null
        )
    }

    fun dismissListSuggestionDialog() {
        if (state.listSuggestionSubmitting) return
        state = state.copy(
            listSuggestionDialogVisible = false,
            listSuggestionComment = "",
            listSuggestionEmail = "",
            listSuggestionError = null,
            listSuggestionIssueUrl = null
        )
    }

    fun updateListSuggestionComment(comment: String) {
        state = state.copy(listSuggestionComment = comment, listSuggestionError = null)
    }

    fun updateListSuggestionEmail(email: String) {
        state = state.copy(listSuggestionEmail = email)
    }

    fun submitListSuggestion() {
        if (state.listSuggestionSubmitting) return
        val language = state.selectedLanguage ?: return

        val comment = state.listSuggestionComment.trim()
        if (comment.isBlank()) return

        state = state.copy(listSuggestionSubmitting = true, listSuggestionError = null)
        viewModelScope.launch {
            try {
                val response = dictionaryClient.sendListSuggestion(
                    language = language,
                    comment = comment,
                    email = state.listSuggestionEmail.trim().takeIf { it.isNotBlank() }
                )
                state = state.copy(
                    listSuggestionSubmitting = false,
                    listSuggestionError = null,
                    listSuggestionIssueUrl = response.issueUrl
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state = state.copy(
                    listSuggestionSubmitting = false,
                    listSuggestionError = NetworkErrorClassifier.userMessage(e)
                )
            }
        }
    }

    fun updateQuery(newQuery: String) {
        val normalized = newQuery.trim()
        // Update UI state immediately for responsive typing
        state = if (normalized.isEmpty()) {
            state.copy(query = newQuery, results = emptyList(), showNoResults = false)
        } else {
            state.copy(query = newQuery)
        }
        // Trigger debounced search
        queryFlow.value =
            queryFlow.value.copy(
                query = normalized,
                language = state.selectedLanguage,
                force = Uuid.random(),
                resetFocus = true
            )
    }

    fun refreshResults() {
        // Re-trigger search to update favorite status
        val normalized = state.query.trim()
        if (normalized.isNotEmpty()) {
            queryFlow.value = queryFlow.value.copy(query = normalized, force = Uuid.random(), resetFocus = false)
        }
    }

    fun refreshLanguageIndicators() {
        val installed = repository.installedDictionaries()
        val currentLanguage = state.selectedLanguage
        val preferred = savedSearchLanguage?.takeIf { it in installed }
        val target = when {
            currentLanguage in installed && (preferred == null || preferred == currentLanguage) -> currentLanguage
            currentLanguage in installed && preferred != null -> preferred
            else -> preferred ?: installed.firstOrNull()
        }
        val languageChanged = currentLanguage != target
        // Do not call setSelectedLanguage — that would overwrite the saved preference.
        // On a language switch, drop the previous language's lists and re-gate so the
        // empty state shows a spinner (not stale wrong-language lists) until the caller's
        // refreshLists() repopulates. Safe because the only caller follows with refreshLists().
        state = if (languageChanged) {
            state.copy(
                availableLanguages = installed,
                selectedLanguage = target,
                curatedLists = emptyList(),
                isEmptyStateLoading = target != null
            )
        } else {
            state.copy(availableLanguages = installed, selectedLanguage = target)
        }
        queryFlow.value = queryFlow.value.copy(language = target)
        if (languageChanged) {
            viewModelScope.launch { loadSuggestionsForCurrentLanguage() }
        }
    }

    fun setSelectedLanguage(language: Language?) {
        val langChanged = state.selectedLanguage != language
        state = state.copy(selectedLanguage = language)
        savedSearchLanguage = language
        viewModelScope.launch {
            if (language != null) {
                settingsRepository.insert(Setting(Setting.Name.SEARCH_LANGUAGE, JsonPrimitive(language.code)))
            } else {
                settingsRepository.deleteById(Setting.Name.SEARCH_LANGUAGE)
            }
            Analytics.logEvent(
                AnalyticsEvent.SETTING_CHANGED,
                mapOf("setting" to "search_language", "value" to (language?.code ?: "any")),
            )
        }
        // Re-trigger search with new language filter
        val normalized = state.query.trim()
        if (normalized.isNotEmpty()) {
            queryFlow.value = queryFlow.value.copy(
                query = normalized,
                language = language,
                force = Uuid.random(),
                resetFocus = langChanged
            )
        }
        // Load suggestions and lists for new language
        if (langChanged) {
            // No per-language lists in "All languages" mode, so don't gate on loading there
            // (refreshLists/loadSuggestions early-return for a null language).
            state = state.copy(curatedLists = emptyList(), isEmptyStateLoading = language != null)
            viewModelScope.launch { loadSuggestionsForCurrentLanguage() }
            refreshLists()
        }
    }

    fun setLanguageDropdownExpanded(expanded: Boolean) {
        state = state.copy(isLanguageDropdownExpanded = expanded)
    }

    fun setShowLanguageIndicators(show: Boolean) {
        state = state.copy(showLanguageIndicators = show)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onWordSelected: (DictionaryRepository.SearchItem) -> Unit = { _ -> },
    onSuggestionSelected: (language: Language, lemma: String) -> Unit = { _, _ -> },
    onNavigateToFavorites: () -> Unit = {},
    onNavigateToStats: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToTextReader: (Language) -> Unit = {},
    hasFavoritesToReview: Boolean = false,
    onListClick: (WordList) -> Unit = {},
) {
    val focusManager = LocalFocusManager.current
    // restore after process death
    val savedQuery = rememberSaveable { viewModel.state.query }

    // Refresh language indicators, recent favorites, search results, and lists when screen is opened
    LaunchedEffect(Unit) {
        viewModel.refreshLanguageIndicators()
        viewModel.refreshRecentFavorites()
        viewModel.refreshResults()
        viewModel.refreshLists()
    }

    LaunchedEffect(savedQuery) {
        if (viewModel.state.query.isBlank() && savedQuery.isNotBlank()) {
            viewModel.updateQuery(savedQuery)
        }
    }

    // Clear focus when scrolling starts
    LaunchedEffect(viewModel.state.scrollState.isScrollInProgress) {
        if (viewModel.state.scrollState.isScrollInProgress) {
            focusManager.clearFocus()
        }
    }

    val isNoDictionaryState = viewModel.state.availableLanguages.isEmpty()
    LaunchedEffect(isNoDictionaryState) {
        if (isNoDictionaryState) {
            viewModel.noDictionaryScrollState.scrollTo(0)
        }
    }

    SearchScreenContent(
        state = viewModel.state,
        noDictionaryScrollState = viewModel.noDictionaryScrollState,
        onQueryChange = { viewModel.updateQuery(it) },
        onResultSelected = { item ->
            focusManager.clearFocus()
            Analytics.logEvent(AnalyticsEvent.WORD_SEARCH_RESULT_SHOW)
            onWordSelected(item)
        },
        onSuggestionSelected = { word ->
            focusManager.clearFocus()
            viewModel.state.selectedLanguage?.let { language ->
                Analytics.logEvent(AnalyticsEvent.WORD_SEARCH_SUGGESTION_CLICK)
                onSuggestionSelected(language, word)
            }
        },
        onLanguageSelected = { language ->
            viewModel.setShowLanguageIndicators(language == null && viewModel.state.availableLanguages.size > 1)
            viewModel.setSelectedLanguage(language)
            // TODO: search is not relaunched or query
        },
        onSetLanguageDropdownExpanded = { viewModel.setLanguageDropdownExpanded(it) },
        onRefreshSuggestions = { viewModel.refreshSuggestionsFromPull() },
        onListClick = onListClick,
        onSuggestListClick = { viewModel.openListSuggestionDialog() },
        onListSuggestionCommentChange = { viewModel.updateListSuggestionComment(it) },
        onListSuggestionEmailChange = { viewModel.updateListSuggestionEmail(it) },
        onDismissListSuggestion = { viewModel.dismissListSuggestionDialog() },
        onSubmitListSuggestion = { viewModel.submitListSuggestion() },
        onNavigateToFavorites = onNavigateToFavorites,
        onNavigateToStats = onNavigateToStats,
        onNavigateToSettings = onNavigateToSettings,
        onNavigateToTextReader = { viewModel.state.selectedLanguage?.let { onNavigateToTextReader(it) } },
        hasFavoritesToReview = hasFavoritesToReview,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreenContent(
    state: SearchUiState,
    noDictionaryScrollState: ScrollState? = null,
    onQueryChange: (String) -> Unit = {},
    onResultSelected: (DictionaryRepository.SearchItem) -> Unit = {},
    onSuggestionSelected: (String) -> Unit = {},
    onLanguageSelected: (Language?) -> Unit = {},
    onSetLanguageDropdownExpanded: (Boolean) -> Unit = {},
    onRefreshSuggestions: () -> Unit = {},
    onListClick: (WordList) -> Unit = {},
    onSuggestListClick: () -> Unit = {},
    onListSuggestionCommentChange: (String) -> Unit = {},
    onListSuggestionEmailChange: (String) -> Unit = {},
    onDismissListSuggestion: () -> Unit = {},
    onSubmitListSuggestion: () -> Unit = {},
    onNavigateToFavorites: () -> Unit = {},
    onNavigateToStats: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToTextReader: () -> Unit = {},
    hasFavoritesToReview: Boolean = false,
) {
    val focusManager = LocalFocusManager.current
    val resolvedNoDictionaryScrollState = noDictionaryScrollState ?: remember { ScrollState(0) }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            AppNavigationBar(
                currentScreen = AppScreen.SEARCH,
                onNavigateToSearch = {},
                onNavigateToFavorites = onNavigateToFavorites,
                onNavigateToStats = onNavigateToStats,
                onNavigateToSettings = onNavigateToSettings,
                hasFavoritesToReview = hasFavoritesToReview,
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
                // Search field with language dropdown on the same row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Search field
                    AppSearchBar(
                        query = state.query,
                        onQueryChange = onQueryChange,
                        modifier = Modifier.weight(1f),
                        placeholder = stringResource(Res.string.search_placeholder)
                    )

                    // Language filter dropdown
                    if (state.availableLanguages.isNotEmpty() && state.availableLanguages.size > 1) {
                        val currentLanguage = state.selectedLanguage ?: state.availableLanguages.firstOrNull()

                        ExposedDropdownMenuBox(
                            expanded = state.isLanguageDropdownExpanded,
                            onExpandedChange = onSetLanguageDropdownExpanded
                        ) {
                            Surface(
                                modifier = Modifier
                                    .menuAnchor(PrimaryEditable)
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

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = {
                                focusManager.clearFocus()
                            })
                        }
                ) {
                    // Result area
                    when {
                        state.availableLanguages.isEmpty() -> {
                            NoDictionaryState(
                                scrollState = resolvedNoDictionaryScrollState,
                                onNavigateToSettings = onNavigateToSettings
                            )
                        }

                        state.query.isBlank() -> {
                            PullToRefreshBox(
                                modifier = Modifier.fillMaxSize(),
                                isRefreshing = state.isSuggestionsRefreshing,
                                onRefresh = onRefreshSuggestions
                            ) {
                                EmptySearchState(
                                    wordSuggestions = state.wordSuggestions,
                                    favoriteLemmas = state.favoriteLemmas,
                                    curatedLists = state.curatedLists,
                                    isLoading = state.isEmptyStateLoading,
                                    onWordClick = onSuggestionSelected,
                                    onListClick = onListClick,
                                    onSuggestListClick = onSuggestListClick,
                                    onNavigateToTextReader = onNavigateToTextReader
                                )
                            }
                        }

                        state.showNoResults -> {
                            NoResultsState(query = state.query)
                        }

                        else -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(16.dp),
                                state = state.scrollState
                            ) {
                                items(state.results) { item ->
                                    SearchResultCard(
                                        item = item,
                                        showLanguageIndicator = state.showLanguageIndicators,
                                        onClick = { onResultSelected(item) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

    if (state.listSuggestionDialogVisible) {
        FeedbackDialog(
            title = stringResource(Res.string.search_suggest_list_dialog_title),
            commentPlaceholder = stringResource(Res.string.search_suggest_list_placeholder),
            commentLabel = stringResource(Res.string.feedback_dialog_comment_label),
            successCopy = null,
            allowDismissWhileSending = false,
            comment = state.listSuggestionComment,
            email = state.listSuggestionEmail,
            isSending = state.listSuggestionSubmitting,
            error = state.listSuggestionError,
            resultUrl = state.listSuggestionIssueUrl,
            onCommentChange = onListSuggestionCommentChange,
            onEmailChange = onListSuggestionEmailChange,
            onDismiss = onDismissListSuggestion,
            onSend = onSubmitListSuggestion
        )
    }
}

@Composable
private fun SearchResultCard(
    item: DictionaryRepository.SearchItem,
    showLanguageIndicator: Boolean = false,
    onClick: () -> Unit
) {
    val containerColor = colorForLemma(item.lemma, MaterialTheme.colorScheme.surface, LocalIsDarkTheme.current)
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { role = Role.Button }
            .clickable(
                onClickLabel = stringResource(Res.string.search_open_item, item.display),
                onClick = onClick
            ),
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.outlinedCardColors(containerColor = containerColor),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = item.display,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = MaterialTheme.serifFontFamily
                ),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showLanguageIndicator) {
                    Badge(
                        text = item.language.selfName,
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        shape = RoundedCornerShape(4.dp)
                    )
                }

                if (item.isFavorite) {
                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = stringResource(Res.string.search_content_desc_favorite),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
                if (item.onlineOnly) {
                    Icon(
                        imageVector = DownloadVector,
                        contentDescription = stringResource(Res.string.search_content_desc_not_downloaded),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmptySearchState(
    wordSuggestions: List<String>,
    favoriteLemmas: List<String>,
    curatedLists: List<WordList>,
    isLoading: Boolean,
    onWordClick: (String) -> Unit,
    onListClick: (WordList) -> Unit,
    onSuggestListClick: () -> Unit,
    onNavigateToTextReader: () -> Unit = {}
) {
    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }
    val isDark = LocalIsDarkTheme.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 4.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (wordSuggestions.isNotEmpty()) {
            Text(
                text = stringResource(Res.string.search_section_explore).uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.6.sp,
                    fontSize = 10.5.sp,
                ),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
            )
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                wordSuggestions.forEach { lemma ->
                    WordChip(lemma = lemma, onClick = { onWordClick(lemma) })
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(Res.string.search_pull_to_refresh_hint),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.5.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (curatedLists.isNotEmpty()) {
            Text(
                text = stringResource(Res.string.search_section_lists_for_you).uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.6.sp,
                    fontSize = 10.5.sp,
                ),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(
                    top = if (wordSuggestions.isNotEmpty()) 12.dp else 8.dp,
                    bottom = 2.dp
                )
            )
            curatedLists.forEachIndexed { index, list ->
                ListCard(
                    list = list,
                    featured = index == 0,
                    isDark = isDark,
                    onClick = { onListClick(list) }
                )
            }
            TextButton(
                onClick = onSuggestListClick,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 4.dp),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Icon(
                    imageVector = Icons.Outlined.Flag,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(Res.string.search_suggest_list_button),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else if (favoriteLemmas.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = if (wordSuggestions.isNotEmpty()) 12.dp else 8.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = null,
                    tint = FavoriteAccentColor,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = stringResource(Res.string.search_section_recently_saved).uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            favoriteLemmas.forEach { lemma ->
                SuggestionCard(lemma = lemma, onClick = { onWordClick(lemma) })
            }
        }

        if (wordSuggestions.isEmpty() && curatedLists.isEmpty() && favoriteLemmas.isEmpty()) {
            Text(
                text = stringResource(Res.string.search_empty_start_typing),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp),
                textAlign = TextAlign.Center
            )
        }

        FilledTonalButton(onClick = onNavigateToTextReader) {
            Text("Paste your text")
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun ListCard(
    list: WordList,
    featured: Boolean,
    isDark: Boolean,
    onClick: () -> Unit
) {
    val localeCode = Locale.current.language
    val title = list.title[localeCode] ?: list.title["en"] ?: list.id
    val subtitle = list.subtitle[localeCode] ?: list.subtitle["en"] ?: ""
    val badge = list.labels[localeCode]?.firstOrNull() ?: list.labels["en"]?.firstOrNull()
    val wordCount = list.senseIds.size
    // The list's stable brand color; it now lives only in the icon and accents, while the
    // card field becomes a quiet wash of the same hue.
    val hue = listHue(list.id)
    val fieldColor = if (isDark) {
        mixColors(over = hue, base = MaterialTheme.colorScheme.surfaceContainer, overFraction = 0.09f)
    } else {
        mixColors(over = hue, base = MaterialTheme.colorScheme.background, overFraction = 0.09f)
    }
    val iconColor = if (isDark) {
        mixColors(over = hue, base = Color(0xFFFFFFFF), overFraction = 0.55f)
    } else {
        mixColors(over = hue, base = Color(0xFF2D2620), overFraction = 0.80f)
    }
    // Contrast-safe accent for the small (10sp) badge text: a darkened hue on the pale
    // light field, a lightened hue on the dark field — keeps the brand tint while passing
    // AA. The vivid [iconColor] is fine for the 38dp icon but too light/dark for small text.
    val badgeTextColor = if (isDark) {
        mixColors(over = Color(0xFFFFFFFF), base = hue, overFraction = 0.55f)
    } else {
        mixColors(over = Color(0xFF2D2620), base = hue, overFraction = 0.55f)
    }
    val titleFontSize = if (featured) 18.sp else 17.sp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(fieldColor)
            .border(
                0.5.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                RoundedCornerShape(16.dp)
            )
            .clickable(onClickLabel = title, onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (badge != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(hue.copy(alpha = 0.14f))
                            .border(0.5.dp, hue.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 9.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = badge.uppercase(),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.4.sp,
                            color = badgeTextColor,
                        )
                    }
                }
                Text(
                    text = title,
                    fontSize = titleFontSize,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp,
                    lineHeight = (titleFontSize.value * 1.2f).sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        fontSize = 13.sp,
                        fontStyle = FontStyle.Italic,
                        fontFamily = MaterialTheme.serifFontFamily,
                        lineHeight = (13f * 1.38f).sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = pluralStringResource(Res.plurals.search_list_word_count, wordCount, wordCount),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }

            WordListIcon(
                iconSvg = list.iconSvg,
                fgColor = iconColor,
                modifier = Modifier.size(38.dp),
            )
        }
    }
}

/**
 * The list's stable saturated brand hue (terracotta, teal, …), derived from the same
 * id→color assignment as [vibrantColorsForList] so a list keeps its color across the
 * redesign. Reused as the icon tint and badge accent over the calm card field.
 */
internal fun listHue(id: String): Color = vibrantColorsForList(id, isDark = false).first

/** Linear per-channel blend of [overFraction] of [over] onto [base]. */
private fun mixColors(over: Color, base: Color, overFraction: Float): Color = Color(
    red = base.red * (1f - overFraction) + over.red * overFraction,
    green = base.green * (1f - overFraction) + over.green * overFraction,
    blue = base.blue * (1f - overFraction) + over.blue * overFraction,
)

internal fun vibrantColorsForList(id: String, isDark: Boolean): Pair<Color, Color> {
    var h = 0L
    for (i in id.indices) {
        h = (h + id[i].code.toLong() * (i + 1)) and 0xFFFFFFFFL
    }
    // light (bg, fg) to dark (bg, fg) — 8 slots from the design palette
    val slots = listOf(
        Pair(Color(0xFFC46A3D), Color(0xFFFFF0E8)) to Pair(Color(0xFFA55530), Color(0xFFFFE4D4)), // 0 Terracotta
        Pair(Color(0xFFD4A03A), Color(0xFFFFF6EE)) to Pair(Color(0xFFA87A1E), Color(0xFFFFF0DC)), // 1 Mustard
        Pair(Color(0xFF3D7A7A), Color(0xFFE8F5F5)) to Pair(Color(0xFF2E5E5E), Color(0xFFD0ECEC)), // 2 Teal
        Pair(Color(0xFF7A4A6E), Color(0xFFF5E8F2)) to Pair(Color(0xFF5C3854), Color(0xFFEDD6E8)), // 3 Plum
        Pair(Color(0xFF6E7C3D), Color(0xFFF0F5E0)) to Pair(Color(0xFF525C2E), Color(0xFFE4EED0)), // 4 Olive
        Pair(Color(0xFFA04A28), Color(0xFFFFF1E8)) to Pair(Color(0xFF7A3A20), Color(0xFFFFE4D4)), // 5 Rust
        Pair(Color(0xFF8B6E7C), Color(0xFFFBF4F8)) to Pair(Color(0xFF6A5360), Color(0xFFF2E2EA)), // 6 Mauve
        Pair(Color(0xFF3A4A5C), Color(0xFFE8EEF5)) to Pair(Color(0xFF2A3848), Color(0xFFD4DCE8)), // 7 Ink
    )
    val (light, dark) = slots[(h % 8L).toInt()]
    return if (isDark) dark else light
}

@Composable
private fun WordChip(lemma: String, onClick: () -> Unit) {
    // Single quiet sage tint for every chip — one color reads as "just words". The border
    // does the heavy lifting so the edge survives on both the cream and near-black surfaces.
    val isDark = LocalIsDarkTheme.current
    val bgColor = if (isDark) Color(0xFF2A3326) else Color(0xFFE6EBDD)
    val lineColor = if (isDark) Color(0xFF3A4636) else Color(0xFFD2DCC4)
    val textColor = if (isDark) Color(0xFFD4DEC8) else Color(0xFF2D2620)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(bgColor)
            .border(0.5.dp, lineColor, RoundedCornerShape(9.dp))
            .semantics { role = Role.Button }
            .clickable(
                onClickLabel = stringResource(Res.string.search_open_item, lemma),
                onClick = onClick
            )
            // Taller vertical padding lifts the tap target without the empty centering gap
            // that a reserved 48dp slot leaves between wrapped rows.
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            text = lemma,
            fontFamily = MaterialTheme.serifFontFamily,
            fontSize = 15.sp,
            lineHeight = 17.25.sp,
            letterSpacing = (-0.1).sp,
            color = textColor
        )
    }
}

@Composable
private fun SuggestionCard(lemma: String, onClick: () -> Unit) {
    val containerColor = colorForLemma(lemma, MaterialTheme.colorScheme.surface, LocalIsDarkTheme.current)
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { role = Role.Button }
            .clickable(
                onClickLabel = stringResource(Res.string.search_open_item, lemma),
                onClick = onClick
            ),
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.outlinedCardColors(containerColor = containerColor),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Text(
            text = lemma,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontFamily = MaterialTheme.serifFontFamily
            ),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        )
    }
}

@Composable
private fun NoDictionaryState(
    scrollState: ScrollState,
    onNavigateToSettings: () -> Unit
) {
    ScrollableNoDictionaryContainer(
        scrollState = scrollState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                imageVector = SlovyIcons.NoDictionaryIcon,
                contentDescription = null,
                modifier = Modifier.size(204.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = stringResource(Res.string.search_no_dictionary_title),
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
                text = stringResource(Res.string.search_no_dictionary_description),
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = MaterialTheme.serifFontFamily,
                    fontStyle = FontStyle.Italic,
                    lineHeight = 24.sp,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 320.dp)
            )

            Spacer(modifier = Modifier.height(36.dp))

            Button(
                onClick = onNavigateToSettings,
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
                    imageVector = Icons.Filled.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(Res.string.search_go_to_settings),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
            }
        }
    }
}

@Composable
private fun ScrollableNoDictionaryContainer(
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
private fun NoResultsState(query: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
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
            title = stringResource(Res.string.search_no_results_title, query),
            description = stringResource(Res.string.search_no_results_description)
        )
    }
}

@Preview
@Composable
private fun SearchScreenPreviewEmptyQuery(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        SearchScreenContent(
            state = SearchUiState(
                query = "",
                results = emptyList(),
                showNoResults = false,
                availableLanguages = listOf(Language.ENGLISH, Language.RUSSIAN),
                selectedLanguage = Language.ENGLISH,
                wordSuggestions = listOf("the", "be", "to", "of", "and"),
                favoriteLemmas = listOf("world", "time", "love")
            ),
        )
    }
}

@Preview
@Composable
private fun SearchScreenPreviewWithLists(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        SearchScreenContent(
            state = SearchUiState(
                query = "",
                results = emptyList(),
                showNoResults = false,
                availableLanguages = listOf(Language.DUTCH),
                selectedLanguage = Language.DUTCH,
                wordSuggestions = listOf("de", "het", "een", "zijn", "hebben", "gezelligheid", "fiets"),
                curatedLists = listOf(
                    WordList(
                        id = "nl_a1_basic",
                        title = mapOf("en" to "500 first Dutch words", "nl" to "500 eerste Nederlandse woorden"),
                        subtitle = mapOf("en" to "This is where your journey begins", "nl" to "Hier begint jouw reis"),
                        labels = mapOf("en" to listOf("A1", "Basic"), "nl" to listOf("A1", "Basis")),
                        senses = List(500) { WordListSense(senseId = it.toString(), lemma = "woord$it", language = Language.DUTCH) },
                        iconSvg = null,
                    ),
                    WordList(
                        id = "nl_doctor",
                        title = mapOf("en" to "At the doctor's", "nl" to "Bij de huisarts"),
                        subtitle = mapOf("en" to "Words for your next appointment", "nl" to "Woorden voor je afspraak"),
                        labels = mapOf("en" to listOf("A2")),
                        senses = List(64) { WordListSense(senseId = it.toString(), lemma = "woord$it", language = Language.DUTCH) },
                        iconSvg = null,
                    ),
                    WordList(
                        id = "nl_digital_life",
                        title = mapOf("en" to "Digital life", "nl" to "Digitaal leven"),
                        subtitle = mapOf("en" to "Phones, apps and the web", "nl" to "Telefoons, apps en het web"),
                        labels = emptyMap(),
                        senses = List(120) { WordListSense(senseId = it.toString(), lemma = "woord$it", language = Language.DUTCH) },
                        iconSvg = null,
                    )
                )
            ),
        )
    }
}

@Preview
@Composable
private fun SearchScreenPreviewNoDictionary(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        SearchScreenContent(
            state = SearchUiState(
                query = "",
                results = emptyList(),
                showNoResults = false,
                availableLanguages = emptyList(),
                selectedLanguage = null
            ),
        )
    }
}

@Preview
@Composable
private fun SearchScreenPreviewWithResults(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        SearchScreenContent(
            state = SearchUiState(
                query = "cel",
                results = listOf(
                    DictionaryRepository.SearchItem(
                        language = Language.ENGLISH,
                        lemmaId = Uuid.parse("00000000-0000-0000-0000-000000000001"),
                        lemma = "celebration",
                        display = "celebration",
                        zipfFrequency = 4.5f,
                        pos = listOf(PartOfSpeech.NOUN),
                        isFavorite = true,
                        onlineOnly = false
                    ),
                    DictionaryRepository.SearchItem(
                        language = Language.ENGLISH,
                        lemmaId = Uuid.parse("00000000-0000-0000-0000-000000000002"),
                        lemma = "celebrity",
                        display = "celebrity",
                        zipfFrequency = 4.3f,
                        pos = listOf(PartOfSpeech.NOUN),
                        onlineOnly = false
                    ),
                    DictionaryRepository.SearchItem(
                        language = Language.ENGLISH,
                        lemmaId = Uuid.parse("00000000-0000-0000-0000-000000000003"),
                        lemma = "celestial",
                        display = "celestial",
                        zipfFrequency = 3.8f,
                        pos = listOf(PartOfSpeech.ADJECTIVE),
                        onlineOnly = true
                    ),
                    DictionaryRepository.SearchItem(
                        language = Language.ENGLISH,
                        lemmaId = Uuid.parse("00000000-0000-0000-0000-000000000004"),
                        lemma = "cell",
                        display = "cell",
                        zipfFrequency = 5.2f,
                        pos = listOf(PartOfSpeech.NOUN),
                        isFavorite = true,
                        onlineOnly = false
                    )
                ),
                showNoResults = false,
                availableLanguages = listOf(Language.ENGLISH, Language.RUSSIAN),
                selectedLanguage = Language.ENGLISH
            ),
        )
    }
}

@Preview
@Composable
private fun SearchScreenPreviewMultilingualResults(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        SearchScreenContent(
            state = SearchUiState(
                query = "program",
                results = listOf(
                    DictionaryRepository.SearchItem(
                        language = Language.ENGLISH,
                        lemmaId = Uuid.parse("00000000-0000-0000-0000-000000000001"),
                        lemma = "program",
                        display = "program",
                        zipfFrequency = 5.5f,
                        pos = listOf(PartOfSpeech.NOUN, PartOfSpeech.VERB),
                        isFavorite = true,
                        onlineOnly = false
                    ),
                    DictionaryRepository.SearchItem(
                        language = Language.ENGLISH,
                        lemmaId = Uuid.parse("00000000-0000-0000-0000-000000000002"),
                        lemma = "programmatically",
                        display = "programmatically",
                        zipfFrequency = 3.2f,
                        pos = listOf(PartOfSpeech.ADVERB),
                        onlineOnly = false
                    ),
                    DictionaryRepository.SearchItem(
                        language = Language.RUSSIAN,
                        lemmaId = Uuid.parse("00000000-0000-0000-0000-000000000003"),
                        lemma = "программа",
                        display = "программа",
                        zipfFrequency = 5.8f,
                        pos = listOf(PartOfSpeech.NOUN),
                        isFavorite = true,
                        onlineOnly = false
                    ),
                    DictionaryRepository.SearchItem(
                        language = Language.POLISH,
                        lemmaId = Uuid.parse("00000000-0000-0000-0000-000000000004"),
                        lemma = "program",
                        display = "program",
                        zipfFrequency = 5.4f,
                        pos = listOf(PartOfSpeech.NOUN),
                        onlineOnly = true
                    )
                ),
                showNoResults = false,
                showLanguageIndicators = true, // Multiple dictionaries - show language badges
                availableLanguages = listOf(Language.ENGLISH, Language.RUSSIAN, Language.POLISH)
            ),
        )
    }
}

@Preview
@Composable
private fun SearchScreenPreviewNoResults(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        SearchScreenContent(
            state = SearchUiState(
                query = "xyzabc123",
                results = emptyList(),
                showNoResults = true,
                availableLanguages = listOf(Language.ENGLISH)
            ),
        )
    }
}

@Preview
@Composable
private fun SearchScreenPreviewInfoDialog(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        SearchScreenContent(
            state = SearchUiState(
                query = "world",
                results = listOf(
                    DictionaryRepository.SearchItem(
                        language = Language.ENGLISH,
                        lemmaId = Uuid.parse("00000000-0000-0000-0000-000000000001"),
                        lemma = "world",
                        display = "world",
                        zipfFrequency = 6.2f,
                        pos = listOf(PartOfSpeech.NOUN),
                        isFavorite = true,
                        onlineOnly = false
                    )
                ),
                showNoResults = false,
                availableLanguages = listOf(Language.ENGLISH)
            ),
        )
    }
}

@Preview
@Composable
private fun SearchScreenPreviewSingleLanguage(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        SearchScreenContent(
            state = SearchUiState(
                query = "bib",
                results = listOf(
                    DictionaryRepository.SearchItem(
                        language = Language.DUTCH,
                        lemmaId = Uuid.parse("00000000-0000-0000-0000-000000000001"),
                        lemma = "bibliotheek",
                        display = "bibliotheek",
                        zipfFrequency = 4.1f,
                        pos = listOf(PartOfSpeech.NOUN),
                        onlineOnly = false
                    ),
                    DictionaryRepository.SearchItem(
                        language = Language.DUTCH,
                        lemmaId = Uuid.parse("00000000-0000-0000-0000-000000000002"),
                        lemma = "bijbel",
                        display = "bijbel",
                        zipfFrequency = 4.8f,
                        pos = listOf(PartOfSpeech.NOUN),
                        onlineOnly = false
                    )
                ),
                showNoResults = false,
                showLanguageIndicators = false, // Single dictionary - no language badges
                availableLanguages = listOf(Language.DUTCH)
            ),
        )
    }
}

@Preview
@Composable
private fun SearchScreenPreviewMultilingualWithoutPOS(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        SearchScreenContent(
            state = SearchUiState(
                query = "word",
                results = listOf(
                    DictionaryRepository.SearchItem(
                        language = Language.ENGLISH,
                        lemmaId = Uuid.parse("00000000-0000-0000-0000-000000000001"),
                        lemma = "word",
                        display = "word",
                        zipfFrequency = 6.1f,
                        pos = emptyList(),
                        isFavorite = true,
                        onlineOnly = false
                    ),
                    DictionaryRepository.SearchItem(
                        language = Language.RUSSIAN,
                        lemmaId = Uuid.parse("00000000-0000-0000-0000-000000000002"),
                        lemma = "ворд",
                        display = "ворд",
                        zipfFrequency = 3.5f,
                        pos = emptyList(),
                        onlineOnly = false
                    ),
                    DictionaryRepository.SearchItem(
                        language = Language.POLISH,
                        lemmaId = Uuid.parse("00000000-0000-0000-0000-000000000003"),
                        lemma = "wyraz",
                        display = "wyraz",
                        zipfFrequency = 4.2f,
                        pos = listOf(PartOfSpeech.NOUN),
                        onlineOnly = false
                    )
                ),
                showNoResults = false,
                showLanguageIndicators = true, // Multiple dictionaries - language badges shown even without POS
                availableLanguages = listOf(Language.ENGLISH, Language.RUSSIAN, Language.POLISH)
            ),
        )
    }
}

@Preview
@Composable
private fun SearchScreenPreviewMixedLanguagesAndForms(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        SearchScreenContent(
            state = SearchUiState(
                query = "run",
                results = listOf(
                    DictionaryRepository.SearchItem(
                        language = Language.ENGLISH,
                        lemmaId = Uuid.parse("00000000-0000-0000-0000-000000000001"),
                        lemma = "run",
                        display = "run",
                        zipfFrequency = 6.3f,
                        pos = listOf(PartOfSpeech.VERB, PartOfSpeech.NOUN),
                        isFavorite = true,
                        onlineOnly = false
                    ),
                    DictionaryRepository.SearchItem(
                        language = Language.ENGLISH,
                        lemmaId = Uuid.parse("00000000-0000-0000-0000-000000000002"),
                        lemma = "run",
                        display = "\"running\" form of \"run\"",
                        zipfFrequency = 5.8f,
                        pos = emptyList(),
                        onlineOnly = false
                    ),
                    DictionaryRepository.SearchItem(
                        language = Language.DUTCH,
                        lemmaId = Uuid.parse("00000000-0000-0000-0000-000000000003"),
                        lemma = "rennen",
                        display = "rennen",
                        zipfFrequency = 4.9f,
                        pos = listOf(PartOfSpeech.VERB),
                        onlineOnly = false
                    ),
                    DictionaryRepository.SearchItem(
                        language = Language.DUTCH,
                        lemmaId = Uuid.parse("00000000-0000-0000-0000-000000000004"),
                        lemma = "rund",
                        display = "rund",
                        zipfFrequency = 3.7f,
                        pos = listOf(PartOfSpeech.NOUN),
                        isFavorite = true,
                        onlineOnly = false
                    )
                ),
                showNoResults = false,
                showLanguageIndicators = true,
                availableLanguages = listOf(Language.ENGLISH, Language.DUTCH)
            ),
        )
    }
}
