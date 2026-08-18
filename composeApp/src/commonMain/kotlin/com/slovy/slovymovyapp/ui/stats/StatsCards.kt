package com.slovy.slovymovyapp.ui.stats

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slovy.slovymovyapp.data.learning.stats.StatsPipelineStage
import com.slovy.slovymovyapp.i18n.ShortDuration
import com.slovy.slovymovyapp.ui.theme.AppSpacing
import com.slovy.slovymovyapp.ui.theme.serifFontFamily
import com.slovy.slovymovyapp.ui.theme.uiItalic
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import slovymovyapp.composeapp.generated.resources.*

@Composable
internal fun EffortCard(state: StatsUiState) {
    val todayCardsUnit = pluralStringResource(Res.plurals.stats_cards_unit, state.reviewsToday)
    val weekCardsUnit = pluralStringResource(Res.plurals.stats_cards_unit, state.reviewsWeek)
    val representativeCards = 1_000
    val representativeMinutes = 5 * 60 + 53
    val rowLayout = rememberEffortRowLayout(
        cardsCounts = listOf(
            formatCount(state.reviewsToday, state.isLoading),
            formatCount(state.reviewsWeek, state.isLoading),
            formatCount(representativeCards, isLoading = false),
        ),
        cardsUnits = listOf(
            todayCardsUnit,
            weekCardsUnit,
            pluralStringResource(Res.plurals.stats_cards_unit, representativeCards),
        ),
        durations = listOf(
            durationPlainText(state.minutesToday, state.isLoading),
            durationPlainText(state.minutesWeek, state.isLoading),
            durationPlainText(representativeMinutes, isLoading = false),
        ),
    )
    StatsCard(
        shape = RoundedCornerShape(16.dp),
        padding = PaddingValues(horizontal = AppSpacing.lg, vertical = AppSpacing.mdPlus),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
        ) {
            EffortRow(
                label = stringResource(Res.string.stats_today_label),
                cards = state.reviewsToday,
                cardsUnit = todayCardsUnit,
                minutes = state.minutesToday,
                isLoading = state.isLoading,
                layout = rowLayout,
            )
            EffortRow(
                label = stringResource(Res.string.stats_this_week_label),
                cards = state.reviewsWeek,
                cardsUnit = weekCardsUnit,
                minutes = state.minutesWeek,
                isLoading = state.isLoading,
                layout = rowLayout,
            )
        }
    }
}

@Composable
private fun EffortRow(
    label: String,
    cards: Int,
    cardsUnit: String,
    minutes: Int,
    isLoading: Boolean,
    layout: EffortRowLayout,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = MaterialTheme.serifFontFamily,
                fontStyle = MaterialTheme.uiItalic,
                fontSize = 13.5.sp,
                lineHeight = 18.sp,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .weight(1f)
                .padding(end = AppSpacing.md),
        )
        CountText(
            text = formatCount(cards, isLoading),
            style = effortNumberTextStyle(),
            color = loadingAwareContentColor(isLoading),
            textAlign = TextAlign.End,
            modifier = Modifier.width(layout.cardsNumberWidth),
        )
        Text(
            text = cardsUnit,
            style = effortUnitTextStyle(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .width(layout.cardsUnitWidth)
                .padding(start = AppSpacing.xs),
        )
        Text(
            // Matches the unit labels' metrics, but deliberately not their font: they use the serif
            // family, and U+00B7 taken from a Latin face sits at Latin mid-height, which reads low
            // beside full-height Han. Left on FontFamily.Default so the platform resolves the dot
            // from the same face as the CJK text around it.
            text = "·",
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 12.5.sp,
                lineHeight = 16.sp,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            textAlign = TextAlign.Center,
            modifier = Modifier.width(24.dp),
        )
        DurationText(
            minutes = minutes,
            isLoading = isLoading,
            modifier = Modifier.width(layout.durationWidth),
        )
    }
}

private data class EffortRowLayout(
    val cardsNumberWidth: Dp,
    val cardsUnitWidth: Dp,
    val durationWidth: Dp,
)

@Composable
private fun rememberEffortRowLayout(
    cardsCounts: List<String>,
    cardsUnits: List<String>,
    durations: List<String>,
): EffortRowLayout {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val numberStyle = effortNumberTextStyle()
    val unitStyle = effortUnitTextStyle()
    return remember(
        cardsCounts,
        cardsUnits,
        durations,
        numberStyle,
        unitStyle,
        density.density,
        density.fontScale,
        textMeasurer,
    ) {
        val cardsNumberWidth = measuredLabelWidth(
            labels = cardsCounts,
            style = numberStyle,
            textMeasurer = textMeasurer,
            density = density,
        ).plus(2.dp).coerceIn(38.dp, 64.dp)
        val cardsUnitWidth = measuredLabelWidth(
            labels = cardsUnits,
            style = unitStyle,
            textMeasurer = textMeasurer,
            density = density,
        ).plus(8.dp).coerceIn(43.dp, 84.dp)
        val durationWidth = measuredLabelWidth(
            labels = durations,
            style = numberStyle,
            textMeasurer = textMeasurer,
            density = density,
        ).plus(4.dp).coerceIn(54.dp, 108.dp)
        EffortRowLayout(
            cardsNumberWidth = cardsNumberWidth,
            cardsUnitWidth = cardsUnitWidth,
            durationWidth = durationWidth,
        )
    }
}

