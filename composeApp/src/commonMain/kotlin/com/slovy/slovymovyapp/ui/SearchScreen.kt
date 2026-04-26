package com.slovy.slovymovyapp.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.foundation.Image
import androidx.compose.material3.*
import androidx.compose.material3.ExposedDropdownMenuAnchorType.Companion.PrimaryEditable
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.slovy.slovymovyapp.ui.word.DownloadVector
import com.slovy.slovymovyapp.ui.word.FavoriteAccentColor
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slovy.slovymovyapp.analytics.Analytics
import com.slovy.slovymovyapp.analytics.AnalyticsEvent
import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.remote.DictionaryRepository
import com.slovy.slovymovyapp.data.remote.PartOfSpeech
import com.slovy.slovymovyapp.ui.components.AppSearchBar
import com.slovy.slovymovyapp.ui.components.EmptyState
import com.slovy.slovymovyapp.ui.icons.SearchOtter
import com.slovy.slovymovyapp.ui.icons.SlovyIcons
import com.slovy.slovymovyapp.ui.theme.serifFontFamily
import com.slovy.slovymovyapp.ui.word.Badge
import com.slovy.slovymovyapp.ui.word.colorForLemma
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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
    val favoriteLemmas: List<String> = emptyList()
)

class SearchViewModel(
    private val repository: DictionaryRepository
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
                selectedLanguage = installed.firstOrNull()
            )
        }
    )
        private set

    private val queryFlow = MutableStateFlow(Search("", state.selectedLanguage, Uuid.random()))
    private var suggestionsInitialized = false

    init {
        viewModelScope.launch {
            @OptIn(kotlinx.coroutines.FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
            queryFlow
                .debounce(200) // Wait ms after last keystroke
                .flatMapLatest { queryState ->
                    flow {
                        val query = queryState.query
                        val results = if (query.isEmpty()) {
                            emptyList()
                        } else {
                            repository.search(query, queryState.language)
                        }
                        emit(results)
                    }.flowOn(Dispatchers.Default) // Run search on background thread
                }
                .collect { results ->
                    val query = queryFlow.value.query
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
        // Load initial suggestions
        viewModelScope.launch {
            loadSuggestionsForCurrentLanguage()
            suggestionsInitialized = true
        }
    }

    private suspend fun loadSuggestionsForCurrentLanguage() {
        val language = state.selectedLanguage ?: return
        val (suggestions, favorites) = repository.getSearchEmptyStateData(language)
        state = state.copy(wordSuggestions = suggestions, favoriteLemmas = favorites)
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

    fun updateQuery(newQuery: String) {
        val trimmed = newQuery.trim()
        // Update UI state immediately for responsive typing
        state = if (trimmed.isEmpty()) {
            state.copy(query = trimmed, results = emptyList(), showNoResults = false)
        } else {
            state.copy(query = trimmed)
        }
        // Trigger debounced search
        queryFlow.value =
            queryFlow.value.copy(
                query = trimmed,
                language = state.selectedLanguage,
                force = Uuid.random(),
                resetFocus = true
            )
    }

    fun refreshResults() {
        // Re-trigger search to update favorite status
        if (state.query.isNotEmpty()) {
            queryFlow.value = queryFlow.value.copy(force = Uuid.random(), resetFocus = false)
        }
    }

    fun refreshLanguageIndicators() {
        val installed = repository.installedDictionaries()
        state = state.copy(
            availableLanguages = installed
        )
        if (state.selectedLanguage !in state.availableLanguages) {
            setSelectedLanguage(state.availableLanguages.firstOrNull())
        }
    }

    fun setSelectedLanguage(language: Language?) {
        val langChanged = state.selectedLanguage != language
        state = state.copy(selectedLanguage = language)
        // Re-trigger search with new language filter
        if (state.query.isNotEmpty()) {
            queryFlow.value = queryFlow.value.copy(language = language, force = Uuid.random(), resetFocus = langChanged)
        }
        // Load suggestions for new language
        if (langChanged) {
            viewModelScope.launch {
                loadSuggestionsForCurrentLanguage()
            }
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
    wordDetailLabel: String? = null,
    onNavigateToWordDetail: () -> Unit = {},
    onNavigateToFavorites: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {}
) {
    val focusManager = LocalFocusManager.current
    // restore after process death
    val savedQuery = rememberSaveable { viewModel.state.query }

    // Refresh language indicators, recent favorites, and search results when screen is opened
    LaunchedEffect(Unit) {
        viewModel.refreshLanguageIndicators()
        viewModel.refreshRecentFavorites()
        viewModel.refreshResults()
    }

    LaunchedEffect(savedQuery) {
        if (viewModel.state.query.isEmpty() && savedQuery.isNotEmpty()) {
            viewModel.updateQuery(savedQuery)
        }
    }

    // Clear focus when scrolling starts
    LaunchedEffect(viewModel.state.scrollState.isScrollInProgress) {
        if (viewModel.state.scrollState.isScrollInProgress) {
            focusManager.clearFocus()
        }
    }

    SearchScreenContent(
        state = viewModel.state,
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
        wordDetailLabel = wordDetailLabel,
        onNavigateToWordDetail = onNavigateToWordDetail,
        onNavigateToFavorites = onNavigateToFavorites,
        onNavigateToSettings = onNavigateToSettings
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreenContent(
    state: SearchUiState,
    onQueryChange: (String) -> Unit = {},
    onResultSelected: (DictionaryRepository.SearchItem) -> Unit = {},
    onSuggestionSelected: (String) -> Unit = {},
    onLanguageSelected: (Language?) -> Unit = {},
    onSetLanguageDropdownExpanded: (Boolean) -> Unit = {},
    onRefreshSuggestions: () -> Unit = {},
    wordDetailLabel: String? = null,
    onNavigateToWordDetail: () -> Unit = {},
    onNavigateToFavorites: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {}
) {
    val focusManager = LocalFocusManager.current
    val searchFocusRequester = remember { FocusRequester() }
    var isSearchFocused by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(isSearchFocused) {
                detectTapGestures(onTap = {
                    if (!isSearchFocused) {
                        searchFocusRequester.requestFocus()
                    } else {
                        focusManager.clearFocus()
                    }
                })
            }
    ) {
        Scaffold(
            bottomBar = {
                AppNavigationBar(
                    currentScreen = AppScreen.SEARCH,
                    onNavigateToSearch = {},
                    onNavigateToFavorites = onNavigateToFavorites,
                    onNavigateToWordDetail = onNavigateToWordDetail,
                    wordDetailLabel = wordDetailLabel,
                    onNavigateToSettings = onNavigateToSettings
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
                        placeholder = stringResource(Res.string.search_placeholder),
                        focusRequester = searchFocusRequester,
                        onFocusChanged = { isSearchFocused = it }
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

                // Result area
                when {
                    state.availableLanguages.isEmpty() -> {
                        NoDictionaryState(onNavigateToSettings = onNavigateToSettings)
                    }

                    state.query.isEmpty() -> {
                        PullToRefreshBox(
                            modifier = Modifier.fillMaxSize(),
                            isRefreshing = state.isSuggestionsRefreshing,
                            onRefresh = onRefreshSuggestions
                        ) {
                            EmptySearchState(
                                wordSuggestions = state.wordSuggestions,
                                favoriteLemmas = state.favoriteLemmas,
                                onWordClick = onSuggestionSelected
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
}

@Composable
private fun SearchResultCard(
    item: DictionaryRepository.SearchItem,
    showLanguageIndicator: Boolean = false,
    onClick: () -> Unit
) {
    val containerColor = colorForLemma(item.lemma, MaterialTheme.colorScheme.surface)
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
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
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

@Composable
private fun EmptySearchState(
    wordSuggestions: List<String>,
    favoriteLemmas: List<String>,
    onWordClick: (String) -> Unit
) {
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
                    letterSpacing = 1.2.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
            )
            wordSuggestions.forEach { lemma ->
                SuggestionCard(lemma = lemma, onClick = { onWordClick(lemma) })
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

        if (favoriteLemmas.isNotEmpty()) {
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

        if (wordSuggestions.isEmpty() && favoriteLemmas.isEmpty()) {
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
    }
}

@Composable
private fun SuggestionCard(lemma: String, onClick: () -> Unit) {
    val containerColor = colorForLemma(lemma, MaterialTheme.colorScheme.surface)
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
    onNavigateToSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(Res.string.search_no_dictionary_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(Res.string.search_no_dictionary_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        FilledTonalButton(onClick = onNavigateToSettings) {
            Text(stringResource(Res.string.search_go_to_settings))
        }
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
