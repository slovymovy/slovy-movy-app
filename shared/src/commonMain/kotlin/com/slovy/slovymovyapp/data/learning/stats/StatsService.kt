package com.slovy.slovymovyapp.data.learning.stats

import com.slovy.slovymovyapp.analytics.PerformanceMonitoring
import com.slovy.slovymovyapp.analytics.putAttributes
import com.slovy.slovymovyapp.analytics.use
import com.slovy.slovymovyapp.data.learning.CardState
import com.slovy.slovymovyapp.data.learning.fsrs.DAY
import com.slovy.slovymovyapp.data.learning.fsrs.FsrsDefaults
import com.slovy.slovymovyapp.db.FavoritesQueries
import com.slovy.slovymovyapp.db.SelectCardSchedulingByLang
import com.slovy.slovymovyapp.ingestion.JsonIngestionBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.datetime.*
import kotlin.math.pow
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class StatsService(
    private val learning: FavoritesQueries,
    private val clock: Clock,
) {

    @OptIn(ExperimentalTime::class)
    fun statsScreenData(
        langCode: String,
        viewMonth: StatsYearMonth,
        today: LocalDate,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): StatsScreenData = PerformanceMonitoring.startTrace("stats_screen_data").use { trace ->
        trace.putAttributes(mapOf("lang" to langCode))
        val todayStartMs = today.atStartOfDayIn(timeZone).toEpochMilliseconds()
        val tomorrowStartMs = today.plus(1, DateTimeUnit.DAY).atStartOfDayIn(timeZone).toEpochMilliseconds()
        val weekStartDate = today.minus(today.dayOfWeek.ordinal, DateTimeUnit.DAY)
        val weekStartMs = weekStartDate.atStartOfDayIn(timeZone).toEpochMilliseconds()

        val reviewsToday = learning.countReviewsBetween(
            lang_code = langCode,
            start_inclusive = todayStartMs,
            end_exclusive = tomorrowStartMs,
        ).executeAsOne().toInt()
        val reviewsWeek = learning.countReviewsSince(
            lang_code = langCode,
            since = weekStartMs,
        ).executeAsOne().toInt()

        val practicedDays = learning
            .selectReviewTimestampsSince(langCode, since = 0L)
            .executeAsList()
            .asSequence()
            .map { Instant.fromEpochMilliseconds(it).toLocalDateTime(timeZone).date }
            .toSet()
        val streakDays = computeStreak(today = today, practicedDays = practicedDays)

        val monthStart = LocalDate(viewMonth.year, viewMonth.monthZeroBased + 1, 1)
        val monthEnd = if (viewMonth.monthZeroBased == 11) {
            LocalDate(viewMonth.year + 1, 1, 1)
        } else {
            LocalDate(viewMonth.year, viewMonth.monthZeroBased + 2, 1)
        }
        val practiceLog = learning
            .selectReviewTimestampsBetween(
                lang_code = langCode,
                start_inclusive = monthStart.atStartOfDayIn(timeZone).toEpochMilliseconds(),
                end_exclusive = monthEnd.atStartOfDayIn(timeZone).toEpochMilliseconds(),
            )
            .executeAsList()
            .asSequence()
            .map { Instant.fromEpochMilliseconds(it).toLocalDateTime(timeZone).date }
            .map { StatsPracticeDay(it.year, it.month.number - 1, it.day) }
            .toSet()

        val cardRows = learning.selectCardSchedulingByLang(langCode).executeAsList()
        val queuedLemmas = learning.selectPendingFavoriteLemmasByLang(langCode).executeAsList()
        val wordsTotal = cardRows.asSequence()
            .map { it.lemma_id }
            .plus(queuedLemmas.asSequence().map { JsonIngestionBuilder.generateLemmaId(it) })
            .toSet()
            .size
        val pipeline = computePipeline(
            cardRows = cardRows,
            queuedCount = learning.countPendingFavoritesByLang(langCode).executeAsOne().toInt(),
        )

        val nowMs = clock.now().toEpochMilliseconds()
        val delayedDueLemmaCount = learning.countDelayedDueLemmasByLang(langCode, nowMs).executeAsOne().toInt()
        val delayedDueCardCount = learning.countDelayedDueCardsByLang(langCode, nowMs).executeAsOne().toInt()

        trace.putMetric("card_rows", cardRows.size.toLong())
        trace.putMetric("queued_lemmas", queuedLemmas.size.toLong())
        trace.putMetric("words_total", wordsTotal.toLong())
        trace.putMetric("reviews_today", reviewsToday.toLong())
        trace.putMetric("delayed_due_lemmas", delayedDueLemmaCount.toLong())
        StatsScreenData(
            streakDays = streakDays,
            practiceLog = practiceLog,
            reviewsToday = reviewsToday,
            reviewsWeek = reviewsWeek,
            wordsTotal = wordsTotal,
            pipeline = pipeline,
            delayedDueLemmaCount = delayedDueLemmaCount,
            delayedDueCardCount = delayedDueCardCount,
        )
    }

    private fun computeStreak(today: LocalDate, practicedDays: Set<LocalDate>): Int {
        if (practicedDays.isEmpty()) return 0
        var streak = 0
        var cursor = today
        // Allow today's "no reviews yet" to not break the streak.
        if (today !in practicedDays) {
            cursor = today.minus(1, DateTimeUnit.DAY)
        }
        while (cursor in practicedDays) {
            streak++
            cursor = cursor.minus(1, DateTimeUnit.DAY)
        }
        return streak
    }

    private fun computePipeline(
        cardRows: List<SelectCardSchedulingByLang>,
        queuedCount: Int,
    ): List<StatsPipelineStage> {
        val counts = StatsPipelineStageId.entries.associateWith { 0 }.toMutableMap()
        counts[StatsPipelineStageId.QUEUE] = queuedCount
        cardRows
            .groupBy { it.sense_id }
            .values
            .forEach { senseCards ->
                val stage = classifySense(senseCards)
                counts[stage] = counts.getValue(stage) + 1
            }
        return StatsPipelineStageId.entries.map { id ->
            StatsPipelineStage(id = id, count = counts.getValue(id))
        }
    }

    private fun classifySense(
        senseCards: List<SelectCardSchedulingByLang>
    ): StatsPipelineStageId {
        if (senseCards.any { it.state == CardState.NEW }) return StatsPipelineStageId.NEW
        val minStability = senseCards.minOf { it.stability }
        return when {
            minStability >= MATURITY_STABILITY_DAYS -> StatsPipelineStageId.LEARNED
            minStability < WEAK_MAX_DAYS -> StatsPipelineStageId.FRESH
            minStability < MIDDLE_MAX_DAYS -> StatsPipelineStageId.MIDDLE
            else -> StatsPipelineStageId.STRONG
        }
    }

    @OptIn(ExperimentalTime::class)
    fun globalStats(langCode: String): GlobalStats {
        val now = clock.now().toEpochMilliseconds()
        val since = now - 7 * DAY.inWholeMilliseconds
        val totalReviews = learning.countReviewsSince(langCode, since).executeAsOne()
        val successfulReviews = learning.countSuccessfulReviewsSince(langCode, since).executeAsOne()
        return GlobalStats(
            totalCards = learning.countCardsByLang(langCode).executeAsOne().toInt(),
            dueToday = learning.countDueCardsByLangDistinctByLemma(langCode, now).executeAsOne().toInt(),
            reviewedLast7d = totalReviews.toInt(),
            rollingRetention7d = if (totalReviews == 0L) null else successfulReviews.toDouble() / totalReviews,
            matureCount = learning.countMatureCardsByLang(langCode, MATURITY_STABILITY_DAYS).executeAsOne().toInt(),
        )
    }

    @OptIn(ExperimentalTime::class)
    suspend fun dueNow(langCode: String): Int = withContext(Dispatchers.IO) {
        val now = clock.now().toEpochMilliseconds()
        learning.countDueCardsByLangDistinctByLemma(langCode, now).executeAsOne().toInt()
    }

    @OptIn(ExperimentalTime::class)
    fun reviewQueueStats(langCode: String): ReviewQueueStats {
        val now = clock.now().toEpochMilliseconds()
        return ReviewQueueStats(
            activeCardCount = learning.countCardsByLang(langCode).executeAsOne().toInt(),
            dueToday = learning.countDueCardsByLangDistinctByLemma(langCode, now).executeAsOne().toInt(),
            delayedDueLemmaCount = learning.countDelayedDueLemmasByLang(langCode, now).executeAsOne().toInt(),
            delayedDueCardCount = learning.countDelayedDueCardsByLang(langCode, now).executeAsOne().toInt(),
            pendingFavoriteLemmaCount = learning.countPendingFavoriteLemmasByLang(langCode).executeAsOne().toInt(),
            nextReviewAtEpochMs = learning.selectNextReviewAtByLang(langCode).executeAsOne().next_review_at,
        )
    }

}

fun retrievability(
    stabilityDays: Double,
    elapsedDays: Double,
    decay: Double = -FsrsDefaults.WEIGHTS[20],
): Double {
    if (stabilityDays <= 0.0) return 0.0
    val factor = 0.9.pow(1.0 / decay) - 1
    return (1.0 + factor * elapsedDays / stabilityDays).pow(decay)
}

private const val WEAK_MAX_DAYS = 3.0
private const val MIDDLE_MAX_DAYS = 14.0
const val MATURITY_STABILITY_DAYS = 90.0
