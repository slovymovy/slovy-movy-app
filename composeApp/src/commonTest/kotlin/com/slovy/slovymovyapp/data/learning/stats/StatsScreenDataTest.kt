package com.slovy.slovymovyapp.data.learning.stats

import com.slovy.slovymovyapp.data.learning.CardFamily
import com.slovy.slovymovyapp.data.learning.CardState
import com.slovy.slovymovyapp.data.learning.Rating
import com.slovy.slovymovyapp.db.AppDatabase
import com.slovy.slovymovyapp.db.FavoritesQueries
import com.slovy.slovymovyapp.ingestion.JsonIngestionBuilder
import com.slovy.slovymovyapp.test.BaseTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.uuid.Uuid

@OptIn(ExperimentalTime::class)
class StatsScreenDataTest : BaseTest() {

    private val tz = TimeZone.UTC
    private val today = LocalDate(2026, 5, 10)
    private val nowInstant: Instant = today.atStartOfDayIn(tz) + 12.hours
    private lateinit var db: AppDatabase
    private lateinit var queries: FavoritesQueries

    @BeforeTest
    fun setUp() {
        db = testAppDatabaseHolder().database
        queries = db.favoritesQueries
        queries.deleteAll()
        queries.deleteAllCards()
        queries.deleteAllLearning()
    }

    @AfterTest
    fun tearDown() {
        queries.deleteAllLearning()
        queries.deleteAllCards()
        queries.deleteAll()
    }

    @Test
    fun pipeline_classifies_senses_by_worst_card() {
        val newSense = Uuid.random()
        insertCard(senseId = newSense, lemmaId = Uuid.random(), state = CardState.NEW, stability = 0.0)
        insertCard(senseId = newSense, lemmaId = Uuid.random(), state = CardState.REVIEW, stability = 30.0)

        val weakSense = Uuid.random()
        insertCard(senseId = weakSense, lemmaId = Uuid.random(), state = CardState.LEARNING, stability = 0.5)

        val middleSense = Uuid.random()
        insertCard(senseId = middleSense, lemmaId = Uuid.random(), state = CardState.REVIEW, stability = 7.0)

        val strongSense = Uuid.random()
        insertCard(senseId = strongSense, lemmaId = Uuid.random(), state = CardState.REVIEW, stability = 18.0)

        val matureDays = MATURITY_STABILITY_DAYS
        val learnedSense = Uuid.random()
        insertCard(
            senseId = learnedSense,
            lemmaId = Uuid.random(),
            state = CardState.REVIEW,
            stability = matureDays + 5
        )

        val service = newService()
        val data = service.statsScreenData("en", today, tz)

        val counts = data.pipeline.associate { it.id to it.count }
        assertEquals(0, counts[StatsPipelineStageId.QUEUE])
        assertEquals(1, counts[StatsPipelineStageId.NEW])
        assertEquals(1, counts[StatsPipelineStageId.FRESH])
        assertEquals(1, counts[StatsPipelineStageId.MIDDLE])
        assertEquals(1, counts[StatsPipelineStageId.STRONG])
        assertEquals(1, counts[StatsPipelineStageId.LEARNED])
    }

    @Test
    fun reward_snapshot_returns_streak_and_pipeline() {
        val cardId = Uuid.random()
        insertCard(
            senseId = Uuid.random(),
            lemmaId = Uuid.random(),
            state = CardState.REVIEW,
            stability = 7.0,
            cardId = cardId,
        )
        insertReviewLog(cardId, today.atStartOfDayIn(tz) + 8.hours)
        insertReviewLog(cardId, today.atStartOfDayIn(tz) - 1.days + 8.hours)

        val snapshot = newService().rewardSnapshot("en", timeZone = tz, today = today)
        val counts = snapshot.pipeline.associate { it.id to it.count }

        assertEquals(2, snapshot.streakDays)
        assertEquals(1, counts[StatsPipelineStageId.MIDDLE])
    }

    @Test
    fun words_total_counts_distinct_lemmas() {
        val lemmaA = Uuid.random()
        val lemmaB = Uuid.random()
        insertCard(senseId = Uuid.random(), lemmaId = lemmaA, state = CardState.REVIEW, stability = 1.0)
        insertCard(senseId = Uuid.random(), lemmaId = lemmaA, state = CardState.LEARNING, stability = 0.2)
        insertCard(senseId = Uuid.random(), lemmaId = lemmaB, state = CardState.REVIEW, stability = 5.0)

        val data = newService().statsScreenData("en", today, tz)
        assertEquals(2, data.wordsTotal)
    }

