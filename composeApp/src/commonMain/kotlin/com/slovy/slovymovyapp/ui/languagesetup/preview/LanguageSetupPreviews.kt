package com.slovy.slovymovyapp.ui.languagesetup

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.slovy.slovymovyapp.data.Language
import slovymovyapp.composeapp.generated.resources.*
import com.slovy.slovymovyapp.ui.ThemePreviewProvider
import com.slovy.slovymovyapp.ui.ThemedPreview

@Preview
@Composable
private fun LanguageSetupScreenDefaultPreview(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        LanguageSetupScreenContent(
            state = LanguageSetupUiState(
                isLoading = false,
                availableLanguages = Language.entries
            )
        )
    }
}

@Preview
@Composable
private fun LanguageSetupScreenLearningOnlyPreview(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        LanguageSetupScreenContent(
            state = LanguageSetupUiState(
                isLoading = false,
                availableLanguages = Language.entries,
                learningLanguage = Language.DUTCH
            )
        )
    }
}

@Preview
@Composable
private fun LanguageSetupScreenSelectedPreview(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        LanguageSetupScreenContent(
            state = LanguageSetupUiState(
                isLoading = false,
                availableLanguages = Language.entries,
                learningLanguage = Language.DUTCH,
                nativeLanguages = setOf(Language.ENGLISH)
            )
        )
    }
}

@Preview
@Composable
private fun LanguageSetupScreenNoTranslationPreview(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        LanguageSetupScreenContent(
            state = LanguageSetupUiState(
                isLoading = false,
                availableLanguages = Language.entries,
                learningLanguage = Language.DUTCH,
                noTranslationSelected = true
            )
        )
    }
}

