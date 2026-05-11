package com.slovy.slovymovyapp.data.learning

import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.db.enumOrdinalAdapter
import com.slovy.slovymovyapp.data.db.ratingFsrsAdapter
import com.slovy.slovymovyapp.data.learning.fsrs.FsrsDefaults
import com.slovy.slovymovyapp.data.learning.fsrs.FsrsScheduler
import com.slovy.slovymovyapp.data.learning.session.SessionCard
import com.slovy.slovymovyapp.data.learning.session.SessionCardLoadErrorReason
import com.slovy.slovymovyapp.data.learning.session.SessionCardLoadState
import com.slovy.slovymovyapp.data.learning.stats.retrievability
import com.slovy.slovymovyapp.data.remote.DictionaryClientException
import com.slovy.slovymovyapp.data.remote.WordResult
import kotlin.math.abs
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.uuid.Uuid

@OptIn(ExperimentalTime::class)
class LearningDomainTest {
    @Test
    fun card_kind_order_is_frozen() {
        assertEquals(
            listOf(
                "SOURCE_DEFINITION_TO_WORD",
                "WORD_TO_SOURCE_DEFINITION",
                "CLOZE_SOURCE",
                "TRANSLATION_TO_WORD",
                "WORD_TO_TRANSLATION",
                "CLOZE_TRANSLATION",
                "LISTENING_TRANSLATION",
            ),
            CardKind.entries.map { it.name },
        )
    }

    @Test
    fun adapters_round_trip_locked_values() {
        val kindAdapter = enumOrdinalAdapter<CardKind>()
        val ratingAdapter = ratingFsrsAdapter()

        assertEquals(3L, kindAdapter.encode(CardKind.TRANSLATION_TO_WORD))
        assertEquals(CardKind.TRANSLATION_TO_WORD, kindAdapter.decode(3L))
        assertEquals(3L, ratingAdapter.encode(Rating.GOOD))
        assertEquals(Rating.GOOD, ratingAdapter.decode(3L))
    }

    @Test
    fun scheduler_preview_is_deterministic_and_intervals_are_ordered() {
        val config = FsrsDefaults.config()
        val scheduler = FsrsScheduler(config.requestRetention, config.weights, config.maximumInterval)
        val card = scheduling()
        val now = Instant.fromEpochMilliseconds(1_700_000_000_000L)

        val first = scheduler.preview(card, now)
        val second = scheduler.preview(card, now)
        assertEquals(Rating.entries.toList(), first.map { it.rating })
        assertEquals(first, second)

        val byRating = first.associateBy { it.rating }
        assertTrue(byRating.getValue(Rating.AGAIN).intervalMillis < byRating.getValue(Rating.HARD).intervalMillis)
        assertTrue(byRating.getValue(Rating.HARD).intervalMillis < byRating.getValue(Rating.GOOD).intervalMillis)
        assertTrue(byRating.getValue(Rating.GOOD).intervalMillis < byRating.getValue(Rating.EASY).intervalMillis)
    }

    @Test
    fun scheduler_initializes_new_card_state_from_selected_rating_without_rounding() {
        val config = FsrsDefaults.config()
        val scheduler = FsrsScheduler(config.requestRetention, config.weights, config.maximumInterval)
        val now = Instant.fromEpochMilliseconds(1_700_000_000_000L)

        val preview = scheduler.preview(scheduling(), now)

        assertEquals(FsrsDefaults.WEIGHTS[0], preview.first { it.rating == Rating.AGAIN }.stability)
        assertEquals(FsrsDefaults.WEIGHTS[2], preview.first { it.rating == Rating.GOOD }.stability)
        assertTrue(preview.first { it.rating == Rating.AGAIN }.difficulty > 0.0)
    }

    @Test
    fun scheduler_clamps_initial_stability_to_minimum_only() {
        val config = FsrsDefaults.config()
        val weights = config.weights.toMutableList().also { it[0] = 0.0001 }
        val scheduler = FsrsScheduler(config.requestRetention, weights, config.maximumInterval)
        val now = Instant.fromEpochMilliseconds(1_700_000_000_000L)

        val again = scheduler.preview(scheduling(), now).first { it.rating == Rating.AGAIN }

        assertEquals(0.001, again.stability)
    }

    @Test
    fun fsrs_review_uses_actual_elapsed_days_for_retrievability() {
        val config = FsrsDefaults.config()
        val fsrs = external.fsrs.FSRS(
            requestRetention = config.requestRetention,
            inputParams = config.weights,
            maximumInterval = config.maximumInterval.inWholeDays.toInt(),
        )
        val onTime = external.fsrs.FlashCard(
            stability = 5.0,
            difficulty = 5.0,
            interval = 1,
            elapsedDays = 1.0,
            reviewCount = 3,
            phase = external.fsrs.CardPhase.Review.value,
        )
        val delayed = onTime.copy(elapsedDays = 10.0)

        val onTimeGood = fsrs.calculate(onTime).first { it.choice == external.fsrs.Rating.Good }
        val delayedGood = fsrs.calculate(delayed).first { it.choice == external.fsrs.Rating.Good }

        assertTrue(delayedGood.stability > onTimeGood.stability)
    }

