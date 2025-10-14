package com.slovy.slovymovyapp.ui.word

import androidx.compose.runtime.Composable
import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.remote.*
import com.slovy.slovymovyapp.ui.ThemePreviewProvider
import com.slovy.slovymovyapp.ui.ThemedPreview
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.jetbrains.compose.ui.tooling.preview.PreviewParameter

val isSenseFavoritePreview: (String) -> Boolean = { it.hashCode() % 2 == 0 }

@Preview
@Composable
private fun WordDetailScreenPreviewContent(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        WordDetailScreenContent(state = sampleTestingCard().toContentUiState(isSenseFavorite = isSenseFavoritePreview))
    }
}

@Preview
@Composable
private fun WordDetailScreenPreviewCollapsed(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        val base = sampleTestingCard().toContentUiState(isSenseFavorite = isSenseFavoritePreview)
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

// Special UI state previews for different word complexity
@Preview
@Composable
private fun WordDetailScreenPreviewSimpleWord(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        // Simple single-sense word like "celebration"
        WordDetailScreenContent(state = sampleCelebrationCard().toContentUiState(isSenseFavorite = isSenseFavoritePreview))
    }
}

@Preview
@Composable
private fun WordDetailScreenPreviewComplexWord(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        // Complex multi-POS word like "amazon"
        WordDetailScreenContent(state = sampleAmazonCard().toContentUiState(isSenseFavorite = isSenseFavoritePreview))
    }
}

@Preview
@Composable
private fun WordDetailScreenPreviewProperNoun(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        // Proper noun with multiple geographical meanings like "Richmond"
        WordDetailScreenContent(state = sampleRichmondCard().toContentUiState(isSenseFavorite = isSenseFavoritePreview))
    }
}

@Preview
@Composable
private fun WordDetailScreenPreviewAdverb(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        // Technical adverb like "programmatically"
        WordDetailScreenContent(state = sampleProgrammaticallyCard().toContentUiState(isSenseFavorite = isSenseFavoritePreview))
    }
}


// Edge case preview functions
@Preview
@Composable
private fun WordDetailScreenPreviewNoTranslations(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        // Word with no translations available
        WordDetailScreenContent(state = sampleNoTranslationCard().toContentUiState(isSenseFavorite = isSenseFavoritePreview))
    }
}

// Edge case preview functions
@Preview
@Composable
private fun WordDetailScreenPreviewNoTranslationsAllExpanded(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        val base = sampleNoTranslationCard().toContentUiState(isSenseFavorite = isSenseFavoritePreview)
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
            state = base.copy(entries = expanded)
        )
    }
}

@Preview
@Composable
private fun WordDetailScreenPreviewNoTranslationsCollapsed(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        val base = sampleNoTranslationCard().toContentUiState(isSenseFavorite = isSenseFavoritePreview)
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

@Preview
@Composable
private fun WordDetailScreenPreviewMultilingual(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        // Dutch word with both Russian and English translations
        WordDetailScreenContent(state = sampleMultilingualCard().toContentUiState(isSenseFavorite = isSenseFavoritePreview))
    }
}

@Preview
@Composable
private fun WordDetailScreenPreviewMultilingualCollapsed(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        val base = sampleMultilingualCard().toContentUiState(isSenseFavorite = isSenseFavoritePreview)
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

// UI state edge cases
@Preview
@Composable
private fun WordDetailScreenPreviewPartiallyExpanded(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        // Show a complex word with some sections expanded and others collapsed
        val base = sampleMultilingualCard().toContentUiState(isSenseFavorite = isSenseFavoritePreview)
        val mixedEntries = base.entries.mapIndexed { entryIndex, entryState ->
            entryState.copy(
                expanded = true,
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

@Preview
@Composable
private fun WordDetailScreenPreviewHighFrequencyWord(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        // Show a high-frequency word (celebration)
        WordDetailScreenContent(state = sampleCelebrationCard().toContentUiState(isSenseFavorite = isSenseFavoritePreview))
    }
}

@Preview
@Composable
private fun WordDetailScreenPreviewLowFrequencyWord(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        // Show a low-frequency/rare word (whippersnapper)
        WordDetailScreenContent(state = sampleNoTranslationCard().toContentUiState(isSenseFavorite = isSenseFavoritePreview))
    }
}


@Preview
@Composable
private fun WordDetailScreenPreviewEmpty(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        WordDetailScreenContent(state = WordDetailUiState.Empty(lemma = "testing"))
    }
}

@Preview
@Composable
private fun WordDetailScreenPreviewWithTraits(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        WordDetailScreenContent(state = sampleWordWithTraits().toContentUiState(isSenseFavorite = isSenseFavoritePreview))
    }
}

@Preview
@Composable
private fun WordDetailScreenPreviewWithNameTypes(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        WordDetailScreenContent(state = sampleRichmondCard().toContentUiState(isSenseFavorite = isSenseFavoritePreview))
    }
}

@Preview
@Composable
private fun WordDetailScreenPreviewAllTraitTypes(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        WordDetailScreenContent(state = sampleAllTraitTypesCard().toContentUiState(isSenseFavorite = isSenseFavoritePreview))
    }
}

