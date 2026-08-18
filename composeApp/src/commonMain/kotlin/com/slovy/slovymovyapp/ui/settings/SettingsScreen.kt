package com.slovy.slovymovyapp.ui.settings

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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.remote.*
import com.slovy.slovymovyapp.i18n.resolve
import com.slovy.slovymovyapp.speech.*
import com.slovy.slovymovyapp.ui.components.SpinningProgressIndicator
import com.slovy.slovymovyapp.ui.theme.AppSpacing
import com.slovy.slovymovyapp.ui.theme.serifFontFamily
import org.jetbrains.compose.resources.stringResource
import slovymovyapp.composeapp.generated.resources.*
import com.slovy.slovymovyapp.ui.AppNavigationBar
import com.slovy.slovymovyapp.ui.AppScreen
import com.slovy.slovymovyapp.ui.DeleteConfirmationDialog
import com.slovy.slovymovyapp.ui.FeedbackDialog
import com.slovy.slovymovyapp.ui.LoadingIndicator
import com.slovy.slovymovyapp.ui.SectionHeader
import com.slovy.slovymovyapp.ui.developer.DeveloperOptionsCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    /**
     * Called when the user picks a translation language they do not have yet. The caller runs the
     * download flow, which is what stores the preference once the databases are in place.
     */
    onAddTranslationLanguage: (Language) -> Unit,
    onNavigateToSearch: () -> Unit = {},
    onNavigateToFavorites: () -> Unit = {},
    onNavigateToStats: () -> Unit = {},
    onNavigateToDeveloper: () -> Unit = {},
    hasFavoritesToReview: Boolean = false,
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
        onToggleTranslationLanguage = { language ->
            if (language in viewModel.state.translationLanguages) {
                viewModel.removeTranslationLanguage(language)
            } else {
                onAddTranslationLanguage(language)
            }
        },
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
        onVersionClick = { viewModel.onVersionClick() },
        onNavigateToSearch = onNavigateToSearch,
        onNavigateToFavorites = onNavigateToFavorites,
        onNavigateToStats = onNavigateToStats,
        onNavigateToDeveloper = onNavigateToDeveloper,
        hasFavoritesToReview = hasFavoritesToReview,
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
    onVersionClick: () -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    onNavigateToFavorites: () -> Unit = {},
    onNavigateToStats: () -> Unit = {},
    onNavigateToDeveloper: () -> Unit = {},
    hasFavoritesToReview: Boolean = false,
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
                    onNavigateToSettings = {},
                    hasFavoritesToReview = hasFavoritesToReview,
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
                                        SpinningProgressIndicator(modifier = Modifier.size(24.dp))
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
                                        onAcknowledgements = onAcknowledgements,
                                        onVersionClick = onVersionClick
                                    )
                                }
                            }

                            if (state.developerModeEnabled) {
                                item {
                                    SectionHeader(
                                        title = stringResource(Res.string.settings_section_developer),
                                        modifier = Modifier.padding(top = AppSpacing.sm)
                                    )
                                }
                                item {
                                    DeveloperOptionsCard(onClick = onNavigateToDeveloper)
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

        if (state.feedback.dialogVisible) {
            FeedbackDialog(
                title = stringResource(Res.string.feedback_dialog_title),
                commentPlaceholder = stringResource(Res.string.feedback_dialog_placeholder),
                commentLabel = stringResource(Res.string.feedback_dialog_comment_label),
                comment = state.feedback.comment,
                email = state.feedback.email,
                isSending = state.feedback.submitting,
                error = state.feedback.error?.resolve(),
                resultUrl = state.feedback.resultUrl,
                onCommentChange = onFeedbackCommentChange,
                onEmailChange = onFeedbackEmailChange,
                onDismiss = onDismissFeedback,
                onSend = onSubmitFeedback
            )
        }
    }
}

