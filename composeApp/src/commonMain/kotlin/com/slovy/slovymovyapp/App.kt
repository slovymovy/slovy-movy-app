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
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.svg.SvgDecoder
import com.slovy.slovymovyapp.analytics.*
import com.slovy.slovymovyapp.analytics.Analytics.logEvent
import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.learning.review.FavoritesReviewCoordinator
import com.slovy.slovymovyapp.data.learning.review.FavoritesReviewState
import com.slovy.slovymovyapp.data.remote.*
import com.slovy.slovymovyapp.data.settings.Setting
import com.slovy.slovymovyapp.data.settings.SettingsRepository
import com.slovy.slovymovyapp.logging.AppLogger
import com.slovy.slovymovyapp.ui.*
import com.slovy.slovymovyapp.ui.favorites.*
import com.slovy.slovymovyapp.ui.settings.*
import com.slovy.slovymovyapp.ui.search.*
import com.slovy.slovymovyapp.ui.developer.*
import com.slovy.slovymovyapp.ui.stats.*
import com.slovy.slovymovyapp.ui.listdetail.*
import com.slovy.slovymovyapp.ui.languagesetup.*
import com.slovy.slovymovyapp.ui.download.*
import com.slovy.slovymovyapp.ui.study.StudySessionScreen
import com.slovy.slovymovyapp.ui.study.StudySessionViewModel
import com.slovy.slovymovyapp.ui.reader.TextReaderScreen
import com.slovy.slovymovyapp.ui.reader.TextReaderViewModel
import com.slovy.slovymovyapp.ui.theme.AppTheme
import com.slovy.slovymovyapp.ui.word.WordDetailScreen
import com.slovy.slovymovyapp.ui.word.WordDetailViewModel
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.compose.resources.stringResource
import slovymovyapp.composeapp.generated.resources.Res
import slovymovyapp.composeapp.generated.resources.download_item_dictionary_name
import slovymovyapp.composeapp.generated.resources.download_title_downloading
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime

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

