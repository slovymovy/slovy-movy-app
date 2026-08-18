package com.slovy.slovymovyapp.ui.favorites.preview

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.remote.*
import com.slovy.slovymovyapp.i18n.ShortDuration
import com.slovy.slovymovyapp.i18n.UiText
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import slovymovyapp.composeapp.generated.resources.*
import com.slovy.slovymovyapp.ui.ThemePreviewProvider
import com.slovy.slovymovyapp.ui.ThemedPreview
import com.slovy.slovymovyapp.ui.favorites.FavoritesScreenContent
import com.slovy.slovymovyapp.ui.favorites.FavoritesStudyDoneAction
import com.slovy.slovymovyapp.ui.favorites.FavoritesStudyDoneUiState
import com.slovy.slovymovyapp.ui.favorites.FavoritesUiState

@Preview
@Composable
fun PreviewFavoritesScreenLoading(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        FavoritesScreenContent(state = FavoritesUiState.Loading)
    }
}

@Preview
@Composable
fun PreviewFavoritesScreenEmpty(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        FavoritesScreenContent(
            state = FavoritesUiState.Content(senses = emptyList(), hasAnyFavorites = false)
        )
    }
}

@Preview
@Composable
fun PreviewFavoritesScreenCollapsed(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        val state = FavoritesUiState.Content(
            senses = listOf(
                createSenseItem(
                    senseId = "run-1",
                    lemma = "run",
                    sense = createMockSense("run-1", "to move swiftly on foot", LearnerLevel.A1, SenseFrequency.HIGH),
                    pos = PartOfSpeech.VERB
                ),
                createSenseItem(
                    senseId = "book-1",
                    lemma = "book",
                    sense = createMockSense(
                        "book-1",
                        "a written or printed work",
                        LearnerLevel.A1,
                        SenseFrequency.HIGH
                    ),
                    pos = PartOfSpeech.NOUN
                )
            ),
            hasAnyFavorites = true
        )
        FavoritesScreenContent(state = state)
    }
}

@Preview
@Composable
fun PreviewFavoritesScreenStudyDone(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        val state = FavoritesUiState.Content(
            senses = listOf(
                createSenseItem(
                    senseId = "sleep-1",
                    lemma = "slapen",
                    sense = createMockSense("sleep-1", "to sleep", LearnerLevel.A1, SenseFrequency.HIGH),
                    pos = PartOfSpeech.VERB
                ),
                createSenseItem(
                    senseId = "gezelligheid-1",
                    lemma = "gezelligheid",
                    sense = createMockSense(
                        "gezelligheid-1",
                        "coziness, togetherness",
                        LearnerLevel.B2,
                        SenseFrequency.MIDDLE
                    ),
                    pos = PartOfSpeech.NOUN
                ),
                createSenseItem(
                    senseId = "uitzonderlijk-1",
                    lemma = "uitzonderlijk",
                    sense = createMockSense(
                        "uitzonderlijk-1",
                        "exceptional, remarkable",
                        LearnerLevel.C1,
                        SenseFrequency.LOW
                    ),
                    pos = PartOfSpeech.ADJECTIVE
                )
            ),
            hasAnyFavorites = true,
            availableLanguages = listOf(Language.DUTCH),
            selectedLanguage = Language.DUTCH,
            studyDone = FavoritesStudyDoneUiState(
                language = Language.DUTCH,
                nextReviewLabel = ShortDuration.uiText(totalMinutes = 4 * 60),
                nextReviewAccessibilityValue = UiText.Plural(
                    Res.plurals.favorites_study_done_duration_hours,
                    quantity = 4,
                    args = listOf(4),
                ),
                action = FavoritesStudyDoneAction.REVIEW_MORE,
                nextReviewAtEpochMs = 0L,
            ),
        )
        FavoritesScreenContent(state = state)
    }
}

@Preview
@Composable
fun PreviewFavoritesScreenExpanded(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        val state = FavoritesUiState.Content(
            senses = listOf(
                createSenseItem(
                    senseId = "happy-1",
                    lemma = "happy",
                    sense = createMockSense(
                        "happy-1",
                        "feeling or showing pleasure",
                        LearnerLevel.A2,
                        SenseFrequency.HIGH,
                        examples = listOf(
                            LanguageCardExample(
                                "I'm happy",
                                mapOf(Language.POLISH to "Jestem szczęśliwy")
                            )
                        )
                    ),
                    pos = PartOfSpeech.ADJECTIVE,
                    expanded = true
                )
            ),
            hasAnyFavorites = true
        )
        FavoritesScreenContent(state = state)
    }
}

@Preview
@Composable
fun PreviewFavoritesScreenWithSearch(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        val state = FavoritesUiState.Content(
            senses = listOf(
                createSenseItem(
                    senseId = "run-1",
                    lemma = "run",
                    sense = createMockSense("run-1", "to move swiftly on foot"),
                    pos = PartOfSpeech.VERB
                )
            ),
            query = "run",
            hasAnyFavorites = true
        )
        FavoritesScreenContent(state = state)
    }
}

@Preview
@Composable
fun PreviewFavoritesScreenNoResults(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        FavoritesScreenContent(
            state = FavoritesUiState.Content(senses = emptyList(), query = "xyz", hasAnyFavorites = true)
        )
    }
}

@Preview
@Composable
fun PreviewFavoritesScreenLoadingAndError(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        val state = FavoritesUiState.Content(
            senses = listOf(
                createSenseItem(
                    senseId = "ready-1",
                    lemma = "ready",
                    sense = createMockSense("ready-1", "completely prepared"),
                    pos = PartOfSpeech.ADJECTIVE
                ),
                createSenseItem(
                    senseId = "ready-2",
                    lemma = "ready",
                    loading = true
                ),
                createSenseItem(
                    senseId = "ready-3",
                    lemma = "ready",
                    error = UiText.Resource(Res.string.favorites_error_load_meaning_failed)
                )
            ),
            hasAnyFavorites = true
        )
        FavoritesScreenContent(state = state)
    }
}

@Preview
@Composable
fun PreviewFavoritesScreenMultiLanguage(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        val state = FavoritesUiState.Content(
            senses = listOf(
                createSenseItem(
                    senseId = "run-1",
                    lemma = "run",
                    targetLang = Language.ENGLISH,
                    sense = createMockSense("run-1", "to move swiftly on foot"),
                    pos = PartOfSpeech.VERB
                ),
                createSenseItem(
                    senseId = "book-1",
                    lemma = "book",
                    targetLang = Language.ENGLISH,
                    sense = createMockSense("book-1", "a written or printed work"),
                    pos = PartOfSpeech.NOUN
                )
            ),
            hasAnyFavorites = true,
            availableLanguages = listOf(Language.ENGLISH, Language.POLISH),
            selectedLanguage = Language.ENGLISH
        )
        FavoritesScreenContent(state = state)
    }
}
