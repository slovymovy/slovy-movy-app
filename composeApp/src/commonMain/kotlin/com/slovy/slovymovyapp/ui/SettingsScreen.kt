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
import com.slovy.slovymovyapp.speech.*
import kotlinx.coroutines.launch
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.jetbrains.compose.ui.tooling.preview.PreviewParameter

data class LanguageUiState(
    val voices: List<Text2SpeechVoice> = emptyList(),
    val isExpanded: Boolean = false,
    val isLoadingVoices: Boolean = false
)

data class SettingsUiState(
    val languages: Map<Text2SpeechLanguage, LanguageUiState> = emptyMap(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val testingVoice: Text2SpeechVoice? = null,
    val ttsStatus: TTSStatus = TTSStatus.IDLE
)

// Test phrases for each language
private val TEST_PHRASES = mapOf(
    Language.ENGLISH to "Hello! This is a test of the text to speech system.",
    Language.RUSSIAN to "Привет! Это тест системы синтеза речи.",
    Language.POLISH to "Cześć! To jest test systemu syntezy mowy.",
    Language.DUTCH to "Hallo! Dit is een test van het tekst-naar-spraak systeem."
)

class SettingsViewModel(
    private val ttsManager: TextToSpeechManager
) : ViewModel() {

    var state by mutableStateOf(SettingsUiState())
        private set

    val scrollState = LazyListState()
    val snackbarHostState = SnackbarHostState()

    init {
        loadLanguages()
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

    fun reloadLanguages() {
        loadLanguages()
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
                updateLanguageState(language) {
                    it.copy(voices = voices, isLoadingVoices = false)
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
            return
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
    // Reload languages when returning from system settings
    LifecycleResumeEffect(Unit) {
        viewModel.reloadLanguages()
        onPauseOrDispose { }
    }

    SettingsScreenContent(
        state = viewModel.state,
        scrollState = viewModel.scrollState,
        snackbarHostState = viewModel.snackbarHostState,
        onLanguageExpand = { viewModel.toggleLanguageExpansion(it) },
        onTestVoice = { voice -> viewModel.testVoice(voice) },
        onOpenSettings = { viewModel.openSystemSettings() },
        onDismissError = { viewModel.dismissError() },
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
    onOpenSettings: () -> Unit = {},
    onDismissError: () -> Unit = {},
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
                    title = { Text("Text-to-Speech Settings") }
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

                    state.languages.isEmpty() -> {
                        EmptySettingsState(
                            onOpenSettings = onOpenSettings,
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

                            items(state.languages.entries.toList()) { e ->
                                LanguageCard(
                                    language = e.key,
                                    languageState = e.value,
                                    onExpand = { onLanguageExpand(e.key) },
                                    onTestVoice = { voice -> onTestVoice(voice) },
                                    testingVoice = state.testingVoice
                                )
                            }
                        }
                    }
                }
            }
        }
    }
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
                                isTesting = (testingVoice == voice)
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
    isTesting: Boolean = false
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
                    if (!voice.networkConnectionRequired) {
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
            state = SettingsUiState(isLoading = false, languages = emptyMap())
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
                                networkConnectionRequired = true
                            ),
                            Text2SpeechVoice(
                                id = "en-us-x-sfg#male_1-local",
                                name = "Male 1",
                                language = Language.ENGLISH,
                                quality = VoiceQuality.GOOD,
                                networkConnectionRequired = true
                            ),
                            Text2SpeechVoice(
                                id = "en-us-x-tpf-network",
                                name = "Network Voice",
                                language = Language.ENGLISH,
                                quality = VoiceQuality.MEDIUM,
                                networkConnectionRequired = false
                            )
                        )
                    ),
                    Text2SpeechLanguage(
                        language = Language.RUSSIAN,
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
                    ) to
                            LanguageUiState()
                ),
                errorMessage = "Failed to load voices for this language"
            )
        )
    }
}
