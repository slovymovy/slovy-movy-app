package com.slovy.slovymovyapp.ui.settings.preview

import com.slovy.slovymovyapp.ui.settings.LearningLanguageUiState
import com.slovy.slovymovyapp.ui.settings.TranslationUiState

import com.slovy.slovymovyapp.ui.settings.DeleteConfirmationState
import com.slovy.slovymovyapp.ui.settings.LanguageUiState
import com.slovy.slovymovyapp.ui.settings.SettingsScreenContent
import com.slovy.slovymovyapp.ui.settings.SettingsUiState

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.remote.*
import com.slovy.slovymovyapp.i18n.UiText
import com.slovy.slovymovyapp.speech.*
import slovymovyapp.composeapp.generated.resources.*
import com.slovy.slovymovyapp.ui.ThemePreviewProvider
import com.slovy.slovymovyapp.ui.ThemedPreview

// === Previews ===

@Preview
@Composable
private fun SettingsScreenPreviewLoading(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        SettingsScreenContent(
            state = SettingsUiState(isLoading = true)
        )
    }
}

@Preview
@Composable
private fun SettingsScreenPreviewEmpty(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        SettingsScreenContent(
            state = SettingsUiState(
                isLoading = false,
                languages = emptyMap(),
                learningLanguages = emptyList()
            )
        )
    }
}

@Preview
@Composable
private fun SettingsScreenPreviewWithLanguages(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        SettingsScreenContent(
            state = SettingsUiState(
                isLoading = false,
                learningLanguages = listOf(
                    LearningLanguageUiState(
                        language = Language.DUTCH,
                        isExpanded = true,
                        dictionarySizeBytes = 12 * 1024 * 1024L,
                        translations = listOf(
                            TranslationUiState(
                                targetLanguage = Language.ENGLISH,
                                isDownloaded = true,
                                isDownloadable = true,
                                sizeBytes = 4 * 1024 * 1024L
                            ),
                            TranslationUiState(
                                targetLanguage = Language.RUSSIAN,
                                isDownloaded = false,
                                isDownloadable = false
                            ),
                            TranslationUiState(
                                targetLanguage = Language.POLISH,
                                isDownloaded = false,
                                isDownloadable = true,
                                sizeBytes = 3 * 1024 * 1024L
                            )
                        )
                    ),
                    LearningLanguageUiState(
                        language = Language.ENGLISH,
                        isExpanded = false,
                        dictionarySizeBytes = 15 * 1024 * 1024L,
                        translations = listOf(
                            TranslationUiState(
                                targetLanguage = Language.RUSSIAN,
                                isDownloaded = true,
                                isDownloadable = true,
                                sizeBytes = 8 * 1024 * 1024L
                            )
                        )
                    )
                ),
                addableLanguages = listOf(
                    AvailableLanguageInfo(
                        language = Language.GERMAN,
                        dictionarySizeBytes = 14 * 1024 * 1024,
                        availableTranslations = emptyList()
                    )
                ),
                translationLanguages = setOf(Language.ENGLISH, Language.RUSSIAN, Language.POLISH),
                languages = mapOf(
                    Text2SpeechLanguage(
                        language = Language.DUTCH,
                        isAvailable = true,
                        missingData = false
                    ) to LanguageUiState(),
                    // The bound engine reports no voices for this language.
                    Text2SpeechLanguage(
                        language = Language.ENGLISH,
                        isAvailable = true,
                        missingData = false
                    ) to LanguageUiState(voicesLoaded = true)
                )
            )
        )
    }
}

