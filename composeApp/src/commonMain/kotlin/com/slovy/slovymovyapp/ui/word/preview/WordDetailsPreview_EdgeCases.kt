package com.slovy.slovymovyapp.ui.word.preview

import androidx.compose.runtime.Composable
import com.slovy.slovymovyapp.ui.ThemePreviewProvider
import com.slovy.slovymovyapp.ui.ThemedPreview
import com.slovy.slovymovyapp.ui.word.WordDetailScreenContent
import com.slovy.slovymovyapp.ui.word.toContentUiState

// Edge case preview functions
@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun WordDetailScreenPreviewNoTranslations(
    @androidx.compose.ui.tooling.preview.PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        // Word with no translations available
        WordDetailScreenContent(state = sampleNoTranslationCard().toContentUiState(isSenseFavorite = isSenseFavoritePreview))
    }
}

// Edge case preview functions
@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun WordDetailScreenPreviewNoTranslationsAllExpanded(
    @androidx.compose.ui.tooling.preview.PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        val base = sampleNoTranslationCard().toContentUiState(isSenseFavorite = isSenseFavoritePreview)
        val expanded = base.entries.map { entryState ->
            entryState.copy(
                expanded = true,
                formsExpanded = true,
                senses = entryState.senses.map { senseState ->
                    senseState.copy(
                        expanded = true,
                        examplesExpanded = true,
                        languageExpanded = senseState.languageExpanded.mapValues { true }
                    )
                }
            )
        }
        WordDetailScreenContent(
            state = base.copy(entries = expanded)
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun WordDetailScreenPreviewNoTranslationsCollapsed(
    @androidx.compose.ui.tooling.preview.PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        val base = sampleNoTranslationCard().toContentUiState(isSenseFavorite = isSenseFavoritePreview)
        val collapsedEntries = base.entries.map { entryState ->
            entryState.copy(
                expanded = false,
                formsExpanded = false,
                senses = entryState.senses.map { senseState ->
                    senseState.copy(
                        expanded = false,
                        examplesExpanded = false,
                        languageExpanded = senseState.languageExpanded.mapValues { false }
                    )
                }
            )
        }
        WordDetailScreenContent(
            state = base.copy(entries = collapsedEntries)
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun WordDetailScreenPreviewMultilingual(
    @androidx.compose.ui.tooling.preview.PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        // Dutch word with both Russian and English translations
        WordDetailScreenContent(state = sampleMultilingualCard().toContentUiState(isSenseFavorite = isSenseFavoritePreview))
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun WordDetailScreenPreviewMultilingualCollapsed(
    @androidx.compose.ui.tooling.preview.PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        val base = sampleMultilingualCard().toContentUiState(isSenseFavorite = isSenseFavoritePreview)
        val collapsedEntries = base.entries.map { entryState ->
            entryState.copy(
                expanded = false,
                formsExpanded = false,
                senses = entryState.senses.map { senseState ->
                    senseState.copy(
                        expanded = false,
                        examplesExpanded = false,
                        languageExpanded = senseState.languageExpanded.mapValues { false }
                    )
                }
            )
        }
        WordDetailScreenContent(
            state = base.copy(entries = collapsedEntries)
        )
    }
}
