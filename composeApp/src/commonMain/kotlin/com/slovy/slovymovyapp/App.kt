package com.slovy.slovymovyapp

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.slovy.slovymovyapp.analytics.Analytics.logEvent
import com.slovy.slovymovyapp.analytics.AnalyticsEvent
import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.export.AppDataExporter
import com.slovy.slovymovyapp.data.favorites.FavoritesRepository
import com.slovy.slovymovyapp.data.learning.fsrs.FsrsDefaults
import com.slovy.slovymovyapp.data.learning.fsrs.FsrsScheduler
import com.slovy.slovymovyapp.data.learning.intake.IntakeService
import com.slovy.slovymovyapp.data.learning.intake.LearningIntake
import com.slovy.slovymovyapp.data.learning.session.ExamplePicker
import com.slovy.slovymovyapp.data.learning.session.SessionService
import com.slovy.slovymovyapp.data.learning.stats.StatsService
import com.slovy.slovymovyapp.data.local.LocalDbManager
import com.slovy.slovymovyapp.data.remote.*
import com.slovy.slovymovyapp.data.settings.Setting
import com.slovy.slovymovyapp.data.settings.SettingsRepository
import com.slovy.slovymovyapp.speech.TextToSpeechManager
import com.slovy.slovymovyapp.speech.VoiceFilterHelper
import com.slovy.slovymovyapp.ui.*
import com.slovy.slovymovyapp.ui.study.StudySessionScreen
import com.slovy.slovymovyapp.ui.study.StudySessionViewModel
import com.slovy.slovymovyapp.ui.theme.AppTheme
import com.slovy.slovymovyapp.ui.word.WordDetailScreen
import com.slovy.slovymovyapp.ui.word.WordDetailViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.compose.resources.stringResource
import slovymovyapp.composeapp.generated.resources.Res
import slovymovyapp.composeapp.generated.resources.download_title_downloading
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

internal data class FavoritesReviewState(
    val dueCountByLanguage: Map<Language, Int>,
) {
    val hasDueCards: Boolean get() = dueCountByLanguage.values.any { it > 0 }
}

@OptIn(ExperimentalTime::class)
internal class FavoritesReviewCoordinator(
    private val clock: Clock = Clock.System,
) {
    private val refreshMutex = Mutex()
    private var lastIntakeAtByLanguage: Map<Language, Instant> = emptyMap()

    suspend fun refresh(
        favoritesRepository: FavoritesRepository,
        intakeService: LearningIntake,
        statsService: StatsService,
        invalidateIntakeCache: Boolean = false,
    ): FavoritesReviewState = refreshMutex.withLock {
        if (invalidateIntakeCache) {
            invalidateIntakeCache()
        }
        computeFavoritesReviewState(favoritesRepository, intakeService, statsService)
    }

    fun invalidateIntakeCache() {
        lastIntakeAtByLanguage = emptyMap()
    }

    private suspend fun computeFavoritesReviewState(
        favoritesRepository: FavoritesRepository,
        intakeService: LearningIntake,
        statsService: StatsService,
    ): FavoritesReviewState = withContext(Dispatchers.Default) {
        val languages = favoritesRepository.getAllGroupedByLangAndLemma()
            .map { it.language }
            .distinct()
        languages.forEach { language ->
            if (shouldRunIntake(language)) {
                intakeService.runIntake(language.code)
                markIntakeRun(language)
            }
        }
        FavoritesReviewState(
            dueCountByLanguage = languages.associateWith { language ->
                statsService.globalStats(language.code).dueToday
            },
        )
    }

    internal fun shouldRunIntake(language: Language): Boolean {
        val lastRunAt = lastIntakeAtByLanguage[language] ?: return true
        return clock.now() - lastRunAt >= INTAKE_CACHE_TTL
    }

    internal fun markIntakeRun(language: Language) {
        lastIntakeAtByLanguage += language to clock.now()
    }

    private companion object {
        val INTAKE_CACHE_TTL = 5.minutes
    }
}

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
    data object Stats : AppDestination

    @Serializable
    data class StudySession(
        val langCode: String,
    ) : AppDestination

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

