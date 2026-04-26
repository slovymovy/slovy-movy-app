package com.slovy.slovymovyapp.ui.word.preview

import androidx.compose.runtime.Composable
import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.ui.ThemePreviewProvider
import com.slovy.slovymovyapp.ui.ThemedPreview
import com.slovy.slovymovyapp.ui.word.WordDetailScreenContent
import com.slovy.slovymovyapp.ui.word.toContentUiState

// Special UI state previews for different word complexity
@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun WordDetailScreenPreviewSimpleWord(
    @androidx.compose.ui.tooling.preview.PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        // Simple single-sense word like "celebration"
        WordDetailScreenContent(state = sampleCelebrationCard().toContentUiState(isSenseFavorite = isSenseFavoritePreview))
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun WordDetailScreenPreviewComplexWord(
    @androidx.compose.ui.tooling.preview.PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        // Complex multi-POS word like "amazon"
        WordDetailScreenContent(state = sampleAmazonCard().toContentUiState(isSenseFavorite = isSenseFavoritePreview))
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun WordDetailScreenPreviewProperNoun(
    @androidx.compose.ui.tooling.preview.PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        // Proper noun with multiple geographical meanings like "Richmond"
        WordDetailScreenContent(state = sampleRichmondCard().toContentUiState(isSenseFavorite = isSenseFavoritePreview))
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun WordDetailScreenPreviewAdverb(
    @androidx.compose.ui.tooling.preview.PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        // Technical adverb like "programmatically"
        WordDetailScreenContent(state = sampleProgrammaticallyCard().toContentUiState(isSenseFavorite = isSenseFavoritePreview))
    }
}

// UI state edge cases
@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun WordDetailScreenPreviewPartiallyExpanded(
    @androidx.compose.ui.tooling.preview.PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        // Show a complex word with some sections expanded and others collapsed
        val base = sampleMultilingualCard().toContentUiState(isSenseFavorite = isSenseFavoritePreview)
        val mixedEntries = base.entries.mapIndexed { entryIndex, entryState ->
            entryState.copy(
                formsExpanded = entryIndex == 0, // Only first entry has forms expanded
                senses = entryState.senses.mapIndexed { senseIndex, senseState ->
                    senseState.copy(
                        expanded = senseIndex == 0, // Only first sense expanded
                        examplesExpanded = senseIndex == 0 && entryIndex == 0, // Only first sense of first entry has examples expanded
                        languageExpanded = senseState.languageExpanded.mapValues { (lang, _) ->
                            lang == Language.RUSSIAN && senseIndex == 0 // Only Russian expanded for first sense
                        }
                    )
                }
            )
        }
        WordDetailScreenContent(
            state = base.copy(entries = mixedEntries)
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun WordDetailScreenPreviewHighFrequencyWord(
    @androidx.compose.ui.tooling.preview.PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        // Show a high-frequency word (celebration)
        WordDetailScreenContent(state = sampleCelebrationCard().toContentUiState(isSenseFavorite = isSenseFavoritePreview))
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun WordDetailScreenPreviewLowFrequencyWord(
    @androidx.compose.ui.tooling.preview.PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        // Show a low-frequency/rare word (whippersnapper)
        WordDetailScreenContent(state = sampleNoTranslationCard().toContentUiState(isSenseFavorite = isSenseFavoritePreview))
    }
}
