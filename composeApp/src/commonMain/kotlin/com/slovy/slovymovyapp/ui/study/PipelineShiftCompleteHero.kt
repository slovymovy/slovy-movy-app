package com.slovy.slovymovyapp.ui.study

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slovy.slovymovyapp.data.learning.stats.StatsPipelineStageId
import com.slovy.slovymovyapp.ui.ThemePreviewProvider
import com.slovy.slovymovyapp.ui.ThemedPreview
import com.slovy.slovymovyapp.ui.theme.AppSpacing
import com.slovy.slovymovyapp.ui.theme.serifFontFamily
import org.jetbrains.compose.resources.stringResource
import slovymovyapp.composeapp.generated.resources.*

@Composable
internal fun PipelineShiftCompleteHero(
    stages: List<PipelineShiftStageUiState>,
    modifier: Modifier = Modifier,
) {
    val maxValue = stages
        .flatMap { listOf(it.before, it.after) }
        .maxOrNull()
        ?.coerceAtLeast(1) ?: 1

    Surface(
        modifier = modifier.widthIn(max = 332.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
        ) {
            Text(
                text = stringResource(Res.string.study_complete_shift_title),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = MaterialTheme.serifFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp,
                    lineHeight = 19.sp,
                    letterSpacing = (-0.2).sp,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth(),
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                stages.forEachIndexed { index, stage ->
                    PipelineShiftRow(
                        stage = stage,
                        maxValue = maxValue,
                        index = index,
                    )
                }
            }
        }
    }
}

@Composable
private fun PipelineShiftRow(
    stage: PipelineShiftStageUiState,
    maxValue: Int,
    index: Int,
) {
    val beforePct = stage.before.toFloat() / maxValue.toFloat()
    val afterPct = stage.after.toFloat() / maxValue.toFloat()

    // Solid fill slides beforePct -> afterPct (§4f / §8e); ghost stays pinned at beforePct.
    val solidFraction by rememberRewardEntranceFloat(
        hiddenValue = beforePct,
        playedValue = afterPct,
        delayMillis = ShiftBarBaseDelayMillis + index * ShiftBarRowStaggerMillis,
        durationMillis = ShiftBarDurationMillis,
        easing = ShiftBarEase,
        label = "bar[$index]",
    )

    val deltaCount by rememberCountUp(
        target = stage.delta,
        delayMillis = ShiftBarBaseDelayMillis + index * ShiftBarRowStaggerMillis + ShiftDeltaExtraDelayMillis,
        durationMillis = ShiftDeltaDurationMillis,
        label = "delta[$index]",
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = stageLabel(stage.id).uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 10.5.sp,
                lineHeight = 12.sp,
                letterSpacing = 0.4.sp,
            ),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (stage.delta == 0) 0.5f else 1f),
            maxLines = 1,
            softWrap = false,
            autoSize = TextAutoSize.StepBased(
                minFontSize = 8.5.sp,
                maxFontSize = 10.5.sp,
                stepSize = 0.2.sp,
            ),
            modifier = Modifier.width(72.dp),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(beforePct)
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(stageColor(stage.id).copy(alpha = 0.28f)),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(solidFraction)
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(stageColor(stage.id)),
            )
        }
        Text(
            text = formatDelta(stage.delta, deltaCount),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = MaterialTheme.serifFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.5.sp,
                lineHeight = 14.sp,
                letterSpacing = (-0.2).sp,
            ),
            color = if (stage.delta == 0) {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            textAlign = TextAlign.End,
            modifier = Modifier.width(44.dp),
        )
    }
}

@Composable
private fun stageLabel(stage: StatsPipelineStageId): String = when (stage) {
    StatsPipelineStageId.QUEUE -> stringResource(Res.string.stats_stage_queue)
    StatsPipelineStageId.NEW -> stringResource(Res.string.stats_stage_new)
    StatsPipelineStageId.FRESH -> stringResource(Res.string.stats_stage_fresh)
    StatsPipelineStageId.MIDDLE -> stringResource(Res.string.stats_stage_middle)
    StatsPipelineStageId.STRONG -> stringResource(Res.string.stats_stage_strong)
    StatsPipelineStageId.LEARNED -> stringResource(Res.string.stats_stage_learned)
}

private fun stageColor(stage: StatsPipelineStageId): Color = when (stage) {
    StatsPipelineStageId.QUEUE -> Color(0xFF9B8FB8)
    StatsPipelineStageId.NEW -> Color(0xFFC8B59A)
    StatsPipelineStageId.FRESH -> Color(0xFFD9866C)
    StatsPipelineStageId.MIDDLE -> Color(0xFFD6A85C)
    StatsPipelineStageId.STRONG -> Color(0xFF6E9CB0)
    StatsPipelineStageId.LEARNED -> Color(0xFF7CB078)
}

// Signed delta with the true minus glyph; the magnitude tracks the count-up while the sign holds.
private fun formatDelta(delta: Int, current: Int): String = when {
    delta == 0 -> "\u2014"
    delta > 0 -> "+${current.coerceAtLeast(0)}"
    else -> "\u2212${(-current).coerceAtLeast(0)}"
}

// Run this preview in interactive mode and tap the hero to (re)play the bar/delta choreography.
@Preview
@Composable
private fun PipelineShiftCompleteHeroEntrancePreview(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean,
) {
    ThemedPreview(darkTheme = isDark) {
        RewardEntrancePreviewPlayer {
            PipelineShiftCompleteHero(
                stages = listOf(
                    PipelineShiftStageUiState(StatsPipelineStageId.NEW, before = 18, after = 12),
                    PipelineShiftStageUiState(StatsPipelineStageId.FRESH, before = 22, after = 28),
                    PipelineShiftStageUiState(StatsPipelineStageId.MIDDLE, before = 16, after = 20),
                    PipelineShiftStageUiState(StatsPipelineStageId.STRONG, before = 11, after = 13),
                    PipelineShiftStageUiState(StatsPipelineStageId.LEARNED, before = 5, after = 6),
                ),
            )
        }
    }
}