@Composable
private fun effortNumberTextStyle(): TextStyle =
    MaterialTheme.typography.bodyMedium.copy(
        fontFamily = MaterialTheme.serifFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 18.sp,
        letterSpacing = (-0.3).sp,
    )

@Composable
private fun effortUnitTextStyle(): TextStyle =
    MaterialTheme.typography.bodySmall.copy(
        fontFamily = MaterialTheme.serifFontFamily,
        fontStyle = MaterialTheme.uiItalic,
        fontSize = 12.5.sp,
        lineHeight = 16.sp,
    )

@Composable
private fun DurationText(
    minutes: Int,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
) {
    val numberColor = loadingAwareContentColor(isLoading)
    val unitColor = MaterialTheme.colorScheme.onSurfaceVariant
    val parts = durationParts(minutes, isLoading)
    val unitStyle = SpanStyle(
        fontFamily = MaterialTheme.serifFontFamily,
        fontStyle = MaterialTheme.uiItalic,
        fontSize = 12.5.sp,
        color = unitColor,
    )
    val text = buildAnnotatedString {
        if (parts.isEmpty()) {
            append("--")
            return@buildAnnotatedString
        }
        parts.forEachIndexed { index, part ->
            if (index > 0) append(" ")
            appendDurationPart(part, unitStyle)
        }
    }
    Text(
        text = text,
        style = effortNumberTextStyle(),
        color = numberColor,
        textAlign = TextAlign.End,
        maxLines = 1,
        softWrap = false,
        modifier = modifier,
    )
}

private data class ResolvedDurationPart(
    val value: Int,
    val unit: String,
)

@Composable
private fun durationParts(minutes: Int, isLoading: Boolean): List<ResolvedDurationPart> {
    if (isLoading) return emptyList()
    return ShortDuration.parts(minutes).map { part ->
        ResolvedDurationPart(value = part.value, unit = stringResource(part.unit))
    }
}

@Composable
private fun durationPlainText(minutes: Int, isLoading: Boolean): String {
    val parts = durationParts(minutes, isLoading)
    return if (parts.isEmpty()) {
        "--"
    } else {
        parts.joinToString(" ") { "${it.value} ${it.unit}" }
    }
}

private fun AnnotatedString.Builder.appendDurationPart(
    part: ResolvedDurationPart,
    unitStyle: SpanStyle,
) {
    append(part.value.toString())
    append(" ")
    withStyle(unitStyle) {
        append(part.unit)
    }
}

@Composable
internal fun LibraryCard(state: StatsUiState) {
    StatsCard(
        shape = RoundedCornerShape(16.dp),
        padding = PaddingValues(horizontal = AppSpacing.lg, vertical = AppSpacing.mdPlus),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.mdPlus)) {
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.mdPlus)) {
                LibraryMetric(
                    value = state.wordsTotal,
                    label = pluralStringResource(Res.plurals.stats_words_label, state.wordsTotal),
                    isLoading = state.isLoading,
                    modifier = Modifier.weight(1f),
                )
                LibraryMetric(
                    value = state.sensesTotal,
                    label = pluralStringResource(Res.plurals.stats_senses_label, state.sensesTotal),
                    isLoading = state.isLoading,
                    modifier = Modifier.weight(1f),
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
            PipelineBars(pipeline = state.pipeline, isLoading = state.isLoading)
            PipelineCaption()
        }
    }
}

@Composable
private fun LibraryMetric(
    value: Int,
    label: String,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(AppSpacing.xxs)) {
        CountText(
            text = formatCount(value, isLoading),
            style = MaterialTheme.typography.headlineMedium.copy(
                fontFamily = MaterialTheme.serifFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 30.sp,
                lineHeight = 30.sp,
                letterSpacing = (-0.5).sp,
            ),
            color = loadingAwareContentColor(isLoading),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = MaterialTheme.serifFontFamily,
                fontStyle = MaterialTheme.uiItalic,
                fontSize = 13.sp,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PipelineBars(pipeline: List<StatsPipelineStage>, isLoading: Boolean) {
    val maxCount = pipeline.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
    val countLanguage = Locale.current.language
    val countColumnWidth = if (pipeline.any { formatCountForLanguage(it.count, countLanguage).length > 3 }) {
        52.dp
    } else {
        36.dp
    }
    val rows = pipeline.map { stage -> stage to stageLabel(stage.id).uppercase() }
    val labelLayout = rememberPipelineLabelLayout(rows.map { (_, label) -> label })
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
        rows.forEach { (stage, label) ->
            val pct = if (isLoading) {
                loadingPipelineWidth(stage.id)
            } else {
                stage.count.toFloat() / maxCount.toFloat()
            }
            val animatedPct by animateFloatAsState(
                targetValue = pct,
                animationSpec = tween(durationMillis = STATS_REVEAL_ANIMATION_MS),
                label = "statsPipelineWidth",
            )
            val targetColor = if (isLoading) {
                MaterialTheme.colorScheme.outlineVariant
            } else {
                stageColor(stage.id)
            }
            val animatedColor by animateColorAsState(
                targetValue = targetColor,
                animationSpec = tween(durationMillis = STATS_REVEAL_ANIMATION_MS),
                label = "statsPipelineColor",
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.smPlus),
            ) {
                PipelineStageLabel(label = label, layout = labelLayout)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedPct)
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(animatedColor),
                    )
                }
                CountText(
                    text = formatCount(stage.count, isLoading, countLanguage),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = MaterialTheme.serifFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                        lineHeight = 15.sp,
                        letterSpacing = (-0.1).sp,
                    ),
                    color = loadingAwareContentColor(isLoading),
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(countColumnWidth),
                )
            }
        }
    }
}

