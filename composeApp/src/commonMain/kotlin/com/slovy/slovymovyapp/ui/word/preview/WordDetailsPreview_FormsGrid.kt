package com.slovy.slovymovyapp.ui.word.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.slovy.slovymovyapp.data.forms.ConjugationScheme
import com.slovy.slovymovyapp.data.forms.configs.EnConjugationScheme
import com.slovy.slovymovyapp.data.forms.configs.NlConjugationScheme
import com.slovy.slovymovyapp.data.forms.configs.RuConjugationScheme
import com.slovy.slovymovyapp.data.remote.*
import com.slovy.slovymovyapp.ui.ThemePreviewProvider
import com.slovy.slovymovyapp.ui.ThemedPreview
import com.slovy.slovymovyapp.ui.word.WordDetailScreenContent
import com.slovy.slovymovyapp.ui.word.WordDetailUiState
import com.slovy.slovymovyapp.ui.word.toContentUiState

private fun previewSense(
    senseId: String,
    definition: String,
    semanticGroupId: String
): LanguageCardResponseSense = LanguageCardResponseSense(
    senseId = senseId,
    senseDefinition = definition,
    learnerLevel = LearnerLevel.A2,
    frequency = SenseFrequency.MIDDLE,
    semanticGroupId = semanticGroupId
)

private fun resolveFormsViews(
    scheme: ConjugationScheme,
    taggedForms: List<Pair<List<String>, String>>
): List<FormsSchemeView> {
    val forms = taggedForms.map { (tags, form) -> LanguageCardForm(tags = tags, form = form) }
    return scheme.views.map { view ->
        FormsSchemeView(
            view = view,
            forms = resolveSchemeView(forms, view, scheme.tagResolver)
        )
    }
}

private fun englishNounGridCard(missingSingular: Boolean = false): LanguageCard {
    val forms = buildList {
        if (!missingSingular) add(listOf("singular") to "book")
        add(listOf("plural") to "books")
    }
    return LanguageCard(
        lemma = "book",
        zipfFrequency = 5.1f,
        entries = listOf(
            LanguageCardPosEntry(
                pos = PartOfSpeech.NOUN,
                formsViews = resolveFormsViews(EnConjugationScheme.EN_NOUN, forms),
                senses = listOf(previewSense("en-noun-1", "A set of bound pages.", "book_sense"))
            )
        )
    )
}

private fun russianNounGridCard(): LanguageCard {
    val cases = listOf(
        "nominative",
        "genitive",
        "dative",
        "accusative",
        "instrumental",
        "prepositional"
    )
    val numbers = listOf("singular", "plural")
    val forms = cases.flatMap { grammaticalCase ->
        numbers.map { number ->
            listOf(grammaticalCase, number) to "${grammaticalCase.take(3)}_${number.take(1)}"
        }
    }
    return LanguageCard(
        lemma = "книга",
        zipfFrequency = 4.7f,
        entries = listOf(
            LanguageCardPosEntry(
                pos = PartOfSpeech.NOUN,
                formsViews = resolveFormsViews(RuConjugationScheme.RU_NOUN, forms),
                senses = listOf(previewSense("ru-noun-1", "Printed work bound as pages.", "ru_book_sense"))
            )
        )
    )
}

private fun dutchVerbGridCard(): LanguageCard {
    val forms = listOf(
        listOf("infinitive") to "maken",
        listOf("finite", "indicative", "past", "first-person", "singular") to "maakte",
        listOf("participle", "past") to "gemaakt",
        listOf("participle", "present") to "makend",
        listOf("finite", "indicative", "main-clause", "present", "first-person", "singular") to "maak",
        listOf("finite", "indicative", "main-clause", "past", "first-person", "singular") to "maakte",
        listOf("finite", "indicative", "subordinate-clause", "present", "first-person", "singular") to "maak",
        listOf("finite", "indicative", "subordinate-clause", "past", "first-person", "singular") to "maakte",
        listOf("finite", "indicative", "main-clause", "present", "plural") to "maken",
        listOf("finite", "indicative", "subordinate-clause", "present", "plural") to "maken",
        listOf("finite", "indicative", "main-clause", "past", "plural") to "maakten",
        listOf("finite", "indicative", "subordinate-clause", "past", "plural") to "maakten",
        listOf("finite", "imperative", "singular") to "maak",
        listOf("finite", "imperative", "plural") to "maakt"
    )
    return LanguageCard(
        lemma = "maken",
        zipfFrequency = 5.4f,
        entries = listOf(
            LanguageCardPosEntry(
                pos = PartOfSpeech.VERB,
                formsViews = resolveFormsViews(NlConjugationScheme.NL_VERB, forms),
                senses = listOf(previewSense("nl-verb-1", "To make or create something.", "nl_make_sense"))
            )
        )
    )
}

private fun formsFocusedState(
    card: LanguageCard,
    selectedViewId: String? = null,
    formsExpanded: Boolean = true
): WordDetailUiState.Content {
    val base = card.toContentUiState(isSenseFavorite = isSenseFavoritePreview)
    return base.copy(
        entries = base.entries.mapIndexed { index, entryState ->
            if (index == 0) {
                entryState.copy(
                    expanded = true,
                    formsExpanded = formsExpanded,
                    selectedFormsViewId = selectedViewId ?: entryState.selectedFormsViewId,
                    senses = entryState.senses.map { senseState ->
                        senseState.copy(
                            expanded = false,
                            examplesExpanded = false,
                            languageExpanded = senseState.languageExpanded.mapValues { false }
                        )
                    }
                )
            } else {
                entryState.copy(expanded = false, formsExpanded = false)
            }
        }
    )
}

@Preview
@Composable
private fun WordDetailScreenPreviewFormsEnglish(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        WordDetailScreenContent(state = formsFocusedState(englishNounGridCard()))
    }
}

@Preview
@Composable
private fun WordDetailScreenPreviewFormsRussian(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        WordDetailScreenContent(state = formsFocusedState(russianNounGridCard(), selectedViewId = "full"))
    }
}

@Preview
@Composable
private fun WordDetailScreenPreviewFormsDutchShortVertical(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        WordDetailScreenContent(state = formsFocusedState(dutchVerbGridCard(), selectedViewId = "short_vertical"))
    }
}

@Preview
@Composable
private fun WordDetailScreenPreviewFormsDutchStamtijd(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        WordDetailScreenContent(state = formsFocusedState(dutchVerbGridCard(), selectedViewId = "stamtijd"))
    }
}

@Preview
@Composable
private fun WordDetailScreenPreviewFormsDutchCategorySummary(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        WordDetailScreenContent(state = formsFocusedState(dutchVerbGridCard(), selectedViewId = "category_summary"))
    }
}

@Preview
@Composable
private fun WordDetailScreenPreviewFormsDutchFull(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        WordDetailScreenContent(state = formsFocusedState(dutchVerbGridCard(), selectedViewId = "full"))
    }
}

@Preview
@Composable
private fun WordDetailScreenPreviewFormsMissingFallback(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        WordDetailScreenContent(state = formsFocusedState(englishNounGridCard(missingSingular = true)))
    }
}

@Preview
@Composable
private fun WordDetailScreenPreviewFormsCollapsed(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        WordDetailScreenContent(state = formsFocusedState(dutchVerbGridCard(), formsExpanded = false))
    }
}

@Preview
@Composable
private fun WordDetailScreenPreviewNoFormsSection(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        WordDetailScreenContent(
            state = sampleCelebrationCard().toContentUiState(isSenseFavorite = isSenseFavoritePreview)
        )
    }
}
