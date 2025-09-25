package com.slovy.slovymovyapp

import androidx.compose.runtime.*
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.slovy.slovymovyapp.data.remote.DataDbManager
import com.slovy.slovymovyapp.data.remote.DictionaryRepository
import com.slovy.slovymovyapp.data.remote.PlatformDbSupport
import com.slovy.slovymovyapp.data.settings.Setting
import com.slovy.slovymovyapp.data.settings.SettingsRepository
import com.slovy.slovymovyapp.ui.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.compose.ui.tooling.preview.Preview

@Serializable
private sealed interface AppDestination {
    @Serializable
    data object DownloadDictionary : AppDestination

    @Serializable
    data object DownloadTranslation : AppDestination

    @Serializable
    data object Language : AppDestination

    @Serializable
    data object Dictionary : AppDestination

    @Serializable
    data object Search : AppDestination

    @Serializable
    data class WordDetail(
        val dictionaryLanguage: String,
        val lemma: String,
    ) : AppDestination

    @Serializable
    data class Error(val message: String) : AppDestination
}

@Composable
@Preview
fun App(settingsRepository: SettingsRepository? = null, platformDbSupport: PlatformDbSupport? = null) {
    var nativeLanguage by remember { mutableStateOf<String?>(null) }
    var dictionaryLanguage by remember { mutableStateOf<String?>(null) }

    val platform = remember(platformDbSupport) { platformDbSupport ?: PlatformDbSupport(null) }
    val dataManager = remember(platform, settingsRepository) { DataDbManager(platform, settingsRepository) }
    val dictionaryRepository = remember(dataManager) { DictionaryRepository(dataManager) }

    val navController = rememberNavController()
    var startDestination by remember { mutableStateOf<AppDestination?>(null) }

    fun selectInitialDestination(): AppDestination {
        val native = settingsRepository?.getById(Setting.Name.LANGUAGE)?.value?.jsonPrimitive?.content
        if (native != null) {
            nativeLanguage = native
            val dictionary = settingsRepository.getById(Setting.Name.DICTIONARY)?.value?.jsonPrimitive?.content
            if (dictionary != null) {
                dictionaryLanguage = dictionary
                return when {
                    !dataManager.hasDictionary(dictionary) -> AppDestination.DownloadDictionary
                    !dataManager.hasTranslation(dictionary, native) -> AppDestination.DownloadTranslation
                    else -> AppDestination.Search
                }
            }
            return AppDestination.Dictionary
        }
        return AppDestination.Language
    }

    LaunchedEffect(Unit) {
        if (startDestination == null) {
            startDestination = selectInitialDestination()
        }
    }

    val resolvedStart = startDestination ?: return

    NavHost(navController = navController, startDestination = resolvedStart) {
        composable<AppDestination.Language> {
            LanguageSelectionScreen(
                onLanguageChosen = { lang ->
                    settingsRepository?.insert(
                        Setting(
                            id = Setting.Name.LANGUAGE,
                            value = Json.parseToJsonElement("\"$lang\"")
                        )
                    )
                    nativeLanguage = lang
                    navController.navigate(AppDestination.Dictionary)
                }
            )
        }
        composable<AppDestination.Dictionary> {
            DictionarySelectionScreen(
                onDictionaryChosen = { lang ->
                    settingsRepository?.insert(
                        Setting(
                            id = Setting.Name.DICTIONARY,
                            value = Json.parseToJsonElement("\"$lang\"")
                        )
                    )
                    dictionaryLanguage = lang
                    navController.navigate(AppDestination.DownloadDictionary)
                }
            )
        }
        composable<AppDestination.DownloadDictionary> {
            val dictLang = dictionaryLanguage
            if (dictLang == null) {
                LaunchedEffect(Unit) {
                    navController.navigate(AppDestination.Error("Dictionary not selected")) {
                        popUpTo<AppDestination.DownloadDictionary> { inclusive = true }
                    }
                }
            } else {
                DownloadScreen(
                    description = "Downloading dictionary",
                    download = { onProgress, cancel ->
                        dataManager.ensureDictionary(dictLang, onProgress, cancel)
                    },
                    onSuccess = {
                        val native = nativeLanguage
                        if (native != null && dataManager.hasTranslation(dictLang, native)) {
                            navController.navigate(AppDestination.Search) {
                                popUpTo<AppDestination.Dictionary> { inclusive = false }
                            }
                        } else {
                            navController.navigate(AppDestination.DownloadTranslation) {
                                popUpTo<AppDestination.DownloadDictionary> { inclusive = true }
                            }
                        }
                    },
                    onCancel = {
                        navController.navigate(AppDestination.Error("Download cancelled")) {
                            popUpTo<AppDestination.DownloadDictionary> { inclusive = true }
                        }
                    },
                    onError = { t ->
                        navController.navigate(AppDestination.Error(t.message ?: "Unknown error")) {
                            popUpTo<AppDestination.DownloadDictionary> { inclusive = true }
                        }
                    }
                )
            }
        }
        composable<AppDestination.DownloadTranslation> {
            val dictLang = dictionaryLanguage
            val native = nativeLanguage
            if (dictLang == null || native == null) {
                LaunchedEffect(Unit) {
                    navController.navigate(AppDestination.Error("Language configuration missing")) {
                        popUpTo<AppDestination.DownloadTranslation> { inclusive = true }
                    }
                }
            } else {
                DownloadScreen(
                    description = "Downloading translation",
                    download = { onProgress, cancel ->
                        dataManager.ensureTranslation(dictLang, native, onProgress, cancel)
                    },
                    onSuccess = {
                        navController.navigate(AppDestination.Search) {
                            popUpTo<AppDestination.Dictionary> { inclusive = false }
                        }
                    },
                    onCancel = {
                        navController.navigate(AppDestination.Error("Download cancelled")) {
                            popUpTo<AppDestination.DownloadTranslation> { inclusive = true }
                        }
                    },
                    onError = { t ->
                        settingsRepository!!.deleteById(Setting.Name.LANGUAGE)
                        settingsRepository.deleteById(Setting.Name.DICTIONARY)
                        navController.navigate(AppDestination.Error(t.message ?: "Unknown error")) {
                            popUpTo<AppDestination.DownloadTranslation> { inclusive = true }
                        }
                    }
                )
            }
        }
        composable<AppDestination.Search> {
            SearchScreen(
                language = nativeLanguage,
                dictionaryLanguage = dictionaryLanguage,
                dataManager = dataManager,
                onWordSelected = { item ->
                    navController.navigate(
                        AppDestination.WordDetail(
                            dictionaryLanguage = item.language,
                            lemma = item.lemma,
                        )
                    )
                }
            )
        }
        composable<AppDestination.WordDetail> { backStackEntry ->
            val args = backStackEntry.toRoute<AppDestination.WordDetail>()
            val card = remember(args.dictionaryLanguage, args.lemma) {
                dictionaryRepository.getLanguageCard(args.dictionaryLanguage, args.lemma)
            }
            if (card == null) {
                LaunchedEffect(args.dictionaryLanguage, args.lemma) {
                    navController.navigate(AppDestination.Error("Word not found, seems like database is broken")) {
                        popUpTo<AppDestination.WordDetail> { inclusive = true }
                    }
                }
            } else {
                WordDetailScreen(
                    card = card,
                    onBack = { navController.popBackStack() }
                )
            }
        }
        composable<AppDestination.Error> { backStackEntry ->
            val args = backStackEntry.toRoute<AppDestination.Error>()
            ErrorScreen(
                message = args.message,
                onOkay = {
                    val target = selectInitialDestination()
                    navController.navigate(target) {
                        popUpTo(navController.graph.startDestinationId) { inclusive = true }
                    }
                }
            )
        }
    }
}
