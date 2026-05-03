package com.slovy.slovymovyapp.ui.study

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.slovy.slovymovyapp.data.util.HtmlTagParser
import com.slovy.slovymovyapp.i18n.UiText
import com.slovy.slovymovyapp.i18n.resolve
import com.slovy.slovymovyapp.ui.ThemePreviewProvider
import com.slovy.slovymovyapp.ui.ThemedPreview
import com.slovy.slovymovyapp.ui.theme.AppSpacing
import com.slovy.slovymovyapp.ui.theme.serifFontFamily
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource
import slovymovyapp.composeapp.generated.resources.Res
import slovymovyapp.composeapp.generated.resources.study_action_close
import slovymovyapp.composeapp.generated.resources.study_action_retry
import slovymovyapp.composeapp.generated.resources.study_complete_description
import slovymovyapp.composeapp.generated.resources.study_complete_title
import slovymovyapp.composeapp.generated.resources.study_empty_description
import slovymovyapp.composeapp.generated.resources.study_empty_title
import slovymovyapp.composeapp.generated.resources.study_error_title
import slovymovyapp.composeapp.generated.resources.study_fill_blank
import slovymovyapp.composeapp.generated.resources.study_chip_fill_in
import slovymovyapp.composeapp.generated.resources.study_chip_listen
import slovymovyapp.composeapp.generated.resources.study_listen_prompt
import slovymovyapp.composeapp.generated.resources.study_loading
import slovymovyapp.composeapp.generated.resources.study_play_prompt_audio
import slovymovyapp.composeapp.generated.resources.study_play_word_audio
import slovymovyapp.composeapp.generated.resources.study_stop_audio
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
    onClose: () -> Unit,
) {
    StudySessionScreenContent(
        state = viewModel.state,
        onClose = onClose,
        onReveal = viewModel::reveal,
        onRate = viewModel::rate,
        onPlayAudio = viewModel::playAudio,
        onStopAudio = viewModel::stopAudio,
        onRetry = viewModel::retry,
    )
}

