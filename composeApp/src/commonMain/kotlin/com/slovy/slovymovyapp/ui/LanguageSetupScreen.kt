package com.slovy.slovymovyapp.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import com.slovy.slovymovyapp.ui.components.SpinningProgressIndicator
import com.slovy.slovymovyapp.ui.theme.serifFontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slovy.slovymovyapp.analytics.Analytics
import com.slovy.slovymovyapp.analytics.AnalyticsEvent
import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.remote.DataDbManager
import com.slovy.slovymovyapp.data.remote.DictionaryClient
import com.slovy.slovymovyapp.data.remote.NetworkErrorClassifier
import com.slovy.slovymovyapp.i18n.UiText
import com.slovy.slovymovyapp.i18n.resolve
import com.slovy.slovymovyapp.ui.theme.AppSpacing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import slovymovyapp.composeapp.generated.resources.*

data class LanguageSetupUiState(
    val isLoading: Boolean = true,
    val availableLanguages: List<Language> = emptyList(),
    val learningLanguage: Language? = null,
    val nativeLanguages: Set<Language> = emptySet(),
    val noTranslationSelected: Boolean = false,
    val errorMessage: String? = null,
    val languageRequestDialogVisible: Boolean = false,
    val languageRequestLearnLanguage: String = "",
    val languageRequestTranslateLanguage: String = "",
    val languageRequestSubmitting: Boolean = false,
    val languageRequestError: UiText? = null,
    val languageRequestDiscussionUrl: String? = null
)