@Preview
@Composable
private fun WordDetailScreenPreviewVeryLongWord(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        WordDetailScreenContent(state = sampleVeryLongWordCard().toContentUiState(isSenseFavorite = isSenseFavoritePreview))
    }
}

// Sample word card creators for different types of words
internal fun sampleAmazonCard(): LanguageCard {
    return LanguageCard(
        lemma = "amazon",
        zipfFrequency = 4.5f,
        entries = listOf(
            // Noun entry with multiple meanings
            LanguageCardPosEntry(
                pos = PartOfSpeech.NOUN,
                forms = mutableListOf(LanguageCardForm(listOf("plural"), "amazons")),
                senses = listOf(
                    LanguageCardResponseSense(
                        senseId = "000a2542-e328-4b25-ae77-27edac4d3796",
                        senseDefinition = "In Greek mythology, a member of a race of female warriors.",
                        learnerLevel = LearnerLevel.B1,
                        frequency = SenseFrequency.MIDDLE,
                        semanticGroupId = "WarriorWoman",
                        nameType = NameType.NO,
                        examples = listOf(
                            LanguageCardExample(
                                text = "The ancient Greeks told stories of the <w>Amazons</w>, a fierce tribe of women warriors.",
                                targetLangTranslations = mapOf(Language.RUSSIAN to "Древние греки рассказывали истории об <w>амазонках</w>, свирепом племени женщин-воительниц.")
                            )
                        ),
                        commonPhrases = listOf("mythical Amazons", "tribe of Amazons"),
                        traits = listOf(
                            LanguageCardTrait(
                                TraitType.FORM,
                                "Often capitalized as 'Amazon' when referring to the mythical race."
                            )
                        ),
                        targetLangDefinitions = mapOf(
                            Language.RUSSIAN to "В греческой мифологии, представительница племени женщин-воительниц."
                        ),
                        translations = mapOf(
                            Language.RUSSIAN to listOf(
                                LanguageCardTranslation(
                                    targetLangWord = "амазонка",
                                    targetLangSenseClarification = "Относится к мифологическим воительницам."
                                )
                            )
                        )
                    ),
                    LanguageCardResponseSense(
                        senseId = "4f67890a-bcde-5678-9012-34567890abcd",
                        senseDefinition = "A major river in South America, the largest river by water volume in the world.",
                        learnerLevel = LearnerLevel.A2,
                        frequency = SenseFrequency.HIGH,
                        semanticGroupId = "River",
                        nameType = NameType.GEOGRAPHICAL_FEATURE,
                        examples = listOf(
                            LanguageCardExample(
                                text = "The <w>Amazon</w> River flows through several South American countries.",
                                targetLangTranslations = mapOf(Language.RUSSIAN to "Река <w>Амазонка</w> протекает через несколько южноамериканских стран.")
                            )
                        ),
                        commonPhrases = listOf("Amazon River", "Amazon Basin", "Amazon Rainforest"),
                        traits = listOf(
                            LanguageCardTrait(
                                TraitType.FORM,
                                "Always capitalized when referring to the river."
                            )
                        ),
                        targetLangDefinitions = mapOf(
                            Language.RUSSIAN to "Крупнейшая река в Южной Америке, самая полноводная река в мире."
                        ),
                        translations = mapOf(
                            Language.RUSSIAN to listOf(
                                LanguageCardTranslation(
                                    targetLangWord = "амазонка",
                                    targetLangSenseClarification = "Название реки."
                                )
                            )
                        )
                    ),
                    LanguageCardResponseSense(
                        senseId = "8c2403c5-1510-45cb-9112-304f78772f96",
                        senseDefinition = "Any of several large, often green, parrots of the genus Amazona, native to tropical America.",
                        learnerLevel = LearnerLevel.B2,
                        frequency = SenseFrequency.LOW,
                        semanticGroupId = "Animal",
                        nameType = NameType.NO,
                        examples = listOf(
                            LanguageCardExample(
                                text = "My neighbor has a beautiful green <w>amazon</w> parrot that can mimic human speech.",
                                targetLangTranslations = mapOf(Language.RUSSIAN to "У моего соседа есть красивый зеленый попугай-<w>амазон</w>.")
                            )
                        ),
                        commonPhrases = listOf("amazon parrot"),
                        targetLangDefinitions = mapOf(
                            Language.RUSSIAN to "Любой из нескольких крупных, часто зеленых попугаев рода Amazona."
                        ),
                        translations = mapOf(
                            Language.RUSSIAN to listOf(
                                LanguageCardTranslation(
                                    targetLangWord = "амазон",
                                    targetLangSenseClarification = "Название вида попугаев (мужской род)."
                                )
                            )
                        )
                    )
                )
            ),
            // Name entry
            LanguageCardPosEntry(
                pos = PartOfSpeech.NAME,
                forms = mutableListOf(),
                senses = listOf(
                    LanguageCardResponseSense(
                        senseId = "e4e736a7-5254-4f11-88e1-1ca59ed5011e",
                        senseDefinition = "Amazon.com Inc., a very large online retail company.",
                        learnerLevel = LearnerLevel.A1,
                        frequency = SenseFrequency.HIGH,
                        semanticGroupId = "Company",
                        nameType = NameType.ORGANIZATION_NAME,
                        examples = listOf(
                            LanguageCardExample(
                                text = "I ordered a new book from <w>Amazon</w> last night.",
                                targetLangTranslations = mapOf(Language.RUSSIAN to "Вчера вечером я заказал новую книгу на <w>Amazon</w>.")
                            )
                        ),
                        commonPhrases = listOf("Amazon Prime", "shop on Amazon"),
                        targetLangDefinitions = mapOf(
                            Language.RUSSIAN to "Amazon.com Inc., очень крупная компания в сфере онлайн-торговли."
                        ),
                        translations = mapOf(
                            Language.RUSSIAN to listOf(
                                LanguageCardTranslation(
                                    targetLangWord = "Amazon",
                                    targetLangSenseClarification = "Название компании."
                                )
                            )
                        )
                    )
                )
            )
        )
    )
}

