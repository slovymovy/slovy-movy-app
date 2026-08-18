package com.slovy.slovymovyapp.ui.developer

import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.ScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.slovy.slovymovyapp.data.learning.intake.IntakeRunMode
import com.slovy.slovymovyapp.ui.components.SpinningProgressIndicator
import com.slovy.slovymovyapp.ui.study.StudySessionCompletePreviewCase
import com.slovy.slovymovyapp.ui.theme.AppSpacing
import org.jetbrains.compose.resources.stringResource
import slovymovyapp.composeapp.generated.resources.*

@Composable
internal fun TimeMachineCard(
    options: List<TimeShiftOption>,
    isBusy: Boolean,
    onShift: (TimeShiftOption) -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        ) {
            Text(
                text = stringResource(Res.string.developer_time_machine_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            options.chunked(3).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                ) {
                    row.forEach { option ->
                        ScaryFilledTonalButton(
                            text = option.label,
                            onClick = { onShift(option) },
                            enabled = !isBusy,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    // Pad incomplete rows so buttons keep their column widths.
                    repeat(3 - row.size) {
                        Box(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
internal fun DeveloperNonDebugWarningCard() {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
        ) {
            Icon(
                imageVector = Icons.Outlined.Warning,
                contentDescription = stringResource(Res.string.developer_non_debug_warning_icon),
            )
            Text(
                text = stringResource(Res.string.developer_non_debug_warning),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
internal fun DeveloperTerminalCard(
    isBusy: Boolean,
    currentActionLabel: String?,
    terminalLines: List<String>,
    terminalRevision: Long,
    scrollState: ScrollState,
    onClear: () -> Unit,
) {
    LaunchedEffect(terminalRevision) {
        scrollState.scrollTo(scrollState.maxValue)
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
            ) {
                Text(
                    text = stringResource(Res.string.developer_terminal_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onClear) {
                    Text(stringResource(Res.string.developer_terminal_clear))
                }
            }
            if (isBusy) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                ) {
                    SpinningProgressIndicator(modifier = Modifier.size(16.dp))
                    Text(
                        text = stringResource(
                            Res.string.developer_terminal_running,
                            currentActionLabel ?: stringResource(Res.string.developer_working_status),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            SelectionContainer {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
                ) {
                    val visibleLines = if (terminalLines.isEmpty()) {
                        listOf(stringResource(Res.string.developer_terminal_ready))
                    } else {
                        terminalLines
                    }
                    visibleLines.forEach { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun CompletionGalleryCard(
    cases: List<StudySessionCompletePreviewCase>,
    onOpen: (StudySessionCompletePreviewCase) -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        ) {
            Text(
                text = stringResource(Res.string.developer_completion_gallery_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            cases.forEach { preview ->
                FilledTonalButton(
                    onClick = { onOpen(preview) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.Start,
                    ) {
                        Text(
                            text = preview.label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = preview.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.78f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun IntakeCard(
    modes: List<IntakeRunMode>,
    isBusy: Boolean,
    onRun: (IntakeRunMode) -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        ) {
            Text(
                text = stringResource(Res.string.developer_intake_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
            ) {
                modes.forEach { mode ->
                    if (mode == IntakeRunMode.CONTINUE_NOW) {
                        ScaryFilledTonalButton(
                            text = intakeModeLabel(mode),
                            onClick = { onRun(mode) },
                            enabled = !isBusy,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        FilledTonalButton(
                            onClick = { onRun(mode) },
                            enabled = !isBusy,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(intakeModeLabel(mode))
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun DeveloperCachesCard(
    isBusy: Boolean,
    onClearListsCache: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        ) {
            Text(
                text = stringResource(Res.string.developer_caches_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FilledTonalButton(
                onClick = onClearListsCache,
                enabled = !isBusy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.developer_clear_lists_cache))
            }
        }
    }
}

@Composable
internal fun DeveloperDangerCard(
    isDebugBuild: Boolean,
    isBusy: Boolean,
    onRemoveSuspendedCards: () -> Unit,
    onRemoveAllLearningCards: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        ) {
            Text(
                text = stringResource(Res.string.developer_danger_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ScaryFilledTonalButton(
                text = stringResource(Res.string.developer_remove_suspended_cards),
                onClick = onRemoveSuspendedCards,
                enabled = !isBusy,
                modifier = Modifier.fillMaxWidth(),
            )
            ScaryFilledTonalButton(
                text = stringResource(Res.string.developer_remove_all_learning_cards),
                onClick = onRemoveAllLearningCards,
                enabled = !isBusy && isDebugBuild,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ScaryFilledTonalButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier,
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Text(text)
    }
}
