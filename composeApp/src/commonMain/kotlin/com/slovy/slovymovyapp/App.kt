package com.slovy.slovymovyapp

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.favorites.FavoritesRepository
import com.slovy.slovymovyapp.data.local.LocalDbManager
import com.slovy.slovymovyapp.data.remote.DataDbManager
import com.slovy.slovymovyapp.data.remote.DictionaryClient
import com.slovy.slovymovyapp.data.remote.DictionaryRepository
import com.slovy.slovymovyapp.data.remote.PlatformDbSupport
import com.slovy.slovymovyapp.data.settings.Setting
import com.slovy.slovymovyapp.data.settings.SettingsRepository
import com.slovy.slovymovyapp.speech.TextToSpeechManager
import com.slovy.slovymovyapp.speech.VoiceFilterHelper
import com.slovy.slovymovyapp.ui.*
import com.slovy.slovymovyapp.ui.theme.AppTheme
import com.slovy.slovymovyapp.ui.word.WordDetailScreen
import com.slovy.slovymovyapp.ui.word.WordDetailViewModel
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive

@Serializable
private sealed interface AppDestination {
    @Serializable
    data object DownloadDictionary : AppDestination

    @Serializable
    data object DownloadTranslation : AppDestination

    @Serializable
    data object SelectLanguage : AppDestination

    @Serializable
    data object SelectDictionary : AppDestination

    @Serializable
    data object Search : AppDestination

    @Serializable
    data object Favorites : AppDestination

    @Serializable
    data class WordDetail(
        @Deprecated("temporal hack, looks like IOS can't handle enums here")
        val dictionaryLanguageCode: String,
        val lemma: String,
        val targetSenseId: String? = null,
        val translationLanguageCodes: List<String>? = null,
    ) : AppDestination {
        @Suppress("DEPRECATION")
        val dictionaryLanguage: Language
            get() = Language.fromCode(dictionaryLanguageCode)

        val translationLanguages: List<Language>?
            get() = translationLanguageCodes?.mapNotNull { Language.fromCodeOrNull(it) }
    }

    @Serializable
    data object Settings : AppDestination

    @Serializable
    data class Error(val message: String) : AppDestination

    @Serializable
    data object DataVersionMismatch : AppDestination
}

