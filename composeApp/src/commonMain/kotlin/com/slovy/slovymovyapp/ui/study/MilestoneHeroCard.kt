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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slovy.slovymovyapp.ui.theme.AppSpacing
import com.slovy.slovymovyapp.ui.theme.serifFontFamily
import com.slovy.slovymovyapp.ui.theme.uiItalic

internal data class MilestoneHeroColors(
    val container: Color,
    val onContainer: Color,
    val accent: Color,
    val onAccent: Color,
    val confettiSecondary: Color,
)

// The shared medallion-card skeleton for milestone heroes (streak and words-learned).
// The two are choreography siblings by design: identical pop/count-up/confetti timing,
// differing only in palette, copy, and medallion digit size.
@Composable
internal fun MilestoneHeroCard(
    medallionTarget: Int,
    title: String,
    subtitle: String,
    colors: MilestoneHeroColors,
    modifier: Modifier = Modifier,
    medallionFontSize: TextUnit = 34.sp,
) {
    Box(
        modifier = modifier
            .border(BorderStroke(0.5.dp, colors.accent.copy(alpha = 0.3f)), RoundedCornerShape(22.dp))
            .clip(RoundedCornerShape(22.dp))
            .background(colors.container),
        contentAlignment = Alignment.Center,
    ) {
        MilestoneConfetti(
            accent = colors.accent,
            secondary = colors.confettiSecondary,
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
                target = medallionTarget,
                delayMillis = MedallionCountUpDelayMillis,
                durationMillis = MedallionCountUpDurationMillis,
                label = "medallionCount",
            )
            Box(
                modifier = Modifier
                    .rewardEntrance(RewardElement.MEDALLION)
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(colors.accent),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = medallionCount.toString(),
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontFamily = MaterialTheme.serifFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = medallionFontSize,
                        lineHeight = medallionFontSize,
                        letterSpacing = (-1.2).sp,
                    ),
                    color = colors.onAccent,
                    textAlign = TextAlign.Center,
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = MaterialTheme.serifFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 22.sp,
                    lineHeight = 24.sp,
                    letterSpacing = (-0.3).sp,
                ),
                color = colors.onContainer,
                textAlign = TextAlign.Center,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = MaterialTheme.serifFontFamily,
                    fontStyle = MaterialTheme.uiItalic,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                ),
                color = colors.onContainer.copy(alpha = 0.78f),
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 250.dp),
            )
        }
    }
}

@Composable
private fun MilestoneConfetti(
    accent: Color,
    secondary: Color,
    modifier: Modifier = Modifier,
) {
    val pieces = remember(accent, secondary) {
        listOf(
            ConfettiPiece(0.12f, 0.18f, ConfettiShape.DOT, accent, 5f, 0f),
            ConfettiPiece(0.88f, 0.24f, ConfettiShape.TICK, secondary, 8f, 28f),
            ConfettiPiece(0.22f, 0.44f, ConfettiShape.TICK, Color(0xFFE9C049), 9f, -18f),
            ConfettiPiece(0.78f, 0.50f, ConfettiShape.DOT, accent, 4f, 0f),
            ConfettiPiece(0.36f, 0.08f, ConfettiShape.TICK, Color(0xFFC46060), 7f, 12f),
            ConfettiPiece(0.68f, 0.12f, ConfettiShape.DOT, secondary, 4f, 0f),
            ConfettiPiece(0.08f, 0.70f, ConfettiShape.TICK, accent, 8f, -36f),
            ConfettiPiece(0.92f, 0.76f, ConfettiShape.TICK, Color(0xFFE9C049), 9f, 22f),
            ConfettiPiece(0.52f, 0.04f, ConfettiShape.DOT, Color(0xFFC46060), 5f, 0f),
            ConfettiPiece(0.16f, 0.88f, ConfettiShape.DOT, Color(0xFFE9C049), 6f, 0f),
            ConfettiPiece(0.84f, 0.92f, ConfettiShape.TICK, secondary, 7f, 8f),
            ConfettiPiece(0.46f, 0.96f, ConfettiShape.TICK, accent, 6f, -12f),
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