internal fun sampleCelebrationCard(): LanguageCard {
    return LanguageCard(
        lemma = "celebration",
        zipfFrequency = 4.2f,
        entries = listOf(
            LanguageCardPosEntry(
                pos = PartOfSpeech.NOUN,
                forms = mutableListOf(),
                senses = listOf(
                    LanguageCardResponseSense(
                        senseId = "65a4e9ac-cd46-4763-a0a4-d2457cfa6071",
                        senseDefinition = "A social gathering or party held to mark a special occasion or to have fun.",
                        learnerLevel = LearnerLevel.A1,
                        frequency = SenseFrequency.HIGH,
                        semanticGroupId = "Social Event",
                        nameType = NameType.NO,
                        examples = listOf(
                            LanguageCardExample(
                                text = "We're having a little <w>celebration</w> tomorrow for Martin's scholarship.",
                                targetLangTranslations = mapOf(Language.RUSSIAN to "Завтра мы устраиваем небольшое <w>празднование</w> по случаю стипендии Мартина.")
                            ),
                            LanguageCardExample(
                                text = "The birthday <w>celebration</w> was a lot of fun.",
                                targetLangTranslations = mapOf(Language.RUSSIAN to "<w>Празднование</w> дня рождения было очень весёлым.")
                            )
                        ),
                        synonyms = listOf("entertainment", "festivity", "function", "gathering"),
                        commonPhrases = listOf(
                            "birthday celebration",
                            "wedding celebration",
                            "anniversary celebration"
                        ),
                        targetLangDefinitions = mapOf(
                            Language.RUSSIAN to "Собрание людей или вечеринка, устраиваемые в честь особого случая."
                        ),
                        translations = mapOf(
                            Language.RUSSIAN to listOf(
                                LanguageCardTranslation(
                                    targetLangWord = "праздник",
                                    targetLangSenseClarification = "Обычно относится к самому событию, вечеринке или дню."
                                ),
                                LanguageCardTranslation(
                                    targetLangWord = "празднование",
                                    targetLangSenseClarification = "Описывает как само событие, так и процесс его отмечания."
                                )
                            )
                        )
                    )
                )
            )
        )
    )
}

internal fun sampleProgrammaticallyCard(): LanguageCard {
    return LanguageCard(
        lemma = "programmatically",
        zipfFrequency = 3.1f,
        entries = listOf(
            LanguageCardPosEntry(
                pos = PartOfSpeech.ADVERB,
                forms = mutableListOf(),
                senses = listOf(
                    LanguageCardResponseSense(
                        senseId = "d3674bcb-9937-4678-bce7-612fee308dc9",
                        senseDefinition = "In a planned or systematic way, often following a set of instructions or a program.",
                        learnerLevel = LearnerLevel.B2,
                        frequency = SenseFrequency.MIDDLE,
                        semanticGroupId = "Planned/Systematic",
                        nameType = NameType.NO,
                        examples = listOf(
                            LanguageCardExample(
                                text = "The robot moved <w>programmatically</w> along the assembly line.",
                                targetLangTranslations = mapOf(Language.RUSSIAN to "Робот <w>программно</w> двигался по сборочной линии.")
                            ),
                            LanguageCardExample(
                                text = "The company decided to expand <w>programmatically</w> into new markets.",
                                targetLangTranslations = mapOf(Language.RUSSIAN to "Компания решила <w>планомерно</w> выходить на новые рынки.")
                            )
                        ),
                        synonyms = listOf("systematically", "methodically", "by design"),
                        antonyms = listOf("randomly", "haphazardly", "spontaneously"),
                        commonPhrases = listOf("programmatically controlled", "programmatically managed"),
                        targetLangDefinitions = mapOf(
                            Language.RUSSIAN to "Планомерно, систематически, в соответствии с заранее определенной программой."
                        ),
                        translations = mapOf(
                            Language.RUSSIAN to listOf(
                                LanguageCardTranslation(
                                    targetLangWord = "программно",
                                    targetLangSenseClarification = "В техническом контексте, с помощью программного обеспечения."
                                ),
                                LanguageCardTranslation(
                                    targetLangWord = "планомерно",
                                    targetLangSenseClarification = "Систематически, согласно плану."
                                )
                            )
                        )
                    )
                )
            )
        )
    )
}

