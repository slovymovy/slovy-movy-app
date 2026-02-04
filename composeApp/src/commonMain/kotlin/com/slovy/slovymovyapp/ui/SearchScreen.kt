package com.slovy.slovymovyapp.ui

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
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.*
import androidx.compose.material3.ExposedDropdownMenuAnchorType.Companion.PrimaryEditable
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.remote.DictionaryRepository
import com.slovy.slovymovyapp.data.remote.PartOfSpeech
import com.slovy.slovymovyapp.ui.components.AppCard
import com.slovy.slovymovyapp.ui.components.AppSearchBar
import com.slovy.slovymovyapp.ui.components.EmptyState
import com.slovy.slovymovyapp.ui.word.Badge
import com.slovy.slovymovyapp.ui.word.colorForLemma
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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
    val wordSuggestions: List<String> = emptyList()
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
        val suggestions = repository.getWordSuggestions(language)
        state = state.copy(wordSuggestions = suggestions)
    }

    fun refreshSuggestions() {
        // Skip if initial load hasn't completed yet (avoid double load on first open)
        if (!suggestionsInitialized) return
        viewModelScope.launch {
            loadSuggestionsForCurrentLanguage()
        }
    }

    fun updateQuery(newQuery: String) {
        val trimmed = newQuery.trim()
        // Update UI state immediately for responsive typing
        state = state.copy(query = trimmed)
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

    fun toggleLanguageDropdown() {
        state = state.copy(isLanguageDropdownExpanded = !state.isLanguageDropdownExpanded)
    }

    fun dismissLanguageDropdown() {
        state = state.copy(isLanguageDropdownExpanded = false)
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

    // Refresh language indicators, suggestions, and search results when screen is opened
    LaunchedEffect(Unit) {
        viewModel.refreshLanguageIndicators()
        viewModel.refreshSuggestions()
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
            onWordSelected(item)
        },
        onSuggestionSelected = { word ->
            focusManager.clearFocus()
            viewModel.state.selectedLanguage?.let { language ->
                onSuggestionSelected(language, word)
            }
        },
        onLanguageSelected = { language ->
            viewModel.setShowLanguageIndicators(language == null && viewModel.state.availableLanguages.size > 1)
            viewModel.setSelectedLanguage(language)
            // TODO: search is not relaunched or query
        },
        onToggleLanguageDropdown = { viewModel.toggleLanguageDropdown() },
        onDismissLanguageDropdown = { viewModel.dismissLanguageDropdown() },
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
    onToggleLanguageDropdown: () -> Unit = {},
    onDismissLanguageDropdown: () -> Unit = {},
    wordDetailLabel: String? = null,
    onNavigateToWordDetail: () -> Unit = {},
    onNavigateToFavorites: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {}
) {
    val focusManager = LocalFocusManager.current
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    focusManager.clearFocus()
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
                        placeholder = "Type a word..."
                    )

                    // Language filter dropdown
                    if (state.availableLanguages.isNotEmpty() && state.availableLanguages.size > 1) {
                        val currentLanguage = state.selectedLanguage ?: state.availableLanguages.firstOrNull()

                        ExposedDropdownMenuBox(
                            expanded = state.isLanguageDropdownExpanded,
                            onExpandedChange = { onToggleLanguageDropdown() }
                        ) {
                            Surface(
                                modifier = Modifier
                                    .menuAnchor(PrimaryEditable)
                                    .height(56.dp)
                                    .widthIn(min = 56.dp)
                                    .clickable(
                                        onClick = { onToggleLanguageDropdown() },
                                        role = Role.Button
                                    ),
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
                                onDismissRequest = onDismissLanguageDropdown,
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
                                            onDismissLanguageDropdown()
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
                        EmptySearchState(
                            wordSuggestions = state.wordSuggestions,
                            onWordClick = onSuggestionSelected
                        )
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchResultCard(
    item: DictionaryRepository.SearchItem,
    showLanguageIndicator: Boolean = false,
    onClick: () -> Unit
) {
    val containerColor = colorForLemma(item.lemma, MaterialTheme.colorScheme.surface)
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Word display
            Text(
                text = item.display,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )

            // Icons section - aligned to the right
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Show language badge if searching multiple dictionaries
                if (showLanguageIndicator) {
                    Badge(
                        text = item.language.selfName,
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        shape = RoundedCornerShape(4.dp)
                    )
                }

                // Favorite icon
                if (item.isFavorite) {
                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = "Favorite",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Online-only / download icon
                if (item.onlineOnly) {
                    Icon(
                        imageVector = Icons.Filled.DownloadForOffline,
                        contentDescription = "Online",
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
    onWordClick: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Search your word",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        if (wordSuggestions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "or explore:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                wordSuggestions.forEach { word ->
                    SuggestionChip(
                        onClick = { onWordClick(word) },
                        label = {
                            Text(
                                text = word,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        border = SuggestionChipDefaults.suggestionChipBorder(
                            enabled = true,
                            borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        ),
                        elevation = SuggestionChipDefaults.suggestionChipElevation(
                            elevation = 2.dp
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
            shape = MaterialTheme.shapes.medium
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Lightbulb,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "You can search by translations and grammar forms too!",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
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
            text = "No dictionary downloaded",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Download a dictionary to start searching for words",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        FilledTonalButton(onClick = onNavigateToSettings) {
            Text("Go to Settings")
        }
    }
}

@Composable
private fun NoResultsState(query: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center
    ) {
        EmptyState(
            icon = Icons.Filled.Search,
            title = "No results found",
            description = "We couldn't find any words matching \"$query\". Try a different spelling or search term."
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
                wordSuggestions = listOf("the", "be", "to", "of", "and")
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
