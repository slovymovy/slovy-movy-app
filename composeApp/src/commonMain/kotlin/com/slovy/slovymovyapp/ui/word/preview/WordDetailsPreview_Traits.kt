package com.slovy.slovymovyapp.ui.word.preview

import androidx.compose.runtime.Composable
import com.slovy.slovymovyapp.ui.ThemePreviewProvider
import com.slovy.slovymovyapp.ui.ThemedPreview
import com.slovy.slovymovyapp.ui.word.WordDetailScreenContent
import com.slovy.slovymovyapp.ui.word.toContentUiState
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.jetbrains.compose.ui.tooling.preview.PreviewParameter

@Preview
@Composable
private fun WordDetailScreenPreviewWithTraits(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        WordDetailScreenContent(state = sampleWordWithTraits().toContentUiState(isSenseFavorite = isSenseFavoritePreview))
    }
}

@Preview
@Composable
private fun WordDetailScreenPreviewWithNameTypes(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        WordDetailScreenContent(state = sampleRichmondCard().toContentUiState(isSenseFavorite = isSenseFavoritePreview))
    }
}

@Preview
@Composable
private fun WordDetailScreenPreviewAllTraitTypes(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        WordDetailScreenContent(state = sampleAllTraitTypesCard().toContentUiState(isSenseFavorite = isSenseFavoritePreview))
    }
}

@Preview
@Composable
private fun WordDetailScreenPreviewVeryLongWord(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        WordDetailScreenContent(state = sampleVeryLongWordCard().toContentUiState(isSenseFavorite = isSenseFavoritePreview))
    }
}