internal fun sampleRichmondCard(): LanguageCard {
    return LanguageCard(
        lemma = "Richmond",
        zipfFrequency = 4.9f,
        entries = listOf(
            LanguageCardPosEntry(
                pos = PartOfSpeech.NAME,
                forms = mutableListOf(),
                senses = listOf(
                    LanguageCardResponseSense(
                        senseId = "e67ffcc3-a126-470c-ad5b-7e48f6a0eb73",
                        senseDefinition = "A town in south-west Greater London, England (properly Richmond upon Thames).",
                        learnerLevel = LearnerLevel.A2,
                        frequency = SenseFrequency.HIGH,
                        semanticGroupId = "Places named Richmond",
                        nameType = NameType.PLACE_NAME,
                        examples = listOf(
                            LanguageCardExample(
                                text = "They decided to spend the day exploring <w>Richmond</w> Park in London.",
                                targetLangTranslations = mapOf(Language.RUSSIAN to "Они решили провести день, исследуя <w>Ричмонд</w>-парк в Лондоне.")
                            )
                        ),
                        traits = listOf(
                            LanguageCardTrait(
                                TraitType.REGIONAL,
                                "England"
                            )
                        ),
                        targetLangDefinitions = mapOf(
                            Language.RUSSIAN to "Город на юго-западе Большого Лондона, Англия."
                        ),
                        translations = mapOf(
                            Language.RUSSIAN to listOf(
                                LanguageCardTranslation(
                                    targetLangWord = "Ричмонд",
                                    targetLangSenseClarification = "город в Англии, на Темзе"
                                )
                            )
                        )
                    ),
                    LanguageCardResponseSense(
                        senseId = "2556596a-2eae-4d77-bbb2-dada74364b55",
                        senseDefinition = "The capital city of Virginia, USA.",
                        learnerLevel = LearnerLevel.A2,
                        frequency = SenseFrequency.HIGH,
                        semanticGroupId = "Places named Richmond",
                        nameType = NameType.PLACE_NAME,
                        examples = listOf(
                            LanguageCardExample(
                                text = "<w>Richmond</w> is known for its historic architecture and Civil War history.",
                                targetLangTranslations = mapOf(Language.RUSSIAN to "<w>Ричмонд</w> известен своей исторической архитектурой и историей Гражданской войны.")
                            )
                        ),
                        traits = listOf(
                            LanguageCardTrait(
                                TraitType.REGIONAL,
                                "Virginia, USA"
                            )
                        ),
                        targetLangDefinitions = mapOf(
                            Language.RUSSIAN to "Столица штата Виргиния, США."
                        ),
                        translations = mapOf(
                            Language.RUSSIAN to listOf(
                                LanguageCardTranslation(
                                    targetLangWord = "Ричмонд",
                                    targetLangSenseClarification = "столица штата Виргиния"
                                )
                            )
                        )
                    )
                )
            )
        )
    )
}

internal fun sampleKwartierCard(): LanguageCard {
    return LanguageCard(
        lemma = "kwartier",
        zipfFrequency = 4.7f,
        entries = listOf(
            LanguageCardPosEntry(
                pos = PartOfSpeech.NOUN,
                forms = mutableListOf(),
                senses = listOf(
                    LanguageCardResponseSense(
                        senseId = "f6691b39-ac88-4711-a3e9-5d1751ce455d",
                        senseDefinition = "Een periode van vijftien minuten, een vierde deel van een uur.",
                        learnerLevel = LearnerLevel.A1,
                        frequency = SenseFrequency.HIGH,
                        semanticGroupId = "Tijdseenheid",
                        nameType = NameType.NO,
                        examples = listOf(
                            LanguageCardExample(
                                text = "Ik ben over een <w>kwartier</w> thuis.",
                                targetLangTranslations = mapOf(
                                    Language.RUSSIAN to "Я буду дома через <w>четверть часа</w>.",
                                    Language.ENGLISH to "I'll be home in a <w>quarter of an hour</w>."
                                )
                            ),
                            LanguageCardExample(
                                text = "De vergadering duurt nog een <w>kwartier</w>.",
                                targetLangTranslations = mapOf(
                                    Language.RUSSIAN to "Собрание продлится ещё <w>четверть часа</w>.",
                                    Language.ENGLISH to "The meeting will last for another <w>fifteen minutes</w>."
                                )
                            )
                        ),
                        synonyms = listOf("vijftien minuten", "een vierde uur"),
                        commonPhrases = listOf("over een kwartier", "binnen een kwartier"),
                        targetLangDefinitions = mapOf(
                            Language.RUSSIAN to "Период в пятнадцать минут, четверть часа.",
                            Language.ENGLISH to "A period of fifteen minutes, a quarter of an hour."
                        ),
                        translations = mapOf(
                            Language.RUSSIAN to listOf(
                                LanguageCardTranslation(
                                    targetLangWord = "четверть часа",
                                    targetLangSenseClarification = "Основной перевод, обозначающий 15 минут."
                                ),
                                LanguageCardTranslation(
                                    targetLangWord = "пятнадцать минут",
                                    targetLangSenseClarification = "Более прямой перевод временного периода."
                                )
                            ),
                            Language.ENGLISH to listOf(
                                LanguageCardTranslation(
                                    targetLangWord = "quarter of an hour",
                                    targetLangSenseClarification = "Literal translation referring to 15 minutes."
                                ),
                                LanguageCardTranslation(
                                    targetLangWord = "fifteen minutes",
                                    targetLangSenseClarification = "Direct translation of the time period."
                                )
                            )
                        )
                    )
                )
            )
        )
    )
}

