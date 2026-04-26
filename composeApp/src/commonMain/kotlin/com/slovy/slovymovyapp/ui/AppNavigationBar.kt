package com.slovy.slovymovyapp.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import org.jetbrains.compose.resources.stringResource
import slovymovyapp.composeapp.generated.resources.*

enum class AppScreen {
    SEARCH,
    FAVORITES,
    WORD_DETAIL,
    SETTINGS
}

@Composable
fun AppNavigationBar(
    currentScreen: AppScreen,
    onNavigateToSearch: () -> Unit,
    onNavigateToFavorites: () -> Unit = {},
    onNavigateToWordDetail: () -> Unit,
    wordDetailLabel: String? = null,
    onNavigateToSettings: () -> Unit = {}
) {
    val searchLabel = stringResource(Res.string.nav_search)
    val favoritesLabel = stringResource(Res.string.nav_favorites)
    val wordDetailLabelText = stringResource(Res.string.nav_word_detail)
    val settingsLabel = stringResource(Res.string.nav_settings)

    val itemColors = NavigationBarItemDefaults.colors(
        indicatorColor = Color.Transparent,
        selectedIconColor = MaterialTheme.colorScheme.primary,
        selectedTextColor = MaterialTheme.colorScheme.primary,
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
        disabledIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
        disabledTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
    )

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = com.slovy.slovymovyapp.ui.theme.AppElevation.level3
    ) {
        NavigationBarItem(
            icon = {
                Icon(
                    imageVector = if (currentScreen == AppScreen.SEARCH) {
                        Icons.Filled.Search
                    } else {
                        Icons.Outlined.Search
                    },
                    contentDescription = searchLabel
                )
            },
            label = {
                Text(
                    searchLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall
                )
            },
            selected = currentScreen == AppScreen.SEARCH,
            onClick = onNavigateToSearch,
            colors = itemColors
        )
        NavigationBarItem(
            icon = {
                Icon(
                    imageVector = if (currentScreen == AppScreen.FAVORITES) {
                        Icons.Filled.Favorite
                    } else {
                        Icons.Outlined.FavoriteBorder
                    },
                    contentDescription = favoritesLabel
                )
            },
            label = {
                Text(
                    favoritesLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall
                )
            },
            selected = currentScreen == AppScreen.FAVORITES,
            onClick = onNavigateToFavorites,
            colors = itemColors
        )
        NavigationBarItem(
            icon = {
                Icon(
                    imageVector = if (currentScreen == AppScreen.WORD_DETAIL) {
                        Icons.Filled.Book
                    } else {
                        Icons.Outlined.Book
                    },
                    contentDescription = wordDetailLabelText
                )
            },
            label = {
                Text(
                    wordDetailLabel ?: wordDetailLabelText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall
                )
            },
            selected = currentScreen == AppScreen.WORD_DETAIL,
            enabled = wordDetailLabel != null,
            onClick = onNavigateToWordDetail,
            colors = itemColors
        )
        NavigationBarItem(
            icon = {
                Icon(
                    imageVector = if (currentScreen == AppScreen.SETTINGS) {
                        Icons.Filled.Settings
                    } else {
                        Icons.Outlined.Settings
                    },
                    contentDescription = settingsLabel
                )
            },
            label = {
                Text(
                    settingsLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall
                )
            },
            selected = currentScreen == AppScreen.SETTINGS,
            onClick = onNavigateToSettings,
            colors = itemColors
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
fun PreviewAppNavigationBar(
    @androidx.compose.ui.tooling.preview.PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        AppNavigationBar(
            currentScreen = AppScreen.SEARCH,
            onNavigateToSearch = {},
            onNavigateToFavorites = {},
            onNavigateToWordDetail = {},
            wordDetailLabel = "example"
        )
    }
}
