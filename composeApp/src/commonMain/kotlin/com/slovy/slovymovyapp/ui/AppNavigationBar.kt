package com.slovy.slovymovyapp.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import org.jetbrains.compose.ui.tooling.preview.Preview

enum class AppScreen {
    SEARCH,
    WORD_DETAIL
}

@Composable
fun AppNavigationBar(
    currentScreen: AppScreen,
    isWordDetailAvailable: Boolean,
    onNavigateToSearch: () -> Unit,
    onNavigateToWordDetail: () -> Unit
) {
    NavigationBar {
        NavigationBarItem(
            icon = { Text("🔍", style = MaterialTheme.typography.titleLarge) },
            label = { Text("Search") },
            selected = currentScreen == AppScreen.SEARCH,
            onClick = onNavigateToSearch
        )
        NavigationBarItem(
            icon = { Text("📖", style = MaterialTheme.typography.titleLarge) },
            label = { Text("Word Detail") },
            selected = currentScreen == AppScreen.WORD_DETAIL,
            enabled = isWordDetailAvailable,
            onClick = onNavigateToWordDetail
        )
    }
}

@Preview
@Composable
fun PreviewAppNavigationBar() {
    AppNavigationBar(
        currentScreen = AppScreen.SEARCH,
        isWordDetailAvailable = true,
        onNavigateToSearch = {},
        onNavigateToWordDetail = {}
    )
}
