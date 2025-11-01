package com.slovy.slovymovyapp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.remote.DataDbManager
import kotlinx.coroutines.launch
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.jetbrains.compose.ui.tooling.preview.PreviewParameter

data class DictionaryOption(
    val label: String,
    val language: Language
)

data class DictionarySelectionUiState(
    val isLoading: Boolean = true,
    val dictionaries: List<DictionaryOption> = emptyList(),
    val errorMessage: String? = null
)

class DictionarySelectionViewModel(
    private val dataDbManager: DataDbManager,
    val title: String = "Choose dictionary"
) : ViewModel() {
    var state by mutableStateOf(DictionarySelectionUiState())
        private set

    init {
        loadAvailableDictionaries()
    }

    private fun loadAvailableDictionaries() {
        viewModelScope.launch {
            state = state.copy(isLoading = true, errorMessage = null)
            try {
                val availableLanguages = dataDbManager.fetchAvailableLanguages()
                val options = availableLanguages
                    .filter { it.dictionarySizeBytes != null } // Only show languages with dictionaries
                    .map { DictionaryOption(it.language.selfName, it.language) }
                state = state.copy(isLoading = false, dictionaries = options)
            } catch (e: Exception) {
                state = state.copy(
                    isLoading = false,
                    errorMessage = "Failed to load available dictionaries: ${e.message}"
                )
            }
        }
    }

    fun retry() {
        loadAvailableDictionaries()
    }
}

@Composable
fun DictionarySelectionScreen(
    viewModel: DictionarySelectionViewModel,
    onDictionaryChosen: (Language) -> Unit = { _ -> }
) {
    DictionarySelectionScreenContent(
        title = viewModel.title,
        state = viewModel.state,
        onDictionaryChosen = onDictionaryChosen,
        onRetry = { viewModel.retry() }
    )
}

@Composable
fun DictionarySelectionScreenContent(
    title: String,
    state: DictionarySelectionUiState,
    onDictionaryChosen: (Language) -> Unit = {},
    onRetry: () -> Unit = {}
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium
            )

            when {
                state.isLoading -> {
                    CircularProgressIndicator()
                }

                state.errorMessage != null -> {
                    Text(
                        text = state.errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Retry",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .clickable { onRetry() }
                            .padding(12.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                else -> {
                    state.dictionaries.forEach { option ->
                        Text(
                            text = option.label,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onDictionaryChosen(option.language) }
                                .padding(vertical = 12.dp)
                        )
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun DictionarySelectionScreenPreviewDefault(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        DictionarySelectionScreenContent(
            title = "Choose dictionary",
            state = DictionarySelectionUiState(
                isLoading = false,
                dictionaries = Language.entries.map { DictionaryOption(it.selfName, it) }
            )
        )
    }
}

@Preview
@Composable
private fun DictionarySelectionScreenPreviewLoading(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        DictionarySelectionScreenContent(
            title = "Choose dictionary",
            state = DictionarySelectionUiState(isLoading = true)
        )
    }
}

@Preview
@Composable
private fun DictionarySelectionScreenPreviewError(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        DictionarySelectionScreenContent(
            title = "Choose dictionary",
            state = DictionarySelectionUiState(
                isLoading = false,
                errorMessage = "Failed to load available dictionaries"
            )
        )
    }
}
