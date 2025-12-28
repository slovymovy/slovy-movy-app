package com.slovy.slovymovyapp.ui.word.preview

import androidx.compose.runtime.Composable
import com.slovy.slovymovyapp.ui.ThemePreviewProvider
import com.slovy.slovymovyapp.ui.ThemedPreview
import com.slovy.slovymovyapp.ui.word.WordDetailScreenContent
import com.slovy.slovymovyapp.ui.word.toContentUiState
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.jetbrains.compose.ui.tooling.preview.PreviewParameter

// Edge case preview functions
@Preview
@Composable
private fun WordDetailScreenPreviewNoTranslations(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        // Word with no translations available
        WordDetailScreenContent(state = sampleNoTranslationCard().toContentUiState(isSenseFavorite = isSenseFavoritePreview))
    }
}

// Edge case preview functions
@Preview
@Composable
private fun WordDetailScreenPreviewNoTranslationsAllExpanded(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
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

@Preview
@Composable
private fun WordDetailScreenPreviewNoTranslationsCollapsed(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
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

@Preview
@Composable
private fun WordDetailScreenPreviewMultilingual(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        // Dutch word with both Russian and English translations
        WordDetailScreenContent(state = sampleMultilingualCard().toContentUiState(isSenseFavorite = isSenseFavoritePreview))
    }
}

@Preview
@Composable
private fun WordDetailScreenPreviewMultilingualCollapsed(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
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
