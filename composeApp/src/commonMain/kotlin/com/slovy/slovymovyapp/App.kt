package com.slovy.slovymovyapp

import androidx.compose.runtime.*
import com.slovy.slovymovyapp.data.remote.DataDbManager
import com.slovy.slovymovyapp.data.remote.PlatformDbSupport
import com.slovy.slovymovyapp.data.settings.Setting
import com.slovy.slovymovyapp.data.settings.SettingsRepository
import com.slovy.slovymovyapp.ui.DownloadDataScreen
import com.slovy.slovymovyapp.ui.LanguageSelectionScreen
import com.slovy.slovymovyapp.ui.SearchScreen
import com.slovy.slovymovyapp.ui.WordDetailScreen
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.compose.ui.tooling.preview.Preview

private enum class Route {
    DOWNLOAD,
    LANGUAGE,
    SEARCH,
    DETAIL
}

@Composable
@Preview
fun App(settingsRepository: SettingsRepository? = null, platformDbSupport: PlatformDbSupport? = null) {
    var route by remember { mutableStateOf(Route.DOWNLOAD) }
    var selectedLanguage by remember { mutableStateOf<String?>(null) }
    var selectedWord by remember { mutableStateOf<String?>(null) }

    val platform = remember(platformDbSupport) { platformDbSupport ?: PlatformDbSupport(null) }
    val dataManager = remember(platform, settingsRepository) { DataDbManager(platform, settingsRepository) }

    // Load persisted language and data version once
    LaunchedEffect(Unit) {
        val saved = settingsRepository?.getById(Setting.Name.LANGUAGE)?.value?.jsonPrimitive?.content
        val hasVersion = dataManager.hasRequiredVersion()
        selectedLanguage = saved
        route = if (!hasVersion) Route.DOWNLOAD else if (saved.isNullOrBlank()) Route.LANGUAGE else Route.SEARCH
    }

    when (route) {
        Route.DOWNLOAD -> DownloadDataScreen(
            manager = dataManager,
            onSuccess = { route = if (selectedLanguage.isNullOrBlank()) Route.LANGUAGE else Route.SEARCH },
            onCancel = { /* stay on screen or close app */ },
            onError = { /* stay and allow retry */ }
        )

        Route.LANGUAGE -> LanguageSelectionScreen(
            onLanguageChosen = { lang ->
                // Persist selection
                settingsRepository!!.insert(
                    Setting(
                        id = Setting.Name.LANGUAGE,
                        value = Json.parseToJsonElement("\"$lang\"")
                    )
                )
                selectedLanguage = lang
                route = Route.SEARCH
            }
        )

        Route.SEARCH -> SearchScreen(
            language = selectedLanguage,
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
