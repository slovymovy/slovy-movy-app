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
    const val ENABLE_FUZZ: Boolean = false
    const val DAILY_NEW_TASK_FAMILY_BUDGET: Int = 20
    const val PAUSE_INTAKE_IF_QUEUE_ABOVE: Int = 100
    const val PAUSE_INTAKE_RETENTION_MIN_REVIEWS: Long = 50L
    const val PAUSE_INTAKE_IF_RETENTION_BELOW: Double = 0.75
    val MATURE_STABILITY: Duration = 21.days
    val PRODUCTION_UNLOCK_STABILITY: Duration = 48.hours
    val CONTEXT_UNLOCK_STABILITY: Duration = 72.hours
    const val RECOGNITION_TO_PRODUCTION_STABILITY_FACTOR: Double = 0.70
    const val PRODUCTION_TO_CONTEXT_STABILITY_FACTOR: Double = 0.60
    const val CONTEXT_TO_VOICE_STABILITY_FACTOR: Double = 0.80
    val BURY_FAILED_SESSION_CARDS_FOR: Duration = 10.minutes
    val SAME_SENSE_COOLDOWN: Duration = 30.minutes
    val SAME_LEMMA_COOLDOWN: Duration = 10.minutes
    val SAME_ANSWER_COOLDOWN: Duration = 10.minutes
    const val COOLDOWN_JITTER_RATIO: Double = 0.2
    const val SELECTION_CANDIDATE_LIMIT: Int = 50

    val DEFAULT_INTAKE_FAMILIES: List<CardFamily> = listOf(
        CardFamily.RECOGNIZE_SENSE,
    )

    private val config = FsrsConfig(
        weights = WEIGHTS,
        requestRetention = REQUEST_RETENTION,
        maximumInterval = MAXIMUM_INTERVAL,
        enableFuzz = ENABLE_FUZZ,
        dailyNewTaskFamilyBudget = DAILY_NEW_TASK_FAMILY_BUDGET,
        pauseIntakeIfQueueAbove = PAUSE_INTAKE_IF_QUEUE_ABOVE,
        pauseIntakeRetentionMinReviews = PAUSE_INTAKE_RETENTION_MIN_REVIEWS,
        pauseIntakeIfRetentionBelow = PAUSE_INTAKE_IF_RETENTION_BELOW,
        matureStability = MATURE_STABILITY,
        defaultIntakeFamilies = DEFAULT_INTAKE_FAMILIES,
        productionUnlockStability = PRODUCTION_UNLOCK_STABILITY,
        contextUnlockStability = CONTEXT_UNLOCK_STABILITY,
        recognitionToProductionStabilityFactor = RECOGNITION_TO_PRODUCTION_STABILITY_FACTOR,
        productionToContextStabilityFactor = PRODUCTION_TO_CONTEXT_STABILITY_FACTOR,
        contextToVoiceStabilityFactor = CONTEXT_TO_VOICE_STABILITY_FACTOR,
        buryFailedSessionCardsFor = BURY_FAILED_SESSION_CARDS_FOR,
        sameSenseCooldown = SAME_SENSE_COOLDOWN,
        sameLemmaCooldown = SAME_LEMMA_COOLDOWN,
        sameAnswerCooldown = SAME_ANSWER_COOLDOWN,
        cooldownJitterRatio = COOLDOWN_JITTER_RATIO,
        selectionCandidateLimit = SELECTION_CANDIDATE_LIMIT,
    )

    fun config(): FsrsConfig = config
}
