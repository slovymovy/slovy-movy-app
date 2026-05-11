package com.slovy.slovymovyapp.data.learning.fsrs

import com.slovy.slovymovyapp.data.learning.CardFamily
import kotlin.time.Duration

data class FsrsConfig(
    val weights: List<Double>,
    val requestRetention: Double,
    val maximumInterval: Duration,
    val enableFuzz: Boolean,
    val dailyNewTaskFamilyBudget: Int,
    val pauseIntakeIfQueueAbove: Int,
    val pauseIntakeRetentionMinReviews: Long,
    val pauseIntakeIfRetentionBelow: Double,
    val matureStability: Duration,
    val defaultIntakeFamilies: List<CardFamily>,
    val productionUnlockStability: Duration,
    val contextUnlockStability: Duration,
    val recognitionToProductionStabilityFactor: Double,
    val productionToContextStabilityFactor: Double,
    val contextToVoiceStabilityFactor: Double,
    val buryFailedSessionCardsFor: Duration,
    val sameSenseCooldownRatio: Double,
    val sameLemmaCooldownRatio: Double,
    val sameAnswerCooldownRatio: Double,
    val siblingCooldownFloor: Duration,
    val siblingCooldownCap: Duration,
    val cooldownJitterRatio: Double,
    val selectionCandidateLimit: Int,
)
