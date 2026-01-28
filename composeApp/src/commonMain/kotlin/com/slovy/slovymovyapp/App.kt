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
import com.slovy.slovymovyapp.data.remote.*
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonPrimitive

@Serializable
private sealed interface AppDestination {
    @Serializable
    data object Welcome : AppDestination

    @Serializable
    data object DownloadDictionary : AppDestination

    @Serializable
    data object DownloadTranslation : AppDestination

    @Serializable
    data object SetupLanguages : AppDestination

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
    var nativeLanguages by remember { mutableStateOf<List<Language>>(emptyList()) }
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
    val wordFetchManager = remember(dictionaryClient) {
        WordFetchManager(dictionaryClient)
    }
    val downloadCoordinator = remember { DownloadCoordinator() }
    val ttsManager = remember(androidContext) { TextToSpeechManager(androidContext) }
    val voiceFilterHelper = remember(settingsRepository) { VoiceFilterHelper(settingsRepository) }

    val navController = rememberNavController()
    var startDestination by remember { mutableStateOf<AppDestination?>(null) }
    val wordDetailViewModels = remember { linkedMapOf<AppDestination.WordDetail, WordDetailViewModel>() }
    // Shared ViewModel for Favorites screen to preserve state across navigation
    val favoritesViewModel = remember { FavoritesViewModel(favoritesRepository, dictionaryRepository) }
    val buildConfig = remember { appBuildConfig }
    val settingsViewModel =
        remember {
            SettingsViewModel(
                ttsManager,
                voiceFilterHelper,
                dataManager,
                downloadCoordinator,
                dictionaryRepository,
                buildConfig
            )
        }
    val coroutineScope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        onDispose { downloadCoordinator.close() }
    }

    suspend fun selectInitialDestination(): AppDestination {
        // Check if data version is current (before welcome, so existing users see mismatch)
        if (!dataManager.hasRequiredVersion()) {
            val savedVersion = settingsRepository.getById(Setting.Name.DATA_VERSION)?.value?.jsonPrimitive?.content
            // If version exists but is outdated, show error before deleting
            if (savedVersion != null) {
                return AppDestination.DataVersionMismatch
            }
        }

        val nativeJson = settingsRepository.getById(Setting.Name.LANGUAGE)?.value
        val nativeCodes = if (nativeJson is JsonArray) {
            nativeJson.mapNotNull { it.jsonPrimitive.content }
        } else {
            listOfNotNull(nativeJson?.jsonPrimitive?.content)
        }
        val natives = nativeCodes.mapNotNull { Language.fromCodeOrNull(it) }

        // Existing user: has language settings configured
        if (natives.isNotEmpty()) {
            nativeLanguages = natives
            val dictionaryCode = settingsRepository.getById(Setting.Name.DICTIONARY)?.value?.jsonPrimitive?.content
            val dictionary = dictionaryCode?.let { Language.fromCodeOrNull(it) }
            if (dictionary != null) {
                dictionaryLanguage = dictionary
                val missingTranslation = natives.find { !dataManager.hasTranslation(dictionary, it) }
                return when {
                    !dataManager.hasDictionary(dictionary) -> AppDestination.DownloadDictionary
                    missingTranslation != null -> AppDestination.DownloadTranslation
                    else -> AppDestination.Search
                }
            }
            // LANGUAGE set but DICTIONARY missing - return to setup
            return AppDestination.SetupLanguages
        }

        // New user: show welcome if not completed yet
        val welcomeCompleted = settingsRepository.getById(Setting.Name.WELCOME_COMPLETED)
            ?.value?.jsonPrimitive?.content == "true"
        if (!welcomeCompleted) {
            return AppDestination.Welcome
        }

        return AppDestination.SetupLanguages
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
            composable<AppDestination.Welcome> { backStackEntry ->
                val viewModel = viewModel(
                    viewModelStoreOwner = backStackEntry
                ) {
                    WelcomeViewModel()
                }

                WelcomeScreen(
                    viewModel = viewModel,
                    onGetStarted = {
                        coroutineScope.launch {
                            try {
                                settingsRepository.insert(
                                    Setting(
                                        id = Setting.Name.WELCOME_COMPLETED,
                                        value = Json.parseToJsonElement("true")
                                    )
                                )
                                navController.navigate(AppDestination.SetupLanguages) {
                                    popUpTo<AppDestination.Welcome> { inclusive = true }
                                }
                            } catch (e: Exception) {
                                viewModel.onError()
                            }
                        }
                    }
                )
            }
            composable<AppDestination.SetupLanguages> { backStackEntry ->
                val viewModel = viewModel(
                    viewModelStoreOwner = backStackEntry
                ) {
                    LanguageSetupViewModel(
                        dataManager,
                        initialLearningLanguage = dictionaryLanguage,
                        initialNativeLanguages = nativeLanguages.toSet()
                    )
                }

                LanguageSetupScreen(
                    viewModel = viewModel,
                    onNext = { learning, native ->
                        coroutineScope.launch {
                            settingsRepository.insert(
                                Setting(
                                    id = Setting.Name.LANGUAGE,
                                    value = Json.parseToJsonElement(Json.encodeToString(native.map { it.code }))
                                )
                            )
                            settingsRepository.insert(
                                Setting(
                                    id = Setting.Name.DICTIONARY,
                                    value = Json.parseToJsonElement("\"${learning.code}\"")
                                )
                            )
                            nativeLanguages = native
                            dictionaryLanguage = learning
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
                            downloadCoordinator = downloadCoordinator,
                            downloadKey = "dict_${dictLang.code}",
                            download = { onProgress, cancel ->
                                dataManager.ensureDictionary(dictLang, onProgress, cancel)
                            },
                            onSuccess = {
                                val missingTranslation =
                                    nativeLanguages.find { !dataManager.hasTranslation(dictLang, it) }
                                if (missingTranslation == null) {
                                    navController.navigate(AppDestination.Search) {
                                        popUpTo<AppDestination.SetupLanguages> { inclusive = false }
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
                val missingTranslations = if (dictLang != null) {
                    nativeLanguages.filter { !dataManager.hasTranslation(dictLang, it) }
                } else {
                    emptyList()
                }
                if (dictLang == null || missingTranslations.isEmpty()) {
                    LaunchedEffect(Unit) {
                        navController.navigate(AppDestination.Search) {
                            popUpTo<AppDestination.DownloadTranslation> { inclusive = true }
                        }
                    }
                } else {
                    val viewModel = viewModel(
                        viewModelStoreOwner = backStackEntry
                    ) {
                        val downloadKey = "setup_trans_${dictLang.code}_" +
                                missingTranslations.joinToString("_") { it.code }
                        DownloadViewModel(
                            downloadCoordinator = downloadCoordinator,
                            downloadKey = downloadKey,
                            //TODO if one of multiple translations fails we show error, but other langs downloaded.
                            download = { onProgress, cancel ->
                                missingTranslations.forEachIndexed { index, target ->
                                    dataManager.ensureTranslation(
                                        dictLang,
                                        target,
                                        onProgress = { p ->
                                            // Combine progress of multiple downloads
                                            val currentBase = (index.toFloat() / missingTranslations.size) * 100
                                            val currentProgress =
                                                if (p.percent >= 0) (p.percent.toFloat() / missingTranslations.size) else 0f
                                            val totalPercent = (currentBase + currentProgress).toInt()

                                            // Create a dummy DownloadProgress with the combined percentage
                                            onProgress(object : DownloadProgress(p.bytesDownloaded, p.totalBytes) {
                                                override val percent: Int = totalPercent
                                            })
                                        },
                                        cancelToken = cancel
                                    )
                                }
                            },
                            onSuccess = {
                                navController.navigate(AppDestination.Search) {
                                    popUpTo<AppDestination.SetupLanguages> { inclusive = false }
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
                        description = "Preparing translations"
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
                    onSuggestionSelected = { language, lemma ->
                        val translationCodes = dictionaryRepository.installedTranslationTargets(language)
                            .map { it.code }
                        val destination = AppDestination.WordDetail(
                            dictionaryLanguageCode = language.code,
                            lemma = lemma,
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
                    onNavigateToWordDetail = { language, lemma, senseId ->
                        val translationCodes = dictionaryRepository.installedTranslationTargets(language)
                            .map { it.code }
                        val destination = AppDestination.WordDetail(
                            dictionaryLanguageCode = language.code,
                            lemma = lemma,
                            targetSenseId = senseId,
                            translationLanguageCodes = translationCodes
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

                wordDetailViewModels[args]?.let { cached ->
                    if (cached.hasError()) {
                        cached.dispose()
                        wordDetailViewModels.remove(args)
                    } else {
                        // Move to end so "last" reflects most recently viewed.
                        wordDetailViewModels.remove(args)
                        wordDetailViewModels[args] = cached
                    }
                }

                val viewModel = wordDetailViewModels[args] ?: run {
                    if (wordDetailViewModels.size >= 10) {
                        val oldest = wordDetailViewModels.entries.firstOrNull()
                        if (oldest != null) {
                            oldest.value.dispose()
                            wordDetailViewModels.remove(oldest.key)
                        }
                    }
                    WordDetailViewModel(
                        dictionaryRepository,
                        wordFetchManager,
                        favoritesRepository,
                        ttsManager,
                        voiceFilterHelper,
                        args.dictionaryLanguage,
                        args.lemma,
                        args.targetSenseId,
                        args.translationLanguages
                    ).also { created ->
                        wordDetailViewModels[args] = created
                    }
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
                            dictionaryRepository.clearSenseCache()
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
