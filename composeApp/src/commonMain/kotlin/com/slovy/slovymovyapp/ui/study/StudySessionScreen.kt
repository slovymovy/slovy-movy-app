package com.slovy.slovymovyapp.ui.study

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material.icons.Icons
import com.slovy.slovymovyapp.ui.SpeakerVector
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slovy.slovymovyapp.data.util.HtmlTagParser
import com.slovy.slovymovyapp.i18n.UiText
import com.slovy.slovymovyapp.i18n.resolve
import com.slovy.slovymovyapp.ui.SpeakerOffVector
import com.slovy.slovymovyapp.ui.ThemePreviewProvider
import com.slovy.slovymovyapp.ui.ThemedPreview
import com.slovy.slovymovyapp.ui.icons.ImageOtterSessionComplete
import com.slovy.slovymovyapp.ui.icons.SlovyIcons
import com.slovy.slovymovyapp.ui.theme.AppSpacing
import com.slovy.slovymovyapp.ui.theme.LocalIsDarkTheme
import com.slovy.slovymovyapp.ui.theme.serifFontFamily
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import slovymovyapp.composeapp.generated.resources.Res
import slovymovyapp.composeapp.generated.resources.guess_by_context
import slovymovyapp.composeapp.generated.resources.study_action_close
import slovymovyapp.composeapp.generated.resources.study_action_done_for_now
import slovymovyapp.composeapp.generated.resources.study_action_retry
import slovymovyapp.composeapp.generated.resources.study_actions_autoplay
import slovymovyapp.composeapp.generated.resources.study_actions_autoplay_description
import slovymovyapp.composeapp.generated.resources.study_actions_autoplay_on
import slovymovyapp.composeapp.generated.resources.study_actions_menu
import slovymovyapp.composeapp.generated.resources.study_actions_remove
import slovymovyapp.composeapp.generated.resources.study_actions_suspend
import slovymovyapp.composeapp.generated.resources.study_actions_suspend_description
import slovymovyapp.composeapp.generated.resources.study_complete_description
import slovymovyapp.composeapp.generated.resources.study_complete_supporting
import slovymovyapp.composeapp.generated.resources.study_remove_cancel
import slovymovyapp.composeapp.generated.resources.study_remove_confirm
import slovymovyapp.composeapp.generated.resources.study_remove_message
import slovymovyapp.composeapp.generated.resources.study_remove_removed_message
import slovymovyapp.composeapp.generated.resources.study_remove_title
import slovymovyapp.composeapp.generated.resources.study_remove_undo
import slovymovyapp.composeapp.generated.resources.study_empty_description
import slovymovyapp.composeapp.generated.resources.study_empty_title
import slovymovyapp.composeapp.generated.resources.study_error_title
import slovymovyapp.composeapp.generated.resources.study_chip_fill_in
import slovymovyapp.composeapp.generated.resources.study_chip_listen
import slovymovyapp.composeapp.generated.resources.study_cant_listen_now
import slovymovyapp.composeapp.generated.resources.study_listen_prompt
import slovymovyapp.composeapp.generated.resources.study_listening_postponed_message
import slovymovyapp.composeapp.generated.resources.study_loading
import slovymovyapp.composeapp.generated.resources.study_multi_sense_front_hint
import slovymovyapp.composeapp.generated.resources.study_play_prompt_audio
import slovymovyapp.composeapp.generated.resources.study_play_word_audio
import slovymovyapp.composeapp.generated.resources.study_hint_starts_with
import slovymovyapp.composeapp.generated.resources.study_hint_show
import slovymovyapp.composeapp.generated.resources.study_hint_show_description
import slovymovyapp.composeapp.generated.resources.study_hint_show_translation_description
import slovymovyapp.composeapp.generated.resources.study_swipe_other_meanings_hint
import slovymovyapp.composeapp.generated.resources.study_stop_audio
import slovymovyapp.composeapp.generated.resources.study_swipe_back_to_rate
import slovymovyapp.composeapp.generated.resources.study_prompt_translate_to
import slovymovyapp.composeapp.generated.resources.study_progress_count
import slovymovyapp.composeapp.generated.resources.study_suspend_message
import slovymovyapp.composeapp.generated.resources.study_suspend_undo
import slovymovyapp.composeapp.generated.resources.study_rating_again
import slovymovyapp.composeapp.generated.resources.study_rating_easy
import slovymovyapp.composeapp.generated.resources.study_rating_good
import slovymovyapp.composeapp.generated.resources.study_rating_hard
import slovymovyapp.composeapp.generated.resources.study_rating_prompt
import slovymovyapp.composeapp.generated.resources.study_tap_to_check
import slovymovyapp.composeapp.generated.resources.study_tap_to_flip

private val MultiSenseFrontHintTopSpacing = 20.dp

@Composable
fun StudySessionScreen(
    viewModel: StudySessionViewModel,
    onCancel: () -> Unit,
    onEnd: () -> Unit,
) {
    StudySessionScreenContent(
        state = viewModel.state,
        completeScrollState = viewModel.completeScrollState,
        snackbarHostState = viewModel.snackbarHostState,
        onCancel = onCancel,
        onEnd = onEnd,
        onReveal = viewModel::reveal,
        onRevealFirstLetterHint = viewModel::revealFirstLetterHint,
        onRevealTranslationHint = viewModel::revealTranslationHint,
        onRate = viewModel::rate,
        onPlayAudio = viewModel::playAudio,
        onStopAudio = viewModel::stopAudio,
        onPostponeListeningCards = viewModel::postponeListeningCards,
        onRetry = viewModel::retry,
        onViewedSenseChange = viewModel::setViewedSense,
        onOpenOverflowMenu = viewModel::openOverflowMenu,
        onDismissOverflowMenu = viewModel::dismissOverflowMenu,
        onToggleAutoplay = viewModel::toggleAutoplay,
        onSuspendWord = viewModel::suspendCurrentWord,
        onRequestRemoveFromLibrary = viewModel::requestRemoveFromLibrary,
        onDismissRemoveConfirmation = viewModel::dismissRemoveConfirmation,
        onConfirmRemoveFromLibrary = viewModel::confirmRemoveFromLibrary,
    )
}

