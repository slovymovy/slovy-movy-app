package com.slovy.slovymovyapp.ui.stats

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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slovy.slovymovyapp.data.learning.stats.StatsPracticeDay
import com.slovy.slovymovyapp.data.learning.stats.StatsYearMonth
import com.slovy.slovymovyapp.ui.theme.AppSpacing
import com.slovy.slovymovyapp.ui.theme.serifFontFamily
import com.slovy.slovymovyapp.ui.theme.uiItalic
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import slovymovyapp.composeapp.generated.resources.*

@Composable
internal fun StreakCard(
    state: StatsUiState,
    onStepMonth: (Int) -> Unit,
) {
    StatsCard(
        shape = RoundedCornerShape(18.dp),
        padding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    CountText(
                        text = formatCount(state.streakDays, state.isLoading),
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontFamily = MaterialTheme.serifFontFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 36.sp,
                            lineHeight = 32.sp,
                            letterSpacing = (-0.8).sp,
                        ),
                        color = loadingAwareContentColor(state.isLoading),
                    )
                    Text(
                        text = pluralStringResource(Res.plurals.stats_day_streak, state.streakDays),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = MaterialTheme.serifFontFamily,
                            fontStyle = MaterialTheme.uiItalic,
                            fontSize = 12.5.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    CountText(
                        text = formatCount(state.activeDaysTotal, state.isLoading),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontFamily = MaterialTheme.serifFontFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 27.sp,
                            lineHeight = 25.sp,
                            letterSpacing = (-0.5).sp,
                        ),
                        color = loadingAwareContentColor(state.isLoading),
                    )
                    Text(
                        text = stringResource(Res.string.stats_active_days_all_time),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = MaterialTheme.serifFontFamily,
                            fontStyle = MaterialTheme.uiItalic,
                            fontSize = 12.5.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                    )
                }
            }
            CalendarGrid(state = state, onStepMonth = onStepMonth)
        }
    }
}

