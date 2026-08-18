package com.slovy.slovymovyapp.ui.stats.preview

import com.slovy.slovymovyapp.ui.stats.StatsUiState
import com.slovy.slovymovyapp.ui.stats.StatsScreenContent
import com.slovy.slovymovyapp.ui.stats.initialStatsState
import com.slovy.slovymovyapp.ui.stats.daysInMonth
import com.slovy.slovymovyapp.ui.stats.rememberPipelineLabelLayout
import com.slovy.slovymovyapp.ui.stats.PipelineStageLabel

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.learning.stats.StatsPipelineStage
import com.slovy.slovymovyapp.data.learning.stats.StatsPipelineStageId
import com.slovy.slovymovyapp.data.learning.stats.StatsPracticeDay
import com.slovy.slovymovyapp.data.learning.stats.StatsYearMonth
import com.slovy.slovymovyapp.ui.theme.AppSpacing
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import slovymovyapp.composeapp.generated.resources.*
import kotlin.time.Clock
import com.slovy.slovymovyapp.ui.ThemePreviewProvider
import com.slovy.slovymovyapp.ui.ThemedPreview

private fun previewStatsState(languages: List<Language>, today: LocalDate): StatsUiState {
    val normalized = languages.ifEmpty { listOf(Language.ENGLISH) }.distinct().sortedBy { it.ordinal }
    val selected = normalized.first()
    val viewMonth = StatsYearMonth(today.year, today.month.ordinal)
    val variant = selected.ordinal % 4
    return StatsUiState(
        learningLanguages = normalized,
        selectedLanguage = selected,
        today = today,
        viewMonth = viewMonth,
        isLoading = false,
        streakDays = 12 + variant,
        activeDaysTotal = 247 + variant * 13,
        practiceLog = previewPracticeLog(today),
        reviewsToday = 28 + variant * 3,
        reviewsWeek = 184 + variant * 11,
        minutesToday = 12 + variant * 4,
        minutesWeek = 45 + variant * 37,
        wordsTotal = 3_008 + variant * 19,
        pipeline = previewPipeline(variant),
    )
}

private fun previewPipeline(variant: Int): List<StatsPipelineStage> = listOf(
    StatsPipelineStage(StatsPipelineStageId.QUEUE, 3_485 + variant),
    StatsPipelineStage(StatsPipelineStageId.NEW, 47 + variant * 2),
    StatsPipelineStage(StatsPipelineStageId.FRESH, 86 + variant * 3),
    StatsPipelineStage(StatsPipelineStageId.MIDDLE, 132 + variant * 4),
    StatsPipelineStage(StatsPipelineStageId.STRONG, 91 + variant * 3),
    StatsPipelineStage(StatsPipelineStageId.LEARNED, 64 + variant * 2),
)

private fun previewPracticeLog(today: LocalDate): Set<StatsPracticeDay> {
    val currentMonth = today.month.ordinal
    val previousMonth = if (currentMonth == 0) 11 else currentMonth - 1
    val previousYear = if (currentMonth == 0) today.year - 1 else today.year
    val previousMonthDays = daysInMonth(previousYear, previousMonth)
    val log = mutableSetOf<StatsPracticeDay>()

    for (day in 1..today.day) {
        if (day % 4 != 0) {
            log += StatsPracticeDay(today.year, currentMonth, day)
        }
    }
    val startPrevious = (previousMonthDays - 26).coerceAtLeast(1)
    for (day in startPrevious..previousMonthDays) {
        if (day % 7 != 0) {
            log += StatsPracticeDay(previousYear, previousMonth, day)
        }
    }
    return log
}

@Preview
@Composable
private fun StatsScreenPreview(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean,
) {
    ThemedPreview(darkTheme = isDark) {
        StatsScreenContent(
            state = previewStatsState(
                languages = listOf(Language.DUTCH, Language.GERMAN),
                today = LocalDate(2026, 5, 8),
            ),
        )
    }
}

@Preview
@Composable
private fun StatsScreenSingleLanguagePreview(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean,
) {
    ThemedPreview(darkTheme = isDark) {
        StatsScreenContent(
            state = previewStatsState(
                languages = listOf(Language.DUTCH),
                today = LocalDate(2026, 5, 8),
            ),
        )
    }
}

@Preview
@Composable
private fun StatsScreenLoadingPreview(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean,
) {
    ThemedPreview(darkTheme = isDark) {
        StatsScreenContent(
            state = initialStatsState(
                languages = listOf(Language.DUTCH, Language.GERMAN),
                clock = Clock.System,
            ),
        )
    }
}

@Preview
@Composable
private fun PipelineStageLabelAutoSizePreview(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean,
) {
    val samples = listOf(
        "QUEUE",
        "LEARNED",
        "RECALLING",
        "EXTRALONGT",
        "В ОЧЕРЕДИ",
        "ПОВТОРЕНИЕ",
        "ЗАКРЕПЛЕНО",
        "WIEDERHOLUNG",
    )
    ThemedPreview(darkTheme = isDark) {
        Surface {
            val labelLayout = rememberPipelineLabelLayout(samples)
            Column(
                modifier = Modifier.padding(AppSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
            ) {
                samples.forEach { label ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PipelineStageLabel(label = label, layout = labelLayout)
                        Box(
                            modifier = Modifier
                                .padding(start = AppSpacing.smPlus)
                                .weight(1f)
                                .height(10.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        )
                    }
                }
            }
        }
    }
}
