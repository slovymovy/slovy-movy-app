package com.slovy.slovymovyapp.data.learning.intake

import com.slovy.slovymovyapp.analytics.Analytics
import com.slovy.slovymovyapp.analytics.AnalyticsEvent
import com.slovy.slovymovyapp.analytics.PerformanceMonitoring
import com.slovy.slovymovyapp.analytics.putAttributes
import com.slovy.slovymovyapp.analytics.use
import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.learning.CardFamily
import com.slovy.slovymovyapp.data.learning.CardState
import com.slovy.slovymovyapp.data.learning.fsrs.FsrsConfig
import com.slovy.slovymovyapp.data.learning.fsrs.FsrsDefaults
import com.slovy.slovymovyapp.data.learning.session.buildTaskVariants
import com.slovy.slovymovyapp.data.remote.DictionaryRepository
import com.slovy.slovymovyapp.db.FavoritesQueries
import com.slovy.slovymovyapp.ingestion.JsonIngestionBuilder
import com.slovy.slovymovyapp.logging.AppLogger
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
    private suspend fun runAndLog(langCode: String, mode: IntakeRunMode): IntakeResult =
        PerformanceMonitoring.startTrace("learning_intake_run").use { trace ->
            trace.putAttributes(
                mapOf(
                    "lang" to langCode,
                    "mode" to when (mode) {
                        IntakeRunMode.DAILY -> "daily"
                        IntakeRunMode.CONTINUE_NOW -> "continue_now"
                    },
                ),
            )
            val result = activatePendingFavorites(langCode, mode)
            AppLogger.debug(
                TAG,
                "Intake completed lang=$langCode mode=${mode.logName()} cardsCreated=${result.cardsCreated} " +
                    "activated=${result.activated.size} skipped=${skipSummary(result.skipped)}",
                null,
            )
            trace.putMetric("cards_created", result.cardsCreated.toLong())
            trace.putMetric("activated", result.activated.size.toLong())
            trace.putMetric("skipped", result.skipped.size.toLong())
            SkipReason.entries.forEach { reason ->
                val count = result.skipped.count { it == reason }.toLong()
                if (count > 0L) trace.putMetric("skip_${reason.name.lowercase()}", count)
            }
            Analytics.logEvent(
                AnalyticsEvent.LEARNING_INTAKE_RUN,
                buildIntakeAnalyticsParams(langCode, mode, result),
            )
            result
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
        if (language == null) {
            AppLogger.debug(TAG, "Intake skipped: unknown language lang=$langCode mode=${mode.logName()}", null)
            return IntakeResult(emptyList(), emptyList(), 0)
        }
        val translationTargets = dictionary.defaultTranslationTargets(language)
            .filter { it != language }
            .distinctBy { it.code }
        val nowInstant = clock.now()
        val now = nowInstant.toEpochMilliseconds()

        val timeZone = TimeZone.currentSystemDefault()
        val startOfToday = nowInstant.toLocalDateTime(timeZone).date
            .atStartOfDayIn(timeZone)
            .toEpochMilliseconds()

        val familiesPerSense = config.defaultIntakeFamilies.size
        val pendingLemmaLimit = when (mode) {
            IntakeRunMode.DAILY -> maxOf(
                config.dailyNewTaskFamilyBudget,
                config.continueNowPendingLemmaLimit,
            ) * 10
            IntakeRunMode.CONTINUE_NOW -> config.continueNowPendingLemmaLimit * 10
        }
        val (extensions, newcomers) = learning
            .selectPendingActivationLemmas(langCode, pendingLemmaLimit.toLong())
            .executeAsList()
            .partition { it.is_extension }
        AppLogger.debug(
            TAG,
            "Intake selected pending lemmas lang=$langCode mode=${mode.logName()} " +
                "translationTargets=${translationTargets.joinToString { it.code }} " +
                "pendingLemmaLimit=$pendingLemmaLimit extensions=${extensions.size} newcomers=${newcomers.size}",
            null,
        )

        val activated = mutableListOf<Uuid>()
        val skipped = mutableListOf<SkipReason>()
        val invalidatedSenses = mutableSetOf<String>()
        var cardsCreated = 0

        suspend fun apply(lemma: String) {
            val outcome = activateLemma(lemma, langCode, language, translationTargets, now)
            skipped += outcome.skipped
            invalidatedSenses += outcome.activatedSenses.map { it.toString() }
            activated += outcome.activatedSenses
            cardsCreated += outcome.cardsCreated
            AppLogger.debug(
                TAG,
                "Intake lemma processed lang=$langCode mode=${mode.logName()} lemma=$lemma " +
                    "cardsCreated=${outcome.cardsCreated} activatedSenses=${outcome.activatedSenses.size} " +
                    "skipped=${skipSummary(outcome.skipped)}",
                null,
            )
        }

        for (row in extensions) apply(row.lemma)

        if (newcomers.isNotEmpty()) {
            when (val gate = newcomerGate(langCode, mode, nowInstant, startOfToday)) {
                is NewcomerGate.Blocked -> {
                    skipped += gate.reason
                    AppLogger.debug(
                        TAG,
                        "Intake newcomers blocked lang=$langCode mode=${mode.logName()} reason=${gate.reason}",
                        null,
                    )
                }

                is NewcomerGate.Open -> {
                    AppLogger.debug(
                        TAG,
                        "Intake newcomers open lang=$langCode mode=${mode.logName()} dailyBudget=${gate.dailyBudget ?: "none"}",
                        null,
                    )
                    var remainingDailyBudget = gate.dailyBudget
                    var activatedLemmaCount = 0
                    for (row in newcomers) {
                        if (mode == IntakeRunMode.CONTINUE_NOW &&
                            activatedLemmaCount >= config.continueNowPendingLemmaLimit
                        ) {
                            AppLogger.debug(
                                TAG,
                                "Intake continue-now lemma limit reached lang=$langCode limit=${config.continueNowPendingLemmaLimit}",
                                null,
                            )
                            break
                        }
                        val upperBoundCards = row.pending_count.toInt() * familiesPerSense
                        val budget = remainingDailyBudget
                        if (budget != null && upperBoundCards > budget) {
                            skipped += SkipReason.BUDGET_EXHAUSTED
                            AppLogger.debug(
                                TAG,
                                "Intake lemma skipped by budget lang=$langCode lemma=${row.lemma} " +
                                    "upperBoundCards=$upperBoundCards remainingBudget=$budget",
                                null,
                            )
                            continue
                        }
                        val before = cardsCreated
                        apply(row.lemma)
                        val created = cardsCreated - before
                        if (created == 0) continue
                        if (budget != null) {
                            remainingDailyBudget = budget - created
                        } else {
                            activatedLemmaCount += 1
                        }
                    }
                }
            }
        }

        if (invalidatedSenses.isNotEmpty()) {
            dictionary.invalidateSenses(invalidatedSenses)
            AppLogger.debug(
                TAG,
                "Intake invalidated dictionary senses lang=$langCode count=${invalidatedSenses.size}",
                null,
            )
        }
        return IntakeResult(activated, skipped, cardsCreated)
    }

    @OptIn(ExperimentalTime::class)
    private fun newcomerGate(
        langCode: String,
        mode: IntakeRunMode,
        nowInstant: Instant,
        startOfToday: Long,
    ): NewcomerGate {
        val pauseReason = pendingFavoritePauseReason(langCode, nowInstant)
        if (pauseReason != null) return NewcomerGate.Blocked(pauseReason)
        if (mode != IntakeRunMode.DAILY) return NewcomerGate.Open(dailyBudget = null)
        val addedToday = learning.countNewCardsCreatedSince(langCode, startOfToday).executeAsOne()
        val remaining = config.dailyNewTaskFamilyBudget - addedToday.toInt()
        return if (remaining <= 0) NewcomerGate.Blocked(SkipReason.BUDGET_EXHAUSTED)
        else NewcomerGate.Open(dailyBudget = remaining)
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun activateLemma(
        lemma: String,
        langCode: String,
        language: Language,
        translationTargets: List<Language>,
        now: Long,
    ): LemmaActivationOutcome {
        val group = learning.selectPendingActivationByLemma(langCode, lemma).executeAsList()
        if (group.isEmpty()) {
            AppLogger.debug(TAG, "Intake lemma skipped: no pending favorites lang=$langCode lemma=$lemma", null)
            return LemmaActivationOutcome.EMPTY
        }

        val skipped = mutableListOf<SkipReason>()
        val languageCard = dictionary.getLanguageCard(
            language = language,
            lemma = lemma,
            translationTargets = translationTargets,
            senseIds = group.mapTo(mutableSetOf()) { it.sense_id },
        )
        if (languageCard == null) {
            skipped += SkipReason.CARD_DATA_UNAVAILABLE
            AppLogger.debug(
                TAG,
                "Intake lemma skipped: card data unavailable lang=$langCode lemma=$lemma pendingSenses=${group.size}",
                null,
            )
            return LemmaActivationOutcome(0, emptyList(), skipped)
        }
        val sensesById = languageCard.entries
            .flatMap { it.senses }
            .associateBy { it.senseId }

        val groupPlan = mutableListOf<IntakePlanItem>()
        for (favorite in group) {
            val sense = sensesById[favorite.sense_id]
            if (sense == null) {
                skipped += SkipReason.CARD_DATA_UNAVAILABLE
                AppLogger.debug(
                    TAG,
                    "Intake sense skipped: sense missing in card data lang=$langCode lemma=$lemma sense=${favorite.sense_id}",
                    null,
                )
                continue
            }
            val tasksToCreate = config.defaultIntakeFamilies.mapNotNull { family ->
                val variants = buildTaskVariants(family, sense, translationTargets)
                if (variants.isEmpty()) null else family
            }
            if (tasksToCreate.isEmpty()) {
                skipped += SkipReason.NO_TASKS_VARIANT_AVAILABLE
                AppLogger.debug(
                    TAG,
                    "Intake sense skipped: no playable variants lang=$langCode lemma=$lemma sense=${favorite.sense_id}",
                    null,
                )
                continue
            }
            AppLogger.debug(
                TAG,
                "Intake sense planned lang=$langCode lemma=$lemma sense=${favorite.sense_id} " +
                    "families=${tasksToCreate.joinToString { it.name }}",
                null,
            )
            groupPlan += IntakePlanItem(Uuid.parse(favorite.sense_id), tasksToCreate)
        }

        if (groupPlan.isEmpty()) {
            AppLogger.debug(
                TAG,
                "Intake lemma skipped: no activatable senses lang=$langCode lemma=$lemma skipped=${skipSummary(skipped)}",
                null,
            )
            return LemmaActivationOutcome(0, emptyList(), skipped)
        }
        val groupTotal = groupPlan.sumOf { it.families.size }

        val lemmaId = JsonIngestionBuilder.generateLemmaId(lemma)
        val answerKey = lemma.lowercase()
        learning.transaction {
            groupPlan.forEach { item ->
                AppLogger.debug(
                    TAG,
                    "Intake activating favorite lang=$langCode lemma=$lemma sense=${item.senseId} " +
                        "newCards=${item.families.size} families=${item.families.joinToString { it.name }} " +
                        "state=${CardState.NEW} due=now availableAfter=none",
                    null,
                )
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
                AppLogger.debug(
                    TAG,
                    "Intake favorite activated lang=$langCode lemma=$lemma sense=${item.senseId} activatedAt=now",
                    null,
                )
            }
        }
        return LemmaActivationOutcome(
            cardsCreated = groupTotal,
            activatedSenses = groupPlan.map { it.senseId },
            skipped = skipped,
        )
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

private sealed interface NewcomerGate {
    data class Open(val dailyBudget: Int?) : NewcomerGate
    data class Blocked(val reason: SkipReason) : NewcomerGate
}

private data class LemmaActivationOutcome(
    val cardsCreated: Int,
    val activatedSenses: List<Uuid>,
    val skipped: List<SkipReason>,
) {
    companion object {
        val EMPTY = LemmaActivationOutcome(0, emptyList(), emptyList())
    }
}

enum class IntakeRunMode {
    DAILY,
    CONTINUE_NOW,
}

private fun IntakeRunMode.logName(): String =
    when (this) {
        IntakeRunMode.DAILY -> "daily"
        IntakeRunMode.CONTINUE_NOW -> "continue_now"
    }

private fun skipSummary(skipped: List<SkipReason>): String =
    if (skipped.isEmpty()) {
        "none"
    } else {
        skipped.groupingBy { it }
            .eachCount()
            .entries
            .sortedBy { it.key.name }
            .joinToString { (reason, count) -> "${reason.name}=$count" }
    }

private const val TAG: String = "IntakeService"