@Preview
@Composable
private fun SettingsScreenPreviewWithExpandedVoice(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        SettingsScreenContent(
            state = SettingsUiState(
                isLoading = false,
                learningLanguages = listOf(
                    LearningLanguageUiState(
                        language = Language.ENGLISH,
                        isExpanded = false,
                        dictionarySizeBytes = 15 * 1024 * 1024L,
                        translations = listOf(
                            TranslationUiState(
                                targetLanguage = Language.RUSSIAN,
                                isDownloaded = true,
                                isDownloadable = true,
                                sizeBytes = 8 * 1024 * 1024L
                            )
                        )
                    )
                ),
                translationLanguages = setOf(Language.RUSSIAN),
                languages = mapOf(
                    Text2SpeechLanguage(
                        language = Language.ENGLISH,
                        isAvailable = true,
                        missingData = false
                    ) to LanguageUiState(
                        isExpanded = true,
                        voicesLoaded = true,
                        enabledVoiceIds = setOf("en-us-x-sfg#female_1-local", "en-us-x-sfg#male_1-local"),
                        voices = listOf(
                            Text2SpeechVoice(
                                id = "en-us-x-sfg#female_1-local",
                                name = "Female 1",
                                language = Language.ENGLISH,
                                localeTag = "en-US",
                                quality = VoiceQuality.BEST,
                                networkConnectionRequired = false,
                                enabledByDefault = true
                            ),
                            Text2SpeechVoice(
                                id = "en-us-x-sfg#male_1-local",
                                name = "Male 1",
                                language = Language.ENGLISH,
                                localeTag = "en-US",
                                quality = VoiceQuality.GOOD,
                                networkConnectionRequired = false,
                                enabledByDefault = true
                            ),
                            Text2SpeechVoice(
                                id = "en-us-x-tpf-network",
                                name = "Network Voice",
                                language = Language.ENGLISH,
                                localeTag = "en-US",
                                quality = VoiceQuality.MEDIUM,
                                networkConnectionRequired = true,
                                enabledByDefault = false
                            )
                        )
                    )
                )
            )
        )
    }
}

@Preview
@Composable
private fun SettingsScreenPreviewWithError(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        SettingsScreenContent(
            state = SettingsUiState(
                isLoading = false,
                learningLanguages = listOf(
                    LearningLanguageUiState(
                        language = Language.ENGLISH,
                        isExpanded = false,
                        dictionarySizeBytes = 15 * 1024 * 1024L,
                        translations = emptyList()
                    )
                ),
                translationLanguages = emptySet(),
                errorMessage = UiText.Plain("Failed to load voices for this language")
            )
        )
    }
}

@Preview
@Composable
private fun SettingsScreenPreviewWithDeleteConfirmation(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        SettingsScreenContent(
            state = SettingsUiState(
                isLoading = false,
                learningLanguages = listOf(
                    LearningLanguageUiState(
                        language = Language.ENGLISH,
                        isExpanded = false,
                        dictionarySizeBytes = 15 * 1024 * 1024L,
                        translations = listOf(
                            TranslationUiState(
                                targetLanguage = Language.RUSSIAN,
                                isDownloaded = true,
                                isDownloadable = true,
                                sizeBytes = 8 * 1024 * 1024L
                            )
                        )
                    )
                ),
                translationLanguages = setOf(Language.RUSSIAN),
                deleteConfirmation = DeleteConfirmationState(
                    title = UiText.Plain("Remove English?"),
                    message = UiText.Plain("The dictionary and all its translations will be deleted. You can re-download anytime."),
                    warning = UiText.Plain("This will also remove 1 translation."),
                    onConfirm = {}
                )
            )
        )
    }
}

@Preview
@Composable
private fun SettingsScreenPreviewWithMixedStates(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        SettingsScreenContent(
            state = SettingsUiState(
                isLoading = false,
                learningLanguages = listOf(
                    LearningLanguageUiState(
                        language = Language.ENGLISH,
                        isExpanded = true,
                        dictionarySizeBytes = 15 * 1024 * 1024L,
                        translations = listOf(
                            TranslationUiState(
                                targetLanguage = Language.RUSSIAN,
                                isDownloaded = true,
                                isDownloadable = true,
                                sizeBytes = 8 * 1024 * 1024L
                            ),
                            TranslationUiState(
                                targetLanguage = Language.POLISH,
                                isDownloaded = false,
                                isDownloadable = true,
                                sizeBytes = 7 * 1024 * 1024L
                            )
                        )
                    )
                ),
                addableLanguages = listOf(
                    AvailableLanguageInfo(
                        language = Language.RUSSIAN,
                        dictionarySizeBytes = 12 * 1024 * 1024,
                        availableTranslations = emptyList()
                    ),
                    AvailableLanguageInfo(
                        language = Language.DUTCH,
                        dictionarySizeBytes = 10 * 1024 * 1024,
                        availableTranslations = emptyList()
                    )
                ),
                translationLanguages = setOf(Language.RUSSIAN, Language.POLISH),
                downloadingItems = mapOf(
                    "dict_ru" to DownloadProgress(5 * 1024 * 1024, 12 * 1024 * 1024),
                    "trans_en_pl" to DownloadProgress(2 * 1024 * 1024, 7 * 1024 * 1024)
                ),
                isAppDataExportSupported = true,
            )
        )
    }
}

@Preview
@Composable
private fun SettingsScreenPreviewAcknowledgements(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        SettingsScreenContent(
            state = SettingsUiState(
                isLoading = false,
                acknowledgementsVisible = true
            )
        )
    }
}
