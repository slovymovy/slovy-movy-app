package com.slovy.slovymovyapp

import androidx.compose.runtime.*
import com.slovy.slovymovyapp.data.remote.DataDbManager
import com.slovy.slovymovyapp.data.remote.LanguageCard
import com.slovy.slovymovyapp.data.remote.PlatformDbSupport
import com.slovy.slovymovyapp.data.settings.Setting
import com.slovy.slovymovyapp.data.settings.SettingsRepository
import com.slovy.slovymovyapp.ui.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.compose.ui.tooling.preview.Preview

private enum class Route {
    DOWNLOAD_DICTIONARY,
    DOWNLOAD_TRANSLATION,
    LANGUAGE,

    DICTIONARY,
    SEARCH,
    DETAIL,
    ERROR
}

@Composable
@Preview
fun App(settingsRepository: SettingsRepository? = null, platformDbSupport: PlatformDbSupport? = null) {
    var route by remember { mutableStateOf<Route?>(null) }
    var nativeLanguage by remember { mutableStateOf<String?>(null) }
    var dictionaryLanguage by remember { mutableStateOf<String?>(null) }
    var selectedCard by remember { mutableStateOf<LanguageCard?>(null) }
    var selectedLemma by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var returnRoute by remember { mutableStateOf<Route?>(null) }

    val platform = remember(platformDbSupport) { platformDbSupport ?: PlatformDbSupport(null) }
    val dataManager = remember(platform, settingsRepository) { DataDbManager(platform, settingsRepository) }

    // Load persisted language and data version once
    fun selectInitialRoute(): Route {
        val native = settingsRepository?.getById(Setting.Name.LANGUAGE)?.value?.jsonPrimitive?.content
        if (native != null) {
            nativeLanguage = native

            val dictionary = settingsRepository.getById(Setting.Name.DICTIONARY)?.value?.jsonPrimitive?.content
            if (dictionary != null) {
                dictionaryLanguage = dictionary
                if (!dataManager.hasDictionary(dictionary)) {
                    return Route.DOWNLOAD_DICTIONARY
                } else if (!dataManager.hasTranslation(dictionary, native)) {
                    return Route.DOWNLOAD_TRANSLATION
                } else {
                    return Route.SEARCH
                }
            }
            return Route.DICTIONARY
        } else {
            return Route.LANGUAGE
        }
    }

    LaunchedEffect(Unit) {
        route = selectInitialRoute()
    }

    when (route) {
        null -> {
            // Waiting for initial route calculation; show nothing to avoid NPE during first composition
        }

        Route.DOWNLOAD_DICTIONARY -> DownloadScreen(
            description = "Downloading dictionary",
            download = { onProgress, cancel ->
                dataManager.ensureDictionary(dictionaryLanguage!!, onProgress, cancel)
            },
            onSuccess = {
                route = if (dataManager.hasTranslation(
                        dictionaryLanguage!!,
                        nativeLanguage!!
                    )
                ) Route.SEARCH else Route.DOWNLOAD_TRANSLATION
            },
            onCancel = {
                errorMessage = "Download cancelled"
                returnRoute = selectInitialRoute()
                route = Route.ERROR
            },
            onError = { t ->
                errorMessage = t.message ?: "Unknown error"
                returnRoute = selectInitialRoute()
                route = Route.ERROR
            }
        )

        Route.DOWNLOAD_TRANSLATION -> DownloadScreen(
            description = "Downloading translation",
            download = { onProgress, cancel ->
                dataManager.ensureTranslation(dictionaryLanguage!!, nativeLanguage!!, onProgress, cancel)
            },
            onSuccess = { route = Route.SEARCH },
            onCancel = {
                errorMessage = "Download cancelled"
                returnRoute = selectInitialRoute()
                route = Route.ERROR
            },
            onError = { t ->
                settingsRepository?.deleteById(Setting.Name.LANGUAGE)
                settingsRepository?.deleteById(Setting.Name.DICTIONARY)

                errorMessage = t.message ?: "Unknown error"
                returnRoute = selectInitialRoute()
                route = Route.ERROR
            }
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
                nativeLanguage = lang
                route = Route.DICTIONARY
            }
        )

        Route.DICTIONARY -> DictionarySelectionScreen(
            onDictionaryChosen = { lang ->
                // Persist selection
                settingsRepository!!.insert(
                    Setting(
                        id = Setting.Name.DICTIONARY,
                        value = Json.parseToJsonElement("\"$lang\"")
                    )
                )
                dictionaryLanguage = lang
                route = Route.DOWNLOAD_DICTIONARY
            }
        )

        Route.SEARCH -> SearchScreen(
            language = nativeLanguage,
            dictionaryLanguage = dictionaryLanguage,
            dataManager = dataManager,
            onWordSelected = { card, lemma ->
                selectedCard = card
                selectedLemma = lemma
                route = Route.DETAIL
            }
        )

        Route.DETAIL -> WordDetailScreen(
            card = selectedCard!!,
            lemma = selectedLemma,
            onBack = { route = Route.SEARCH }
        )

        Route.ERROR -> ErrorScreen(
            message = errorMessage ?: "Unknown error",
            onOkay = { route = returnRoute ?: Route.LANGUAGE }
        )
    }
}