@Composable
fun App(
    settingsRepository: SettingsRepository,
    dataManager: DataDbManager,
    platform: PlatformDbSupport,
    appBuildConfig: AppBuildConfig,
    androidContext: Any? = null,
) {
    var nativeLanguage by remember { mutableStateOf<Language?>(null) }
    var dictionaryLanguage by remember { mutableStateOf<Language?>(null) }
    val favoritesRepository = remember(dataManager) {
        FavoritesRepository(DataDbManager.openAppDatabase(platform))
    }
    val localDbManager = remember(platform) { LocalDbManager(platform) }
    val dictionaryRepository =
        remember(dataManager, localDbManager, favoritesRepository) {
            DictionaryRepository(dataManager, localDbManager, favoritesRepository)
        }
    val dictionaryClient = remember(platform, dictionaryRepository, localDbManager, dataManager) {
        DictionaryClient(platform, dictionaryRepository, localDbManager, dataManager)
    }
    val ttsManager = remember(androidContext) { TextToSpeechManager(androidContext) }
    val voiceFilterHelper = remember(settingsRepository) { VoiceFilterHelper(settingsRepository) }

    val navController = rememberNavController()
    var startDestination by remember { mutableStateOf<AppDestination?>(null) }
    val wordDetailViewModels = remember { linkedMapOf<AppDestination.WordDetail, WordDetailViewModel>() }
    // Shared ViewModel for Favorites screen to preserve state across navigation
    val favoritesViewModel = remember { FavoritesViewModel(favoritesRepository, dictionaryRepository) }
    val buildConfig = remember { appBuildConfig }
    val settingsViewModel = remember { SettingsViewModel(ttsManager, voiceFilterHelper, dataManager, buildConfig) }
    val coroutineScope = rememberCoroutineScope()

    suspend fun selectInitialDestination(): AppDestination {
        // Check if data version is current
        if (!dataManager.hasRequiredVersion()) {
            val savedVersion = settingsRepository.getById(Setting.Name.DATA_VERSION)?.value?.jsonPrimitive?.content
            // If version exists but is outdated, show error before deleting
            if (savedVersion != null) {
                return AppDestination.DataVersionMismatch
            }
        }

        val nativeCode = settingsRepository.getById(Setting.Name.LANGUAGE)?.value?.jsonPrimitive?.content
        val native = nativeCode?.let { Language.fromCodeOrNull(it) }
        if (native != null) {
            nativeLanguage = native
            val dictionaryCode = settingsRepository.getById(Setting.Name.DICTIONARY)?.value?.jsonPrimitive?.content
            val dictionary = dictionaryCode?.let { Language.fromCodeOrNull(it) }
            if (dictionary != null) {
                dictionaryLanguage = dictionary
                return when {
                    !dataManager.hasDictionary(dictionary) -> AppDestination.DownloadDictionary
                    !dataManager.hasTranslation(dictionary, native) -> AppDestination.DownloadTranslation
                    else -> AppDestination.Search
                }
            }
            return AppDestination.SelectDictionary
        }
        return AppDestination.SelectLanguage
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
            composable<AppDestination.SelectLanguage> { backStackEntry ->
                val viewModel = viewModel(
                    viewModelStoreOwner = backStackEntry
                ) {
                    LanguageSelectionViewModel(dataManager)
                }

                LanguageSelectionScreen(
                    viewModel = viewModel,
                    onLanguageChosen = { lang ->
                        coroutineScope.launch {
                            settingsRepository.insert(
                                Setting(
                                    id = Setting.Name.LANGUAGE,
                                    value = Json.parseToJsonElement("\"${lang.code}\"")
                                )
                            )
                            nativeLanguage = lang
                            navController.navigate(AppDestination.SelectDictionary)
                        }
                    }
                )
            }
            composable<AppDestination.SelectDictionary> { backStackEntry ->
                val viewModel = viewModel(
                    viewModelStoreOwner = backStackEntry
                ) {
                    DictionarySelectionViewModel(dataManager)
                }

                DictionarySelectionScreen(
                    viewModel = viewModel,
                    onDictionaryChosen = { lang ->
                        coroutineScope.launch {
                            settingsRepository.insert(
                                Setting(
                                    id = Setting.Name.DICTIONARY,
                                    value = Json.parseToJsonElement("\"${lang.code}\"")
                                )
                            )
                            dictionaryLanguage = lang
                            navController.navigate(AppDestination.DownloadDictionary)
                        }
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
                                        popUpTo<AppDestination.SelectDictionary> { inclusive = false }
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
                                    popUpTo<AppDestination.SelectDictionary> { inclusive = false }
                                }
                            },
                            onCancel = {
                                navController.navigate(AppDestination.Error("Download cancelled")) {
                                    popUpTo<AppDestination.DownloadTranslation> { inclusive = true }
                                }
                            },
                            onError = { t ->
                                coroutineScope.launch {
                                    settingsRepository.deleteById(Setting.Name.LANGUAGE)
                                    settingsRepository.deleteById(Setting.Name.DICTIONARY)
                                    navController.navigate(AppDestination.Error(t.message ?: "Unknown error")) {
                                        popUpTo<AppDestination.DownloadTranslation> { inclusive = true }
                                    }
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
                    SearchViewModel(dictionaryRepository)
                }

                SearchScreen(
                    viewModel = viewModel,
                    onWordSelected = { item ->
                        val translationCodes = dictionaryRepository.installedTranslationTargets(item.language)
                            .map { it.code }
                        val destination = AppDestination.WordDetail(
                            dictionaryLanguageCode = item.language.code,
                            lemma = item.lemma,
                            translationLanguageCodes = translationCodes
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
            composable<AppDestination.Settings> {
                SettingsScreen(
                    viewModel = settingsViewModel,
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

                // Remove cached ViewModel if it has error or loading (will be recreated fresh)
                wordDetailViewModels[args]?.let { cached ->
                    if (cached.hasError()) {
                        wordDetailViewModels.remove(args)
                    }
                }

                // Try to retrieve cached ViewModel, otherwise create new one with proper lifecycle
                val viewModel = viewModel(
                    viewModelStoreOwner = backStackEntry
                ) {
                    wordDetailViewModels[args] ?: WordDetailViewModel(
                        dictionaryRepository,
                        dictionaryClient,
                        favoritesRepository,
                        ttsManager,
                        voiceFilterHelper,
                        args.dictionaryLanguage,
                        args.lemma,
                        args.targetSenseId,
                        args.translationLanguages
                    )
                }.also {
                    // Enforce max N cached ViewModels (remove oldest if at capacity)
                    if (wordDetailViewModels.size >= 10 && args !in wordDetailViewModels) {
                        wordDetailViewModels.remove(wordDetailViewModels.keys.first())
                    }
                    wordDetailViewModels[args] = it
                }


                // Reload favorites and voices when navigating to this screen
                LaunchedEffect(Unit) {
                    viewModel.reload()
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
                    },
                    onNavigateToSettings = {
                        if (!navController.popBackStack(AppDestination.Settings, inclusive = false))
                            navController.navigate(AppDestination.Settings)
                    },
                    onNavigateToWordDetail = { language, lemma ->
                        val translationCodes = dictionaryRepository.installedTranslationTargets(language)
                            .map { it.code }
                        val destination = AppDestination.WordDetail(
                            dictionaryLanguageCode = language.code,
                            lemma = lemma,
                            translationLanguageCodes = translationCodes
                        )
                        navController.navigate(destination)
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
