package com.slovy.slovymovyapp.ui.word.preview

import androidx.compose.runtime.Composable
import com.slovy.slovymovyapp.ui.ThemePreviewProvider
import com.slovy.slovymovyapp.ui.ThemedPreview
import com.slovy.slovymovyapp.ui.word.WordDetailScreenContent
import com.slovy.slovymovyapp.ui.word.WordDetailUiState
import com.slovy.slovymovyapp.ui.word.toContentUiState

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun WordDetailScreenPreviewContent(
    @androidx.compose.ui.tooling.preview.PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        WordDetailScreenContent(state = sampleTestingCard().toContentUiState(isSenseFavorite = isSenseFavoritePreview))
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun WordDetailScreenPreviewCollapsed(
    @androidx.compose.ui.tooling.preview.PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        val base = sampleTestingCard().toContentUiState(isSenseFavorite = isSenseFavoritePreview)
        val collapsedEntries = base.entries.map { entryState ->
            entryState.copy(
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
private fun WordDetailScreenPreviewLoading(
    @androidx.compose.ui.tooling.preview.PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        WordDetailScreenContent(
            state = WordDetailUiState.Empty(
                lemma = "Word",
                isLoading = true,
                message = "Loading..."
            )
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun WordDetailScreenPreviewError(
    @androidx.compose.ui.tooling.preview.PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        WordDetailScreenContent(
            state = WordDetailUiState.Empty(
                lemma = "Word",
                isError = true,
                message = "No such word found"
            )
        )
    }
}
