package com.slovy.slovymovyapp.ui.word.preview

import androidx.compose.runtime.Composable
import com.slovy.slovymovyapp.ui.ThemePreviewProvider
import com.slovy.slovymovyapp.ui.ThemedPreview
import com.slovy.slovymovyapp.ui.word.WordDetailScreenContent
import com.slovy.slovymovyapp.ui.word.toContentUiState

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun WordDetailScreenPreviewTargetSenseAmazon(
    @androidx.compose.ui.tooling.preview.PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        WordDetailScreenContent(
            state = sampleAmazonCard().toContentUiState(
                targetSenseId = "4f67890a-bcde-5678-9012-34567890abcd",
                isSenseFavorite = isSenseFavoritePreview,
            )
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun WordDetailScreenPreviewTargetSenseAmazonParrot(
    @androidx.compose.ui.tooling.preview.PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        WordDetailScreenContent(
            state = sampleAmazonCard().toContentUiState(
                targetSenseId = "8c2403c5-1510-45cb-9112-304f78772f96",
                isSenseFavorite = isSenseFavoritePreview,
            )
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun WordDetailScreenPreviewTargetSenseMultilingual(
    @androidx.compose.ui.tooling.preview.PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        WordDetailScreenContent(
            state = sampleMultilingualCard().toContentUiState(
                targetSenseId = "d4e5f6a7-b8c9-0123-def0-456789012345",
                isSenseFavorite = isSenseFavoritePreview,
            )
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun WordDetailScreenPreviewTargetSenseRichmondVirginia(
    @androidx.compose.ui.tooling.preview.PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        WordDetailScreenContent(
            state = sampleRichmondCard().toContentUiState(
                targetSenseId = "2556596a-2eae-4d77-bbb2-dada74364b55",
                isSenseFavorite = isSenseFavoritePreview,
            )
        )
    }
}
