package com.slovy.slovymovyapp.data.learning.stats

import com.slovy.slovymovyapp.data.learning.Card
import com.slovy.slovymovyapp.data.learning.CardFamily
import com.slovy.slovymovyapp.data.learning.CardState
import com.slovy.slovymovyapp.data.learning.CardVariant
import com.slovy.slovymovyapp.data.learning.Rating
import com.slovy.slovymovyapp.data.learning.fsrs.DAY
import com.slovy.slovymovyapp.data.learning.fsrs.FsrsConfig
import com.slovy.slovymovyapp.data.learning.fsrs.FsrsDefaults
import com.slovy.slovymovyapp.data.learning.toCard
import com.slovy.slovymovyapp.db.FavoritesQueries
import com.slovy.slovymovyapp.db.SelectReviewLogsBySense
import kotlin.math.pow
import kotlin.time.Clock
import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.time.toDuration
import kotlin.uuid.Uuid

class StatsService(
    private val learning: FavoritesQueries,
    private val config: FsrsConfig = FsrsDefaults.config(),
    private val clock: Clock,
) {
    private val matureStabilityDays = config.matureStability.toDouble(DurationUnit.DAYS)

    @OptIn(ExperimentalTime::class)
    fun progressForSense(senseId: Uuid, langCode: String): SenseProgress {
        val cards = learning.selectCardsBySense(senseId, langCode).executeAsList().map { it.toCard() }
        val reviewLogs = learning.selectReviewLogsBySense(
            sense_id = senseId,
            lang_code = langCode,
        ).executeAsList()
        val nextDue = learning.selectNextDueBySense(senseId, langCode).executeAsOneOrNull()?.next_due
        val lapses = learning.selectLapsesBySense(senseId, langCode).executeAsOneOrNull()?.total_lapses ?: 0L
        return SenseProgress(
            senseId = senseId,
            langCode = langCode,
            totalCards = cards.size,
            newCards = cards.count { it.scheduling.state == CardState.NEW },
            learningCards = cards.count { it.scheduling.state == CardState.LEARNING || it.scheduling.state == CardState.RELEARNING },
            reviewCards = cards.count { it.scheduling.state == CardState.REVIEW },
            matureCards = cards.count { it.scheduling.stability.toDuration(DurationUnit.DAYS) >= config.matureStability },
            avgStability = cards.takeIf { it.isNotEmpty() }?.map { it.scheduling.stability }?.average(),
            nextDue = nextDue?.let { Instant.fromEpochMilliseconds(it) },
            lapses = lapses.toInt(),
            familyProgress = CardFamily.entries.mapNotNull { family ->
                familyProgress(
                    family = family,
                    cards = cards.filter { it.family == family },
                    reviewLogs = reviewLogs.filter { it.family == family },
                )
            },
            variantProgress = variantProgress(reviewLogs),
        )
    }

    @OptIn(ExperimentalTime::class)
    fun cardsForSense(senseId: Uuid, langCode: String): List<CardSummary> {
        val now = clock.now()
        val reviewLogsByCard = learning.selectReviewLogsBySense(
            sense_id = senseId,
            lang_code = langCode,
        ).executeAsList().groupBy { it.card_id }
        return learning.selectCardsBySense(senseId, langCode).executeAsList().map { row ->
            val card = row.toCard()
            val scheduling = card.scheduling
            val elapsed =
                scheduling.lastReviewEpochMs?.let {
                    ((now.toEpochMilliseconds() - it).coerceAtLeast(0L)).toDouble() / DAY.inWholeMilliseconds
                }
                    ?: 0.0
            CardSummary(
                card = card,
                state = scheduling.state,
                stability = scheduling.stability,
                difficulty = scheduling.difficulty,
                due = Instant.fromEpochMilliseconds(scheduling.dueEpochMs),
                reps = scheduling.reps.toInt(),
                lapses = scheduling.lapses.toInt(),
                retrievabilityNow = retrievability(scheduling.stability, elapsed, -config.weights[20]),
                variantProgress = variantProgress(reviewLogsByCard[row.id].orEmpty()),
            )
        }
    }

    @OptIn(ExperimentalTime::class)
    fun historyForCard(cardId: Uuid, limit: Int): List<ReviewLogEntry> =
        learning.selectReviewLogsByCard(cardId, limit.toLong()).executeAsList().map { row ->
            ReviewLogEntry(
                reviewedAt = Instant.fromEpochMilliseconds(row.reviewed_at),
                rating = row.rating,
                variantKind = row.variant_kind,
                variantTargetLang = row.variant_target_lang,
                exampleId = row.example_id,
                stateBefore = row.state_before,
                stabilityBefore = row.stability_before,
                difficultyBefore = row.difficulty_before,
                elapsedDays = row.elapsed_days,
                scheduledDays = row.scheduled_days,
                stabilityAfter = row.stability_after,
                difficultyAfter = row.difficulty_after,
                durationMs = row.duration_ms,
            )
        }

    @OptIn(ExperimentalTime::class)
    fun globalStats(langCode: String): GlobalStats {
        val now = clock.now().toEpochMilliseconds()
        val since = now - 7 * DAY.inWholeMilliseconds
        val totalReviews = learning.countReviewsSince(langCode, since).executeAsOne()
        val successfulReviews = learning.countSuccessfulReviewsSince(langCode, since).executeAsOne()
        return GlobalStats(
            totalCards = learning.countCardsByLang(langCode).executeAsOne().toInt(),
            dueToday = learning.countDueCardsByLangDistinctBySense(langCode, now).executeAsOne().toInt(),
            reviewedLast7d = totalReviews.toInt(),
            rollingRetention7d = if (totalReviews == 0L) null else successfulReviews.toDouble() / totalReviews,
            matureCount = learning.countMatureCardsByLang(langCode, matureStabilityDays).executeAsOne().toInt(),
        )
    }

    @OptIn(ExperimentalTime::class)
    private fun familyProgress(
        family: CardFamily,
        cards: List<Card>,
        reviewLogs: List<SelectReviewLogsBySense>,
    ): CardFamilyProgress? {
        if (cards.isEmpty() && reviewLogs.isEmpty()) return null

        val nextDue = cards
            .map { card ->
                val due = card.scheduling.dueEpochMs
                val availableAfter = card.scheduling.availableAfterEpochMs
                if (availableAfter != null && availableAfter > due) availableAfter else due
            }
            .minOrNull()
        val successfulReviews = reviewLogs.count { it.rating != Rating.AGAIN }
        return CardFamilyProgress(
            family = family,
            totalCards = cards.size,
            newCards = cards.count { it.scheduling.state == CardState.NEW },
            learningCards = cards.count {
                it.scheduling.state == CardState.LEARNING || it.scheduling.state == CardState.RELEARNING
            },
            reviewCards = cards.count { it.scheduling.state == CardState.REVIEW },
            matureCards = cards.count { it.scheduling.stability.toDuration(DurationUnit.DAYS) >= config.matureStability },
            avgStability = cards.takeIf { it.isNotEmpty() }?.map { it.scheduling.stability }?.average(),
            nextDue = nextDue?.let { Instant.fromEpochMilliseconds(it) },
            lapses = cards.sumOf { it.scheduling.lapses }.toInt(),
            reviews = reviewLogs.size,
            successfulReviews = successfulReviews,
            retention = reviewLogs.takeIf { it.isNotEmpty() }?.let { successfulReviews.toDouble() / it.size },
        )
    }

    @OptIn(ExperimentalTime::class)
    private fun variantProgress(reviewLogs: List<SelectReviewLogsBySense>): List<CardVariantProgress> =
        reviewLogs
            .groupBy { CardVariant(it.variant_kind, it.variant_target_lang) }
            .map { (variant, logs) ->
                val successfulReviews = logs.count { it.rating != Rating.AGAIN }
                CardVariantProgress(
                    variant = variant,
                    reviews = logs.size,
                    successfulReviews = successfulReviews,
                    lapses = logs.count { it.rating == Rating.AGAIN },
                    retention = successfulReviews.toDouble() / logs.size,
                    averageDurationMs = logs.map { it.duration_ms }.average().toLong(),
                    lastReviewedAt = logs.maxOfOrNull { it.reviewed_at }?.let { Instant.fromEpochMilliseconds(it) },
                )
            }
            .sortedWith(
                compareBy<CardVariantProgress> { it.variant.kind.family.ordinal }
                    .thenBy { it.variant.kind.ordinal }
                    .thenBy { it.variant.targetLang ?: "" }
            )
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