@Composable
fun StudySessionScreenContent(
    state: StudySessionUiState,
    onClose: () -> Unit,
    onReveal: () -> Unit = {},
    onRate: (StudyRating) -> Unit = {},
    onPlayAudio: (String) -> Unit = {},
    onStopAudio: () -> Unit = {},
    onRetry: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    when (state) {
        is StudySessionUiState.Loading -> {
            val progress = state.progress
            if (progress == null) {
                StudySessionMessageScaffold(
                    modifier = modifier,
                    onClose = onClose,
                ) {
                    StudyLoadingIndicator()
                }
            } else {
                StudySessionLoadingContent(
                    progress = progress,
                    onClose = onClose,
                    modifier = modifier,
                )
            }
        }

        StudySessionUiState.Empty -> StudySessionMessageScaffold(
            modifier = modifier,
            onClose = onClose,
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
            onClose = onClose,
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
            onClose = onClose,
            onReveal = onReveal,
            onRate = onRate,
            onPlayAudio = onPlayAudio,
            onStopAudio = onStopAudio,
            modifier = modifier,
        )

        is StudySessionUiState.Complete -> StudySessionMessageScaffold(
            modifier = modifier,
            onClose = onClose,
        ) {
            Text(
                text = stringResource(Res.string.study_complete_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(AppSpacing.sm))
            Text(
                text = stringResource(Res.string.study_complete_description, state.reviewedCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
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
            StudySegmentProgress(progress = progress)
            Spacer(Modifier.height(AppSpacing.md))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
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
                progress = state.progress,
                onClose = onClose,
            )
            Spacer(Modifier.height(AppSpacing.md))
            StudySegmentProgress(progress = state.progress)
            Spacer(Modifier.height(AppSpacing.md))
            StudyCardSurface(
                card = state.card,
                side = state.side,
                isPlayingAudio = state.isPlayingAudio,
                isPreparingAudio = state.isPreparingAudio,
                onPlayAudio = onPlayAudio,
                onStopAudio = onStopAudio,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
            Spacer(Modifier.height(AppSpacing.lg))
            if (state.side == StudyCardSide.FRONT) {
                Button(
                    onClick = onReveal,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    contentPadding = PaddingValues(horizontal = AppSpacing.lg),
                ) {
                    Text(
                        text = stringResource(
                            if (state.card is StudyCardUiState.Recognition) {
                                Res.string.study_tap_to_flip
                            } else {
                                Res.string.study_tap_to_check
                            },
                        ),
                    )
                }
            } else {
                StudyRatingRow(
                    ratings = state.ratingOptions,
                    enabled = !state.isSubmittingReview,
                    onRate = onRate,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun StudySessionTopBar(
    progress: StudySessionProgressUiState?,
    onClose: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
    ) {
        IconButton(
            onClick = onClose,
            modifier = Modifier.align(Alignment.CenterStart),
        ) {
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
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

@Composable
private fun StudySegmentProgress(
    progress: StudySessionProgressUiState,
    modifier: Modifier = Modifier,
) {
    val total = progress.safeTotal.coerceAtLeast(1)
    val segments = total.coerceAtMost(20)
    val filledSegments = if (total <= 20) {
        progress.safeCurrent.coerceIn(0, segments)
    } else {
        ((progress.safeCurrent.toFloat() / total) * segments)
            .roundToInt()
            .coerceIn(if (progress.safeCurrent > 0) 1 else 0, segments)
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs),
    ) {
        repeat(segments) { index ->
            val selected = index < filledSegments
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(
                        if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        },
                    ),
            )
        }
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
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(AppSpacing.xl),
        ) {
            StudyChip(label = card.chipLabel)
            Spacer(Modifier.height(AppSpacing.xl))
            if (side == StudyCardSide.FRONT) {
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
            } else {
                StudyCardBack(
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
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
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
    }
}

@Composable
private fun ProductionFront(
    card: StudyCardUiState.Production,
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
            Text(
                text = card.promptLabel.resolve(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            StudyTaggedText(
                text = card.promptText,
                style = MaterialTheme.typography.displaySmall.copy(
                    fontFamily = MaterialTheme.serifFontFamily,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            card.firstLetterHint?.let { hint ->
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ) {
                    Text(
                        text = hint,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = AppSpacing.lg, vertical = AppSpacing.sm),
                    )
                }
            }
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
                text = stringResource(Res.string.study_fill_blank),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
            )
            StudyClozeText(
                cloze = card.prompt,
                filled = false,
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
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
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
        }
    }
}

@Composable
private fun StudyCardBack(
    back: StudyCardBackUiState,
    isPlayingAudio: Boolean,
    isPreparingAudio: Boolean,
    onPlayAudio: (String) -> Unit,
    onStopAudio: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
    ) {
        back.cloze?.let { cloze ->
            StudyClozeText(
                cloze = cloze,
                filled = true,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = MaterialTheme.serifFontFamily,
                ),
            )
            Spacer(Modifier.height(AppSpacing.xs))
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StudyTaggedText(
                text = back.headline,
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontFamily = MaterialTheme.serifFontFamily,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Start,
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
                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
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
    filled: Boolean,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    val highlightColor = MaterialTheme.colorScheme.primary
    val highlightBackground = MaterialTheme.colorScheme.primaryContainer
    val blank = " ".repeat(cloze.answer.length.coerceAtLeast(6))
    val text = buildAnnotatedString {
        append(HtmlTagParser.plainText(cloze.prefix))
        if (filled) {
            pushStyle(
                SpanStyle(
                    color = highlightColor,
                    background = highlightBackground,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
            append(HtmlTagParser.plainText(cloze.answer))
            pop()
        } else {
            pushStyle(
                SpanStyle(
                    color = highlightColor,
                    textDecoration = TextDecoration.Underline,
                ),
            )
            append(blank)
            pop()
        }
        append(HtmlTagParser.plainText(cloze.suffix))
    }
    Text(
        text = text,
        style = style,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun StudyTaggedText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    textAlign: TextAlign? = null,
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
    Text(
        text = annotated,
        style = style,
        color = color,
        textAlign = textAlign,
        modifier = modifier,
    )
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

private fun sampleRatings() = listOf(
    StudyRatingUiState(StudyRating.AGAIN, "< 1m"),
    StudyRatingUiState(StudyRating.HARD, "6m"),
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

private fun productionCard() = StudyCardUiState.Production(
    id = "production",
    chipLabel = UiText.Plain("EN -> NL"),
    promptLabel = UiText.Resource(Res.string.study_prompt_translate_to, listOf("Dutch")),
    promptText = "cosy, sociable",
    firstLetterHint = "g _ _ _ _ _ _ _ _",
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
            onClose = {},
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
            onClose = {},
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
            onClose = {},
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
            onClose = {},
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
            onClose = {},
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
            onClose = {},
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
            onClose = {},
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
            onClose = {},
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
            onClose = {},
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
            onClose = {},
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
            onClose = {},
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
            onClose = {},
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
            state = StudySessionUiState.Complete(reviewedCount = 12),
            onClose = {},
        )
    }
}
