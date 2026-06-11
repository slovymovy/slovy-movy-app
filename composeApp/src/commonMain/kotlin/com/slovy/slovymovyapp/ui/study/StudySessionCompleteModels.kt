package com.slovy.slovymovyapp.ui.study

import com.slovy.slovymovyapp.data.learning.stats.StatsPipelineStageId

data class StudySessionCompleteUiState(
    val cardsReviewed: Int,
    val minutes: Int,
    val streakDays: Int,
    val message: String,
    val hero: StudySessionCompleteHero,
)

sealed interface StudySessionCompleteHero {
    data object None : StudySessionCompleteHero

    data class Milestone(
        val streakDays: Int,
    ) : StudySessionCompleteHero

    data class PipelineShift(
        val stages: List<PipelineShiftStageUiState>,
    ) : StudySessionCompleteHero
}

data class PipelineShiftStageUiState(
    val id: StatsPipelineStageId,
    val before: Int,
    val after: Int,
) {
    val delta: Int = after - before
}
