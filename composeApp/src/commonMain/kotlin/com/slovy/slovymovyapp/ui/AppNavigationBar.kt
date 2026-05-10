package com.slovy.slovymovyapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import slovymovyapp.composeapp.generated.resources.*

enum class AppScreen {
    SEARCH,
    FAVORITES,
    STATS,
    WORD_DETAIL,
    SETTINGS
}

@Composable
fun AppNavigationBar(
    currentScreen: AppScreen,
    onNavigateToSearch: () -> Unit,
    onNavigateToFavorites: () -> Unit = {},
    onNavigateToStats: () -> Unit = {},
    onNavigateToWordDetail: () -> Unit,
    wordDetailLabel: String? = null,
    onNavigateToSettings: () -> Unit = {},
    hasFavoritesToReview: Boolean = false,
) {
    val searchLabel = stringResource(Res.string.nav_search)
    val favoritesLabel = stringResource(Res.string.nav_favorites)
    val statsLabel = stringResource(Res.string.nav_stats)
    val wordDetailLabelText = stringResource(Res.string.nav_word_detail)
    val settingsLabel = stringResource(Res.string.nav_settings)
    val showFavoritesDueDot = hasFavoritesToReview && currentScreen != AppScreen.FAVORITES

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
                NavigationIconWithReviewDot(
                    imageVector = if (currentScreen == AppScreen.FAVORITES) {
                        Icons.Filled.Favorite
                    } else {
                        Icons.Outlined.FavoriteBorder
                    },
                    contentDescription = favoritesLabel,
                    showDot = showFavoritesDueDot,
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
                    imageVector = if (currentScreen == AppScreen.STATS) {
                        StatsFilledVector
                    } else {
                        StatsOutlineVector
                    },
                    contentDescription = statsLabel
                )
            },
            label = {
                Text(
                    statsLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall
                )
            },
            selected = currentScreen == AppScreen.STATS,
            onClick = onNavigateToStats,
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

@Composable
private fun NavigationIconWithReviewDot(
    imageVector: ImageVector,
    contentDescription: String,
    showDot: Boolean,
) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .wrapContentSize(unbounded = true)
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            modifier = Modifier.size(22.dp),
        )
        if (showDot) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-3.5).dp)
                    .size(11.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(MaterialTheme.colorScheme.error, CircleShape),
                )
            }
        }
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
            onNavigateToStats = {},
            onNavigateToWordDetail = {},
            wordDetailLabel = "example",
            hasFavoritesToReview = true,
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
fun PreviewAppNavigationBarFavoritesSelected(
    @androidx.compose.ui.tooling.preview.PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        AppNavigationBar(
            currentScreen = AppScreen.FAVORITES,
            onNavigateToSearch = {},
            onNavigateToFavorites = {},
            onNavigateToStats = {},
            onNavigateToWordDetail = {},
            wordDetailLabel = "example",
            hasFavoritesToReview = true,
        )
    }
}
