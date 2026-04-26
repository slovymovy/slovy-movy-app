package com.slovy.slovymovyapp.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import com.slovy.slovymovyapp.ui.ThemePreviewProvider
import com.slovy.slovymovyapp.ui.ThemedPreview
import com.slovy.slovymovyapp.ui.theme.LocalIsDarkTheme
import org.jetbrains.compose.resources.stringResource
import slovymovyapp.composeapp.generated.resources.Res
import slovymovyapp.composeapp.generated.resources.common_clear
import slovymovyapp.composeapp.generated.resources.common_search
import slovymovyapp.composeapp.generated.resources.common_search_placeholder

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
 * @param placeholder Placeholder text shown when query is empty; defaults to localized text
 * @param onSearch Optional callback when search is submitted (IME action)
 * @param leadingIcon Optional custom leading icon composable
 * @param enabled Whether the search bar is enabled
 */
@Composable
fun AppSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    onSearch: (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
    onFocusChanged: ((Boolean) -> Unit)? = null
) {
    var isFocused by remember { mutableStateOf(false) }
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
    val placeholderText = placeholder ?: stringResource(Res.string.common_search_placeholder)
    val searchContentDescription = stringResource(Res.string.common_search)
    val clearContentDescription = stringResource(Res.string.common_clear)

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
        PlatformSearchTextInput(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = placeholderText,
            onSearch = onSearch,
            leadingIcon = leadingIcon ?: {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = searchContentDescription,
                    tint = if (isFocused) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            },
            trailingIcon = if (query.isNotEmpty()) {
                {
                    IconButton(onClick = {
                        onQueryChange("")
                    }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = clearContentDescription,
                            tint = if (isFocused) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            } else null,
            enabled = enabled,
            focusRequester = focusRequester,
            onFocusChanged = { focused ->
                isFocused = focused
                onFocusChanged?.invoke(focused)
            },
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
