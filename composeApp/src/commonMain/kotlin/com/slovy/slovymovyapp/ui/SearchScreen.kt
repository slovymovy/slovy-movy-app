package com.slovy.slovymovyapp.ui

// Using a simple text button instead of material icons to keep commonMain lightweight
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.slovy.slovymovyapp.data.remote.DataDbManager
import com.slovy.slovymovyapp.data.remote.DictionaryRepository
import kotlin.uuid.Uuid
import org.jetbrains.compose.ui.tooling.preview.Preview

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    language: String? = null,
    dictionaryLanguage: String? = null,
    dataManager: DataDbManager? = null,
    onWordSelected: (DictionaryRepository.SearchItem) -> Unit = { _ -> }
) {
    var query by remember { mutableStateOf("") }
    var showInfo by remember { mutableStateOf(false) }

    // Repository instance
    val repository = remember(dataManager) { dataManager?.let { DictionaryRepository(it) } }

    val results = remember(query, dictionaryLanguage, repository) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) emptyList()
        else {
            repository?.search(trimmed, dictionaryLanguage) ?: emptyList()
        }
    }

    val uiState = remember(query, results, showInfo, language) {
        SearchUiState(
            title = "Search",
            query = query,
            results = results,
            isInfoDialogVisible = showInfo,
            infoText = language ?: "Not selected",
            showNoResults = query.isNotBlank() && results.isEmpty()
        )
    }

    SearchScreenContent(
        state = uiState,
        onQueryChange = { query = it },
        onResultSelected = onWordSelected,
        onInfoClick = { showInfo = true },
        onInfoDismiss = { showInfo = false }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreenContent(
    state: SearchUiState,
    onQueryChange: (String) -> Unit = {},
    onResultSelected: (DictionaryRepository.SearchItem) -> Unit = {},
    onInfoClick: () -> Unit = {},
    onInfoDismiss: () -> Unit = {},
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(state.title) },
                    actions = {
                        TextButton(onClick = onInfoClick) {
                            Text("ℹ︎")
                        }
                    }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(innerPadding)
                    .padding(16.dp)
            ) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Search a word") }
                )

                if (state.showNoResults) {
                    Text(
                        text = "No results",
                        modifier = Modifier.padding(top = 16.dp)
                    )
                } else {
                    state.results.forEach { item ->
                        Text(
                            text = item.display,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onResultSelected(item) }
                                .padding(vertical = 12.dp)
                        )
                    }
                }
            }
        }

        if (state.isInfoDialogVisible) {
            AlertDialog(
                onDismissRequest = onInfoDismiss,
                confirmButton = {
                    TextButton(onClick = onInfoDismiss) {
                        Text("OK")
                    }
                },
                title = { Text("Selected language") },
                text = { Text(state.infoText) }
            )
        }
    }
}

data class SearchUiState(
    val title: String,
    val query: String,
    val results: List<DictionaryRepository.SearchItem>,
    val isInfoDialogVisible: Boolean,
    val infoText: String,
    val showNoResults: Boolean
)

@Preview
@Composable
private fun SearchScreenPreviewEmptyQuery() {
    SearchScreenContent(
        state = SearchUiState(
            title = "Search",
            query = "",
            results = emptyList(),
            isInfoDialogVisible = false,
            infoText = "Not selected",
            showNoResults = false
        )
    )
}

@Preview
@Composable
private fun SearchScreenPreviewWithResults() {
    SearchScreenContent(
        state = SearchUiState(
            title = "Search",
            query = "wo",
            results = listOf(
                DictionaryRepository.SearchItem(
                    language = "en",
                    lemmaId = Uuid.parse("00000000-0000-0000-0000-000000000001"),
                    lemma = "world",
                    display = "\"world\""
                ),
                DictionaryRepository.SearchItem(
                    language = "en",
                    lemmaId = Uuid.parse("00000000-0000-0000-0000-000000000002"),
                    lemma = "word",
                    display = "\"word\""
                )
            ),
            isInfoDialogVisible = false,
            infoText = "English",
            showNoResults = false
        )
    )
}

@Preview
@Composable
private fun SearchScreenPreviewNoResults() {
    SearchScreenContent(
        state = SearchUiState(
            title = "Search",
            query = "xyz",
            results = emptyList(),
            isInfoDialogVisible = false,
            infoText = "English",
            showNoResults = true
        )
    )
}

@Preview
@Composable
private fun SearchScreenPreviewInfoDialog() {
    SearchScreenContent(
        state = SearchUiState(
            title = "Search",
            query = "world",
            results = emptyList(),
            isInfoDialogVisible = true,
            infoText = "English",
            showNoResults = false
        )
    )
}
