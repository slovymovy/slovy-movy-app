package com.slovy.slovymovyapp.ui.study

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slovy.slovymovyapp.ui.ThemePreviewProvider
import com.slovy.slovymovyapp.ui.ThemedPreview
import com.slovy.slovymovyapp.ui.theme.AppSpacing
import com.slovy.slovymovyapp.ui.theme.serifFontFamily
import org.jetbrains.compose.resources.stringResource
import slovymovyapp.composeapp.generated.resources.*

@Composable
internal fun MilestoneCompleteHero(
    streakDays: Int,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary

    Box(
        modifier = modifier
            .border(BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)), RoundedCornerShape(22.dp))
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        MilestoneConfetti(
            primary = primary,
            tertiary = tertiary,
            modifier = Modifier.matchParentSize(),
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
            modifier = Modifier
                .align(Alignment.Center)
                .padding(start = 22.dp, top = 22.dp, end = 22.dp, bottom = 24.dp),
        ) {
            val medallionCount by rememberCountUp(
                target = streakDays,
                delayMillis = MedallionCountUpDelayMillis,
                durationMillis = MedallionCountUpDurationMillis,
                label = "medallionCount",
            )
            Box(
                modifier = Modifier
                    .rewardEntrance(RewardElement.MEDALLION)
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = medallionCount.toString(),
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontFamily = MaterialTheme.serifFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 34.sp,
                        lineHeight = 34.sp,
                        letterSpacing = (-1.2).sp,
                    ),
                    color = MaterialTheme.colorScheme.onPrimary,
                    textAlign = TextAlign.Center,
                )
            }
            Text(
                text = milestoneTitle(streakDays),
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = MaterialTheme.serifFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 22.sp,
                    lineHeight = 24.sp,
                    letterSpacing = (-0.3).sp,
                ),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center,
            )
            Text(
                text = milestoneSubtitle(streakDays),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = MaterialTheme.serifFontFamily,
                    fontStyle = FontStyle.Italic,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                ),
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 250.dp),
            )
        }
    }
}

@Composable
private fun milestoneTitle(days: Int): String = when (days) {
    7 -> stringResource(Res.string.study_complete_milestone_title_7)
    14 -> stringResource(Res.string.study_complete_milestone_title_14)
    30 -> stringResource(Res.string.study_complete_milestone_title_30)
    50 -> stringResource(Res.string.study_complete_milestone_title_50)
    100 -> stringResource(Res.string.study_complete_milestone_title_100)
    else -> stringResource(Res.string.study_complete_milestone_title_generic, days)
}

@Composable
private fun milestoneSubtitle(days: Int): String = when (days) {
    7 -> stringResource(Res.string.study_complete_milestone_subtitle_7)
    14 -> stringResource(Res.string.study_complete_milestone_subtitle_14)
    30 -> stringResource(Res.string.study_complete_milestone_subtitle_30)
    50 -> stringResource(Res.string.study_complete_milestone_subtitle_50)
    100 -> stringResource(Res.string.study_complete_milestone_subtitle_100)
    else -> stringResource(Res.string.study_complete_milestone_subtitle_generic, days)
}

@Composable
private fun MilestoneConfetti(
    primary: Color,
    tertiary: Color,
    modifier: Modifier = Modifier,
) {
    val pieces = remember(primary, tertiary) {
        listOf(
            ConfettiPiece(0.12f, 0.18f, ConfettiShape.DOT, primary, 5f, 0f),
            ConfettiPiece(0.88f, 0.24f, ConfettiShape.TICK, tertiary, 8f, 28f),
            ConfettiPiece(0.22f, 0.44f, ConfettiShape.TICK, Color(0xFFE9C049), 9f, -18f),
            ConfettiPiece(0.78f, 0.50f, ConfettiShape.DOT, primary, 4f, 0f),
            ConfettiPiece(0.36f, 0.08f, ConfettiShape.TICK, Color(0xFFC46060), 7f, 12f),
            ConfettiPiece(0.68f, 0.12f, ConfettiShape.DOT, tertiary, 4f, 0f),
            ConfettiPiece(0.08f, 0.70f, ConfettiShape.TICK, primary, 8f, -36f),
            ConfettiPiece(0.92f, 0.76f, ConfettiShape.TICK, Color(0xFFE9C049), 9f, 22f),
            ConfettiPiece(0.52f, 0.04f, ConfettiShape.DOT, Color(0xFFC46060), 5f, 0f),
            ConfettiPiece(0.16f, 0.88f, ConfettiShape.DOT, Color(0xFFE9C049), 6f, 0f),
            ConfettiPiece(0.84f, 0.92f, ConfettiShape.TICK, tertiary, 7f, 8f),
            ConfettiPiece(0.46f, 0.96f, ConfettiShape.TICK, primary, 6f, -12f),
        )
    }

    // One-shot fall per piece (§8b rwFall), staggered by 55ms. Never loops; finished state = 1f.
    val fall = pieces.mapIndexed { index, _ ->
        rememberRewardEntranceFloat(
            hiddenValue = 0f,
            playedValue = 1f,
            delayMillis = ConfettiBaseDelayMillis + index * ConfettiPieceStaggerMillis,
            durationMillis = ConfettiDurationMillis,
            easing = ConfettiEase,
            label = "confetti[$index]",
        )
    }

    Canvas(modifier = modifier) {
        pieces.forEachIndexed { index, piece ->
            val t = fall[index].value
            // rwFall: drops from -42px, fades in over the first 30% of travel, lands upright.
            val fallAlpha = (t / 0.3f).coerceIn(0f, 1f)
            val alpha = 0.55f * fallAlpha
            if (alpha <= 0f) return@forEachIndexed
            val center = Offset(
                x = piece.x * size.width,
                y = piece.y * size.height + (1f - t) * (-42.dp.toPx()),
            )
            rotate(degrees = piece.rotation + (1f - t) * -40f, pivot = center) {
                if (piece.shape == ConfettiShape.DOT) {
                    drawCircle(
                        color = piece.color.copy(alpha = alpha),
                        radius = piece.size.dp.toPx() / 2f,
                        center = center,
                    )
                } else {
                    drawRoundRect(
                        color = piece.color.copy(alpha = alpha),
                        topLeft = Offset(
                            x = center.x - piece.size.dp.toPx() * 1.1f,
                            y = center.y - piece.size.dp.toPx() * 0.25f,
                        ),
                        size = Size(
                            width = piece.size.dp.toPx() * 2.2f,
                            height = piece.size.dp.toPx() * 0.5f,
                        ),
                    )
                }
            }
        }
    }
}

private data class ConfettiPiece(
    val x: Float,
    val y: Float,
    val shape: ConfettiShape,
    val color: Color,
    val size: Float,
    val rotation: Float,
)

private enum class ConfettiShape {
    DOT,
    TICK,
}

// Run this preview in interactive mode and tap the hero to (re)play the entrance choreography.
@Preview
@Composable
private fun MilestoneCompleteHeroEntrancePreview(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean,
) {
    ThemedPreview(darkTheme = isDark) {
        RewardEntrancePreviewPlayer {
            MilestoneCompleteHero(streakDays = 7)
        }
    }
}
