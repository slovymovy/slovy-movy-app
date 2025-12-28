package com.slovy.slovymovyapp.ui.word.preview

import androidx.compose.runtime.Composable
import com.slovy.slovymovyapp.ui.ThemePreviewProvider
import com.slovy.slovymovyapp.ui.ThemedPreview
import com.slovy.slovymovyapp.ui.word.WordDetailScreenContent
import com.slovy.slovymovyapp.ui.word.toContentUiState
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.jetbrains.compose.ui.tooling.preview.PreviewParameter

// Amazon word previews - multiple POS and senses
@Preview
@Composable
private fun WordDetailScreenPreviewAmazon(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        WordDetailScreenContent(state = sampleAmazonCard().toContentUiState(isSenseFavorite = isSenseFavoritePreview))
    }
}

@Preview
@Composable
private fun WordDetailScreenPreviewAmazonCollapsed(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        val base = sampleAmazonCard().toContentUiState(isSenseFavorite = isSenseFavoritePreview)
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

// Celebration word previews - noun with multiple senses
@Preview
@Composable
private fun WordDetailScreenPreviewCelebration(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        WordDetailScreenContent(state = sampleCelebrationCard().toContentUiState(isSenseFavorite = isSenseFavoritePreview))
    }
}

@Preview
@Composable
private fun WordDetailScreenPreviewCelebrationCollapsed(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        val base = sampleCelebrationCard().toContentUiState(isSenseFavorite = isSenseFavoritePreview)
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

// Programmatically word previews - adverb
@Preview
@Composable
private fun WordDetailScreenPreviewProgrammatically(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        WordDetailScreenContent(state = sampleProgrammaticallyCard().toContentUiState(isSenseFavorite = isSenseFavoritePreview))
    }
}

@Preview
@Composable
private fun WordDetailScreenPreviewProgrammaticallyCollapsed(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        val base = sampleProgrammaticallyCard().toContentUiState(isSenseFavorite = isSenseFavoritePreview)
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

// Richmond word previews - proper noun/name with multiple places
@Preview
@Composable
private fun WordDetailScreenPreviewRichmond(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        WordDetailScreenContent(state = sampleRichmondCard().toContentUiState(isSenseFavorite = isSenseFavoritePreview))
    }
}

@Preview
@Composable
private fun WordDetailScreenPreviewRichmondCollapsed(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        val base = sampleRichmondCard().toContentUiState(isSenseFavorite = isSenseFavoritePreview)
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

// Dutch word previews - kwartier (quarter hour)
@Preview
@Composable
private fun WordDetailScreenPreviewKwartier(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        WordDetailScreenContent(state = sampleKwartierCard().toContentUiState(isSenseFavorite = isSenseFavoritePreview))
    }
}

@Preview
@Composable
private fun WordDetailScreenPreviewKwartierCollapsed(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        val base = sampleKwartierCard().toContentUiState(isSenseFavorite = isSenseFavoritePreview)
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

// Russian word previews - программа (program)
@Preview
@Composable
private fun WordDetailScreenPreviewProgramma(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        WordDetailScreenContent(state = sampleProgrammaCard().toContentUiState(isSenseFavorite = isSenseFavoritePreview))
    }
}

@Preview
@Composable
private fun WordDetailScreenPreviewProgrammaCollapsed(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        val base = sampleProgrammaCard().toContentUiState(isSenseFavorite = isSenseFavoritePreview)
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
