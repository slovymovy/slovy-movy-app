package com.slovy.slovymovyapp.ui.word.preview

import androidx.compose.runtime.Composable
import com.slovy.slovymovyapp.data.remote.RelatedWord
import com.slovy.slovymovyapp.ui.ThemePreviewProvider
import com.slovy.slovymovyapp.ui.ThemedPreview
import com.slovy.slovymovyapp.ui.word.WordDetailScreenContent
import com.slovy.slovymovyapp.ui.word.toContentUiState
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.jetbrains.compose.ui.tooling.preview.PreviewParameter

@Preview
@Composable
private fun WordDetailScreenPreviewWithWordFamily(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        WordDetailScreenContent(
            state = sampleDoubleCard().toContentUiState(
                isSenseFavorite = isSenseFavoritePreview,
                wordFamilyExpanded = true
            )
        )
    }
}

@Preview
@Composable
private fun WordDetailScreenPreviewWithWordFamilyCollapsed(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        WordDetailScreenContent(state = sampleDoubleCard().toContentUiState(isSenseFavorite = isSenseFavoritePreview))
    }
}

@Preview
@Composable
private fun WordDetailScreenPreviewClickableRelatedWords(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        WordDetailScreenContent(
            state = sampleDoubleCard().toContentUiState(
                isSenseFavorite = isSenseFavoritePreview,
                wordFamilyExpanded = true
            ),
            onWordClick = { /* Preview - no action */ }
        )
    }
}

@Preview
@Composable
private fun WordDetailScreenPreviewWithRelatedWordsInSynonyms(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        val programmaticallyWithRelated = sampleProgrammaticallyCard().copy(
            relatedWords = mapOf(
                "systematically" to RelatedWord("systematically", 3.9f, false),
                "methodically" to RelatedWord("methodically", 3.2f, false),
                "randomly" to RelatedWord("randomly", 4.1f, false),
                "haphazardly" to RelatedWord("haphazardly", 2.7f, false)
            )
        )
        WordDetailScreenContent(
            state = programmaticallyWithRelated.toContentUiState(isSenseFavorite = isSenseFavoritePreview),
            onWordClick = { /* Preview - no action */ }
        )
    }
}

@Preview
@Composable
private fun WordDetailScreenPreviewWithClickableHighlightedWords(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        val amazonWithRelated = sampleAmazonCard().copy(
            relatedWords = mapOf(
                "Amazons" to RelatedWord("Amazons", 3.2f, false),
                "Amazon" to RelatedWord("Amazon", 5.1f, false),
                "parrot" to RelatedWord("parrot", 4.3f, false),
                "river" to RelatedWord("river", 5.8f, false),
                "warrior" to RelatedWord("warrior", 4.7f, false)
            )
        )
        val base = amazonWithRelated.toContentUiState(
            isSenseFavorite = isSenseFavoritePreview,
        )
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
            state = base.copy(entries = expanded),
            onWordClick = { /* Preview - no action */ }
        )
    }
}
