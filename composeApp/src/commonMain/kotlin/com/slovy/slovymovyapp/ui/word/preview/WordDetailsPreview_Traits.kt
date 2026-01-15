package com.slovy.slovymovyapp.ui.word.preview

import androidx.compose.runtime.Composable
import com.slovy.slovymovyapp.ui.ThemePreviewProvider
import com.slovy.slovymovyapp.ui.ThemedPreview
import com.slovy.slovymovyapp.ui.word.WordDetailScreenContent
import com.slovy.slovymovyapp.ui.word.toContentUiState

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun WordDetailScreenPreviewWithTraits(
    @androidx.compose.ui.tooling.preview.PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        WordDetailScreenContent(state = sampleWordWithTraits().toContentUiState(isSenseFavorite = isSenseFavoritePreview))
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun WordDetailScreenPreviewWithNameTypes(
    @androidx.compose.ui.tooling.preview.PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        WordDetailScreenContent(state = sampleRichmondCard().toContentUiState(isSenseFavorite = isSenseFavoritePreview))
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun WordDetailScreenPreviewAllTraitTypes(
    @androidx.compose.ui.tooling.preview.PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        WordDetailScreenContent(state = sampleAllTraitTypesCard().toContentUiState(isSenseFavorite = isSenseFavoritePreview))
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun WordDetailScreenPreviewVeryLongWord(
    @androidx.compose.ui.tooling.preview.PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        WordDetailScreenContent(state = sampleVeryLongWordCard().toContentUiState(isSenseFavorite = isSenseFavoritePreview))
    }
}
