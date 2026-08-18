package com.slovy.slovymovyapp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import com.slovy.slovymovyapp.data.export.AppDataExporter
import com.slovy.slovymovyapp.data.favorites.FavoritesRepository
import com.slovy.slovymovyapp.data.learning.fsrs.FsrsDefaults
import com.slovy.slovymovyapp.data.learning.fsrs.FsrsScheduler
import com.slovy.slovymovyapp.data.learning.intake.IntakeService
import com.slovy.slovymovyapp.data.learning.session.ExamplePicker
import com.slovy.slovymovyapp.data.learning.session.SessionService
import com.slovy.slovymovyapp.data.learning.stats.StatsService
import com.slovy.slovymovyapp.data.lists.ListsService
import com.slovy.slovymovyapp.data.lists.WordListsRepository
import com.slovy.slovymovyapp.data.local.LocalDbManager
import com.slovy.slovymovyapp.data.recovery.RecoverableSense
import com.slovy.slovymovyapp.data.remote.*
import com.slovy.slovymovyapp.data.settings.SettingsRepository
import com.slovy.slovymovyapp.speech.TextToSpeechManager
import com.slovy.slovymovyapp.speech.VoiceFilterHelper
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * The app-scoped object graph: the databases, repositories, clients, managers and services that
 * outlive any single screen.
 *
 * These used to be twenty `remember` calls at the top of [App], where the wiring sat between the
 * navigation graph and the startup routing and was hard to read past. Keeping them together also
 * makes the rule explicit: everything here is created once for the session, so a screen must take
 * what it needs from the container rather than build its own — a second [WordFetchManager] or
 * [DownloadCoordinator] would quietly split the state they exist to share.
 *
 * Build it with [rememberAppContainer], which preserves each object's original `remember` keys, so
 * a service is still recreated exactly when its own inputs change and not when a sibling's do.
 */
@Stable
internal class AppContainer(
    val favoritesRepository: FavoritesRepository,
    val localDbManager: LocalDbManager,
    val dictionaryRepository: DictionaryRepository,
    val dictionaryClient: DictionaryClient,
    val listsService: ListsService,
    val wordFetchManager: WordFetchManager,
    val lemmaRecovery: LemmaRecovery,
    val lemmaRecoveryController: LemmaRecoveryController,
    val intakeService: IntakeService,
    val sessionService: SessionService,
    val statsService: StatsService,
    val downloadCoordinator: DownloadCoordinator,
    val ttsManager: TextToSpeechManager,
    val appDataExporter: AppDataExporter,
    val voiceFilterHelper: VoiceFilterHelper,
)

/**
 * Creates the app-scoped graph and ties the two objects that own resources to the composition:
 * [LemmaRecoveryController] and [DownloadCoordinator] are closed when it leaves.
 */
@OptIn(ExperimentalTime::class)
@Composable
internal fun rememberAppContainer(
    settingsRepository: SettingsRepository,
    dataManager: DataDbManager,
    platform: PlatformDbSupport,
    androidContext: Any?,
): AppContainer {
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
    val listsClient = remember(platform) { ListsClient(platform) }
    val wordListsRepository = remember(appDatabase) { WordListsRepository(appDatabase) }
    val listsService = remember(wordListsRepository, listsClient) {
        ListsService(wordListsRepository, listsClient)
    }
    val wordFetchManager = remember(dictionaryClient) {
        WordFetchManager(dictionaryClient)
    }
    val lemmaRecovery = remember(
        favoritesRepository,
        wordListsRepository,
        dataManager,
        dictionaryRepository,
        wordFetchManager,
    ) {
        LemmaRecovery(
            itemsProvider = {
                // Recover favorites and curated word-list senses in one combined pass; both
                // implement RecoverableSense, and recover() dedups by (language, lemma).
                val favorites: List<RecoverableSense> = favoritesRepository.getAll()
                favorites + wordListsRepository.getAllSenses()
            },
            dataDbManager = dataManager,
            dictionaryRepository = dictionaryRepository,
            wordFetchManager = wordFetchManager,
        )
    }
    val lemmaRecoveryController = remember(lemmaRecovery, platform) {
        LemmaRecoveryController(lemmaRecovery, platform)
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
    DisposableEffect(lemmaRecoveryController) {
        onDispose { lemmaRecoveryController.close() }
    }
    DisposableEffect(downloadCoordinator) {
        onDispose { downloadCoordinator.close() }
    }

    return remember(
        favoritesRepository,
        localDbManager,
        dictionaryRepository,
        dictionaryClient,
        wordListsRepository,
        listsService,
        wordFetchManager,
        lemmaRecovery,
        lemmaRecoveryController,
        intakeService,
        sessionService,
        statsService,
        downloadCoordinator,
        ttsManager,
        appDataExporter,
        voiceFilterHelper,
    ) {
        AppContainer(
            favoritesRepository = favoritesRepository,
            localDbManager = localDbManager,
            dictionaryRepository = dictionaryRepository,
            dictionaryClient = dictionaryClient,
            listsService = listsService,
            wordFetchManager = wordFetchManager,
            lemmaRecovery = lemmaRecovery,
            lemmaRecoveryController = lemmaRecoveryController,
            intakeService = intakeService,
            sessionService = sessionService,
            statsService = statsService,
            downloadCoordinator = downloadCoordinator,
            ttsManager = ttsManager,
            appDataExporter = appDataExporter,
            voiceFilterHelper = voiceFilterHelper,
        )
    }
}
