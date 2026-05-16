package com.slovy.slovymovyapp.data.learning.fsrs

import com.slovy.slovymovyapp.data.learning.CardFamily
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

object FsrsDefaults {
    val WEIGHTS: List<Double> = listOf(
        0.40255, 1.18385, 3.173, 15.69105, 7.1949, 0.5345, 1.4604,
        0.0046, 1.54575, 0.1192, 1.01925, 1.9395, 0.11, 0.29605,
        2.2698, 0.2315, 2.9898, 0.51655, 0.6621, 0.0001, 0.5373,
    )
    const val REQUEST_RETENTION: Double = 0.9
    val MAXIMUM_INTERVAL: Duration = 36500.days
    const val ENABLE_FUZZ: Boolean = true
    const val DAILY_NEW_TASK_FAMILY_BUDGET: Int = 40
    const val CONTINUE_NOW_LEMMA_LIMIT: Int = 10
    const val CONTINUE_NOW_PENDING_LEMMA_LIMIT: Int = 5
    const val PAUSE_INTAKE_IF_QUEUE_ABOVE: Int = 100
    const val PAUSE_INTAKE_RETENTION_MIN_REVIEWS: Long = 50L
    const val PAUSE_INTAKE_IF_RETENTION_BELOW: Double = 0.75
    val MATURE_STABILITY: Duration = 21.days
    val PRODUCTION_UNLOCK_STABILITY: Duration = 48.hours
    val CONTEXT_UNLOCK_STABILITY: Duration = 72.hours
    val BURY_FAILED_SESSION_CARDS_FOR: Duration = 10.minutes
    const val SAME_SENSE_COOLDOWN_RATIO: Double = 0.30
    const val SAME_LEMMA_COOLDOWN_RATIO: Double = 0.02
    const val SAME_ANSWER_COOLDOWN_RATIO: Double = 0.05
    val SIBLING_COOLDOWN_FLOOR: Duration = 10.minutes
    val SIBLING_COOLDOWN_CAP: Duration = 3.days
    val LEMMA_COOLDOWN_FLOOR: Duration = 2.minutes
    val LEMMA_COOLDOWN_CAP: Duration = 6.hours
    const val SAME_SENSE_EXPOSURE_RATIO: Double = 0.05
    val SAME_SENSE_EXPOSURE_FLOOR: Duration = 2.hours
    val SAME_SENSE_EXPOSURE_CAP: Duration = 36.hours
    const val FORWARD_CREDIT_DELAY_RATIO: Double = 0.25
    val FORWARD_CREDIT_DELAY_FLOOR: Duration = 4.hours
    val FORWARD_CREDIT_DELAY_CAP: Duration = 3.days
    const val BACKWARD_CREDIT_DELAY_RATIO: Double = 0.75
    val BACKWARD_CREDIT_DELAY_FLOOR: Duration = 12.hours
    val BACKWARD_CREDIT_DELAY_CAP: Duration = 14.days
    const val COOLDOWN_JITTER_RATIO: Double = 0.2
    const val SELECTION_CANDIDATE_LIMIT: Int = 50

    val DEFAULT_INTAKE_FAMILIES: List<CardFamily> = listOf(
        CardFamily.RECOGNIZE_SENSE,
    )

