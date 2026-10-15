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
    val productionUnlockStability: Duration,
    val contextUnlockStability: Duration,
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
 * ladder's chaining is only for unlocking, see [ladderCreditFactor].
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
 * The stability factor a new [targetFamily] card inherits when [sourceFamily] unlocks it. An
 * adjacent step uses its own credit. A step over families the learner switched off multiplies the
 * credits of every step it skips, so a word reaching a far family directly inherits no more than it
 * would have climbing through the ladder. Null when the target is not ahead of the source or a
 * step in between carries no credit.
 */
fun FsrsConfig.ladderCreditFactor(
    sourceFamily: CardFamily,
    targetFamily: CardFamily,
    rating: Rating,
): Double? {
    if (targetFamily.ordinal <= sourceFamily.ordinal) return null
    var factor = 1.0
    var from = sourceFamily
    for (next in CardFamily.entries) {
        if (next.ordinal <= sourceFamily.ordinal || next.ordinal > targetFamily.ordinal) continue
        factor *= crossFamilyCreditFactor(from, next, rating) ?: return null
        from = next
    }
    return factor
}

/**
 * The stability a [sourceFamily] card must reach before it unlocks [targetFamily]. Each step of the
 * ladder has its own gate; a step over switched-off families takes the strictest of the gates it
 * skips, so a limited selection never reaches a far family sooner than the full ladder would.
 * Null when the target is not ahead of the source.
 */
fun FsrsConfig.unlockStabilityGate(sourceFamily: CardFamily, targetFamily: CardFamily): Duration? {
    if (targetFamily.ordinal <= sourceFamily.ordinal) return null
    return CardFamily.entries
        .filter { it.ordinal >= sourceFamily.ordinal && it.ordinal < targetFamily.ordinal }
        .maxOf { step ->
            when (step) {
                CardFamily.RECOGNIZE_SENSE -> productionUnlockStability
                CardFamily.PRODUCE_WORD,
                CardFamily.PRODUCE_WORD_IN_CONTEXT,
                    -> contextUnlockStability

                // Nothing lies beyond listening; the filter above never yields it.
                CardFamily.RECOGNIZE_VOICE -> Duration.INFINITE
            }
        }
}

/** What a card re-entered in another family starts with; see [FsrsConfig.reentryScheduling]. */
data class ReentryScheduling(
    val state: CardState,
    val stability: Double,
    val difficulty: Double,
    /** How long before the new card is first presented. */
    val delay: Duration,
)

/**
 * The scheduling of a card that re-enters a sense in [targetFamily] from its strongest existing
 * card, the way an unlock would: forward along the ladder it inherits through the chained credit
 * and waits the forward delay, back from a family further up it inherits through that pair's
 * credit and waits the backward delay. A source never reviewed, or a pair without credit, starts
 * the new card fresh.
 */
fun FsrsConfig.reentryScheduling(
    sourceFamily: CardFamily,
    sourceState: CardState,
    sourceStability: Double,
    sourceDifficulty: Double,
    targetFamily: CardFamily,
): ReentryScheduling {
    val forward = targetFamily.ordinal > sourceFamily.ordinal
    val factor = if (forward) {
        ladderCreditFactor(sourceFamily, targetFamily, Rating.GOOD)
    } else {
        crossFamilyCreditFactor(sourceFamily, targetFamily, Rating.GOOD)
    }
    if (sourceState == CardState.NEW || factor == null) {
        return ReentryScheduling(CardState.NEW, 0.0, 0.0, Duration.ZERO)
    }
    val stability = inheritedStability(sourceStability, factor)
    return ReentryScheduling(
        state = CardState.LEARNING,
        stability = stability,
        difficulty = sourceDifficulty,
        delay = if (forward) forwardCreditDelay(stability) else backwardCreditDelay(stability),
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