@Composable
private fun MonthStepper(
    viewMonth: StatsYearMonth,
    today: LocalDate,
    onStepMonth: (Int) -> Unit,
) {
    val atCurrentMonth = viewMonth.year == today.year && viewMonth.monthZeroBased == today.month.ordinal
    val monthLabelStyle = MaterialTheme.typography.bodySmall.copy(
        fontWeight = FontWeight.Medium,
        fontSize = 13.5.sp,
        letterSpacing = 0.1.sp,
    )
    val monthLabels = statsMonthLabels(viewMonth.year)
    val monthLabelWidth = rememberMonthLabelWidth(
        labels = monthLabels,
        style = monthLabelStyle,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        IconButton(
            onClick = { onStepMonth(-1) },
            modifier = Modifier.size(28.dp),
        ) {
            Text(
                text = "‹",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = monthLabels[viewMonth.monthZeroBased],
            style = monthLabelStyle,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.width(monthLabelWidth),
        )
        if (atCurrentMonth) {
            Spacer(Modifier.size(28.dp))
        } else {
            IconButton(
                onClick = { onStepMonth(1) },
                modifier = Modifier.size(28.dp),
            ) {
                Text(
                    text = "›",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// Month and year go through a format resource rather than string interpolation: Chinese writes the
// year first (2026年8月), so the order has to be the locale's to decide, not the call site's.
@Composable
private fun statsMonthLabels(year: Int): List<String> = listOf(
    Res.string.stats_month_january,
    Res.string.stats_month_february,
    Res.string.stats_month_march,
    Res.string.stats_month_april,
    Res.string.stats_month_may,
    Res.string.stats_month_june,
    Res.string.stats_month_july,
    Res.string.stats_month_august,
    Res.string.stats_month_september,
    Res.string.stats_month_october,
    Res.string.stats_month_november,
    Res.string.stats_month_december,
).map { month ->
    stringResource(Res.string.stats_month_year, stringResource(month), year)
}

@Composable
private fun rememberMonthLabelWidth(labels: List<String>, style: TextStyle): Dp {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(labels, style, density.density, density.fontScale, textMeasurer) {
        measuredLabelWidth(
            labels = labels,
            style = style,
            textMeasurer = textMeasurer,
            density = density,
        ).plus(4.dp).coerceIn(94.dp, 180.dp)
    }
}

@Composable
private fun CalendarGrid(
    state: StatsUiState,
    onStepMonth: (Int) -> Unit,
) {
    val cells = calendarCells(state.viewMonth)
    val weekdays = listOf(
        stringResource(Res.string.stats_weekday_initial_monday),
        stringResource(Res.string.stats_weekday_initial_tuesday),
        stringResource(Res.string.stats_weekday_initial_wednesday),
        stringResource(Res.string.stats_weekday_initial_thursday),
        stringResource(Res.string.stats_weekday_initial_friday),
        stringResource(Res.string.stats_weekday_initial_saturday),
        stringResource(Res.string.stats_weekday_initial_sunday),
    )
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            MonthStepper(
                viewMonth = state.viewMonth,
                today = state.today,
                onStepMonth = onStepMonth,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                weekdays.forEach { day ->
                    Text(
                        text = day,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            cells.chunked(7).forEach { week ->
                Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                    week.forEach { day ->
                        CalendarCell(day = day, state = state, modifier = Modifier.weight(1f))
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = AppSpacing.xs),
                horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LegendDot(
                    color = MaterialTheme.colorScheme.primary,
                    label = stringResource(Res.string.stats_legend_today),
                    borderStroke = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                )
                LegendDot(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    label = stringResource(Res.string.stats_legend_practiced),
                    borderStroke = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                )
                LegendDot(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    label = stringResource(Res.string.stats_legend_missed),
                    borderStroke = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                )
            }
        }
    }
}

@Composable
private fun CalendarCell(
    day: Int?,
    state: StatsUiState,
    modifier: Modifier = Modifier,
) {
    if (day == null) {
        Spacer(modifier = modifier.aspectRatio(1f))
        return
    }

    val date = LocalDate(state.viewMonth.year, state.viewMonth.monthZeroBased + 1, day)
    val isToday = date == state.today
    val isFuture = date > state.today
    val practiced = StatsPracticeDay(state.viewMonth.year, state.viewMonth.monthZeroBased, day) in state.practiceLog
    val shape = RoundedCornerShape(7.dp)
    val background: Color
    val content: Color
    val weight: FontWeight
    when {
        state.isLoading -> {
            background = MaterialTheme.colorScheme.surfaceContainerHighest
            content = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            weight = FontWeight.Medium
        }

        isToday -> {
            background = MaterialTheme.colorScheme.primary
            content = MaterialTheme.colorScheme.onPrimary
            weight = FontWeight.Bold
        }

        practiced -> {
            background = MaterialTheme.colorScheme.primaryContainer
            content = MaterialTheme.colorScheme.onPrimaryContainer
            weight = FontWeight.SemiBold
        }

        isFuture -> {
            background = Color.Transparent
            content = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
            weight = FontWeight.Medium
        }

        else -> {
            background = MaterialTheme.colorScheme.surfaceContainerHighest
            content = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            weight = FontWeight.Medium
        }
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(background)
            .then(
                if (!state.isLoading && isToday) {
                    Modifier.border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                        shape = shape,
                    )
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = day.toString(),
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = MaterialTheme.serifFontFamily,
                fontSize = 12.sp,
                lineHeight = 12.sp,
                fontWeight = weight,
            ),
            color = content,
        )
    }
}

@Composable
private fun LegendDot(color: Color, label: String, borderStroke: BorderStroke) {
    val shape = RoundedCornerShape(3.dp)
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(11.dp)
                .clip(shape)
                .background(color)
                .border(
                    borderStroke,
                    shape,
                ),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            ),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
internal fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall.copy(
            fontFamily = MaterialTheme.serifFontFamily,
            fontStyle = MaterialTheme.uiItalic,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            letterSpacing = 0.1.sp,
        ),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = AppSpacing.xs, top = AppSpacing.xs, bottom = 0.dp),
    )
}