@Composable
fun StudySessionScreenContent(
    state: StudySessionUiState,
    completeScrollState: ScrollState = ScrollState(0),
    snackbarHostState: SnackbarHostState = SnackbarHostState(),
    onCancel: () -> Unit,
    onEnd: () -> Unit,
    onReveal: () -> Unit = {},
    onRevealFirstLetterHint: () -> Unit = {},
    onRevealTranslationHint: () -> Unit = {},
    onRate: (StudyRating) -> Unit = {},
    onPlayAudio: (String) -> Unit = {},
    onStopAudio: () -> Unit = {},
    onPostponeListeningCards: (String) -> Unit = {},
    onRetry: () -> Unit = {},
    onViewedSenseChange: (String) -> Unit = {},
    onOpenOverflowMenu: () -> Unit = {},
    onDismissOverflowMenu: () -> Unit = {},
    onToggleAutoplay: () -> Unit = {},
    onSuspendWord: (String, String) -> Unit = { _, _ -> },
    onRequestRemoveFromLibrary: () -> Unit = {},
    onDismissRemoveConfirmation: () -> Unit = {},
    onConfirmRemoveFromLibrary: (String, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    when (state) {
        is StudySessionUiState.Loading -> {
            val progress = state.progress
            if (progress == null) {
                StudySessionMessageScaffold(
                    modifier = modifier,
                    onClose = onCancel,
                    snackbarHostState = snackbarHostState,
                ) {
                    StudyLoadingIndicator()
                }
            } else {
                StudySessionLoadingContent(
                    progress = progress,
                    onClose = onCancel,
                    snackbarHostState = snackbarHostState,
                    modifier = modifier,
                )
            }
        }

        StudySessionUiState.Empty -> StudySessionMessageScaffold(
            modifier = modifier,
            onClose = onCancel,
            snackbarHostState = snackbarHostState,
        ) {
            Text(
                text = stringResource(Res.string.study_empty_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(AppSpacing.sm))
            Text(
                text = stringResource(Res.string.study_empty_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        is StudySessionUiState.Error -> StudySessionMessageScaffold(
            modifier = modifier,
            onClose = onCancel,
            snackbarHostState = snackbarHostState,
        ) {
            Text(
                text = stringResource(Res.string.study_error_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(AppSpacing.sm))
            Text(
                text = state.message.resolve(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (state.canRetry) {
                Spacer(Modifier.height(AppSpacing.xl))
                OutlinedButton(onClick = onRetry) {
                    Text(stringResource(Res.string.study_action_retry))
                }
            }
        }

        is StudySessionUiState.Active -> StudySessionActiveContent(
            state = state,
            onClose = onCancel,
            onReveal = onReveal,
            onRevealFirstLetterHint = onRevealFirstLetterHint,
            onRevealTranslationHint = onRevealTranslationHint,
            onRate = onRate,
            onPlayAudio = onPlayAudio,
            onStopAudio = onStopAudio,
            onPostponeListeningCards = onPostponeListeningCards,
            onViewedSenseChange = onViewedSenseChange,
            onOpenOverflowMenu = onOpenOverflowMenu,
            onDismissOverflowMenu = onDismissOverflowMenu,
            onToggleAutoplay = onToggleAutoplay,
            onSuspendWord = onSuspendWord,
            onRequestRemoveFromLibrary = onRequestRemoveFromLibrary,
            onDismissRemoveConfirmation = onDismissRemoveConfirmation,
            onConfirmRemoveFromLibrary = onConfirmRemoveFromLibrary,
            snackbarHostState = snackbarHostState,
            modifier = modifier,
        )

        is StudySessionUiState.Complete -> StudySessionCompleteContent(
            completedCount = state.completedCount,
            message = state.message,
            scrollState = completeScrollState,
            onClose = onEnd,
            snackbarHostState = snackbarHostState,
            modifier = modifier,
        )
    }
}

@Composable
private fun StudySessionLoadingContent(
    progress: StudySessionProgressUiState,
    onClose: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { StudySessionSnackbarHost(hostState = snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.md),
        ) {
            StudySessionTopBar(
                progress = progress,
                onClose = onClose,
                isAutoplayEnabled = false,
                onOpenActions = null,
            )
            Spacer(Modifier.height(AppSpacing.md))
            StudyProgressBar(progress = progress)
            Spacer(Modifier.height(AppSpacing.md))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .shadow(
                        elevation = 8.dp,
                        shape = MaterialTheme.shapes.extraLarge,
                        clip = false,
                        ambientColor = StudySessionCardShadowColor.copy(alpha = 0.04f),
                        spotColor = StudySessionCardShadowColor.copy(alpha = 0.06f),
                    ),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.09f)),
                tonalElevation = 0.dp,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(AppSpacing.xl),
                    contentAlignment = Alignment.Center,
                ) {
                    StudyLoadingIndicator()
                }
            }
            Spacer(Modifier.height(AppSpacing.lg))
        }
    }
}

@Composable
private fun StudySessionCompleteContent(
    completedCount: Int,
    message: String,
    scrollState: ScrollState,
    onClose: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { StudySessionSnackbarHost(hostState = snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.md),
        ) {
            StudySessionTopBar(
                progress = null,
                onClose = onClose,
                isAutoplayEnabled = false,
                onOpenActions = null,
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.Center)
                        .verticalScroll(scrollState)
                        .padding(horizontal = AppSpacing.xl, vertical = AppSpacing.md),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Image(
                        imageVector = with(SlovyIcons) { ImageOtterSessionComplete },
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth(0.58f)
                            .heightIn(max = 280.dp),
                    )
                    Spacer(Modifier.height(AppSpacing.xxl))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.displaySmall,
                        fontFamily = MaterialTheme.serifFontFamily,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(AppSpacing.md))
                    Text(
                        text = pluralStringResource(
                            Res.plurals.study_complete_description,
                            completedCount,
                            completedCount,
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = MaterialTheme.serifFontFamily,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(AppSpacing.xs))
                    Text(
                        text = stringResource(Res.string.study_complete_supporting),
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = MaterialTheme.serifFontFamily,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Button(
                onClick = onClose,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = MaterialTheme.shapes.extraLarge,
                contentPadding = PaddingValues(horizontal = AppSpacing.lg),
            ) {
                Text(
                    text =stringResource(Res.string.study_action_done_for_now),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun StudyLoadingIndicator() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(32.dp))
        Spacer(Modifier.height(AppSpacing.lg))
        Text(
            text = stringResource(Res.string.study_loading),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun StudySessionMessageScaffold(
    onClose: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { StudySessionSnackbarHost(hostState = snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.md),
        ) {
            StudySessionTopBar(
                progress = null,
                onClose = onClose,
                isAutoplayEnabled = false,
                onOpenActions = null,
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = AppSpacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                content = content,
            )
        }
    }
}

@Composable
private fun StudySessionActiveContent(
    state: StudySessionUiState.Active,
    onClose: () -> Unit,
    onReveal: () -> Unit,
    onRevealFirstLetterHint: () -> Unit,
    onRevealTranslationHint: () -> Unit,
    onRate: (StudyRating) -> Unit,
    onPlayAudio: (String) -> Unit,
    onStopAudio: () -> Unit,
    onPostponeListeningCards: (String) -> Unit,
    onViewedSenseChange: (String) -> Unit,
    onOpenOverflowMenu: () -> Unit,
    onDismissOverflowMenu: () -> Unit,
    onToggleAutoplay: () -> Unit,
    onSuspendWord: (String, String) -> Unit,
    onRequestRemoveFromLibrary: () -> Unit,
    onDismissRemoveConfirmation: () -> Unit,
    onConfirmRemoveFromLibrary: (String, String) -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val originalSenseId = state.card.activeSenseId
    val viewedSenseId = state.viewedSenseId ?: originalSenseId
    val isOnOriginalSense = !state.card.hasMultiSense ||
        viewedSenseId == originalSenseId
    val suspendedMessage = stringResource(
        Res.string.study_suspend_message,
        state.card.studyWord(),
    )
    val suspendedUndoLabel = stringResource(Res.string.study_suspend_undo)
    val listeningPostponedMessage = stringResource(Res.string.study_listening_postponed_message)

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { StudySessionSnackbarHost(hostState = snackbarHostState) },
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.md),
            ) {
                StudySessionTopBar(
                    progress = state.progress,
                    onClose = onClose,
                    isAutoplayEnabled = state.isAutoplayEnabled,
                    onOpenActions = onOpenOverflowMenu,
                )
                Spacer(Modifier.height(AppSpacing.md))
                StudyProgressBar(progress = state.progress)
                Spacer(Modifier.height(AppSpacing.md))
                StudyCardSurface(
                    card = state.card,
                    side = state.side,
                    isPlayingAudio = state.isPlayingAudio,
                    isPreparingAudio = state.isPreparingAudio,
                    onPlayAudio = onPlayAudio,
                    onStopAudio = onStopAudio,
                    onPostponeListeningCards = { onPostponeListeningCards(listeningPostponedMessage) },
                    onReveal = onReveal,
                    onRevealFirstLetterHint = onRevealFirstLetterHint,
                    onRevealTranslationHint = onRevealTranslationHint,
                    viewedSenseId = viewedSenseId,
                    onViewedSenseChange = onViewedSenseChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
                Spacer(Modifier.height(AppSpacing.md))
                Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                    if (state.side == StudyCardSide.BACK) {
                        if (isOnOriginalSense) {
                            StudyRatingRow(
                                ratings = state.ratingOptions,
                                enabled = !state.isSubmittingReview,
                                onRate = onRate,
                                modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                            )
                        } else {
                            Text(
                                text = stringResource(Res.string.study_swipe_back_to_rate),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontStyle = FontStyle.Italic,
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
        if (state.isOverflowMenuOpen) {
            StudySessionOverflowSheet(
                autoplayEnabled = state.isAutoplayEnabled,
                onToggleAutoplay = onToggleAutoplay,
                onSuspendWord = { onSuspendWord(suspendedMessage, suspendedUndoLabel) },
                onRequestRemoveFromLibrary = onRequestRemoveFromLibrary,
                onDismiss = onDismissOverflowMenu,
            )
        }
        state.removeConfirmation?.let { confirmation ->
            StudyRemoveConfirmationDialog(
                confirmation = confirmation,
                onDismiss = onDismissRemoveConfirmation,
                onConfirm = onConfirmRemoveFromLibrary,
            )
        }
    }
}

private fun StudyCardUiState.studyWord(): String =
    when (this) {
        is StudyCardUiState.Recognition -> promptWord
        is StudyCardUiState.Production -> back.headline
        is StudyCardUiState.Cloze -> back.headline
        is StudyCardUiState.Listening -> back.headline
    }

@Composable
private fun StudySessionSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    SnackbarHost(
        hostState = hostState,
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp),
    ) { data ->
        StudySessionSnackbar(data = data)
    }
}

@Composable
private fun StudySessionSnackbar(
    data: SnackbarData,
    modifier: Modifier = Modifier,
) {
    val isDarkTheme = LocalIsDarkTheme.current
    val snackbarBackground = if (isDarkTheme) Color(0xFFF2EEE6) else Color(0xFF1F1A14)
    val snackbarContent = if (isDarkTheme) Color(0xFF2D2620) else Color(0xFFF2EBE0)
    val snackbarAction = if (isDarkTheme) Color(0xFFB87333) else Color(0xFFE8B57A)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = MaterialTheme.shapes.medium,
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.18f),
                spotColor = Color.Black.copy(alpha = 0.28f),
            ),
        shape = MaterialTheme.shapes.medium,
        color = snackbarBackground,
        contentColor = snackbarContent,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(horizontal = AppSpacing.lg, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = data.visuals.message,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = snackbarContent,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            data.visuals.actionLabel?.let { actionLabel ->
                TextButton(
                    onClick = data::performAction,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = AppSpacing.xs, vertical = 0.dp),
                ) {
                    Text(
                        text = actionLabel.uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        color = snackbarAction,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun StudySessionTopBar(
    progress: StudySessionProgressUiState?,
    onClose: () -> Unit,
    isAutoplayEnabled: Boolean,
    onOpenActions: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(Res.string.study_action_close),
            )
        }
        if (progress != null) {
            StudySessionProgressLabel(
                progress = progress,
                isAutoplayEnabled = isAutoplayEnabled,
                modifier = Modifier.weight(1f),
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        if (onOpenActions != null) {
            IconButton(onClick = onOpenActions) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = stringResource(Res.string.study_actions_menu),
                )
            }
        } else {
            Spacer(Modifier.size(48.dp))
        }
    }
}

@Composable
private fun StudySessionProgressLabel(
    progress: StudySessionProgressUiState,
    isAutoplayEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isAutoplayEnabled) {
            Icon(
                imageVector = SpeakerVector,
                contentDescription = stringResource(Res.string.study_actions_autoplay_on),
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = stringResource(
                Res.string.study_progress_count,
                progress.safeCurrent,
                progress.safeTotal,
            ),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun StudySessionOverflowSheet(
    autoplayEnabled: Boolean,
    onToggleAutoplay: () -> Unit,
    onSuspendWord: () -> Unit,
    onRequestRemoveFromLibrary: () -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .clickable(onClick = onDismiss),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 0.dp,
            shadowElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(top = AppSpacing.sm, bottom = AppSpacing.lg),
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(32.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)),
                )
                Spacer(Modifier.height(AppSpacing.sm))
                StudyOverflowMenuItem(
                    icon = {
                        Icon(
                            imageVector = SpeakerVector,
                            contentDescription = null,
                        )
                    },
                    label = stringResource(Res.string.study_actions_autoplay),
                    supporting = stringResource(Res.string.study_actions_autoplay_description),
                    trailing = {
                        Switch(
                            checked = autoplayEnabled,
                            onCheckedChange = { onToggleAutoplay() },
                        )
                    },
                    onClick = onToggleAutoplay,
                )
                StudyOverflowMenuItem(
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.PauseCircle,
                            contentDescription = null,
                        )
                    },
                    label = stringResource(Res.string.study_actions_suspend),
                    supporting = stringResource(Res.string.study_actions_suspend_description),
                    onClick = onSuspendWord,
                )
                StudyOverflowMenuItem(
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = null,
                        )
                    },
                    label = stringResource(Res.string.study_actions_remove),
                    destructive = true,
                    onClick = onRequestRemoveFromLibrary,
                )
            }
        }
    }
}

