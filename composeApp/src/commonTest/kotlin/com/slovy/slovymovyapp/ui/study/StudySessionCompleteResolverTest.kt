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

    @Test
    fun words_milestone_fires_on_first_mark() {
        val hero = resolveStudySessionCompleteHero(
            streakDays = 4,
            pipelineBefore = pipeline(new = 10, fresh = 4, middle = 2, strong = 1, learned = 4),
            pipelineAfter = pipeline(new = 10, fresh = 4, middle = 2, strong = 1, learned = 5),
        )

        assertEquals(StudySessionCompleteHero.WordsMilestone(learnedTotal = 5), hero)
    }

    @Test
    fun words_milestone_shows_real_total_when_vaulting_several_marks() {
        val hero = resolveStudySessionCompleteHero(
            streakDays = 4,
            pipelineBefore = pipeline(new = 10, fresh = 4, middle = 2, strong = 1, learned = 3),
            pipelineAfter = pipeline(new = 10, fresh = 4, middle = 2, strong = 1, learned = 27),
        )

        assertEquals(StudySessionCompleteHero.WordsMilestone(learnedTotal = 27), hero)
    }

    @Test
    fun words_milestone_beats_streak_milestone() {
        val hero = resolveStudySessionCompleteHero(
            streakDays = 7,
            pipelineBefore = pipeline(new = 10, fresh = 4, middle = 2, strong = 1, learned = 9),
            pipelineAfter = pipeline(new = 10, fresh = 4, middle = 2, strong = 1, learned = 10),
        )

        assertEquals(StudySessionCompleteHero.WordsMilestone(learnedTotal = 10), hero)
    }

    @Test
    fun words_milestone_beats_pipeline_shift() {
        val hero = resolveStudySessionCompleteHero(
            streakDays = 4,
            pipelineBefore = pipeline(new = 10, fresh = 4, middle = 2, strong = 1, learned = 4),
            pipelineAfter = pipeline(new = 8, fresh = 4, middle = 2, strong = 3, learned = 6),
        )

        assertEquals(StudySessionCompleteHero.WordsMilestone(learnedTotal = 6), hero)
    }

    @Test
    fun no_words_milestone_when_mark_was_already_reached_before_session() {
        val hero = resolveStudySessionCompleteHero(
            streakDays = 4,
            pipelineBefore = pipeline(new = 10, fresh = 4, middle = 2, strong = 1, learned = 5),
            pipelineAfter = pipeline(new = 10, fresh = 4, middle = 2, strong = 1, learned = 7),
        )

        assertTrue(
            hero is StudySessionCompleteHero.PipelineShift,
            "Learned growth without a mark crossing must fall through to the shift hero, got $hero",
        )
    }

    @Test
    fun no_words_milestone_when_learned_count_is_unchanged() {
        val hero = resolveStudySessionCompleteHero(
            streakDays = 7,
            pipelineBefore = pipeline(new = 10, fresh = 4, middle = 2, strong = 1, learned = 10),
            pipelineAfter = pipeline(new = 10, fresh = 4, middle = 2, strong = 1, learned = 10),
        )

        assertEquals(StudySessionCompleteHero.Milestone(streakDays = 7), hero)
    }

    @Test
    fun no_words_milestone_below_first_mark() {
        val hero = resolveStudySessionCompleteHero(
            streakDays = 4,
            pipelineBefore = pipeline(new = 10, fresh = 4, middle = 2, strong = 1, learned = 0),
            pipelineAfter = pipeline(new = 10, fresh = 4, middle = 2, strong = 1, learned = 4),
        )

        assertTrue(
            hero !is StudySessionCompleteHero.WordsMilestone,
            "Learned counts below the first ladder mark must not celebrate, got $hero",
        )
    }

    @Test
    fun no_words_milestone_on_learned_regression() {
        val hero = resolveStudySessionCompleteHero(
            streakDays = 4,
            pipelineBefore = pipeline(new = 10, fresh = 4, middle = 2, strong = 1, learned = 12),
            pipelineAfter = pipeline(new = 10, fresh = 4, middle = 2, strong = 1, learned = 8),
        )

        assertTrue(
            hero !is StudySessionCompleteHero.WordsMilestone,
            "A shrinking learned count must not celebrate, got $hero",
        )
    }

    @Test
    fun words_milestone_ladder_hits_hundred() {
        val hero = resolveStudySessionCompleteHero(
            streakDays = 4,
            pipelineBefore = pipeline(new = 10, fresh = 4, middle = 2, strong = 1, learned = 99),
            pipelineAfter = pipeline(new = 10, fresh = 4, middle = 2, strong = 1, learned = 100),
        )

        assertEquals(StudySessionCompleteHero.WordsMilestone(learnedTotal = 100), hero)
    }

    @Test
    fun words_milestone_ladder_has_no_marks_between_hundred_and_hundred_fifty() {
        val hero = resolveStudySessionCompleteHero(
            streakDays = 4,
            pipelineBefore = pipeline(new = 10, fresh = 4, middle = 2, strong = 1, learned = 100),
            pipelineAfter = pipeline(new = 10, fresh = 4, middle = 2, strong = 1, learned = 149),
        )

        assertTrue(
            hero !is StudySessionCompleteHero.WordsMilestone,
            "The ladder has no marks in 101..149, got $hero",
        )
    }

    @Test
    fun words_milestone_ladder_hits_hundred_fifty() {
        val hero = resolveStudySessionCompleteHero(
            streakDays = 4,
            pipelineBefore = pipeline(new = 10, fresh = 4, middle = 2, strong = 1, learned = 149),
            pipelineAfter = pipeline(new = 10, fresh = 4, middle = 2, strong = 1, learned = 150),
        )

        assertEquals(StudySessionCompleteHero.WordsMilestone(learnedTotal = 150), hero)
    }

    @Test
    fun words_milestone_ladder_uses_fifty_steps_above_hundred_fifty() {
        val hero = resolveStudySessionCompleteHero(
            streakDays = 4,
            pipelineBefore = pipeline(new = 10, fresh = 4, middle = 2, strong = 1, learned = 240),
            pipelineAfter = pipeline(new = 10, fresh = 4, middle = 2, strong = 1, learned = 260),
        )

        assertEquals(StudySessionCompleteHero.WordsMilestone(learnedTotal = 260), hero)
    }

    @Test
    fun no_words_milestone_with_empty_before_snapshot() {
        val hero = resolveStudySessionCompleteHero(
            streakDays = 4,
            pipelineBefore = emptyList(),
            pipelineAfter = pipeline(new = 10, fresh = 4, middle = 2, strong = 1, learned = 60),
        )

        assertTrue(
            hero !is StudySessionCompleteHero.WordsMilestone,
            "A missing session-start snapshot must not read as a crossing, got $hero",
        )
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
