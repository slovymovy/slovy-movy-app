package com.slovy.slovymovyapp.data.learning.intake

import com.slovy.slovymovyapp.analytics.Analytics
import com.slovy.slovymovyapp.analytics.AnalyticsEvent
import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.learning.CardFamily
import com.slovy.slovymovyapp.data.learning.CardState
import com.slovy.slovymovyapp.data.learning.fsrs.FsrsConfig
import com.slovy.slovymovyapp.data.learning.fsrs.FsrsDefaults
import com.slovy.slovymovyapp.data.learning.session.buildTaskVariants
import com.slovy.slovymovyapp.data.remote.DictionaryRepository
import com.slovy.slovymovyapp.db.FavoritesQueries
import com.slovy.slovymovyapp.ingestion.JsonIngestionBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.uuid.Uuid

interface LearningIntake {
    suspend fun runIntake(langCode: String): IntakeResult
    suspend fun continueWithPendingFavoritesNow(langCode: String): IntakeResult
    fun canContinueWithPendingFavoritesNow(langCode: String): Boolean
}

class IntakeService(
    private val learning: FavoritesQueries,
    private val dictionary: DictionaryRepository,
    private val config: FsrsConfig = FsrsDefaults.config(),
    private val clock: Clock,
) : LearningIntake {
    @OptIn(ExperimentalTime::class)
    override suspend fun runIntake(langCode: String): IntakeResult =
        withContext(Dispatchers.IO) {
            runAndLog(langCode, IntakeRunMode.DAILY)
        }

    @OptIn(ExperimentalTime::class)
    override suspend fun continueWithPendingFavoritesNow(langCode: String): IntakeResult =
        withContext(Dispatchers.IO) {
            runAndLog(langCode, IntakeRunMode.CONTINUE_NOW)
        }

    @OptIn(ExperimentalTime::class)
    private suspend fun runAndLog(langCode: String, mode: IntakeRunMode): IntakeResult {
        val result = activatePendingFavorites(langCode, mode)
        Analytics.logEvent(
            AnalyticsEvent.LEARNING_INTAKE_RUN,
            buildIntakeAnalyticsParams(langCode, mode, result),
        )
        return result
    }

    @OptIn(ExperimentalTime::class)
    private fun buildIntakeAnalyticsParams(
        langCode: String,
        mode: IntakeRunMode,
        result: IntakeResult,
    ): Map<String, Any> {
        val nowMs = clock.now().toEpochMilliseconds()
        val params = mutableMapOf<String, Any>(
            "lang" to langCode,
            "mode" to when (mode) {
                IntakeRunMode.DAILY -> "daily"
                IntakeRunMode.CONTINUE_NOW -> "continue_now"
            },
            "cards_created" to result.cardsCreated.toLong(),
            "activated_count" to result.activated.size.toLong(),
            "active_card_count" to learning.countCardsByLang(langCode).executeAsOne(),
            "due_count" to learning.countDueCardsByLang(langCode, nowMs).executeAsOne(),
            "delayed_due_lemma_count" to learning.countDelayedDueLemmasByLang(langCode, nowMs).executeAsOne(),
            "delayed_due_card_count" to learning.countDelayedDueCardsByLang(langCode, nowMs).executeAsOne(),
            "pending_favorite_lemma_count" to learning.countPendingFavoriteLemmasByLang(langCode).executeAsOne(),
        )
        SkipReason.entries.forEach { reason ->
            params["skip_${reason.name.lowercase()}"] = result.skipped.count { it == reason }.toLong()
        }
        return params
    }

    @OptIn(ExperimentalTime::class)
    override fun canContinueWithPendingFavoritesNow(langCode: String): Boolean =
        pendingFavoritePauseReason(langCode, clock.now()) == null

    @OptIn(ExperimentalTime::class)
    private suspend fun activatePendingFavorites(
        langCode: String,
        mode: IntakeRunMode,
    ): IntakeResult {
        val language = Language.fromCodeOrNull(langCode)
            ?: return IntakeResult(emptyList(), emptyList(), 0)
        val translationTargets = dictionary.defaultTranslationTargets(language)
            .filter { it != language }
            .distinctBy { it.code }
        val nowInstant = clock.now()
        val now = nowInstant.toEpochMilliseconds()

        val pauseReason = pendingFavoritePauseReason(langCode, nowInstant)
        if (pauseReason != null) {
            return IntakeResult(emptyList(), listOf(pauseReason), 0)
        }

        val timeZone = TimeZone.currentSystemDefault()
        val startOfToday = nowInstant.toLocalDateTime(timeZone).date
            .atStartOfDayIn(timeZone)
            .toEpochMilliseconds()
        var remainingDailyTaskFamilies: Int? = null
        if (mode == IntakeRunMode.DAILY) {
            val addedToday = learning.countNewCardsCreatedSince(langCode, startOfToday).executeAsOne()
            remainingDailyTaskFamilies = config.dailyNewTaskFamilyBudget - addedToday.toInt()
            if (remainingDailyTaskFamilies <= 0) {
                return IntakeResult(emptyList(), listOf(SkipReason.BUDGET_EXHAUSTED), 0)
            }
        }

        val familiesPerSense = config.defaultIntakeFamilies.size
        val pendingLemmaLimit = when (mode) {
            IntakeRunMode.DAILY -> requireNotNull(remainingDailyTaskFamilies) * 10
            IntakeRunMode.CONTINUE_NOW -> config.continueNowPendingLemmaLimit * 10
        }
        val pendingLemmas = learning
            .selectPendingActivationLemmas(langCode, pendingLemmaLimit.toLong())
            .executeAsList()
        val activated = mutableListOf<Uuid>()
        val skipped = mutableListOf<SkipReason>()
        val invalidatedSenses = mutableSetOf<String>()
        var cardsCreated = 0
        var activatedLemmaCount = 0

        for (row in pendingLemmas) {
            if (mode == IntakeRunMode.CONTINUE_NOW && activatedLemmaCount >= config.continueNowPendingLemmaLimit) {
                break
            }

            val upperBoundCards = row.pending_count.toInt() * familiesPerSense
            if (remainingDailyTaskFamilies != null && upperBoundCards > remainingDailyTaskFamilies) {
                skipped += SkipReason.BUDGET_EXHAUSTED
                continue
            }

            val group = learning.selectPendingActivationByLemma(langCode, row.lemma).executeAsList()
            if (group.isEmpty()) continue

            val languageCard = dictionary.getLanguageCard(
                language = language,
                lemma = row.lemma,
                translationTargets = translationTargets,
                senseIds = group.mapTo(mutableSetOf()) { it.sense_id },
            )
            if (languageCard == null) {
                skipped += SkipReason.CARD_DATA_UNAVAILABLE
                continue
            }
            val sensesById = languageCard.entries
                .flatMap { it.senses }
                .associateBy { it.senseId }

            val groupPlan = mutableListOf<IntakePlanItem>()
            for (favorite in group) {
                val sense = sensesById[favorite.sense_id]
                if (sense == null) {
                    skipped += SkipReason.CARD_DATA_UNAVAILABLE
                    continue
                }
                val tasksToCreate = config.defaultIntakeFamilies.mapNotNull { family ->
                    val variants = buildTaskVariants(family, sense, translationTargets)
                    if (variants.isEmpty()) null else family
                }
                if (tasksToCreate.isEmpty()) {
                    skipped += SkipReason.NO_TASKS_VARIANT_AVAILABLE
                    continue
                }
                groupPlan += IntakePlanItem(Uuid.parse(favorite.sense_id), tasksToCreate)
            }

            if (groupPlan.isEmpty()) continue
            val groupTotal = groupPlan.sumOf { it.families.size }

            val lemmaId = JsonIngestionBuilder.generateLemmaId(row.lemma)
            val answerKey = row.lemma.lowercase()
            learning.transaction {
                groupPlan.forEach { item ->
                    item.families.forEach { family ->
                        learning.insertCard(
                            id = Uuid.random(),
                            sense_id = item.senseId,
                            lemma_id = lemmaId,
                            lang_code = langCode,
                            family = family,
                            state = CardState.NEW,
                            stability = 0.0,
                            difficulty = 0.0,
                            due = now,
                            last_review = null,
                            reps = 0,
                            lapses = 0,
                            created_at = now,
                            available_after = null,
                            answer_key = answerKey,
                            suspended = false,
                        )
                    }
                    learning.markFavoriteActivated(now, item.senseId.toString(), langCode)
                }
            }
            groupPlan.forEach { item ->
                invalidatedSenses += item.senseId.toString()
                activated += item.senseId
            }
            cardsCreated += groupTotal
            if (remainingDailyTaskFamilies != null) {
                remainingDailyTaskFamilies -= groupTotal
            } else {
                activatedLemmaCount += 1
            }
        }

        if (invalidatedSenses.isNotEmpty()) {
            dictionary.invalidateSenses(invalidatedSenses)
        }
        return IntakeResult(activated, skipped, cardsCreated)
    }

    @OptIn(ExperimentalTime::class)
    private fun pendingFavoritePauseReason(langCode: String, now: Instant): SkipReason? {
        val nowMs = now.toEpochMilliseconds()
        val due = learning.countDueCardsByLang(langCode, nowMs).executeAsOne()
        if (due > config.pauseIntakeIfQueueAbove) return SkipReason.QUEUE_TOO_FULL

        val weekAgo = (now - 7.days).toEpochMilliseconds()
        val reviewCount = learning.countReviewsSince(langCode, weekAgo).executeAsOne()
        if (reviewCount < config.pauseIntakeRetentionMinReviews) return null

        val successful = learning.countSuccessfulReviewsSince(langCode, weekAgo).executeAsOne()
        val retention = successful.toDouble() / reviewCount
        return if (retention < config.pauseIntakeIfRetentionBelow) {
            SkipReason.RETENTION_TOO_LOW
        } else {
            null
        }
    }
}

data class IntakeResult(
    val activated: List<Uuid>,
    val skipped: List<SkipReason>,
    val cardsCreated: Int,
)

enum class SkipReason {
    QUEUE_TOO_FULL,
    RETENTION_TOO_LOW,
    BUDGET_EXHAUSTED,
    NO_TASKS_VARIANT_AVAILABLE,
    CARD_DATA_UNAVAILABLE,
}

private data class IntakePlanItem(
    val senseId: Uuid,
    val families: List<CardFamily>,
)

private enum class IntakeRunMode {
    DAILY,
    CONTINUE_NOW,
}