    @Test
    fun scheduler_counts_lapse_only_when_review_first_enters_relearning() {
        val config = FsrsDefaults.config()
        val scheduler = FsrsScheduler(config.requestRetention, config.weights, config.maximumInterval)
        val now = Instant.fromEpochMilliseconds(1_700_000_000_000L)
        val reviewCard = scheduling().copy(
            state = CardState.REVIEW,
            stability = 4.0,
            difficulty = 5.0,
            lastReviewEpochMs = (now - 3.days).toEpochMilliseconds(),
            reps = 3,
        )

        val missedReview = scheduler.apply(
            reviewCard,
            scheduler.preview(reviewCard, now).first { it.rating == Rating.AGAIN },
            now,
        )
        val missedRelearning = scheduler.apply(
            missedReview,
            scheduler.preview(missedReview, now).first { it.rating == Rating.AGAIN },
            now,
        )

        assertEquals(CardState.RELEARNING, missedReview.state)
        assertEquals(1L, missedReview.lapses)
        assertEquals(CardState.RELEARNING, missedRelearning.state)
        assertEquals(1L, missedRelearning.lapses)
    }

    @Test
    fun retrievability_matches_fsrs_curve_sanity() {
        assertClose(1.0, retrievability(10.0, 0.0))
        assertClose(0.9, retrievability(10.0, 10.0))
        val decay = -0.3
        val factor = 0.9.pow(1.0 / decay) - 1
        assertClose((1.0 + factor * 0.5).pow(decay), retrievability(10.0, 5.0, decay))
    }

    @Test
    fun config_matches_defaults() {
        val config = FsrsDefaults.config()

        assertEquals(FsrsDefaults.DEFAULT_INTAKE_FAMILIES, config.defaultIntakeFamilies)
        assertEquals(FsrsDefaults.PRODUCTION_UNLOCK_STABILITY, config.productionUnlockStability)
        assertEquals(FsrsDefaults.CONTEXT_UNLOCK_STABILITY, config.contextUnlockStability)
        assertEquals(FsrsDefaults.PAUSE_INTAKE_RETENTION_MIN_REVIEWS, config.pauseIntakeRetentionMinReviews)
        assertEquals(
            FsrsDefaults.RECOGNITION_TO_PRODUCTION_STABILITY_FACTOR,
            config.recognitionToProductionStabilityFactor,
        )
        assertEquals(
            FsrsDefaults.PRODUCTION_TO_CONTEXT_STABILITY_FACTOR,
            config.productionToContextStabilityFactor,
        )
        assertEquals(
            FsrsDefaults.CONTEXT_TO_VOICE_STABILITY_FACTOR,
            config.contextToVoiceStabilityFactor,
        )
        assertEquals(FsrsDefaults.BURY_FAILED_SESSION_CARDS_FOR, config.buryFailedSessionCardsFor)
        assertEquals(FsrsDefaults.SAME_SENSE_COOLDOWN_RATIO, config.sameSenseCooldownRatio)
        assertEquals(FsrsDefaults.SAME_LEMMA_COOLDOWN_RATIO, config.sameLemmaCooldownRatio)
        assertEquals(FsrsDefaults.SAME_ANSWER_COOLDOWN_RATIO, config.sameAnswerCooldownRatio)
        assertEquals(FsrsDefaults.SIBLING_COOLDOWN_FLOOR, config.siblingCooldownFloor)
        assertEquals(FsrsDefaults.SIBLING_COOLDOWN_CAP, config.siblingCooldownCap)
        assertEquals(FsrsDefaults.COOLDOWN_JITTER_RATIO, config.cooldownJitterRatio)
    }

    @Test
    fun session_card_error_only_result_is_visible_and_retryable() {
        val senseId = Uuid.random()
        val error = DictionaryClientException.RawDataMissingException("missing", Language.ENGLISH)
        val sessionCard = SessionCard(
            card = card(senseId),
            variant = CardVariant(CardKind.WORD_TO_SOURCE_DEFINITION, targetLang = null),
            wordResult = WordResult(error = error),
            senseId = senseId.toString(),
            example = null,
        )

        assertEquals(SessionCardLoadState.ERROR, sessionCard.loadState())
        assertEquals(SessionCardLoadErrorReason.WORD_LOAD_FAILED, sessionCard.loadError()?.reason)
        assertEquals(error, sessionCard.loadError()?.cause)
        assertTrue(sessionCard.canRetry())
        assertFalse(sessionCard.isReady())
    }

    private fun card(senseId: Uuid): Card = Card(
        id = Uuid.random(),
        senseId = senseId,
        lemmaId = Uuid.random(),
        langCode = "en",
        family = CardFamily.RECOGNIZE_SENSE,
        answerKey = "test",
        scheduling = scheduling(),
    )

    private fun scheduling(): CardScheduling = CardScheduling(
        state = CardState.NEW,
        stability = 0.0,
        difficulty = 0.0,
        dueEpochMs = 1_700_000_000_000L,
        lastReviewEpochMs = null,
        reps = 0,
        lapses = 0,
        createdAtEpochMs = 1_700_000_000_000L,
        availableAfterEpochMs = null,
        suspended = false,
    )

    private fun assertClose(expected: Double, actual: Double) {
        assertTrue(abs(expected - actual) < 0.0001, "expected=$expected actual=$actual")
    }
}
