package com.slovy.slovymovyapp.data.learning.stats

data class GlobalStats(
    val totalCards: Int,
    val dueToday: Int,
    val reviewedLast7d: Int,
    val rollingRetention7d: Double?,
    val matureCount: Int,
)

enum class StatsPipelineStageId {
    QUEUE,
    NEW,
    FRESH,
    MIDDLE,
    STRONG,
    LEARNED,
}

data class StatsPipelineStage(
    val id: StatsPipelineStageId,
    val count: Int,
)

data class StatsYearMonth(
    val year: Int,
    val monthZeroBased: Int,
)

data class StatsPracticeDay(
    val year: Int,
    val monthZeroBased: Int,
    val day: Int,
)

data class StatsScreenData(
    val streakDays: Int,
    val practiceLog: Set<StatsPracticeDay>,
    val reviewsToday: Int,
    val reviewsWeek: Int,
    val wordsTotal: Int,
    val pipeline: List<StatsPipelineStage>,
)
