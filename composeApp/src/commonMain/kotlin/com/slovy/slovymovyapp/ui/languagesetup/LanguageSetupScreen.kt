package com.slovy.slovymovyapp.ui.languagesetup

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.slovy.slovymovyapp.ui.components.SpinningProgressIndicator
import com.slovy.slovymovyapp.ui.theme.serifFontFamily
import com.slovy.slovymovyapp.ui.theme.uiItalic
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.slovy.slovymovyapp.analytics.Analytics
import com.slovy.slovymovyapp.analytics.AnalyticsEvent
import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.i18n.resolve
import com.slovy.slovymovyapp.ui.theme.AppSpacing
import org.jetbrains.compose.resources.stringResource
import slovymovyapp.composeapp.generated.resources.*
import com.slovy.slovymovyapp.ui.OnboardingHeader
import com.slovy.slovymovyapp.ui.LanguageRequestDialog

@Composable
fun LanguageSetupScreen(
    viewModel: LanguageSetupViewModel,
    onNext: (learning: Language, native: List<Language>) -> Unit
) {
    LanguageSetupScreenContent(
        state = viewModel.state,
        scrollState = viewModel.scrollState,
        onLearningLanguageSelected = viewModel::selectLearningLanguage,
        onNativeLanguageToggled = viewModel::toggleNativeLanguage,
        onNoTranslationChanged = viewModel::setNoTranslationSelected,
        onOpenLanguageRequest = viewModel::openLanguageRequestDialog,
        onDismissLanguageRequest = viewModel::dismissLanguageRequestDialog,
        onLanguageRequestLearnChange = viewModel::updateLanguageRequestLearnLanguage,
        onLanguageRequestTranslateChange = viewModel::updateLanguageRequestTranslateLanguage,
        onSubmitLanguageRequest = viewModel::submitLanguageRequest,
        onNext = {
            val learning = viewModel.state.learningLanguage
            val native = viewModel.state.nativeLanguages.sortedBy { it.selfName }
            when {
                learning != null && (native.isNotEmpty() || viewModel.state.noTranslationSelected) -> {
                    native.forEach { language ->
                        Analytics.logEvent(AnalyticsEvent.LANG_TO_TRANSLATE_SELECTED, mapOf("lang" to language.code))
                    }
                    if (viewModel.state.noTranslationSelected) {
                        Analytics.logEvent(AnalyticsEvent.LANG_TO_TRANSLATE_SELECTED, mapOf("lang" to "none"))
                    }
                    onNext(learning, native)
                    Analytics.logEvent(AnalyticsEvent.LANG_TO_LEARN_SELECTED, mapOf("lang" to learning.code))
                }

                learning == null -> {
                    Analytics.logEvent(AnalyticsEvent.LANG_TO_LEARN_NOT_SELECTED)
                }
            }
        },
        onRetry = viewModel::retry
    )
}

@Composable
fun LanguageSetupScreenContent(
    state: LanguageSetupUiState,
    scrollState: ScrollState = ScrollState(0),
    onLearningLanguageSelected: (Language) -> Unit = {},
    onNativeLanguageToggled: (Language) -> Unit = {},
    onNoTranslationChanged: (Boolean) -> Unit = {},
    onOpenLanguageRequest: () -> Unit = {},
    onDismissLanguageRequest: () -> Unit = {},
    onLanguageRequestLearnChange: (String) -> Unit = {},
    onLanguageRequestTranslateChange: (String) -> Unit = {},
    onSubmitLanguageRequest: () -> Unit = {},
    onNext: () -> Unit = {},
    onRetry: () -> Unit = {}
) {
    val canGoNext = state.learningLanguage != null && (state.nativeLanguages.isNotEmpty() || state.noTranslationSelected)
    val translateIntoEnabled = state.learningLanguage != null
    val translationLanguages = Language.translationTargets
        .filter { it != state.learningLanguage }
        .sortedBy { it.selfName }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
        if (state.isLoading) {
            Box(
                Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                SpinningProgressIndicator()
            }
        } else if (state.errorMessage != null) {
            Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(AppSpacing.xl),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    state.errorMessage.resolve(),
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
                Button(onClick = onRetry, modifier = Modifier.padding(top = AppSpacing.lg)) {
                    Text(stringResource(Res.string.common_retry))
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = AppSpacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(AppSpacing.xxxl))

                OnboardingHeader(
                    title = stringResource(Res.string.language_setup_title),
                    subtitle = stringResource(Res.string.language_setup_subtitle)
                )

                Spacer(Modifier.height(AppSpacing.xxl))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.xl)
                ) {
                    LanguageSetupSection(
                        number = 1,
                        label = stringResource(Res.string.language_setup_learning_label),
                        state = if (state.learningLanguage == null) SectionVisualState.Active else SectionVisualState.Done,
                        lockedHint = null
                    ) {
                        LanguageSetupListCard(enabled = true) {
                            state.availableLanguages.forEachIndexed { index, language ->
                                LanguageSetupRow(
                                    language = language,
                                    selected = language == state.learningLanguage,
                                    enabled = true,
                                    multiSelect = false,
                                    showDivider = index > 0,
                                    onClick = { onLearningLanguageSelected(language) }
                                )
                            }
                        }
                    }

                    LanguageSetupSection(
                        number = 2,
                        label = stringResource(Res.string.language_setup_translate_into),
                        state = when {
                            !translateIntoEnabled -> SectionVisualState.Locked
                            state.nativeLanguages.isEmpty() && !state.noTranslationSelected -> SectionVisualState.Active
                            else -> SectionVisualState.Done
                        },
                        lockedHint = if (translateIntoEnabled) {
                            null
                        } else {
                            stringResource(Res.string.language_setup_choose_learning_first)
                        }
                    ) {
                        LanguageSetupListCard(enabled = translateIntoEnabled) {
                            translationLanguages.forEachIndexed { index, language ->
                                LanguageSetupRow(
                                    language = language,
                                    selected = translateIntoEnabled && language in state.nativeLanguages,
                                    enabled = translateIntoEnabled,
                                    multiSelect = true,
                                    showDivider = index > 0,
                                    onClick = { onNativeLanguageToggled(language) }
                                )
                            }
                            LanguageSetupNoTranslationRow(
                                selected = translateIntoEnabled && state.noTranslationSelected,
                                enabled = translateIntoEnabled,
                                showDivider = translationLanguages.isNotEmpty(),
                                onCheckedChange = onNoTranslationChanged
                            )
                        }
                    }

                    LanguageRequestLink(onClick = onOpenLanguageRequest)
                }

                Spacer(Modifier.height(AppSpacing.lg))

                Text(
                    text = stringResource(Res.string.language_setup_update_anytime),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = MaterialTheme.serifFontFamily,
                        fontStyle = MaterialTheme.uiItalic
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(AppSpacing.lg))

                Button(
                    onClick = onNext,
                    enabled = canGoNext,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        stringResource(Res.string.common_next),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }

                Spacer(Modifier.height(AppSpacing.xxl))
            }
        }

        if (state.languageRequestDialogVisible) {
            LanguageRequestDialog(
                learnLanguage = state.languageRequestLearnLanguage,
                translateLanguage = state.languageRequestTranslateLanguage,
                isSending = state.languageRequestSubmitting,
                error = state.languageRequestError?.resolve(),
                discussionUrl = state.languageRequestDiscussionUrl,
                onLearnLanguageChange = onLanguageRequestLearnChange,
                onTranslateLanguageChange = onLanguageRequestTranslateChange,
                onDismiss = onDismissLanguageRequest,
                onSend = onSubmitLanguageRequest
            )
        }
    }
}

