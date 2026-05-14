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
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
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
import com.slovy.slovymovyapp.ui.ThemePreviewProvider
import com.slovy.slovymovyapp.ui.ThemedPreview
import com.slovy.slovymovyapp.ui.icons.ImageOtterSessionComplete
import com.slovy.slovymovyapp.ui.icons.SlovyIcons
import com.slovy.slovymovyapp.ui.theme.AppSpacing
import com.slovy.slovymovyapp.ui.theme.serifFontFamily
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import slovymovyapp.composeapp.generated.resources.Res
import slovymovyapp.composeapp.generated.resources.guess_by_context
import slovymovyapp.composeapp.generated.resources.study_action_close
import slovymovyapp.composeapp.generated.resources.study_action_done_for_now
import slovymovyapp.composeapp.generated.resources.study_action_retry
import slovymovyapp.composeapp.generated.resources.study_complete_description
import slovymovyapp.composeapp.generated.resources.study_complete_supporting
import slovymovyapp.composeapp.generated.resources.study_empty_description
import slovymovyapp.composeapp.generated.resources.study_empty_title
import slovymovyapp.composeapp.generated.resources.study_error_title
import slovymovyapp.composeapp.generated.resources.study_chip_fill_in
import slovymovyapp.composeapp.generated.resources.study_chip_listen
import slovymovyapp.composeapp.generated.resources.study_listen_prompt
import slovymovyapp.composeapp.generated.resources.study_loading
import slovymovyapp.composeapp.generated.resources.study_multi_sense_front_hint
import slovymovyapp.composeapp.generated.resources.study_play_prompt_audio
import slovymovyapp.composeapp.generated.resources.study_play_word_audio
import slovymovyapp.composeapp.generated.resources.study_hint_starts_with
import slovymovyapp.composeapp.generated.resources.study_sense_position_accessibility
import slovymovyapp.composeapp.generated.resources.study_sense_position_label
import slovymovyapp.composeapp.generated.resources.study_stop_audio
import slovymovyapp.composeapp.generated.resources.study_swipe_back_to_rate
import slovymovyapp.composeapp.generated.resources.study_prompt_translate_to
import slovymovyapp.composeapp.generated.resources.study_progress_count
import slovymovyapp.composeapp.generated.resources.study_rating_again
import slovymovyapp.composeapp.generated.resources.study_rating_easy
import slovymovyapp.composeapp.generated.resources.study_rating_good
import slovymovyapp.composeapp.generated.resources.study_rating_hard
import slovymovyapp.composeapp.generated.resources.study_tap_to_check
import slovymovyapp.composeapp.generated.resources.study_tap_to_flip

@Composable
fun StudySessionScreen(
    viewModel: StudySessionViewModel,
    onCancel: () -> Unit,
    onEnd: () -> Unit,
) {
    StudySessionScreenContent(
        state = viewModel.state,
        completeScrollState = viewModel.completeScrollState,
        onCancel = onCancel,
        onEnd = onEnd,
        onReveal = viewModel::reveal,
        onRate = viewModel::rate,
        onPlayAudio = viewModel::playAudio,
        onStopAudio = viewModel::stopAudio,
        onRetry = viewModel::retry,
        onViewedSenseChange = viewModel::setViewedSense,
    )
}

@Composable
fun StudySessionScreenContent(
    state: StudySessionUiState,
    completeScrollState: ScrollState = ScrollState(0),
    onCancel: () -> Unit,
    onEnd: () -> Unit,
    onReveal: () -> Unit = {},
    onRate: (StudyRating) -> Unit = {},
    onPlayAudio: (String) -> Unit = {},
    onStopAudio: () -> Unit = {},
    onRetry: () -> Unit = {},
    onViewedSenseChange: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    when (state) {
        is StudySessionUiState.Loading -> {
            val progress = state.progress
            if (progress == null) {
                StudySessionMessageScaffold(
                    modifier = modifier,
                    onClose = onCancel,
                ) {
                    StudyLoadingIndicator()
                }
            } else {
                StudySessionLoadingContent(
                    progress = progress,
                    onClose = onCancel,
                    modifier = modifier,
                )
            }
        }

        StudySessionUiState.Empty -> StudySessionMessageScaffold(
            modifier = modifier,
            onClose = onCancel,
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
            onRate = onRate,
            onPlayAudio = onPlayAudio,
            onStopAudio = onStopAudio,
            onViewedSenseChange = onViewedSenseChange,
            modifier = modifier,
        )

        is StudySessionUiState.Complete -> StudySessionCompleteContent(
            reviewedCount = state.reviewedCount,
            message = state.message,
            scrollState = completeScrollState,
            onClose = onEnd,
            modifier = modifier,
        )
    }
}