    @Test
    fun queue_counts_pending_favorites_and_includes_unique_queued_lemmas_in_words_total() {
        insertCard(
            senseId = Uuid.random(),
            lemmaId = JsonIngestionBuilder.generateLemmaId("scheduled"),
            state = CardState.REVIEW,
            stability = 1.0
        )
        insertCard(
            senseId = Uuid.random(),
            lemmaId = JsonIngestionBuilder.generateLemmaId("shared"),
            state = CardState.REVIEW,
            stability = 1.0
        )

        insertFavorite(senseId = Uuid.random(), lemma = "shared")
        insertFavorite(senseId = Uuid.random(), lemma = "shared")
        insertFavorite(senseId = Uuid.random(), lemma = "queued-only")
        insertFavorite(senseId = Uuid.random(), lemma = "activated", activatedAt = nowInstant.toEpochMilliseconds())
        insertFavorite(senseId = Uuid.random(), lemma = "other-language", langCode = "nl")

        val data = newService().statsScreenData("en", today, tz)
        val counts = data.pipeline.associate { it.id to it.count }

        assertEquals(3, counts[StatsPipelineStageId.QUEUE])
        assertEquals(3, data.wordsTotal)

        val otherLanguage = newService().statsScreenData("nl", today, tz)
        val otherCounts = otherLanguage.pipeline.associate { it.id to it.count }
        assertEquals(1, otherCounts[StatsPipelineStageId.QUEUE])
        assertEquals(1, otherLanguage.wordsTotal)
    }

    @Test
    fun reviews_today_uses_local_midnight_boundary() {
        val cardId = Uuid.random()
        insertCard(
            senseId = Uuid.random(),
            lemmaId = Uuid.random(),
            state = CardState.REVIEW,
            stability = 1.0,
            cardId = cardId
        )
        // Yesterday 23:59 — must NOT count
        insertReviewLog(cardId, today.atStartOfDayIn(tz) - 1.hours)
        // Today 00:00 — counts
        insertReviewLog(cardId, today.atStartOfDayIn(tz))
        // Today noon — counts
        insertReviewLog(cardId, today.atStartOfDayIn(tz) + 12.hours)

        val data = newService().statsScreenData("en", today, tz)
        assertEquals(2, data.reviewsToday)
    }

    @Test
    fun reviews_week_starts_at_local_monday() {
        // 2026-05-10 is a Sunday. Week-Monday = 2026-05-04.
        val cardId = Uuid.random()
        insertCard(
            senseId = Uuid.random(),
            lemmaId = Uuid.random(),
            state = CardState.REVIEW,
            stability = 1.0,
            cardId = cardId
        )
        insertReviewLog(cardId, LocalDate(2026, 5, 3).atStartOfDayIn(tz) + 23.hours) // Sun (prev week) — must NOT count
        insertReviewLog(cardId, LocalDate(2026, 5, 4).atStartOfDayIn(tz)) // Mon — counts
        insertReviewLog(cardId, LocalDate(2026, 5, 7).atStartOfDayIn(tz) + 8.hours) // Thu — counts
        insertReviewLog(cardId, today.atStartOfDayIn(tz) + 12.hours) // today — counts

        val data = newService().statsScreenData("en", today, tz)
        assertEquals(3, data.reviewsWeek)
    }

    @Test
    fun streak_counts_consecutive_days_back_from_today() {
        val cardId = Uuid.random()
        insertCard(
            senseId = Uuid.random(),
            lemmaId = Uuid.random(),
            state = CardState.REVIEW,
            stability = 1.0,
            cardId = cardId
        )
        // today + 4 prior days, then a gap
        insertReviewLog(cardId, today.atStartOfDayIn(tz) + 10.hours)
        insertReviewLog(cardId, today.atStartOfDayIn(tz) - 1.days + 12.hours)
        insertReviewLog(cardId, today.atStartOfDayIn(tz) - 2.days + 12.hours)
        insertReviewLog(cardId, today.atStartOfDayIn(tz) - 3.days + 12.hours)
        // gap on day -4
        insertReviewLog(cardId, today.atStartOfDayIn(tz) - 5.days + 12.hours)

        val data = newService().statsScreenData("en", today, tz)
        assertEquals(4, data.streakDays)
    }

    @Test
    fun streak_does_not_break_when_today_has_no_reviews_yet() {
        val cardId = Uuid.random()
        insertCard(
            senseId = Uuid.random(),
            lemmaId = Uuid.random(),
            state = CardState.REVIEW,
            stability = 1.0,
            cardId = cardId
        )
        insertReviewLog(cardId, today.atStartOfDayIn(tz) - 1.days + 12.hours)
        insertReviewLog(cardId, today.atStartOfDayIn(tz) - 2.days + 12.hours)

        val data = newService().statsScreenData("en", today, tz)
        assertEquals(2, data.streakDays)
    }

