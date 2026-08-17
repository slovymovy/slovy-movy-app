package com.slovy.slovymovyapp.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slovy.slovymovyapp.AppBuildConfig
import com.slovy.slovymovyapp.analytics.Analytics
import com.slovy.slovymovyapp.analytics.AnalyticsEvent
import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.export.AppDataExporter
import com.slovy.slovymovyapp.data.remote.*
import com.slovy.slovymovyapp.data.settings.Setting
import com.slovy.slovymovyapp.data.settings.SettingsRepository
import com.slovy.slovymovyapp.i18n.UiText
import com.slovy.slovymovyapp.i18n.networkErrorUiText
import com.slovy.slovymovyapp.logging.AppLogger
import com.slovy.slovymovyapp.speech.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import slovymovyapp.composeapp.generated.resources.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import com.slovy.slovymovyapp.ui.FeedbackFormState

data class LanguageUiState(
    val voices: List<Text2SpeechVoice> = emptyList(),
    val isExpanded: Boolean = false,
    val isLoadingVoices: Boolean = false,
    /** True once the engine has answered for this language, so an empty [voices] means "none". */
    val voicesLoaded: Boolean = false,
    /** Ids of the enabled voices, always a subset of [voices]; see `VoiceFilterHelper`. */
    val enabledVoiceIds: Set<String> = emptySet()
)

data class SettingsUiState(
    // Languages I learn
    val learningLanguages: List<LearningLanguageUiState> = emptyList(),
    val addableLanguages: List<AvailableLanguageInfo> = emptyList(),
    val translationLanguages: Set<Language> = emptySet(),
    val isTranslationLanguagesExpanded: Boolean = false,
    val activeDictionaryLanguage: Language? = null,

    // Voice section
    val languages: Map<Text2SpeechLanguage, LanguageUiState> = emptyMap(),

    // Downloads & status
    val downloadingItems: Map<String, DownloadProgress?> = emptyMap(),
    val isLoading: Boolean = true,
    val isLoadingAvailable: Boolean = false,
    val settingsLoaded: Boolean = false,
    val errorMessage: UiText? = null,

    // Voice
    val testingVoice: Text2SpeechVoice? = null,
    val ttsStatus: TTSStatus = TTSStatus.IDLE,

    // Delete confirmation
    val deleteConfirmation: DeleteConfirmationState? = null,

    // Data export
    val isAppDataExportSupported: Boolean = false,
    val isExportingAppData: Boolean = false,

    // About
    val buildConfig: AppBuildConfig = AppBuildConfig("compile", 42, true, "com.slovy.slovymovyapp"),
    val acknowledgementsVisible: Boolean = false,

    // Feedback
    val feedback: FeedbackFormState = FeedbackFormState(),

    // Developer
    val developerModeEnabled: Boolean = false
)

data class DeleteConfirmationState(
    val title: UiText,
    val message: UiText,
    val warning: UiText? = null,
    val onConfirm: () -> Unit
)

