package com.slovy.slovymovyapp.ui.word.preview

import androidx.compose.runtime.Composable
import com.slovy.slovymovyapp.data.remote.PartOfSpeech
import com.slovy.slovymovyapp.ui.ThemePreviewProvider
import com.slovy.slovymovyapp.ui.ThemedPreview
import com.slovy.slovymovyapp.ui.word.WordDetailScreenContent
import com.slovy.slovymovyapp.ui.word.WordDetailUiState
import com.slovy.slovymovyapp.ui.word.toContentUiState
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.jetbrains.compose.ui.tooling.preview.PreviewParameter

@Preview
@Composable
private fun WordDetailScreenPreviewCardLoading(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        val loadingCard = createLoadingCard("testing", listOf(PartOfSpeech.NOUN))
        WordDetailScreenContent(
            state = WordDetailUiState.Content(
                card = loadingCard,
                entries = emptyList(),
                cardLoading = true
            )
        )
    }
}

@Preview
@Composable
private fun WordDetailScreenPreviewCardLoadingMultiPos(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        val loadingCard = createLoadingCard("amazon", listOf(PartOfSpeech.NOUN, PartOfSpeech.NAME))
        WordDetailScreenContent(
            state = WordDetailUiState.Content(
                card = loadingCard,
                entries = emptyList(),
                cardLoading = true
            )
        )
    }
}

@Preview
@Composable
private fun WordDetailScreenPreviewTranslationLoading(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        val base = sampleNoTranslationCard().toContentUiState(isSenseFavorite = isSenseFavoritePreview)
        WordDetailScreenContent(
            state = base.copy(translationLoading = true)
        )
    }
}

@Preview
@Composable
private fun WordDetailScreenPreviewSenseTranslationLoading(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        val base = sampleNoTranslationCard().toContentUiState(isSenseFavorite = isSenseFavoritePreview)
        val entriesWithLoading = base.entries.mapIndexed { idx, entry ->
            if (idx == 0) {
                entry.copy(senses = entry.senses.mapIndexed { sIdx, sense ->
                    if (sIdx == 0) sense.copy(translationLoading = true, expanded = true) else sense
                })
            } else entry
        }
        WordDetailScreenContent(state = base.copy(entries = entriesWithLoading))
    }
}

@Preview
@Composable
private fun WordDetailScreenPreviewCardError(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        val loadingCard = createLoadingCard("testing", listOf(PartOfSpeech.NOUN))
        WordDetailScreenContent(
            state = WordDetailUiState.Content(
                card = loadingCard,
                entries = emptyList(),
                cardError = "Failed to load card data"
            )
        )
    }
}

@Preview
@Composable
private fun WordDetailScreenPreviewCardErrorMultiPos(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        val loadingCard = createLoadingCard("amazon", listOf(PartOfSpeech.NOUN, PartOfSpeech.NAME))
        WordDetailScreenContent(
            state = WordDetailUiState.Content(
                card = loadingCard,
                entries = emptyList(),
                cardError = "Network error: Unable to connect"
            )
        )
    }
}

@Preview
@Composable
private fun WordDetailScreenPreviewSenseTranslationError(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        val base = sampleNoTranslationCard().toContentUiState(isSenseFavorite = isSenseFavoritePreview)
        val entriesWithError = base.entries.mapIndexed { idx, entry ->
            if (idx == 0) {
                entry.copy(senses = entry.senses.mapIndexed { sIdx, sense ->
                    if (sIdx == 0) sense.copy(translationError = "Failed to translate", expanded = true) else sense
                })
            } else entry
        }
        WordDetailScreenContent(state = base.copy(entries = entriesWithError))
    }
}
