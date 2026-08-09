package com.slovy.slovymovyapp.ui.study

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StudySessionExitRewardTest {

    @Test
    fun closingBelowThresholdLeavesImmediately() {
        assertFalse(
            StudySessionViewModel.shouldRewardOnExit(reviewedCount = 49, isAlreadyComplete = false),
            "A cross tap means leave; a session short of the threshold must not stop at the reward",
        )
    }

    @Test
    fun closingAtThresholdShowsReward() {
        assertTrue(
            StudySessionViewModel.shouldRewardOnExit(reviewedCount = 50, isAlreadyComplete = false),
            "The threshold itself must qualify",
        )
    }

    @Test
    fun closingWithNoReviewsLeavesImmediately() {
        assertFalse(
            StudySessionViewModel.shouldRewardOnExit(reviewedCount = 0, isAlreadyComplete = false),
        )
    }

    @Test
    fun finishedSessionDoesNotRebuildItsReward() {
        assertFalse(
            StudySessionViewModel.shouldRewardOnExit(reviewedCount = 120, isAlreadyComplete = true),
            "The reward is already on screen; closing it must leave",
        )
    }
}
