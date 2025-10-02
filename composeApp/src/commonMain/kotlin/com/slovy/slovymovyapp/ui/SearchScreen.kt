package com.slovy.slovymovyapp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
    onWordSelected: (DictionaryRepository.SearchItem) -> Unit = { _ -> }
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
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreenContent(
    state: SearchUiState,
    onQueryChange: (String) -> Unit = {},
    onResultSelected: (DictionaryRepository.SearchItem) -> Unit = {}
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
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
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
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.display,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
            }

            SuggestionChip(
                onClick = { },
                label = {
                    Text(
                        text = item.language.uppercase(),
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

@Composable
private fun EmptySearchState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
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
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun NoResultsState(query: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
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
                text = "We couldn't find any words matching \"$query\"",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Try a different spelling or search term",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
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
                    display = "\"celebration\""
                ),
                DictionaryRepository.SearchItem(
                    language = "en",
                    lemmaId = Uuid.parse("00000000-0000-0000-0000-000000000002"),
                    lemma = "celebrity",
                    display = "\"celebrity\""
                ),
                DictionaryRepository.SearchItem(
                    language = "en",
                    lemmaId = Uuid.parse("00000000-0000-0000-0000-000000000003"),
                    lemma = "celestial",
                    display = "\"celestial\""
                ),
                DictionaryRepository.SearchItem(
                    language = "en",
                    lemmaId = Uuid.parse("00000000-0000-0000-0000-000000000004"),
                    lemma = "cell",
                    display = "\"cell\""
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
                    display = "\"program\""
                ),
                DictionaryRepository.SearchItem(
                    language = "en",
                    lemmaId = Uuid.parse("00000000-0000-0000-0000-000000000002"),
                    lemma = "programmatically",
                    display = "\"programmatically\""
                ),
                DictionaryRepository.SearchItem(
                    language = "ru",
                    lemmaId = Uuid.parse("00000000-0000-0000-0000-000000000003"),
                    lemma = "программа",
                    display = "\"программа\""
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
                    display = "\"world\""
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
                    display = "\"bibliotheek\""
                ),
                DictionaryRepository.SearchItem(
                    language = "nl",
                    lemmaId = Uuid.parse("00000000-0000-0000-0000-000000000002"),
                    lemma = "bijbel",
                    display = "\"bijbel\""
                )
            ),
            showNoResults = false,
        ),
    )
}
