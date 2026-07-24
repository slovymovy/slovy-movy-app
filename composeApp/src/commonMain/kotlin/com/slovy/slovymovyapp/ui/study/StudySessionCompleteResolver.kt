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
    // Words milestone outranks everything: a lifetime vocabulary landmark is the rarest,
    // biggest moment. Detection is intentionally session-window only (no persisted
    // high-water mark), so a word slipping below the LEARNED threshold and recovering
    // later may re-celebrate the same mark — accepted product decision.
    // The empty-before guard keeps a missing session-start snapshot from reading as a
    // giant crossing.
    if (pipelineBefore.isNotEmpty()) {
        val learnedAfter = learnedCount(pipelineAfter)
        val mark = wordsMilestoneMark(
            learnedBefore = learnedCount(pipelineBefore),
            learnedAfter = learnedAfter,
        )
        if (mark != null) {
            // The ladder mark only gates the celebration; the hero shows the real total.
            return StudySessionCompleteHero.WordsMilestone(learnedTotal = learnedAfter)
        }
    }

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

private fun learnedCount(pipeline: List<StatsPipelineStage>): Int =
    pipeline.firstOrNull { it.id == StatsPipelineStageId.LEARNED }?.count ?: 0

// Ladder: 5, then every 10 up to 100, then every 50 (unbounded). Returns the highest
// mark crossed this session (before < mark <= after), or null when none was crossed.
private fun wordsMilestoneMark(learnedBefore: Int, learnedAfter: Int): Int? {
    val highestMarkAtOrBelowAfter = when {
        learnedAfter >= 150 -> (learnedAfter / 50) * 50
        learnedAfter >= 100 -> 100
        learnedAfter >= 10 -> (learnedAfter / 10) * 10
        learnedAfter >= 5 -> 5
        else -> return null
    }
    return highestMarkAtOrBelowAfter.takeIf { it > learnedBefore }
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
