package com.slovy.slovymovyapp

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.intl.Locale
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.slovy.slovymovyapp.analytics.*
import com.slovy.slovymovyapp.analytics.Analytics.logEvent
import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.export.AppDataExporter
import com.slovy.slovymovyapp.data.favorites.FavoritesRepository
import com.slovy.slovymovyapp.data.learning.fsrs.FsrsDefaults
import com.slovy.slovymovyapp.data.learning.fsrs.FsrsScheduler
import com.slovy.slovymovyapp.data.learning.intake.IntakeService
import com.slovy.slovymovyapp.data.learning.intake.LearningIntake
import com.slovy.slovymovyapp.data.learning.session.ExamplePicker
import com.slovy.slovymovyapp.data.learning.session.SessionService
import com.slovy.slovymovyapp.data.learning.stats.ReviewQueueStats
import com.slovy.slovymovyapp.data.learning.stats.StatsService
import com.slovy.slovymovyapp.data.local.LocalDbManager
import com.slovy.slovymovyapp.data.remote.*
import com.slovy.slovymovyapp.data.settings.Setting
import com.slovy.slovymovyapp.data.settings.SettingsRepository
import com.slovy.slovymovyapp.logging.AppLogger
import com.slovy.slovymovyapp.speech.TextToSpeechManager
import com.slovy.slovymovyapp.speech.VoiceFilterHelper
import com.slovy.slovymovyapp.ui.*
import com.slovy.slovymovyapp.ui.study.StudySessionScreen
import com.slovy.slovymovyapp.ui.study.StudySessionViewModel
import com.slovy.slovymovyapp.ui.theme.AppTheme
import com.slovy.slovymovyapp.ui.word.WordDetailScreen
import com.slovy.slovymovyapp.ui.word.WordDetailViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.compose.resources.stringResource
import slovymovyapp.composeapp.generated.resources.Res
import slovymovyapp.composeapp.generated.resources.download_title_downloading
import kotlin.concurrent.Volatile
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

internal data class FavoritesReviewState(
    val reviewByLanguage: Map<Language, FavoriteLanguageReviewState>,
) {
    val hasDueCards: Boolean get() = reviewByLanguage.values.any { it.dueCount > 0 }
}

internal data class FavoriteLanguageReviewState(
    val dueCount: Int,
    val activeCardCount: Int,
    val delayedDueLemmaCount: Int,
    val delayedDueCardCount: Int,
    val pendingFavoriteLemmaCount: Int,
    val canStudyPendingFavoritesNow: Boolean,
    val nextReviewAtEpochMs: Long?,
)