@Composable
private fun StudySessionLoadingContent(
    progress: StudySessionProgressUiState,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
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
    reviewedCount: Int,
    message: String,
    scrollState: ScrollState,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
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
                        text = pluralStringResource(Res.plurals.study_complete_description, reviewedCount, reviewedCount),
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
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
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
    onRate: (StudyRating) -> Unit,
    onPlayAudio: (String) -> Unit,
    onStopAudio: () -> Unit,
    onViewedSenseChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val originalSenseId = state.card.activeSenseId
    val viewedSenseId = state.viewedSenseId ?: originalSenseId
    val isOnOriginalSense = !state.card.hasMultiSense ||
        viewedSenseId == originalSenseId

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
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
                onReveal = onReveal,
                viewedSenseId = viewedSenseId,
                onViewedSenseChange = onViewedSenseChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
            Spacer(Modifier.height(AppSpacing.lg))
            Box(Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.Center) {
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
}

@Composable
private fun StudySessionTopBar(
    progress: StudySessionProgressUiState?,
    onClose: () -> Unit,
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
            Text(
                text = stringResource(
                    Res.string.study_progress_count,
                    progress.safeCurrent,
                    progress.safeTotal,
                ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.size(48.dp))
    }
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
    onReveal: () -> Unit,
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(AppSpacing.xl),
        ) {
            StudyChip(label = card.chipLabel)
            Spacer(Modifier.height(AppSpacing.xl))
            SensePositionIndicator(
                activeSense = currentSense,
                senses = senses,
            )
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
            modifier = modifier,
        )

        is StudyCardUiState.Cloze -> ClozeFront(
            card = card,
            modifier = modifier,
        )

        is StudyCardUiState.Listening -> ListeningFront(
            card = card,
            isPlayingAudio = isPlayingAudio,
            isPreparingAudio = isPreparingAudio,
            onPlayAudio = onPlayAudio,
            onStopAudio = onStopAudio,
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
                modifier = Modifier.padding(top = AppSpacing.md),
            )
        }
    }
}

@Composable
private fun ProductionFront(
    card: StudyCardUiState.Production,
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
            FirstLetterHintView(hint = hint)
        }
    }
}

@Composable
private fun ClozeFront(
    card: StudyCardUiState.Cloze,
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
                StudyExampleBlock(
                    example = StudyExampleUiState(text = hint),
                )
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
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
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
            Text(
                text = stringResource(Res.string.study_listen_prompt),
                style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            MultiSenseFrontHint(card = card)
        }
    }
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
        FrontSenseDots(count = card.senses.size)
        Spacer(Modifier.height(AppSpacing.md))
        Text(
            text = pluralStringResource(
                Res.plurals.study_multi_sense_front_hint,
                card.senses.size,
                card.senses.size,
            ),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = MaterialTheme.serifFontFamily,
                fontStyle = FontStyle.Italic,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
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
private fun FrontSenseDots(
    count: Int,
    modifier: Modifier = Modifier,
) {
    SenseDotRow(
        count = count,
        activeIndex = null,
        activeColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
        inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
        modifier = modifier.clearAndSetSemantics {},
    )
}

@Composable
private fun SensePositionIndicator(
    activeSense: StudyCardSenseUiState,
    senses: List<StudyCardSenseUiState>,
    modifier: Modifier = Modifier,
) {
    val activeIndex = senses.indexOfFirst { it.id == activeSense.id }.takeIf { it >= 0 } ?: 0
    val label = stringResource(
        Res.string.study_sense_position_label,
        activeSense.num,
        senses.size,
    )
    val accessibilityLabel = stringResource(
        Res.string.study_sense_position_accessibility,
        activeSense.num,
        senses.size,
    )
    Surface(
        modifier = modifier.clearAndSetSemantics { contentDescription = accessibilityLabel },
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 12.5.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFeatureSettings = "tnum",
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )
            SenseDotRow(
                count = senses.size,
                activeIndex = activeIndex,
                activeColor = MaterialTheme.colorScheme.primary,
                inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
    }
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

@Composable
private fun StudyClozeText(
    cloze: StudyClozeTextUiState,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    val highlightColor = MaterialTheme.colorScheme.primary
    val highlightBackground = MaterialTheme.colorScheme.primaryContainer
    val blank = " ".repeat(cloze.answer.length.coerceAtLeast(6))

    val prefixPlain = HtmlTagParser.plainText(cloze.prefix)
    val answerPlain = HtmlTagParser.plainText(cloze.answer)
    val highlightStart = prefixPlain.length
    val highlightEnd = highlightStart + answerPlain.length

    val text = buildAnnotatedString {
        append(prefixPlain)
        if (cloze.filled) {
            pushStyle(SpanStyle(color = highlightColor, fontWeight = FontWeight.SemiBold))
            append(answerPlain)
            pop()
        } else {
            pushStyle(SpanStyle(color = highlightColor, textDecoration = TextDecoration.Underline))
            append(blank)
            pop()
        }
        append(HtmlTagParser.plainText(cloze.suffix))
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
private fun FirstLetterHintView(
    hint: FirstLetterHint,
    modifier: Modifier = Modifier,
) {
    val hintContentDescription = pluralStringResource(
        Res.plurals.study_hint_starts_with,
        hint.letterCount,
        hint.letter.toString(),
        hint.letterCount,
    )
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier.clearAndSetSemantics { contentDescription = hintContentDescription },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
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
                    text = "·".repeat(hint.dotCount.coerceAtMost(15)),
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
    Row(
        modifier = modifier,
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
            )
            Text(
                text = option.intervalLabel,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
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
        prefix = "Het was zo ",
        answer = "gezellig",
        suffix = " bij jullie thuis.",
    ),
    translationHint = "It was so <w>lovely</w> at your place.",
    back = StudyCardBackUiState(
        headline = "gezellig",
        secondary = "cosy, sociable",
        definition = "a feeling of warmth and conviviality from being together",
        cloze = StudyClozeTextUiState(
            prefix = "Het was zo ",
            answer = "gezellig",
            suffix = " bij jullie thuis.",
            filled = true,
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
private fun StudySessionCompletePreview(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean,
) {
    ThemedPreview(darkTheme = isDark) {
        StudySessionScreenContent(
            state = StudySessionUiState.Complete(reviewedCount = 12, message = "Goed gedaan!"),
            onCancel = {},
            onEnd = {},
        )
    }
}
