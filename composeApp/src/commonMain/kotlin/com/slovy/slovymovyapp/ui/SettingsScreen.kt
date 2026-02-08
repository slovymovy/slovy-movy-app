package com.slovy.slovymovyapp.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Feedback
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewModelScope
import com.slovy.slovymovyapp.AppBuildConfig
import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.remote.*
import com.slovy.slovymovyapp.getPlatform
import com.slovy.slovymovyapp.speech.*
import com.slovy.slovymovyapp.ui.theme.AppSpacing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

data class LanguageUiState(
    val voices: List<Text2SpeechVoice> = emptyList(),
    val isExpanded: Boolean = false,
    val isLoadingVoices: Boolean = false,
    val enabledVoiceIds: Set<String> = emptySet()
)

data class DatabaseItemUiState(
    val displayName: String,
    val sizeBytes: Long,
    val deleteAction: () -> Unit
)

data class SettingsUiState(
    val languages: Map<Text2SpeechLanguage, LanguageUiState> = emptyMap(),
    val databases: List<DatabaseItemUiState> = emptyList(),
    val availableLanguages: List<AvailableLanguageInfo> = emptyList(),
    val downloadingItems: Map<String, DownloadProgress?> = emptyMap(),
    val expandedDictionaries: Set<String> = emptySet(),
    val isLoading: Boolean = true,
    val isLoadingAvailable: Boolean = false,
    val errorMessage: String? = null,
    val testingVoice: Text2SpeechVoice? = null,
    val ttsStatus: TTSStatus = TTSStatus.IDLE,
    val deleteConfirmation: DeleteConfirmationState? = null,
    val buildConfig: AppBuildConfig = AppBuildConfig("compile", 42, true, "com.slovy.slovymovyapp"),
    val feedbackDialogVisible: Boolean = false,
    val feedbackComment: String = "",
    val feedbackEmail: String = "",
    val feedbackSubmitting: Boolean = false,
    val feedbackError: String? = null,
    val feedbackDiscussionUrl: String? = null
)

data class DeleteConfirmationState(
    val title: String,
    val message: String,
    val warning: String? = null,
    val onConfirm: () -> Unit
)

// Test phrases for each language
private val TEST_PHRASES = mapOf(
    Language.ENGLISH to "Hello! This is a test of the text to speech system.",
    Language.RUSSIAN to "Привет! Это тест системы синтеза речи.",
    Language.POLISH to "Cześć! To jest test systemu syntezy mowy.",
    Language.DUTCH to "Hallo! Dit is een test van het tekst-naar-spraak systeem.",
    Language.GERMAN to "Hallo! Dies ist ein Test des Text-zu-Sprache-Systems.",
    Language.FRENCH to "Bonjour ! Ceci est un test du système de synthèse vocale.",
    Language.ITALIAN to "Ciao! Questo è un test del sistema di sintesi vocale.",
    Language.CZECH to "Ahoj! Toto je test systému převodu textu na řeč.",
    Language.TURKISH to "Merhaba! Bu, metinden konuşmaya sisteminin bir testidir.",
    Language.SPANISH to "¡Hola! Esta es una prueba del sistema de texto a voz."
)

