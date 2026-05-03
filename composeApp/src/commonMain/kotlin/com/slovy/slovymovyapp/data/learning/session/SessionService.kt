package com.slovy.slovymovyapp.data.learning.session

import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.learning.Card
import com.slovy.slovymovyapp.data.learning.CardFamily
import com.slovy.slovymovyapp.data.learning.CardKind
import com.slovy.slovymovyapp.data.learning.CardState
import com.slovy.slovymovyapp.data.learning.CardVariant
import com.slovy.slovymovyapp.data.learning.GradeOutcome
import com.slovy.slovymovyapp.data.learning.Rating
import com.slovy.slovymovyapp.data.learning.fsrs.FsrsConfig
import com.slovy.slovymovyapp.data.learning.fsrs.FsrsDefaults
import com.slovy.slovymovyapp.data.learning.fsrs.FsrsScheduler
import com.slovy.slovymovyapp.data.learning.stats.retrievability
import com.slovy.slovymovyapp.data.learning.toCard
import com.slovy.slovymovyapp.data.remote.LanguageCard
import com.slovy.slovymovyapp.data.remote.LanguageCardResponseSense
import com.slovy.slovymovyapp.data.remote.WordFetchManager
import com.slovy.slovymovyapp.data.remote.WordResult
import com.slovy.slovymovyapp.db.FavoritesQueries
import com.slovy.slovymovyapp.db.SelectRecentReviewedCards
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlin.math.roundToLong
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.time.toDuration
import kotlin.uuid.Uuid

