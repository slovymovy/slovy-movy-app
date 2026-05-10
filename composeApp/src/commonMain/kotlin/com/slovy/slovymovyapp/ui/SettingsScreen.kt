package com.slovy.slovymovyapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
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
import com.slovy.slovymovyapp.i18n.resolve
import com.slovy.slovymovyapp.speech.*
import com.slovy.slovymovyapp.ui.theme.AppSpacing
import com.slovy.slovymovyapp.ui.theme.serifFontFamily
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import slovymovyapp.composeapp.generated.resources.*

data class LanguageUiState(
    val voices: List<Text2SpeechVoice> = emptyList(),
    val isExpanded: Boolean = false,
    val isLoadingVoices: Boolean = false,
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
    val feedbackDialogVisible: Boolean = false,
    val feedbackComment: String = "",
    val feedbackEmail: String = "",
    val feedbackSubmitting: Boolean = false,
    val feedbackError: UiText? = null,
    val feedbackDiscussionUrl: String? = null
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

    init {
        loadLearningLanguages()
        loadLanguages()
        setupTTSListeners()
        observeDownloads()
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
                    errorMessage = UiText.Plain(NetworkErrorClassifier.userMessage(e))
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

    fun toggleTranslationLanguage(language: Language) {
        val previous = state.translationLanguages
        val updated = if (language in previous) previous - language else previous + language
        state = state.copy(translationLanguages = updated)
        translationSaveJob?.cancel()
        translationSaveJob = viewModelScope.launch {
            try {
                val current = state.translationLanguages
                val jsonArray = JsonArray(current.sortedBy { it.ordinal }.map { JsonPrimitive(it.code) })
                settingsRepository.insert(Setting(Setting.Name.LANGUAGE, jsonArray))
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
        }
    }

    fun downloadTranslation(sourceLanguage: Language, targetLanguage: Language) {
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
                    val enabledIds = voiceFilterHelper.getEnabledVoices(language)
                    if (enabledIds.isNotEmpty()) {
                        updateLanguageState(language) { it.copy(enabledVoiceIds = enabledIds) }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state = state.copy(
                    errorMessage = UiText.Plain(NetworkErrorClassifier.userMessage(e))
                )
            }
        }
    }

    fun toggleLanguageExpansion(language: Text2SpeechLanguage) {
        val s = state.languages[language] ?: return
        if (!s.isExpanded && s.voices.isEmpty()) {
            loadVoicesForLanguage(language)
        }

        updateLanguageState(language) { it.copy(isExpanded = !it.isExpanded) }
    }

    private fun loadVoicesForLanguage(language: Text2SpeechLanguage) {
        viewModelScope.launch {
            updateLanguageState(language) { it.copy(isLoadingVoices = true) }

            try {
                val voices = ttsManager.getVoicesForLanguage(language)

                if (!voiceFilterHelper.hasEnabledVoices(language)) {
                    voiceFilterHelper.initializeDefaultVoices(language, voices)
                }

                val enabledIds = voiceFilterHelper.getEnabledVoices(language)
                updateLanguageState(language) {
                    it.copy(voices = voices, enabledVoiceIds = enabledIds, isLoadingVoices = false)
                }
            } catch (e: Exception) {
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
            state = state.copy(
                testingVoice = null,
                errorMessage = UiText.Resource(
                    Res.string.settings_error_test_voice_with_reason,
                    listOf(messageOrUnknown(e))
                )
            )
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
        downloadCoordinator.cancel(downloadKey)
    }

    var pendingDiscussionUrl by mutableStateOf<String?>(null)
        private set

    fun consumePendingDiscussionUrl(): String? {
        val url = pendingDiscussionUrl
        pendingDiscussionUrl = null
        return url
    }

    fun openAcknowledgements() {
        state = state.copy(acknowledgementsVisible = true)
    }

    fun dismissAcknowledgements() {
        state = state.copy(acknowledgementsVisible = false)
    }

    fun openFeedbackDialog() {
        state = state.copy(
            feedbackDialogVisible = true,
            feedbackComment = "",
            feedbackEmail = "",
            feedbackSubmitting = false,
            feedbackError = null,
            feedbackDiscussionUrl = null
        )
    }

    fun dismissFeedbackDialog() {
        if (state.feedbackSubmitting) return
        val discussionUrl = state.feedbackDiscussionUrl
        state = state.copy(
            feedbackDialogVisible = false,
            feedbackComment = "",
            feedbackEmail = "",
            feedbackError = null,
            feedbackDiscussionUrl = null
        )
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
        state = state.copy(feedbackComment = comment, feedbackError = null)
    }

    fun updateFeedbackEmail(email: String) {
        state = state.copy(feedbackEmail = email)
    }

    fun submitFeedback() {
        if (state.feedbackSubmitting) return

        val comment = state.feedbackComment.trim()
        if (comment.isBlank()) {
            state = state.copy(feedbackError = UiText.Resource(Res.string.settings_feedback_comment_required))
            return
        }

        state = state.copy(feedbackSubmitting = true, feedbackError = null)
        viewModelScope.launch {
            try {
                val response = dictionaryClient.sendGeneralFeedback(
                    comment = comment,
                    email = state.feedbackEmail.trim().takeIf { it.isNotBlank() }
                )
                state = state.copy(
                    feedbackSubmitting = false,
                    feedbackError = null,
                    feedbackDiscussionUrl = response.discussionUrl
                )
            } catch (e: Exception) {
                state = state.copy(
                    feedbackSubmitting = false,
                    feedbackError = UiText.Plain(NetworkErrorClassifier.userMessage(e))
                )
            }
        }
    }

    private fun messageOrUnknown(throwable: Throwable): String {
        val message = throwable.message?.takeIf { it.isNotBlank() }
        return message ?: NetworkErrorClassifier.userMessage(throwable)
    }

    private suspend fun resolveUiText(text: UiText): String = when (text) {
        is UiText.Plain -> text.value
        is UiText.Resource -> getString(text.key, *text.args.toTypedArray())
        is UiText.Plural -> getPluralString(text.key, text.quantity, *text.args.toTypedArray())
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
                            val message =
                                if (entry.error != null) NetworkErrorClassifier.userMessage(entry.error)
                                else resolveUiText(UiText.Resource(Res.string.common_unknown_error))
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    wordDetailLabel: String? = null,
    onNavigateToSearch: () -> Unit = {},
    onNavigateToFavorites: () -> Unit = {},
    onNavigateToStats: () -> Unit = {},
    onNavigateToWordDetail: () -> Unit = {}
) {
    LifecycleResumeEffect(Unit) {
        viewModel.reloadSettings()
        onPauseOrDispose { }
    }

    val uriHandler = LocalUriHandler.current

    LaunchedEffect(viewModel.pendingDiscussionUrl) {
        viewModel.consumePendingDiscussionUrl()?.let { url ->
            uriHandler.openUri(url)
        }
    }

    SettingsScreenContent(
        state = viewModel.state,
        scrollState = viewModel.scrollState,
        snackbarHostState = viewModel.snackbarHostState,
        onToggleLearningLanguageExpansion = { viewModel.toggleLearningLanguageExpansion(it) },
        onRemoveLearningLanguage = { viewModel.removeLearningLanguage(it) },
        onAddLearningLanguage = { viewModel.addLearningLanguage(it) },
        onDownloadTranslation = { src, tgt -> viewModel.downloadTranslation(src, tgt) },
        onCancelDownload = { key -> viewModel.cancelDownload(key) },
        onDeleteTranslation = { src, tgt -> viewModel.deleteTranslation(src, tgt) },
        onToggleTranslationLanguage = { viewModel.toggleTranslationLanguage(it) },
        onToggleTranslationLanguagesExpanded = { viewModel.toggleTranslationLanguagesExpanded() },
        onLanguageExpand = { viewModel.toggleLanguageExpansion(it) },
        onTestVoice = { voice -> viewModel.testVoice(voice) },
        onToggleVoiceEnabled = { language, voiceId -> viewModel.toggleVoiceEnabled(language, voiceId) },
        onOpenSettings = { viewModel.openSystemSettings() },
        onExportAppData = { viewModel.exportAppData() },
        onDismissError = { viewModel.dismissError() },
        onConfirmDelete = { viewModel.confirmDelete() },
        onDismissDeleteConfirmation = { viewModel.dismissDeleteConfirmation() },
        onAcknowledgements = { viewModel.openAcknowledgements() },
        onDismissAcknowledgements = { viewModel.dismissAcknowledgements() },
        onSendFeedback = { viewModel.openFeedbackDialog() },
        onDismissFeedback = { viewModel.dismissFeedbackDialog() },
        onFeedbackCommentChange = { viewModel.updateFeedbackComment(it) },
        onFeedbackEmailChange = { viewModel.updateFeedbackEmail(it) },
        onSubmitFeedback = { viewModel.submitFeedback() },
        wordDetailLabel = wordDetailLabel,
        onNavigateToSearch = onNavigateToSearch,
        onNavigateToFavorites = onNavigateToFavorites,
        onNavigateToStats = onNavigateToStats,
        onNavigateToWordDetail = onNavigateToWordDetail
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreenContent(
    state: SettingsUiState,
    scrollState: LazyListState = LazyListState(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onToggleLearningLanguageExpansion: (Language) -> Unit = {},
    onRemoveLearningLanguage: (Language) -> Unit = {},
    onAddLearningLanguage: (Language) -> Unit = {},
    onDownloadTranslation: (Language, Language) -> Unit = { _, _ -> },
    onCancelDownload: (String) -> Unit = {},
    onDeleteTranslation: (Language, Language) -> Unit = { _, _ -> },
    onToggleTranslationLanguage: (Language) -> Unit = {},
    onToggleTranslationLanguagesExpanded: () -> Unit = {},
    onLanguageExpand: (Text2SpeechLanguage) -> Unit = {},
    onTestVoice: (Text2SpeechVoice) -> Unit = { _ -> },
    onToggleVoiceEnabled: (Text2SpeechLanguage, String) -> Unit = { _, _ -> },
    onOpenSettings: () -> Unit = {},
    onExportAppData: () -> Unit = {},
    onDismissError: () -> Unit = {},
    onConfirmDelete: () -> Unit = {},
    onDismissDeleteConfirmation: () -> Unit = {},
    onAcknowledgements: () -> Unit = {},
    onDismissAcknowledgements: () -> Unit = {},
    onSendFeedback: () -> Unit = {},
    onDismissFeedback: () -> Unit = {},
    onFeedbackCommentChange: (String) -> Unit = {},
    onFeedbackEmailChange: (String) -> Unit = {},
    onSubmitFeedback: () -> Unit = {},
    wordDetailLabel: String? = null,
    onNavigateToSearch: () -> Unit = {},
    onNavigateToFavorites: () -> Unit = {},
    onNavigateToStats: () -> Unit = {},
    onNavigateToWordDetail: () -> Unit = {}
) {
    val dismissActionLabel = stringResource(Res.string.common_dismiss)
    state.errorMessage?.let { error ->
        val errorMessage = error.resolve()
        LaunchedEffect(error) {
            snackbarHostState.showSnackbar(
                message = errorMessage,
                actionLabel = dismissActionLabel,
                duration = SnackbarDuration.Short
            )
            onDismissError()
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            stringResource(Res.string.settings_title),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontFamily = MaterialTheme.serifFontFamily,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                )
            },
            bottomBar = {
                AppNavigationBar(
                    currentScreen = AppScreen.SETTINGS,
                    onNavigateToSearch = onNavigateToSearch,
                    onNavigateToFavorites = onNavigateToFavorites,
                    onNavigateToStats = onNavigateToStats,
                    onNavigateToWordDetail = onNavigateToWordDetail,
                    wordDetailLabel = wordDetailLabel,
                    onNavigateToSettings = {}
                )
            },
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState)
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when {
                    state.isLoading -> {
                        LoadingIndicator(
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            state = scrollState,
                            contentPadding = PaddingValues(AppSpacing.lg),
                            verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
                        ) {
                            // === Languages I learn ===
                            item {
                                SectionHeader(title = stringResource(Res.string.settings_section_languages_i_learn))
                            }

                            if (state.isLoadingAvailable) {
                                item {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(AppSpacing.xl),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                    }
                                }
                            } else {
                                // Installed learning language cards
                                if (state.learningLanguages.isEmpty() && state.addableLanguages.isEmpty()) {
                                    item {
                                        Text(
                                            text = stringResource(Res.string.settings_no_languages_available),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                items(
                                    items = state.learningLanguages,
                                    key = { "learning_${it.language.code}" }
                                ) { langState ->
                                    LearningLanguageCard(
                                        state = langState,
                                        downloadingItems = state.downloadingItems,
                                        onToggleExpansion = { onToggleLearningLanguageExpansion(langState.language) },
                                        onRemove = { onRemoveLearningLanguage(langState.language) },
                                        onDownloadTranslation = { tgt ->
                                            onDownloadTranslation(langState.language, tgt)
                                        },
                                        onCancelDownload = onCancelDownload,
                                        onDeleteTranslation = { tgt ->
                                            onDeleteTranslation(langState.language, tgt)
                                        }
                                    )
                                }

                                // Add a language to learn
                                if (state.addableLanguages.isNotEmpty()) {
                                    item {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = AppSpacing.sm),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            HorizontalDivider(
                                                modifier = Modifier.weight(1f),
                                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                            )
                                            Text(
                                                text = stringResource(Res.string.settings_add_language_to_learn),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = AppSpacing.md)
                                            )
                                            HorizontalDivider(
                                                modifier = Modifier.weight(1f),
                                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                            )
                                        }
                                    }

                                    items(
                                        items = state.addableLanguages,
                                        key = { "addable_${it.language.code}" }
                                    ) { langInfo ->
                                        AddLanguageCard(
                                            language = langInfo.language,
                                            dictionarySizeBytes = langInfo.dictionarySizeBytes,
                                            downloadingItems = state.downloadingItems,
                                            onDownload = { onAddLearningLanguage(langInfo.language) },
                                            onCancelDownload = onCancelDownload
                                        )
                                    }
                                }

                            }

                            // Translation languages (always visible — prefs are local)
                            item {
                                SectionHeader(
                                    title = stringResource(Res.string.settings_section_translation_languages),
                                    modifier = Modifier.padding(top = AppSpacing.sm)
                                )
                            }
                            item {
                                TranslationLanguageSection(
                                    allLanguages = Language.entries.sortedBy { it.selfName },
                                    selectedLanguages = state.translationLanguages,
                                    isExpanded = state.isTranslationLanguagesExpanded,
                                    onToggleExpanded = onToggleTranslationLanguagesExpanded,
                                    onToggleLanguage = onToggleTranslationLanguage
                                )
                            }

                            // === Voice ===
                            if (state.languages.isNotEmpty()) {
                                item {
                                    SectionHeader(
                                        title = stringResource(Res.string.settings_section_voice),
                                        modifier = Modifier.padding(top = AppSpacing.sm)
                                    )
                                }

                                items(
                                    items = state.languages.entries.toList(),
                                    key = { "voice_${it.key.language.code}" }
                                ) { e ->
                                    VoiceSectionItem(
                                        language = e.key,
                                        languageState = e.value,
                                        onExpand = { onLanguageExpand(e.key) },
                                        onTestVoice = onTestVoice,
                                        onToggleVoiceEnabled = { voiceId -> onToggleVoiceEnabled(e.key, voiceId) },
                                        testingVoice = state.testingVoice
                                    )
                                }

                                item {
                                    DownloadMoreVoicesCard(onOpenSettings = onOpenSettings)
                                }
                            }

                            if (state.isAppDataExportSupported) {
                                item {
                                    SectionHeader(
                                        title = stringResource(Res.string.settings_section_your_data),
                                        modifier = Modifier.padding(top = AppSpacing.sm)
                                    )
                                }

                                item {
                                    AppDataSection(
                                        isExporting = state.isExportingAppData,
                                        onExport = onExportAppData
                                    )
                                }
                            }

                            // === About ===
                            state.buildConfig.let { buildConfig ->
                                item {
                                    SectionHeader(
                                        title = stringResource(Res.string.settings_section_about),
                                        modifier = Modifier.padding(top = AppSpacing.sm)
                                    )
                                }

                                item {
                                    AboutSection(
                                        buildConfig = buildConfig,
                                        onSendFeedback = onSendFeedback,
                                        onAcknowledgements = onAcknowledgements
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        state.deleteConfirmation?.let { confirmation ->
            DeleteConfirmationDialog(
                title = confirmation.title.resolve(),
                message = confirmation.message.resolve(),
                warning = confirmation.warning?.resolve(),
                onConfirm = onConfirmDelete,
                onDismiss = onDismissDeleteConfirmation
            )
        }

        if (state.acknowledgementsVisible) {
            AcknowledgementsBottomSheet(onDismiss = onDismissAcknowledgements)
        }

        if (state.feedbackDialogVisible) {
            FeedbackDialog(
                title = stringResource(Res.string.feedback_dialog_title),
                commentPlaceholder = stringResource(Res.string.feedback_dialog_placeholder),
                comment = state.feedbackComment,
                email = state.feedbackEmail,
                isSending = state.feedbackSubmitting,
                error = state.feedbackError?.resolve(),
                resultUrl = state.feedbackDiscussionUrl,
                onCommentChange = onFeedbackCommentChange,
                onEmailChange = onFeedbackEmailChange,
                onDismiss = onDismissFeedback,
                onSend = onSubmitFeedback
            )
        }
    }
}

// === Previews ===

@Preview
@Composable
private fun SettingsScreenPreviewLoading(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        SettingsScreenContent(
            state = SettingsUiState(isLoading = true)
        )
    }
}

@Preview
@Composable
private fun SettingsScreenPreviewEmpty(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        SettingsScreenContent(
            state = SettingsUiState(
                isLoading = false,
                languages = emptyMap(),
                learningLanguages = emptyList()
            )
        )
    }
}

@Preview
@Composable
private fun SettingsScreenPreviewWithLanguages(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        SettingsScreenContent(
            state = SettingsUiState(
                isLoading = false,
                learningLanguages = listOf(
                    LearningLanguageUiState(
                        language = Language.DUTCH,
                        isExpanded = true,
                        dictionarySizeBytes = 12 * 1024 * 1024L,
                        translations = listOf(
                            TranslationUiState(
                                targetLanguage = Language.ENGLISH,
                                isDownloaded = true,
                                isDownloadable = true,
                                sizeBytes = 4 * 1024 * 1024L
                            ),
                            TranslationUiState(
                                targetLanguage = Language.RUSSIAN,
                                isDownloaded = false,
                                isDownloadable = false
                            ),
                            TranslationUiState(
                                targetLanguage = Language.POLISH,
                                isDownloaded = false,
                                isDownloadable = true,
                                sizeBytes = 3 * 1024 * 1024L
                            )
                        )
                    ),
                    LearningLanguageUiState(
                        language = Language.ENGLISH,
                        isExpanded = false,
                        dictionarySizeBytes = 15 * 1024 * 1024L,
                        translations = listOf(
                            TranslationUiState(
                                targetLanguage = Language.RUSSIAN,
                                isDownloaded = true,
                                isDownloadable = true,
                                sizeBytes = 8 * 1024 * 1024L
                            )
                        )
                    )
                ),
                addableLanguages = listOf(
                    AvailableLanguageInfo(
                        language = Language.GERMAN,
                        dictionarySizeBytes = 14 * 1024 * 1024,
                        availableTranslations = emptyList()
                    )
                ),
                translationLanguages = setOf(Language.ENGLISH, Language.RUSSIAN, Language.POLISH),
                languages = mapOf(
                    Text2SpeechLanguage(
                        language = Language.DUTCH,
                        isAvailable = true,
                        missingData = false
                    ) to LanguageUiState(),
                    Text2SpeechLanguage(
                        language = Language.ENGLISH,
                        isAvailable = true,
                        missingData = false
                    ) to LanguageUiState()
                )
            )
        )
    }
}

@Preview
@Composable
private fun SettingsScreenPreviewWithExpandedVoice(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        SettingsScreenContent(
            state = SettingsUiState(
                isLoading = false,
                learningLanguages = listOf(
                    LearningLanguageUiState(
                        language = Language.ENGLISH,
                        isExpanded = false,
                        dictionarySizeBytes = 15 * 1024 * 1024L,
                        translations = listOf(
                            TranslationUiState(
                                targetLanguage = Language.RUSSIAN,
                                isDownloaded = true,
                                isDownloadable = true,
                                sizeBytes = 8 * 1024 * 1024L
                            )
                        )
                    )
                ),
                translationLanguages = setOf(Language.RUSSIAN),
                languages = mapOf(
                    Text2SpeechLanguage(
                        language = Language.ENGLISH,
                        isAvailable = true,
                        missingData = false
                    ) to LanguageUiState(
                        isExpanded = true,
                        voices = listOf(
                            Text2SpeechVoice(
                                id = "en-us-x-sfg#female_1-local",
                                name = "Female 1",
                                language = Language.ENGLISH,
                                quality = VoiceQuality.BEST,
                                networkConnectionRequired = false
                            ),
                            Text2SpeechVoice(
                                id = "en-us-x-sfg#male_1-local",
                                name = "Male 1",
                                language = Language.ENGLISH,
                                quality = VoiceQuality.GOOD,
                                networkConnectionRequired = false
                            ),
                            Text2SpeechVoice(
                                id = "en-us-x-tpf-network",
                                name = "Network Voice",
                                language = Language.ENGLISH,
                                quality = VoiceQuality.MEDIUM,
                                networkConnectionRequired = true
                            )
                        )
                    )
                )
            )
        )
    }
}

@Preview
@Composable
private fun SettingsScreenPreviewWithError(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        SettingsScreenContent(
            state = SettingsUiState(
                isLoading = false,
                learningLanguages = listOf(
                    LearningLanguageUiState(
                        language = Language.ENGLISH,
                        isExpanded = false,
                        dictionarySizeBytes = 15 * 1024 * 1024L,
                        translations = emptyList()
                    )
                ),
                translationLanguages = emptySet(),
                errorMessage = UiText.Plain("Failed to load voices for this language")
            )
        )
    }
}

@Preview
@Composable
private fun SettingsScreenPreviewWithDeleteConfirmation(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        SettingsScreenContent(
            state = SettingsUiState(
                isLoading = false,
                learningLanguages = listOf(
                    LearningLanguageUiState(
                        language = Language.ENGLISH,
                        isExpanded = false,
                        dictionarySizeBytes = 15 * 1024 * 1024L,
                        translations = listOf(
                            TranslationUiState(
                                targetLanguage = Language.RUSSIAN,
                                isDownloaded = true,
                                isDownloadable = true,
                                sizeBytes = 8 * 1024 * 1024L
                            )
                        )
                    )
                ),
                translationLanguages = setOf(Language.RUSSIAN),
                deleteConfirmation = DeleteConfirmationState(
                    title = UiText.Plain("Remove English?"),
                    message = UiText.Plain("The dictionary and all its translations will be deleted. You can re-download anytime."),
                    warning = UiText.Plain("This will also remove 1 translation."),
                    onConfirm = {}
                )
            )
        )
    }
}

@Preview
@Composable
private fun SettingsScreenPreviewWithMixedStates(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        SettingsScreenContent(
            state = SettingsUiState(
                isLoading = false,
                learningLanguages = listOf(
                    LearningLanguageUiState(
                        language = Language.ENGLISH,
                        isExpanded = true,
                        dictionarySizeBytes = 15 * 1024 * 1024L,
                        translations = listOf(
                            TranslationUiState(
                                targetLanguage = Language.RUSSIAN,
                                isDownloaded = true,
                                isDownloadable = true,
                                sizeBytes = 8 * 1024 * 1024L
                            ),
                            TranslationUiState(
                                targetLanguage = Language.POLISH,
                                isDownloaded = false,
                                isDownloadable = true,
                                sizeBytes = 7 * 1024 * 1024L
                            )
                        )
                    )
                ),
                addableLanguages = listOf(
                    AvailableLanguageInfo(
                        language = Language.RUSSIAN,
                        dictionarySizeBytes = 12 * 1024 * 1024,
                        availableTranslations = emptyList()
                    ),
                    AvailableLanguageInfo(
                        language = Language.DUTCH,
                        dictionarySizeBytes = 10 * 1024 * 1024,
                        availableTranslations = emptyList()
                    )
                ),
                translationLanguages = setOf(Language.RUSSIAN, Language.POLISH),
                downloadingItems = mapOf(
                    "dict_ru" to DownloadProgress(5 * 1024 * 1024, 12 * 1024 * 1024),
                    "trans_en_pl" to DownloadProgress(2 * 1024 * 1024, 7 * 1024 * 1024)
                ),
                isAppDataExportSupported = true,
            )
        )
    }
}

@Preview
@Composable
private fun SettingsScreenPreviewAcknowledgements(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        SettingsScreenContent(
            state = SettingsUiState(
                isLoading = false,
                acknowledgementsVisible = true
            )
        )
    }
}