class LanguageSetupViewModel(
    private val dataDbManager: DataDbManager,
    private val dictionaryClient: DictionaryClient,
    initialLearningLanguage: Language? = null,
    initialNativeLanguages: Set<Language> = emptySet()
) : ViewModel() {
    private var languageRequestJob: Job? = null

    var state by mutableStateOf(
        LanguageSetupUiState(
            learningLanguage = initialLearningLanguage,
            nativeLanguages = initialNativeLanguages - setOfNotNull(initialLearningLanguage),
            // Routing only reaches setup with a learning language after LANGUAGE was persisted,
            // so an empty set here means a saved "no translation" choice (LANGUAGE=[]), not a
            // fresh user who skipped the step.
            noTranslationSelected = initialLearningLanguage != null && initialNativeLanguages.isEmpty()
        )
    )
        private set

    val scrollState = ScrollState(0)

    init {
        loadAvailableLanguages()
    }

    private fun loadAvailableLanguages() {
        viewModelScope.launch {
            state = state.copy(isLoading = true, errorMessage = null)
            try {
                val available = dataDbManager.fetchAvailableLanguages()
                    .filter { it.dictionarySizeBytes != null }
                    .map { it.language }

                state = state.copy(
                    isLoading = false,
                    availableLanguages = available.sortedBy { it.selfName }
                )
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

    fun selectLearningLanguage(language: Language) {
        if (state.learningLanguage == language) return
        state = state.copy(
            learningLanguage = language,
            nativeLanguages = state.nativeLanguages.filter { it != language }.toSet(),
            noTranslationSelected = false
        )
    }

    fun toggleNativeLanguage(language: Language) {
        val current = state.nativeLanguages
        state = state.copy(
            nativeLanguages = if (language in current) {
                current - language
            } else {
                current + language
            },
            noTranslationSelected = false
        )
    }

    fun setNoTranslationSelected(selected: Boolean) {
        state = if (selected) {
            state.copy(nativeLanguages = emptySet(), noTranslationSelected = true)
        } else {
            state.copy(noTranslationSelected = false)
        }
    }

    fun openLanguageRequestDialog() {
        Analytics.logEvent(AnalyticsEvent.LANGUAGE_REQUEST_OPEN)
        cancelLanguageRequestJob()
        state = state.copy(
            languageRequestDialogVisible = true,
            languageRequestLearnLanguage = "",
            languageRequestTranslateLanguage = "",
            languageRequestSubmitting = false,
            languageRequestError = null,
            languageRequestDiscussionUrl = null
        )
    }

    fun dismissLanguageRequestDialog() {
        cancelLanguageRequestJob()
        state = state.copy(
            languageRequestDialogVisible = false,
            languageRequestLearnLanguage = "",
            languageRequestTranslateLanguage = "",
            languageRequestSubmitting = false,
            languageRequestError = null,
            languageRequestDiscussionUrl = null
        )
    }

    fun updateLanguageRequestLearnLanguage(language: String) {
        state = state.copy(languageRequestLearnLanguage = language, languageRequestError = null)
    }

    fun updateLanguageRequestTranslateLanguage(language: String) {
        state = state.copy(languageRequestTranslateLanguage = language, languageRequestError = null)
    }

    fun submitLanguageRequest() {
        if (state.languageRequestSubmitting) return

        val learn = state.languageRequestLearnLanguage.trim()
        val translate = state.languageRequestTranslateLanguage.trim()
        if (learn.isEmpty() && translate.isEmpty()) {
            state = state.copy(
                languageRequestError = UiText.Resource(Res.string.language_setup_request_language_required)
            )
            return
        }

        val comment = buildLanguageRequestComment(learn = learn, translate = translate)
        state = state.copy(languageRequestSubmitting = true, languageRequestError = null)
        cancelLanguageRequestJob()
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                val response = dictionaryClient.sendGeneralFeedback(comment = comment)
                if (isCurrentLanguageRequestJob()) {
                    // Only which slots were filled, never what the user typed: the language names
                    // themselves are free text and already reach maintainers via the discussion.
                    Analytics.logEvent(
                        AnalyticsEvent.LANGUAGE_REQUEST_SENT,
                        mapOf("slots" to languageRequestSlots(learn = learn, translate = translate))
                    )
                    if (state.languageRequestDialogVisible) {
                        state = state.copy(
                            languageRequestSubmitting = false,
                            languageRequestError = null,
                            languageRequestDiscussionUrl = response.discussionUrl
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (isCurrentLanguageRequestJob() && state.languageRequestDialogVisible) {
                    state = state.copy(
                        languageRequestSubmitting = false,
                        languageRequestError = UiText.Plain(NetworkErrorClassifier.userMessage(e))
                    )
                }
            } finally {
                if (isCurrentLanguageRequestJob()) {
                    languageRequestJob = null
                }
            }
        }
        languageRequestJob = job
        job.start()
    }

    private fun cancelLanguageRequestJob() {
        languageRequestJob?.cancel()
        languageRequestJob = null
    }

    private suspend fun isCurrentLanguageRequestJob(): Boolean = languageRequestJob == currentCoroutineContext()[Job]

    fun retry() {
        loadAvailableLanguages()
    }

    /**
     * Builds the maintainer-facing feedback body. The labels stay in English on purpose: the
     * discussion is read by maintainers, not by the requester, so a fixed shape keeps requests
     * greppable no matter which UI locale produced them. Blank slots are omitted rather than
     * sent as empty lines.
     */
    private fun languageRequestSlots(learn: String, translate: String): String = when {
        learn.isNotEmpty() && translate.isNotEmpty() -> "both"
        learn.isNotEmpty() -> "learn"
        else -> "translate"
    }

    private fun buildLanguageRequestComment(learn: String, translate: String): String {
        return buildString {
            append(LANGUAGE_REQUEST_PREFIX)
            if (learn.isNotEmpty()) {
                append("\n")
                append(LEARN_LABEL)
                append(learn)
            }
            if (translate.isNotEmpty()) {
                append("\n")
                append(TRANSLATE_LABEL)
                append(translate)
            }
        }
    }

    private companion object {
        // Language requests share the generic /feedback endpoint; the prefix is what lets
        // maintainers tell them apart from general feedback in the GitHub Feedback category.
        const val LANGUAGE_REQUEST_PREFIX = "[Language request]"
        const val LEARN_LABEL = "Language to learn: "
        const val TRANSLATE_LABEL = "Language to translate into: "
    }
}

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
    val translationLanguages = Language.entries
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
                Text(state.errorMessage, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
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
                        fontStyle = FontStyle.Italic
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

private enum class SectionVisualState {
    Active,
    Done,
    Locked
}

@Composable
private fun LanguageSetupSection(
    number: Int,
    label: String,
    state: SectionVisualState,
    lockedHint: String?,
    content: @Composable () -> Unit
) {
    val active = state != SectionVisualState.Locked

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = AppSpacing.xs, bottom = AppSpacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .background(
                        color = if (active) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.background
                        },
                        shape = CircleShape
                    )
                    .border(
                        width = 1.dp,
                        color = if (active) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                        shape = CircleShape
                    )
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (state == SectionVisualState.Done) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = stringResource(Res.string.language_setup_step_completed, number),
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(
                        text = number.toString(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = if (active) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            Spacer(Modifier.width(AppSpacing.sm))

            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = if (active) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )

            if (lockedHint != null) {
                Spacer(Modifier.weight(1f))
                Text(
                    text = lockedHint,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = MaterialTheme.serifFontFamily,
                        fontStyle = FontStyle.Italic
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End
                )
            }
        }

        content()
    }
}

@Composable
private fun LanguageSetupListCard(
    enabled: Boolean,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.5f),
        shape = RoundedCornerShape(16.dp),
        color = if (enabled) {
            MaterialTheme.colorScheme.surface
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (enabled) {
                MaterialTheme.colorScheme.outlineVariant
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            }
        )
    ) {
        Column(content = content)
    }
}

@Composable
private fun LanguageSetupRow(
    language: Language,
    selected: Boolean,
    enabled: Boolean,
    multiSelect: Boolean,
    showDivider: Boolean,
    onClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (showDivider) {
            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (multiSelect) {
                        Modifier.toggleable(
                            value = selected,
                            enabled = enabled,
                            role = Role.Checkbox,
                            onValueChange = { onClick() }
                        )
                    } else {
                        Modifier.selectable(
                            selected = selected,
                            enabled = enabled,
                            role = Role.RadioButton,
                            onClick = onClick
                        )
                    }
                )
                .semantics(mergeDescendants = true) {}
                .background(
                    if (selected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                    } else {
                        Color.Transparent
                    }
                )
                .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = language.flag,
                    modifier = Modifier.width(28.dp),
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(Modifier.width(AppSpacing.md))

                Text(
                    text = language.selfName,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            SelectionIndicator(
                selected = selected,
                multiSelect = multiSelect,
                enabled = enabled
            )
        }
    }
}

