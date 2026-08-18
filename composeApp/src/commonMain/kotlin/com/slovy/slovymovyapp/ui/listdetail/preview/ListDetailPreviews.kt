package com.slovy.slovymovyapp.ui.listdetail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.lists.WordList
import com.slovy.slovymovyapp.data.lists.WordListSense
import com.slovy.slovymovyapp.data.remote.LearnerLevel
import com.slovy.slovymovyapp.data.remote.SenseFrequency
import com.slovy.slovymovyapp.speech.RowAudioUiState
import com.slovy.slovymovyapp.ui.ThemePreviewProvider
import com.slovy.slovymovyapp.ui.ThemedPreview

private fun previewWordList() = WordList(
    id = "nl_a1_basic",
    title = mapOf("en" to "500 first Dutch words"),
    subtitle = mapOf("en" to "This is where your journey begins"),
    labels = mapOf("en" to listOf("A1", "Basic")),
    senses = List(3) { WordListSense(senseId = it.toString(), lemma = "woord$it", language = Language.DUTCH) },
    iconSvg = null,
)

@Preview
@Composable
private fun ListDetailPreviewContent(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        ListDetailContent(
            list = previewWordList(),
            language = Language.DUTCH,
            state = ListDetailUiState(
                items = listOf(
                    ListWordItem(
                        senseId = "1",
                        lemma = "huis",
                        definition = "a building where people live",
                        learnerLevel = LearnerLevel.A1,
                        frequency = SenseFrequency.HIGH,
                        isFavorited = true,
                    ),
                    ListWordItem(
                        senseId = "2",
                        lemma = "fiets",
                        definition = "a vehicle with two wheels that you ride by pushing pedals",
                        learnerLevel = LearnerLevel.A1,
                        frequency = SenseFrequency.MIDDLE,
                    ),
                    ListWordItem(
                        senseId = "3",
                        lemma = "gezellig",
                        expanded = true,
                        loading = true,
                    ),
                ),
                isLoading = false,
                favoriteLemmas = setOf("huis"),
            ),
        )
    }
}

@Preview
@Composable
private fun ListDetailPreviewTranslationRepair(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        ListDetailContent(
            list = previewWordList(),
            language = Language.DUTCH,
            state = ListDetailUiState(
                items = listOf(
                    // Row 1 is having its missing translation fetched from the server: it keeps
                    // rendering its local content and shows a translation progress placeholder.
                    ListWordItem(
                        senseId = "1",
                        lemma = "huis",
                        definition = "a building where people live",
                        learnerLevel = LearnerLevel.A1,
                        frequency = SenseFrequency.HIGH,
                        translationLoading = true,
                    ),
                    ListWordItem(
                        senseId = "2",
                        lemma = "fiets",
                        definition = "a vehicle with two wheels that you ride by pushing pedals",
                        learnerLevel = LearnerLevel.A1,
                        frequency = SenseFrequency.MIDDLE,
                    ),
                ),
                isLoading = false,
                favoriteLemmas = setOf("huis"),
            ),
        )
    }
}

@Preview
@Composable
private fun ListDetailPreviewRowPlaying(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        ListDetailContent(
            list = previewWordList(),
            language = Language.DUTCH,
            state = ListDetailUiState(
                items = listOf(
                    ListWordItem(
                        senseId = "1",
                        lemma = "huis",
                        definition = "a building where people live",
                        learnerLevel = LearnerLevel.A1,
                        frequency = SenseFrequency.HIGH,
                        isFavorited = true,
                    ),
                    ListWordItem(
                        senseId = "2",
                        lemma = "fiets",
                        definition = "a vehicle with two wheels that you ride by pushing pedals",
                        learnerLevel = LearnerLevel.A1,
                        frequency = SenseFrequency.MIDDLE,
                    ),
                ),
                isLoading = false,
                favoriteLemmas = setOf("huis"),
            ),
            // Row 1 is speaking (stop glyph); tapping any other row would move playback there.
            rowAudio = RowAudioUiState(playingSenseId = "1"),
        )
    }
}

@Preview
@Composable
private fun ListDetailPreviewAllInMyWords(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        ListDetailContent(
            list = previewWordList(),
            language = Language.DUTCH,
            state = ListDetailUiState(
                items = listOf(
                    ListWordItem(
                        senseId = "1",
                        lemma = "huis",
                        definition = "a building where people live",
                        learnerLevel = LearnerLevel.A1,
                        frequency = SenseFrequency.HIGH,
                        isFavorited = true,
                    ),
                    ListWordItem(
                        senseId = "2",
                        lemma = "fiets",
                        definition = "a vehicle with two wheels that you ride by pushing pedals",
                        learnerLevel = LearnerLevel.A1,
                        frequency = SenseFrequency.MIDDLE,
                        isFavorited = true,
                    ),
                ),
                isLoading = false,
                favoriteLemmas = setOf("huis", "fiets"),
            ),
        )
    }
}

@Preview
@Composable
private fun ListDetailPreviewLoading(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        ListDetailContent(
            list = previewWordList(),
            language = Language.DUTCH,
            state = ListDetailUiState(isLoading = true),
        )
    }
}

@Preview
@Composable
private fun ListDetailPreviewEmpty(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        ListDetailContent(
            list = previewWordList(),
            language = Language.DUTCH,
            state = ListDetailUiState(isLoading = false),
        )
    }
}

@Preview
@Composable
private fun ListDetailPreviewError(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        ListDetailErrorScreen(onBack = {}, onRetry = {})
    }
}

