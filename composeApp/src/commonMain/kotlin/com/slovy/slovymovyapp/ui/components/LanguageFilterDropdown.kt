package com.slovy.slovymovyapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.ui.ThemePreviewProvider
import com.slovy.slovymovyapp.ui.ThemedPreview
import com.slovy.slovymovyapp.ui.theme.AppElevation
import com.slovy.slovymovyapp.ui.theme.AppSpacing

/** Matches the height of [AppSearchBar] so the two sit level when placed in the same row. */
private val AnchorHeight = 56.dp

/** Keeps the collapsed anchor square around the flag glyph. */
private val AnchorMinWidth = 56.dp

/** Wide enough for a flag plus the longest `Language.selfName`. */
private val MenuWidth = 200.dp

/**
 * Compact language picker used to filter a screen by learning language.
 *
 * Renders as a square anchor showing the current language's flag, expanding to a menu of
 * flag + self-name rows. Sized to sit next to [AppSearchBar] in a search row, and also used
 * standalone in a screen header.
 *
 * The caller owns the expanded state so it can live in the screen's `UiState` and survive
 * recomposition and configuration changes.
 *
 * @param languages Languages offered in the menu, in display order.
 * @param selectedLanguage Language whose flag the anchor shows; when null the first entry of
 *   [languages] is shown instead, so the anchor is never blank while a selection is resolving.
 * @param expanded Whether the menu is open.
 * @param onExpandedChange Called when the menu wants to open or close.
 * @param onLanguageSelected Called with the chosen language; the menu closes itself afterwards.
 * @param modifier Modifier applied to the dropdown container.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageFilterDropdown(
    languages: List<Language>,
    selectedLanguage: Language?,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onLanguageSelected: (Language) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentLanguage = selectedLanguage ?: languages.firstOrNull()

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        modifier = modifier,
    ) {
        Surface(
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
                .height(AnchorHeight)
                .widthIn(min = AnchorMinWidth),
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = AppElevation.level1,
            shadowElevation = AppElevation.level1,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            Row(
                modifier = Modifier.padding(start = AppSpacing.lg, end = AppSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = currentLanguage?.flag ?: "",
                    style = MaterialTheme.typography.bodyLarge,
                )
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            }
        }
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            modifier = Modifier.width(MenuWidth),
            shape = MaterialTheme.shapes.small,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 2.dp,
        ) {
            languages.forEach { language ->
                DropdownMenuItem(
                    text = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(language.flag)
                            Text(language.selfName)
                        }
                    },
                    onClick = {
                        onLanguageSelected(language)
                        onExpandedChange(false)
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

@Preview
@Composable
private fun LanguageFilterDropdownCollapsedPreview(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean,
) {
    ThemedPreview(darkTheme = isDark) {
        LanguageFilterDropdown(
            languages = listOf(Language.ENGLISH, Language.RUSSIAN, Language.DUTCH),
            selectedLanguage = Language.RUSSIAN,
            expanded = false,
            onExpandedChange = {},
            onLanguageSelected = {},
            modifier = Modifier.padding(AppSpacing.lg),
        )
    }
}

@Preview
@Composable
private fun LanguageFilterDropdownNoSelectionPreview(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean,
) {
    ThemedPreview(darkTheme = isDark) {
        LanguageFilterDropdown(
            languages = listOf(Language.ENGLISH, Language.POLISH),
            selectedLanguage = null,
            expanded = false,
            onExpandedChange = {},
            onLanguageSelected = {},
            modifier = Modifier.padding(AppSpacing.lg),
        )
    }
}