@OptIn(ExperimentalTime::class)
class SessionService(
    private val learning: FavoritesQueries,
    private val wordFetchManager: WordFetchManager,
    private val scheduler: FsrsScheduler,
    private val examplePicker: ExamplePicker,
    private val config: FsrsConfig = FsrsDefaults.config(),
    private val clock: Clock,
    private val translationTargets: suspend (Language) -> List<Language> = { emptyList() },
) {
    fun nextCard(langCode: String, sessionStartedAt: Instant): Flow<SessionCard?> = flow {
        val now = clock.now().toEpochMilliseconds()
        for (card in nextCandidates(langCode, now, sessionStartedAt.toEpochMilliseconds())) {
            val sessionCard = loadUntilTerminal(card)
            if (sessionCard != null) {
                emit(sessionCard)
                return@flow
            }
        }
        emit(null)
    }

    suspend fun previewRatings(card: SessionCard): List<GradeOutcome> =
        scheduler.preview(card.card.scheduling, clock.now())

    suspend fun submitReview(card: SessionCard, outcome: GradeOutcome, durationMs: Long): SessionCard {
        val now = clock.now()
        val nowMs = now.toEpochMilliseconds()
        val before = card.card.scheduling
        val after = scheduler.apply(before, outcome, now)
        val elapsedDays = scheduler.elapsedDaysForReview(before, now)
        val scheduledDays = scheduler.scheduledDaysForReview(before)

        learning.transaction {
            learning.updateCardAfterReview(
                state = after.state,
                stability = after.stability,
                difficulty = after.difficulty,
                due = after.dueEpochMs,
                last_review = after.lastReviewEpochMs,
                reps = after.reps,
                lapses = after.lapses,
                id = card.card.id,
            )
            learning.insertReviewLog(
                id = Uuid.random(),
                card_id = card.card.id,
                reviewed_at = nowMs,
                rating = outcome.rating,
                variant_kind = card.variant.kind,
                variant_target_lang = card.variant.targetLang,
                example_id = card.example?.exampleIndex,
                state_before = before.state,
                stability_before = before.stability,
                difficulty_before = before.difficulty,
                elapsed_days = elapsedDays,
                scheduled_days = scheduledDays,
                stability_after = after.stability,
                difficulty_after = after.difficulty,
                duration_ms = durationMs,
            )
            if (outcome.rating.buriesSiblings()) {
                applyAvailableAfter(
                    cards = learning.selectAvailableAfterCandidatesBySense(
                        sense_id = card.card.senseId,
                        lang_code = card.card.langCode,
                        id = card.card.id,
                    ).executeAsList().map { it.toCard() },
                    nowMs = nowMs,
                    cooldown = config.sameSenseCooldown,
                    relation = "same-sense",
                )
                applyAvailableAfter(
                    cards = learning.selectAvailableAfterCandidatesByLemma(
                        lang_code = card.card.langCode,
                        lemma_id = card.card.lemmaId,
                        sense_id = card.card.senseId,
                    ).executeAsList().map { it.toCard() },
                    nowMs = nowMs,
                    cooldown = config.sameLemmaCooldown,
                    relation = "same-lemma",
                )
                if (card.card.family.testsWordRecall) {
                    applyAvailableAfter(
                        cards = learning.selectAvailableAfterCandidatesByAnswer(
                            lang_code = card.card.langCode,
                            answer_key = card.card.answerKey,
                            id = card.card.id,
                        ).executeAsList()
                            .map { it.toCard() }
                            .filter { it.family.testsWordRecall },
                        nowMs = nowMs,
                        cooldown = config.sameAnswerCooldown,
                        relation = "same-answer",
                    )
                }
            } else {
                learning.setCardAvailableAfter(
                    availableAfter = after.dueEpochMs,
                    id = card.card.id,
                )
            }
            if (outcome.rating.unlocksNextFamily()) {
                unlockNextFamilyIfEligible(card, after, nowMs)
            }
        }

        return card.copy(card = card.card.copy(scheduling = after))
    }

    fun putCardForLater(card: SessionCard) {
        setCardAvailableAfter(card.card)
    }

    private suspend fun loadSessionCard(card: Card): Flow<SessionCard?>? {
        val favorite =
            learning.selectFavoriteWithActivation(card.senseId.toString(), card.langCode).executeAsOneOrNull()
                ?: return null
        val language = Language.fromCodeOrNull(card.langCode)
            ?: error("Unknown learning card language: ${card.langCode}")
        val targets = translationTargets(language)
            .filter { it != language }
            .distinctBy { it.code }

        return wordFetchManager.getWord(
            language = language,
            lemma = favorite.lemma,
            translationTargets = targets,
        ).map { result ->
            result.toSessionCard(card)
        }
    }

    private suspend fun FlowCollector<SessionCard?>.loadUntilTerminal(
        card: Card,
    ): SessionCard? {
        val sessionCardFlow = loadSessionCard(card) ?: return null
        return sessionCardFlow
            .onEach { sessionCard ->
                if (sessionCard?.isFetchLoading() == true) {
                    emit(sessionCard)
                }
            }
            .first { it == null || !it.isFetchLoading() }
    }

    private fun SessionCard.isFetchLoading(): Boolean =
        wordResult.isWordLoading || wordResult.isTranslationLoading

    @OptIn(ExperimentalTime::class)
    private fun setCardAvailableAfter(card: Card) {
        val delay = config.buryFailedSessionCardsFor
        if (delay <= Duration.ZERO) return
        val availableAfter = clock.now().toEpochMilliseconds() + delay.inWholeMilliseconds
        learning.setCardAvailableAfter(
            availableAfter = availableAfter,
            id = card.id,
        )
    }

    private fun nextCandidates(
        langCode: String,
        now: Long,
        sessionStartedAt: Long,
    ): List<Card> {
        val limit = config.selectionCandidateLimit.toLong()
        val recentReviews = learning.selectRecentReviewedCards(langCode, sessionStartedAt, RECENT_LIMIT.toLong())
            .executeAsList()
        val candidates = learning.selectDueCards(langCode, now, limit).executeAsList()
            .map { it.toCard() }
            .plus(
                learning.selectNewCards(langCode, now, limit).executeAsList()
                    .map { it.toCard() }
            )

        return candidates
            .map { it to priority(it, now, recentReviews) }
            .filter { (_, score) -> score > -HARD_EXCLUDE / 2 }
            .sortedByDescending { (_, score) -> score }
            .map { (card, _) -> card }
    }

    private fun priority(card: Card, now: Long, recentReviews: List<SelectRecentReviewedCards>): Double {
        val scheduling = card.scheduling
        val memoryUrgency = when (scheduling.state) {
            CardState.LEARNING,
            CardState.RELEARNING,
            -> 1000.0

            CardState.REVIEW -> {
                val elapsedDays = scheduling.lastReviewEpochMs
                    ?.let { (now - it).coerceAtLeast(0L).toDouble() / DAY.inWholeMilliseconds }
                    ?: 0.0
                (config.requestRetention - retrievability(scheduling.stability, elapsedDays)).coerceAtLeast(0.0) * 100.0
            }

            CardState.NEW -> 10.0
        }
        val overdueBonus = ((now - scheduling.dueEpochMs).coerceAtLeast(0L).toDouble() / DAY.inWholeMilliseconds) * 2.0
        return memoryUrgency + overdueBonus - collisionPenalty(card, recentReviews)
    }

    private fun collisionPenalty(card: Card, recentReviews: List<SelectRecentReviewedCards>): Double {
        if (recentReviews.take(3).any { it.sense_id == card.senseId }) return HARD_EXCLUDE

        var penalty = 0.0
        if (recentReviews.take(5).any { it.lemma_id == card.lemmaId }) penalty += 80.0
        if (card.family.testsWordRecall && recentReviews.take(5).any {
                it.family.testsWordRecall && it.answer_key == card.answerKey
            }) {
            penalty += 120.0
        }
        if (recentReviews.take(2).any { it.family == card.family }) penalty += 15.0
        return penalty
    }

    private fun availableAfter(nowMs: Long, cooldown: Duration, salt: String): Long? {
        if (cooldown <= Duration.ZERO) return null
        return nowMs + jitteredCooldown(cooldown, salt).inWholeMilliseconds
    }

    private fun applyAvailableAfter(
        cards: List<Card>,
        nowMs: Long,
        cooldown: Duration,
        relation: String,
    ) {
        if (cooldown <= Duration.ZERO) return

        cards.forEach { affected ->
            learning.setCardAvailableAfter(
                availableAfter = nowMs + jitteredCooldown(
                    cooldown = cooldown,
                    salt = "$relation:${affected.id}",
                ).inWholeMilliseconds,
                id = affected.id,
            )
        }
    }

    private fun jitteredCooldown(cooldown: Duration, salt: String): Duration {
        val cooldownMillis = cooldown.inWholeMilliseconds
        val spread = (cooldownMillis * config.cooldownJitterRatio.coerceAtLeast(0.0)).roundToLong()
        if (spread <= 0) return cooldown

        val range = spread * 2 + 1
        val hash = salt.hashCode().toUInt().toLong()
        val offset = (hash % range) - spread
        return (cooldownMillis + offset).coerceAtLeast(1L).toDuration(DurationUnit.MILLISECONDS)
    }

    private fun sortedVariants(card: Card, sense: LanguageCardResponseSense?): List<CardVariant> {
        val generated = sense
            ?.let { buildTaskVariants(card.family, it, translationTargetsFor(it)) }
            .orEmpty()
            .ifEmpty { listOf(fallbackVariant(card.family)) }
        val lastVariant = learning.selectLastReviewVariantByCard(card.id).executeAsOneOrNull()

        return generated.sortedWith(
            compareBy<CardVariant> { variant ->
                if (lastVariant?.variant_kind == variant.kind &&
                    lastVariant.variant_target_lang == variant.targetLang
                ) {
                    1
                } else {
                    0
                }
            }.thenBy { variantPriority(it.kind) }
                .thenBy { it.targetLang ?: "" }
        )
    }

    private fun variantPriority(kind: CardKind): Int =
        when (kind) {
            CardKind.WORD_TO_SOURCE_DEFINITION -> 0
            CardKind.WORD_TO_TRANSLATION -> 1
            CardKind.SOURCE_DEFINITION_TO_WORD -> 0
            CardKind.TRANSLATION_TO_WORD -> 1
            CardKind.CLOZE_SOURCE -> 0
            CardKind.CLOZE_TRANSLATION -> 1
        }

    private fun fallbackVariant(family: CardFamily): CardVariant =
        when (family) {
            CardFamily.RECOGNIZE_SENSE -> CardVariant(
                CardKind.WORD_TO_SOURCE_DEFINITION,
                targetLang = null,
            )

            CardFamily.PRODUCE_WORD -> CardVariant(
                CardKind.SOURCE_DEFINITION_TO_WORD,
                targetLang = null,
            )

            CardFamily.PRODUCE_WORD_IN_CONTEXT -> CardVariant(
                CardKind.CLOZE_SOURCE,
                targetLang = null,
            )
        }

    private fun WordResult.toSessionCard(card: Card): SessionCard? {
        val senseId = card.senseId.toString()
        val sense = this.card?.findSense(senseId)
        val sessionCards = sortedVariants(card, sense).map { variant ->
            toSessionCard(card, variant, senseId, sense)
        }

        return sessionCards.firstOrNull { it.loadState() == SessionCardLoadState.READY }
            ?: sessionCards.firstOrNull { it.loadState() == SessionCardLoadState.LOADING }
            ?: sessionCards.firstOrNull()
    }

    private fun WordResult.toSessionCard(
        card: Card,
        variant: CardVariant,
        senseId: String,
        sense: LanguageCardResponseSense?,
    ): SessionCard {
        val example = if (variant.kind.isCloze && sense != null) {
            val targetLanguage = variant.targetLang?.let(Language::fromCodeOrNull)
            if (variant.kind.requiresTranslation) {
                targetLanguage?.let { examplePicker.pickTranslation(card.senseId, sense.examples, it) }
            } else {
                examplePicker.pick(card.senseId, sense.examples)
            }
        } else {
            null
        }

        return SessionCard(
            card = card,
            variant = variant,
            wordResult = this,
            senseId = senseId,
            example = example,
        )
    }

    private fun LanguageCard.findSense(
        senseId: String,
    ) = entries.firstNotNullOfOrNull { entry ->
        entry.senses.firstOrNull { it.senseId == senseId }
    }

    private fun unlockNextFamilyIfEligible(card: SessionCard, after: com.slovy.slovymovyapp.data.learning.CardScheduling, now: Long) {
        val stability = after.stability.toDuration(DurationUnit.DAYS)
        val familyToUnlock = when {
            card.card.family == CardFamily.RECOGNIZE_SENSE &&
                    stability >= config.productionUnlockStability -> CardFamily.PRODUCE_WORD

            card.card.family == CardFamily.PRODUCE_WORD &&
                    stability >= config.contextUnlockStability -> CardFamily.PRODUCE_WORD_IN_CONTEXT

            else -> null
        } ?: return

        val sense = card.wordResult.card?.findSense(card.senseId) ?: return
        val variants = buildTaskVariants(familyToUnlock, sense, translationTargetsFor(sense))
        if (variants.isEmpty()) return
        insertTaskIfMissing(
            source = card.card,
            family = familyToUnlock,
            now = now,
            availableAfter = availableAfter(
                nowMs = now,
                cooldown = config.sameSenseCooldown,
                salt = "${card.card.id}:unlock-$familyToUnlock",
            ),
        )
    }

    private fun insertTaskIfMissing(
        source: Card,
        family: CardFamily,
        now: Long,
        availableAfter: Long?,
    ) {
        val existing = learning.selectCardBySenseAndFamily(source.senseId, source.langCode, family)
            .executeAsOneOrNull()
        if (existing != null) return

        val cardId = Uuid.random()
        learning.insertCard(
            id = cardId,
            sense_id = source.senseId,
            lemma_id = source.lemmaId,
            lang_code = source.langCode,
            family = family,
            state = CardState.NEW,
            stability = 0.0,
            difficulty = 0.0,
            due = now,
            last_review = null,
            reps = 0,
            lapses = 0,
            created_at = now,
            available_after = availableAfter,
            answer_key = source.answerKey,
            suspended = false,
        )
    }

    private fun translationTargetsFor(sense: LanguageCardResponseSense): List<Language> =
        sense.targetLangDefinitions.keys
            .plus(sense.translations.keys)
            .plus(sense.examples.flatMap { it.targetLangTranslations.keys })
            .distinctBy { it.code }

    private fun Rating.buriesSiblings(): Boolean =
        this != Rating.AGAIN

    private fun Rating.unlocksNextFamily(): Boolean =
        this == Rating.GOOD || this == Rating.EASY

    private companion object {
        const val HARD_EXCLUDE: Double = 1_000_000.0
        val DAY: Duration = 1.days
        const val RECENT_LIMIT: Int = 20
    }
}
