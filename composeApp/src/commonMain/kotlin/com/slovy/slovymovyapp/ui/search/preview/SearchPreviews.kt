package com.slovy.slovymovyapp.ui.search.preview

import com.slovy.slovymovyapp.ui.search.SearchScreenContent
import com.slovy.slovymovyapp.ui.search.SearchUiState

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.slovy.slovymovyapp.data.lists.WordList
import com.slovy.slovymovyapp.data.lists.WordListSense
import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.remote.DictionaryRepository
import com.slovy.slovymovyapp.data.remote.PartOfSpeech
import kotlinx.coroutines.flow.*
import slovymovyapp.composeapp.generated.resources.*
import kotlin.uuid.Uuid
import com.slovy.slovymovyapp.ui.ThemePreviewProvider
import com.slovy.slovymovyapp.ui.ThemedPreview

@Preview
@Composable
private fun SearchScreenPreviewEmptyQuery(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        SearchScreenContent(
            state = SearchUiState(
                query = "",
                results = emptyList(),
                showNoResults = false,
                availableLanguages = listOf(Language.ENGLISH, Language.RUSSIAN),
                selectedLanguage = Language.ENGLISH,
                wordSuggestions = listOf("the", "be", "to", "of", "and"),
                favoriteLemmas = listOf("world", "time", "love")
            ),
        )
    }
}

@Preview
@Composable
private fun SearchScreenPreviewWithLists(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        SearchScreenContent(
            state = SearchUiState(
                query = "",
                results = emptyList(),
                showNoResults = false,
                availableLanguages = listOf(Language.DUTCH),
                selectedLanguage = Language.DUTCH,
                wordSuggestions = listOf("de", "het", "een", "zijn", "hebben", "gezelligheid", "fiets"),
                curatedLists = listOf(
                    WordList(
                        id = "nl_a1_basic",
                        title = mapOf("en" to "500 first Dutch words", "nl" to "500 eerste Nederlandse woorden"),
                        subtitle = mapOf("en" to "This is where your journey begins", "nl" to "Hier begint jouw reis"),
                        labels = mapOf("en" to listOf("A1", "Basic"), "nl" to listOf("A1", "Basis")),
                        senses = List(500) { WordListSense(senseId = it.toString(), lemma = "woord$it", language = Language.DUTCH) },
                        iconSvg = null,
                    ),
                    WordList(
                        id = "nl_doctor",
                        title = mapOf("en" to "At the doctor's", "nl" to "Bij de huisarts"),
                        subtitle = mapOf("en" to "Words for your next appointment", "nl" to "Woorden voor je afspraak"),
                        labels = mapOf("en" to listOf("A2")),
                        senses = List(64) { WordListSense(senseId = it.toString(), lemma = "woord$it", language = Language.DUTCH) },
                        iconSvg = null,
                    ),
                    WordList(
                        id = "nl_digital_life",
                        title = mapOf("en" to "Digital life", "nl" to "Digitaal leven"),
                        subtitle = mapOf("en" to "Phones, apps and the web", "nl" to "Telefoons, apps en het web"),
                        labels = emptyMap(),
                        senses = List(120) { WordListSense(senseId = it.toString(), lemma = "woord$it", language = Language.DUTCH) },
                        iconSvg = null,
                    )
                )
            ),
        )
    }
}

@Preview
@Composable
private fun SearchScreenPreviewNoDictionary(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        SearchScreenContent(
            state = SearchUiState(
                query = "",
                results = emptyList(),
                showNoResults = false,
                availableLanguages = emptyList(),
                selectedLanguage = null
            ),
        )
    }
}