internal fun sampleProgrammaCard(): LanguageCard {
    return LanguageCard(
        lemma = "программа",
        zipfFrequency = 5.6f,
        entries = listOf(
            LanguageCardPosEntry(
                pos = PartOfSpeech.NOUN,
                forms = mutableListOf(),
                senses = listOf(
                    LanguageCardResponseSense(
                        senseId = "a51a3765-9b26-412d-bca0-c03443ffc445",
                        senseDefinition = "Расписание передач радио или телевидения, а также перечень номеров, исполняемых на концерте, вечере, представлении.",
                        learnerLevel = LearnerLevel.A2,
                        frequency = SenseFrequency.HIGH,
                        semanticGroupId = "Расписание/Представление",
                        nameType = NameType.NO,
                        examples = listOf(
                            LanguageCardExample(
                                text = "<w>Программа</w> телепередач на завтра.",
                                targetLangTranslations = mapOf(Language.ENGLISH to "Tomorrow's TV <w>schedule</w>.")
                            ),
                            LanguageCardExample(
                                text = "В <w>программе</w> пианист стоит вторым.",
                                targetLangTranslations = mapOf(Language.ENGLISH to "The pianist is second on the <w>program</w>.")
                            ),
                            LanguageCardExample(
                                text = "Театральная <w>программа</w> была очень интересной.",
                                targetLangTranslations = mapOf(Language.ENGLISH to "The theater <w>program</w> was very interesting.")
                            )
                        ),
                        synonyms = listOf("расписание", "план", "репертуар"),
                        commonPhrases = listOf("программа передач", "телевизионная программа"),
                        targetLangDefinitions = mapOf(
                            Language.ENGLISH to "A schedule of radio or television broadcasts, or a list of acts performed at a concert, evening, or show."
                        ),
                        translations = mapOf(
                            Language.ENGLISH to listOf(
                                LanguageCardTranslation(
                                    targetLangWord = "program",
                                    targetLangSenseClarification = "American spelling, commonly used for entertainment shows and events."
                                ),
                                LanguageCardTranslation(
                                    targetLangWord = "programme",
                                    targetLangSenseClarification = "British spelling, used for broadcast schedules and event listings."
                                ),
                                LanguageCardTranslation(
                                    targetLangWord = "schedule",
                                    targetLangSenseClarification = "More specific to broadcast timetables and programming schedules."
                                )
                            )
                        )
                    )
                )
            )
        )
    )
}

internal fun sampleNoTranslationCard(): LanguageCard {
    return LanguageCard(
        lemma = "whippersnapper",
        zipfFrequency = 2.1f,
        entries = listOf(
            LanguageCardPosEntry(
                pos = PartOfSpeech.NOUN,
                forms = mutableListOf(),
                senses = listOf(
                    LanguageCardResponseSense(
                        senseId = "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
                        senseDefinition = "A young person considered to be presumptuous or overconfident.",
                        learnerLevel = LearnerLevel.C1,
                        frequency = SenseFrequency.LOW,
                        semanticGroupId = "Person/Age",
                        nameType = NameType.NO,
                        examples = listOf(
                            LanguageCardExample(
                                text = "That young <w>whippersnapper</w> thinks he knows everything!",
                                targetLangTranslations = emptyMap() // No translations available
                            ),
                            LanguageCardExample(
                                text = "The old man grumbled about the <w>whippersnapper</w> who cut in line.",
                                targetLangTranslations = emptyMap() // No translations available
                            )
                        ),
                        synonyms = listOf("upstart", "youngster", "punk"),
                        antonyms = listOf("elder", "veteran"),
                        commonPhrases = listOf("young whippersnapper", "little whippersnapper"),
                        traits = listOf(
                            LanguageCardTrait(
                                TraitType.COLLOQUIAL,
                                "Informal, often used by older people"
                            )
                        ),
                        targetLangDefinitions = emptyMap(), // No target language definitions
                        translations = emptyMap() // No translations available
                    ),
                    LanguageCardResponseSense(
                        senseId = "b2c3d4e5-f6a7-8901-bcde-f23456789012",
                        senseDefinition = "A small or insignificant person, especially a child.",
                        learnerLevel = LearnerLevel.B2,
                        frequency = SenseFrequency.VERY_LOW,
                        semanticGroupId = "Person/Size",
                        nameType = NameType.NO,
                        examples = listOf(
                            LanguageCardExample(
                                text = "Don't mind that <w>whippersnapper</w>, he's just being silly.",
                                targetLangTranslations = emptyMap()
                            )
                        ),
                        synonyms = listOf("little one", "small fry"),
                        commonPhrases = listOf("just a whippersnapper"),
                        traits = listOf(
                            LanguageCardTrait(
                                TraitType.COLLOQUIAL,
                                "Informal and somewhat dated usage"
                            ),
                            LanguageCardTrait(
                                TraitType.DATED,
                                "More commonly used in earlier generations"
                            )
                        ),
                        targetLangDefinitions = emptyMap(),
                        translations = emptyMap()
                    )
                )
            )
        )
    )
}