@Composable
internal fun PipelineStageLabel(label: String, layout: PipelineLabelLayout) {
    Text(
        text = label,
        style = layout.style,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier.width(layout.width),
    )
}

internal data class PipelineLabelLayout(
    val style: TextStyle,
    val width: Dp,
)

@Composable
internal fun rememberPipelineLabelLayout(labels: List<String>): PipelineLabelLayout {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val baseLabelStyle = pipelineLabelTextStyle(11.sp)
    val compactLabelStyle = pipelineLabelTextStyle(10.sp)
    val minLabelWidth = 72.dp
    val maxLabelWidth = 104.dp
    val labelPadding = 2.dp
    return remember(
        labels,
        baseLabelStyle,
        compactLabelStyle,
        density.density,
        density.fontScale,
        textMeasurer,
    ) {
        val baseRequiredWidth = measuredLabelWidth(
            labels = labels,
            style = baseLabelStyle,
            textMeasurer = textMeasurer,
            density = density,
        ) + labelPadding
        val labelStyle = if (baseRequiredWidth > maxLabelWidth) compactLabelStyle else baseLabelStyle
        val measuredWidth = if (labelStyle == baseLabelStyle) {
            baseRequiredWidth
        } else {
            measuredLabelWidth(
                labels = labels,
                style = labelStyle,
                textMeasurer = textMeasurer,
                density = density,
            ) + labelPadding
        }
        PipelineLabelLayout(
            style = labelStyle,
            width = measuredWidth.coerceIn(minLabelWidth, maxLabelWidth),
        )
    }
}

@Composable
private fun pipelineLabelTextStyle(fontSize: TextUnit): TextStyle =
    MaterialTheme.typography.labelSmall.copy(
        fontWeight = FontWeight.Bold,
        fontSize = fontSize,
        lineHeight = 13.sp,
        letterSpacing = 0.4.sp,
    )

internal fun measuredLabelWidth(
    labels: List<String>,
    style: TextStyle,
    textMeasurer: TextMeasurer,
    density: Density,
): Dp {
    val maxWidthPx = labels.maxOfOrNull { label ->
        textMeasurer.measure(
            text = label,
            style = style,
            maxLines = 1,
            softWrap = false,
        ).size.width
    } ?: 0
    return with(density) { maxWidthPx.toDp() }
}

@Composable
private fun PipelineCaption() {
    val learned = stringResource(Res.string.stats_stage_learned)
    val captionText = stringResource(Res.string.stats_caption_learned, learned)
    val learnedStart = captionText.indexOf(learned)
    val caption = buildAnnotatedString {
        if (learnedStart == -1) {
            append(captionText)
            return@buildAnnotatedString
        }
        append(captionText.substring(0, learnedStart))
        withStyle(
            SpanStyle(
                color = MaterialTheme.colorScheme.onSurface,
                fontStyle = FontStyle.Normal,
                fontWeight = FontWeight.Medium,
            )
        ) {
            append(learned)
        }
        append(captionText.substring(learnedStart + learned.length))
    }
    Text(
        text = caption,
        style = MaterialTheme.typography.bodySmall.copy(
            fontFamily = MaterialTheme.serifFontFamily,
            fontStyle = MaterialTheme.uiItalic,
            fontSize = 12.sp,
            lineHeight = 17.sp,
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = AppSpacing.xxs),
    )
}

@Composable
internal fun CountText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    textAlign: TextAlign? = null,
) {
    Crossfade(
        targetState = CountTextTarget(text, color),
        animationSpec = tween(durationMillis = STATS_REVEAL_ANIMATION_MS),
        modifier = modifier,
        label = "statsCountText",
    ) { target ->
        Text(
            text = target.text,
            style = style,
            color = target.color,
            maxLines = 1,
            softWrap = false,
            textAlign = textAlign,
            modifier = if (textAlign == null) Modifier else Modifier.fillMaxWidth(),
        )
    }
}

private data class CountTextTarget(
    val text: String,
    val color: Color,
)

@Composable
internal fun StatsCard(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape,
    padding: PaddingValues,
    content: @Composable () -> Unit,
) {
    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline),
    ) {
        Box(modifier = Modifier.padding(padding)) {
            content()
        }
    }
}