    @Test
    fun practice_log_includes_all_practice_days_for_local_month_paging() {
        val cardId = Uuid.random()
        insertCard(
            senseId = Uuid.random(),
            lemmaId = Uuid.random(),
            state = CardState.REVIEW,
            stability = 1.0,
            cardId = cardId
        )
        // April 2026
        insertReviewLog(cardId, LocalDate(2026, 4, 30).atStartOfDayIn(tz) + 12.hours)
        // May 2026 — same month as today
        insertReviewLog(cardId, LocalDate(2026, 5, 1).atStartOfDayIn(tz) + 12.hours)
        insertReviewLog(cardId, LocalDate(2026, 5, 1).atStartOfDayIn(tz) + 20.hours) // dedup
        insertReviewLog(cardId, today.atStartOfDayIn(tz) + 12.hours)

        val data = newService().statsScreenData("en", today, tz)
        assertEquals(
            setOf(
                StatsPracticeDay(2026, 3, 30),
                StatsPracticeDay(2026, 4, 1),
                StatsPracticeDay(2026, 4, 10),
            ),
            data.practiceLog,
        )
    }

    @Test
    fun active_days_total_counts_distinct_practice_days_across_all_time() {
        val cardId = Uuid.random()
        insertCard(
            senseId = Uuid.random(),
            lemmaId = Uuid.random(),
            state = CardState.REVIEW,
            stability = 1.0,
            cardId = cardId
        )
        insertReviewLog(cardId, LocalDate(2024, 4, 7).atStartOfDayIn(tz) + 8.hours)
        insertReviewLog(cardId, LocalDate(2024, 4, 7).atStartOfDayIn(tz) + 20.hours)
        insertReviewLog(cardId, LocalDate(2025, 8, 12).atStartOfDayIn(tz) + 12.hours)
        insertReviewLog(cardId, today.atStartOfDayIn(tz) + 12.hours)

        val data = newService().statsScreenData("en", today, tz)
        assertEquals(3, data.activeDaysTotal)
    }

    @Test
    fun minutes_today_and_week_sum_review_duration_with_local_boundaries() {
        val cardId = Uuid.random()
        insertCard(
            senseId = Uuid.random(),
            lemmaId = Uuid.random(),
            state = CardState.REVIEW,
            stability = 1.0,
            cardId = cardId
        )
        insertReviewLog(cardId, LocalDate(2026, 5, 3).atStartOfDayIn(tz) + 23.hours, durationMs = 1.minutes.inWholeMilliseconds)
        insertReviewLog(cardId, LocalDate(2026, 5, 4).atStartOfDayIn(tz) + 8.hours, durationMs = 61.seconds.inWholeMilliseconds)
        insertReviewLog(cardId, today.atStartOfDayIn(tz) + 12.hours, durationMs = 1.hours.inWholeMilliseconds)
        insertReviewLog(cardId, today.atStartOfDayIn(tz) + 18.hours, durationMs = 30.seconds.inWholeMilliseconds)

        val data = newService().statsScreenData("en", today, tz)
        assertEquals(61, data.minutesToday)
        assertEquals(62, data.minutesWeek)
    }

    @Test
    fun effort_minutes_are_scoped_to_lang_code() {
        val cardEn = Uuid.random()
        val cardNl = Uuid.random()
        insertCard(
            senseId = Uuid.random(),
            lemmaId = Uuid.random(),
            state = CardState.REVIEW,
            stability = 1.0,
            cardId = cardEn,
            langCode = "en"
        )
        insertCard(
            senseId = Uuid.random(),
            lemmaId = Uuid.random(),
            state = CardState.REVIEW,
            stability = 1.0,
            cardId = cardNl,
            langCode = "nl"
        )
        insertReviewLog(cardEn, today.atStartOfDayIn(tz) + 8.hours, durationMs = 120_000)
        insertReviewLog(cardNl, today.atStartOfDayIn(tz) + 9.hours, durationMs = 240_000)

        val en = newService().statsScreenData("en", today, tz)
        val nl = newService().statsScreenData("nl", today, tz)

        assertEquals(2, en.minutesToday)
        assertEquals(4, nl.minutesToday)
    }

    @Test
    fun streak_extends_beyond_one_year_of_history() {
        val cardId = Uuid.random()
        insertCard(
            senseId = Uuid.random(),
            lemmaId = Uuid.random(),
            state = CardState.REVIEW,
            stability = 1.0,
            cardId = cardId
        )
        val streakLength = 400
        for (offset in 0 until streakLength) {
            insertReviewLog(cardId, today.atStartOfDayIn(tz) - offset.days + 12.hours)
        }

        val data = newService().statsScreenData("en", today, tz)
        assertEquals(streakLength, data.streakDays)
    }