@Composable
private fun StudyRemoveConfirmationDialog(
    confirmation: StudyRemoveConfirmationUiState,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    val removedMessage = stringResource(Res.string.study_remove_removed_message, confirmation.lemma)
    val undoLabel = stringResource(Res.string.study_remove_undo)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(Res.string.study_remove_title, confirmation.lemma),
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = MaterialTheme.serifFontFamily,
            )
        },
        text = {
            Text(
                text = stringResource(Res.string.study_remove_message, confirmation.lemma),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(removedMessage, undoLabel) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Text(stringResource(Res.string.study_remove_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.study_remove_cancel))
            }
        },
    )
}

@Composable
private fun StudyOverflowMenuItem(
    icon: @Composable () -> Unit,
    label: String,
    onClick: () -> Unit,
    supporting: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    destructive: Boolean = false,
) {
    val contentColor = if (destructive) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val supportingColor = if (destructive) {
        MaterialTheme.colorScheme.error.copy(alpha = 0.72f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = AppSpacing.xl, vertical = AppSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            CompositionLocalProviderForIconTint(contentColor) {
                icon()
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = contentColor,
            )
            supporting?.let { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = supportingColor,
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
private fun CompositionLocalProviderForIconTint(
    tint: Color,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalContentColor provides tint, content = content)
}

@Composable
private fun StudyProgressBar(
    progress: StudySessionProgressUiState,
    modifier: Modifier = Modifier,
) {
    val total = progress.safeTotal.coerceAtLeast(1)
    val target = (progress.safeCurrent.toFloat() / total).coerceIn(0f, 1f)
    val fraction by animateFloatAsState(targetValue = target, label = "studyProgress")
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(MaterialTheme.shapes.extraSmall)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .fillMaxHeight()
                .clip(MaterialTheme.shapes.extraSmall)
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

@Composable
private fun StudyCardSurface(
    card: StudyCardUiState,
    side: StudyCardSide,
    isPlayingAudio: Boolean,
    isPreparingAudio: Boolean,
    onPlayAudio: (String) -> Unit,
    onStopAudio: () -> Unit,
    onPostponeListeningCards: () -> Unit,
    onReveal: () -> Unit,
    onRevealFirstLetterHint: () -> Unit,
    onRevealTranslationHint: () -> Unit,
    viewedSenseId: String?,
    onViewedSenseChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val flipLabel = stringResource(
        if (card is StudyCardUiState.Recognition) Res.string.study_tap_to_flip else Res.string.study_tap_to_check,
    )
    Surface(
        modifier = modifier.shadow(
            elevation = 8.dp,
            shape = MaterialTheme.shapes.extraLarge,
            clip = false,
            ambientColor = StudySessionCardShadowColor.copy(alpha = 0.04f),
            spotColor = StudySessionCardShadowColor.copy(alpha = 0.06f),
        ),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.09f)),
        tonalElevation = 0.dp,
    ) {
        if (side == StudyCardSide.FRONT) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(
                            start = AppSpacing.xl,
                            top = AppSpacing.xl,
                            end = AppSpacing.xl,
                            bottom = AppSpacing.xl + 88.dp,
                        ),
                ) {
                    StudyChip(label = card.chipLabel)
                    Spacer(Modifier.height(AppSpacing.xl))
                    StudyCardFront(
                        card = card,
                        isPlayingAudio = isPlayingAudio,
                        isPreparingAudio = isPreparingAudio,
                        onPlayAudio = onPlayAudio,
                        onStopAudio = onStopAudio,
                        onPostponeListeningCards = onPostponeListeningCards,
                        onRevealFirstLetterHint = onRevealFirstLetterHint,
                        onRevealTranslationHint = onRevealTranslationHint,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 360.dp),
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(88.dp)
                        .align(Alignment.BottomCenter)
                        .clip(MaterialTheme.shapes.extraLarge)
                        .clickable(role = Role.Button, onClickLabel = flipLabel) { onReveal() },
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = flipLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        } else {
            if (card.hasMultiSense) {
                MultiSenseBack(
                    card = card,
                    viewedSenseId = viewedSenseId,
                    onViewedSenseChange = onViewedSenseChange,
                    isPlayingAudio = isPlayingAudio,
                    isPreparingAudio = isPreparingAudio,
                    onPlayAudio = onPlayAudio,
                    onStopAudio = onStopAudio,
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(AppSpacing.xl),
                ) {
                    StudyChip(label = card.chipLabel)
                    Spacer(Modifier.height(AppSpacing.xl))
                    StudyCardBackContent(
                        back = card.back,
                        isPlayingAudio = isPlayingAudio,
                        isPreparingAudio = isPreparingAudio,
                        onPlayAudio = onPlayAudio,
                        onStopAudio = onStopAudio,
                    )
                }
            }
        }
    }
}

@Composable
private fun MultiSenseBack(
    card: StudyCardUiState,
    viewedSenseId: String?,
    onViewedSenseChange: (String) -> Unit,
    isPlayingAudio: Boolean,
    isPreparingAudio: Boolean,
    onPlayAudio: (String) -> Unit,
    onStopAudio: () -> Unit,
) {
    val senses = card.senses
    val initialPage = senses
        .indexOfFirst { it.id == viewedSenseId }
        .takeIf { it >= 0 }
        ?: senses.indexOfFirst { it.id == card.activeSenseId }.coerceAtLeast(0)
    key(card.id) {
        val pagerState = rememberPagerState(
            initialPage = initialPage,
            pageCount = { senses.size },
        )
        LaunchedEffect(pagerState, senses) {
            snapshotFlow { pagerState.currentPage }.collect { page ->
                senses.getOrNull(page)?.let { onViewedSenseChange(it.id) }
            }
        }
        val currentSense = senses.getOrNull(pagerState.currentPage) ?: senses.first()
        val otherCount = (senses.size - 1).coerceAtLeast(0)
        val swipeHint = pluralStringResource(
            Res.plurals.study_swipe_other_meanings_hint,
            otherCount,
            otherCount,
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(AppSpacing.xl),
        ) {
            StudyChip(label = card.chipLabel)
            Spacer(Modifier.height(AppSpacing.md))
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) { page ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                ) {
                    StudyCardBackContent(
                        back = senses[page].back,
                        isPlayingAudio = isPlayingAudio,
                        isPreparingAudio = isPreparingAudio,
                        onPlayAudio = onPlayAudio,
                        onStopAudio = onStopAudio,
                        headlineEmphasized = true,
                    )
                }
            }
            Spacer(Modifier.height(AppSpacing.md))
            SensePositionIndicator(
                activeSense = currentSense,
                senses = senses,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(AppSpacing.xs))
            Text(
                text = swipeHint,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontStyle = FontStyle.Italic,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun StudyChip(
    label: UiText,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        StudyTaggedText(
            text = label.resolve(),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xs),
        )
    }
}

@Composable
private fun StudyCardFront(
    card: StudyCardUiState,
    isPlayingAudio: Boolean,
    isPreparingAudio: Boolean,
    onPlayAudio: (String) -> Unit,
    onStopAudio: () -> Unit,
    onPostponeListeningCards: () -> Unit,
    onRevealFirstLetterHint: () -> Unit,
    onRevealTranslationHint: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (card) {
        is StudyCardUiState.Recognition -> RecognitionFront(
            card = card,
            isPlayingAudio = isPlayingAudio,
            isPreparingAudio = isPreparingAudio,
            onPlayAudio = onPlayAudio,
            onStopAudio = onStopAudio,
            modifier = modifier,
        )

        is StudyCardUiState.Production -> ProductionFront(
            card = card,
            onRevealFirstLetterHint = onRevealFirstLetterHint,
            modifier = modifier,
        )

        is StudyCardUiState.Cloze -> ClozeFront(
            card = card,
            onRevealTranslationHint = onRevealTranslationHint,
            modifier = modifier,
        )

        is StudyCardUiState.Listening -> ListeningFront(
            card = card,
            isPlayingAudio = isPlayingAudio,
            isPreparingAudio = isPreparingAudio,
            onPlayAudio = onPlayAudio,
            onStopAudio = onStopAudio,
            onPostponeListeningCards = onPostponeListeningCards,
            modifier = modifier,
        )
    }
}

@Composable
private fun RecognitionFront(
    card: StudyCardUiState.Recognition,
    isPlayingAudio: Boolean,
    isPreparingAudio: Boolean,
    onPlayAudio: (String) -> Unit,
    onStopAudio: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        val availableWidth = maxWidth
        val speakerSpace = if (card.promptAudioText != null) 36.dp + AppSpacing.sm else 0.dp
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StudyTaggedText(
                    text = card.promptWord,
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontFamily = MaterialTheme.serifFontFamily,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(
                        minFontSize = 14.sp,
                        maxFontSize = MaterialTheme.typography.displayMedium.fontSize,
                        stepSize = 1.sp,
                    ),
                    modifier = Modifier.widthIn(max = (availableWidth - speakerSpace).coerceAtLeast(0.dp)),
                )
                card.promptAudioText?.let { audioText ->
                    StudySpeakerButton(
                        audioText = audioText,
                        playContentDescription = stringResource(Res.string.study_play_word_audio),
                        stopContentDescription = stringResource(Res.string.study_stop_audio),
                        isPlayingAudio = isPlayingAudio,
                        isPreparingAudio = isPreparingAudio,
                        onPlayAudio = onPlayAudio,
                        onStopAudio = onStopAudio,
                    )
                }
            }
            MultiSenseFrontHint(
                card = card,
                modifier = Modifier.padding(top = MultiSenseFrontHintTopSpacing),
            )
        }
    }
}