@OptIn(ExperimentalTime::class)
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
    val appDatabase = remember(platform) {
        DataDbManager.openAppDatabase(platform)
    }
    val favoritesRepository = remember(appDatabase) {
        FavoritesRepository(appDatabase)
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
    val favoriteLemmaRecovery = remember(favoritesRepository, dataManager, dictionaryRepository, wordFetchManager) {
        FavoriteLemmaRecovery(favoritesRepository, dataManager, dictionaryRepository, wordFetchManager)
    }
    val fsrsConfig = remember { FsrsDefaults.config() }
    val fsrsScheduler = remember(fsrsConfig) {
        FsrsScheduler(
            retention = fsrsConfig.requestRetention,
            weights = fsrsConfig.weights,
            maximumInterval = fsrsConfig.maximumInterval,
            enableFuzz = fsrsConfig.enableFuzz,
        )
    }
    val intakeService = remember(appDatabase, dictionaryRepository, fsrsConfig) {
        IntakeService(
            learning = appDatabase.favoritesQueries,
            dictionary = dictionaryRepository,
            config = fsrsConfig,
            clock = Clock.System,
        )
    }
    val sessionService = remember(appDatabase, wordFetchManager, fsrsScheduler, fsrsConfig, dictionaryRepository) {
        SessionService(
            learning = appDatabase.favoritesQueries,
            wordFetchManager = wordFetchManager,
            scheduler = fsrsScheduler,
            examplePicker = ExamplePicker(appDatabase.favoritesQueries),
            config = fsrsConfig,
            clock = Clock.System,
            translationTargets = dictionaryRepository::defaultTranslationTargets,
        )
    }
    val statsService = remember(appDatabase, fsrsConfig) {
        StatsService(
            learning = appDatabase.favoritesQueries,
            clock = Clock.System,
        )
    }
    val downloadCoordinator = remember { DownloadCoordinator() }
    val ttsManager = remember(androidContext) { TextToSpeechManager(androidContext) }
    val appDataExporter = remember(androidContext) { AppDataExporter(androidContext) }
    val voiceFilterHelper = remember(settingsRepository) { VoiceFilterHelper(settingsRepository) }

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    var startDestination by remember { mutableStateOf<AppDestination?>(null) }
    val wordDetailViewModels = remember { linkedMapOf<AppDestination.WordDetail, WordDetailViewModel>() }
    val coroutineScope = rememberCoroutineScope()
    var hasFavoritesToReview by remember { mutableStateOf(false) }
    val favoritesReviewCoordinator = remember { FavoritesReviewCoordinator() }

    // Shared ViewModel for Favorites screen to preserve state across navigation
    val favoritesViewModel = remember {
        FavoritesViewModel(
            favoritesRepository = favoritesRepository,
            dictionaryRepository = dictionaryRepository,
            settingsRepository = settingsRepository,
        )
    }

    suspend fun refreshFavoritesReviewState(invalidateIntakeCache: Boolean = false) {
        val reviewState = favoritesReviewCoordinator.refresh(
            favoritesRepository = favoritesRepository,
            intakeService = intakeService,
            statsService = statsService,
            invalidateIntakeCache = invalidateIntakeCache,
        )
        favoritesViewModel.updateReviewDueCounts(reviewState.dueCountByLanguage)
        hasFavoritesToReview = reviewState.hasDueCards
    }
    val buildConfig = remember { appBuildConfig }
    val settingsViewModel =
        remember {
            SettingsViewModel(
                ttsManager,
                voiceFilterHelper,
                dataManager,
                downloadCoordinator,
                dictionaryClient,
                appDataExporter,
                settingsRepository,
                platform,
                buildConfig,
                onDictionaryDataChanged = { recoverFavorites ->
                    dictionaryRepository.clearSenseCache()
                    if (recoverFavorites) {
                        platform.runWithProcessKeepAlive {
                            favoriteLemmaRecovery.recoverAllInstalledFavorites()
                        }
                    }
                    favoritesViewModel.dropCachedFavoriteDetails()
                },
            )
        }
    DisposableEffect(Unit) {
        onDispose { downloadCoordinator.close() }
    }

    LaunchedEffect(navBackStackEntry) {
        refreshFavoritesReviewState()
    }

    // Keep nativeLanguages and dictionaryLanguage in sync with settings changes from SettingsScreen
    // Only sync after settings have successfully loaded at least once
    val settingsState = settingsViewModel.state
    val learningLanguagesForStats = settingsState.learningLanguages
        .map { it.language }
        .ifEmpty { dictionaryLanguage?.let { listOf(it) }.orEmpty() }
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
            // If version exists but is outdated, show error before deleting
            if (savedVersion != null) {
                return AppDestination.DataVersionMismatch
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
                } catch (_: Exception) {
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
                                logEvent(AnalyticsEvent.WELCOME_SCREEN_CLICK)
                                navController.navigate(AppDestination.SetupLanguages) {
                                    popUpTo<AppDestination.Welcome> { inclusive = true }
                                }
                            } catch (_: Exception) {
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
                            finalize = {
                                dictionaryRepository.clearSenseCache()
                                platform.runWithProcessKeepAlive {
                                    withContext(Dispatchers.Default) {
                                        favoriteLemmaRecovery.recoverAllInstalledFavorites()
                                    }
                                }
                                favoritesViewModel.dropCachedFavoriteDetails()
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
                            platform = platform,
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
                            logEvent(AnalyticsEvent.DOWNLOAD_LATER_CLICK)
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
                    SearchViewModel(dictionaryRepository, settingsRepository)
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
                    onNavigateToStats = {
                        if (!navController.popBackStack(AppDestination.Stats, inclusive = false))
                            navController.navigate(AppDestination.Stats)
                    },
                    onNavigateToSettings = {
                        if (!navController.popBackStack(AppDestination.Settings, inclusive = false))
                            navController.navigate(AppDestination.Settings)
                    },
                    hasFavoritesToReview = hasFavoritesToReview,
                )
            }
            composable<AppDestination.Favorites> {
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
                    },
                    onNavigateToStats = {
                        if (!navController.popBackStack(AppDestination.Stats, inclusive = false))
                            navController.navigate(AppDestination.Stats)
                    },
                    onStartStudy = { language ->
                        logEvent(AnalyticsEvent.STUDY_START_SESSION)
                        navController.navigate(AppDestination.StudySession(language.code))
                    },
                    onFavoritesChanged = {
                        coroutineScope.launch { refreshFavoritesReviewState(invalidateIntakeCache = true) }
                    },
                )
            }
            composable<AppDestination.Stats> { backStackEntry ->
                val viewModel = viewModel(
                    viewModelStoreOwner = backStackEntry
                ) {
                    StatsViewModel(learningLanguagesForStats, statsService, Clock.System)
                }

                StatsScreen(
                    viewModel = viewModel,
                    learningLanguages = learningLanguagesForStats,
                    wordDetailLabel = wordDetailViewModels.keys.lastOrNull()?.lemma,
                    onNavigateToSearch = {
                        if (!navController.popBackStack(AppDestination.Search, inclusive = false))
                            navController.navigate(AppDestination.Search)
                    },
                    onNavigateToFavorites = {
                        if (!navController.popBackStack(AppDestination.Favorites, inclusive = false))
                            navController.navigate(AppDestination.Favorites)
                    },
                    onNavigateToWordDetail = {
                        wordDetailViewModels.keys.lastOrNull()?.let { destination ->
                            navController.navigate(destination)
                        }
                    },
                    onNavigateToSettings = {
                        if (!navController.popBackStack(AppDestination.Settings, inclusive = false))
                            navController.navigate(AppDestination.Settings)
                    },
                )
            }
            composable<AppDestination.StudySession> { backStackEntry ->
                val args = backStackEntry.toRoute<AppDestination.StudySession>()
                val viewModel = viewModel(
                    viewModelStoreOwner = backStackEntry
                ) {
                    StudySessionViewModel(
                        langCode = args.langCode,
                        intakeService = intakeService,
                        sessionService = sessionService,
                        statsService = statsService,
                        clock = Clock.System,
                        ttsManager = ttsManager,
                        voiceFilterHelper = voiceFilterHelper,
                    )
                }
                StudySessionScreen(
                    viewModel = viewModel,
                    onCancel = {
                        logEvent(AnalyticsEvent.STUDY_CANCEL_SESSION)
                        if (!navController.popBackStack()) {
                            navController.navigate(AppDestination.Favorites)
                        }
                    },
                    onEnd = {
                        logEvent(AnalyticsEvent.STUDY_END_SESSION)
                        if (!navController.popBackStack()) {
                            navController.navigate(AppDestination.Favorites)
                        }
                    },
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
                    onNavigateToStats = {
                        if (!navController.popBackStack(AppDestination.Stats, inclusive = false))
                            navController.navigate(AppDestination.Stats)
                    },
                    wordDetailLabel = wordDetailViewModels.keys.lastOrNull()?.lemma,
                    onNavigateToWordDetail = {
                        wordDetailViewModels.keys.lastOrNull()?.let { destination ->
                            navController.navigate(destination)
                        }
                    },
                    hasFavoritesToReview = hasFavoritesToReview,
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
                        onFavoriteChanged = { added ->
                            if (added) {
                                favoritesViewModel.requestScrollToTop()
                            }
                            coroutineScope.launch { refreshFavoritesReviewState(invalidateIntakeCache = true) }
                        },
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
                    onNavigateToStats = {
                        if (!navController.popBackStack(AppDestination.Stats, inclusive = false))
                            navController.navigate(AppDestination.Stats)
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
                    },
                    hasFavoritesToReview = hasFavoritesToReview,
                )
            }
            composable<AppDestination.DataVersionMismatch> {
                val coroutineScope = rememberCoroutineScope()
                DataVersionMismatchScreen(
                    onRedownload = {
                        coroutineScope.launch {
                            dataManager.deleteAllDownloadedData()
                            localDbManager.deleteAll()
                            dictionaryRepository.clearSenseCache()
                            favoritesViewModel.dropCachedFavoriteDetails()
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
