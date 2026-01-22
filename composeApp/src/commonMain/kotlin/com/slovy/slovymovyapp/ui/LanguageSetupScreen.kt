package com.slovy.slovymovyapp.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.remote.DataDbManager
import kotlinx.coroutines.launch

data class LanguageSetupUiState(
    val isLoading: Boolean = true,
    val availableLanguages: List<Language> = emptyList(),
    val learningLanguage: Language? = null,
    val nativeLanguages: Set<Language> = emptySet(),
    val errorMessage: String? = null
)

class LanguageSetupViewModel(
    private val dataDbManager: DataDbManager,
    initialLearningLanguage: Language? = null,
    initialNativeLanguages: Set<Language> = emptySet()
) : ViewModel() {
    var state by mutableStateOf(
        LanguageSetupUiState(
            learningLanguage = initialLearningLanguage,
            nativeLanguages = initialNativeLanguages
        )
    )
        private set

    init {
        loadAvailableLanguages()
    }

    private fun loadAvailableLanguages() {
        viewModelScope.launch {
            state = state.copy(isLoading = true, errorMessage = null)
            try {
                val available = dataDbManager.fetchAvailableLanguages()
                    .filter { it.dictionarySizeBytes != null }
                    .map { it.language }

                state = state.copy(
                    isLoading = false,
                    availableLanguages = available
                )
            } catch (e: Exception) {
                state = state.copy(
                    isLoading = false,
                    errorMessage = "Failed to load languages: ${e.message}"
                )
            }
        }
    }

    fun selectLearningLanguage(language: Language) {
        state = state.copy(
            learningLanguage = language,
            nativeLanguages = state.nativeLanguages.filter { it != language }.toSet()
        )
    }

    fun toggleNativeLanguage(language: Language) {
        val current = state.nativeLanguages
        state = state.copy(
            nativeLanguages = if (language in current) {
                current - language
            } else {
                current + language
            }
        )
    }

    fun retry() {
        loadAvailableLanguages()
    }
}

@Composable
fun LanguageSetupScreen(
    viewModel: LanguageSetupViewModel,
    onNext: (learning: Language, native: List<Language>) -> Unit
) {
    LanguageSetupScreenContent(
        state = viewModel.state,
        onLearningLanguageSelected = viewModel::selectLearningLanguage,
        onNativeLanguageToggled = viewModel::toggleNativeLanguage,
        onNext = {
            val learning = viewModel.state.learningLanguage
            val native = viewModel.state.nativeLanguages.toList()
            if (learning != null && native.isNotEmpty()) {
                onNext(learning, native)
            }
        },
        onRetry = viewModel::retry
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSetupScreenContent(
    state: LanguageSetupUiState,
    onLearningLanguageSelected: (Language) -> Unit = {},
    onNativeLanguageToggled: (Language) -> Unit = {},
    onNext: () -> Unit = {},
    onRetry: () -> Unit = {}
) {
    val canGoNext = state.learningLanguage != null && state.nativeLanguages.isNotEmpty()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (state.errorMessage != null) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(state.errorMessage, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                Button(onClick = onRetry, modifier = Modifier.padding(top = 16.dp)) {
                    Text("Retry")
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(48.dp))

                Text(
                    text = "Choose Your Languages",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold
                    )
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "Select the language you want to learn and your native languages",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(32.dp))

                // I'm learning... section
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "I'm learning...",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(Modifier.height(8.dp))

                    var expanded by remember { mutableStateOf(false) }

                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        OutlinedCard(
                            onClick = { expanded = true },
                            modifier = Modifier.fillMaxWidth()
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, Color.LightGray)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(state.learningLanguage?.flag ?: "", fontSize = 20.sp)
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        text = state.learningLanguage?.selfName ?: "Select language",
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                        }

                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            state.availableLanguages.forEach { language ->
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(language.flag)
                                            Spacer(Modifier.width(8.dp))
                                            Text(language.selfName)
                                        }
                                    },
                                    onClick = {
                                        onLearningLanguageSelected(language)
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(32.dp))

                // My native language(s) section
                Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    Text(
                        text = "My native language(s):",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(Modifier.height(8.dp))

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(state.availableLanguages) { language ->
                            val isSelected = language in state.nativeLanguages
                            val isEnabled = language != state.learningLanguage

                            Card(
                                modifier = Modifier.fillMaxWidth().then(
                                    if (isEnabled) Modifier.clickable { onNativeLanguageToggled(language) }
                                    else Modifier
                                ),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isEnabled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(
                                        alpha = 0.5f
                                    )
                                ),
                                border = if (isSelected) BorderStroke(
                                    2.dp,
                                    MaterialTheme.colorScheme.primary
                                ) else BorderStroke(1.dp, Color.LightGray),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = language.flag,
                                            fontSize = 20.sp,
                                            color = if (isEnabled) Color.Unspecified else Color.Gray
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Text(
                                            text = language.selfName,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = if (isEnabled) Color.Unspecified else Color.Gray
                                        )
                                    }
                                    if (isSelected) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = onNext,
                    enabled = canGoNext,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(28.dp)
                ) {
                    Text("Next", style = MaterialTheme.typography.titleMedium)
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    text = "You can adjust these settings later in Settings",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun LanguageSetupScreenDefaultPreview(
    @androidx.compose.ui.tooling.preview.PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        LanguageSetupScreenContent(
            state = LanguageSetupUiState(
                isLoading = false,
                availableLanguages = Language.entries
            )
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun LanguageSetupScreenSelectedPreview(
    @androidx.compose.ui.tooling.preview.PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        LanguageSetupScreenContent(
            state = LanguageSetupUiState(
                isLoading = false,
                availableLanguages = Language.entries,
                learningLanguage = Language.DUTCH,
                nativeLanguages = setOf(Language.ENGLISH)
            )
        )
    }
}