@Composable
private fun ProductionFront(
    card: StudyCardUiState.Production,
    onRevealFirstLetterHint: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = card.promptLabel.resolve().uppercase(),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.4.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(AppSpacing.md))
        StudyTaggedText(
            text = card.promptText,
            style = (if (card.isDefinitionPrompt) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.displaySmall).copy(
                fontFamily = MaterialTheme.serifFontFamily,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        card.firstLetterHint?.let { hint ->
            Spacer(Modifier.height(AppSpacing.xl))
            FirstLetterHintView(
                hint = hint,
                revealed = card.firstLetterHintRevealed,
                onReveal = onRevealFirstLetterHint,
            )
        }
    }
}

@Composable
private fun ClozeFront(
    card: StudyCardUiState.Cloze,
    onRevealTranslationHint: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
        ) {
            Text(
                text = stringResource(Res.string.guess_by_context).uppercase(),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            StudyClozeText(
                cloze = card.prompt,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = MaterialTheme.serifFontFamily,
                ),
            )
            card.translationHint?.let { hint ->
                if (card.translationHintRevealed) {
                    StudyTranslationHintBlock(cloze = hint)
                } else {
                    HintRevealPill(
                        contentDescription = stringResource(Res.string.study_hint_show_translation_description),
                        onReveal = onRevealTranslationHint,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }
            }
        }
    }
}

