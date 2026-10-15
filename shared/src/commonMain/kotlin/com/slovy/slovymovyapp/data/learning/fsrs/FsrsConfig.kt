package com.slovy.slovymovyapp.data.learning.fsrs

import com.slovy.slovymovyapp.data.learning.CardFamily
import com.slovy.slovymovyapp.data.learning.Rating
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

/** The credit factor a [rating] on [sourceFamily] passes to [targetFamily]; null when none. */
fun CrossFamilyCredit.factorFor(rating: Rating): Double? =
    when (rating) {
        Rating.GOOD -> goodFactor
        Rating.EASY -> easyFactor
        Rating.AGAIN,
        Rating.HARD,
            -> null
    }?.takeIf { it > 0.0 }

/**
 * The stability factor carried from [sourceFamily] to [targetFamily] by a [rating]. A pair with
 * its own credit uses it. A forward pair without one, which happens when the learner has switched
 * the families between them off, chains the credits of every step it skips, so a word reaching a
 * far family directly inherits no more than it would have through the ladder.
 */
fun FsrsConfig.crossFamilyCreditFactor(
    sourceFamily: CardFamily,
    targetFamily: CardFamily,
    rating: Rating,
): Double? {
    directCreditFactor(sourceFamily, targetFamily, rating)?.let { return it }
    if (targetFamily.ordinal <= sourceFamily.ordinal) return null
    var factor = 1.0
    var from = sourceFamily
    for (next in CardFamily.entries) {
        if (next.ordinal <= sourceFamily.ordinal || next.ordinal > targetFamily.ordinal) continue
        factor *= directCreditFactor(from, next, rating) ?: return null
        from = next
    }
    return factor
}

private fun FsrsConfig.directCreditFactor(
    sourceFamily: CardFamily,
    targetFamily: CardFamily,
    rating: Rating,
): Double? =
    crossFamilyCredits
        .firstOrNull { it.sourceFamily == sourceFamily && it.targetFamily == targetFamily }
        ?.factorFor(rating)
