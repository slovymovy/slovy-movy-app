package com.slovy.slovymovyapp.data.learning.stats

import com.slovy.slovymovyapp.data.learning.Card
import com.slovy.slovymovyapp.data.learning.CardFamily
import com.slovy.slovymovyapp.data.learning.CardKind
import com.slovy.slovymovyapp.data.learning.CardState
import com.slovy.slovymovyapp.data.learning.CardVariant
import com.slovy.slovymovyapp.data.learning.Rating
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.uuid.Uuid

data class SenseProgress @OptIn(ExperimentalTime::class) constructor(
    val senseId: Uuid,
    val langCode: String,
    val totalCards: Int,
    val newCards: Int,
    val learningCards: Int,
    val reviewCards: Int,
    val matureCards: Int,
    val avgStability: Double?,
    val nextDue: Instant?,
    val lapses: Int,
    val familyProgress: List<CardFamilyProgress> = emptyList(),
    val variantProgress: List<CardVariantProgress> = emptyList(),
)

data class CardFamilyProgress @OptIn(ExperimentalTime::class) constructor(
    val family: CardFamily,
    val totalCards: Int,
    val newCards: Int,
    val learningCards: Int,
    val reviewCards: Int,
    val matureCards: Int,
    val avgStability: Double?,
    val nextDue: Instant?,
    val lapses: Int,
    val reviews: Int,
    val successfulReviews: Int,
    val retention: Double?,
)

data class CardVariantProgress @OptIn(ExperimentalTime::class) constructor(
    val variant: CardVariant,
    val reviews: Int,
    val successfulReviews: Int,
    val lapses: Int,
    val retention: Double?,
    val averageDurationMs: Long?,
    val lastReviewedAt: Instant?,
)

data class CardSummary @OptIn(ExperimentalTime::class) constructor(
    val card: Card,
    val state: CardState,
    val stability: Double,
    val difficulty: Double,
    val due: Instant,
    val reps: Int,
    val lapses: Int,
    val retrievabilityNow: Double,
    val variantProgress: List<CardVariantProgress> = emptyList(),
)

data class ReviewLogEntry @OptIn(ExperimentalTime::class) constructor(
    val reviewedAt: Instant,
    val rating: Rating,
    val variantKind: CardKind,
    val variantTargetLang: String?,
    val exampleId: Long?,
    val stateBefore: CardState,
    val stabilityBefore: Double,
    val difficultyBefore: Double,
    val elapsedDays: Long,
    val scheduledDays: Long,
    val stabilityAfter: Double,
    val difficultyAfter: Double,
    val durationMs: Long,
)

data class GlobalStats(
    val totalCards: Int,
    val dueToday: Int,
    val reviewedLast7d: Int,
    val rollingRetention7d: Double?,
    val matureCount: Int,
)