internal fun sampleMultilingualCard(): LanguageCard {
    return LanguageCard(
        lemma = "bibliotheek",
        zipfFrequency = 4.8f,
        entries = listOf(
            LanguageCardPosEntry(
                pos = PartOfSpeech.NOUN,
                forms = mutableListOf(),
                senses = listOf(
                    LanguageCardResponseSense(
                        senseId = "c3d4e5f6-a7b8-9012-cdef-345678901234",
                        senseDefinition = "Een gebouw of ruimte waar boeken en andere media worden bewaard en uitgeleend aan het publiek.",
                        learnerLevel = LearnerLevel.A2,
                        frequency = SenseFrequency.HIGH,
                        semanticGroupId = "Gebouw/Instituut",
                        nameType = NameType.NO,
                        examples = listOf(
                            LanguageCardExample(
                                text = "Ik ga naar de <w>bibliotheek</w> om een boek te lenen.",
                                targetLangTranslations = mapOf(
                                    Language.RUSSIAN to "Я иду в <w>библиотеку</w>, чтобы взять книгу.",
                                    Language.ENGLISH to "I'm going to the <w>library</w> to borrow a book."
                                )
                            ),
                            LanguageCardExample(
                                text = "De <w>bibliotheek</w> is elke dag open van 9 tot 17 uur.",
                                targetLangTranslations = mapOf(
                                    Language.RUSSIAN to "<w>Библиотека</w> открыта каждый день с 9 до 17 часов.",
                                    Language.ENGLISH to "The <w>library</w> is open every day from 9 to 5."
                                )
                            ),
                            LanguageCardExample(
                                text = "In de universitaire <w>bibliotheek</w> vind je alle wetenschappelijke boeken.",
                                targetLangTranslations = mapOf(
                                    Language.RUSSIAN to "В университетской <w>библиотеке</w> можно найти все научные книги.",
                                    Language.ENGLISH to "In the university <w>library</w> you can find all academic books."
                                )
                            )
                        ),
                        synonyms = listOf("boekenzaal", "leeszaal"),
                        commonPhrases = listOf(
                            "openbare bibliotheek",
                            "universitaire bibliotheek",
                            "naar de bibliotheek gaan"
                        ),
                        traits = listOf(
                            LanguageCardTrait(
                                TraitType.FORM,
                                "Countable noun, plural: bibliotheken"
                            )
                        ),
                        targetLangDefinitions = mapOf(
                            Language.RUSSIAN to "Здание или помещение, где хранятся книги и другие материалы, которые выдаются населению.",
                            Language.ENGLISH to "A building or room where books and other media are kept and lent to the public."
                        ),
                        translations = mapOf(
                            Language.RUSSIAN to listOf(
                                LanguageCardTranslation(
                                    targetLangWord = "библиотека",
                                    targetLangSenseClarification = "Основной перевод. Обозначает как здание, так и учреждение."
                                ),
                                LanguageCardTranslation(
                                    targetLangWord = "книгохранилище",
                                    targetLangSenseClarification = "Более формальный термин, обычно для крупных собраний книг."
                                )
                            ),
                            Language.ENGLISH to listOf(
                                LanguageCardTranslation(
                                    targetLangWord = "library",
                                    targetLangSenseClarification = "Standard translation for a public lending library."
                                ),
                                LanguageCardTranslation(
                                    targetLangWord = "book repository",
                                    targetLangSenseClarification = "More formal term, typically used for large collections."
                                )
                            )
                        )
                    ),
                    LanguageCardResponseSense(
                        senseId = "d4e5f6a7-b8c9-0123-def0-456789012345",
                        senseDefinition = "Een verzameling boeken of documenten, vaak gespecialiseerd in een bepaald onderwerp.",
                        learnerLevel = LearnerLevel.B1,
                        frequency = SenseFrequency.MIDDLE,
                        semanticGroupId = "Verzameling/Collectie",
                        nameType = NameType.NO,
                        examples = listOf(
                            LanguageCardExample(
                                text = "Hij heeft een uitgebreide <w>bibliotheek</w> over kunstgeschiedenis.",
                                targetLangTranslations = mapOf(
                                    Language.RUSSIAN to "У него есть обширная <w>библиотека</w> по истории искусства.",
                                    Language.ENGLISH to "He has an extensive <w>library</w> on art history."
                                )
                            ),
                            LanguageCardExample(
                                text = "De professor toonde ons zijn persoonlijke <w>bibliotheek</w>.",
                                targetLangTranslations = mapOf(
                                    Language.RUSSIAN to "Профессор показал нам свою личную <w>библиотеку</w>.",
                                    Language.ENGLISH to "The professor showed us his personal <w>library</w>."
                                )
                            )
                        ),
                        synonyms = listOf("boekenverzameling", "collectie"),
                        commonPhrases = listOf("persoonlijke bibliotheek", "digitale bibliotheek"),
                        targetLangDefinitions = mapOf(
                            Language.RUSSIAN to "Коллекция книг или документов, часто специализированная по определённой теме.",
                            Language.ENGLISH to "A collection of books or documents, often specialized in a particular subject."
                        ),
                        translations = mapOf(
                            Language.RUSSIAN to listOf(
                                LanguageCardTranslation(
                                    targetLangWord = "библиотека",
                                    targetLangSenseClarification = "Личная или частная коллекция книг."
                                ),
                                LanguageCardTranslation(
                                    targetLangWord = "коллекция книг",
                                    targetLangSenseClarification = "Более описательный перевод."
                                )
                            ),
                            Language.ENGLISH to listOf(
                                LanguageCardTranslation(
                                    targetLangWord = "library",
                                    targetLangSenseClarification = "Personal or private collection of books."
                                ),
                                LanguageCardTranslation(
                                    targetLangWord = "book collection",
                                    targetLangSenseClarification = "More descriptive translation emphasizing the collection aspect."
                                )
                            )
                        )
                    )
                )
            )
        )
    )
}

