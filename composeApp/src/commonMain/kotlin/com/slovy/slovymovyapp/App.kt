package com.slovy.slovymovyapp

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.slovy.slovymovyapp.data.favorites.FavoritesRepository
import com.slovy.slovymovyapp.data.remote.DataDbManager
import com.slovy.slovymovyapp.data.remote.DictionaryRepository
import com.slovy.slovymovyapp.data.remote.PlatformDbSupport
import com.slovy.slovymovyapp.data.settings.Setting
import com.slovy.slovymovyapp.data.settings.SettingsRepository
import com.slovy.slovymovyapp.speech.TextToSpeechManager
import com.slovy.slovymovyapp.ui.*
import com.slovy.slovymovyapp.ui.theme.AppTheme
import com.slovy.slovymovyapp.ui.word.WordDetailScreen
import com.slovy.slovymovyapp.ui.word.WordDetailViewModel
import kotlinx.coroutines.launch
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
    data object Favorites : AppDestination

    @Serializable
    data class WordDetail(
        val dictionaryLanguage: String,
        val lemma: String,
        val targetSenseId: String? = null,
    ) : AppDestination

    @Serializable
    data object Settings : AppDestination

    @Serializable
    data class Error(val message: String) : AppDestination

    @Serializable
    data object DataVersionMismatch : AppDestination
}

