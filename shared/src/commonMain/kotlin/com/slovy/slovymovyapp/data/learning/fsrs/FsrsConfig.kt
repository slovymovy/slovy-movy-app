package com.slovy.slovymovyapp.data.learning.fsrs

import com.slovy.slovymovyapp.data.learning.CardFamily
import kotlin.time.Duration

data class FsrsConfig(
    val weights: List<Double>,
    val requestRetention: Double,
    val maximumInterval: Duration,
    val enableFuzz: Boolean,
    val dailyNewTaskFamilyBudget: Int,
    val continueNowLemmaLimit: Int,
    val continueNowPendingLemmaLimit: Int,
    val pauseIntakeIfQueueAbove: Int,
    val pauseIntakeRetentionMinReviews: Long,
    val pauseIntakeIfRetentionBelow: Double,
    val matureStability: Duration,
    val defaultIntakeFamilies: List<CardFamily>,
    val productionUnlockStability: Duration,
    val contextUnlockStability: Duration,
    val crossFamilyCredits: List<CrossFamilyCredit>,
    val buryFailedSessionCardsFor: Duration,
    val sameSenseCooldownRatio: Double,
    val sameLemmaCooldownRatio: Double,
    val sameAnswerCooldownRatio: Double,
    val siblingCooldownFloor: Duration,
    val siblingCooldownCap: Duration,
    val lemmaCooldownFloor: Duration,
    val lemmaCooldownCap: Duration,
    val sameSenseExposureRatio: Double,
    val sameSenseExposureFloor: Duration,
    val sameSenseExposureCap: Duration,
    val forwardCreditDelayRatio: Double,
    val forwardCreditDelayFloor: Duration,
    val forwardCreditDelayCap: Duration,
    val backwardCreditDelayRatio: Double,
    val backwardCreditDelayFloor: Duration,
    val backwardCreditDelayCap: Duration,
    val cooldownJitterRatio: Double,
    val selectionCandidateLimit: Int,
)

data class CrossFamilyCredit(
    val sourceFamily: CardFamily,
    val targetFamily: CardFamily,
    val goodFactor: Double,
    val easyFactor: Double,
)
