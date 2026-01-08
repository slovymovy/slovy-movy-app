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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.Feedback
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewModelScope
import com.slovy.slovymovyapp.AppBuildConfig
import com.slovy.slovymovyapp.getPlatform
import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.remote.AvailableLanguageInfo
import com.slovy.slovymovyapp.data.remote.DataDbManager
import com.slovy.slovymovyapp.data.remote.DictionaryRepository
import com.slovy.slovymovyapp.data.remote.DownloadProgress
import com.slovy.slovymovyapp.speech.*
import kotlinx.coroutines.launch
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.jetbrains.compose.ui.tooling.preview.PreviewParameter

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
    val downloadingItems: Map<String, DownloadProgress> = emptyMap(),
    val isLoading: Boolean = true,
    val isLoadingAvailable: Boolean = false,
    val errorMessage: String? = null,
    val testingVoice: Text2SpeechVoice? = null,
    val ttsStatus: TTSStatus = TTSStatus.IDLE,
    val deleteConfirmation: DeleteConfirmationState? = null,
    val buildConfig: AppBuildConfig = AppBuildConfig("compile", 42, true, "com.slovy.slovymovyapp")
)

data class DeleteConfirmationState(
    val displayName: String,
    val additionalInfo: String? = null,
    val onConfirm: () -> Unit
)

// Test phrases for each language
private val TEST_PHRASES = mapOf(
    Language.ENGLISH to "Hello! This is a test of the text to speech system.",
    Language.RUSSIAN to "Привет! Это тест системы синтеза речи.",
    Language.POLISH to "Cześć! To jest test systemu syntezy mowy.",
    Language.DUTCH to "Hallo! Dit is een test van het tekst-naar-spraak systeem."
)

