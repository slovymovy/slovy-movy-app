package com.slovy.slovymovyapp.data.learning.fsrs

import com.slovy.slovymovyapp.data.learning.CardFamily
import com.slovy.slovymovyapp.data.learning.CardState
import com.slovy.slovymovyapp.data.learning.Rating
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

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
    val unlockRules: List<UnlockRule>,
    val crossFamilyCredits: List<CrossFamilyCredit>,
    val buryFailedSessionCardsFor: Duration,
    /**
     * How long a due card is set aside when its sense has playable variants but none in the
     * formats the learner studies. Longer than [buryFailedSessionCardsFor], since no fetch will
     * change that, yet short enough that switching a format back on is felt the same day.
     */
    val buryUnplayableUnderFormatsFor: Duration,
    /** The floor for stability a card inherits through cross-family credit, so it is never zero. */
    val minInheritedStability: Double,
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

/**
 * A successful review of a [sourceFamily] card whose stability has reached [minStability] creates
 * the sense's [targetFamily] card, if it does not exist yet. The new card inherits stability through
 * the matching [CrossFamilyCredit].
 */
data class UnlockRule(
    val sourceFamily: CardFamily,
    val targetFamily: CardFamily,
    val minStability: Duration,
)

data class CrossFamilyCredit(
    val sourceFamily: CardFamily,
    val targetFamily: CardFamily,
    val goodFactor: Double,
    val easyFactor: Double,
)

/** The credit factor a [rating] carries; null when this rating passes nothing on. */
fun CrossFamilyCredit.factorFor(rating: Rating): Double? =
    when (rating) {
        Rating.GOOD -> goodFactor
        Rating.EASY -> easyFactor
        Rating.AGAIN,
        Rating.HARD,
            -> null
    }?.takeIf { it > 0.0 }

/**
 * The stability factor a [rating] on [sourceFamily] passes to a [targetFamily] card that already
 * exists: the configured credit for that pair, or none. Pairs without a credit get nothing; the
 * chaining along the unlock rules is only for unlocking, see [unlockPathCreditFactor].
 */
fun FsrsConfig.crossFamilyCreditFactor(
    sourceFamily: CardFamily,
    targetFamily: CardFamily,
    rating: Rating,
): Double? =
    crossFamilyCredits
        .firstOrNull { it.sourceFamily == sourceFamily && it.targetFamily == targetFamily }
        ?.factorFor(rating)

/**
 * Whether a card of [sourceFamily] leads to [targetFamily] through the unlock rules, directly or
 * through the families it unlocks first.
 */
fun FsrsConfig.unlocksEventually(sourceFamily: CardFamily, targetFamily: CardFamily): Boolean =
    unlockPath(sourceFamily, targetFamily) != null

/**
 * The stability factor a new [targetFamily] card inherits from [sourceFamily] when it is reached
 * along the unlock rules without the families in between: the credits of every step on that path
 * multiplied, so a word reaching a far family directly inherits no more than it would have
 * climbing through them. A direct rule is its own credit. Null when the rules never lead from the
 * source to the target, or a step on the way carries no credit.
 */
fun FsrsConfig.unlockPathCreditFactor(
    sourceFamily: CardFamily,
    targetFamily: CardFamily,
    rating: Rating,
): Double? {
    val path = unlockPath(sourceFamily, targetFamily) ?: return null
    var factor = 1.0
    for (step in 1 until path.size) {
        factor *= crossFamilyCreditFactor(path[step - 1], path[step], rating) ?: return null
    }
    return factor
}

/**
 * The families from [sourceFamily] to [targetFamily] along the unlock rules, both included, or
 * null when the rules never lead there. The rules form a tree, so the first path found is the one.
 */
private fun FsrsConfig.unlockPath(sourceFamily: CardFamily, targetFamily: CardFamily): List<CardFamily>? {
    if (sourceFamily == targetFamily) return null
    fun walk(from: CardFamily, visited: List<CardFamily>): List<CardFamily>? {
        for (rule in unlockRules) {
            if (rule.sourceFamily != from || rule.targetFamily in visited) continue
            val path = visited + rule.targetFamily
            if (rule.targetFamily == targetFamily) return path
            walk(rule.targetFamily, path)?.let { return it }
        }
        return null
    }
    return walk(sourceFamily, listOf(sourceFamily))
}

/** What a card re-entered in another family starts with; see [FsrsConfig.reentryScheduling]. */
data class ReentryScheduling(
    val state: CardState,
    val stability: Double,
    val difficulty: Double,
)

/**
 * The memory of a card that re-enters a sense in [targetFamily] from its strongest existing card,
 * the way an unlock would: forward along the unlock rules it inherits through the chained credit,
 * back from a family further up through that pair's credit. A source never reviewed, or a pair
 * without credit, starts the new card fresh.
 *
 * Unlike an unlock, re-entry carries no delay: it follows a format change, not a review, so the
 * new card takes over the hidden card's due time, and a word that was due stays due.
 */
fun FsrsConfig.reentryScheduling(
    sourceFamily: CardFamily,
    sourceState: CardState,
    sourceStability: Double,
    sourceDifficulty: Double,
    targetFamily: CardFamily,
): ReentryScheduling {
    val forward = unlocksEventually(sourceFamily, targetFamily)
    val factor = if (forward) {
        unlockPathCreditFactor(sourceFamily, targetFamily, Rating.GOOD)
    } else {
        crossFamilyCreditFactor(sourceFamily, targetFamily, Rating.GOOD)
    }
    if (sourceState == CardState.NEW || factor == null) {
        return ReentryScheduling(CardState.NEW, 0.0, 0.0)
    }
    return ReentryScheduling(
        state = CardState.LEARNING,
        stability = inheritedStability(sourceStability, factor),
        difficulty = sourceDifficulty,
    )
}

/** The stability a card inherits from a source at [stabilityDays] through a credit [factor]. */
fun FsrsConfig.inheritedStability(stabilityDays: Double, factor: Double): Double =
    (stabilityDays * factor).coerceAtLeast(minInheritedStability)

/** How long a card credited forward to [stabilityDays] waits before it is first presented. */
fun FsrsConfig.forwardCreditDelay(stabilityDays: Double): Duration =
    scaledStability(stabilityDays, forwardCreditDelayRatio, forwardCreditDelayFloor, forwardCreditDelayCap)

/** How long a card credited backward to [stabilityDays] waits before it is presented again. */
fun FsrsConfig.backwardCreditDelay(stabilityDays: Double): Duration =
    scaledStability(stabilityDays, backwardCreditDelayRatio, backwardCreditDelayFloor, backwardCreditDelayCap)

/** How long a sense's siblings are held back after a review that left it at [stabilityDays]. */
fun FsrsConfig.sameSenseExposureCooldown(stabilityDays: Double): Duration =
    scaledStability(stabilityDays, sameSenseExposureRatio, sameSenseExposureFloor, sameSenseExposureCap)

private fun scaledStability(stabilityDays: Double, ratio: Double, floor: Duration, cap: Duration): Duration {
    if (ratio <= 0.0) return Duration.ZERO
    return (stabilityDays.toDuration(DurationUnit.DAYS) * ratio).coerceIn(floor, cap)
}
