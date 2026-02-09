package com.slovy.slovymovyapp.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.slovy.slovymovyapp.ui.ThemePreviewProvider
import com.slovy.slovymovyapp.ui.ThemedPreview
import com.slovy.slovymovyapp.ui.theme.LocalIsDarkTheme

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
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
    onFocusChanged: ((Boolean) -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isDarkTheme = LocalIsDarkTheme.current

    // Animate elevation based on focus state
    // In dark theme, use higher elevation when focused for a soft glow effect
    val elevation by animateDpAsState(
        targetValue = when {
            !enabled -> 0.dp
            isFocused && isDarkTheme -> 6.dp
            isFocused -> 3.dp
            else -> 1.dp
        },
        label = "searchBarElevation"
    )

    // Use TextFieldValue to control cursor position
    var textFieldValue by remember { mutableStateOf(TextFieldValue(query, TextRange(query.length))) }

    // Sync external query changes and place cursor at end
    LaunchedEffect(query) {
        if (textFieldValue.text != query) {
            textFieldValue = TextFieldValue(query, TextRange(query.length))
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        tonalElevation = elevation,
        shadowElevation = elevation,
        color = when {
            !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            isFocused -> MaterialTheme.colorScheme.surfaceContainerHigh
            else -> MaterialTheme.colorScheme.surfaceContainerHighest
        }
    ) {
        OutlinedTextField(
            value = textFieldValue,
            onValueChange = { newValue ->
                textFieldValue = newValue
                onQueryChange(newValue.text)
            },
            modifier = Modifier
                .fillMaxWidth()
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .then(if (onFocusChanged != null) Modifier.onFocusChanged { onFocusChanged(it.isFocused) } else Modifier),
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
            trailingIcon = if (textFieldValue.text.isNotEmpty()) {
                {
                    IconButton(onClick = {
                        textFieldValue = TextFieldValue("", TextRange(0))
                        onQueryChange("")
                    }) {
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
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                disabledBorderColor = Color.Transparent,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                focusedPlaceholderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
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
}


@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun AppSearchBarEmptyPreview(
    @androidx.compose.ui.tooling.preview.PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        AppSearchBar(
            query = "",
            onQueryChange = {},
            modifier = Modifier.padding(16.dp)
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun AppSearchBarWithTextPreview(
    @androidx.compose.ui.tooling.preview.PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        AppSearchBar(
            query = "example search",
            onQueryChange = {},
            modifier = Modifier.padding(16.dp)
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun AppSearchBarDisabledPreview(
    @androidx.compose.ui.tooling.preview.PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
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