class SettingsViewModel(
    private val ttsManager: TextToSpeechManager,
    private val voiceFilterHelper: VoiceFilterHelper,
    private val dataDbManager: DataDbManager,
    private val dictionaryRepository: DictionaryRepository,
    buildConfig: AppBuildConfig
) : ViewModel() {

    var state by mutableStateOf(SettingsUiState(buildConfig = buildConfig))
        private set

    val scrollState = LazyListState()
    val snackbarHostState = SnackbarHostState()

    init {
        loadLanguages()
        loadDatabases()
        loadAvailableLanguages()
        setupTTSListeners()
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
                        is com.slovy.slovymovyapp.data.remote.DatabaseFileInfo.Dictionary -> {
                            val displayName = "Dictionary: ${fileInfo.language.selfName}"
                            // Count how many translations will be deleted along with the dictionary
                            val translationCount = files.count {
                                it is com.slovy.slovymovyapp.data.remote.DatabaseFileInfo.Translation &&
                                        it.sourceLanguage == fileInfo.language
                            }
                            val additionalInfo = if (translationCount > 0) {
                                "$translationCount translation${if (translationCount > 1) "s" else ""} will also be deleted"
                            } else null

                            DatabaseItemUiState(
                                displayName = displayName,
                                sizeBytes = fileInfo.sizeBytes,
                                deleteAction = {
                                    showDeleteConfirmation(displayName, additionalInfo) {
                                        deleteDictionary(fileInfo.language)
                                    }
                                }
                            )
                        }

                        is com.slovy.slovymovyapp.data.remote.DatabaseFileInfo.Translation -> {
                            val displayName =
                                "Translation: ${fileInfo.sourceLanguage.selfName} → ${fileInfo.targetLanguage.selfName}"
                            DatabaseItemUiState(
                                displayName = displayName,
                                sizeBytes = fileInfo.sizeBytes,
                                deleteAction = {
                                    showDeleteConfirmation(displayName) {
                                        deleteTranslation(
                                            fileInfo.sourceLanguage,
                                            fileInfo.targetLanguage
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
            } catch (e: Exception) {
                state = state.copy(
                    isLoadingAvailable = false,
                    errorMessage = "Failed to load available languages: ${e.message}"
                )
            }
        }
    }

    fun showDeleteConfirmation(displayName: String, additionalInfo: String? = null, onConfirm: () -> Unit) {
        state = state.copy(
            deleteConfirmation = DeleteConfirmationState(
                displayName = displayName,
                additionalInfo = additionalInfo,
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

    private fun deleteDictionary(language: Language) {
        viewModelScope.launch {
            try {
                dataDbManager.deleteDictionary(language)
                dictionaryRepository.clearSenseCache()
                reloadSettings()
                snackbarHostState.showSnackbar("Database deleted successfully")
            } catch (e: Exception) {
                state = state.copy(errorMessage = "Failed to delete database: ${e.message}")
            }
        }
    }

    private fun deleteTranslation(src: Language, tgt: Language) {
        viewModelScope.launch {
            try {
                dataDbManager.deleteTranslation(src, tgt)
                dictionaryRepository.clearSenseCache()
                loadDatabases()
                snackbarHostState.showSnackbar("Database deleted successfully")
            } catch (e: Exception) {
                state = state.copy(errorMessage = "Failed to delete database: ${e.message}")
            }
        }
    }

    fun downloadDictionary(language: Language) {
        val downloadKey = "dict_${language.code}"
        viewModelScope.launch {
            try {
                dataDbManager.ensureDictionary(
                    lang = language,
                    onProgress = { progress ->
                        state = state.copy(
                            downloadingItems = state.downloadingItems + (downloadKey to progress)
                        )
                    }
                )
                // Remove from downloading items and reload databases
                state = state.copy(
                    downloadingItems = state.downloadingItems - downloadKey
                )
                dictionaryRepository.clearSenseCache()
                reloadSettings()
                snackbarHostState.showSnackbar("Dictionary downloaded successfully")
            } catch (e: Exception) {
                state = state.copy(
                    downloadingItems = state.downloadingItems - downloadKey,
                    errorMessage = "Failed to download dictionary: ${e.message}"
                )
            }
        }
    }

    fun downloadTranslation(sourceLanguage: Language, targetLanguage: Language) {
        val downloadKey = "trans_${sourceLanguage.code}_${targetLanguage.code}"
        viewModelScope.launch {
            try {
                dataDbManager.ensureTranslation(
                    src = sourceLanguage,
                    tgt = targetLanguage,
                    onProgress = { progress ->
                        state = state.copy(
                            downloadingItems = state.downloadingItems + (downloadKey to progress)
                        )
                    }
                )
                // Remove from downloading items and reload databases
                state = state.copy(
                    downloadingItems = state.downloadingItems - downloadKey
                )
                dictionaryRepository.clearSenseCache()
                loadDatabases()
                snackbarHostState.showSnackbar("Translation downloaded successfully")
            } catch (e: Exception) {
                state = state.copy(
                    downloadingItems = state.downloadingItems - downloadKey,
                    errorMessage = "Failed to download translation: ${e.message}"
                )
            }
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
                val downloadedLanguages = downloadedDatabases.filterIsInstance<com.slovy.slovymovyapp.data.remote.DatabaseFileInfo.Dictionary>()
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
            } catch (e: Exception) {
                state = state.copy(
                    isLoading = false,
                    errorMessage = "Failed to load languages: ${e.message}"
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

    override fun onCleared() {
        super.onCleared()
        ttsManager.stop()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
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

    SettingsScreenContent(
        state = viewModel.state,
        scrollState = viewModel.scrollState,
        snackbarHostState = viewModel.snackbarHostState,
        onLanguageExpand = { viewModel.toggleLanguageExpansion(it) },
        onTestVoice = { voice -> viewModel.testVoice(voice) },
        onToggleVoiceEnabled = { language, voiceId -> viewModel.toggleVoiceEnabled(language, voiceId) },
        onOpenSettings = { viewModel.openSystemSettings() },
        onDismissError = { viewModel.dismissError() },
        onConfirmDelete = { viewModel.confirmDelete() },
        onDismissDeleteConfirmation = { viewModel.dismissDeleteConfirmation() },
        onDownloadDictionary = { language -> viewModel.downloadDictionary(language) },
        onDownloadTranslation = { src, tgt -> viewModel.downloadTranslation(src, tgt) },
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
    onLanguageExpand: (Text2SpeechLanguage) -> Unit = {},
    onTestVoice: (Text2SpeechVoice) -> Unit = { _ -> },
    onToggleVoiceEnabled: (Text2SpeechLanguage, String) -> Unit = { _, _ -> },
    onOpenSettings: () -> Unit = {},
    onDismissError: () -> Unit = {},
    onConfirmDelete: () -> Unit = {},
    onDismissDeleteConfirmation: () -> Unit = {},
    onDownloadDictionary: (Language) -> Unit = {},
    onDownloadTranslation: (Language, Language) -> Unit = { _, _ -> },
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

    var expandedDictionaries by rememberSaveable { mutableStateOf(setOf<String>()) }

    Surface(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("Settings") }
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
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Languages and Dictionaries
                            item {
                                Text(
                                    text = "Languages and Dictionaries",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }

                            if (state.isLoadingAvailable) {
                                item {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
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

                                items(downloaded) { langInfo ->
                                    val dictDb = state.databases.find {
                                        it.displayName == "Dictionary: ${langInfo.language.selfName}"
                                    }
                                    DictionaryCard(
                                        languageInfo = langInfo,
                                        dictionaryDb = dictDb,
                                        isDownloaded = true,
                                        isExpanded = expandedDictionaries.contains(langInfo.language.code),
                                        onExpand = {
                                            expandedDictionaries = if (expandedDictionaries.contains(langInfo.language.code)) {
                                                expandedDictionaries - langInfo.language.code
                                            } else {
                                                expandedDictionaries + langInfo.language.code
                                            }
                                        },
                                        onDownloadDictionary = { onDownloadDictionary(langInfo.language) },
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

                                items(available) { langInfo ->
                                    val dictDb = state.databases.find {
                                        it.displayName == "Dictionary: ${langInfo.language.selfName}"
                                    }
                                    DictionaryCard(
                                        languageInfo = langInfo,
                                        dictionaryDb = dictDb,
                                        isDownloaded = false,
                                        isExpanded = expandedDictionaries.contains(langInfo.language.code),
                                        onExpand = {
                                            expandedDictionaries = if (expandedDictionaries.contains(langInfo.language.code)) {
                                                expandedDictionaries - langInfo.language.code
                                            } else {
                                                expandedDictionaries + langInfo.language.code
                                            }
                                        },
                                        onDownloadDictionary = { onDownloadDictionary(langInfo.language) },
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
                                    Text(
                                        text = "Voice",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                                    )
                                }

                                items(state.languages.entries.toList()) { e ->
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
                                                .padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Download,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            }

                                            Spacer(modifier = Modifier.width(16.dp))

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "Download more voices",
                                                    style = MaterialTheme.typography.titleMedium,
                                                )
                                                val platform = getPlatform().name
                                                val voicesText = when {
                                                    platform.contains("Android", ignoreCase = true) -> "Open system settings"
                                                    platform.contains("iOS", ignoreCase = true) -> "Accessibility → Spoken Content → Voices"
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
                                    Text(
                                        text = "About",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                                    )
                                }

                                item {
                                    AboutSection(
                                        buildConfig = buildConfig
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
                displayName = confirmation.displayName,
                additionalInfo = confirmation.additionalInfo,
                onConfirm = onConfirmDelete,
                onDismiss = onDismissDeleteConfirmation
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
    onDeleteDictionary: () -> Unit,
    onDownloadTranslation: (Language) -> Unit,
    onDeleteTranslation: (Language) -> Unit,
    downloadedTranslations: List<DatabaseItemUiState>,
    downloadingItems: Map<String, DownloadProgress>
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
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isDownloaded) "I'm learning" else "Available",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "${languageInfo.language.flag} ${languageInfo.language.selfName}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    val sizeBytes = dictionaryDb?.sizeBytes ?: languageInfo.dictionarySizeBytes
                    if (sizeBytes != null) {
                        Text(
                            text = "${if (isDownloaded) "Downloaded dictionary" else "Dictionary"} ${formatFileSize(sizeBytes)}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                val dictDownloadKey = "dict_${languageInfo.language.code}"
                val dictDownloading = downloadingItems.containsKey(dictDownloadKey)
                val dictProgress = downloadingItems[dictDownloadKey]

                if (dictDownloading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        progress = { dictProgress?.percent?.toFloat()?.div(100f) ?: 0f }
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
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val (downloadedTrans, availableTrans) = languageInfo.availableTranslations.partition { translation ->
                        downloadedTranslations.any {
                            it.displayName == "Translation: ${languageInfo.language.selfName} → ${translation.targetLanguage.selfName}"
                        }
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
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${targetLanguage.flag} ${targetLanguage.selfName}",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = formatFileSize(sizeBytes),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (isDownloading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    progress = { downloadProgress?.percent?.toFloat()?.div(100f) ?: 0f }
                )
            } else if (isDownloaded) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else {
                IconButton(
                    onClick = onDownload,
                    modifier = Modifier.size(24.dp)
                ) {
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
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${language.language.selfName} voice",
                        style = MaterialTheme.typography.titleMedium,
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
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                } else if (languageState.voices.isEmpty()) {
                    Text(
                        text = "No voices available",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                    ) {
                        languageState.voices.forEachIndexed { index, voice ->
                            if (index > 0) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 8.dp),
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
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun AboutSection(
    buildConfig: AppBuildConfig
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            AboutItem(
                icon = Icons.Outlined.Feedback,
                title = "Send us feedback",
                subtitle = "We'd love to hear from you",
                onClick = {},
                iconBackground = MaterialTheme.colorScheme.primary,
                iconTint = MaterialTheme.colorScheme.onPrimary
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                thickness = 0.5.dp
            )
            AboutItem(
                icon = Icons.Outlined.Info,
                title = "Version",
                subtitle = buildConfig.versionName,
                onClick = {},
                iconBackground = MaterialTheme.colorScheme.primary,
                iconTint = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

@Composable
private fun AboutItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    iconTint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    iconBackground: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surface
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(iconBackground),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
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
    displayName: String,
    additionalInfo: String? = null,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Delete Data?")
        },
        text = {
            Column {
                Text("Are you sure you want to delete $displayName?")
                if (additionalInfo != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = additionalInfo,
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
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
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
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (voice.name != null) {
                Text(
                    text = voice.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                text = voice.id,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

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
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val qualityColor = when (voice.quality) {
                    VoiceQuality.BEST -> MaterialTheme.colorScheme.primary
                    VoiceQuality.GOOD -> androidx.compose.ui.graphics.Color(0xFF4CAF50) // Greenish
                    VoiceQuality.MEDIUM -> androidx.compose.ui.graphics.Color(0xFFFFA000) // Amber
                }
                Surface(
                    shape = CircleShape,
                    border = BorderStroke(1.dp, qualityColor.copy(alpha = 0.5f)),
                    color = androidx.compose.ui.graphics.Color.Transparent
                ) {
                    Text(
                        text = when (voice.quality) {
                            VoiceQuality.BEST -> "High"
                            VoiceQuality.GOOD -> "Good"
                            VoiceQuality.MEDIUM -> "Medium"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = qualityColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
                if (voice.networkConnectionRequired) {
                    Surface(
                        shape = CircleShape,
                        color = androidx.compose.ui.graphics.Color.Transparent
                    ) {
                        Text(
                            text = "Network required",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
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
                            com.slovy.slovymovyapp.data.remote.AvailableTranslationInfo(
                                targetLanguage = Language.RUSSIAN,
                                sizeBytes = 8 * 1024 * 1024
                            )
                        )
                    ),
                    AvailableLanguageInfo(
                        language = Language.RUSSIAN,
                        dictionarySizeBytes = 12 * 1024 * 1024,
                        availableTranslations = listOf(
                            com.slovy.slovymovyapp.data.remote.AvailableTranslationInfo(
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
                    displayName = "Dictionary: English",
                    additionalInfo = "2 translations will also be deleted",
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
                            com.slovy.slovymovyapp.data.remote.AvailableTranslationInfo(
                                targetLanguage = Language.RUSSIAN,
                                sizeBytes = 8 * 1024 * 1024
                            ),
                            com.slovy.slovymovyapp.data.remote.AvailableTranslationInfo(
                                targetLanguage = Language.POLISH,
                                sizeBytes = 7 * 1024 * 1024
                            )
                        )
                    ),
                    AvailableLanguageInfo(
                        language = Language.RUSSIAN,
                        dictionarySizeBytes = 12 * 1024 * 1024,
                        availableTranslations = listOf(
                            com.slovy.slovymovyapp.data.remote.AvailableTranslationInfo(
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