@Preview
@Composable
private fun SearchScreenPreviewWithResults(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        SearchScreenContent(
            state = SearchUiState(
                query = "cel",
                results = listOf(
                    DictionaryRepository.SearchItem(
                        language = Language.ENGLISH,
                        lemmaId = Uuid.parse("00000000-0000-0000-0000-000000000001"),
                        lemma = "celebration",
                        display = "celebration",
                        zipfFrequency = 4.5f,
                        pos = listOf(PartOfSpeech.NOUN),
                        isFavorite = true,
                        onlineOnly = false
                    ),
                    DictionaryRepository.SearchItem(
                        language = Language.ENGLISH,
                        lemmaId = Uuid.parse("00000000-0000-0000-0000-000000000002"),
                        lemma = "celebrity",
                        display = "celebrity",
                        zipfFrequency = 4.3f,
                        pos = listOf(PartOfSpeech.NOUN),
                        onlineOnly = false
                    ),
                    DictionaryRepository.SearchItem(
                        language = Language.ENGLISH,
                        lemmaId = Uuid.parse("00000000-0000-0000-0000-000000000003"),
                        lemma = "celestial",
                        display = "celestial",
                        zipfFrequency = 3.8f,
                        pos = listOf(PartOfSpeech.ADJECTIVE),
                        onlineOnly = true
                    ),
                    DictionaryRepository.SearchItem(
                        language = Language.ENGLISH,
                        lemmaId = Uuid.parse("00000000-0000-0000-0000-000000000004"),
                        lemma = "cell",
                        display = "cell",
                        zipfFrequency = 5.2f,
                        pos = listOf(PartOfSpeech.NOUN),
                        isFavorite = true,
                        onlineOnly = false
                    )
                ),
                showNoResults = false,
                availableLanguages = listOf(Language.ENGLISH, Language.RUSSIAN),
                selectedLanguage = Language.ENGLISH
            ),
        )
    }
}

@Preview
@Composable
private fun SearchScreenPreviewMultilingualResults(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        SearchScreenContent(
            state = SearchUiState(
                query = "program",
                results = listOf(
                    DictionaryRepository.SearchItem(
                        language = Language.ENGLISH,
                        lemmaId = Uuid.parse("00000000-0000-0000-0000-000000000001"),
                        lemma = "program",
                        display = "program",
                        zipfFrequency = 5.5f,
                        pos = listOf(PartOfSpeech.NOUN, PartOfSpeech.VERB),
                        isFavorite = true,
                        onlineOnly = false
                    ),
                    DictionaryRepository.SearchItem(
                        language = Language.ENGLISH,
                        lemmaId = Uuid.parse("00000000-0000-0000-0000-000000000002"),
                        lemma = "programmatically",
                        display = "programmatically",
                        zipfFrequency = 3.2f,
                        pos = listOf(PartOfSpeech.ADVERB),
                        onlineOnly = false
                    ),
                    DictionaryRepository.SearchItem(
                        language = Language.RUSSIAN,
                        lemmaId = Uuid.parse("00000000-0000-0000-0000-000000000003"),
                        lemma = "программа",
                        display = "программа",
                        zipfFrequency = 5.8f,
                        pos = listOf(PartOfSpeech.NOUN),
                        isFavorite = true,
                        onlineOnly = false
                    ),
                    DictionaryRepository.SearchItem(
                        language = Language.POLISH,
                        lemmaId = Uuid.parse("00000000-0000-0000-0000-000000000004"),
                        lemma = "program",
                        display = "program",
                        zipfFrequency = 5.4f,
                        pos = listOf(PartOfSpeech.NOUN),
                        onlineOnly = true
                    )
                ),
                showNoResults = false,
                showLanguageIndicators = true, // Multiple dictionaries - show language badges
                availableLanguages = listOf(Language.ENGLISH, Language.RUSSIAN, Language.POLISH)
            ),
        )
    }
}

@Preview
@Composable
private fun SearchScreenPreviewNoResults(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        SearchScreenContent(
            state = SearchUiState(
                query = "xyzabc123",
                results = emptyList(),
                showNoResults = true,
                availableLanguages = listOf(Language.ENGLISH)
            ),
        )
    }
}

@Preview
@Composable
private fun SearchScreenPreviewInfoDialog(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        SearchScreenContent(
            state = SearchUiState(
                query = "world",
                results = listOf(
                    DictionaryRepository.SearchItem(
                        language = Language.ENGLISH,
                        lemmaId = Uuid.parse("00000000-0000-0000-0000-000000000001"),
                        lemma = "world",
                        display = "world",
                        zipfFrequency = 6.2f,
                        pos = listOf(PartOfSpeech.NOUN),
                        isFavorite = true,
                        onlineOnly = false
                    )
                ),
                showNoResults = false,
                availableLanguages = listOf(Language.ENGLISH)
            ),
        )
    }
}

