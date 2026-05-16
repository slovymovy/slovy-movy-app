package com.slovy.slovymovyapp.data.learning.fsrs

import com.slovy.slovymovyapp.data.learning.CardScheduling
import com.slovy.slovymovyapp.data.learning.CardState
import com.slovy.slovymovyapp.data.learning.GradeOutcome
import com.slovy.slovymovyapp.data.learning.Rating
import external.fsrs.CardPhase
import external.fsrs.FSRS
import external.fsrs.FlashCard
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class FsrsScheduler(
    retention: Double,
    weights: List<Double>,
    maximumInterval: Duration,
    enableFuzz: Boolean = FsrsDefaults.ENABLE_FUZZ,
) {
    private val fsrs = FSRS(
        requestRetention = retention,
        inputParams = weights,
        enableFuzz = enableFuzz,
        maximumInterval = maximumInterval.inWholeDays.toInt(),
    )

    @OptIn(ExperimentalTime::class)
    fun preview(card: CardScheduling, now: Instant, fuzzSeed: Long? = null): List<GradeOutcome> {
        return fsrs.calculate(card.toFlashCard(now), fuzzSeed = fuzzSeed).map { grade ->
            val rating = grade.choice.toAppRating()
            val intervalMillis = grade.durationMillis
            val intervalDays = intervalDays(intervalMillis)
            GradeOutcome(
                rating = rating,
                newState = nextState(card.state, rating, intervalDays),
                stability = grade.stability,
                difficulty = grade.difficulty,
                intervalMillis = intervalMillis,
            )
        }
    }

    @OptIn(ExperimentalTime::class)
    fun apply(card: CardScheduling, outcome: GradeOutcome, now: Instant): CardScheduling {
        val nowMs = now.toEpochMilliseconds()
        return card.copy(
            state = outcome.newState,
            stability = outcome.stability,
            difficulty = outcome.difficulty,
            dueEpochMs = nowMs + outcome.intervalMillis,
            lastReviewEpochMs = nowMs,
            reps = card.reps + 1,
            lapses = card.lapses + if (card.state == CardState.REVIEW && outcome.rating == Rating.AGAIN) 1 else 0,
        )
    }

    @OptIn(ExperimentalTime::class)
    fun elapsedDaysForReview(card: CardScheduling, now: Instant): Long =
        elapsedDaysSinceLastReview(card, now.toEpochMilliseconds()).toLong()

    fun scheduledDaysForReview(card: CardScheduling): Long =
        scheduledDays(card)

    @OptIn(ExperimentalTime::class)
    private fun CardScheduling.toFlashCard(now: Instant): FlashCard {
        val nowMs = now.toEpochMilliseconds()
        val scheduledDays = scheduledDays(this)
        return FlashCard(
            stability = stability,
            difficulty = difficulty,
            interval = scheduledDays.toInt().coerceAtLeast(0),
            elapsedDays = elapsedDaysSinceLastReview(this, nowMs),
            reviewCount = reps.toInt(),
            phase = when (state) {
                CardState.NEW -> CardPhase.Added.value
                CardState.LEARNING, CardState.RELEARNING -> CardPhase.ReLearning.value
                CardState.REVIEW -> CardPhase.Review.value
            },
        )
    }

    private fun scheduledDays(card: CardScheduling): Long =
        card.lastReviewEpochMs
            ?.let { ((card.dueEpochMs - it).coerceAtLeast(0L)) / DAY.inWholeMilliseconds }
            ?: 0L

    private fun elapsedDaysSinceLastReview(card: CardScheduling, nowMs: Long): Double =
        card.lastReviewEpochMs
            ?.let { (nowMs - it).coerceAtLeast(0L).toDouble() / DAY.inWholeMilliseconds }
            ?: 0.0

    private fun external.fsrs.Rating.toAppRating(): Rating =
        when (this) {
            external.fsrs.Rating.Again -> Rating.AGAIN
            external.fsrs.Rating.Hard -> Rating.HARD
            external.fsrs.Rating.Good -> Rating.GOOD
            external.fsrs.Rating.Easy -> Rating.EASY
        }
}

internal val DAY: Duration = 1.days

fun nextState(state: CardState, rating: Rating, intervalDays: Long): CardState =
    when (state) {
        CardState.NEW -> if (intervalDays >= 1) CardState.REVIEW else CardState.LEARNING
        CardState.LEARNING -> when {
            rating == Rating.AGAIN -> CardState.LEARNING
            intervalDays >= 1 -> CardState.REVIEW
            else -> CardState.LEARNING
        }

        CardState.REVIEW -> when (rating) {
            Rating.AGAIN -> CardState.RELEARNING
            Rating.HARD, Rating.GOOD, Rating.EASY -> CardState.REVIEW
        }

        CardState.RELEARNING -> when {
            rating == Rating.AGAIN -> CardState.RELEARNING
            intervalDays >= 1 -> CardState.REVIEW
            else -> CardState.RELEARNING
        }
    }

internal fun intervalDays(intervalMillis: Long): Long = intervalMillis / DAY.inWholeMilliseconds
