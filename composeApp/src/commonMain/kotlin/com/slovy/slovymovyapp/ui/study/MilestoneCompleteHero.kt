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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = streakDays.toString(),
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
    val pieces = listOf(
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
    Canvas(modifier = modifier) {
        pieces.forEachIndexed { index, piece ->
            val alpha = 0.55f
            val center = Offset(piece.x * size.width, piece.y * size.height)
            rotate(degrees = piece.rotation, pivot = center) {
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