@OptIn(ExperimentalTime::class)
internal class FavoritesReviewCoordinator(
    private val clock: Clock = Clock.System,
) {
    private val refreshMutex = Mutex()
    private var lastIntakeAtByLanguage: Map<Language, Instant> = emptyMap()

    // Intake reads the local dictionary DB. The data-version-mismatch flow wipes that DB
    // (LocalDbManager.deleteAll closes the driver), so we must keep the coordinator disabled
    // until the routing layer has confirmed we're past that check.
    @Volatile
    var enabled: Boolean = false

    suspend fun refresh(
        favoritesRepository: FavoritesRepository,
        intakeService: LearningIntake,
        statsService: StatsService,
    ): FavoritesReviewState = refreshMutex.withLock {
        if (!enabled) return@withLock FavoritesReviewState(emptyMap())
        computeFavoritesReviewState(favoritesRepository, intakeService, statsService)
    }

    /**
     * Re-reads review queue state without running intake. Cheap enough to call right after a
     * favorite toggle: remove/add already mutate `card.suspended` synchronously, so
     * due and delayed-card metadata are accurate. Skipping intake here avoids serializing the dictionary-DB
     * driver against the screen's own queries on iOS.
     */
    suspend fun refreshDueCountsOnly(
        favoritesRepository: FavoritesRepository,
        intakeService: LearningIntake,
        statsService: StatsService,
    ): FavoritesReviewState = refreshMutex.withLock {
        if (!enabled) return@withLock FavoritesReviewState(emptyMap())
        PerformanceMonitoring.startTrace("favorites_review_due_counts").useWithResult {
            withContext(Dispatchers.Default) {
                val languages = favoritesRepository.getAllGroupedByLangAndLemma()
                    .map { it.language }
                    .distinct()
                putMetric("languages", languages.size.toLong())
                val reviewByLanguage = languages.associateWith { language ->
                    statsService.reviewQueueStats(language.code)
                        .toFavoriteLanguageReviewState(language, intakeService)
                }
                putReviewStateMetrics(reviewByLanguage)
                FavoritesReviewState(reviewByLanguage = reviewByLanguage)
            }
        }
    }

    fun invalidateIntakeCacheForLanguage(language: Language) {
        lastIntakeAtByLanguage = lastIntakeAtByLanguage - language
    }

    fun invalidateAllIntakeCache() {
        lastIntakeAtByLanguage = emptyMap()
    }

    private suspend fun computeFavoritesReviewState(
        favoritesRepository: FavoritesRepository,
        intakeService: LearningIntake,
        statsService: StatsService,
    ): FavoritesReviewState = PerformanceMonitoring.startTrace("favorites_review_compute_state").useWithResult {
        withContext(Dispatchers.Default) {
            val favorites = favoritesRepository.getAllGroupedByLangAndLemma()
            val languages = favorites
                .map { it.language }
                .distinct()
            putMetric("favorites", favorites.size.toLong())
            putMetric("languages", languages.size.toLong())
            var intakeRuns = 0L
            languages.forEach { language ->
                if (shouldRunIntake(language)) {
                    intakeService.runIntake(language.code)
                    markIntakeRun(language)
                    intakeRuns += 1
                }
            }
            putMetric("intake_runs", intakeRuns)
            val reviewByLanguage = languages.associateWith { language ->
                statsService.reviewQueueStats(language.code)
                    .toFavoriteLanguageReviewState(language, intakeService)
            }
            putReviewStateMetrics(reviewByLanguage)
            FavoritesReviewState(reviewByLanguage = reviewByLanguage)
        }
    }

    private fun ReviewQueueStats.toFavoriteLanguageReviewState(
        language: Language,
        intakeService: LearningIntake,
    ) =
        FavoriteLanguageReviewState(
            dueCount = dueToday,
            activeCardCount = activeCardCount,
            delayedDueLemmaCount = delayedDueLemmaCount,
            delayedDueCardCount = delayedDueCardCount,
            pendingFavoriteLemmaCount = pendingFavoriteLemmaCount,
            canStudyPendingFavoritesNow = intakeService.canContinueWithPendingFavoritesNow(language.code),
            nextReviewAtEpochMs = nextReviewAtEpochMs,
        )

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

private fun FavoritesReviewState.toFavoriteLanguageReviewUiState(): Map<Language, FavoriteLanguageReviewUiState> =
    reviewByLanguage.mapValues { (_, reviewState) ->
        FavoriteLanguageReviewUiState(
            dueCount = reviewState.dueCount,
            activeCardCount = reviewState.activeCardCount,
            delayedDueLemmaCount = reviewState.delayedDueLemmaCount,
            delayedDueCardCount = reviewState.delayedDueCardCount,
            pendingFavoriteLemmaCount = reviewState.pendingFavoriteLemmaCount,
            canStudyPendingFavoritesNow = reviewState.canStudyPendingFavoritesNow,
            nextReviewAtEpochMs = reviewState.nextReviewAtEpochMs,
        )
    }

private fun PerformanceTrace.putReviewStateMetrics(reviewByLanguage: Map<Language, FavoriteLanguageReviewState>) {
    putMetric("due_cards", reviewByLanguage.values.sumOf { it.dueCount }.toLong())
    putMetric("active_cards", reviewByLanguage.values.sumOf { it.activeCardCount }.toLong())
    putMetric("delayed_due_lemmas", reviewByLanguage.values.sumOf { it.delayedDueLemmaCount }.toLong())
    putMetric("delayed_due_cards", reviewByLanguage.values.sumOf { it.delayedDueCardCount }.toLong())
    putMetric("pending_favorite_lemmas", reviewByLanguage.values.sumOf { it.pendingFavoriteLemmaCount }.toLong())
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
    data object Developer : AppDestination

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
    val favoriteRecoveryController = remember(favoriteLemmaRecovery, platform) {
        FavoriteRecoveryController(favoriteLemmaRecovery, platform)
    }
    DisposableEffect(favoriteRecoveryController) {
        onDispose { favoriteRecoveryController.close() }
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
    val appCoroutineScope = rememberCoroutineScope()
    var hasFavoritesToReview by remember { mutableStateOf(false) }
    val favoritesReviewCoordinator = remember { FavoritesReviewCoordinator() }

    // Shared ViewModel for Favorites screen to preserve state across navigation
    val favoritesViewModel = remember {
        FavoritesViewModel(
            favoritesRepository = favoritesRepository,
            dictionaryRepository = dictionaryRepository,
            settingsRepository = settingsRepository,
            clock = Clock.System,
        ).also { it.start() }
    }

    suspend fun refreshFavoritesReviewState() {
        val reviewState = favoritesReviewCoordinator.refresh(
            favoritesRepository = favoritesRepository,
            intakeService = intakeService,
            statsService = statsService,
        )
        favoritesViewModel.updateReviewState(reviewState.toFavoriteLanguageReviewUiState())
        hasFavoritesToReview = reviewState.hasDueCards
    }

    suspend fun refreshFavoritesDueCountsOnly() {
        val reviewState = favoritesReviewCoordinator.refreshDueCountsOnly(
            favoritesRepository = favoritesRepository,
            intakeService = intakeService,
            statsService = statsService,
        )
        favoritesViewModel.updateReviewState(reviewState.toFavoriteLanguageReviewUiState())
        hasFavoritesToReview = reviewState.hasDueCards
    }

    val buildConfig = remember { appBuildConfig }
    LaunchedEffect(buildConfig) {
        AppLogger.info(
            tag = "App",
            message = "App started ${buildConfig.applicationId} ${buildConfig.versionName} (${buildConfig.versionCode}), debug=${buildConfig.isDebug}",
            throwable = null,
        )
    }
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
                    favoritesReviewCoordinator.invalidateAllIntakeCache()
                    dictionaryRepository.clearSenseCache()
                    if (recoverFavorites) {
                        favoriteRecoveryController.ensureStarted()
                    }
                    favoritesViewModel.dropCachedFavoriteDetails()
                },
            )
        }
    LaunchedEffect(settingsViewModel) {
        settingsViewModel.migrateLegacyDefaultVoiceSelectionsAfterAppStart()
    }
    DisposableEffect(Unit) {
        onDispose { downloadCoordinator.close() }
    }

    // Update the badge/due counts immediately from current SR state so the bottom-nav dot
    // and Study Due card are correct as soon as the user lands on a screen.
    LaunchedEffect(navBackStackEntry) {
        refreshFavoritesDueCountsOnly()
    }

    // Intake reads the dictionary DB and on iOS serializes against the visible screen's
    // queries (e.g. FavoritesScreen's computeFavoritesState, which itself debounces ~200ms).
    // Defer it so the destination gets to paint first; after the delay it runs only for
    // languages whose cache is stale.
    LaunchedEffect(navBackStackEntry) {
        delay(500.milliseconds)
        refreshFavoritesReviewState()
    }

    // Keep nativeLanguages and dictionaryLanguage in sync with settings changes from SettingsScreen
    // Only sync after settings have successfully loaded at least once
    val settingsState = settingsViewModel.state
    val learningLanguagesForStats = settingsState.learningLanguages
        .map { it.language }
        .ifEmpty { dictionaryLanguage?.let { listOf(it) }.orEmpty() }
    LaunchedEffect(buildConfig.isDebug, settingsState.developerModeEnabled) {
        AppLogger.debugLoggingEnabled = buildConfig.isDebug || settingsState.developerModeEnabled
    }
    LaunchedEffect(settingsState.translationLanguages, settingsState.settingsLoaded) {
        if (settingsState.settingsLoaded) {
            nativeLanguages = settingsState.translationLanguages.sortedBy { it.ordinal }
        }
    }
    LaunchedEffect(settingsState.activeDictionaryLanguage, settingsState.settingsLoaded) {
        if (settingsState.settingsLoaded) {
            dictionaryLanguage = settingsState.activeDictionaryLanguage
            Analytics.setUserProperty("learning_lang", dictionaryLanguage?.code)
        }
    }

    // GA4 user properties: stable per-user dimensions usable as report breakdowns
    // across every event without adding params to each call site.
    val uiLang = Locale.current.language
    LaunchedEffect(Unit) {
        Analytics.setUserProperty("ui_lang", uiLang)
        Analytics.setUserProperty("data_version", DataDbManager.VERSION)
    }
    suspend fun selectInitialDestination(): AppDestination {
        // Sweep any zero-byte / partial downloaded DBs left behind by past interrupted downloads
        // so the routing below sees an accurate `hasDictionary` / `hasTranslation` picture.
        dataManager.cleanupCorruptDownloadedDbs()

        // Check if data version is current (before welcome, so existing users see mismatch).
        // The coordinator defaults to disabled, so returning DataVersionMismatch here keeps
        // intake off — the redownload flow calls localDbManager.deleteAll(), and any concurrent
        // intake query against the cached driver would crash.
        if (!dataManager.hasRequiredVersion()) {
            val savedVersion = settingsRepository.getById(Setting.Name.DATA_VERSION)?.value?.jsonPrimitive?.content
            if (savedVersion != null) {
                return AppDestination.DataVersionMismatch
            }
        }

        favoritesReviewCoordinator.enabled = true

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
        Box(modifier = Modifier.fillMaxSize()) {
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
                            appCoroutineScope.launch {
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
                            dictionaryClient,
                            initialLearningLanguage = dictionaryLanguage,
                            initialNativeLanguages = nativeLanguages.toSet()
                        )
                    }

                    LanguageSetupScreen(
                        viewModel = viewModel,
                        onNext = { learning, native ->
                            appCoroutineScope.launch {
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
                                analyticsParams = mapOf(
                                    "kind" to "setup",
                                    "lang" to dictLang.code,
                                ),
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
                                                val current =
                                                    if (p.percent >= 0) p.percent.toFloat() / totalItems else 0f
                                                onProgress(object : DownloadProgress(p.bytesDownloaded, p.totalBytes) {
                                                    override val percent: Int = (base + current).toInt()
                                                    override val currentFile: String = fileName
                                                })
                                            },
                                            cancelToken = cancel
                                        )
                                    }
                                },
                                finalize = { onRecoveryProgress ->
                                    favoritesReviewCoordinator.invalidateAllIntakeCache()
                                    dictionaryRepository.clearSenseCache()
                                    val recoveryJob = favoriteRecoveryController.ensureStarted()
                                    coroutineScope {
                                        val observerJob = launch {
                                            favoriteRecoveryController.progress.collect { progress ->
                                                if (progress != null) {
                                                    onRecoveryProgress(progress)
                                                }
                                            }
                                        }
                                        try {
                                            recoveryJob.join()
                                        } finally {
                                            observerJob.cancel()
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
                                            items.add(
                                                DownloadItem(
                                                    "${dictLang.selfName} Dictionary",
                                                    size,
                                                    dictLang.flag
                                                )
                                            )
                                        }
                                    }
                                    val downloadableTargets = try {
                                        dataManager.downloadableTranslationTargets(dictLang, nativeLanguages)
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (_: Exception) {
                                        emptyList()
                                    }
                                    val missing =
                                        downloadableTargets.filter { !dataManager.hasTranslation(dictLang, it) }
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
                        onNavigateToSettings = {
                            if (!navController.popBackStack(AppDestination.Settings, inclusive = false))
                                navController.navigate(AppDestination.Settings)
                        },
                        onNavigateToStats = {
                            if (!navController.popBackStack(AppDestination.Stats, inclusive = false))
                                navController.navigate(AppDestination.Stats)
                        },
                        onStartStudy = { language ->
                            logEvent(AnalyticsEvent.STUDY_START_SESSION, mapOf("lang" to language.code))
                            navController.navigate(AppDestination.StudySession(language.code))
                        },
                        onContinueStudyingNow = { language, action ->
                            appCoroutineScope.launch {
                                val shouldStartStudy = when (action) {
                                    FavoritesStudyDoneAction.REVIEW_MORE -> {
                                        sessionService.continueDelayedCardsNow(language.code)
                                        true
                                    }

                                    FavoritesStudyDoneAction.STUDY_NEW ->
                                        intakeService.continueWithPendingFavoritesNow(language.code).cardsCreated > 0
                                }
                                refreshFavoritesDueCountsOnly()
                                if (shouldStartStudy) {
                                    logEvent(AnalyticsEvent.STUDY_START_SESSION, mapOf("lang" to language.code))
                                    navController.navigate(AppDestination.StudySession(language.code))
                                }
                            }
                        },
                        onFavoritesChanged = { language ->
                            favoritesReviewCoordinator.invalidateIntakeCacheForLanguage(language)
                            // Toggle stays on Favorites, so navBackStackEntry doesn't change and the
                            // nav effects don't refire. remove() and undo's add() already update
                            // card.suspended in-DB, so a stats-only refresh shows correct counts
                            // without paying for intake on the dictionary-DB driver.
                            appCoroutineScope.launch { refreshFavoritesDueCountsOnly() }
                        },
                        onRefreshReviewState = {
                            appCoroutineScope.launch { refreshFavoritesDueCountsOnly() }
                        },
                    )
                }
                composable<AppDestination.Stats> { backStackEntry ->
                    val viewModel = viewModel(
                        viewModelStoreOwner = backStackEntry
                    ) {
                        StatsViewModel(learningLanguagesForStats, statsService, settingsRepository, Clock.System)
                    }

                    StatsScreen(
                        viewModel = viewModel,
                        learningLanguages = learningLanguagesForStats,
                        onNavigateToSearch = {
                            if (!navController.popBackStack(AppDestination.Search, inclusive = false))
                                navController.navigate(AppDestination.Search)
                        },
                        onNavigateToFavorites = {
                            if (!navController.popBackStack(AppDestination.Favorites, inclusive = false))
                                navController.navigate(AppDestination.Favorites)
                        },
                        onNavigateToSettings = {
                            if (!navController.popBackStack(AppDestination.Settings, inclusive = false))
                                navController.navigate(AppDestination.Settings)
                        },
                        hasFavoritesToReview = hasFavoritesToReview,
                    )
                }
                composable<AppDestination.StudySession> { backStackEntry ->
                    val args = backStackEntry.toRoute<AppDestination.StudySession>()
                    val viewModel = viewModel(
                        viewModelStoreOwner = backStackEntry
                    ) {
                        StudySessionViewModel(
                            langCode = args.langCode,
                            favoritesRepository = favoritesRepository,
                            intakeService = intakeService,
                            sessionService = sessionService,
                            statsService = statsService,
                            clock = Clock.System,
                            ttsManager = ttsManager,
                            voiceFilterHelper = voiceFilterHelper,
                            onReviewSubmitted = {
                                Language.fromCodeOrNull(args.langCode)?.let { lang ->
                                    favoritesReviewCoordinator.invalidateIntakeCacheForLanguage(lang)
                                }
                                // A submitted review can push the card past today; refresh the
                                // bottom-nav dot from stats. No intake needed — review changes
                                // hit `card.due_at` directly.
                                appCoroutineScope.launch { refreshFavoritesDueCountsOnly() }
                            },
                            onFavoriteChanged = { language ->
                                favoritesReviewCoordinator.invalidateIntakeCacheForLanguage(language)
                                appCoroutineScope.launch { refreshFavoritesDueCountsOnly() }
                            },
                        )
                    }
                    StudySessionScreen(
                        viewModel = viewModel,
                        onCancel = {
                            logEvent(AnalyticsEvent.STUDY_CANCEL_SESSION, viewModel.buildSessionEndParams("cancel"))
                            if (!navController.popBackStack()) {
                                navController.navigate(AppDestination.Favorites)
                            }
                        },
                        onEnd = {
                            logEvent(AnalyticsEvent.STUDY_END_SESSION, viewModel.buildSessionEndParams("finished"))
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
                        onNavigateToDeveloper = {
                            navController.navigate(AppDestination.Developer)
                        },
                        hasFavoritesToReview = hasFavoritesToReview,
                    )
                }
                composable<AppDestination.Developer> { backStackEntry ->
                    val viewModel = viewModel(viewModelStoreOwner = backStackEntry) {
                        DeveloperViewModel(
                            favoritesRepository = favoritesRepository,
                            intake = intakeService,
                            learningLanguagesProvider = {
                                dataManager.listDownloadedDatabases()
                                    .filterIsInstance<DatabaseFileInfo.Dictionary>()
                                    .map { it.language }
                            },
                        )
                    }
                    DeveloperScreen(
                        viewModel = viewModel,
                        isDebugBuild = buildConfig.isDebug,
                        onBack = { navController.popBackStack() },
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
                                favoritesReviewCoordinator.invalidateIntakeCacheForLanguage(args.dictionaryLanguage)
                                // Remove flips card.suspended in-DB so the dot updates immediately.
                                // Add creates a pending favorite with no SR cards yet — the dot
                                // catches up when the user navigates back and the delayed intake
                                // effect runs intake for this language.
                                appCoroutineScope.launch { refreshFavoritesDueCountsOnly() }
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
                                try {
                                    dataManager.deleteAllDownloadedData()
                                    localDbManager.deleteAll()
                                    dictionaryRepository.clearSenseCache()
                                    favoritesReviewCoordinator.invalidateAllIntakeCache()
                                    favoritesViewModel.dropCachedFavoriteDetails()
                                    val target = selectInitialDestination()
                                    navController.navigate(target) {
                                        popUpTo<AppDestination.DataVersionMismatch> { inclusive = true }
                                    }
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Throwable) {
                                    navController.navigate(
                                        AppDestination.Error(
                                            e.message ?: "Failed to refresh dictionaries"
                                        )
                                    )
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
            DeveloperTerminalOverlay(
                enabled = settingsState.developerModeEnabled,
                onShiftLearningTime = { duration ->
                    withContext(Dispatchers.IO) {
                        favoritesRepository.shiftLearningTimestampsBack(duration)
                    }
                    refreshFavoritesDueCountsOnly()
                },
            )
        }
    }
}
