package com.slovy.slovymovyapp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewModelScope
import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.remote.AvailableLanguageInfo
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
    val deleteConfirmation: DeleteConfirmationState? = null
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
    private val dataDbManager: com.slovy.slovymovyapp.data.remote.DataDbManager
) : ViewModel() {

    var state by mutableStateOf(SettingsUiState())
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

    private fun showDeleteConfirmation(displayName: String, additionalInfo: String? = null, onConfirm: () -> Unit) {
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
                loadDatabases()
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
                loadDatabases()
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
            if (state.languages.isEmpty()) {
                state = state.copy(isLoading = true, errorMessage = null)
            } else {
                state = state.copy(errorMessage = null)
            }
            try {
                val languages = ttsManager.getAvailableLanguages()
                state = state.copy(
                    languages = languages.associateWith { language ->
                        // Preserve existing state if language already exists
                        state.languages[language] ?: LanguageUiState()
                    },
                    isLoading = false
                )
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

    Surface(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
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
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Voices section header
                            item {
                                Text(
                                    text = "Text-to-Speech Voices",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            }

                            item {
                                ElevatedCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.elevatedCardColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer
                                    ),
                                    elevation = CardDefaults.elevatedCardElevation(
                                        defaultElevation = 2.dp
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(20.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "System TTS Settings",
                                                style = MaterialTheme.typography.titleLarge,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "Manage voices and download languages",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        FilledTonalButton(
                                            onClick = onOpenSettings,
                                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Settings,
                                                contentDescription = "Open Settings",
                                                modifier = Modifier.size(30.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Open", style = MaterialTheme.typography.labelLarge)
                                        }
                                    }
                                }
                            }

                            if (state.languages.isEmpty()) {
                                item {
                                    Text(
                                        text = "No voices available. Download language data from system settings to enable text-to-speech.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                items(state.languages.entries.toList()) { e ->
                                    LanguageCard(
                                        language = e.key,
                                        languageState = e.value,
                                        onExpand = { onLanguageExpand(e.key) },
                                        onTestVoice = { voice -> onTestVoice(voice) },
                                        onToggleVoiceEnabled = { voiceId -> onToggleVoiceEnabled(e.key, voiceId) },
                                        testingVoice = state.testingVoice
                                    )
                                }
                            }

                            // Databases section
                            item {
                                Text(
                                    text = "Databases",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
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
                                items(state.availableLanguages) { langInfo ->
                                    val dictDb = state.databases.find {
                                        it.displayName == "Dictionary: ${langInfo.language.selfName}"
                                    }
                                    DatabaseLanguageCard(
                                        languageInfo = langInfo,
                                        dictionaryDb = dictDb,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeleteConfirmationDialog(
    displayName: String,
    additionalInfo: String? = null,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Delete Database?")
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
private fun EmptySettingsState(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Settings,
            contentDescription = "No languages",
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No languages available",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Download language data from system settings to enable text-to-speech",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onOpenSettings) {
            Text("Open System Settings")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LanguageCard(
    language: Text2SpeechLanguage,
    languageState: LanguageUiState,
    onExpand: () -> Unit,
    onTestVoice: (Text2SpeechVoice) -> Unit,
    onToggleVoiceEnabled: (String) -> Unit,
    testingVoice: Text2SpeechVoice? = null
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = 1.dp,
            pressedElevation = 3.dp
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Language header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onExpand)
                    .padding(18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = language.language.selfName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (language.isAvailable) {
                            Icon(
                                imageVector = Icons.Outlined.CheckCircle,
                                contentDescription = "Available",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.Error,
                                contentDescription = "Not available",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = language.language.selfName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (language.missingData) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Download,
                                contentDescription = "Needs download",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = "Needs download",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Expanded voices section
            if (languageState.isExpanded) {
                HorizontalDivider(thickness = 1.dp)
                if (languageState.isLoadingVoices) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    }
                } else if (languageState.voices.isEmpty()) {
                    Text(
                        text = "No voices available for this language",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(20.dp)
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Available voices",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        languageState.voices.forEach { voice ->
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
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun VoiceItem(
    voice: Text2SpeechVoice,
    onTest: () -> Unit = {},
    isTesting: Boolean = false,
    isEnabled: Boolean = true,
    onToggleEnabled: () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
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
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = {
                            Text(
                                text = when (voice.quality) {
                                    VoiceQuality.BEST -> "Best"
                                    VoiceQuality.GOOD -> "Good"
                                    VoiceQuality.MEDIUM -> "Medium"
                                },
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = when (voice.quality) {
                                VoiceQuality.BEST -> MaterialTheme.colorScheme.primaryContainer
                                VoiceQuality.GOOD -> MaterialTheme.colorScheme.secondaryContainer
                                VoiceQuality.MEDIUM -> MaterialTheme.colorScheme.surfaceVariant
                            },
                            labelColor = when (voice.quality) {
                                VoiceQuality.BEST -> MaterialTheme.colorScheme.onPrimaryContainer
                                VoiceQuality.GOOD -> MaterialTheme.colorScheme.onSecondaryContainer
                                VoiceQuality.MEDIUM -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            disabledContainerColor = when (voice.quality) {
                                VoiceQuality.BEST -> MaterialTheme.colorScheme.primaryContainer
                                VoiceQuality.GOOD -> MaterialTheme.colorScheme.secondaryContainer
                                VoiceQuality.MEDIUM -> MaterialTheme.colorScheme.surfaceVariant
                            },
                            disabledLabelColor = when (voice.quality) {
                                VoiceQuality.BEST -> MaterialTheme.colorScheme.onPrimaryContainer
                                VoiceQuality.GOOD -> MaterialTheme.colorScheme.onSecondaryContainer
                                VoiceQuality.MEDIUM -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    )
                    if (voice.networkConnectionRequired) {
                        AssistChip(
                            onClick = {},
                            enabled = false,
                            label = {
                                Text(
                                    text = "Network required",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                labelColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                disabledContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                disabledLabelColor = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))
            Checkbox(
                checked = isEnabled,
                onCheckedChange = { onToggleEnabled() }
            )
            Spacer(modifier = Modifier.width(4.dp))
            Surface(
                shape = MaterialTheme.shapes.small,
                color = if (isTesting) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (isTesting) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                IconButton(onClick = onTest) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Test voice"
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DatabaseLanguageCard(
    languageInfo: AvailableLanguageInfo,
    dictionaryDb: DatabaseItemUiState?,
    onDownloadDictionary: () -> Unit,
    onDeleteDictionary: () -> Unit,
    onDownloadTranslation: (Language) -> Unit,
    onDeleteTranslation: (Language) -> Unit,
    downloadedTranslations: List<DatabaseItemUiState>,
    downloadingItems: Map<String, DownloadProgress>
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = 1.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Dictionary section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Dictionary: ${languageInfo.language.selfName}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    val sizeBytes = dictionaryDb?.sizeBytes ?: languageInfo.dictionarySizeBytes
                    if (sizeBytes != null) {
                        Text(
                            text = formatFileSize(sizeBytes),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))

                val dictDownloadKey = "dict_${languageInfo.language.code}"
                val dictDownloading = downloadingItems.containsKey(dictDownloadKey)
                val dictProgress = downloadingItems[dictDownloadKey]

                if (dictionaryDb != null) {
                    // Downloaded - show delete button
                    FilledTonalButton(
                        onClick = onDeleteDictionary,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Text("Delete")
                    }
                } else if (dictDownloading) {
                    // Downloading - show progress
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        if (dictProgress?.percent != null && dictProgress.percent >= 0) {
                            Text(
                                text = "${dictProgress.percent}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    // Not downloaded - show download button
                    FilledTonalButton(onClick = onDownloadDictionary) {
                        Icon(
                            imageVector = Icons.Filled.Download,
                            contentDescription = "Download",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Download")
                    }
                }
            }

            // Translations section
            if (languageInfo.availableTranslations.isNotEmpty()) {
                HorizontalDivider(thickness = 1.dp)
                Text(
                    text = "Translations:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                languageInfo.availableTranslations.forEach { translation ->
                    val transDownloadKey = "trans_${languageInfo.language.code}_${translation.targetLanguage.code}"
                    val transDownloading = downloadingItems.containsKey(transDownloadKey)
                    val transProgress = downloadingItems[transDownloadKey]
                    val transDb = downloadedTranslations.find {
                        it.displayName == "Translation: ${languageInfo.language.selfName} → ${translation.targetLanguage.selfName}"
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "→ ${translation.targetLanguage.selfName}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            val transSizeBytes = transDb?.sizeBytes ?: translation.sizeBytes
                            Text(
                                text = formatFileSize(transSizeBytes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))

                        if (transDb != null) {
                            // Downloaded - show delete button
                            FilledTonalButton(
                                onClick = { onDeleteTranslation(translation.targetLanguage) },
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text("Delete", style = MaterialTheme.typography.labelMedium)
                            }
                        } else if (transDownloading) {
                            // Downloading - show progress
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                                if (transProgress?.percent != null && transProgress.percent >= 0) {
                                    Text(
                                        text = "${transProgress.percent}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            // Not downloaded - show download button (disabled if dictionary not downloaded)
                            FilledTonalButton(
                                onClick = { onDownloadTranslation(translation.targetLanguage) },
                                enabled = dictionaryDb != null, // Only allow download if dictionary is downloaded
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Download,
                                    contentDescription = "Download",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Download", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
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
