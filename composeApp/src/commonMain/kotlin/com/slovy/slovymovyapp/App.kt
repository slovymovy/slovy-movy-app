package com.slovy.slovymovyapp

import androidx.compose.runtime.*
import com.slovy.slovymovyapp.data.settings.Setting
import com.slovy.slovymovyapp.data.settings.SettingsRepository
import com.slovy.slovymovyapp.ui.LanguageSelectionScreen
import com.slovy.slovymovyapp.ui.SearchScreen
import com.slovy.slovymovyapp.ui.WordDetailScreen
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.compose.ui.tooling.preview.Preview

private enum class Route {
    LANGUAGE,
    SEARCH,
    DETAIL
}

@Composable
@Preview
fun App(settingsRepository: SettingsRepository) {
    var route by remember { mutableStateOf(Route.SEARCH) }
    var selectedLanguage by remember { mutableStateOf<String?>(null) }
    var selectedWord by remember { mutableStateOf<String?>(null) }

    // Load persisted native language once
    LaunchedEffect(Unit) {
        val saved = settingsRepository.getById(Setting.Name.native_language)?.value?.jsonPrimitive?.content
        if (saved.isNullOrBlank()) {
            route = Route.LANGUAGE
        } else {
            selectedLanguage = saved
            route = Route.SEARCH
        }
    }

    when (route) {
        Route.LANGUAGE -> LanguageSelectionScreen(
            settingsRepository = settingsRepository,
            onDone = {
                val lang = settingsRepository.getById(Setting.Name.native_language)?.value?.jsonPrimitive?.content
                selectedLanguage = lang
                route = Route.SEARCH
            }
        )

        Route.SEARCH -> SearchScreen(
            language = selectedLanguage,
            settingsRepository = settingsRepository,
            onWordSelected = { word ->
                selectedWord = word
                route = Route.DETAIL
            }
        )

        Route.DETAIL -> WordDetailScreen(
            language = selectedLanguage,
            word = selectedWord ?: "",
            onBack = { route = Route.SEARCH }
        )
    }
}