    val CROSS_FAMILY_CREDITS: List<CrossFamilyCredit> = listOf(
        CrossFamilyCredit(
            sourceFamily = CardFamily.RECOGNIZE_SENSE,
            targetFamily = CardFamily.PRODUCE_WORD,
            goodFactor = 0.35,
            easyFactor = 0.55,
        ),
        CrossFamilyCredit(
            sourceFamily = CardFamily.PRODUCE_WORD,
            targetFamily = CardFamily.PRODUCE_WORD_IN_CONTEXT,
            goodFactor = 0.35,
            easyFactor = 0.55,
        ),
        CrossFamilyCredit(
            sourceFamily = CardFamily.PRODUCE_WORD_IN_CONTEXT,
            targetFamily = CardFamily.RECOGNIZE_VOICE,
            goodFactor = 0.30,
            easyFactor = 0.45,
        ),
        CrossFamilyCredit(
            sourceFamily = CardFamily.PRODUCE_WORD,
            targetFamily = CardFamily.RECOGNIZE_SENSE,
            goodFactor = 0.85,
            easyFactor = 1.00,
        ),
        CrossFamilyCredit(
            sourceFamily = CardFamily.PRODUCE_WORD_IN_CONTEXT,
            targetFamily = CardFamily.PRODUCE_WORD,
            goodFactor = 0.85,
            easyFactor = 1.00,
        ),
        CrossFamilyCredit(
            sourceFamily = CardFamily.PRODUCE_WORD_IN_CONTEXT,
            targetFamily = CardFamily.RECOGNIZE_SENSE,
            goodFactor = 0.75,
            easyFactor = 1.00,
        ),
        CrossFamilyCredit(
            sourceFamily = CardFamily.RECOGNIZE_VOICE,
            targetFamily = CardFamily.PRODUCE_WORD_IN_CONTEXT,
            goodFactor = 0.75,
            easyFactor = 0.90,
        ),
        CrossFamilyCredit(
            sourceFamily = CardFamily.RECOGNIZE_VOICE,
            targetFamily = CardFamily.PRODUCE_WORD,
            goodFactor = 0.65,
            easyFactor = 0.80,
        ),
        CrossFamilyCredit(
            sourceFamily = CardFamily.RECOGNIZE_VOICE,
            targetFamily = CardFamily.RECOGNIZE_SENSE,
            goodFactor = 0.80,
            easyFactor = 0.95,
        ),
    )

    private val config = FsrsConfig(
        weights = WEIGHTS,
        requestRetention = REQUEST_RETENTION,
        maximumInterval = MAXIMUM_INTERVAL,
        enableFuzz = ENABLE_FUZZ,
        dailyNewTaskFamilyBudget = DAILY_NEW_TASK_FAMILY_BUDGET,
        continueNowLemmaLimit = CONTINUE_NOW_LEMMA_LIMIT,
        continueNowPendingLemmaLimit = CONTINUE_NOW_PENDING_LEMMA_LIMIT,
        pauseIntakeIfQueueAbove = PAUSE_INTAKE_IF_QUEUE_ABOVE,
        pauseIntakeRetentionMinReviews = PAUSE_INTAKE_RETENTION_MIN_REVIEWS,
        pauseIntakeIfRetentionBelow = PAUSE_INTAKE_IF_RETENTION_BELOW,
        matureStability = MATURE_STABILITY,
        defaultIntakeFamilies = DEFAULT_INTAKE_FAMILIES,
        productionUnlockStability = PRODUCTION_UNLOCK_STABILITY,
        contextUnlockStability = CONTEXT_UNLOCK_STABILITY,
        crossFamilyCredits = CROSS_FAMILY_CREDITS,
        buryFailedSessionCardsFor = BURY_FAILED_SESSION_CARDS_FOR,
        sameSenseCooldownRatio = SAME_SENSE_COOLDOWN_RATIO,
        sameLemmaCooldownRatio = SAME_LEMMA_COOLDOWN_RATIO,
        sameAnswerCooldownRatio = SAME_ANSWER_COOLDOWN_RATIO,
        siblingCooldownFloor = SIBLING_COOLDOWN_FLOOR,
        siblingCooldownCap = SIBLING_COOLDOWN_CAP,
        lemmaCooldownFloor = LEMMA_COOLDOWN_FLOOR,
        lemmaCooldownCap = LEMMA_COOLDOWN_CAP,
        sameSenseExposureRatio = SAME_SENSE_EXPOSURE_RATIO,
        sameSenseExposureFloor = SAME_SENSE_EXPOSURE_FLOOR,
        sameSenseExposureCap = SAME_SENSE_EXPOSURE_CAP,
        forwardCreditDelayRatio = FORWARD_CREDIT_DELAY_RATIO,
        forwardCreditDelayFloor = FORWARD_CREDIT_DELAY_FLOOR,
        forwardCreditDelayCap = FORWARD_CREDIT_DELAY_CAP,
        backwardCreditDelayRatio = BACKWARD_CREDIT_DELAY_RATIO,
        backwardCreditDelayFloor = BACKWARD_CREDIT_DELAY_FLOOR,
        backwardCreditDelayCap = BACKWARD_CREDIT_DELAY_CAP,
        cooldownJitterRatio = COOLDOWN_JITTER_RATIO,
        selectionCandidateLimit = SELECTION_CANDIDATE_LIMIT,
    )

    fun config(): FsrsConfig = config
}