class SettingsViewModel(
    private val ttsManager: TextToSpeechManager,
    private val voiceFilterHelper: VoiceFilterHelper,
    private val dataDbManager: DataDbManager,
    private val downloadCoordinator: DownloadCoordinator,
    private val dictionaryRepository: DictionaryRepository,
    private val dictionaryClient: DictionaryClient,
    buildConfig: AppBuildConfig
) : ViewModel() {

    var state by mutableStateOf(SettingsUiState(buildConfig = buildConfig))
        private set

    val scrollState = LazyListState()
    val snackbarHostState = SnackbarHostState()
    private val downloadJobs = mutableMapOf<String, Job>()

    init {
        loadLanguages()
        loadDatabases()
        loadAvailableLanguages()
        setupTTSListeners()
        observeDownloads()
    }

    private fun setupTTSListeners() {
        ttsManager.setOnStatusChangeListener { status ->
            state = state.copy(
                ttsStatus = status,
                testingVoice = if (status == TTSStatus.IDLE) null else state.testingVoice
            )
        }
    }

    fun reloadSettings() {
        loadLanguages()
        loadDatabases()
        loadAvailableLanguages()
    }

    private fun loadDatabases() {
        viewModelScope.launch {
            try {
                val files = dataDbManager.listDownloadedDatabases()
                val uiItems = files.map { fileInfo ->
                    when (fileInfo) {
                        is DatabaseFileInfo.Dictionary -> {
                            val langName = fileInfo.language.selfName
                            val displayName = "Dictionary: $langName"
                            // Count how many translations will be deleted along with the dictionary
                            val translationCount = files.count {
                                it is DatabaseFileInfo.Translation &&
                                        it.sourceLanguage == fileInfo.language
                            }
                            val warning = if (translationCount > 0) {
                                "This will also remove $translationCount translation${if (translationCount > 1) "s" else ""}."
                            } else null

                            val toastMsg = "$langName dictionary deleted"
                            DatabaseItemUiState(
                                displayName = displayName,
                                sizeBytes = fileInfo.sizeBytes,
                                deleteAction = {
                                    showDeleteConfirmation(
                                        title = "Delete $langName Dictionary?",
                                        message = "You can re-download it anytime.",
                                        warning = warning
                                    ) {
                                        deleteDictionary(fileInfo.language, toastMsg)
                                    }
                                }
                            )
                        }

                        is DatabaseFileInfo.Translation -> {
                            val srcName = fileInfo.sourceLanguage.selfName
                            val tgtName = fileInfo.targetLanguage.selfName
                            val displayName = "Translation: $srcName → $tgtName"
                            val toastMsg = "$srcName → $tgtName translation deleted"
                            DatabaseItemUiState(
                                displayName = displayName,
                                sizeBytes = fileInfo.sizeBytes,
                                deleteAction = {
                                    showDeleteConfirmation(
                                        title = "Delete $srcName → $tgtName Translation?",
                                        message = "You can re-download it anytime."
                                    ) {
                                        deleteTranslation(
                                            fileInfo.sourceLanguage,
                                            fileInfo.targetLanguage,
                                            toastMsg
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
                state = state.copy(databases = uiItems)
            } catch (e: Exception) {
                state = state.copy(errorMessage = "Failed to load databases: ${e.message}")
            }
        }
    }

    private fun loadAvailableLanguages() {
        viewModelScope.launch {
            state = state.copy(isLoadingAvailable = true)
            try {
                val available = dataDbManager.fetchAvailableLanguages()
                state = state.copy(
                    availableLanguages = available,
                    isLoadingAvailable = false
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state = state.copy(
                    isLoadingAvailable = false,
                    errorMessage = NetworkErrorClassifier.userMessage(e)
                )
            }
        }
    }

    fun showDeleteConfirmation(
        title: String,
        message: String,
        warning: String? = null,
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

    private fun deleteDictionary(language: Language, toastMessage: String) {
        viewModelScope.launch {
            try {
                dataDbManager.deleteDictionary(language)
                dictionaryRepository.clearSenseCache()
                reloadSettings()
                snackbarHostState.showSnackbar(toastMessage)
            } catch (e: Exception) {
                state = state.copy(errorMessage = "Failed to delete: ${e.message}")
            }
        }
    }

    private fun deleteTranslation(src: Language, tgt: Language, toastMessage: String) {
        viewModelScope.launch {
            try {
                dataDbManager.deleteTranslation(src, tgt)
                dictionaryRepository.clearSenseCache()
                loadDatabases()
                snackbarHostState.showSnackbar(toastMessage)
            } catch (e: Exception) {
                state = state.copy(errorMessage = "Failed to delete: ${e.message}")
            }
        }
    }

    fun downloadDictionary(language: Language) {
        val downloadKey = "dict_${language.code}"
        if (downloadCoordinator.isRunning(downloadKey)) return

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
            successMessage = "Dictionary downloaded successfully",
            errorPrefix = "Failed to download dictionary"
        ) {
            dictionaryRepository.clearSenseCache()
            reloadSettings()
            // Auto-expand the dictionary card to reveal translations
            expandDictionary(language.code)
        }
    }

    fun downloadTranslation(sourceLanguage: Language, targetLanguage: Language) {
        val downloadKey = "trans_${sourceLanguage.code}_${targetLanguage.code}"
        if (downloadCoordinator.isRunning(downloadKey)) return

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
            successMessage = "Translation downloaded successfully",
            errorPrefix = "Failed to download translation"
        ) {
            dictionaryRepository.clearSenseCache()
            loadDatabases()
        }
    }

    private fun loadLanguages() {
        viewModelScope.launch {
            state = if (state.languages.isEmpty()) {
                state.copy(isLoading = true, errorMessage = null)
            } else {
                state.copy(errorMessage = null)
            }
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
                        // Preserve existing state if language already exists
                        state.languages[language] ?: LanguageUiState()
                    },
                    isLoading = false
                )

                // Load enabled voice IDs for each language so counts can be shown
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
                    isLoading = false,
                    errorMessage = NetworkErrorClassifier.userMessage(e)
                )
            }
        }
    }

    fun toggleLanguageExpansion(language: Text2SpeechLanguage) {
        val s = state.languages[language]!!
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

                // Initialize default voices if needed
                if (!voiceFilterHelper.hasEnabledVoices(language)) {
                    voiceFilterHelper.initializeDefaultVoices(language, voices)
                }

                val enabledIds = voiceFilterHelper.getEnabledVoices(language)
                updateLanguageState(language) {
                    it.copy(voices = voices, enabledVoiceIds = enabledIds, isLoadingVoices = false)
                }
            } catch (e: Exception) {
                updateLanguageState(language) { it.copy(isLoadingVoices = false) }
                state = state.copy(errorMessage = "Failed to load voices: ${e.message}")
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
                errorMessage = "Failed to test voice: ${e.message}"
            )
        }
    }

    fun openSystemSettings() {
        ttsManager.openSettings()
    }

    fun dismissError() {
        state = state.copy(errorMessage = null)
    }

    fun toggleDictionaryExpansion(languageCode: String) {
        state = state.copy(
            expandedDictionaries = if (state.expandedDictionaries.contains(languageCode)) {
                state.expandedDictionaries - languageCode
            } else {
                state.expandedDictionaries + languageCode
            }
        )
    }

    private fun expandDictionary(languageCode: String) {
        state = state.copy(
            expandedDictionaries = state.expandedDictionaries + languageCode
        )
    }

    fun toggleVoiceEnabled(language: Text2SpeechLanguage, voiceId: String) {
        viewModelScope.launch {
            try {
                val langState = state.languages[language] ?: return@launch
                val currentEnabled = langState.enabledVoiceIds
                val newEnabled = if (voiceId in currentEnabled) {
                    currentEnabled - voiceId
                } else {
                    currentEnabled + voiceId
                }

                voiceFilterHelper.setEnabledVoices(language, newEnabled)
                updateLanguageState(language) { it.copy(enabledVoiceIds = newEnabled) }
            } catch (e: Exception) {
                state = state.copy(errorMessage = "Failed to update voice selection: ${e.message}")
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
                val result = snackbarHostState.showSnackbar(
                    message = "Feedback sent",
                    actionLabel = "View",
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
            state = state.copy(feedbackError = "Comment is required")
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
                    feedbackError = NetworkErrorClassifier.userMessage(e)
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
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
        successMessage: String,
        errorPrefix: String,
        onSuccess: suspend () -> Unit
    ) {
        if (downloadJobs[downloadKey]?.isActive == true) return

        downloadJobs[downloadKey] = viewModelScope.launch {
            try {
                downloadFlow.collect { entry ->
                    when (entry?.status) {
                        DownloadStatus.Done -> {
                            downloadCoordinator.clear(downloadKey)
                            onSuccess()
                            snackbarHostState.showSnackbar(successMessage)
                            cancel()
                        }

                        DownloadStatus.Cancelled -> {
                            downloadCoordinator.clear(downloadKey)
                            snackbarHostState.showSnackbar("Download cancelled")
                            cancel()
                        }

                        DownloadStatus.Failed -> {
                            downloadCoordinator.clear(downloadKey)
                            val message =
                                if (entry.error != null) NetworkErrorClassifier.userMessage(entry.error) else "Unknown error"
                            state = state.copy(errorMessage = "$errorPrefix: $message")
                            cancel()
                        }

                        else -> Unit
                    }
                }
            } finally {
                downloadJobs.remove(downloadKey)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    learningLanguage: Language? = null,
    nativeLanguages: List<Language> = emptyList(),
    onChangeLanguages: () -> Unit = {},
    wordDetailLabel: String? = null,
    onNavigateToSearch: () -> Unit = {},
    onNavigateToFavorites: () -> Unit = {},
    onNavigateToWordDetail: () -> Unit = {}
) {
    // Reload settings when returning from system settings
    LifecycleResumeEffect(Unit) {
        viewModel.reloadSettings()
        onPauseOrDispose { }
    }

    val uriHandler = LocalUriHandler.current

    // Handle pending discussion URL from snackbar action
    LaunchedEffect(viewModel.pendingDiscussionUrl) {
        viewModel.consumePendingDiscussionUrl()?.let { url ->
            uriHandler.openUri(url)
        }
    }

    SettingsScreenContent(
        state = viewModel.state,
        scrollState = viewModel.scrollState,
        snackbarHostState = viewModel.snackbarHostState,
        learningLanguage = learningLanguage,
        nativeLanguages = nativeLanguages,
        onChangeLanguages = onChangeLanguages,
        onLanguageExpand = { viewModel.toggleLanguageExpansion(it) },
        onTestVoice = { voice -> viewModel.testVoice(voice) },
        onToggleVoiceEnabled = { language, voiceId -> viewModel.toggleVoiceEnabled(language, voiceId) },
        onOpenSettings = { viewModel.openSystemSettings() },
        onDismissError = { viewModel.dismissError() },
        onConfirmDelete = { viewModel.confirmDelete() },
        onDismissDeleteConfirmation = { viewModel.dismissDeleteConfirmation() },
        onDownloadDictionary = { language -> viewModel.downloadDictionary(language) },
        onCancelDownload = { key -> viewModel.cancelDownload(key) },
        onDownloadTranslation = { src, tgt -> viewModel.downloadTranslation(src, tgt) },
        onToggleDictionaryExpansion = { languageCode -> viewModel.toggleDictionaryExpansion(languageCode) },
        onSendFeedback = { viewModel.openFeedbackDialog() },
        onDismissFeedback = { viewModel.dismissFeedbackDialog() },
        onFeedbackCommentChange = { viewModel.updateFeedbackComment(it) },
        onFeedbackEmailChange = { viewModel.updateFeedbackEmail(it) },
        onSubmitFeedback = { viewModel.submitFeedback() },
        wordDetailLabel = wordDetailLabel,
        onNavigateToSearch = onNavigateToSearch,
        onNavigateToFavorites = onNavigateToFavorites,
        onNavigateToWordDetail = onNavigateToWordDetail
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreenContent(
    state: SettingsUiState,
    scrollState: LazyListState = LazyListState(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    learningLanguage: Language? = null,
    nativeLanguages: List<Language> = emptyList(),
    onChangeLanguages: () -> Unit = {},
    onLanguageExpand: (Text2SpeechLanguage) -> Unit = {},
    onTestVoice: (Text2SpeechVoice) -> Unit = { _ -> },
    onToggleVoiceEnabled: (Text2SpeechLanguage, String) -> Unit = { _, _ -> },
    onOpenSettings: () -> Unit = {},
    onDismissError: () -> Unit = {},
    onConfirmDelete: () -> Unit = {},
    onDismissDeleteConfirmation: () -> Unit = {},
    onDownloadDictionary: (Language) -> Unit = {},
    onCancelDownload: (String) -> Unit = {},
    onDownloadTranslation: (Language, Language) -> Unit = { _, _ -> },
    onToggleDictionaryExpansion: (String) -> Unit = {},
    onSendFeedback: () -> Unit = {},
    onDismissFeedback: () -> Unit = {},
    onFeedbackCommentChange: (String) -> Unit = {},
    onFeedbackEmailChange: (String) -> Unit = {},
    onSubmitFeedback: () -> Unit = {},
    wordDetailLabel: String? = null,
    onNavigateToSearch: () -> Unit = {},
    onNavigateToFavorites: () -> Unit = {},
    onNavigateToWordDetail: () -> Unit = {}
) {
    // Show error snackbar
    state.errorMessage?.let { error ->
        LaunchedEffect(error) {
            snackbarHostState.showSnackbar(
                message = error,
                actionLabel = "Dismiss",
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
                            "Settings",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                )
            },
            bottomBar = {
                AppNavigationBar(
                    currentScreen = AppScreen.SETTINGS,
                    onNavigateToSearch = onNavigateToSearch,
                    onNavigateToFavorites = onNavigateToFavorites,
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
                            // Language Preferences
                            if (learningLanguage != null) {
                                item {
                                    SectionHeader(title = "Language Preferences")
                                }

                                item {
                                    ElevatedCard(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = MaterialTheme.shapes.extraLarge,
                                        colors = CardDefaults.elevatedCardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                                        ),
                                        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
                                        onClick = onChangeLanguages
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(AppSpacing.lg),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Language,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(24.dp)
                                            )

                                            Spacer(modifier = Modifier.width(AppSpacing.md))

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "Learning: ${learningLanguage.flag} ${learningLanguage.selfName}",
                                                    style = MaterialTheme.typography.titleMedium,
                                                )
                                                nativeLanguages.forEach { nativeLanguage ->
                                                    Text(
                                                        text = "Native: ${nativeLanguage.flag} ${nativeLanguage.selfName}",
                                                        style = MaterialTheme.typography.titleMedium
                                                    )
                                                }
                                            }

                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }

                            // Languages and Dictionaries
                            item {
                                SectionHeader(title = "Languages and Dictionaries")
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
                            } else if (state.availableLanguages.isEmpty()) {
                                item {
                                    Text(
                                        text = "No databases available",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                // Group into Downloaded and Available
                                val (downloaded, available) = state.availableLanguages.partition { langInfo ->
                                    state.databases.any { it.displayName == "Dictionary: ${langInfo.language.selfName}" }
                                }

                                items(
                                    items = downloaded,
                                    key = { "downloaded_${it.language.code}" }
                                ) { langInfo ->
                                    val dictDb = state.databases.find {
                                        it.displayName == "Dictionary: ${langInfo.language.selfName}"
                                    }
                                    DictionaryCard(
                                        languageInfo = langInfo,
                                        dictionaryDb = dictDb,
                                        isDownloaded = true,
                                        isExpanded = state.expandedDictionaries.contains(langInfo.language.code),
                                        onExpand = { onToggleDictionaryExpansion(langInfo.language.code) },
                                        onDownloadDictionary = { onDownloadDictionary(langInfo.language) },
                                        onCancelDownload = onCancelDownload,
                                        onDeleteDictionary = { dictDb?.deleteAction?.invoke() },
                                        onDownloadTranslation = { tgtLang ->
                                            onDownloadTranslation(langInfo.language, tgtLang)
                                        },
                                        onDeleteTranslation = { tgtLang ->
                                            state.databases.find {
                                                it.displayName == "Translation: ${langInfo.language.selfName} → ${tgtLang.selfName}"
                                            }?.deleteAction?.invoke()
                                        },
                                        downloadedTranslations = state.databases.filter {
                                            it.displayName.startsWith("Translation: ${langInfo.language.selfName} →")
                                        },
                                        downloadingItems = state.downloadingItems
                                    )
                                }

                                if (available.isNotEmpty() && downloaded.isNotEmpty()) {
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
                                                text = "Available to download",
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
                                }

                                items(
                                    items = available,
                                    key = { "available_${it.language.code}" }
                                ) { langInfo ->
                                    val dictDb = state.databases.find {
                                        it.displayName == "Dictionary: ${langInfo.language.selfName}"
                                    }
                                    DictionaryCard(
                                        languageInfo = langInfo,
                                        dictionaryDb = dictDb,
                                        isDownloaded = false,
                                        isExpanded = state.expandedDictionaries.contains(langInfo.language.code),
                                        onExpand = { onToggleDictionaryExpansion(langInfo.language.code) },
                                        onDownloadDictionary = { onDownloadDictionary(langInfo.language) },
                                        onCancelDownload = onCancelDownload,
                                        onDeleteDictionary = { dictDb?.deleteAction?.invoke() },
                                        onDownloadTranslation = { tgtLang ->
                                            onDownloadTranslation(langInfo.language, tgtLang)
                                        },
                                        onDeleteTranslation = { tgtLang ->
                                            state.databases.find {
                                                it.displayName == "Translation: ${langInfo.language.selfName} → ${tgtLang.selfName}"
                                            }?.deleteAction?.invoke()
                                        },
                                        downloadedTranslations = state.databases.filter {
                                            it.displayName.startsWith("Translation: ${langInfo.language.selfName} →")
                                        },
                                        downloadingItems = state.downloadingItems
                                    )
                                }
                            }

                            if (state.languages.isNotEmpty()) {
                                // Voice section header
                                item {
                                    SectionHeader(
                                        title = "Voice",
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
                                    ElevatedCard(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = MaterialTheme.shapes.extraLarge,
                                        colors = CardDefaults.elevatedCardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                                        ),
                                        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
                                        onClick = onOpenSettings
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(AppSpacing.lg),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Download,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(24.dp)
                                            )

                                            Spacer(modifier = Modifier.width(AppSpacing.md))

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "Download more voices",
                                                    style = MaterialTheme.typography.titleMedium,
                                                )
                                                val platform = getPlatform().name
                                                val voicesText = when {
                                                    platform.contains(
                                                        "Android",
                                                        ignoreCase = true
                                                    ) -> "Open system settings"

                                                    platform.contains(
                                                        "iOS",
                                                        ignoreCase = true
                                                    ) -> "Accessibility → Spoken Content → Voices"

                                                    else -> ""
                                                }

                                                if (voicesText.isNotEmpty()) {
                                                    Text(
                                                        text = voicesText,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }

                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }

                            // About section
                            state.buildConfig.let { buildConfig ->
                                item {
                                    SectionHeader(
                                        title = "About",
                                        modifier = Modifier.padding(top = AppSpacing.sm)
                                    )
                                }

                                item {
                                    AboutSection(
                                        buildConfig = buildConfig,
                                        onSendFeedback = onSendFeedback
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Delete confirmation dialog
        state.deleteConfirmation?.let { confirmation ->
            DeleteConfirmationDialog(
                title = confirmation.title,
                message = confirmation.message,
                warning = confirmation.warning,
                onConfirm = onConfirmDelete,
                onDismiss = onDismissDeleteConfirmation
            )
        }

        if (state.feedbackDialogVisible) {
            FeedbackDialog(
                title = "App feedback",
                comment = state.feedbackComment,
                email = state.feedbackEmail,
                isSending = state.feedbackSubmitting,
                error = state.feedbackError,
                resultUrl = state.feedbackDiscussionUrl,
                onCommentChange = onFeedbackCommentChange,
                onEmailChange = onFeedbackEmailChange,
                onDismiss = onDismissFeedback,
                onSend = onSubmitFeedback
            )
        }
    }
}

@Composable
private fun DictionaryCard(
    languageInfo: AvailableLanguageInfo,
    dictionaryDb: DatabaseItemUiState?,
    isDownloaded: Boolean,
    isExpanded: Boolean,
    onExpand: () -> Unit,
    onDownloadDictionary: () -> Unit,
    onCancelDownload: (String) -> Unit,
    onDeleteDictionary: () -> Unit,
    onDownloadTranslation: (Language) -> Unit,
    onDeleteTranslation: (Language) -> Unit,
    downloadedTranslations: List<DatabaseItemUiState>,
    downloadingItems: Map<String, DownloadProgress?>
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isDownloaded) {
                    Modifier.border(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        MaterialTheme.shapes.extraLarge
                    )
                } else Modifier
            ),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isDownloaded) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            }
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onExpand() }
                    .padding(AppSpacing.lg),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)
                    ) {
                        if (isDownloaded) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Text(
                            text = if (isDownloaded) "Downloaded" else "Available",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isDownloaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(AppSpacing.xs))
                    Text(
                        text = "${languageInfo.language.flag} ${languageInfo.language.selfName}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(AppSpacing.xs))
                    val sizeBytes = dictionaryDb?.sizeBytes ?: languageInfo.dictionarySizeBytes
                    if (sizeBytes != null) {
                        Text(
                            text = "${if (isDownloaded) "Downloaded" else "Dictionary"} ${formatFileSize(sizeBytes)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                val dictDownloadKey = "dict_${languageInfo.language.code}"
                val dictDownloading = downloadingItems.containsKey(dictDownloadKey)
                val dictProgress = downloadingItems[dictDownloadKey]

                Box(
                    modifier = Modifier.size(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (dictDownloading) {
                        CancellableProgressIndicator(
                            progress = dictProgress?.percent?.toFloat()?.div(100f) ?: -1f,
                            onCancel = { onCancelDownload(dictDownloadKey) },
                            size = 48.dp
                        )
                    } else if (isDownloaded) {
                        IconButton(onClick = onDeleteDictionary) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        IconButton(onClick = onDownloadDictionary) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Download",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppSpacing.lg)
                        .padding(bottom = AppSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                ) {
                    val (downloadedTrans, availableTrans) = languageInfo.availableTranslations.partition { translation ->
                        downloadedTranslations.any {
                            it.displayName == "Translation: ${languageInfo.language.selfName} → ${translation.targetLanguage.selfName}"
                        }
                    }

                    if (languageInfo.availableTranslations.isNotEmpty()) {
                        Text(
                            text = "Translations",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    (downloadedTrans + availableTrans).forEach { translation ->
                        val transDownloadKey = "trans_${languageInfo.language.code}_${translation.targetLanguage.code}"
                        val transDownloading = downloadingItems.containsKey(transDownloadKey)
                        val transProgress = downloadingItems[transDownloadKey]
                        val transDb = downloadedTranslations.find {
                            it.displayName == "Translation: ${languageInfo.language.selfName} → ${translation.targetLanguage.selfName}"
                        }

                        TranslationItem(
                            targetLanguage = translation.targetLanguage,
                            sizeBytes = translation.sizeBytes,
                            isDownloaded = transDb != null,
                            isDownloading = transDownloading,
                            downloadProgress = transProgress,
                            onDownload = { onDownloadTranslation(translation.targetLanguage) },
                            onCancel = { onCancelDownload(transDownloadKey) },
                            onDelete = { onDeleteTranslation(translation.targetLanguage) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TranslationItem(
    targetLanguage: Language,
    sizeBytes: Long,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    downloadProgress: DownloadProgress?,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = if (isDownloaded) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)
        } else {
            androidx.compose.ui.graphics.Color.Transparent
        },
        border = if (isDownloaded) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
        } else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${targetLanguage.flag} ${targetLanguage.selfName}",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = formatFileSize(sizeBytes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isDownloading) {
                    CancellableProgressIndicator(
                        progress = downloadProgress?.percent?.toFloat()?.div(100f) ?: -1f,
                        onCancel = onCancel,
                        size = 48.dp
                    )
                } else if (isDownloaded) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else {
                    IconButton(onClick = onDownload) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Download",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceSectionItem(
    language: Text2SpeechLanguage,
    languageState: LanguageUiState,
    onExpand: () -> Unit,
    onTestVoice: (Text2SpeechVoice) -> Unit,
    onToggleVoiceEnabled: (String) -> Unit,
    testingVoice: Text2SpeechVoice? = null
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onExpand() }
                    .padding(AppSpacing.lg),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )

                Spacer(modifier = Modifier.width(AppSpacing.md))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = language.language.selfName,
                        style = MaterialTheme.typography.titleMedium
                    )
                    val enabledVoicesCount = languageState.enabledVoiceIds.size
                    val voiceText = when {
                        enabledVoicesCount == 0 -> "No voices enabled"
                        enabledVoicesCount == 1 -> {
                            languageState.voices.find { it.id in languageState.enabledVoiceIds }?.let {
                                "${it.name ?: "Unknown"} (${it.language.code.uppercase()})"
                            } ?: "1 voice enabled"
                        }

                        else -> "$enabledVoicesCount voices enabled"
                    }
                    Text(
                        text = voiceText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Icon(
                    imageVector = if (languageState.isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (languageState.isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (languageState.isExpanded) {
                if (languageState.isLoadingVoices) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(AppSpacing.lg),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                } else if (languageState.voices.isEmpty()) {
                    Text(
                        text = "No voices available",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = AppSpacing.lg, vertical = AppSpacing.sm)
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AppSpacing.lg),
                    ) {
                        languageState.voices.forEachIndexed { index, voice ->
                            if (index > 0) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = AppSpacing.sm),
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                            }
                            VoiceItem(
                                voice = voice,
                                onTest = { onTestVoice(voice) },
                                isTesting = (testingVoice == voice),
                                isEnabled = voice.id in languageState.enabledVoiceIds,
                                onToggleEnabled = { onToggleVoiceEnabled(voice.id) }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(AppSpacing.lg))
            }
        }
    }
}

@Composable
private fun AboutSection(
    buildConfig: AppBuildConfig,
    onSendFeedback: () -> Unit = {}
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            AboutItem(
                icon = Icons.Outlined.Feedback,
                title = "Send us feedback",
                subtitle = "We'd love to hear from you",
                onClick = onSendFeedback
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = AppSpacing.lg),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
            AboutItem(
                icon = Icons.Outlined.Info,
                title = "Version",
                subtitle = buildConfig.versionName,
                onClick = {}
            )
        }
    }
}

@Composable
private fun AboutItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(AppSpacing.lg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(AppSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LoadingIndicator(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
fun DeleteConfirmationDialog(
    title: String,
    message: String,
    warning: String? = null,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (warning != null) {
                    Text(
                        text = warning,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(vertical = AppSpacing.sm)
    )
}

@Composable
private fun VoiceItem(
    voice: Text2SpeechVoice,
    onTest: () -> Unit = {},
    isTesting: Boolean = false,
    isEnabled: Boolean = true,
    onToggleEnabled: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AppSpacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
        ) {
            if (voice.name == null) {
                Text(
                    text = voice.id,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = voice.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }


            val languagePair = remember(voice.id) {
                val pattern1 = Regex("^([a-zA-Z]{2}-[a-zA-Z]{2})")
                val pattern2 = Regex("com\\.apple\\.voice\\.[a-zA-Z]+\\.([a-zA-Z]{2}-[a-zA-Z]{2})\\.")

                (pattern1.find(voice.id)?.groupValues?.get(1)
                    ?: pattern2.find(voice.id)?.groupValues?.get(1))?.uppercase()
            }

            if (languagePair != null) {
                Text(
                    text = languagePair,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val qualityColor = when (voice.quality) {
                    VoiceQuality.BEST -> MaterialTheme.colorScheme.primary
                    VoiceQuality.GOOD -> MaterialTheme.colorScheme.tertiary
                    VoiceQuality.MEDIUM -> MaterialTheme.colorScheme.outline
                }
                Surface(
                    shape = CircleShape,
                    border = BorderStroke(1.dp, qualityColor.copy(alpha = 0.5f)),
                    color = qualityColor.copy(alpha = 0.1f)
                ) {
                    Text(
                        text = when (voice.quality) {
                            VoiceQuality.BEST -> "High"
                            VoiceQuality.GOOD -> "Good"
                            VoiceQuality.MEDIUM -> "Medium"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = qualityColor,
                        modifier = Modifier.padding(horizontal = AppSpacing.sm, vertical = 2.dp)
                    )
                }
                if (voice.networkConnectionRequired) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                    ) {
                        Text(
                            text = "Online",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = AppSpacing.sm, vertical = 2.dp)
                        )
                    }
                }
            }
        }

        IconButton(onClick = onTest) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Test Voice",
                tint = if (isTesting) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(
                    if (isEnabled) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
                .clickable { onToggleEnabled() }
                .border(
                    width = 1.dp,
                    color = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isEnabled) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun CancellableProgressIndicator(
    progress: Float,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp
) {
    val iconSize = size * 0.4f
    val strokeWidth = 2.5.dp
    // Clamp progress to valid range, use indeterminate if unknown (-1)
    val clampedProgress = progress.coerceIn(0f, 1f)
    val isIndeterminate = progress < 0f
    val progressPercent = if (isIndeterminate) null else (clampedProgress * 100).toInt()

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .semantics(mergeDescendants = true) {
                // Expose progress to screen readers
                progressBarRangeInfo = if (isIndeterminate) {
                    ProgressBarRangeInfo.Indeterminate
                } else {
                    ProgressBarRangeInfo(clampedProgress, 0f..1f)
                }
                stateDescription = if (isIndeterminate) "Downloading" else "Downloading $progressPercent%"
            }
            .clickable(
                onClick = onCancel,
                onClickLabel = "Cancel download",
                role = Role.Button
            ),
        contentAlignment = Alignment.Center
    ) {
        // Progress ring with track
        if (isIndeterminate) {
            CircularProgressIndicator(
                modifier = Modifier.size(size),
                strokeWidth = strokeWidth,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                color = MaterialTheme.colorScheme.primary
            )
        } else {
            CircularProgressIndicator(
                progress = { clampedProgress },
                modifier = Modifier.size(size),
                strokeWidth = strokeWidth,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                color = MaterialTheme.colorScheme.primary
            )
        }

        // Cancel icon with subtle background
        Surface(
            modifier = Modifier.size(iconSize + 6.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = null, // Parent provides accessibility label
                    modifier = Modifier.size(iconSize),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> {
            val gb = (bytes * 100 / (1024 * 1024 * 1024)).toDouble() / 100.0
            "$gb GB"
        }
    }
}

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
                databases = emptyList()
            )
        )
    }
}

@Preview
@Composable
private fun SettingsScreenPreviewWithDatabases(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        SettingsScreenContent(
            state = SettingsUiState(
                isLoading = false,
                languages = emptyMap(),
                databases = listOf(
                    DatabaseItemUiState(
                        displayName = "Dictionary: English",
                        sizeBytes = 15 * 1024 * 1024,
                        deleteAction = {}
                    ),
                    DatabaseItemUiState(
                        displayName = "Dictionary: Русский",
                        sizeBytes = 12 * 1024 * 1024,
                        deleteAction = {}
                    ),
                    DatabaseItemUiState(
                        displayName = "Translation: English → Русский",
                        sizeBytes = 8 * 1024 * 1024,
                        deleteAction = {}
                    ),
                    DatabaseItemUiState(
                        displayName = "Translation: Русский → English",
                        sizeBytes = 7500 * 1024,
                        deleteAction = {}
                    )
                ),
                availableLanguages = listOf(
                    AvailableLanguageInfo(
                        language = Language.ENGLISH,
                        dictionarySizeBytes = 15 * 1024 * 1024,
                        availableTranslations = listOf(
                            AvailableTranslationInfo(
                                targetLanguage = Language.RUSSIAN,
                                sizeBytes = 8 * 1024 * 1024
                            )
                        )
                    ),
                    AvailableLanguageInfo(
                        language = Language.RUSSIAN,
                        dictionarySizeBytes = 12 * 1024 * 1024,
                        availableTranslations = listOf(
                            AvailableTranslationInfo(
                                targetLanguage = Language.ENGLISH,
                                sizeBytes = 8 * 1024 * 1024
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
private fun SettingsScreenPreviewWithLanguages(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        SettingsScreenContent(
            state = SettingsUiState(
                isLoading = false,
                languages = mapOf(
                    Text2SpeechLanguage(
                        language = Language.ENGLISH,
                        isAvailable = true,
                        missingData = false
                    ) to LanguageUiState(),
                    Text2SpeechLanguage(
                        language = Language.RUSSIAN,
                        isAvailable = true,
                        missingData = false
                    ) to LanguageUiState(),
                    Text2SpeechLanguage(
                        language = Language.DUTCH,
                        isAvailable = true,
                        missingData = true
                    ) to LanguageUiState(),
                    Text2SpeechLanguage(
                        language = Language.POLISH,
                        isAvailable = false,
                        missingData = false
                    ) to LanguageUiState(),
                ),
                databases = listOf(
                    DatabaseItemUiState(
                        displayName = "Dictionary: English",
                        sizeBytes = 15 * 1024 * 1024,
                        deleteAction = {}
                    ),
                    DatabaseItemUiState(
                        displayName = "Translation: English → Русский",
                        sizeBytes = 8 * 1024 * 1024,
                        deleteAction = {}
                    )
                )
            )
        )
    }
}

@Preview
@Composable
private fun SettingsScreenPreviewWithMultipleNativeLanguages(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        SettingsScreenContent(
            state = SettingsUiState(
                isLoading = false,
                languages = mapOf(
                    Text2SpeechLanguage(
                        language = Language.GERMAN,
                        isAvailable = true,
                        missingData = false
                    ) to LanguageUiState(),
                    Text2SpeechLanguage(
                        language = Language.ENGLISH,
                        isAvailable = true,
                        missingData = false
                    ) to LanguageUiState()
                ),
                databases = listOf(
                    DatabaseItemUiState(
                        displayName = "Dictionary: Deutsch",
                        sizeBytes = 14 * 1024 * 1024,
                        deleteAction = {}
                    ),
                    DatabaseItemUiState(
                        displayName = "Translation: Deutsch → English",
                        sizeBytes = 8 * 1024 * 1024,
                        deleteAction = {}
                    )
                ),
                availableLanguages = listOf(
                    AvailableLanguageInfo(
                        language = Language.GERMAN,
                        dictionarySizeBytes = 14 * 1024 * 1024,
                        availableTranslations = listOf(
                            AvailableTranslationInfo(
                                targetLanguage = Language.ENGLISH,
                                sizeBytes = 8 * 1024 * 1024
                            ),
                            AvailableTranslationInfo(
                                targetLanguage = Language.SPANISH,
                                sizeBytes = 7 * 1024 * 1024
                            )
                        )
                    )
                )
            ),
            learningLanguage = Language.GERMAN,
            nativeLanguages = listOf(Language.ENGLISH, Language.SPANISH, Language.CZECH)
        )
    }
}

@Preview
@Composable
private fun SettingsScreenPreviewWithExpandedLanguage(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        SettingsScreenContent(
            state = SettingsUiState(
                isLoading = false,
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
                    ),
                    Text2SpeechLanguage(
                        language = Language.RUSSIAN,
                        isAvailable = true,
                        missingData = false
                    ) to LanguageUiState()
                ),
                databases = listOf(
                    DatabaseItemUiState(
                        displayName = "Dictionary: English",
                        sizeBytes = 15 * 1024 * 1024,
                        deleteAction = {}
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
                languages = mapOf(
                    Text2SpeechLanguage(
                        language = Language.ENGLISH,
                        isAvailable = true,
                        missingData = false
                    ) to LanguageUiState()
                ),
                databases = listOf(
                    DatabaseItemUiState(
                        displayName = "Dictionary: English",
                        sizeBytes = 15 * 1024 * 1024,
                        deleteAction = {}
                    )
                ),
                errorMessage = "Failed to load voices for this language"
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
                languages = mapOf(
                    Text2SpeechLanguage(
                        language = Language.ENGLISH,
                        isAvailable = true,
                        missingData = false
                    ) to LanguageUiState()
                ),
                databases = listOf(
                    DatabaseItemUiState(
                        displayName = "Dictionary: English",
                        sizeBytes = 15 * 1024 * 1024,
                        deleteAction = {}
                    ),
                    DatabaseItemUiState(
                        displayName = "Translation: English → Русский",
                        sizeBytes = 8 * 1024 * 1024,
                        deleteAction = {}
                    )
                ),
                deleteConfirmation = DeleteConfirmationState(
                    title = "Delete English Dictionary?",
                    message = "You can re-download it anytime.",
                    warning = "This will also remove 2 translations.",
                    onConfirm = {}
                )
            )
        )
    }
}

@Preview
@Composable
private fun SettingsScreenPreviewWithMixedDatabaseStates(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        SettingsScreenContent(
            state = SettingsUiState(
                isLoading = false,
                languages = emptyMap(),
                databases = listOf(
                    DatabaseItemUiState(
                        displayName = "Dictionary: English",
                        sizeBytes = 15 * 1024 * 1024,
                        deleteAction = {}
                    ),
                    DatabaseItemUiState(
                        displayName = "Translation: English → Русский",
                        sizeBytes = 8 * 1024 * 1024,
                        deleteAction = {}
                    )
                ),
                availableLanguages = listOf(
                    AvailableLanguageInfo(
                        language = Language.ENGLISH,
                        dictionarySizeBytes = 15 * 1024 * 1024,
                        availableTranslations = listOf(
                            AvailableTranslationInfo(
                                targetLanguage = Language.RUSSIAN,
                                sizeBytes = 8 * 1024 * 1024
                            ),
                            AvailableTranslationInfo(
                                targetLanguage = Language.POLISH,
                                sizeBytes = 7 * 1024 * 1024
                            )
                        )
                    ),
                    AvailableLanguageInfo(
                        language = Language.RUSSIAN,
                        dictionarySizeBytes = 12 * 1024 * 1024,
                        availableTranslations = listOf(
                            AvailableTranslationInfo(
                                targetLanguage = Language.ENGLISH,
                                sizeBytes = 8 * 1024 * 1024
                            )
                        )
                    ),
                    AvailableLanguageInfo(
                        language = Language.DUTCH,
                        dictionarySizeBytes = 10 * 1024 * 1024,
                        availableTranslations = emptyList()
                    )
                ),
                downloadingItems = mapOf(
                    "dict_ru" to DownloadProgress(5 * 1024 * 1024, 12 * 1024 * 1024),
                    "trans_en_pl" to DownloadProgress(2 * 1024 * 1024, 7 * 1024 * 1024)
                )
            )
        )
    }
}