internal fun sampleTestingCard(): LanguageCard {
    return LanguageCard(
        lemma = "testing",
        zipfFrequency = 4.6f,
        entries = listOf(
            // Verb entry
            LanguageCardPosEntry(
                pos = PartOfSpeech.VERB,
                forms = mutableListOf(),
                senses = listOf(
                    LanguageCardResponseSense(
                        senseId = "f8731e0a-3d06-4a0f-af1c-98de00309b76",
                        senseDefinition = "The present participle or gerund form of the verb 'to test', meaning to perform an examination or evaluation.",
                        learnerLevel = LearnerLevel.A2,
                        frequency = SenseFrequency.HIGH,
                        semanticGroupId = "action_of_testing",
                        nameType = null,
                        examples = listOf(
                            LanguageCardExample(
                                text = "The scientist is <w>testing</w> the new hypothesis in the lab.",
                                targetLangTranslations = mapOf(Language.RUSSIAN to "Учёный <w>проверяет</w> новую гипотезу в лаборатории.")
                            ),
                            LanguageCardExample(
                                text = "<w>Testing</w> the water before swimming is a good idea.",
                                targetLangTranslations = mapOf(Language.RUSSIAN to "<w>Проверять</w> воду перед плаванием — хорошая идея.")
                            )
                        ),
                        synonyms = listOf("examining", "evaluating", "checking", "trying out"),
                        commonPhrases = listOf("testing out", "testing for", "testing positive/negative"),
                        traits = listOf(
                            LanguageCardTrait(
                                TraitType.FORM,
                                "present participle and gerund form of 'test'"
                            )
                        ),
                        targetLangDefinitions = mapOf(
                            Language.RUSSIAN to "Причастие настоящего времени или герундий от глагола 'to test'."
                        ),
                        translations = mapOf(
                            Language.RUSSIAN to listOf(
                                LanguageCardTranslation(
                                    targetLangWord = "тестирование",
                                    targetLangSenseClarification = "Процесс проверки или испытания чего-либо."
                                )
                            )
                        )
                    )
                )
            ),
            // Noun entry
            LanguageCardPosEntry(
                pos = PartOfSpeech.NOUN,
                forms = mutableListOf(),
                senses = listOf(
                    LanguageCardResponseSense(
                        senseId = "16f3dcde-882f-464a-8f53-0843847294b2",
                        senseDefinition = "The process of checking something to see if it works correctly or meets certain standards.",
                        learnerLevel = LearnerLevel.B2,
                        frequency = SenseFrequency.HIGH,
                        semanticGroupId = "evaluation_process",
                        nameType = null,
                        examples = listOf(
                            LanguageCardExample(
                                text = "The software is currently undergoing rigorous <w>testing</w> before its release.",
                                targetLangTranslations = mapOf(Language.RUSSIAN to "Программное обеспечение проходит тщательное <w>тестирование</w> перед выпуском.")
                            )
                        ),
                        synonyms = listOf("examination", "evaluation", "trial", "assessment"),
                        commonPhrases = listOf("product testing", "software testing", "quality testing"),
                        targetLangDefinitions = mapOf(
                            Language.RUSSIAN to "Процесс проверки чего-либо с целью убедиться, что оно работает правильно."
                        ),
                        translations = mapOf(
                            Language.RUSSIAN to listOf(
                                LanguageCardTranslation(
                                    targetLangWord = "тестирование",
                                    targetLangSenseClarification = "Процесс испытания или проверки чего-либо."
                                )
                            )
                        )
                    )
                )
            )
        )
    )
}

internal fun sampleWordWithTraits(): LanguageCard {
    return LanguageCard(
        lemma = "ain't",
        zipfFrequency = 3.9f,
        entries = listOf(
            LanguageCardPosEntry(
                pos = PartOfSpeech.VERB,
                forms = mutableListOf(),
                senses = listOf(
                    LanguageCardResponseSense(
                        senseId = "trait-example-1",
                        senseDefinition = "Contraction of 'am not', 'is not', 'are not', 'has not', or 'have not'.",
                        learnerLevel = LearnerLevel.B1,
                        frequency = SenseFrequency.MIDDLE,
                        semanticGroupId = "Contraction",
                        nameType = NameType.NO,
                        examples = listOf(
                            LanguageCardExample(
                                text = "That <w>ain't</w> right!",
                                targetLangTranslations = mapOf(Language.RUSSIAN to "Это <w>неправильно</w>!")
                            ),
                            LanguageCardExample(
                                text = "I <w>ain't</w> seen nothing like that before.",
                                targetLangTranslations = mapOf(Language.RUSSIAN to "Я никогда раньше такого <w>не видел</w>.")
                            )
                        ),
                        synonyms = listOf("isn't", "aren't", "am not"),
                        traits = listOf(
                            LanguageCardTrait(
                                TraitType.COLLOQUIAL,
                                "Very informal usage, common in casual speech"
                            ),
                            LanguageCardTrait(
                                TraitType.DIALECTAL,
                                "Particularly common in Southern American English and African American Vernacular English"
                            ),
                            LanguageCardTrait(
                                TraitType.FORM,
                                "Considered non-standard in formal writing"
                            )
                        ),
                        targetLangDefinitions = mapOf(
                            Language.RUSSIAN to "Разговорное сокращение отрицательных форм глаголов 'be' и 'have'."
                        ),
                        translations = mapOf(
                            Language.RUSSIAN to listOf(
                                LanguageCardTranslation(
                                    targetLangWord = "не",
                                    targetLangSenseClarification = "Общее отрицание в разговорной речи."
                                )
                            )
                        )
                    )
                )
            )
        )
    )
}