class SettingsViewModel(
    private val ttsManager: TextToSpeechManager,
    private val voiceFilterHelper: VoiceFilterHelper,
    private val dataDbManager: DataDbManager,
    private val downloadCoordinator: DownloadCoordinator,
    private val dictionaryClient: DictionaryClient,
    private val appDataExporter: AppDataExporter,
    private val settingsRepository: SettingsRepository,
    private val platform: PlatformDbSupport,
    buildConfig: AppBuildConfig,
    private val onDictionaryDataChanged: suspend (recoverFavorites: Boolean) -> Unit = { _ -> },
) : ViewModel() {

    var state by mutableStateOf(
        SettingsUiState(
            buildConfig = buildConfig,
            isAppDataExportSupported = appDataExporter.isSupported
        )
    )
        private set

    val scrollState = LazyListState()
    val snackbarHostState = SnackbarHostState()
    private val downloadJobs = mutableMapOf<String, Job>()
    private var translationSaveJob: Job? = null
    private var loadLearningLanguagesJob: Job? = null
    private var loadLanguagesJob: Job? = null

    private var versionTapCount = 0
    private var lastVersionTapAtMs = 0L

    companion object {
        private const val TAG = "SettingsViewModel"
        private const val VERSION_TAPS_FOR_DEVELOPER_MODE = 7
        private val VERSION_TAP_WINDOW_MS = 2.seconds.inWholeMilliseconds
    }

    init {
        loadLearningLanguages()
        loadLanguages()
        setupTTSListeners()
        observeDownloads()
        loadDeveloperMode()
    }

    private fun loadDeveloperMode() {
        viewModelScope.launch {
            val enabled = settingsRepository.getDeveloperMode(default = state.buildConfig.isDebug)
            state = state.copy(developerModeEnabled = enabled)
        }
    }

    private fun setupTTSListeners() {
        ttsManager.addOnStatusChangeListener(this) { status ->
            state = state.copy(
                ttsStatus = status,
                testingVoice = if (status == TTSStatus.IDLE) null else state.testingVoice
            )
        }
    }

    fun reloadSettings() {
        loadLearningLanguages()
        loadLanguages()
    }

    @Suppress("DEPRECATION")
    suspend fun migrateLegacyDefaultVoiceSelectionsAfterAppStart() {
        try {
            if (settingsRepository.isLegacyVoiceMigrationDone()) return

            val languages = ttsManager.getAvailableLanguages()
            val downloadedLanguages = dataDbManager.listDownloadedDatabases()
                .filterIsInstance<DatabaseFileInfo.Dictionary>()
                .map { it.language }
                .toSet()

            languages
                .filter { it.language in downloadedLanguages }
                .forEach { language ->
                    try {
                        val voices = ttsManager.getVoicesForLanguage(language)
                        val migrated = voiceFilterHelper.migrateLegacyDefaultVoiceSelection(language, voices)
                        if (migrated) {
                            val enabledIds = voiceFilterHelper.getEnabledVoices(language)
                            updateLanguageState(language) { it.copy(enabledVoiceIds = enabledIds) }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        AppLogger.warn(TAG, "Unable to migrate legacy default voices for ${language.language.code}", e)
                    }
                }

            settingsRepository.setLegacyVoiceMigrationDone()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.warn(TAG, "Unable to run legacy voice default migration", e)
        }
    }

    private fun loadLearningLanguages() {
        loadLearningLanguagesJob?.cancel()
        loadLearningLanguagesJob = viewModelScope.launch {
            state = state.copy(isLoadingAvailable = state.learningLanguages.isEmpty())
            try {
                val downloadedFiles = dataDbManager.listDownloadedDatabases()
                val translationPrefs = loadTranslationLanguages()

                val installedDicts = downloadedFiles
                    .filterIsInstance<DatabaseFileInfo.Dictionary>()
                    .associate { it.language to it.sizeBytes }

                val downloadedTranslations = downloadedFiles
                    .filterIsInstance<DatabaseFileInfo.Translation>()
                    .associate { "${it.sourceLanguage.code}_${it.targetLanguage.code}" to it.sizeBytes }

                // Try to fetch remote availability; degrade gracefully if offline
                val available = try {
                    dataDbManager.fetchAvailableLanguages()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    emptyList()
                }
                val availableByLang = available.associateBy { it.language }

                // Build cards from installed dictionaries (local), enrich with remote data
                // Sort by ordinal for stable card ordering across refreshes
                val sortedDicts = installedDicts.entries.sortedBy { it.key.ordinal }
                val learningLanguageStates = sortedDicts.map { (language, dictSize) ->
                    val langInfo = availableByLang[language]

                    val downloadedTargetsForLang = downloadedTranslations.keys
                        .filter { it.startsWith("${language.code}_") }
                        .mapNotNull { key ->
                            val tgtCode = key.substringAfter("_")
                            Language.fromCodeOrNull(tgtCode)
                        }
                        .toSet()

                    // Include targets with active downloads so progress UI stays visible
                    val activeDownloadTargets = downloadCoordinator.downloadEntries().value.keys
                        .filter { it.startsWith("trans_${language.code}_") }
                        .mapNotNull { key ->
                            val tgtCode = key.removePrefix("trans_${language.code}_")
                            Language.fromCodeOrNull(tgtCode)
                        }
                        .toSet()

                    val allTargets = (translationPrefs + downloadedTargetsForLang + activeDownloadTargets)
                        .filter { it != language }
                        .sortedBy { it.ordinal }

                    val translations = allTargets.map { targetLang ->
                        val transKey = "${language.code}_${targetLang.code}"
                        val downloadedSize = downloadedTranslations[transKey]
                        val isDownloaded = downloadedSize != null
                        val availableTrans = langInfo?.availableTranslations?.find { it.targetLanguage == targetLang }
                        TranslationUiState(
                            targetLanguage = targetLang,
                            isDownloaded = isDownloaded,
                            isDownloadable = availableTrans != null,
                            sizeBytes = downloadedSize ?: availableTrans?.sizeBytes
                        )
                    }

                    LearningLanguageUiState(
                        language = language,
                        isExpanded = false, // placeholder, merged below
                        dictionarySizeBytes = dictSize,
                        translations = translations
                    )
                }

                val addableFromRemote = available
                    .filter { info -> info.dictionarySizeBytes != null && info.language !in installedDicts }

                // Preserve entries with active dict downloads even when remote is unavailable
                val addableLanguageCodes = addableFromRemote.map { it.language }.toSet()
                val activeDownloadEntries = downloadCoordinator.downloadEntries().value.keys
                    .filter { it.startsWith("dict_") }
                    .mapNotNull { key ->
                        val code = key.removePrefix("dict_")
                        Language.fromCodeOrNull(code)
                    }
                    .filter { it !in installedDicts && it !in addableLanguageCodes }
                    .map {
                        AvailableLanguageInfo(
                            language = it,
                            dictionarySizeBytes = null,
                            availableTranslations = emptyList()
                        )
                    }

                val addable = (addableFromRemote + activeDownloadEntries)
                    .sortedBy { it.language.ordinal }

                val activeDictCode = settingsRepository
                    .getById(Setting.Name.DICTIONARY)?.value?.jsonPrimitive?.content
                val activeDict = activeDictCode?.let { Language.fromCodeOrNull(it) }
                    ?.takeIf { it in installedDicts }

                // Merge with latest state to preserve isExpanded toggled during async fetch
                val currentExpanded = state.learningLanguages.associate { it.language to it.isExpanded }
                val mergedStates = learningLanguageStates.map { card ->
                    card.copy(isExpanded = currentExpanded[card.language] ?: false)
                }

                state = state.copy(
                    learningLanguages = mergedStates.sortedBy { it.language.selfName },
                    addableLanguages = addable.sortedBy { it.language.selfName },
                    translationLanguages = translationPrefs,
                    activeDictionaryLanguage = activeDict,
                    isLoadingAvailable = false,
                    isLoading = false,
                    settingsLoaded = true,
                    errorMessage = null
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state = state.copy(
                    isLoadingAvailable = false,
                    isLoading = false,
                    errorMessage = networkErrorUiText(e)
                )
            }
        }
    }

    private suspend fun loadTranslationLanguages(): Set<Language> {
        return settingsRepository.getTranslationLanguages()
    }

    fun toggleLearningLanguageExpansion(language: Language) {
        state = state.copy(
            learningLanguages = state.learningLanguages.map { ls ->
                if (ls.language == language) ls.copy(isExpanded = !ls.isExpanded) else ls
            }
        )
    }

    fun addLearningLanguage(language: Language) {
        downloadDictionary(language)
    }

    fun removeLearningLanguage(language: Language) {
        val translationCount = state.learningLanguages
            .find { it.language == language }
            ?.translations?.count { it.isDownloaded } ?: 0

        val warning = if (translationCount > 0) {
            UiText.Plural(
                Res.plurals.settings_remove_language_warning,
                translationCount,
                listOf(translationCount)
            )
        } else null

        showDeleteConfirmation(
            title = UiText.Resource(Res.string.settings_remove_language_confirm_title, listOf(language.selfName)),
            message = UiText.Resource(Res.string.settings_remove_language_confirm_message),
            warning = warning
        ) {
            deleteDictionary(
                language,
                UiText.Resource(Res.string.settings_language_removed, listOf(language.selfName))
            )
        }
    }

    fun toggleTranslationLanguagesExpanded() {
        state = state.copy(isTranslationLanguagesExpanded = !state.isTranslationLanguagesExpanded)
    }

    /**
     * Drops [language] from the translation preferences. Selecting a new one is not handled here:
     * it goes through the download flow, which stores the preference only once the databases for
     * the new target are in place.
     */
    fun removeTranslationLanguage(language: Language) {
        val previous = state.translationLanguages
        if (language !in previous) return
        val updated = previous - language
        state = state.copy(translationLanguages = updated)
        translationSaveJob?.cancel()
        translationSaveJob = viewModelScope.launch {
            try {
                settingsRepository.setTranslationLanguages(updated)
                onDictionaryDataChanged(false)
                loadLearningLanguages()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state = state.copy(
                    translationLanguages = previous,
                    errorMessage = UiText.Resource(
                        Res.string.settings_error_save_translation_languages,
                        listOf(messageOrUnknown(e))
                    )
                )
            }
        }
    }

    fun showDeleteConfirmation(
        title: UiText,
        message: UiText,
        warning: UiText? = null,
        onConfirm: () -> Unit
    ) {
        state = state.copy(
            deleteConfirmation = DeleteConfirmationState(
                title = title,
                message = message,
                warning = warning,
                onConfirm = onConfirm
            )
        )
    }

    fun dismissDeleteConfirmation() {
        state = state.copy(deleteConfirmation = null)
    }

    fun confirmDelete() {
        state.deleteConfirmation?.onConfirm?.invoke()
        state = state.copy(deleteConfirmation = null)
    }

    private fun deleteDictionary(language: Language, toastMessage: UiText) {
        viewModelScope.launch {
            try {
                // Cancel all in-flight downloads for this language before deleting
                downloadCoordinator.cancel("dict_${language.code}")
                downloadCoordinator.downloadEntries().value.keys
                    .filter { it.startsWith("trans_${language.code}_") }
                    .forEach { downloadCoordinator.cancel(it) }

                dataDbManager.deleteDictionary(language)
                onDictionaryDataChanged(false)

                // If deleted dictionary was the active one, switch to another or clear
                val activeDictCode = settingsRepository
                    .getById(Setting.Name.DICTIONARY)?.value?.jsonPrimitive?.content
                if (activeDictCode == language.code) {
                    val remaining = state.learningLanguages
                        .map { it.language }
                        .filter { it != language }
                    if (remaining.isNotEmpty()) {
                        settingsRepository.insert(
                            Setting(Setting.Name.DICTIONARY, JsonPrimitive(remaining.first().code))
                        )
                    } else {
                        settingsRepository.deleteById(Setting.Name.DICTIONARY)
                    }
                }

                reloadSettings()
                showSnackbar(toastMessage)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state = state.copy(
                    errorMessage = UiText.Resource(
                        Res.string.settings_error_delete_with_reason,
                        listOf(messageOrUnknown(e))
                    )
                )
            }
        }
    }

    fun deleteTranslation(src: Language, tgt: Language) {
        val toastMsg = UiText.Resource(
            Res.string.settings_translation_deleted,
            listOf(src.selfName, tgt.selfName)
        )
        showDeleteConfirmation(
            title = UiText.Resource(
                Res.string.settings_delete_translation_confirm_title,
                listOf(src.selfName, tgt.selfName)
            ),
            message = UiText.Resource(Res.string.settings_delete_translation_confirm_message)
        ) {
            viewModelScope.launch {
                try {
                    dataDbManager.deleteTranslation(src, tgt)
                    onDictionaryDataChanged(false)
                    loadLearningLanguages()
                    showSnackbar(toastMsg)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    state = state.copy(
                        errorMessage = UiText.Resource(
                            Res.string.settings_error_delete_with_reason,
                            listOf(messageOrUnknown(e))
                        )
                    )
                }
            }
        }
    }

    fun downloadDictionary(language: Language) {
        val downloadKey = "dict_${language.code}"
        if (downloadCoordinator.isRunning(downloadKey)) return

        Analytics.logEvent(
            AnalyticsEvent.SETTINGS_DOWNLOAD_DICTIONARY_CLICK,
            mapOf("kind" to "dictionary", "lang" to language.code),
        )
        val keepAlive = platform.acquireProcessKeepAlive()
        val downloadFlow = downloadCoordinator.startDownload(downloadKey) { onProgress, cancelToken ->
            dataDbManager.ensureDictionary(
                lang = language,
                onProgress = onProgress,
                cancelToken = cancelToken
            )
        }
        attachDownloadCallbacks(
            downloadKey = downloadKey,
            downloadFlow = downloadFlow,
            successMessage = UiText.Resource(Res.string.settings_dictionary_download_success),
            errorMessageBuilder = { reason ->
                UiText.Resource(Res.string.settings_error_download_dictionary_with_reason, listOf(reason))
            },
            onTerminal = { keepAlive.release() }
        ) {
            onDictionaryDataChanged(true)
            reloadSettings()
            downloadMissingTranslationsForLearningLanguage(language)
        }
    }

    fun downloadTranslation(sourceLanguage: Language, targetLanguage: Language) {
        logTranslationDownloadClick(sourceLanguage, targetLanguage)
        startTranslationDownload(sourceLanguage, targetLanguage)
    }

    private suspend fun downloadMissingTranslationsForLearningLanguage(language: Language) {
        val translationLanguages = loadTranslationLanguagesForAutoDownload() ?: return
        downloadMissingTranslationsForLearningLanguage(language, translationLanguages)
    }

    private suspend fun downloadMissingTranslationsForLearningLanguage(
        language: Language,
        translationLanguages: List<Language>
    ) {
        if (translationLanguages.isEmpty()) return
        loadDownloadableTranslationTargets(language, translationLanguages)
            .filter { !dataDbManager.hasTranslation(language, it) }
            .forEach { target ->
                startTranslationDownload(language, target)
            }
    }

    private fun logTranslationDownloadClick(sourceLanguage: Language, targetLanguage: Language) {
        Analytics.logEvent(
            AnalyticsEvent.SETTINGS_DOWNLOAD_TRANSLATION_CLICK,
            mapOf(
                "kind" to "translation",
                "src_lang" to sourceLanguage.code,
                "tgt_lang" to targetLanguage.code,
            ),
        )
    }

    private suspend fun loadTranslationLanguagesForAutoDownload(): List<Language>? {
        val translationLanguages = try {
            loadTranslationLanguages()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.warn(TAG, "Unable to load translation languages for automatic download", e)
            return null
        }
        return translationLanguages.takeIf { it.isNotEmpty() }?.toList()
    }

    private suspend fun loadDownloadableTranslationTargets(
        sourceLanguage: Language,
        translationLanguages: List<Language>
    ): List<Language> = try {
        dataDbManager.downloadableTranslationTargets(sourceLanguage, translationLanguages)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AppLogger.warn(TAG, "Unable to load downloadable translations for ${sourceLanguage.code}", e)
        emptyList()
    }

    private fun startTranslationDownload(sourceLanguage: Language, targetLanguage: Language) {
        val downloadKey = "trans_${sourceLanguage.code}_${targetLanguage.code}"
        if (downloadCoordinator.isRunning(downloadKey)) return

        val keepAlive = platform.acquireProcessKeepAlive()
        val downloadFlow = downloadCoordinator.startDownload(downloadKey) { onProgress, cancelToken ->
            dataDbManager.ensureTranslation(
                src = sourceLanguage,
                tgt = targetLanguage,
                onProgress = onProgress,
                cancelToken = cancelToken
            )
        }
        attachDownloadCallbacks(
            downloadKey = downloadKey,
            downloadFlow = downloadFlow,
            successMessage = UiText.Resource(Res.string.settings_translation_download_success),
            errorMessageBuilder = { reason ->
                UiText.Resource(Res.string.settings_error_download_translation_with_reason, listOf(reason))
            },
            onTerminal = { keepAlive.release() }
        ) {
            onDictionaryDataChanged(false)
            loadLearningLanguages()
        }
    }

    private fun loadLanguages() {
        loadLanguagesJob?.cancel()
        loadLanguagesJob = viewModelScope.launch {
            try {
                val languages = ttsManager.getAvailableLanguages()
                val downloadedDatabases = dataDbManager.listDownloadedDatabases()
                val downloadedLanguages =
                    downloadedDatabases.filterIsInstance<DatabaseFileInfo.Dictionary>()
                        .map { it.language }
                        .toSet()

                val filteredLanguages = languages.filter { it.language in downloadedLanguages }

                state = state.copy(
                    languages = filteredLanguages.associateWith { language ->
                        state.languages[language] ?: LanguageUiState()
                    }
                )

                filteredLanguages.forEach { language ->
                    try {
                        refreshVoices(language)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        AppLogger.warn(TAG, "Unable to load voices for ${language.language.code}", e)
                        // The expanded row still shows a localized load error if this language is opened.
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state = state.copy(
                    errorMessage = networkErrorUiText(e)
                )
            }
        }
    }

    fun toggleLanguageExpansion(language: Text2SpeechLanguage) {
        val s = state.languages[language] ?: return
        if (!s.isExpanded && !s.voicesLoaded) {
            loadVoicesForLanguage(language)
        }

        updateLanguageState(language) { it.copy(isExpanded = !it.isExpanded) }
    }

    /** Reads [language]'s voices from the bound engine into its row and returns them. */
    private suspend fun refreshVoices(language: Text2SpeechLanguage): List<Text2SpeechVoice> {
        val voices = ttsManager.getVoicesForLanguage(language)
        voiceFilterHelper.initializeDefaultVoices(language, voices)
        val enabledIds = voiceFilterHelper.enabledVoiceIds(voices, language)
        updateLanguageState(language) {
            it.copy(voices = voices, enabledVoiceIds = enabledIds, voicesLoaded = true)
        }
        return voices
    }

    private fun loadVoicesForLanguage(language: Text2SpeechLanguage) {
        viewModelScope.launch {
            updateLanguageState(language) { it.copy(isLoadingVoices = true) }

            try {
                refreshVoices(language)
                updateLanguageState(language) { it.copy(isLoadingVoices = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.warn(TAG, "Unable to load voices for ${language.language.code}", e)
                updateLanguageState(language) { it.copy(isLoadingVoices = false) }
                state = state.copy(
                    errorMessage = UiText.Resource(
                        Res.string.settings_error_load_voices_with_reason,
                        listOf(messageOrUnknown(e))
                    )
                )
            }
        }
    }

    private fun updateLanguageState(
        language: Text2SpeechLanguage,
        transform: (LanguageUiState) -> LanguageUiState
    ) {
        state = state.copy(
            languages = state.languages.mapValues { (lang, langState) ->
                if (lang == language) transform(langState) else langState
            }
        )
    }

    fun testVoice(voice: Text2SpeechVoice) {
        Analytics.logEvent(AnalyticsEvent.SETTINGS_TEST_VOICE_CLICK, mapOf("lang" to voice.language.code))
        if (state.ttsStatus == TTSStatus.SPEAKING && state.testingVoice != voice) {
            ttsManager.stop()
        }

        val text = TEST_PHRASES[voice.language] ?: "Hello, this is a test."
        state = state.copy(testingVoice = voice)
        try {
            ttsManager.setVoice(voice)
            ttsManager.speak(text)
        } catch (e: Exception) {
            // Only reachable when the engine does not have this id, which means the row predates a
            // default-engine change. Reload the rows instead of reporting an id the user never saw.
            AppLogger.warn(TAG, "Unable to test voice ${voice.id} for ${voice.language.code}", e)
            state = state.copy(testingVoice = null)
            loadLanguages()
        }
    }

    fun openSystemSettings() {
        Analytics.logEvent(AnalyticsEvent.SETTINGS_OPEN_SYSTEM_SETTINGS_CLICK)
        ttsManager.openSettings()
    }

    fun exportAppData() {
        if (!state.isAppDataExportSupported || state.isExportingAppData) return

        viewModelScope.launch {
            state = state.copy(isExportingAppData = true)
            try {
                val result = appDataExporter.exportAppData()
                showSnackbar(
                    UiText.Resource(
                        Res.string.settings_export_success,
                        listOf(result.artifactName, result.destinationLabel)
                    )
                )
                if (appDataExporter.canShareExport) {
                    try {
                        appDataExporter.shareExport(result)
                    } catch (e: Exception) {
                        state = state.copy(
                            errorMessage = UiText.Resource(
                                Res.string.settings_error_open_share_dialog_with_reason,
                                listOf(messageOrUnknown(e))
                            )
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state = state.copy(
                    errorMessage = UiText.Resource(
                        Res.string.settings_error_export_app_data_with_reason,
                        listOf(messageOrUnknown(e))
                    )
                )
            } finally {
                state = state.copy(isExportingAppData = false)
            }
        }
    }

    fun dismissError() {
        state = state.copy(errorMessage = null)
    }

    fun toggleVoiceEnabled(language: Text2SpeechLanguage, voiceId: String) {
        viewModelScope.launch {
            try {
                val langState = state.languages[language] ?: return@launch
                val currentEnabled = langState.enabledVoiceIds
                val newEnabled = if (voiceId in currentEnabled) {
                    Analytics.logEvent(
                        AnalyticsEvent.SETTINGS_VOICE_DISABLE_CLICK,
                        mapOf("lang" to language.language.code)
                    )
                    currentEnabled - voiceId
                } else {
                    Analytics.logEvent(
                        AnalyticsEvent.SETTINGS_VOICE_ENABLE_CLICK,
                        mapOf("lang" to language.language.code)
                    )
                    currentEnabled + voiceId
                }

                voiceFilterHelper.setEnabledVoices(language, newEnabled)
                updateLanguageState(language) { it.copy(enabledVoiceIds = newEnabled) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state = state.copy(
                    errorMessage = UiText.Resource(
                        Res.string.settings_error_update_voice_selection_with_reason,
                        listOf(messageOrUnknown(e))
                    )
                )
            }
        }
    }

    fun cancelDownload(downloadKey: String) {
        Analytics.logEvent(
            AnalyticsEvent.SETTINGS_DOWNLOAD_CANCEL_CLICK,
            mapOf("key" to downloadKey),
        )
        downloadCoordinator.cancel(downloadKey)
    }

    var pendingDiscussionUrl by mutableStateOf<String?>(null)
        private set

    fun consumePendingDiscussionUrl(): String? {
        val url = pendingDiscussionUrl
        pendingDiscussionUrl = null
        return url
    }

    @OptIn(ExperimentalTime::class)
    fun onVersionClick() {
        val now = Clock.System.now().toEpochMilliseconds()
        versionTapCount = if (now - lastVersionTapAtMs > VERSION_TAP_WINDOW_MS) 1 else versionTapCount + 1
        lastVersionTapAtMs = now
        if (versionTapCount >= VERSION_TAPS_FOR_DEVELOPER_MODE) {
            versionTapCount = 0
            lastVersionTapAtMs = 0
            toggleDeveloperMode()
        }
    }

    private fun toggleDeveloperMode() {
        viewModelScope.launch {
            val current = settingsRepository.getDeveloperMode(default = state.buildConfig.isDebug)
            val next = !current
            settingsRepository.setDeveloperMode(next)
            state = state.copy(developerModeEnabled = next)
            showSnackbar(
                if (next) UiText.Resource(Res.string.settings_developer_mode_enabled)
                else UiText.Resource(Res.string.settings_developer_mode_disabled)
            )
        }
    }

    fun openAcknowledgements() {
        state = state.copy(acknowledgementsVisible = true)
    }

    fun dismissAcknowledgements() {
        state = state.copy(acknowledgementsVisible = false)
    }

    fun openFeedbackDialog() {
        state = state.copy(feedback = state.feedback.opened())
    }

    fun dismissFeedbackDialog() {
        if (state.feedback.submitting) return
        val discussionUrl = state.feedback.resultUrl
        state = state.copy(feedback = state.feedback.dismissed())
        if (discussionUrl != null) {
            viewModelScope.launch {
                val result = showSnackbar(
                    message = UiText.Resource(Res.string.settings_feedback_sent),
                    actionLabel = UiText.Resource(Res.string.settings_feedback_view),
                    duration = SnackbarDuration.Long
                )
                if (result == SnackbarResult.ActionPerformed) {
                    pendingDiscussionUrl = discussionUrl
                }
            }
        }
    }

    fun updateFeedbackComment(comment: String) {
        state = state.copy(feedback = state.feedback.withComment(comment))
    }

    fun updateFeedbackEmail(email: String) {
        state = state.copy(feedback = state.feedback.withEmail(email))
    }

    fun submitFeedback() {
        val form = state.feedback
        if (form.submitting) return

        val comment = form.trimmedComment
        if (comment.isBlank()) {
            state = state.copy(
                feedback = form.submissionFailed(
                    UiText.Resource(Res.string.feedback_dialog_comment_required)
                )
            )
            return
        }

        state = state.copy(feedback = form.submissionStarted())
        viewModelScope.launch {
            try {
                val response = dictionaryClient.sendGeneralFeedback(
                    comment = comment,
                    email = form.trimmedEmail
                )
                state = state.copy(
                    feedback = state.feedback.submissionSucceeded(response.discussionUrl)
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state = state.copy(
                    feedback = state.feedback.submissionFailed(
                        networkErrorUiText(e)
                    )
                )
            }
        }
    }

    /**
     * Reason text for the `..._with_reason` templates. These wrap local storage and settings
     * failures, where the platform's own message ("database is locked", "permission denied") is
     * the diagnostic worth showing, so it wins over any generic copy. Only a blank message falls
     * back, and it falls back to the localized "unknown error" rather than an English sentence.
     */
    private suspend fun messageOrUnknown(throwable: Throwable): String {
        val message = throwable.message?.takeIf { it.isNotBlank() }
        return message ?: resolveUiText(UiText.Resource(Res.string.common_unknown_error))
    }

    private suspend fun resolveUiText(text: UiText): String = when (text) {
        is UiText.Plain -> text.value
        is UiText.Resource -> getString(text.key, *text.args.toTypedArray())
        is UiText.Plural -> getPluralString(text.key, text.quantity, *text.args.toTypedArray())
        is UiText.Joined -> text.items.map { resolveUiText(it) }.joinToString(text.separator)
    }

    private suspend fun showSnackbar(
        message: UiText,
        actionLabel: UiText? = null,
        duration: SnackbarDuration = SnackbarDuration.Short
    ): SnackbarResult {
        return snackbarHostState.showSnackbar(
            message = resolveUiText(message),
            actionLabel = actionLabel?.let { resolveUiText(it) },
            duration = duration
        )
    }

    override fun onCleared() {
        super.onCleared()
        ttsManager.removeOnStatusChangeListener(this)
        ttsManager.stop()
    }

    private fun observeDownloads() {
        viewModelScope.launch {
            downloadCoordinator.downloadEntries()
                .map { entries ->
                    entries.filterValues { it.status == DownloadStatus.Running }
                        .mapValues { (_, entry) -> entry.progress }
                }
                .distinctUntilChanged()
                .collect { running ->
                    state = state.copy(downloadingItems = running)
                }
        }
    }

    private fun attachDownloadCallbacks(
        downloadKey: String,
        downloadFlow: Flow<DownloadEntry?>,
        successMessage: UiText,
        errorMessageBuilder: (String) -> UiText,
        onTerminal: () -> Unit = {},
        onSuccess: suspend () -> Unit
    ) {
        if (downloadJobs[downloadKey]?.isActive == true) {
            onTerminal()
            return
        }

        val job = viewModelScope.launch {
            try {
                downloadFlow.collect { entry ->
                    when (entry?.status) {
                        DownloadStatus.Done -> {
                            downloadCoordinator.clear(downloadKey)
                            try {
                                onSuccess()
                            } finally {
                                onTerminal()
                            }
                            // Snackbar runs in a sibling coroutine so this observer can exit
                            // immediately. Otherwise its suspend would keep [downloadJobs]
                            // marked active, and a quick retry for the same key would skip
                            // its own callback wiring.
                            viewModelScope.launch { showSnackbar(successMessage) }
                            cancel()
                        }

                        DownloadStatus.Cancelled -> {
                            downloadCoordinator.clear(downloadKey)
                            onTerminal()
                            viewModelScope.launch {
                                showSnackbar(UiText.Resource(Res.string.settings_download_cancelled))
                            }
                            cancel()
                        }

                        DownloadStatus.Failed -> {
                            downloadCoordinator.clear(downloadKey)
                            val message = resolveUiText(
                                if (entry.error != null) networkErrorUiText(entry.error)
                                else UiText.Resource(Res.string.common_unknown_error)
                            )
                            state = state.copy(errorMessage = errorMessageBuilder(message))
                            onTerminal()
                            cancel()
                        }

                        else -> Unit
                    }
                }
            } finally {
                // Identity check guards against a fresh attach for the same key replacing
                // [downloadJobs] before this finally runs.
                if (downloadJobs[downloadKey] === coroutineContext[Job]) {
                    downloadJobs.remove(downloadKey)
                }
            }
        }
        downloadJobs[downloadKey] = job
    }
}