@Composable
private fun ListeningFront(
    card: StudyCardUiState.Listening,
    isPlayingAudio: Boolean,
    isPreparingAudio: Boolean,
    onPlayAudio: (String) -> Unit,
    onStopAudio: () -> Unit,
    onPostponeListeningCards: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppSpacing.xl),
        ) {
            Surface(
                modifier = Modifier
                    .size(132.dp)
                    .clip(CircleShape)
                    .clickable(enabled = !isPreparingAudio, role = Role.Button) {
                        if (isPlayingAudio) onStopAudio() else onPlayAudio(card.promptAudioText)
                    },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    when {
                        isPreparingAudio -> CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            strokeWidth = 3.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        isPlayingAudio -> Icon(
                            imageVector = Icons.Filled.StopCircle,
                            contentDescription = stringResource(Res.string.study_stop_audio),
                            modifier = Modifier.size(56.dp),
                        )
                        else -> Icon(
                            imageVector = SpeakerVector,
                            contentDescription = stringResource(Res.string.study_play_prompt_audio),
                            modifier = Modifier.size(56.dp),
                        )
                    }
                }
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = stringResource(Res.string.study_listen_prompt),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = MaterialTheme.serifFontFamily,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium,
                        fontStyle = FontStyle.Normal,
                        lineHeight = 19.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                ListeningMultiSenseByline(card = card)
            }
            CantListenNowButton(onClick = onPostponeListeningCards)
        }
    }
}

@Composable
private fun CantListenNowButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        shape = CircleShape,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Icon(
            imageVector = SpeakerOffVector,
            contentDescription = null,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(Res.string.study_cant_listen_now),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.1.sp,
        )
    }
}

@Composable
private fun ListeningMultiSenseByline(
    card: StudyCardUiState,
    modifier: Modifier = Modifier,
) {
    if (!card.hasMultiSense) return

    Text(
        text = pluralStringResource(
            Res.plurals.study_multi_sense_front_hint,
            card.senses.size,
            card.senses.size,
        ),
        style = MaterialTheme.typography.bodySmall.copy(
            fontFamily = MaterialTheme.serifFontFamily,
            fontStyle = FontStyle.Italic,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            lineHeight = 17.sp,
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
        textAlign = TextAlign.Center,
        modifier = modifier.widthIn(max = 240.dp),
    )
}

@Composable
private fun MultiSenseFrontHint(
    card: StudyCardUiState,
    modifier: Modifier = Modifier,
) {
    if (!card.hasMultiSense) return

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = pluralStringResource(
                Res.plurals.study_multi_sense_front_hint,
                card.senses.size,
                card.senses.size,
            ),
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = MaterialTheme.serifFontFamily,
                fontStyle = FontStyle.Italic,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 240.dp),
        )
    }
}

@Composable
private fun StudyCardBackContent(
    back: StudyCardBackUiState,
    isPlayingAudio: Boolean,
    isPreparingAudio: Boolean,
    onPlayAudio: (String) -> Unit,
    onStopAudio: () -> Unit,
    headlineEmphasized: Boolean = false,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
    ) {
        back.cloze?.let { cloze ->
            StudyClozeText(
                cloze = cloze,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = MaterialTheme.serifFontFamily,
                ),
            )
            Spacer(Modifier.height(AppSpacing.xs))
        }

        val headlineIsLemma = back.isLemmaHeadline
        Row(
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
            verticalAlignment = Alignment.Top,
        ) {
            StudyTaggedText(
                text = back.headline,
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontFamily = MaterialTheme.serifFontFamily,
                    fontSize = if (headlineEmphasized) 30.sp else MaterialTheme.typography.headlineLarge.fontSize,
                    lineHeight = if (headlineEmphasized) 33.sp else MaterialTheme.typography.headlineLarge.lineHeight,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Start,
                maxLines = if (headlineIsLemma) 1 else Int.MAX_VALUE,
                autoSize = if (headlineIsLemma) TextAutoSize.StepBased(
                    minFontSize = 14.sp,
                    maxFontSize = MaterialTheme.typography.headlineLarge.fontSize,
                    stepSize = 1.sp,
                ) else null,
                modifier = Modifier.weight(1f, fill = false),
            )
            back.audioText?.let { audioText ->
                StudySpeakerButton(
                    audioText = audioText,
                    playContentDescription = stringResource(Res.string.study_play_word_audio),
                    stopContentDescription = stringResource(Res.string.study_stop_audio),
                    isPlayingAudio = isPlayingAudio,
                    isPreparingAudio = isPreparingAudio,
                    onPlayAudio = onPlayAudio,
                    onStopAudio = onStopAudio,
                )
            }
        }

        back.secondary?.let { secondary ->
            StudyTaggedText(
                text = secondary,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        back.definition?.let { definition ->
            StudyTaggedText(
                text = definition,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = MaterialTheme.serifFontFamily,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        back.examples.forEach { example ->
            StudyExampleBlock(example = example)
        }
    }
}

@Composable
private fun SensePositionIndicator(
    activeSense: StudyCardSenseUiState,
    senses: List<StudyCardSenseUiState>,
    modifier: Modifier = Modifier,
) {
    val activeIndex = senses.indexOfFirst { it.id == activeSense.id }.takeIf { it >= 0 } ?: 0
    SenseDotRow(
        count = senses.size,
        activeIndex = activeIndex,
        activeColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f),
        modifier = modifier.clearAndSetSemantics {},
    )
}

@Composable
private fun SenseDotRow(
    count: Int,
    activeIndex: Int?,
    activeColor: Color,
    inactiveColor: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            val active = activeIndex == index
            val dotWidth by animateDpAsState(
                targetValue = if (active) 16.dp else 6.dp,
                label = "senseDotWidth",
            )
            Box(
                modifier = Modifier
                    .width(dotWidth)
                    .height(6.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (active) activeColor else inactiveColor),
            )
        }
    }
}

@Composable
private fun StudySpeakerButton(
    audioText: String,
    playContentDescription: String,
    stopContentDescription: String,
    isPlayingAudio: Boolean,
    isPreparingAudio: Boolean,
    onPlayAudio: (String) -> Unit,
    onStopAudio: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(enabled = !isPreparingAudio, role = Role.Button) {
                if (isPlayingAudio) onStopAudio() else onPlayAudio(audioText)
            },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Box(contentAlignment = Alignment.Center) {
            when {
                isPreparingAudio -> CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                isPlayingAudio -> Icon(
                    imageVector = Icons.Filled.StopCircle,
                    contentDescription = stopContentDescription,
                    modifier = Modifier.size(20.dp),
                )
                else -> Icon(
                    imageVector = SpeakerVector,
                    contentDescription = playContentDescription,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun StudyTranslationHintBlock(
    cloze: StudyClozeTextUiState,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(1.5.dp))
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        )
        StudyClozeText(
            cloze = cloze,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontFamily = MaterialTheme.serifFontFamily,
                fontStyle = FontStyle.Normal,
                lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.1f,
            ),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StudyExampleBlock(
    example: StudyExampleUiState,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(1.5.dp))
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            StudyExampleText(
                text = example.text,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = MaterialTheme.serifFontFamily,
                    fontStyle = FontStyle.Normal,
                    lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.1f,
                ),
            )
            example.translation?.let { translation ->
                StudyExampleText(
                    text = translation,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.1f,
                    ),
                )
            }
        }
    }
}

