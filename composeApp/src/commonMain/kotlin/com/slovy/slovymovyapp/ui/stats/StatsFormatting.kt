package com.slovy.slovymovyapp.ui.stats

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.intl.Locale
import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.learning.stats.StatsPipelineStage
import com.slovy.slovymovyapp.data.learning.stats.StatsPipelineStageId
import com.slovy.slovymovyapp.data.learning.stats.StatsYearMonth
import com.slovy.slovymovyapp.i18n.NumberFormatter
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import slovymovyapp.composeapp.generated.resources.*
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@Composable
internal fun stageLabel(stage: StatsPipelineStageId): String = when (stage) {
    StatsPipelineStageId.QUEUE -> stringResource(Res.string.stats_stage_queue)
    StatsPipelineStageId.NEW -> stringResource(Res.string.stats_stage_new)
    StatsPipelineStageId.FRESH -> stringResource(Res.string.stats_stage_fresh)
    StatsPipelineStageId.MIDDLE -> stringResource(Res.string.stats_stage_middle)
    StatsPipelineStageId.STRONG -> stringResource(Res.string.stats_stage_strong)
    StatsPipelineStageId.LEARNED -> stringResource(Res.string.stats_stage_learned)
}

internal fun stageColor(stage: StatsPipelineStageId): Color = when (stage) {
    StatsPipelineStageId.QUEUE -> Color(0xFF9B8FB8)
    StatsPipelineStageId.NEW -> Color(0xFFC8B59A)
    StatsPipelineStageId.FRESH -> Color(0xFFD9866C)
    StatsPipelineStageId.MIDDLE -> Color(0xFFD6A85C)
    StatsPipelineStageId.STRONG -> Color(0xFF6E9CB0)
    StatsPipelineStageId.LEARNED -> Color(0xFF7CB078)
}

// Visual-only skeleton proportions; the loaded values come from the stats pipeline.
internal fun loadingPipelineWidth(stage: StatsPipelineStageId): Float = when (stage) {
    StatsPipelineStageId.QUEUE -> 0.66f
    StatsPipelineStageId.NEW -> 0.74f
    StatsPipelineStageId.FRESH -> 0.58f
    StatsPipelineStageId.MIDDLE -> 0.82f
    StatsPipelineStageId.STRONG -> 0.48f
    StatsPipelineStageId.LEARNED -> 0.64f
}

@Composable
internal fun loadingAwareContentColor(isLoading: Boolean): Color =
    if (isLoading) {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    } else {
        MaterialTheme.colorScheme.onSurface
    }

internal fun calendarCells(viewMonth: StatsYearMonth): List<Int?> {
    val first = LocalDate(viewMonth.year, viewMonth.monthZeroBased + 1, 1)
    val leading = first.dayOfWeek.ordinal
    val cells = mutableListOf<Int?>()
    repeat(leading) { cells += null }
    for (day in 1..daysInMonth(viewMonth.year, viewMonth.monthZeroBased)) {
        cells += day
    }
    while (cells.size % 7 != 0) cells += null
    return cells
}

internal fun daysInMonth(year: Int, month: Int): Int = when (month) {
    0, 2, 4, 6, 7, 9, 11 -> 31
    3, 5, 8, 10 -> 30
    else -> if (isLeapYear(year)) 29 else 28
}

private fun isLeapYear(year: Int): Boolean =
    (year % 4 == 0 && year % 100 != 0) || year % 400 == 0

@OptIn(ExperimentalTime::class)
internal fun initialStatsState(
    languages: List<Language>,
    clock: Clock,
): StatsUiState {
    val normalized = languages.ifEmpty { listOf(Language.ENGLISH) }.distinct().sortedBy { it.ordinal }
    val today = currentLocalDate(clock)
    return StatsUiState(
        learningLanguages = normalized,
        selectedLanguage = normalized.first(),
        today = today,
        viewMonth = StatsYearMonth(today.year, today.month.ordinal),
        isLoading = true,
        streakDays = 0,
        activeDaysTotal = 0,
        practiceLog = emptySet(),
        reviewsToday = 0,
        reviewsWeek = 0,
        minutesToday = 0,
        minutesWeek = 0,
        wordsTotal = 0,
        pipeline = StatsPipelineStageId.entries.map { StatsPipelineStage(it, 0) },
    )
}

@OptIn(ExperimentalTime::class)
internal fun currentLocalDate(clock: Clock): LocalDate =
    clock.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

@Composable
internal fun formatCount(value: Int, isLoading: Boolean): String {
    if (isLoading) return "--"
    return formatCountForLanguage(value, Locale.current.language)
}

internal fun formatCount(value: Int, isLoading: Boolean, language: String): String {
    if (isLoading) return "--"
    return formatCountForLanguage(value, language)
}

internal fun formatCountForLanguage(value: Int, language: String): String =
    NumberFormatter.formatInteger(value, language)

internal const val STATS_REVEAL_ANIMATION_MS = 260
