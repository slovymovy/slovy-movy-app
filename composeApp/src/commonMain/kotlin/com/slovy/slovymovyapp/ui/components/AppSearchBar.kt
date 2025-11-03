package com.slovy.slovymovyapp.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.slovy.slovymovyapp.ui.ThemePreviewProvider
import com.slovy.slovymovyapp.ui.ThemedPreview
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.jetbrains.compose.ui.tooling.preview.PreviewParameter

/**
 * Themed search bar component following Material Design 3 principles.
 *
 * Features:
 * - Rounded (extraLarge shape - 28dp)
 * - Dynamic elevation on focus (level1 → level3)
 * - Surface color changes on focus
 * - Leading search icon
 * - Clear button when text is entered
 * - Primary color accent on focus
 *
 * @param query Current search query text
 * @param onQueryChange Callback when query text changes
 * @param modifier Modifier to be applied to the search bar
 * @param placeholder Placeholder text shown when query is empty
 * @param onSearch Optional callback when search is submitted (IME action)
 * @param leadingIcon Optional custom leading icon composable
 * @param enabled Whether the search bar is enabled
 */
@Composable
fun AppSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search...",
    onSearch: (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        placeholder = {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyLarge
            )
        },
        leadingIcon = leadingIcon ?: {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = if (isFocused) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        },
        trailingIcon = if (query.isNotEmpty()) {
            {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear",
                        tint = if (isFocused) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        } else null,
        shape = MaterialTheme.shapes.extraLarge,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            focusedBorderColor = Color.Transparent,
            unfocusedBorderColor = Color.Transparent,
            disabledBorderColor = Color.Transparent,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        singleLine = true,
        enabled = enabled,
        interactionSource = interactionSource,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(
            onSearch = { onSearch?.invoke() }
        ),
        textStyle = MaterialTheme.typography.bodyLarge
    )
}


@Preview
@Composable
private fun AppSearchBarEmptyPreview(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        AppSearchBar(
            query = "",
            onQueryChange = {},
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Preview
@Composable
private fun AppSearchBarWithTextPreview(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        AppSearchBar(
            query = "example search",
            onQueryChange = {},
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Preview
@Composable
private fun AppSearchBarDisabledPreview(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        AppSearchBar(
            query = "disabled",
            onQueryChange = {},
            enabled = false,
            modifier = Modifier.padding(16.dp)
        )
    }
}
