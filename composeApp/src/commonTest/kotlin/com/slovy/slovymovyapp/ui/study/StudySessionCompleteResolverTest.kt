package com.slovy.slovymovyapp.ui.study

import com.slovy.slovymovyapp.data.learning.stats.StatsPipelineStage
import com.slovy.slovymovyapp.data.learning.stats.StatsPipelineStageId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StudySessionCompleteResolverTest {

    @Test
    fun milestone_wins_over_pipeline_shift() {
        val hero = resolveStudySessionCompleteHero(
            streakDays = 7,
            pipelineBefore = pipeline(new = 10, fresh = 4, middle = 2, strong = 1, learned = 0),
            pipelineAfter = pipeline(new = 4, fresh = 8, middle = 4, strong = 2, learned = 1),
        )

        assertTrue(hero is StudySessionCompleteHero.Milestone)
        assertEquals(7, hero.streakDays)
    }

    @Test
    fun strong_or_learned_move_shows_pipeline_shift() {
        val hero = resolveStudySessionCompleteHero(
            streakDays = 4,
            pipelineBefore = pipeline(new = 10, fresh = 4, middle = 2, strong = 1, learned = 0),
            pipelineAfter = pipeline(new = 10, fresh = 4, middle = 2, strong = 2, learned = 0),
        )

        assertTrue(hero is StudySessionCompleteHero.PipelineShift)
        assertEquals(
            listOf(
                StatsPipelineStageId.NEW,
                StatsPipelineStageId.FRESH,
                StatsPipelineStageId.MIDDLE,
                StatsPipelineStageId.STRONG,
                StatsPipelineStageId.LEARNED,
            ),
            hero.stages.map { it.id },
        )
    }

    @Test
    fun low_tier_moves_only_have_no_hero() {
        val hero = resolveStudySessionCompleteHero(
            streakDays = 4,
            pipelineBefore = pipeline(new = 10, fresh = 4, middle = 2, strong = 1, learned = 0),
            pipelineAfter = pipeline(new = 8, fresh = 6, middle = 3, strong = 1, learned = 0),
        )

        assertEquals(StudySessionCompleteHero.None, hero)
    }

    @Test
    fun high_tier_regression_only_has_no_hero() {
        val hero = resolveStudySessionCompleteHero(
            streakDays = 4,
            pipelineBefore = pipeline(new = 10, fresh = 4, middle = 2, strong = 1, learned = 3),
            pipelineAfter = pipeline(new = 10, fresh = 4, middle = 3, strong = 1, learned = 2),
        )

        assertEquals(StudySessionCompleteHero.None, hero)
    }

    @Test
    fun learned_growth_shows_pipeline_shift() {
        val hero = resolveStudySessionCompleteHero(
            streakDays = 4,
            pipelineBefore = pipeline(new = 10, fresh = 4, middle = 2, strong = 1, learned = 2),
            pipelineAfter = pipeline(new = 10, fresh = 4, middle = 2, strong = 1, learned = 3),
        )

        assertTrue(hero is StudySessionCompleteHero.PipelineShift)
    }

    private fun pipeline(
        new: Int,
        fresh: Int,
        middle: Int,
        strong: Int,
        learned: Int,
    ) = listOf(
        StatsPipelineStage(StatsPipelineStageId.QUEUE, 3),
        StatsPipelineStage(StatsPipelineStageId.NEW, new),
        StatsPipelineStage(StatsPipelineStageId.FRESH, fresh),
        StatsPipelineStage(StatsPipelineStageId.MIDDLE, middle),
        StatsPipelineStage(StatsPipelineStageId.STRONG, strong),
        StatsPipelineStage(StatsPipelineStageId.LEARNED, learned),
    )
}
