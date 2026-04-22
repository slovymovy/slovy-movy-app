package com.slovy.slovymovyapp.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slovy.slovymovyapp.analytics.Analytics
import com.slovy.slovymovyapp.analytics.AnalyticsEvent
import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.remote.DataDbManager
import com.slovy.slovymovyapp.data.remote.NetworkErrorClassifier
import com.slovy.slovymovyapp.ui.theme.AppSpacing
import kotlinx.coroutines.CancellationException
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
            nativeLanguages = initialNativeLanguages - setOfNotNull(initialLearningLanguage)
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
                    availableLanguages = available.sortedBy { it.selfName }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state = state.copy(
                    isLoading = false,
                    errorMessage = NetworkErrorClassifier.userMessage(e)
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
            val native = viewModel.state.nativeLanguages.sortedBy { it.selfName }
            Analytics.logEvent(AnalyticsEvent.LANG_TO_TRANSLATE_SELECTED, mapOf("lang" to native.joinToString(",") { it.code }))
            if (learning != null) {
                onNext(learning, native)
                Analytics.logEvent(AnalyticsEvent.LANG_TO_LEARN_SELECTED, mapOf("lang" to learning.code))
            } else {
                Analytics.logEvent(AnalyticsEvent.LANG_TO_LEARN_NOT_SELECTED, mapOf("lang" to "not selected"))
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
    val canGoNext = state.learningLanguage != null

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
        if (state.isLoading) {
            Box(
                Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (state.errorMessage != null) {
            Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(AppSpacing.xl),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(state.errorMessage, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                Button(onClick = onRetry, modifier = Modifier.padding(top = AppSpacing.lg)) {
                    Text("Retry")
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = AppSpacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(AppSpacing.xxxl))

                Text(
                    text = "Select Languages",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(Modifier.height(AppSpacing.sm))

                Text(
                    text = "Choose what you're learning and your translation preferences.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(AppSpacing.xxl))

                // I'm learning... section
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "I'm learning...",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Medium
                        )
                    )
                    Spacer(Modifier.height(AppSpacing.sm))

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
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Row(
                                modifier = Modifier.padding(AppSpacing.lg).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(state.learningLanguage?.flag ?: "", fontSize = 20.sp)
                                    Spacer(Modifier.width(AppSpacing.md))
                                    Text(
                                        text = state.learningLanguage?.selfName ?: "Select language",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = if (state.learningLanguage != null)
                                            MaterialTheme.colorScheme.onSurface
                                        else
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
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
                                            Spacer(Modifier.width(AppSpacing.sm))
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

                Spacer(Modifier.height(AppSpacing.xxl))

                // My native language(s) section
                Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    Text(
                        text = "Translate into:",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Medium
                        )
                    )
                    Spacer(Modifier.height(AppSpacing.sm))

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
                    ) {
                        items(
                            items = Language.entries
                                .filter { it != state.learningLanguage }
                                .sortedBy { it.selfName },
                            key = { it.name }
                        ) { language ->
                            val isSelected = language in state.nativeLanguages

                            Card(
                                modifier = Modifier.fillMaxWidth()
                                    .semantics(mergeDescendants = true) {}
                                    .toggleable(
                                        value = isSelected,
                                        role = Role.Checkbox,
                                        onValueChange = { onNativeLanguageToggled(language) }
                                    ),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                border = if (isSelected)
                                    BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                                else
                                    BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(AppSpacing.lg).fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = language.flag,
                                            fontSize = 20.sp
                                        )
                                        Spacer(Modifier.width(AppSpacing.md))
                                        Text(
                                            text = language.selfName,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurface
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

                Spacer(Modifier.height(AppSpacing.lg))

                Text(
                    text = "Update anytime in Settings",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(AppSpacing.lg))

                Button(
                    onClick = onNext,
                    enabled = canGoNext,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        "Next",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }

                Spacer(Modifier.height(AppSpacing.xxl))
            }
        }
    }
}

@Preview
@Composable
private fun LanguageSetupScreenDefaultPreview(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
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

@Preview
@Composable
private fun LanguageSetupScreenSelectedPreview(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
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