@Composable
@Preview
fun App(
    settingsRepository: SettingsRepository? = null,
    platformDbSupport: PlatformDbSupport? = null,
    androidContext: Any? = null
) {
    var nativeLanguage by remember { mutableStateOf<String?>(null) }
    var dictionaryLanguage by remember { mutableStateOf<String?>(null) }

    val platform = remember(platformDbSupport) { platformDbSupport ?: PlatformDbSupport(null) }
    val dataManager = remember(platform, settingsRepository) { DataDbManager(platform, settingsRepository) }
    val dictionaryRepository = remember(dataManager) { DictionaryRepository(dataManager) }
    val favoritesRepository = remember(dataManager) {
        FavoritesRepository(dataManager.openAppDatabase())
    }
    val ttsManager = remember(androidContext) { TextToSpeechManager(androidContext) }

    val navController = rememberNavController()
    var startDestination by remember { mutableStateOf<AppDestination?>(null) }
    val wordDetailViewModels = remember { linkedMapOf<AppDestination.WordDetail, WordDetailViewModel>() }
    // Shared ViewModel for Favorites screen to preserve state across navigation
    val favoritesViewModel = remember { FavoritesViewModel(favoritesRepository, dictionaryRepository) }

    suspend fun selectInitialDestination(): AppDestination {
        // Check if data version is current
        if (!dataManager.hasRequiredVersion()) {
            val savedVersion = settingsRepository?.getById(Setting.Name.DATA_VERSION)?.value?.jsonPrimitive?.content
            // If version exists but is outdated, show error before deleting
            if (savedVersion != null) {
                return AppDestination.DataVersionMismatch
            }
        }

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

    AppTheme {
        NavHost(
            navController = navController,
            startDestination = resolvedStart,
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None }
        ) {
            composable<AppDestination.Language> { backStackEntry ->
                val viewModel = viewModel(
                    viewModelStoreOwner = backStackEntry
                ) {
                    LanguageSelectionViewModel()
                }

                LanguageSelectionScreen(
                    viewModel = viewModel,
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
            composable<AppDestination.Dictionary> { backStackEntry ->
                val viewModel = viewModel(
                    viewModelStoreOwner = backStackEntry
                ) {
                    DictionarySelectionViewModel()
                }

                DictionarySelectionScreen(
                    viewModel = viewModel,
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
            composable<AppDestination.DownloadDictionary> { backStackEntry ->
                val dictLang = dictionaryLanguage
                if (dictLang == null) {
                    LaunchedEffect(Unit) {
                        navController.navigate(AppDestination.Error("Dictionary not selected")) {
                            popUpTo<AppDestination.DownloadDictionary> { inclusive = true }
                        }
                    }
                } else {
                    val viewModel = viewModel(
                        viewModelStoreOwner = backStackEntry
                    ) {
                        DownloadViewModel(
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

                    DownloadScreen(
                        viewModel = viewModel,
                        description = "Downloading dictionary"
                    )
                }
            }
            composable<AppDestination.DownloadTranslation> { backStackEntry ->
                val dictLang = dictionaryLanguage
                val native = nativeLanguage
                if (dictLang == null || native == null) {
                    LaunchedEffect(Unit) {
                        navController.navigate(AppDestination.Error("Language configuration missing")) {
                            popUpTo<AppDestination.DownloadTranslation> { inclusive = true }
                        }
                    }
                } else {
                    val viewModel = viewModel(
                        viewModelStoreOwner = backStackEntry
                    ) {
                        DownloadViewModel(
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

                    DownloadScreen(
                        viewModel = viewModel,
                        description = "Downloading translation"
                    )
                }
            }
            composable<AppDestination.Search> { backStackEntry ->

                val viewModel = viewModel(
                    viewModelStoreOwner = backStackEntry
                ) {
                    SearchViewModel(DictionaryRepository(dataManager))
                }

                SearchScreen(
                    viewModel = viewModel,
                    onWordSelected = { item ->
                        val destination = AppDestination.WordDetail(
                            dictionaryLanguage = item.language,
                            lemma = item.lemma,
                        )
                        navController.navigate(destination)
                    },
                    wordDetailLabel = wordDetailViewModels.keys.lastOrNull()?.lemma,
                    onNavigateToWordDetail = {
                        wordDetailViewModels.keys.lastOrNull()?.let { destination ->
                            navController.navigate(destination)
                        }
                    },
                    onNavigateToFavorites = {
                        if (!navController.popBackStack(AppDestination.Favorites, inclusive = false))
                            navController.navigate(AppDestination.Favorites)
                    },
                    onNavigateToSettings = {
                        if (!navController.popBackStack(AppDestination.Settings, inclusive = false))
                            navController.navigate(AppDestination.Settings)
                    }
                )
            }
            composable<AppDestination.Favorites> {
                // Reload favorites when navigating to this screen
                LaunchedEffect(Unit) {
                    favoritesViewModel.loadFavorites()
                }

                FavoritesScreen(
                    viewModel = favoritesViewModel,
                    onNavigateToSearch = {
                        if (!navController.popBackStack(AppDestination.Search, inclusive = false))
                            navController.navigate(AppDestination.Search)
                    },
                    onNavigateToWordDetail = { targetLang, lemma, senseId ->
                        val destination = AppDestination.WordDetail(
                            dictionaryLanguage = targetLang,
                            lemma = lemma,
                            targetSenseId = senseId
                        )
                        navController.navigate(destination)
                    },
                    wordDetailLabel = wordDetailViewModels.keys.lastOrNull()?.lemma,
                    onNavigateToLastWordDetail = {
                        wordDetailViewModels.keys.lastOrNull()?.let { destination ->
                            navController.navigate(destination)
                        }
                    },
                    onNavigateToSettings = {
                        if (!navController.popBackStack(AppDestination.Settings, inclusive = false))
                            navController.navigate(AppDestination.Settings)
                    }
                )
            }
            composable<AppDestination.Settings> { backStackEntry ->
                val viewModel = viewModel(
                    viewModelStoreOwner = backStackEntry
                ) {
                    SettingsViewModel(ttsManager)
                }

                SettingsScreen(
                    viewModel = viewModel,
                    onNavigateToSearch = {
                        if (!navController.popBackStack(AppDestination.Search, inclusive = false))
                            navController.navigate(AppDestination.Search)
                    },
                    onNavigateToFavorites = {
                        if (!navController.popBackStack(AppDestination.Favorites, inclusive = false))
                            navController.navigate(AppDestination.Favorites)
                    },
                    wordDetailLabel = wordDetailViewModels.keys.lastOrNull()?.lemma,
                    onNavigateToWordDetail = {
                        wordDetailViewModels.keys.lastOrNull()?.let { destination ->
                            navController.navigate(destination)
                        }
                    }
                )
            }
            composable<AppDestination.WordDetail> { backStackEntry ->
                val args = backStackEntry.toRoute<AppDestination.WordDetail>()

                // Try to retrieve cached ViewModel, otherwise create new one with proper lifecycle
                val viewModel = viewModel(
                    viewModelStoreOwner = backStackEntry
                ) {
                    wordDetailViewModels[args] ?: WordDetailViewModel(
                        dictionaryRepository,
                        favoritesRepository,
                        args.dictionaryLanguage,
                        args.lemma,
                        args.targetSenseId
                    )
                }.also {
                    // Enforce max N cached ViewModels (remove oldest if at capacity)
                    if (wordDetailViewModels.size >= 10 && args !in wordDetailViewModels) {
                        wordDetailViewModels.remove(wordDetailViewModels.keys.first())
                    }
                    wordDetailViewModels[args] = it
                }


                // Reload favorites when navigating to this screen
                LaunchedEffect(Unit) {
                    viewModel.loadFavorites()
                }

                WordDetailScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onNavigateToSearch = {
                        navController.popBackStack(AppDestination.Search, inclusive = false)
                    },
                    onNavigateToFavorites = {
                        if (!navController.popBackStack(AppDestination.Favorites, inclusive = false))
                            navController.navigate(AppDestination.Favorites)
                    }
                )
            }
            composable<AppDestination.DataVersionMismatch> { backStackEntry ->
                val coroutineScope = rememberCoroutineScope()
                val viewModel = viewModel(
                    viewModelStoreOwner = backStackEntry
                ) {
                    ErrorViewModel("Data format has been updated. Your downloaded dictionaries will be deleted and need to be re-downloaded.")
                }

                ErrorScreen(
                    viewModel = viewModel,
                    onOkay = {
                        coroutineScope.launch {
                            dataManager.deleteAllDownloadedData()
                            val target = selectInitialDestination()
                            navController.navigate(target) {
                                popUpTo<AppDestination.DataVersionMismatch> { inclusive = true }
                            }
                        }
                    }
                )
            }
            composable<AppDestination.Error> { backStackEntry ->
                val args = backStackEntry.toRoute<AppDestination.Error>()
                val coroutineScope = rememberCoroutineScope()
                val viewModel = viewModel(
                    viewModelStoreOwner = backStackEntry
                ) {
                    ErrorViewModel(args.message)
                }

                ErrorScreen(
                    viewModel = viewModel,
                    onOkay = {
                        coroutineScope.launch {
                            val target = selectInitialDestination()
                            navController.navigate(target) {
                                popUpTo(navController.graph.startDestinationId) { inclusive = true }
                            }
                        }
                    }
                )
            }
        }
    }
}