@Composable
private fun StudyExampleText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    val annotated = buildAnnotatedString {
        HtmlTagParser.parseTextSegments(text).forEach { segment ->
            if (segment.isTagged) {
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                append(HtmlTagParser.plainText(segment.text))
                pop()
            } else {
                append(segment.text)
            }
        }
    }
    Text(text = annotated, style = style, modifier = modifier)
}

internal data class StudyClozeDisplayText(
    val text: String,
    val answerRanges: List<IntRange>,
)

internal fun StudyClozeTextUiState.toDisplayText(): StudyClozeDisplayText {
    val normalizedRanges = answerRanges.mapNotNull { range ->
        val start = range.first.coerceIn(0, text.length)
        val endExclusive = (range.last + 1).coerceIn(start, text.length)
        if (start == endExclusive) null else start until endExclusive
    }.sortedBy { it.first }

    val output = StringBuilder()
    val outputRanges = mutableListOf<IntRange>()
    var cursor = 0

    normalizedRanges.forEach { range ->
        val start = maxOf(range.first, cursor)
        val endExclusive = range.last + 1
        if (start >= endExclusive) return@forEach

        output.append(HtmlTagParser.plainText(text.substring(cursor, start)))
        val outputStart = output.length
        val answer = HtmlTagParser.plainText(text.substring(start, endExclusive))
        if (filled) {
            output.append(answer)
        } else {
            repeat(answer.length.coerceAtLeast(6)) {
                output.append('\u00A0')
            }
        }
        outputRanges.add(outputStart until output.length)
        cursor = endExclusive
    }

    output.append(HtmlTagParser.plainText(text.substring(cursor)))
    return StudyClozeDisplayText(
        text = output.toString(),
        answerRanges = outputRanges,
    )
}

@Composable
private fun StudyClozeText(
    cloze: StudyClozeTextUiState,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    val highlightColor = MaterialTheme.colorScheme.primary
    val highlightBackground = MaterialTheme.colorScheme.primaryContainer
    val displayText = remember(cloze) { cloze.toDisplayText() }
    val text = buildAnnotatedString {
        var cursor = 0
        displayText.answerRanges.forEach { range ->
            append(displayText.text.substring(cursor, range.first))
            if (cloze.filled) {
                pushStyle(SpanStyle(color = highlightColor, fontWeight = FontWeight.SemiBold))
            } else {
                pushStyle(SpanStyle(color = highlightColor, textDecoration = TextDecoration.Underline))
            }
            append(displayText.text.substring(range.first, range.last + 1))
            pop()
            cursor = range.last + 1
        }
        append(displayText.text.substring(cursor))
    }

    var layoutResult by remember(text) { mutableStateOf<TextLayoutResult?>(null) }

    Text(
        text = text,
        style = style,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                if (!cloze.filled) return@drawBehind
                val layout = layoutResult ?: return@drawBehind
                displayText.answerRanges.forEach { highlightRange ->
                    val highlightStart = highlightRange.first
                    val highlightEnd = highlightRange.last + 1
                    val startLine = layout.getLineForOffset(highlightStart)
                    val endLine = layout.getLineForOffset((highlightEnd - 1).coerceAtLeast(highlightStart))
                    for (line in startLine..endLine) {
                        val lineRangeStart = maxOf(layout.getLineStart(line), highlightStart)
                        val lineRangeEnd = minOf(layout.getLineEnd(line), highlightEnd)
                        if (lineRangeStart >= lineRangeEnd) continue
                        val startBound = layout.getBoundingBox(lineRangeStart)
                        val endBound = layout.getBoundingBox((lineRangeEnd - 1).coerceAtLeast(lineRangeStart))
                        drawRoundRect(
                            color = highlightBackground,
                            topLeft = Offset(startBound.left, layout.getLineTop(line)),
                            size = Size(endBound.right - startBound.left, layout.getLineBottom(line) - layout.getLineTop(line)),
                            cornerRadius = CornerRadius(4.dp.toPx()),
                        )
                    }
                }
            },
        onTextLayout = { layoutResult = it },
    )
}

