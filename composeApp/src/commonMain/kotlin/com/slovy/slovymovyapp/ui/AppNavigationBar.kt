package com.slovy.slovymovyapp.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.jetbrains.compose.ui.tooling.preview.PreviewParameter

enum class AppScreen {
    SEARCH,
    FAVORITES,
    WORD_DETAIL
}

@Composable
fun AppNavigationBar(
    currentScreen: AppScreen,
    onNavigateToSearch: () -> Unit,
    onNavigateToFavorites: () -> Unit = {},
    onNavigateToWordDetail: () -> Unit,
    wordDetailLabel: String? = null
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        NavigationBarItem(
            icon = {
                Icon(
                    imageVector = if (currentScreen == AppScreen.SEARCH) {
                        Icons.Filled.Search
                    } else {
                        Icons.Outlined.Search
                    },
                    contentDescription = "Search"
                )
            },
            label = { Text("Search") },
            selected = currentScreen == AppScreen.SEARCH,
            onClick = onNavigateToSearch
        )
        NavigationBarItem(
            icon = {
                Icon(
                    imageVector = if (currentScreen == AppScreen.FAVORITES) {
                        Icons.Filled.Favorite
                    } else {
                        Icons.Outlined.FavoriteBorder
                    },
                    contentDescription = "Favorites"
                )
            },
            label = { Text("Favorites") },
            selected = currentScreen == AppScreen.FAVORITES,
            onClick = onNavigateToFavorites
        )
        NavigationBarItem(
            icon = {
                Icon(
                    imageVector = if (currentScreen == AppScreen.WORD_DETAIL) {
                        Icons.Filled.Book
                    } else {
                        Icons.Outlined.Book
                    },
                    contentDescription = "Word Detail"
                )
            },
            label = { Text(wordDetailLabel ?: "Word Detail") },
            selected = currentScreen == AppScreen.WORD_DETAIL,
            enabled = wordDetailLabel != null,
            onClick = onNavigateToWordDetail
        )
    }
}

@Preview
@Composable
fun PreviewAppNavigationBar(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
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
