package com.slovy.slovymovyapp.data.learning.session

import com.slovy.slovymovyapp.analytics.PerformanceMonitoring
import com.slovy.slovymovyapp.analytics.putAttributes
import com.slovy.slovymovyapp.analytics.use
import com.slovy.slovymovyapp.analytics.useWithResult
import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.learning.*
import com.slovy.slovymovyapp.data.learning.fsrs.CrossFamilyCredit
import com.slovy.slovymovyapp.data.learning.fsrs.FsrsConfig
import com.slovy.slovymovyapp.data.learning.fsrs.FsrsDefaults
import com.slovy.slovymovyapp.data.learning.fsrs.FsrsScheduler
import com.slovy.slovymovyapp.data.learning.stats.retrievability
import com.slovy.slovymovyapp.data.remote.LanguageCard
import com.slovy.slovymovyapp.data.remote.LanguageCardResponseSense
import com.slovy.slovymovyapp.data.remote.WordFetchManager
import com.slovy.slovymovyapp.data.remote.WordResult
import com.slovy.slovymovyapp.db.FavoritesQueries
import com.slovy.slovymovyapp.db.SelectRecentReviewedCards
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import kotlin.math.roundToLong
import kotlin.time.*
import kotlin.time.Duration.Companion.days
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
        PerformanceMonitoring.startTrace("session_next_card").useWithResult(successResult = "empty") {
            putAttribute("lang", langCode)
            var attemptedCandidates = 0L
            var loadingEmissions = 0L
            try {
                val now = clock.now().toEpochMilliseconds()
                val candidates = nextCandidates(langCode, now, sessionStartedAt.toEpochMilliseconds())
                putMetric("candidates", candidates.size.toLong())
                for (card in candidates) {
                    attemptedCandidates += 1
                    val sessionCard = loadUntilTerminal(card) {
                        loadingEmissions += 1
                    }
                    if (sessionCard != null) {
                        val loadState = sessionCard.loadState()
                        markResult(loadState.name.lowercase())
                        putAttribute("family", sessionCard.card.family.name.lowercase())
                        putAttribute("variant", sessionCard.variant.kind.name.lowercase())
                        emit(sessionCard)
                        return@flow
                    }
                }
                emit(null)
            } finally {
                putMetric("attempted_candidates", attemptedCandidates)
                putMetric("loading_emissions", loadingEmissions)
            }
        }
    }.flowOn(Dispatchers.IO)

    fun previewRatings(card: SessionCard): List<GradeOutcome> =
        scheduler.preview(card.card.scheduling, clock.now(), fuzzSeed = fuzzSeed(card.card))

    suspend fun submitReview(
        card: SessionCard,
        outcome: GradeOutcome,
        durationMs: Long,
    ): SessionCard = withContext(Dispatchers.IO) {
        PerformanceMonitoring.startTrace("session_submit_review").use { trace ->
            trace.putAttributes(
                mapOf(
                    "lang" to card.card.langCode,
                    "family" to card.card.family.name.lowercase(),
                    "rating" to outcome.rating.name.lowercase(),
                    "state_before" to card.card.scheduling.state.name.lowercase(),
                ),
            )
            trace.putMetric("duration_ms", durationMs)
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
                val nextInterval = (after.dueEpochMs - nowMs).coerceAtLeast(0L)
                    .toDuration(DurationUnit.MILLISECONDS)
                if (outcome.rating.buriesSiblings()) {
                    spaceSameSenseSiblings(card.card, after, nowMs, nextInterval)
                    buryLemma(
                        card.card,
                        nowMs,
                        siblingCooldown(
                            nextInterval,
                            config.sameLemmaCooldownRatio,
                            config.lemmaCooldownFloor,
                            config.lemmaCooldownCap,
                        ),
                    )
                    if (card.card.family.testsWordRecall) {
                        buryAnswer(
                            card.card,
                            nowMs,
                            siblingCooldown(
                                nextInterval,
                                config.sameAnswerCooldownRatio,
                                config.siblingCooldownFloor,
                                config.siblingCooldownCap,
                            ),
                        )
                    }
                } else {
                    learning.setCardAvailableAfter(
                        availableAfter = after.dueEpochMs,
                        id = card.card.id,
                    )
                }
                if (outcome.rating.propagatesCredit()) {
                    propagateSameSenseCredit(card.card, after, outcome.rating, nowMs)
                }
                if (outcome.rating.unlocksNextFamily()) {
                    unlockNextFamilyIfEligible(card, after, outcome.rating, nowMs)
                }
            }

            card.copy(card = card.card.copy(scheduling = after))
        }
    }

    suspend fun putCardForLater(card: SessionCard) = withContext(Dispatchers.IO) {
        setCardAvailableAfter(card.card)
    }

    suspend fun continueDelayedCardsNow(langCode: String) = withContext(Dispatchers.IO) {
        learning.clearAvailableAfterForDelayedLemmas(
            lang_code = langCode,
            now = clock.now().toEpochMilliseconds(),
            lemma_limit = config.continueNowLemmaLimit.toLong(),
        )
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
        onLoadingEmission: () -> Unit,
    ): SessionCard? {
        val sessionCardFlow = loadSessionCard(card) ?: return null
        return sessionCardFlow
            .onEach { sessionCard ->
                if (sessionCard?.isFetchLoading() == true) {
                    onLoadingEmission()
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
        val candidates = learning.selectDueCards(
            lang_code = langCode,
            now = now,
            new_state = CardState.NEW,
            limit = limit,
        ).executeAsList()
            .map { it.toCard() }
            .plus(
                learning.selectNewCards(
                    lang_code = langCode,
                    new_state = CardState.NEW,
                    now = now,
                    limit = limit,
                ).executeAsList()
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

    private fun fuzzSeed(card: Card): Long {
        var seed = uuidSeed(card.id)
        seed = seed * 31 + card.family.ordinal
        seed = seed * 31 + card.scheduling.reps
        return seed
    }

    private fun uuidSeed(id: Uuid): Long {
        val bytes = id.toByteArray()
        var high = 0L
        var low = 0L
        for (index in 0 until 8) {
            high = (high shl 8) or (bytes[index].toLong() and 0xff)
            low = (low shl 8) or (bytes[index + 8].toLong() and 0xff)
        }
        return high xor low
    }

    private fun spaceSameSenseSiblings(
        source: Card,
        after: CardScheduling,
        nowMs: Long,
        nextInterval: Duration,
    ) {
        val exposureCooldown = sameSenseExposureCooldown(after)
        val intervalCooldown = siblingCooldown(
            nextInterval,
            config.sameSenseCooldownRatio,
            config.siblingCooldownFloor,
            config.siblingCooldownCap,
        )
        burySense(source, nowMs, maxOf(intervalCooldown, exposureCooldown))
    }

    private fun burySense(card: Card, nowMs: Long, cooldown: Duration) {
        val params = bulkBuryParams(nowMs, cooldown) ?: return
        learning.burySiblingCardsBySense(
            min_value = params.minValue,
            jitter_range = params.jitterRange,
            sense_id = card.senseId,
            lang_code = card.langCode,
            id = card.id,
        )
    }

    private fun buryLemma(card: Card, nowMs: Long, cooldown: Duration) {
        val params = bulkBuryParams(nowMs, cooldown) ?: return
        learning.burySiblingCardsByLemma(
            min_value = params.minValue,
            jitter_range = params.jitterRange,
            lang_code = card.langCode,
            lemma_id = card.lemmaId,
            sense_id = card.senseId,
            new_state = CardState.NEW,
        )
    }

    private fun buryAnswer(card: Card, nowMs: Long, cooldown: Duration) {
        val params = bulkBuryParams(nowMs, cooldown) ?: return
        learning.burySiblingCardsByAnswer(
            min_value = params.minValue,
            jitter_range = params.jitterRange,
            lang_code = card.langCode,
            answer_key = card.answerKey,
            allowed_families = WORD_RECALL_FAMILIES,
            id = card.id,
        )
    }

    private fun bulkBuryParams(nowMs: Long, cooldown: Duration): BulkBuryParams? {
        if (cooldown <= Duration.ZERO) return null
        val cooldownMillis = cooldown.inWholeMilliseconds
        val spread = (cooldownMillis * config.cooldownJitterRatio.coerceAtLeast(0.0)).roundToLong()
        return BulkBuryParams(
            minValue = nowMs + cooldownMillis - spread,
            jitterRange = (2 * spread + 1).coerceAtLeast(1L),
        )
    }

    private fun siblingCooldown(
        nextInterval: Duration,
        ratio: Double,
        floor: Duration,
        cap: Duration,
    ): Duration {
        if (ratio <= 0.0) return Duration.ZERO
        val scaled = nextInterval * ratio
        return scaled.coerceIn(floor, cap)
    }

    private fun stabilityCooldown(
        stability: Double,
        ratio: Double,
        floor: Duration,
        cap: Duration,
    ): Duration {
        if (ratio <= 0.0) return Duration.ZERO
        val scaled = stability.toDuration(DurationUnit.DAYS) * ratio
        return scaled.coerceIn(floor, cap)
    }

    private fun sameSenseExposureCooldown(after: CardScheduling): Duration =
        stabilityCooldown(
            after.stability,
            config.sameSenseExposureRatio,
            config.sameSenseExposureFloor,
            config.sameSenseExposureCap,
        )

    private fun creditDelay(stability: Double, direction: CreditDirection): Duration =
        when (direction) {
            CreditDirection.FORWARD -> stabilityCooldown(
                stability,
                config.forwardCreditDelayRatio,
                config.forwardCreditDelayFloor,
                config.forwardCreditDelayCap,
            )

            CreditDirection.BACKWARD -> stabilityCooldown(
                stability,
                config.backwardCreditDelayRatio,
                config.backwardCreditDelayFloor,
                config.backwardCreditDelayCap,
            )
        }

    private fun delayedEpochMs(nowMs: Long, delay: Duration): Long =
        nowMs + delay.inWholeMilliseconds

    private data class BulkBuryParams(
        val minValue: Long,
        val jitterRange: Long,
    )

    private fun sortedVariants(card: Card, sense: LanguageCardResponseSense?): List<CardVariant> {
        sense ?: return emptyList()
        val lastReview = learning.selectLastReviewVariantByCard(card.id).executeAsOneOrNull()
        val lastVariant = lastReview?.let { CardVariant(it.variant_kind, it.variant_target_lang) }
        return selectVariantsForReview(
            family = card.family,
            sense = sense,
            translationTargets = translationTargetsFor(sense),
            cardStability = card.scheduling.stability.toDuration(DurationUnit.DAYS),
            lastVariant = lastVariant,
        )
    }

    private fun WordResult.toSessionCard(card: Card): SessionCard? {
        val senseId = card.senseId.toString()
        val sense = this.card?.findSense(senseId)
        val studiedSenseIds = learning.selectActiveSenseIdsByLemma(
            lang_code = card.langCode,
            lemma_id = card.lemmaId,
        ).executeAsList().mapTo(HashSet()) { it.toString() }
        val sessionCards = sortedVariants(card, sense).map { variant ->
            toSessionCard(card, variant, senseId, sense, studiedSenseIds)
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
        studiedSenseIds: Set<String>,
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
            studiedSenseIds = studiedSenseIds,
        )
    }

    private fun LanguageCard.findSense(
        senseId: String,
    ) = entries.firstNotNullOfOrNull { entry ->
        entry.senses.firstOrNull { it.senseId == senseId }
    }

    private fun unlockNextFamilyIfEligible(
        card: SessionCard,
        after: CardScheduling,
        rating: Rating,
        now: Long,
    ) {
        val stability = after.stability.toDuration(DurationUnit.DAYS)
        val unlockFamily = when (card.card.family) {
            CardFamily.RECOGNIZE_SENSE if stability >= config.productionUnlockStability -> CardFamily.PRODUCE_WORD
            CardFamily.PRODUCE_WORD if stability >= config.contextUnlockStability -> CardFamily.PRODUCE_WORD_IN_CONTEXT
            CardFamily.PRODUCE_WORD_IN_CONTEXT if stability >= config.contextUnlockStability -> CardFamily.RECOGNIZE_VOICE

            else -> null
        } ?: return
        val credit = crossFamilyCredit(card.card.family, unlockFamily, rating) ?: return
        val inheritedStability = (after.stability * credit.factor).coerceAtLeast(MIN_INHERITED_STABILITY)

        val sense = card.wordResult.card?.findSense(card.senseId) ?: return
        val variants = buildTaskVariants(unlockFamily, sense, translationTargetsFor(sense))
        if (variants.isEmpty()) return
        insertTaskIfMissing(
            source = card.card,
            family = unlockFamily,
            now = now,
            due = delayedEpochMs(now, creditDelay(inheritedStability, CreditDirection.FORWARD)),
            state = CardState.LEARNING,
            stability = inheritedStability,
            difficulty = after.difficulty,
            availableAfter = delayedEpochMs(now, sameSenseExposureCooldown(after)),
        )
    }

    private fun insertTaskIfMissing(
        source: Card,
        family: CardFamily,
        now: Long,
        due: Long,
        state: CardState,
        stability: Double,
        difficulty: Double,
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
            state = state,
            stability = stability,
            difficulty = difficulty,
            due = due,
            last_review = null,
            reps = 0,
            lapses = 0,
            created_at = now,
            available_after = availableAfter,
            answer_key = source.answerKey,
            suspended = false,
        )
    }

    private fun propagateSameSenseCredit(
        source: Card,
        after: CardScheduling,
        rating: Rating,
        now: Long,
    ) {
        learning.selectCardsBySense(source.senseId, source.langCode).executeAsList()
            .map { it.toCard() }
            .filter { it.id != source.id }
            .forEach { sibling ->
                val credit = crossFamilyCredit(source.family, sibling.family, rating) ?: return@forEach
                val propagatedStability = (after.stability * credit.factor).coerceAtLeast(MIN_INHERITED_STABILITY)
                // The SQL repeats this guard so concurrent writes cannot lower stability.
                if (propagatedStability <= sibling.scheduling.stability) return@forEach

                val due = delayedEpochMs(now, creditDelay(propagatedStability, credit.direction))
                learning.creditSiblingCard(
                    state = stateAfterPropagatedCredit(sibling, propagatedStability),
                    stability = propagatedStability,
                    difficulty = after.difficulty,
                    due = due,
                    id = sibling.id,
                )
            }
    }

    private fun stateAfterPropagatedCredit(sibling: Card, stability: Double): CardState {
        val current = sibling.scheduling.state
        if (current == CardState.NEW) return CardState.LEARNING
        if (
            sibling.scheduling.reps > 0 &&
            sibling.scheduling.lastReviewEpochMs != null &&
            stability >= PROPAGATED_REVIEW_STABILITY_DAYS &&
            (current == CardState.LEARNING || current == CardState.RELEARNING)
        ) {
            return CardState.REVIEW
        }
        return current
    }

    private fun translationTargetsFor(sense: LanguageCardResponseSense): List<Language> =
        sense.targetLangDefinitions.keys
            .plus(sense.translations.keys)
            .plus(sense.examples.flatMap { it.targetLangTranslations.keys })
            .distinctBy { it.code }

    private fun Rating.buriesSiblings(): Boolean =
        this != Rating.AGAIN

    private fun Rating.unlocksNextFamily(): Boolean =
        givesCrossFamilyCredit()

    private fun Rating.propagatesCredit(): Boolean =
        givesCrossFamilyCredit()

    private fun Rating.givesCrossFamilyCredit(): Boolean =
        this == Rating.GOOD || this == Rating.EASY

    private fun crossFamilyCredit(
        sourceFamily: CardFamily,
        targetFamily: CardFamily,
        rating: Rating,
    ): AppliedCredit? {
        val credit = config.crossFamilyCredits.firstOrNull {
            it.sourceFamily == sourceFamily && it.targetFamily == targetFamily
        } ?: return null
        val factor = credit.factorFor(rating) ?: return null
        val direction = if (targetFamily.ordinal > sourceFamily.ordinal) {
            CreditDirection.FORWARD
        } else {
            CreditDirection.BACKWARD
        }
        return AppliedCredit(factor, direction)
    }

    private fun CrossFamilyCredit.factorFor(rating: Rating): Double? =
        when (rating) {
            Rating.GOOD -> goodFactor
            Rating.EASY -> easyFactor
            Rating.AGAIN,
            Rating.HARD,
                -> null
        }?.takeIf { it > 0.0 }

    private data class AppliedCredit(
        val factor: Double,
        val direction: CreditDirection,
    )

    private enum class CreditDirection {
        FORWARD,
        BACKWARD,
    }

    private companion object {
        const val HARD_EXCLUDE: Double = 1_000_000.0
        const val MIN_INHERITED_STABILITY: Double = 0.001
        const val PROPAGATED_REVIEW_STABILITY_DAYS: Double = 1.0
        val DAY: Duration = 1.days
        const val RECENT_LIMIT: Int = 20
        val WORD_RECALL_FAMILIES: List<CardFamily> =
            CardFamily.entries.filter { it.testsWordRecall }
    }
}
