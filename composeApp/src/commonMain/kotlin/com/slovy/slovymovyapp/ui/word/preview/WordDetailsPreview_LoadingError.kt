package com.slovy.slovymovyapp.ui.word.preview

import androidx.compose.runtime.Composable
import com.slovy.slovymovyapp.data.remote.PartOfSpeech
import com.slovy.slovymovyapp.i18n.UiText
import com.slovy.slovymovyapp.ui.ThemePreviewProvider
import com.slovy.slovymovyapp.ui.ThemedPreview
import com.slovy.slovymovyapp.ui.word.WordDetailScreenContent
import com.slovy.slovymovyapp.ui.word.WordDetailUiState
import com.slovy.slovymovyapp.ui.word.toContentUiState

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun WordDetailScreenPreviewCardLoading(
    @androidx.compose.ui.tooling.preview.PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
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

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun WordDetailScreenPreviewCardLoadingMultiPos(
    @androidx.compose.ui.tooling.preview.PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
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

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun WordDetailScreenPreviewTranslationLoading(
    @androidx.compose.ui.tooling.preview.PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        val base = sampleNoTranslationCard().toContentUiState(isSenseFavorite = isSenseFavoritePreview)
        WordDetailScreenContent(
            state = base.copy(translationLoading = true)
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun WordDetailScreenPreviewSenseTranslationLoading(
    @androidx.compose.ui.tooling.preview.PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
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

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun WordDetailScreenPreviewCardError(
    @androidx.compose.ui.tooling.preview.PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        val loadingCard = createLoadingCard("testing", listOf(PartOfSpeech.NOUN))
        WordDetailScreenContent(
            state = WordDetailUiState.Content(
                card = loadingCard,
                entries = emptyList(),
                cardError = UiText.Plain("Failed to load card data")
            )
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun WordDetailScreenPreviewCardErrorMultiPos(
    @androidx.compose.ui.tooling.preview.PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        val loadingCard = createLoadingCard("amazon", listOf(PartOfSpeech.NOUN, PartOfSpeech.NAME))
        WordDetailScreenContent(
            state = WordDetailUiState.Content(
                card = loadingCard,
                entries = emptyList(),
                cardError = UiText.Plain("Network error: Unable to connect")
            )
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun WordDetailScreenPreviewSenseTranslationError(
    @androidx.compose.ui.tooling.preview.PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        val base = sampleNoTranslationCard().toContentUiState(isSenseFavorite = isSenseFavoritePreview)
        val entriesWithError = base.entries.mapIndexed { idx, entry ->
            if (idx == 0) {
                entry.copy(senses = entry.senses.mapIndexed { sIdx, sense ->
                    if (sIdx == 0) sense.copy(translationError = UiText.Plain("Failed to translate"), expanded = true) else sense
                })
            } else entry
        }
        WordDetailScreenContent(state = base.copy(entries = entriesWithError))
    }
}
