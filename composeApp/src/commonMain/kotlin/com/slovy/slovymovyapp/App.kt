package com.slovy.slovymovyapp

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.slovy.slovymovyapp.analytics.Analytics
import com.slovy.slovymovyapp.analytics.AnalyticsEvent
import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.export.AppDataExporter
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.compose.resources.stringResource
import slovymovyapp.composeapp.generated.resources.*

@Serializable
private sealed interface AppDestination {
    @Serializable
    data object Welcome : AppDestination

    @Serializable
    data object DownloadSetup : AppDestination

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
    data object DataVersionUpdateDownload : AppDestination
}

@Composable
fun App(
    settingsRepository: SettingsRepository,
    dataManager: DataDbManager,
    platform: PlatformDbSupport,
    appBuildConfig: AppBuildConfig,
    androidContext: Any? = null,
) {
    var pendingSearchQuery by remember { mutableStateOf<String?>(null) }
    var nativeLanguages by remember { mutableStateOf<List<Language>>(emptyList()) }
    var dictionaryLanguage by remember { mutableStateOf<Language?>(null) }
    var versionUpdateTargets by remember { mutableStateOf<List<DatabaseFileInfo>?>(null) }
    val favoritesRepository = remember(dataManager) {
        FavoritesRepository(DataDbManager.openAppDatabase(platform))
    }
    val localDbManager = remember(platform) { LocalDbManager(platform) }
    val dictionaryRepository =
        remember(dataManager, localDbManager, favoritesRepository, settingsRepository) {
            DictionaryRepository(dataManager, localDbManager, favoritesRepository, settingsRepository)
        }
    val dictionaryClient = remember(platform, dictionaryRepository, localDbManager, dataManager) {
        DictionaryClient(platform, dictionaryRepository, localDbManager, dataManager)
    }
    val wordFetchManager = remember(dictionaryClient) {
        WordFetchManager(dictionaryClient)
    }
    val downloadCoordinator = remember { DownloadCoordinator() }
    val ttsManager = remember(androidContext) { TextToSpeechManager(androidContext) }
    val appDataExporter = remember(androidContext) { AppDataExporter(androidContext) }
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
                dictionaryClient,
                appDataExporter,
                settingsRepository,
                buildConfig
            )
        }
    val coroutineScope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        onDispose { downloadCoordinator.close() }
    }

    // Keep nativeLanguages and dictionaryLanguage in sync with settings changes from SettingsScreen
    // Only sync after settings have successfully loaded at least once
    val settingsState = settingsViewModel.state
    LaunchedEffect(settingsState.translationLanguages, settingsState.settingsLoaded) {
        if (settingsState.settingsLoaded) {
            nativeLanguages = settingsState.translationLanguages.sortedBy { it.ordinal }
        }
    }
    LaunchedEffect(settingsState.activeDictionaryLanguage, settingsState.settingsLoaded) {
        if (settingsState.settingsLoaded) {
            dictionaryLanguage = settingsState.activeDictionaryLanguage
        }
    }

    suspend fun selectInitialDestination(): AppDestination {
        // Check if data version is current (before welcome, so existing users see mismatch)
        if (!dataManager.hasRequiredVersion()) {
            val savedVersion = settingsRepository.getById(Setting.Name.DATA_VERSION)?.value?.jsonPrimitive?.content
            // If version exists but is outdated, capture what's downloaded and go straight to re-download
            if (savedVersion != null) {
                val downloaded = dataManager.listDownloadedDatabases()
                if (downloaded.isNotEmpty()) {
                    versionUpdateTargets = downloaded
                    return AppDestination.DataVersionUpdateDownload
                }
                // Nothing to re-download; clear the stale version and fall through to normal routing
                dataManager.clearVersion()
            }
        }

        val nativeSetting = settingsRepository.getById(Setting.Name.LANGUAGE)
        val natives = settingsRepository.getTranslationLanguages().sortedBy { it.ordinal }

        // Existing user: has language settings configured (empty array is a valid selection).
        if (nativeSetting != null) {
            nativeLanguages = natives
            val dictionaryCode = settingsRepository.getById(Setting.Name.DICTIONARY)?.value?.jsonPrimitive?.content
            val dictionary = dictionaryCode?.let { Language.fromCodeOrNull(it) }
            if (dictionary != null) {
                dictionaryLanguage = dictionary
                val downloadableTargets = try {
                    dataManager.downloadableTranslationTargets(dictionary, natives)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    emptyList()
                }
                val needsDownload = !dataManager.hasDictionary(dictionary) ||
                        downloadableTargets.any { !dataManager.hasTranslation(dictionary, it) }
                return if (needsDownload) {
                    AppDestination.DownloadSetup
                } else {
                    AppDestination.Search
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
                                Analytics.logEvent(AnalyticsEvent.WELCOME_SCREEN_CLICK)
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
                            navController.navigate(AppDestination.DownloadSetup)
                        }
                    }
                )
            }
            composable<AppDestination.DownloadSetup> { backStackEntry ->
                val dictLang = dictionaryLanguage
                if (dictLang == null) {
                    LaunchedEffect(Unit) {
                        navController.navigate(AppDestination.SetupLanguages) {
                            popUpTo<AppDestination.SetupLanguages> { inclusive = true }
                        }
                    }
                } else {
                    var downloadDict = false
                    val downloadTranslations = mutableListOf<Language>()

                    val viewModel = viewModel(
                        viewModelStoreOwner = backStackEntry
                    ) {
                        DownloadViewModel(
                            downloadCoordinator = downloadCoordinator,
                            downloadKey = "setup_${dictLang.code}",
                            download = { onProgress, cancel ->
                                val totalItems = (if (downloadDict) 1 else 0) + downloadTranslations.size
                                val translationOffset = if (downloadDict) 1 else 0
                                if (downloadDict) {
                                    val fileName = "${dictLang.selfName} Dictionary"
                                    dataManager.ensureDictionary(dictLang, { p ->
                                        val current = if (p.percent >= 0) p.percent.toFloat() / totalItems else 0f
                                        onProgress(object : DownloadProgress(p.bytesDownloaded, p.totalBytes) {
                                            override val percent: Int = current.toInt()
                                            override val currentFile: String = fileName
                                        })
                                    }, cancel)
                                }
                                downloadTranslations.forEachIndexed { index, target ->
                                    val itemIndex = index + translationOffset
                                    val fileName = "${dictLang.selfName} \u2192 ${target.selfName}"
                                    dataManager.ensureTranslation(
                                        dictLang,
                                        target,
                                        onProgress = { p ->
                                            val base = (itemIndex.toFloat() / totalItems) * 100
                                            val current = if (p.percent >= 0) p.percent.toFloat() / totalItems else 0f
                                            onProgress(object : DownloadProgress(p.bytesDownloaded, p.totalBytes) {
                                                override val percent: Int = (base + current).toInt()
                                                override val currentFile: String = fileName
                                            })
                                        },
                                        cancelToken = cancel
                                    )
                                }
                            },
                            onSuccess = {
                                navController.navigate(AppDestination.Search) {
                                    popUpTo<AppDestination.DownloadSetup> { inclusive = true }
                                }
                            },
                            onCancel = {
                                navController.navigate(AppDestination.Search) {
                                    popUpTo<AppDestination.SetupLanguages> { inclusive = false }
                                }
                            },
                            onError = { _ ->
                                navController.navigate(AppDestination.Search) {
                                    popUpTo<AppDestination.DownloadSetup> { inclusive = true }
                                }
                            },
                            loadItems = {
                                downloadDict = !dataManager.hasDictionary(dictLang)
                                val available = dataManager.fetchAvailableLanguages()
                                val langInfo = available.find { it.language == dictLang }
                                val items = mutableListOf<DownloadItem>()
                                if (downloadDict) {
                                    langInfo?.dictionarySizeBytes?.let { size ->
                                        items.add(DownloadItem("${dictLang.selfName} Dictionary", size, dictLang.flag))
                                    }
                                }
                                val downloadableTargets = try {
                                    dataManager.downloadableTranslationTargets(dictLang, nativeLanguages)
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (_: Exception) {
                                    emptyList()
                                }
                                val missing = downloadableTargets.filter { !dataManager.hasTranslation(dictLang, it) }
                                downloadTranslations.clear()
                                downloadTranslations.addAll(missing)
                                for (target in downloadTranslations) {
                                    langInfo?.availableTranslations
                                        ?.find { it.targetLanguage == target }?.sizeBytes
                                        ?.let { size ->
                                            items.add(
                                                DownloadItem(
                                                    "${dictLang.selfName} \u2192 ${target.selfName}",
                                                    size,
                                                    target.flag
                                                )
                                            )
                                        }
                                }
                                items
                            }
                        )
                    }

                    DownloadScreen(
                        viewModel = viewModel,
                        description = stringResource(Res.string.download_title_downloading),
                        onLaterClick = {
                            Analytics.logEvent(AnalyticsEvent.DOWNLOAD_LATER_CLICK)
                            navController.navigate(AppDestination.Search) {
                                popUpTo<AppDestination.DownloadSetup> { inclusive = true }
                            }
                        }
                    )
                }
            }
            composable<AppDestination.Search> { backStackEntry ->

                val viewModel = viewModel(
                    viewModelStoreOwner = backStackEntry
                ) {
                    SearchViewModel(dictionaryRepository)
                }

                LaunchedEffect(pendingSearchQuery) {
                    pendingSearchQuery?.let { query ->
                        viewModel.updateQuery(query)
                        pendingSearchQuery = null
                    }
                }

                SearchScreen(
                    viewModel = viewModel,
                    onWordSelected = { item ->
                        val translationCodes = nativeLanguages.filter { it != item.language }
                            .map { it.code }
                        val destination = AppDestination.WordDetail(
                            dictionaryLanguageCode = item.language.code,
                            lemma = item.lemma,
                            translationLanguageCodes = translationCodes
                        )
                        navController.navigate(destination)
                    },
                    onSuggestionSelected = { language, lemma ->
                        val translationCodes = nativeLanguages.filter { it != language }
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
                // Reload favorites when navigating to this screen.
                LaunchedEffect(Unit) {
                    favoritesViewModel.loadFavorites()
                }

                FavoritesScreen(
                    viewModel = favoritesViewModel,
                    onNavigateToSearch = {
                        if (!navController.popBackStack(AppDestination.Search, inclusive = false))
                            navController.navigate(AppDestination.Search)
                    },
                    onSearchInDictionary = { query ->
                        pendingSearchQuery = query
                        if (!navController.popBackStack(AppDestination.Search, inclusive = false))
                            navController.navigate(AppDestination.Search)
                    },
                    onNavigateToWordDetail = { language, lemma, senseId ->
                        val translationCodes = nativeLanguages.filter { it != language }
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
                        dictionaryClient,
                        wordFetchManager,
                        favoritesRepository,
                        ttsManager,
                        voiceFilterHelper,
                        args.dictionaryLanguage,
                        args.lemma,
                        args.targetSenseId,
                        args.translationLanguages,
                        onFavoriteAdded = { favoritesViewModel.requestScrollToTop() }
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
                        val translationCodes = nativeLanguages.filter { it != language }
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
            composable<AppDestination.DataVersionUpdateDownload> { backStackEntry ->
                val targets = versionUpdateTargets ?: emptyList()
                val dictTargets = targets.filterIsInstance<DatabaseFileInfo.Dictionary>()
                val transTargets = targets.filterIsInstance<DatabaseFileInfo.Translation>()
                val totalItems = dictTargets.size + transTargets.size

                val viewModel = viewModel(viewModelStoreOwner = backStackEntry) {
                    DownloadViewModel(
                        downloadCoordinator = downloadCoordinator,
                        downloadKey = "version_update",
                        download = { onProgress, cancel ->
                            dictTargets.forEachIndexed { index, dict ->
                                val fileName = "${dict.language.selfName} Dictionary"
                                dataManager.ensureDictionary(dict.language, { p ->
                                    val base = (index.toFloat() / totalItems) * 100
                                    val current = if (p.percent >= 0) p.percent.toFloat() / totalItems else 0f
                                    onProgress(object : DownloadProgress(p.bytesDownloaded, p.totalBytes) {
                                        override val percent: Int = (base + current).toInt()
                                        override val currentFile: String = fileName
                                    })
                                }, cancel)
                            }
                            transTargets.forEachIndexed { index, trans ->
                                val itemIndex = dictTargets.size + index
                                val fileName = "${trans.sourceLanguage.selfName} → ${trans.targetLanguage.selfName}"
                                dataManager.ensureTranslation(trans.sourceLanguage, trans.targetLanguage, { p ->
                                    val base = (itemIndex.toFloat() / totalItems) * 100
                                    val current = if (p.percent >= 0) p.percent.toFloat() / totalItems else 0f
                                    onProgress(object : DownloadProgress(p.bytesDownloaded, p.totalBytes) {
                                        override val percent: Int = (base + current).toInt()
                                        override val currentFile: String = fileName
                                    })
                                }, cancel)
                            }
                        },
                        onSuccess = {
                            val dest = selectInitialDestination()
                            navController.navigate(dest) {
                                popUpTo<AppDestination.DataVersionUpdateDownload> { inclusive = true }
                            }
                        },
                        onCancel = {},
                        onError = {},
                        loadItems = {
                            dataManager.deleteAllDownloadedData()
                            localDbManager.deleteAll()
                            dictionaryRepository.clearSenseCache()

                            val items = mutableListOf<DownloadItem>()
                            for (dict in dictTargets) {
                                items.add(DownloadItem("${dict.language.selfName} Dictionary", dict.sizeBytes, dict.language.flag))
                            }
                            for (trans in transTargets) {
                                items.add(DownloadItem("${trans.sourceLanguage.selfName} → ${trans.targetLanguage.selfName}", trans.sizeBytes, trans.targetLanguage.flag))
                            }
                            items
                        }
                    )
                }

                DownloadScreen(
                    viewModel = viewModel,
                    description = stringResource(Res.string.download_title_downloading),
                    isMandatory = true
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
