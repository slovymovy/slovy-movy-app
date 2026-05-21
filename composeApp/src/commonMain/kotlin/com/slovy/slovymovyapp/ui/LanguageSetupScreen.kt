package com.slovy.slovymovyapp.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.slovy.slovymovyapp.ui.theme.serifFontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
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
import org.jetbrains.compose.resources.stringResource
import slovymovyapp.composeapp.generated.resources.*

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

    val scrollState = ScrollState(0)

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
        scrollState = viewModel.scrollState,
        onLearningLanguageSelected = viewModel::selectLearningLanguage,
        onNativeLanguageToggled = viewModel::toggleNativeLanguage,
        onNext = {
            val learning = viewModel.state.learningLanguage
            val native = viewModel.state.nativeLanguages.sortedBy { it.selfName }
            when {
                learning != null && native.isNotEmpty() -> {
                    native.forEach { language ->
                        Analytics.logEvent(AnalyticsEvent.LANG_TO_TRANSLATE_SELECTED, mapOf("lang" to language.code))
                    }
                    onNext(learning, native)
                    Analytics.logEvent(AnalyticsEvent.LANG_TO_LEARN_SELECTED, mapOf("lang" to learning.code))
                }

                learning == null -> {
                    Analytics.logEvent(AnalyticsEvent.LANG_TO_LEARN_NOT_SELECTED)
                }
            }
        },
        onRetry = viewModel::retry
    )
}

@Composable
fun LanguageSetupScreenContent(
    state: LanguageSetupUiState,
    scrollState: ScrollState = ScrollState(0),
    onLearningLanguageSelected: (Language) -> Unit = {},
    onNativeLanguageToggled: (Language) -> Unit = {},
    onNext: () -> Unit = {},
    onRetry: () -> Unit = {}
) {
    val canGoNext = state.learningLanguage != null && state.nativeLanguages.isNotEmpty()
    val translateIntoEnabled = state.learningLanguage != null
    val translationLanguages = Language.entries
        .filter { it != state.learningLanguage }
        .sortedBy { it.selfName }

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
                    Text(stringResource(Res.string.common_retry))
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = AppSpacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(AppSpacing.xxxl))

                OnboardingHeader(
                    title = stringResource(Res.string.language_setup_title),
                    subtitle = stringResource(Res.string.language_setup_subtitle)
                )

                Spacer(Modifier.height(AppSpacing.xxl))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.xl)
                ) {
                    LanguageSetupSection(
                        number = 1,
                        label = stringResource(Res.string.language_setup_learning_label),
                        state = if (state.learningLanguage == null) SectionVisualState.Active else SectionVisualState.Done,
                        lockedHint = null
                    ) {
                        LanguageSetupListCard(enabled = true) {
                            state.availableLanguages.forEachIndexed { index, language ->
                                LanguageSetupRow(
                                    language = language,
                                    selected = language == state.learningLanguage,
                                    enabled = true,
                                    multiSelect = false,
                                    showDivider = index > 0,
                                    onClick = { onLearningLanguageSelected(language) }
                                )
                            }
                        }
                    }

                    LanguageSetupSection(
                        number = 2,
                        label = stringResource(Res.string.language_setup_translate_into),
                        state = when {
                            !translateIntoEnabled -> SectionVisualState.Locked
                            state.nativeLanguages.isEmpty() -> SectionVisualState.Active
                            else -> SectionVisualState.Done
                        },
                        lockedHint = if (translateIntoEnabled) {
                            null
                        } else {
                            stringResource(Res.string.language_setup_choose_learning_first)
                        }
                    ) {
                        LanguageSetupListCard(enabled = translateIntoEnabled) {
                            translationLanguages.forEachIndexed { index, language ->
                                LanguageSetupRow(
                                    language = language,
                                    selected = translateIntoEnabled && language in state.nativeLanguages,
                                    enabled = translateIntoEnabled,
                                    multiSelect = true,
                                    showDivider = index > 0,
                                    onClick = { onNativeLanguageToggled(language) }
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(AppSpacing.lg))

                Text(
                    text = stringResource(Res.string.language_setup_update_anytime),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = MaterialTheme.serifFontFamily,
                        fontStyle = FontStyle.Italic
                    ),
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
                        stringResource(Res.string.common_next),
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

private enum class SectionVisualState {
    Active,
    Done,
    Locked
}

@Composable
private fun LanguageSetupSection(
    number: Int,
    label: String,
    state: SectionVisualState,
    lockedHint: String?,
    content: @Composable () -> Unit
) {
    val active = state != SectionVisualState.Locked

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = AppSpacing.xs, bottom = AppSpacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .background(
                        color = if (active) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.background
                        },
                        shape = CircleShape
                    )
                    .border(
                        width = 1.dp,
                        color = if (active) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                        shape = CircleShape
                    )
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (state == SectionVisualState.Done) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = stringResource(Res.string.language_setup_step_completed, number),
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(
                        text = number.toString(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = if (active) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            Spacer(Modifier.width(AppSpacing.sm))

            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = if (active) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )

            if (lockedHint != null) {
                Spacer(Modifier.weight(1f))
                Text(
                    text = lockedHint,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = MaterialTheme.serifFontFamily,
                        fontStyle = FontStyle.Italic
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End
                )
            }
        }

        content()
    }
}

@Composable
private fun LanguageSetupListCard(
    enabled: Boolean,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.5f),
        shape = RoundedCornerShape(16.dp),
        color = if (enabled) {
            MaterialTheme.colorScheme.surface
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (enabled) {
                MaterialTheme.colorScheme.outlineVariant
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            }
        )
    ) {
        Column(content = content)
    }
}

@Composable
private fun LanguageSetupRow(
    language: Language,
    selected: Boolean,
    enabled: Boolean,
    multiSelect: Boolean,
    showDivider: Boolean,
    onClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (showDivider) {
            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (multiSelect) {
                        Modifier.toggleable(
                            value = selected,
                            enabled = enabled,
                            role = Role.Checkbox,
                            onValueChange = { onClick() }
                        )
                    } else {
                        Modifier.selectable(
                            selected = selected,
                            enabled = enabled,
                            role = Role.RadioButton,
                            onClick = onClick
                        )
                    }
                )
                .semantics(mergeDescendants = true) {}
                .background(
                    if (selected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                    } else {
                        MaterialTheme.colorScheme.surface.copy(alpha = 0f)
                    }
                )
                .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = language.flag,
                    modifier = Modifier.width(28.dp),
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(Modifier.width(AppSpacing.md))

                Text(
                    text = language.selfName,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            SelectionIndicator(
                selected = selected,
                multiSelect = multiSelect,
                enabled = enabled
            )
        }
    }
}

@Composable
private fun SelectionIndicator(
    selected: Boolean,
    multiSelect: Boolean,
    enabled: Boolean
) {
    val shape = if (multiSelect) RoundedCornerShape(6.dp) else CircleShape
    val outlineColor = if (enabled) {
        MaterialTheme.colorScheme.outline
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
    }

    Box(
        modifier = Modifier
            .size(22.dp)
            .background(
                color = if (selected && multiSelect) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surface.copy(alpha = 0f)
                },
                shape = shape
            )
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else outlineColor,
                shape = shape
            )
            .clip(shape),
        contentAlignment = Alignment.Center
    ) {
        when {
            selected && multiSelect -> Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onPrimary
            )

            selected -> Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
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
private fun LanguageSetupScreenLearningOnlyPreview(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        LanguageSetupScreenContent(
            state = LanguageSetupUiState(
                isLoading = false,
                availableLanguages = Language.entries,
                learningLanguage = Language.DUTCH
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
