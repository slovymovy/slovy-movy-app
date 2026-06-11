package com.slovy.slovymovyapp.ui.study

import com.slovy.slovymovyapp.data.learning.stats.StatsPipelineStage
import com.slovy.slovymovyapp.data.learning.stats.StatsPipelineStageId

private val MilestoneDays = setOf(7, 14, 21, 30, 50, 75, 100, 150, 200, 365)
private val RewardPipelineStages = listOf(
    StatsPipelineStageId.NEW,
    StatsPipelineStageId.FRESH,
    StatsPipelineStageId.MIDDLE,
    StatsPipelineStageId.STRONG,
    StatsPipelineStageId.LEARNED,
)

fun resolveStudySessionCompleteHero(
    streakDays: Int,
    pipelineBefore: List<StatsPipelineStage>,
    pipelineAfter: List<StatsPipelineStage>,
): StudySessionCompleteHero {
    if (streakDays in MilestoneDays) {
        return StudySessionCompleteHero.Milestone(streakDays)
    }

    val shiftStages = pipelineShiftStages(pipelineBefore, pipelineAfter)
    return if (shiftStages.any { it.isHighSignalMove }) {
        StudySessionCompleteHero.PipelineShift(shiftStages)
    } else {
        StudySessionCompleteHero.None
    }
}

private val PipelineShiftStageUiState.isHighSignalMove: Boolean
    get() = delta > 0 && (id == StatsPipelineStageId.STRONG || id == StatsPipelineStageId.LEARNED)

private fun pipelineShiftStages(
    pipelineBefore: List<StatsPipelineStage>,
    pipelineAfter: List<StatsPipelineStage>,
): List<PipelineShiftStageUiState> {
    val beforeCounts = pipelineBefore.associate { it.id to it.count }
    val afterCounts = pipelineAfter.associate { it.id to it.count }
    return RewardPipelineStages.map { id ->
        PipelineShiftStageUiState(
            id = id,
            before = beforeCounts[id] ?: 0,
            after = afterCounts[id] ?: 0,
        )
    }
}
