package com.slovy.slovymovyapp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import com.slovy.slovymovyapp.data.remote.DictionaryRepository
import com.slovy.slovymovyapp.data.remote.PartOfSpeech
import com.slovy.slovymovyapp.ui.word.Badge
import com.slovy.slovymovyapp.ui.word.colorsForPos
import com.slovy.slovymovyapp.ui.word.getFrequencyColor
import kotlinx.coroutines.launch
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.uuid.Uuid

data class SearchUiState(
    val title: String,
    val query: String,
    val results: List<DictionaryRepository.SearchItem>,
    val showNoResults: Boolean,
    val scrollState: LazyListState = LazyListState()
)

class SearchViewModel(
    private val repository: DictionaryRepository
) : ViewModel() {

    var state by mutableStateOf(SearchUiState("Search", "", emptyList(), false))
        private set

    suspend fun updateQuery(newQuery: String) {
        val trimmed = newQuery.trim()
        val newResults = if (trimmed.isEmpty()) {
            emptyList()
        } else {
            repository.search(trimmed)
        }
        state = state.copy(
            query = trimmed,
            results = newResults,
            showNoResults = newResults.isEmpty() && trimmed.isNotEmpty()
        )
        // Reset scroll to top when query changes
        state.scrollState.scrollToItem(0)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onWordSelected: (DictionaryRepository.SearchItem) -> Unit = { _ -> },
    wordDetailLabel: String? = null,
    onNavigateToWordDetail: () -> Unit = {},
    onNavigateToFavorites: () -> Unit = {}
) {
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()
    // restore after process death
    val savedQuery = rememberSaveable { viewModel.state.query }

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
        onQueryChange = { coroutineScope.launch { viewModel.updateQuery(it) } },
        onResultSelected = { item ->
            focusManager.clearFocus()
            onWordSelected(item)
        },
        wordDetailLabel = wordDetailLabel,
        onNavigateToWordDetail = onNavigateToWordDetail,
        onNavigateToFavorites = onNavigateToFavorites
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreenContent(
    state: SearchUiState,
    onQueryChange: (String) -> Unit = {},
    onResultSelected: (DictionaryRepository.SearchItem) -> Unit = {},
    wordDetailLabel: String? = null,
    onNavigateToWordDetail: () -> Unit = {},
    onNavigateToFavorites: () -> Unit = {}
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(state.title)
                        }
                    }
                )
            },
            bottomBar = {
                AppNavigationBar(
                    currentScreen = AppScreen.SEARCH,
                    onNavigateToSearch = {},
                    onNavigateToFavorites = onNavigateToFavorites,
                    onNavigateToWordDetail = onNavigateToWordDetail,
                    wordDetailLabel = wordDetailLabel
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Search field with padding
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 16.dp),
                    label = { Text("Search a word") },
                    placeholder = { Text("Type to search...") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    trailingIcon = {
                        if (state.query.isNotEmpty()) {
                            TextButton(onClick = { onQueryChange("") }) {
                                Text("✕")
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Result area
                when {
                    state.query.isEmpty() -> {
                        EmptySearchState()
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
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val (lc, lcc) = getFrequencyColor(item.zipfFrequency)
                    Badge(
                        text = item.display,
                        containerColor = lc,
                        contentColor = lcc,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            if (item.pos.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    item.pos.forEach { pos ->
                        val (posLc, posLcc) = colorsForPos(pos)
                        Badge(
                            text = pos.short,
                            containerColor = posLc,
                            contentColor = posLcc
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptySearchState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        Text(
            text = "🔍",
            style = MaterialTheme.typography.displayLarge
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Search for words",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Start typing to find words in your selected language",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}

@Composable
private fun NoResultsState(query: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        Text(
            text = "🤔",
            style = MaterialTheme.typography.displayLarge
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No results found",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "We couldn't find any words matching $query",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Try a different spelling or search term",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}

@Preview
@Composable
private fun SearchScreenPreviewEmptyQuery() {
    SearchScreenContent(
        state = SearchUiState(
            title = "Dictionary Search",
            query = "",
            results = emptyList(),
            showNoResults = false,
        ),
    )
}

@Preview
@Composable
private fun SearchScreenPreviewWithResults() {
    SearchScreenContent(
        state = SearchUiState(
            title = "Dictionary Search",
            query = "cel",
            results = listOf(
                DictionaryRepository.SearchItem(
                    language = "en",
                    lemmaId = Uuid.parse("00000000-0000-0000-0000-000000000001"),
                    lemma = "celebration",
                    display = "celebration",
                    zipfFrequency = 4.5f,
                    pos = listOf(PartOfSpeech.NOUN)
                ),
                DictionaryRepository.SearchItem(
                    language = "en",
                    lemmaId = Uuid.parse("00000000-0000-0000-0000-000000000002"),
                    lemma = "celebrity",
                    display = "celebrity",
                    zipfFrequency = 4.3f,
                    pos = listOf(PartOfSpeech.NOUN)
                ),
                DictionaryRepository.SearchItem(
                    language = "en",
                    lemmaId = Uuid.parse("00000000-0000-0000-0000-000000000003"),
                    lemma = "celestial",
                    display = "celestial",
                    zipfFrequency = 3.8f,
                    pos = listOf(PartOfSpeech.ADJECTIVE)
                ),
                DictionaryRepository.SearchItem(
                    language = "en",
                    lemmaId = Uuid.parse("00000000-0000-0000-0000-000000000004"),
                    lemma = "cell",
                    display = "cell",
                    zipfFrequency = 5.2f,
                    pos = listOf(PartOfSpeech.NOUN)
                )
            ),
            showNoResults = false,
        ),
    )
}

@Preview
@Composable
private fun SearchScreenPreviewMultilingualResults() {
    SearchScreenContent(
        state = SearchUiState(
            title = "Dictionary Search",
            query = "program",
            results = listOf(
                DictionaryRepository.SearchItem(
                    language = "en",
                    lemmaId = Uuid.parse("00000000-0000-0000-0000-000000000001"),
                    lemma = "program",
                    display = "program",
                    zipfFrequency = 5.5f,
                    pos = listOf(PartOfSpeech.NOUN, PartOfSpeech.VERB)
                ),
                DictionaryRepository.SearchItem(
                    language = "en",
                    lemmaId = Uuid.parse("00000000-0000-0000-0000-000000000002"),
                    lemma = "programmatically",
                    display = "programmatically",
                    zipfFrequency = 3.2f,
                    pos = listOf(PartOfSpeech.ADVERB)
                ),
                DictionaryRepository.SearchItem(
                    language = "ru",
                    lemmaId = Uuid.parse("00000000-0000-0000-0000-000000000003"),
                    lemma = "программа",
                    display = "программа",
                    zipfFrequency = 5.8f,
                    pos = listOf(PartOfSpeech.NOUN)
                )
            ),
            showNoResults = false,
        ),
    )
}

@Preview
@Composable
private fun SearchScreenPreviewNoResults() {
    SearchScreenContent(
        state = SearchUiState(
            title = "Dictionary Search",
            query = "xyzabc123",
            results = emptyList(),
            showNoResults = true,
        ),
    )
}

@Preview
@Composable
private fun SearchScreenPreviewInfoDialog() {
    SearchScreenContent(
        state = SearchUiState(
            title = "Dictionary Search",
            query = "world",
            results = listOf(
                DictionaryRepository.SearchItem(
                    language = "en",
                    lemmaId = Uuid.parse("00000000-0000-0000-0000-000000000001"),
                    lemma = "world",
                    display = "world",
                    zipfFrequency = 6.2f,
                    pos = listOf(PartOfSpeech.NOUN)
                )
            ),
            showNoResults = false,
        ),
    )
}

@Preview
@Composable
private fun SearchScreenPreviewDutchLanguage() {
    SearchScreenContent(
        state = SearchUiState(
            title = "Dictionary Search",
            query = "bib",
            results = listOf(
                DictionaryRepository.SearchItem(
                    language = "nl",
                    lemmaId = Uuid.parse("00000000-0000-0000-0000-000000000001"),
                    lemma = "bibliotheek",
                    display = "bibliotheek",
                    zipfFrequency = 4.1f,
                    pos = listOf(PartOfSpeech.NOUN)
                ),
                DictionaryRepository.SearchItem(
                    language = "nl",
                    lemmaId = Uuid.parse("00000000-0000-0000-0000-000000000002"),
                    lemma = "bijbel",
                    display = "bijbel",
                    zipfFrequency = 4.8f,
                    pos = listOf(PartOfSpeech.NOUN)
                )
            ),
            showNoResults = false,
        ),
    )
}