    @Test
    fun practice_log_includes_months_older_than_one_year_for_local_paging() {
        val cardId = Uuid.random()
        insertCard(
            senseId = Uuid.random(),
            lemmaId = Uuid.random(),
            state = CardState.REVIEW,
            stability = 1.0,
            cardId = cardId
        )
        // The service returns the full practice log so the UI can page older months locally.
        insertReviewLog(cardId, LocalDate(2024, 4, 7).atStartOfDayIn(tz) + 12.hours)
        insertReviewLog(cardId, LocalDate(2024, 4, 22).atStartOfDayIn(tz) + 12.hours)

        val data = newService().statsScreenData("en", today, tz)
        assertEquals(setOf(7, 22), data.practiceLog.map { it.day }.toSet())
        assertTrue(data.practiceLog.all { it.year == 2024 && it.monthZeroBased == 3 })
    }

    @Test
    fun results_are_scoped_to_lang_code() {
        val cardEn = Uuid.random()
        val cardNl = Uuid.random()
        insertCard(
            senseId = Uuid.random(),
            lemmaId = Uuid.random(),
            state = CardState.REVIEW,
            stability = 1.0,
            cardId = cardEn,
            langCode = "en"
        )
        insertCard(
            senseId = Uuid.random(),
            lemmaId = Uuid.random(),
            state = CardState.REVIEW,
            stability = 1.0,
            cardId = cardNl,
            langCode = "nl"
        )
        insertReviewLog(cardEn, today.atStartOfDayIn(tz) + 8.hours)
        insertReviewLog(cardNl, today.atStartOfDayIn(tz) + 9.hours)

        val en = newService().statsScreenData("en", today, tz)
        assertEquals(1, en.reviewsToday)
        assertEquals(1, en.wordsTotal)

        val nl = newService().statsScreenData("nl", today, tz)
        assertEquals(1, nl.reviewsToday)
        assertEquals(1, nl.wordsTotal)
    }

    @Test
    fun suspended_cards_are_ignored() {
        insertCard(
            senseId = Uuid.random(),
            lemmaId = Uuid.random(),
            state = CardState.REVIEW,
            stability = 1.0,
            suspended = true
        )
        insertCard(senseId = Uuid.random(), lemmaId = Uuid.random(), state = CardState.REVIEW, stability = 1.0)

        val data = newService().statsScreenData("en", today, tz)
        assertEquals(1, data.wordsTotal)
        assertEquals(1, data.pipeline.sumOf { it.count })
    }

    private fun newService(): StatsService = StatsService(
        learning = queries,
        clock = object : Clock {
            override fun now(): Instant = nowInstant
        },
    )

    private fun insertCard(
        senseId: Uuid,
        lemmaId: Uuid,
        state: CardState,
        stability: Double,
        cardId: Uuid = Uuid.random(),
        langCode: String = "en",
        suspended: Boolean = false,
    ) {
        queries.insertCard(
            id = cardId,
            sense_id = senseId,
            lemma_id = lemmaId,
            lang_code = langCode,
            family = CardFamily.RECOGNIZE_SENSE,
            state = state,
            stability = stability,
            difficulty = 5.0,
            due = nowInstant.toEpochMilliseconds(),
            last_review = null,
            reps = 0,
            lapses = 0,
            created_at = nowInstant.toEpochMilliseconds(),
            available_after = null,
            answer_key = "ans-${cardId}",
            suspended = suspended,
        )
    }

    private fun insertFavorite(
        senseId: Uuid,
        lemma: String,
        langCode: String = "en",
        activatedAt: Long? = null,
    ) {
        queries.insertFavorite(
            sense_id = senseId.toString(),
            lang_code = langCode,
            lemma = lemma,
            created_at = nowInstant.toEpochMilliseconds(),
            activated_at = activatedAt,
        )
    }

    private fun insertReviewLog(
        cardId: Uuid,
        at: Instant,
        rating: Rating = Rating.GOOD,
        durationMs: Long = 1500,
    ) {
        queries.insertReviewLog(
            id = Uuid.random(),
            card_id = cardId,
            reviewed_at = at.toEpochMilliseconds(),
            rating = rating,
            variant_kind = com.slovy.slovymovyapp.data.learning.CardKind.WORD_TO_SOURCE_DEFINITION,
            variant_target_lang = null,
            example_id = null,
            state_before = CardState.REVIEW,
            stability_before = 1.0,
            difficulty_before = 5.0,
            elapsed_days = 1,
            scheduled_days = 1,
            stability_after = 1.5,
            difficulty_after = 5.0,
            duration_ms = durationMs,
        )
    }
}