@Composable
private fun LanguageSetupNoTranslationRow(
    selected: Boolean,
    enabled: Boolean,
    showDivider: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    LanguageSetupOptionRow(
        title = stringResource(Res.string.language_setup_no_translation_title),
        subtitle = stringResource(Res.string.language_setup_no_translation_subtitle),
        accessibilityDescription = stringResource(Res.string.language_setup_no_translation_accessibility),
        leadingIcon = Icons.Outlined.Public,
        selected = selected,
        enabled = enabled,
        showDivider = showDivider,
        onCheckedChange = onCheckedChange
    )
}

@Composable
private fun LanguageSetupOptionRow(
    title: String,
    subtitle: String?,
    accessibilityDescription: String?,
    leadingIcon: ImageVector,
    selected: Boolean,
    enabled: Boolean,
    showDivider: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (showDivider) {
            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(
                    value = selected,
                    enabled = enabled,
                    role = Role.Checkbox,
                    onValueChange = onCheckedChange
                )
                .semantics(mergeDescendants = true) {
                    if (accessibilityDescription != null) {
                        contentDescription = accessibilityDescription
                    }
                }
                .background(
                    if (selected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                    } else {
                        Color.Transparent
                    }
                )
                .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.width(28.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.width(AppSpacing.md))

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = MaterialTheme.serifFontFamily,
                                fontStyle = FontStyle.Italic
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            SelectionIndicator(
                selected = selected,
                multiSelect = true,
                enabled = enabled
            )
        }
    }
}

@Composable
private fun LanguageRequestLink(onClick: () -> Unit) {
    val linkTag = "language_request"
    val beforeText = stringResource(Res.string.language_setup_request_language_before)
    val linkText = stringResource(Res.string.language_setup_request_language_link)
    val afterText = stringResource(Res.string.language_setup_request_language_after)
    val accentColor = MaterialTheme.colorScheme.primary
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val currentOnClick by rememberUpdatedState(onClick)
    val text = remember(beforeText, linkText, afterText, accentColor) {
        buildAnnotatedString {
            append(beforeText)
            append(" ")
            val link = LinkAnnotation.Clickable(
                tag = linkTag,
                linkInteractionListener = { currentOnClick() }
            )
            withLink(link) {
                withStyle(SpanStyle(color = accentColor, textDecoration = TextDecoration.Underline)) {
                    append(linkText)
                }
            }
            append(afterText)
        }
    }

    Text(
        text = text,
        modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.xs),
        style = MaterialTheme.typography.bodyMedium.copy(
            color = textColor,
            fontFamily = MaterialTheme.serifFontFamily,
            fontStyle = FontStyle.Italic,
            textAlign = TextAlign.Center
        )
    )
}

@Composable
private fun SelectionIndicator(
    selected: Boolean,
    multiSelect: Boolean,
    enabled: Boolean
) {
    val shape = if (multiSelect) RoundedCornerShape(6.dp) else CircleShape
    val outlineColor = if (enabled) {
        MaterialTheme.colorScheme.outline
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
    }

    Box(
        modifier = Modifier
            .size(22.dp)
            .background(
                color = if (selected && multiSelect) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surface.copy(alpha = 0f)
                },
                shape = shape
            )
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else outlineColor,
                shape = shape
            )
            .clip(shape),
        contentAlignment = Alignment.Center
    ) {
        when {
            selected && multiSelect -> Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onPrimary
            )

            selected -> Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

@Preview
@Composable
private fun LanguageSetupScreenDefaultPreview(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        LanguageSetupScreenContent(
            state = LanguageSetupUiState(
                isLoading = false,
                availableLanguages = Language.entries
            )
        )
    }
}

@Preview
@Composable
private fun LanguageSetupScreenLearningOnlyPreview(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        LanguageSetupScreenContent(
            state = LanguageSetupUiState(
                isLoading = false,
                availableLanguages = Language.entries,
                learningLanguage = Language.DUTCH
            )
        )
    }
}

@Preview
@Composable
private fun LanguageSetupScreenSelectedPreview(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        LanguageSetupScreenContent(
            state = LanguageSetupUiState(
                isLoading = false,
                availableLanguages = Language.entries,
                learningLanguage = Language.DUTCH,
                nativeLanguages = setOf(Language.ENGLISH)
            )
        )
    }
}

@Preview
@Composable
private fun LanguageSetupScreenNoTranslationPreview(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        LanguageSetupScreenContent(
            state = LanguageSetupUiState(
                isLoading = false,
                availableLanguages = Language.entries,
                learningLanguage = Language.DUTCH,
                noTranslationSelected = true
            )
        )
    }
}