@OptIn(ExperimentalTime::class)
@Composable
fun App(
    settingsRepository: SettingsRepository,
    dataManager: DataDbManager,
    platform: PlatformDbSupport,
    appBuildConfig: AppBuildConfig,
    androidContext: Any? = null,
) {
    // Word-list icons arrive from the server as SVG text; Coil needs the SVG decoder
    // registered to render them (see WordListIcon).
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .components { add(SvgDecoder.Factory()) }
            .build()
    }
    var pendingSearchQuery by remember { mutableStateOf<String?>(null) }
    var nativeLanguages by remember { mutableStateOf<List<Language>>(emptyList()) }
    var dictionaryLanguage by remember { mutableStateOf<Language?>(null) }
    val container = rememberAppContainer(
        settingsRepository = settingsRepository,
        dataManager = dataManager,
        platform = platform,
        androidContext = androidContext,
    )

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
            favoritesRepository = container.favoritesRepository,
            dictionaryRepository = container.dictionaryRepository,
            settingsRepository = settingsRepository,
            speechPlayer = container.ttsManager,
            voiceFilterHelper = container.voiceFilterHelper,
            clock = Clock.System,
        ).also { it.start() }
    }

    suspend fun refreshFavoritesReviewState() {
        val reviewState = favoritesReviewCoordinator.refresh(
            favoritesRepository = container.favoritesRepository,
            intakeService = container.intakeService,
            statsService = container.statsService,
        )
        favoritesViewModel.updateReviewState(reviewState.toFavoriteLanguageReviewUiState())
        hasFavoritesToReview = reviewState.hasDueCards
    }

    suspend fun refreshFavoritesDueCountsOnly() {
        val reviewState = favoritesReviewCoordinator.refreshDueCountsOnly(
            favoritesRepository = container.favoritesRepository,
            intakeService = container.intakeService,
            statsService = container.statsService,
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
                container.ttsManager,
                container.voiceFilterHelper,
                dataManager,
                container.downloadCoordinator,
                container.dictionaryClient,
                container.appDataExporter,
                settingsRepository,
                platform,
                buildConfig,
                onDictionaryDataChanged = { recoverFavorites ->
                    favoritesReviewCoordinator.invalidateAllIntakeCache()
                    container.dictionaryRepository.clearSenseCache()
                    if (recoverFavorites) {
                        container.lemmaRecoveryController.ensureStarted()
                    }
                    favoritesViewModel.dropCachedFavoriteDetails()
                },
            )
        }
    LaunchedEffect(settingsViewModel) {
        settingsViewModel.migrateLegacyDefaultVoiceSelectionsAfterAppStart()
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
        // intake off — the redownload flow calls container.localDbManager.deleteAll(), and any concurrent
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
                    AppDestination.DownloadSetup(addedTranslationCode = null)
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
                                } catch (e: CancellationException) {
                                    throw e
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
                            container.dictionaryClient,
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
                                navController.navigate(AppDestination.DownloadSetup(addedTranslationCode = null))
                            }
                        }
                    )
                }
                composable<AppDestination.DownloadSetup> { backStackEntry ->
                    val args = backStackEntry.toRoute<AppDestination.DownloadSetup>()
                    val addedTranslation = args.addedTranslationCode?.let { Language.fromCodeOrNull(it) }
                    val dictLang = dictionaryLanguage
                    if (addedTranslation == null && dictLang == null) {
                        LaunchedEffect(Unit) {
                            navController.navigate(AppDestination.SetupLanguages) {
                                popUpTo<AppDestination.SetupLanguages> { inclusive = true }
                            }
                        }
                    } else {
                        // Resolved here rather than inside the plan: it runs outside composition,
                        // and the label is a fixed function of the learning language anyway.
                        val dictionaryItemName = dictLang
                            ?.let { stringResource(Res.string.download_item_dictionary_name, it.selfName) }
                            .orEmpty()

                        // Adding a translation language returns to the screen it started from and
                        // leaves the installed dictionaries alone; initial setup continues into the
                        // app and may still have to fetch the dictionary itself.
                        val leaveDownload: () -> Unit = if (addedTranslation != null) {
                            {
                                if (!navController.popBackStack(AppDestination.Settings, inclusive = false)) {
                                    navController.navigate(AppDestination.Settings)
                                }
                            }
                        } else {
                            {
                                navController.navigate(AppDestination.Search) {
                                    popUpTo<AppDestination.DownloadSetup> { inclusive = true }
                                }
                            }
                        }

                        val viewModel = viewModel(
                            viewModelStoreOwner = backStackEntry
                        ) {
                            val plan = if (addedTranslation != null) {
                                SetupDownloadPlan(
                                    dataDbManager = dataManager,
                                    downloadCoordinator = container.downloadCoordinator,
                                    learningLanguages = {
                                        dataManager.listDownloadedDatabases()
                                            .filterIsInstance<DatabaseFileInfo.Dictionary>()
                                            .map { it.language }
                                            .sortedBy { it.ordinal }
                                    },
                                    translationTargets = listOf(addedTranslation),
                                    dictionaryLanguage = null,
                                    dictionaryItemLabel = dictionaryItemName,
                                )
                            } else {
                                SetupDownloadPlan(
                                    dataDbManager = dataManager,
                                    downloadCoordinator = container.downloadCoordinator,
                                    learningLanguages = { listOfNotNull(dictLang) },
                                    translationTargets = nativeLanguages,
                                    dictionaryLanguage = dictLang,
                                    dictionaryItemLabel = dictionaryItemName,
                                )
                            }
                            DownloadViewModel(
                                downloadCoordinator = container.downloadCoordinator,
                                downloadKey = if (addedTranslation != null) {
                                    "add_trans_${addedTranslation.code}"
                                } else {
                                    "setup_${dictLang?.code.orEmpty()}"
                                },
                                analyticsParams = if (addedTranslation != null) {
                                    mapOf(
                                        "kind" to "add_translation",
                                        "lang" to addedTranslation.code,
                                    )
                                } else {
                                    mapOf(
                                        "kind" to "setup",
                                        "lang" to dictLang?.code.orEmpty(),
                                    )
                                },
                                download = plan::download,
                                finalize = { onRecoveryProgress, onWordListsSync ->
                                    try {
                                        // The preference is stored only once every translation pair
                                        // the run needed is in place, so leaving this screen early
                                        // — Later, cancel, a failed pair or system back — keeps the
                                        // language unselected. A run with no pair to fetch still
                                        // gets here and selects it. Recovery cannot undo the write:
                                        // it runs after, and its failures do not propagate. The
                                        // write has to land before recovery, which reads the stored
                                        // targets to decide what to fetch.
                                        if (addedTranslation != null) {
                                            settingsViewModel.addTranslationLanguage(addedTranslation)
                                        }
                                        favoritesReviewCoordinator.invalidateAllIntakeCache()
                                        container.dictionaryRepository.clearSenseCache()
                                        // Curated lists are keyed by learning language, so only a new
                                        // dictionary needs them synced; bring them up to date while
                                        // the finalizing screen is visible.
                                        if (addedTranslation == null && dictLang != null) {
                                            onWordListsSync(true)
                                            container.listsService.sync(dictLang)
                                            onWordListsSync(false)
                                        }
                                        container.lemmaRecoveryController.runToCompletion(onRecoveryProgress)
                                    } finally {
                                        // Also runs when the user skips the wait and recovery keeps
                                        // going in the background, so the screen behind never shows
                                        // details resolved before the new data landed.
                                        favoritesViewModel.dropCachedFavoriteDetails()
                                    }
                                },
                                onSuccess = { leaveDownload() },
                                onCancel = {
                                    if (addedTranslation != null) {
                                        leaveDownload()
                                    } else {
                                        navController.navigate(AppDestination.Search) {
                                            popUpTo<AppDestination.SetupLanguages> { inclusive = false }
                                        }
                                    }
                                },
                                onError = { _ -> leaveDownload() },
                                platform = platform,
                                loadItems = plan::loadItems,
                            )
                        }

                        DownloadScreen(
                            viewModel = viewModel,
                            description = stringResource(Res.string.download_title_downloading),
                            onLaterClick = {
                                logEvent(AnalyticsEvent.DOWNLOAD_LATER_CLICK)
                                leaveDownload()
                            }
                        )
                    }
                }
                composable<AppDestination.Search> { backStackEntry ->

                    val viewModel = viewModel(
                        viewModelStoreOwner = backStackEntry
                    ) {
                        SearchViewModel(container.dictionaryRepository, settingsRepository, container.listsService, container.dictionaryClient)
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
                        onNavigateToTextReader = { language ->
                            navController.navigate(AppDestination.TextReader(language.code))
                        },
                        hasFavoritesToReview = hasFavoritesToReview,
                        onListClick = { list ->
                            val lang = viewModel.state.selectedLanguage
                            if (lang != null) {
                                logEvent(
                                    AnalyticsEvent.LIST_CARD_CLICK,
                                    mapOf("lang" to lang.code, "list_id" to list.id)
                                )
                                navController.navigate(AppDestination.ListDetail(lang.code, list.id))
                            }
                        },
                    )
                }
                composable<AppDestination.ListDetail> { backStackEntry ->
                    val args = backStackEntry.toRoute<AppDestination.ListDetail>()
                    val lang = Language.fromCodeOrNull(args.languageCode)
                    if (lang == null) {
                        LaunchedEffect(Unit) { navController.popBackStack() }
                        return@composable
                    }
                    val viewModel = viewModel(viewModelStoreOwner = backStackEntry) {
                        ListDetailViewModel(
                            listId = args.listId,
                            language = lang,
                            repository = container.dictionaryRepository,
                            favoritesRepository = container.favoritesRepository,
                            listsService = container.listsService,
                            lemmaRecovery = container.lemmaRecovery,
                            speechPlayer = container.ttsManager,
                            voiceFilterHelper = container.voiceFilterHelper,
                            onFavoriteChanged = { _ ->
                                favoritesReviewCoordinator.invalidateIntakeCacheForLanguage(lang)
                                appCoroutineScope.launch { refreshFavoritesDueCountsOnly() }
                            },
                        )
                    }
                    ListDetailScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                        onNavigateToWordDetail = { language, lemma, senseId ->
                            val translationCodes = nativeLanguages
                                .filter { it != language }
                                .map { it.code }
                            navController.navigate(
                                AppDestination.WordDetail(
                                    dictionaryLanguageCode = language.code,
                                    lemma = lemma,
                                    targetSenseId = senseId,
                                    translationLanguageCodes = translationCodes,
                                )
                            )
                        },
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
                                        container.sessionService.continueDelayedCardsNow(language.code)
                                        true
                                    }

                                    FavoritesStudyDoneAction.STUDY_NEW ->
                                        container.intakeService.continueWithPendingFavoritesNow(language.code).cardsCreated > 0
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
                        StatsViewModel(learningLanguagesForStats, container.statsService, settingsRepository, Clock.System)
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
                            favoritesRepository = container.favoritesRepository,
                            intakeService = container.intakeService,
                            sessionService = container.sessionService,
                            statsService = container.statsService,
                            clock = Clock.System,
                            ttsManager = container.ttsManager,
                            voiceFilterHelper = container.voiceFilterHelper,
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
                        onAddTranslationLanguage = { language ->
                            navController.navigate(
                                AppDestination.DownloadSetup(addedTranslationCode = language.code)
                            )
                        },
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
                            favoritesRepository = container.favoritesRepository,
                            intake = container.intakeService,
                            listsService = container.listsService,
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
                            container.dictionaryRepository,
                            container.dictionaryClient,
                            container.wordFetchManager,
                            container.favoritesRepository,
                            container.ttsManager,
                            container.voiceFilterHelper,
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
                composable<AppDestination.TextReader> { backStackEntry ->
                    val args = backStackEntry.toRoute<AppDestination.TextReader>()
                    val language = Language.fromCodeOrNull(args.languageCode) ?: run {
                        LaunchedEffect(Unit) {
                            navController.navigate(AppDestination.Error("Unknown language: ${args.languageCode}")) {
                                popUpTo<AppDestination.TextReader> { inclusive = true }
                            }
                        }
                        return@composable
                    }
                    val viewModel = viewModel(viewModelStoreOwner = backStackEntry) {
                        TextReaderViewModel(container.dictionaryRepository, container.favoritesRepository, language)
                    }
                    TextReaderScreen(
                        viewModel = viewModel,
                        onWordClick = { wordLanguage, lemma ->
                            val translationCodes = nativeLanguages.filter { it != wordLanguage }.map { it.code }
                            val destination = AppDestination.WordDetail(
                                dictionaryLanguageCode = wordLanguage.code,
                                lemma = lemma,
                                translationLanguageCodes = translationCodes
                            )
                            navController.navigate(destination)
                        },
                        onBack = { navController.popBackStack() }
                    )
                }
                composable<AppDestination.DataVersionMismatch> {
                    val coroutineScope = rememberCoroutineScope()
                    DataVersionMismatchScreen(
                        onRedownload = {
                            coroutineScope.launch {
                                try {
                                    dataManager.deleteAllDownloadedData()
                                    container.localDbManager.deleteAll()
                                    container.dictionaryRepository.clearSenseCache()
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
                        container.favoritesRepository.shiftLearningTimestampsBack(duration)
                    }
                    refreshFavoritesDueCountsOnly()
                },
            )
        }
    }
}