@Composable
private fun StudyTaggedText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    autoSize: TextAutoSize? = null,
) {
    val highlightColor = MaterialTheme.colorScheme.primary
    val annotated = buildAnnotatedString {
        HtmlTagParser.parseTextSegments(text).forEach { segment ->
            if (segment.isTagged) {
                pushStyle(
                    SpanStyle(
                        color = highlightColor,
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
                append(HtmlTagParser.plainText(segment.text))
                pop()
            } else {
                append(segment.text)
            }
        }
    }
    if (autoSize != null) {
        Text(
            text = annotated,
            style = style,
            color = color,
            textAlign = textAlign,
            maxLines = maxLines,
            overflow = overflow,
            autoSize = autoSize,
            modifier = modifier,
        )
    } else {
        Text(
            text = annotated,
            style = style,
            color = color,
            textAlign = textAlign,
            maxLines = maxLines,
            overflow = overflow,
            modifier = modifier,
        )
    }
}

@Composable
private fun HintRevealPill(
    contentDescription: String,
    onReveal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f)
    val dashWidth = 6.dp
    val dashGap = 4.dp
    val description = contentDescription
    Box(
        modifier = modifier
            .height(48.dp)
            .drawBehind {
                val strokeWidth = 1.dp.toPx()
                drawRoundRect(
                    color = borderColor,
                    topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f),
                    size = Size(
                        width = size.width - strokeWidth,
                        height = size.height - strokeWidth,
                    ),
                    cornerRadius = CornerRadius(size.height / 2f, size.height / 2f),
                    style = Stroke(
                        width = strokeWidth,
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(dashWidth.toPx(), dashGap.toPx()),
                        ),
                    ),
                )
            }
            .clip(CircleShape)
            .semantics(mergeDescendants = true) {
                this.contentDescription = description
            }
            .clickable(role = Role.Button, onClickLabel = description) { onReveal() }
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.VpnKey,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(Res.string.study_hint_show),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FirstLetterHintView(
    hint: FirstLetterHint,
    revealed: Boolean,
    onReveal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hintContentDescription = pluralStringResource(
        Res.plurals.study_hint_starts_with,
        hint.letterCount,
        hint.letter.toString(),
        hint.letterCount,
    )
    val shellModifier = modifier.height(48.dp)
    if (!revealed) {
        HintRevealPill(
            contentDescription = stringResource(Res.string.study_hint_show_description),
            onReveal = onReveal,
            modifier = shellModifier,
        )
        return
    }

    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = shellModifier
            .clip(CircleShape)
            .clearAndSetSemantics {
                contentDescription = hintContentDescription
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = hint.letter.toString(),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.sp,
                fontFamily = MaterialTheme.serifFontFamily,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (hint.dotCount > 0) {
                Text(
                    text = "·".repeat(hint.dotCount),
                    fontSize = 16.sp,
                    fontFamily = MaterialTheme.serifFontFamily,
                    letterSpacing = 8.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun StudyRatingRow(
    ratings: List<StudyRatingUiState>,
    enabled: Boolean,
    onRate: (StudyRating) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(Res.string.study_rating_prompt).uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 12.sp,
                letterSpacing = 0.3.sp,
            ),
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            autoSize = TextAutoSize.StepBased(
                minFontSize = 9.sp,
                maxFontSize = 12.sp,
                stepSize = 1.sp,
            ),
        )
        Spacer(Modifier.height(AppSpacing.sm))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        ) {
            ratings.forEach { option ->
                StudyRatingButton(
                    option = option,
                    enabled = enabled && option.enabled,
                    onRate = onRate,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun StudyRatingButton(
    option: StudyRatingUiState,
    enabled: Boolean,
    onRate: (StudyRating) -> Unit,
    modifier: Modifier = Modifier,
) {
    val (containerColor, contentColor) = colorsForRating(option.rating)
    Surface(
        modifier = modifier
            .height(56.dp)
            .clip(MaterialTheme.shapes.large)
            .clickable(enabled = enabled, role = Role.Button) { onRate(option.rating) },
        shape = MaterialTheme.shapes.large,
        color = containerColor.copy(alpha = if (enabled) 1f else 0.5f),
        contentColor = contentColor,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = AppSpacing.xs),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = ratingLabel(option.rating),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                autoSize = TextAutoSize.StepBased(
                    minFontSize = 9.sp,
                    maxFontSize = MaterialTheme.typography.labelMedium.fontSize,
                    stepSize = 1.sp,
                ),
            )
        }
    }
}

@Composable
private fun ratingLabel(rating: StudyRating): String =
    stringResource(
        when (rating) {
            StudyRating.AGAIN -> Res.string.study_rating_again
            StudyRating.HARD -> Res.string.study_rating_hard
            StudyRating.GOOD -> Res.string.study_rating_good
            StudyRating.EASY -> Res.string.study_rating_easy
        },
    )

private val StudyCardUiState.hasMultiSense: Boolean
    get() = senses.size > 1

private fun sampleRatings() = listOf(
    StudyRatingUiState(StudyRating.AGAIN, "< 1min"),
    StudyRatingUiState(StudyRating.HARD, "6min"),
    StudyRatingUiState(StudyRating.GOOD, "1d"),
    StudyRatingUiState(StudyRating.EASY, "4d"),
)

private fun recognitionCard() = StudyCardUiState.Recognition(
    id = "recognition",
    chipLabel = UiText.Plain("NL -> EN"),
    promptWord = "gezellig",
    mode = StudyRecognitionMode.BILINGUAL,
    back = StudyCardBackUiState(
        headline = "cosy, sociable",
        definition = "a feeling of warmth, comfort, and conviviality from being together with others",
        examples = listOf(
            StudyExampleUiState(
                text = "Het was zo <w>gezellig</w> bij jullie thuis.",
                translation = "It was so lovely at your place.",
            ),
        ),
        audioText = null,
    ),
)

private fun multiSenseRecognitionCard() = StudyCardUiState.Recognition(
    id = "multi-sense-recognition",
    chipLabel = UiText.Plain("NL -> EN"),
    promptWord = "zetten",
    mode = StudyRecognitionMode.BILINGUAL,
    senses = listOf(
        StudyCardSenseUiState(
            id = "sense-1",
            num = 1,
            back = StudyCardBackUiState(
                headline = "to put, place",
                definition = "place something somewhere with intent.",
                examples = listOf(
                    StudyExampleUiState(
                        text = "Zet de vaas op <w>tafel</w>.",
                        translation = "Put the vase on the table.",
                    ),
                ),
                audioText = null,
            ),
        ),
        StudyCardSenseUiState(
            id = "sense-2",
            num = 2,
            back = StudyCardBackUiState(
                headline = "to set",
                definition = "adjust something to a particular position or value.",
                examples = listOf(
                    StudyExampleUiState(
                        text = "Zet de wekker op zeven uur.",
                        translation = "Set the alarm for seven.",
                    ),
                ),
                audioText = null,
            ),
        ),
        StudyCardSenseUiState(
            id = "sense-3",
            num = 3,
            back = StudyCardBackUiState(
                headline = "to turn on",
                definition = "start a device or source of light.",
                examples = listOf(
                    StudyExampleUiState(
                        text = "Zet het licht aan.",
                        translation = "Turn on the light.",
                    ),
                ),
                audioText = null,
            ),
        ),
    ),
    activeSenseId = "sense-1",
    back = StudyCardBackUiState(
        headline = "to put, place",
        definition = "place something somewhere with intent.",
        examples = listOf(
            StudyExampleUiState(
                text = "Zet de vaas op <w>tafel</w>.",
                translation = "Put the vase on the table.",
            ),
        ),
        audioText = null,
    ),
)

private fun productionCard() = StudyCardUiState.Production(
    id = "production",
    chipLabel = UiText.Plain("EN -> NL"),
    promptLabel = UiText.Resource(Res.string.study_prompt_translate_to, listOf("Dutch")),
    promptText = "cosy, sociable",
    firstLetterHint = FirstLetterHint(letter = 'g', letterCount = 8, dotCount = 7),
    back = StudyCardBackUiState(
        headline = "gezellig",
        secondary = "cosy, sociable",
        definition = "a uniquely Dutch flavour of warm conviviality",
        examples = listOf(
            StudyExampleUiState(
                text = "Bij Marja is het altijd <w>gezellig</w>.",
                translation = "It's always cosy at Marja's.",
            ),
        ),
    ),
)

private fun clozeCard() = StudyCardUiState.Cloze(
    id = "cloze",
    chipLabel = UiText.Resource(Res.string.study_chip_fill_in),
    prompt = StudyClozeTextUiState(
        text = "Het was zo gezellig bij jullie thuis.",
        answerRanges = listOf(11..18),
    ),
    translationHint = StudyClozeTextUiState(
        text = "It was so lovely at your place.",
        answerRanges = listOf(10..15),
    ),
    back = StudyCardBackUiState(
        headline = "gezellig",
        secondary = "cosy, sociable",
        definition = "a feeling of warmth and conviviality from being together",
        cloze = StudyClozeTextUiState(
            text = "Het was zo gezellig bij jullie thuis.",
            answerRanges = listOf(11..18),
            filled = true,
        ),
    ),
)

private fun translationClozeCard() = StudyCardUiState.Cloze(
    id = "cloze-translation",
    chipLabel = UiText.Plain("Recall NL"),
    prompt = StudyClozeTextUiState(
        text = "It was so lovely at your place.",
        answerRanges = listOf(10..15),
        filled = true,
    ),
    translationHint = StudyClozeTextUiState(
        text = "Het was zo gezellig bij jullie thuis.",
        answerRanges = listOf(11..18),
    ),
    back = StudyCardBackUiState(
        headline = "gezellig",
        isLemmaHeadline = true,
        secondary = "cosy, sociable",
        definition = "a feeling of warmth and conviviality from being together",
        examples = listOf(
            StudyExampleUiState(
                text = "Het was zo gezellig bij jullie thuis.",
                translation = "It was so lovely at your place.",
            ),
        ),
    ),
)

private fun listeningCard() = StudyCardUiState.Listening(
    id = "listening",
    chipLabel = UiText.Resource(Res.string.study_chip_listen),
    promptAudioText = "gezellig",
    back = StudyCardBackUiState(
        headline = "gezellig",
        secondary = "cosy, sociable",
        definition = "a feeling of warmth, comfort, and conviviality from being together with others",
        examples = listOf(
            StudyExampleUiState(
                text = "Het was zo <w>gezellig</w> bij jullie thuis.",
                translation = "It was so lovely at your place.",
            ),
        ),
    ),
)

private fun multiSenseListeningCard() = StudyCardUiState.Listening(
    id = "multi-sense-listening",
    chipLabel = UiText.Resource(Res.string.study_chip_listen),
    promptAudioText = "zetten",
    senses = multiSenseRecognitionCard().senses.map { sense ->
        sense.copy(
            back = sense.back.copy(
                headline = "zetten",
                isLemmaHeadline = true,
                secondary = sense.back.headline,
                audioText = "zetten",
            ),
        )
    },
    activeSenseId = "sense-1",
    back = StudyCardBackUiState(
        headline = "zetten",
        isLemmaHeadline = true,
        secondary = "to put, place",
        definition = "place something somewhere with intent.",
        examples = listOf(
            StudyExampleUiState(
                text = "Zet de vaas op <w>tafel</w>.",
                translation = "Put the vase on the table.",
            ),
        ),
        audioText = "zetten",
    ),
)

private fun activeState(
    card: StudyCardUiState,
    side: StudyCardSide,
    current: Int = 3,
) = StudySessionUiState.Active(
    progress = StudySessionProgressUiState(current = current, total = 12),
    card = card,
    side = side,
    ratingOptions = sampleRatings(),
)

@Preview
@Composable
private fun StudySessionLoadingPreview(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean,
) {
    ThemedPreview(darkTheme = isDark) {
        StudySessionScreenContent(
            state = StudySessionUiState.Loading(),
            onCancel = {},
            onEnd = {},
        )
    }
}

@Preview
@Composable
private fun StudySessionCardLoadingPreview(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean,
) {
    ThemedPreview(darkTheme = isDark) {
        StudySessionScreenContent(
            state = StudySessionUiState.Loading(
                progress = StudySessionProgressUiState(current = 3, total = 12),
            ),
            onCancel = {},
            onEnd = {},
        )
    }
}

@Preview
@Composable
private fun StudySessionEmptyPreview(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean,
) {
    ThemedPreview(darkTheme = isDark) {
        StudySessionScreenContent(
            state = StudySessionUiState.Empty,
            onCancel = {},
            onEnd = {},
        )
    }
}

@Preview
@Composable
private fun StudySessionErrorPreview(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean,
) {
    ThemedPreview(darkTheme = isDark) {
        StudySessionScreenContent(
            state = StudySessionUiState.Error(UiText.Plain("This card is missing example data.")),
            onCancel = {},
            onEnd = {},
        )
    }
}

@Preview
@Composable
private fun StudySessionRecognitionFrontPreview(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean,
) {
    ThemedPreview(darkTheme = isDark) {
        StudySessionScreenContent(
            state = activeState(recognitionCard(), StudyCardSide.FRONT),
            onCancel = {},
            onEnd = {},
        )
    }
}

@Preview
@Composable
private fun StudySessionRecognitionBackPreview(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean,
) {
    ThemedPreview(darkTheme = isDark) {
        StudySessionScreenContent(
            state = activeState(recognitionCard(), StudyCardSide.BACK),
            onCancel = {},
            onEnd = {},
        )
    }
}

@Preview
@Composable
private fun StudySessionMultiSenseRecognitionFrontPreview(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean,
) {
    ThemedPreview(darkTheme = isDark) {
        StudySessionScreenContent(
            state = activeState(multiSenseRecognitionCard(), StudyCardSide.FRONT),
            onCancel = {},
            onEnd = {},
        )
    }
}

@Preview
@Composable
private fun StudySessionMultiSenseRecognitionBackPreview(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean,
) {
    ThemedPreview(darkTheme = isDark) {
        StudySessionScreenContent(
            state = activeState(multiSenseRecognitionCard(), StudyCardSide.BACK),
            onCancel = {},
            onEnd = {},
        )
    }
}

@Preview
@Composable
private fun StudySessionProductionFrontPreview(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean,
) {
    ThemedPreview(darkTheme = isDark) {
        StudySessionScreenContent(
            state = activeState(productionCard(), StudyCardSide.FRONT, current = 5),
            onCancel = {},
            onEnd = {},
        )
    }
}

@Preview
@Composable
private fun StudySessionProductionFrontHintRevealedPreview(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean,
) {
    ThemedPreview(darkTheme = isDark) {
        StudySessionScreenContent(
            state = activeState(
                productionCard().copy(firstLetterHintRevealed = true),
                StudyCardSide.FRONT,
                current = 5,
            ),
            onCancel = {},
            onEnd = {},
        )
    }
}

@Preview
@Composable
private fun StudySessionProductionBackPreview(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean,
) {
    ThemedPreview(darkTheme = isDark) {
        StudySessionScreenContent(
            state = activeState(productionCard(), StudyCardSide.BACK, current = 5),
            onCancel = {},
            onEnd = {},
        )
    }
}

@Preview
@Composable
private fun StudySessionClozeFrontPreview(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean,
) {
    ThemedPreview(darkTheme = isDark) {
        StudySessionScreenContent(
            state = activeState(clozeCard(), StudyCardSide.FRONT, current = 6),
            onCancel = {},
            onEnd = {},
        )
    }
}

@Preview
@Composable
private fun StudySessionClozeFrontHintRevealedPreview(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean,
) {
    ThemedPreview(darkTheme = isDark) {
        StudySessionScreenContent(
            state = activeState(
                clozeCard().copy(translationHintRevealed = true),
                StudyCardSide.FRONT,
                current = 6,
            ),
            onCancel = {},
            onEnd = {},
        )
    }
}

@Preview
@Composable
private fun StudySessionClozeBackPreview(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean,
) {
    ThemedPreview(darkTheme = isDark) {
        StudySessionScreenContent(
            state = activeState(clozeCard(), StudyCardSide.BACK, current = 6),
            onCancel = {},
            onEnd = {},
        )
    }
}

@Preview
@Composable
private fun StudySessionTranslationClozeFrontPreview(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean,
) {
    ThemedPreview(darkTheme = isDark) {
        StudySessionScreenContent(
            state = activeState(translationClozeCard(), StudyCardSide.FRONT, current = 6),
            onCancel = {},
            onEnd = {},
        )
    }
}

@Preview
@Composable
private fun StudySessionTranslationClozeFrontHintRevealedPreview(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean,
) {
    ThemedPreview(darkTheme = isDark) {
        StudySessionScreenContent(
            state = activeState(
                translationClozeCard().copy(translationHintRevealed = true),
                StudyCardSide.FRONT,
                current = 6,
            ),
            onCancel = {},
            onEnd = {},
        )
    }
}

@Preview
@Composable
private fun StudySessionListeningFrontPreview(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean,
) {
    ThemedPreview(darkTheme = isDark) {
        StudySessionScreenContent(
            state = activeState(listeningCard(), StudyCardSide.FRONT, current = 2),
            onCancel = {},
            onEnd = {},
        )
    }
}

@Preview
@Composable
private fun StudySessionListeningBackPreview(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean,
) {
    ThemedPreview(darkTheme = isDark) {
        StudySessionScreenContent(
            state = activeState(listeningCard(), StudyCardSide.BACK, current = 2),
            onCancel = {},
            onEnd = {},
        )
    }
}

@Preview
@Composable
private fun StudySessionMultiSenseListeningFrontPreview(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean,
) {
    ThemedPreview(darkTheme = isDark) {
        StudySessionScreenContent(
            state = activeState(multiSenseListeningCard(), StudyCardSide.FRONT, current = 2),
            onCancel = {},
            onEnd = {},
        )
    }
}

@Preview
@Composable
private fun StudySessionMultiSenseListeningBackPreview(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean,
) {
    ThemedPreview(darkTheme = isDark) {
        StudySessionScreenContent(
            state = activeState(multiSenseListeningCard(), StudyCardSide.BACK, current = 2),
            onCancel = {},
            onEnd = {},
        )
    }
}

@Preview
@Composable
private fun StudySessionOverflowMenuOpenPreview(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean,
) {
    ThemedPreview(darkTheme = isDark) {
        StudySessionScreenContent(
            state = activeState(recognitionCard(), StudyCardSide.FRONT).copy(
                isOverflowMenuOpen = true,
                isAutoplayEnabled = true,
            ),
            onCancel = {},
            onEnd = {},
        )
    }
}

@Preview
@Composable
private fun StudySessionCompletePreview(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean,
) {
    ThemedPreview(darkTheme = isDark) {
        StudySessionScreenContent(
            state = StudySessionUiState.Complete(completedCount = 12, message = "Goed gedaan!"),
            onCancel = {},
            onEnd = {},
        )
    }
}
