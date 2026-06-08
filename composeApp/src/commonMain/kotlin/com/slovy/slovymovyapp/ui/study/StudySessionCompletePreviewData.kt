package com.slovy.slovymovyapp.ui.study

import com.slovy.slovymovyapp.data.learning.stats.StatsPipelineStageId

data class StudySessionCompletePreviewCase(
    val label: String,
    val description: String,
    val reward: StudySessionCompleteUiState,
)

object StudySessionCompletePreviewData {
    val cases: List<StudySessionCompletePreviewCase> = listOf(
        StudySessionCompletePreviewCase(
            label = "Basic",
            description = "No milestone or broad pipeline shift.",
            reward = StudySessionCompleteUiState(
                cardsReviewed = 12,
                minutes = 6,
                streakDays = 1,
                message = "Nicely done!",
                hero = StudySessionCompleteHero.None,
            ),
        ),
        StudySessionCompletePreviewCase(
            label = "Milestone 7",
            description = "A full-week streak milestone.",
            reward = milestone(days = 7, cardsReviewed = 12, minutes = 6),
        ),
        StudySessionCompletePreviewCase(
            label = "Milestone 14",
            description = "Two-week streak milestone.",
            reward = milestone(days = 14, cardsReviewed = 15, minutes = 7),
        ),
        StudySessionCompletePreviewCase(
            label = "Milestone 30",
            description = "Month-long streak milestone.",
            reward = milestone(days = 30, cardsReviewed = 18, minutes = 8),
        ),
        StudySessionCompletePreviewCase(
            label = "Milestone 100",
            description = "Three-digit medallion stress case.",
            reward = milestone(days = 100, cardsReviewed = 21, minutes = 10),
        ),
        StudySessionCompletePreviewCase(
            label = "Pipeline shift",
            description = "Multiple stages move forward.",
            reward = StudySessionCompleteUiState(
                cardsReviewed = 18,
                minutes = 8,
                streakDays = 4,
                message = "Nicely done!",
                hero = StudySessionCompleteHero.PipelineShift(
                    stages = listOf(
                        PipelineShiftStageUiState(StatsPipelineStageId.NEW, before = 18, after = 12),
                        PipelineShiftStageUiState(StatsPipelineStageId.FRESH, before = 22, after = 28),
                        PipelineShiftStageUiState(StatsPipelineStageId.MIDDLE, before = 16, after = 20),
                        PipelineShiftStageUiState(StatsPipelineStageId.STRONG, before = 11, after = 13),
                        PipelineShiftStageUiState(StatsPipelineStageId.LEARNED, before = 5, after = 6),
                    ),
                ),
            ),
        ),
        StudySessionCompletePreviewCase(
            label = "Pipeline slips",
            description = "Mixed movement with neutral negative deltas.",
            reward = StudySessionCompleteUiState(
                cardsReviewed = 14,
                minutes = 5,
                streakDays = 3,
                message = "Nicely done!",
                hero = StudySessionCompleteHero.PipelineShift(
                    stages = listOf(
                        PipelineShiftStageUiState(StatsPipelineStageId.NEW, before = 10, after = 6),
                        PipelineShiftStageUiState(StatsPipelineStageId.FRESH, before = 24, after = 22),
                        PipelineShiftStageUiState(StatsPipelineStageId.MIDDLE, before = 13, after = 18),
                        PipelineShiftStageUiState(StatsPipelineStageId.STRONG, before = 15, after = 13),
                        PipelineShiftStageUiState(StatsPipelineStageId.LEARNED, before = 6, after = 8),
                    ),
                ),
            ),
        ),
    )

    private fun milestone(
        days: Int,
        cardsReviewed: Int,
        minutes: Int,
    ): StudySessionCompleteUiState = StudySessionCompleteUiState(
        cardsReviewed = cardsReviewed,
        minutes = minutes,
        streakDays = days,
        message = "Nicely done!",
        hero = StudySessionCompleteHero.Milestone(streakDays = days),
    )
}