@Preview
@Composable
private fun SearchScreenPreviewSingleLanguage(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        SearchScreenContent(
            state = SearchUiState(
                query = "bib",
                results = listOf(
                    DictionaryRepository.SearchItem(
                        language = Language.DUTCH,
                        lemmaId = Uuid.parse("00000000-0000-0000-0000-000000000001"),
                        lemma = "bibliotheek",
                        display = "bibliotheek",
                        zipfFrequency = 4.1f,
                        pos = listOf(PartOfSpeech.NOUN),
                        onlineOnly = false
                    ),
                    DictionaryRepository.SearchItem(
                        language = Language.DUTCH,
                        lemmaId = Uuid.parse("00000000-0000-0000-0000-000000000002"),
                        lemma = "bijbel",
                        display = "bijbel",
                        zipfFrequency = 4.8f,
                        pos = listOf(PartOfSpeech.NOUN),
                        onlineOnly = false
                    )
                ),
                showNoResults = false,
                showLanguageIndicators = false, // Single dictionary - no language badges
                availableLanguages = listOf(Language.DUTCH)
            ),
        )
    }
}

@Preview
@Composable
private fun SearchScreenPreviewMultilingualWithoutPOS(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        SearchScreenContent(
            state = SearchUiState(
                query = "word",
                results = listOf(
                    DictionaryRepository.SearchItem(
                        language = Language.ENGLISH,
                        lemmaId = Uuid.parse("00000000-0000-0000-0000-000000000001"),
                        lemma = "word",
                        display = "word",
                        zipfFrequency = 6.1f,
                        pos = emptyList(),
                        isFavorite = true,
                        onlineOnly = false
                    ),
                    DictionaryRepository.SearchItem(
                        language = Language.RUSSIAN,
                        lemmaId = Uuid.parse("00000000-0000-0000-0000-000000000002"),
                        lemma = "ворд",
                        display = "ворд",
                        zipfFrequency = 3.5f,
                        pos = emptyList(),
                        onlineOnly = false
                    ),
                    DictionaryRepository.SearchItem(
                        language = Language.POLISH,
                        lemmaId = Uuid.parse("00000000-0000-0000-0000-000000000003"),
                        lemma = "wyraz",
                        display = "wyraz",
                        zipfFrequency = 4.2f,
                        pos = listOf(PartOfSpeech.NOUN),
                        onlineOnly = false
                    )
                ),
                showNoResults = false,
                showLanguageIndicators = true, // Multiple dictionaries - language badges shown even without POS
                availableLanguages = listOf(Language.ENGLISH, Language.RUSSIAN, Language.POLISH)
            ),
        )
    }
}

@Preview
@Composable
private fun SearchScreenPreviewMixedLanguagesAndForms(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        SearchScreenContent(
            state = SearchUiState(
                query = "run",
                results = listOf(
                    DictionaryRepository.SearchItem(
                        language = Language.ENGLISH,
                        lemmaId = Uuid.parse("00000000-0000-0000-0000-000000000001"),
                        lemma = "run",
                        display = "run",
                        zipfFrequency = 6.3f,
                        pos = listOf(PartOfSpeech.VERB, PartOfSpeech.NOUN),
                        isFavorite = true,
                        onlineOnly = false
                    ),
                    DictionaryRepository.SearchItem(
                        language = Language.ENGLISH,
                        lemmaId = Uuid.parse("00000000-0000-0000-0000-000000000002"),
                        lemma = "run",
                        display = "\"running\" form of \"run\"",
                        zipfFrequency = 5.8f,
                        pos = emptyList(),
                        onlineOnly = false
                    ),
                    DictionaryRepository.SearchItem(
                        language = Language.DUTCH,
                        lemmaId = Uuid.parse("00000000-0000-0000-0000-000000000003"),
                        lemma = "rennen",
                        display = "rennen",
                        zipfFrequency = 4.9f,
                        pos = listOf(PartOfSpeech.VERB),
                        onlineOnly = false
                    ),
                    DictionaryRepository.SearchItem(
                        language = Language.DUTCH,
                        lemmaId = Uuid.parse("00000000-0000-0000-0000-000000000004"),
                        lemma = "rund",
                        display = "rund",
                        zipfFrequency = 3.7f,
                        pos = listOf(PartOfSpeech.NOUN),
                        isFavorite = true,
                        onlineOnly = false
                    )
                ),
                showNoResults = false,
                showLanguageIndicators = true,
                availableLanguages = listOf(Language.ENGLISH, Language.DUTCH)
            ),
        )
    }
}
