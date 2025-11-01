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


data class LanguageOption(
    val label: String,
    val language: Language
)

data class LanguageSelectionUiState(
    val isLoading: Boolean = true,
    val languages: List<LanguageOption> = emptyList(),
    val errorMessage: String? = null
)

class LanguageSelectionViewModel(
    private val dataDbManager: DataDbManager,
    val title: String = "Choose your native language"
) : ViewModel() {
    var state by mutableStateOf(LanguageSelectionUiState())
        private set

    init {
        loadAvailableLanguages()
    }

    private fun loadAvailableLanguages() {
        viewModelScope.launch {
            state = state.copy(isLoading = true, errorMessage = null)
            try {
                val availableLanguages = dataDbManager.fetchAvailableLanguages()
                val options = availableLanguages
                    .filter { it.dictionarySizeBytes != null } // Only show languages with dictionaries
                    .map { LanguageOption(it.language.selfName, it.language) }
                state = state.copy(isLoading = false, languages = options)
            } catch (e: Exception) {
                state = state.copy(
                    isLoading = false,
                    errorMessage = "Failed to load available languages: ${e.message}"
                )
            }
        }
    }

    fun retry() {
        loadAvailableLanguages()
    }
}

@Composable
fun LanguageSelectionScreen(
    viewModel: LanguageSelectionViewModel,
    onLanguageChosen: (Language) -> Unit = { _ -> }
) {
    LanguageSelectionScreenContent(
        title = viewModel.title,
        state = viewModel.state,
        onLanguageChosen = onLanguageChosen,
        onRetry = { viewModel.retry() }
    )
}

@Composable
fun LanguageSelectionScreenContent(
    title: String,
    state: LanguageSelectionUiState,
    onLanguageChosen: (Language) -> Unit = {},
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
                    state.languages.forEach { option ->
                        Text(
                            text = option.label,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onLanguageChosen(option.language) }
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
private fun LanguageSelectionScreenPreviewDefault(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        LanguageSelectionScreenContent(
            title = "Choose your native language",
            state = LanguageSelectionUiState(
                isLoading = false,
                languages = Language.entries.map { LanguageOption(it.selfName, it) }
            )
        )
    }
}

@Preview
@Composable
private fun LanguageSelectionScreenPreviewLoading(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        LanguageSelectionScreenContent(
            title = "Choose your native language",
            state = LanguageSelectionUiState(isLoading = true)
        )
    }
}

@Preview
@Composable
private fun LanguageSelectionScreenPreviewError(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        LanguageSelectionScreenContent(
            title = "Choose your native language",
            state = LanguageSelectionUiState(
                isLoading = false,
                errorMessage = "Failed to load available languages"
            )
        )
    }
}