internal fun sampleAllTraitTypesCard(): LanguageCard {
    return LanguageCard(
        lemma = "thou",
        zipfFrequency = 3.4f,
        entries = listOf(
            LanguageCardPosEntry(
                pos = PartOfSpeech.PRONOUN,
                forms = mutableListOf(),
                senses = listOf(
                    LanguageCardResponseSense(
                        senseId = "all-traits-example",
                        senseDefinition = "Second person singular pronoun (archaic or dialectal).",
                        learnerLevel = LearnerLevel.C1,
                        frequency = SenseFrequency.VERY_LOW,
                        semanticGroupId = "Pronoun",
                        nameType = NameType.NO,
                        examples = listOf(
                            LanguageCardExample(
                                text = "<w>Thou</w> shalt not pass!",
                                targetLangTranslations = mapOf(Language.RUSSIAN to "<w>Ты</w> не пройдёшь!")
                            )
                        ),
                        traits = listOf(
                            LanguageCardTrait(
                                TraitType.ARCHAIC,
                                "Rarely used in modern English except in religious or historical contexts"
                            ),
                            LanguageCardTrait(
                                TraitType.DATED,
                                "Common in Early Modern English (Shakespeare era)"
                            ),
                            LanguageCardTrait(
                                TraitType.DIALECTAL,
                                "Still used in some Northern English dialects"
                            ),
                            LanguageCardTrait(
                                TraitType.REGIONAL,
                                "Yorkshire, Lancashire"
                            ),
                            LanguageCardTrait(
                                TraitType.FORM,
                                "Nominative form; 'thee' is the oblique form"
                            )
                        ),
                        targetLangDefinitions = mapOf(
                            Language.RUSSIAN to "Архаичное местоимение второго лица единственного числа."
                        ),
                        translations = mapOf(
                            Language.RUSSIAN to listOf(
                                LanguageCardTranslation(
                                    targetLangWord = "ты",
                                    targetLangSenseClarification = "Устаревшая форма обращения."
                                )
                            )
                        )
                    )
                )
            )
        )
    )
}

internal fun sampleVeryLongWordCard(): LanguageCard {
    return LanguageCard(
        lemma = "pneumonoultramicroscopicsilicovolcanoconiosis",
        zipfFrequency = 1.5f,
        entries = listOf(
            LanguageCardPosEntry(
                pos = PartOfSpeech.NOUN,
                forms = mutableListOf(),
                senses = listOf(
                    LanguageCardResponseSense(
                        senseId = "very-long-word-example",
                        senseDefinition = "A lung disease caused by inhaling very fine silicate or quartz dust.",
                        learnerLevel = LearnerLevel.C2,
                        frequency = SenseFrequency.VERY_LOW,
                        semanticGroupId = "Medical",
                        nameType = NameType.NO,
                        examples = listOf(
                            LanguageCardExample(
                                text = "<w>Pneumonoultramicroscopicsilicovolcanoconiosis</w> is one of the longest words in the English language.",
                                targetLangTranslations = mapOf(Language.RUSSIAN to "<w>Пневмоноультрамикроскопическийсиликовулканокониоз</w> - одно из самых длинных слов в английском языке.")
                            )
                        ),
                        synonyms = listOf("silicosis", "black lung disease"),
                        traits = listOf(
                            LanguageCardTrait(
                                TraitType.FORM,
                                "Considered one of the longest words in major dictionaries"
                            )
                        ),
                        targetLangDefinitions = mapOf(
                            Language.RUSSIAN to "Заболевание легких, вызванное вдыханием мелкодисперсной пыли силикатов или кварца."
                        ),
                        translations = mapOf(
                            Language.RUSSIAN to listOf(
                                LanguageCardTranslation(
                                    targetLangWord = "пневмокониоз",
                                    targetLangSenseClarification = "Медицинский термин для болезни легких."
                                )
                            )
                        )
                    )
                )
            )
        )
    )
}

@Preview
@Composable
private fun WordDetailScreenPreviewTargetSenseAmazon(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
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

@Preview
@Composable
private fun WordDetailScreenPreviewTargetSenseAmazonParrot(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
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

@Preview
@Composable
private fun WordDetailScreenPreviewTargetSenseMultilingual(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
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

@Preview
@Composable
private fun WordDetailScreenPreviewTargetSenseRichmondVirginia(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
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
