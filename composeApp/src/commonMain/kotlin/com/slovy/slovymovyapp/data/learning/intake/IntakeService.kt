package com.slovy.slovymovyapp.data.learning.intake

import com.slovy.slovymovyapp.analytics.Analytics
import com.slovy.slovymovyapp.analytics.AnalyticsEvent
import com.slovy.slovymovyapp.analytics.PerformanceMonitoring
import com.slovy.slovymovyapp.analytics.putAttributes
import com.slovy.slovymovyapp.analytics.use
import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.learning.CardFamily
import com.slovy.slovymovyapp.data.learning.CardState
import com.slovy.slovymovyapp.data.learning.Rating
import com.slovy.slovymovyapp.data.learning.StudyFormatSelection
import com.slovy.slovymovyapp.data.learning.fsrs.FsrsConfig
import com.slovy.slovymovyapp.data.learning.fsrs.FsrsDefaults
import com.slovy.slovymovyapp.data.learning.fsrs.reentryScheduling
import com.slovy.slovymovyapp.data.learning.session.buildTaskVariants
import com.slovy.slovymovyapp.data.remote.DictionaryRepository
import com.slovy.slovymovyapp.data.remote.LanguageCard
import com.slovy.slovymovyapp.db.Card as DbCard
import com.slovy.slovymovyapp.db.FavoritesQueries
import com.slovy.slovymovyapp.ingestion.JsonIngestionBuilder
import com.slovy.slovymovyapp.logging.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    /** Whether pending words may start now, judged on the queue of [studiedFamilies]. */
    fun canContinueWithPendingFavoritesNow(langCode: String, studiedFamilies: List<CardFamily>): Boolean
}

class IntakeService(
    private val learning: FavoritesQueries,
    private val dictionary: DictionaryRepository,
    private val config: FsrsConfig = FsrsDefaults.config(),
    private val clock: Clock,
    /** The learner's formats for a language, given its already-resolved translation targets. */
    private val studyFormats: suspend (Language, List<Language>) -> StudyFormatSelection,
) : LearningIntake {
    @OptIn(ExperimentalTime::class)
    override suspend fun runIntake(langCode: String): IntakeResult =
        withContext(Dispatchers.IO) {
            runMutex.withLock { runAndLog(langCode, IntakeRunMode.DAILY) }
        }

    @OptIn(ExperimentalTime::class)
    override suspend fun continueWithPendingFavoritesNow(langCode: String): IntakeResult =
        withContext(Dispatchers.IO) {
            runMutex.withLock { runAndLog(langCode, IntakeRunMode.CONTINUE_NOW) }
        }

    /**
     * Intake runs one at a time: a session start and the review coordinator can both ask for
     * one, and two runs interleaving would race each other's re-entry inserts and cursor.
     */
    private val runMutex = Mutex()

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
            val language = Language.fromCodeOrNull(langCode)
            val translationTargets = language?.let { resolveTranslationTargets(it) }.orEmpty()
            val formats = language?.let { studyFormats(it, translationTargets) } ?: StudyFormatSelection.ALL
            val result = if (language == null) {
                AppLogger.debug(TAG, null) { "Intake skipped: unknown language lang=$langCode mode=${mode.logName()}" }
                IntakeResult(emptyList(), emptyList(), 0, 0)
            } else {
                activatePendingFavorites(langCode, language, translationTargets, mode, formats)
            }
            val studiedFamilies = formats.families
            AppLogger.debug(TAG, null) {
                "Intake completed lang=$langCode mode=${mode.logName()} cardsCreated=${result.cardsCreated} " +
                    "activated=${result.activated.size} rerouted=${result.rerouted} skipped=${skipSummary(result.skipped)}"
            }
            trace.putMetric("cards_created", result.cardsCreated.toLong())
            trace.putMetric("activated", result.activated.size.toLong())
            trace.putMetric("rerouted", result.rerouted.toLong())
            trace.putMetric("skipped", result.skipped.size.toLong())
            SkipReason.entries.forEach { reason ->
                val count = result.skipped.count { it == reason }.toLong()
                if (count > 0L) trace.putMetric("skip_${reason.name.lowercase()}", count)
            }
            Analytics.logEvent(
                AnalyticsEvent.LEARNING_INTAKE_RUN,
                buildIntakeAnalyticsParams(langCode, mode, result, studiedFamilies),
            )
            result
        }

    @OptIn(ExperimentalTime::class)
    private fun buildIntakeAnalyticsParams(
        langCode: String,
        mode: IntakeRunMode,
        result: IntakeResult,
        studiedFamilies: List<CardFamily>,
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
            "rerouted_count" to result.rerouted.toLong(),
            "active_card_count" to learning.countCardsByLang(langCode).executeAsOne(),
            "due_count" to learning.countDueCardsByLang(langCode, nowMs, studiedFamilies).executeAsOne(),
            "delayed_due_lemma_count" to learning.countDelayedDueLemmasByLang(langCode, nowMs, studiedFamilies).executeAsOne(),
            "delayed_due_card_count" to learning.countDelayedDueCardsByLang(langCode, nowMs, studiedFamilies).executeAsOne(),
            "pending_favorite_lemma_count" to learning.countPendingFavoriteLemmasByLang(langCode).executeAsOne(),
        )
        SkipReason.entries.forEach { reason ->
            params["skip_${reason.name.lowercase()}"] = result.skipped.count { it == reason }.toLong()
        }
        return params
    }

    @OptIn(ExperimentalTime::class)
    override fun canContinueWithPendingFavoritesNow(langCode: String, studiedFamilies: List<CardFamily>): Boolean =
        pendingFavoritePauseReason(langCode, clock.now(), studiedFamilies) == null

    private suspend fun resolveTranslationTargets(language: Language): List<Language> =
        dictionary.defaultTranslationTargets(language)
            .filter { it != language }
            .distinctBy { it.code }

    @OptIn(ExperimentalTime::class)
    private suspend fun activatePendingFavorites(
        langCode: String,
        language: Language,
        translationTargets: List<Language>,
        mode: IntakeRunMode,
        formats: StudyFormatSelection,
    ): IntakeResult {
        val intakeFamilies = formats.intakeFamilies(config.defaultIntakeFamilies)
        val nowInstant = clock.now()
        val now = nowInstant.toEpochMilliseconds()

        val timeZone = TimeZone.currentSystemDefault()
        val startOfToday = nowInstant.toLocalDateTime(timeZone).date
            .atStartOfDayIn(timeZone)
            .toEpochMilliseconds()

        val familiesPerSense = intakeFamilies.size
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
        AppLogger.debug(TAG, null) {
            "Intake selected pending lemmas lang=$langCode mode=${mode.logName()} " +
                "translationTargets=${translationTargets.joinToString { it.code }} " +
                "pendingLemmaLimit=$pendingLemmaLimit extensions=${extensions.size} newcomers=${newcomers.size}"
        }

        val activated = mutableListOf<Uuid>()
        val skipped = mutableListOf<SkipReason>()
        val invalidatedSenses = mutableSetOf<String>()
        var cardsCreated = 0
        var rerouted = 0

        suspend fun apply(lemma: String) {
            val outcome = activateLemma(lemma, langCode, language, translationTargets, formats, intakeFamilies, now)
            skipped += outcome.skipped
            invalidatedSenses += outcome.activatedSenses.map { it.toString() }
            activated += outcome.activatedSenses
            cardsCreated += outcome.cardsCreated
            AppLogger.debug(TAG, null) {
                "Intake lemma processed lang=$langCode mode=${mode.logName()} lemma=$lemma " +
                    "cardsCreated=${outcome.cardsCreated} activatedSenses=${outcome.activatedSenses.size} " +
                    "skipped=${skipSummary(outcome.skipped)}"
            }
        }

        for (row in extensions) apply(row.lemma)

        // Re-entry is daily work under the daily budget alone: "continue now" is the learner
        // asking for the pending words by name, and the pause rules protect them from new words,
        // not from the words they already know. Known words come back before new ones start,
        // so a toggle re-enters a large vocabulary over days rather than in one session.
        var remainingDailyBudget: Int? = null
        if (mode == IntakeRunMode.DAILY) {
            val dailyBudget = remainingDailyBudget(langCode, startOfToday)
            remainingDailyBudget = dailyBudget
            if (formats.excludedFamilies.isNotEmpty()) {
                val reroute = Rerouter(langCode, language, translationTargets, formats, now)
                val outcome = reroute.reenterStranded(dailyBudget)
                rerouted += outcome.inserted
                cardsCreated += outcome.inserted
                repeat(outcome.notPlayable) { skipped += SkipReason.NO_TASKS_VARIANT_AVAILABLE }
                repeat(outcome.dataUnavailable) { skipped += SkipReason.CARD_DATA_UNAVAILABLE }
                remainingDailyBudget = dailyBudget - outcome.inserted
            }
        }

        if (newcomers.isNotEmpty()) {
            when (val gate = newcomerGate(langCode, mode, nowInstant, remainingDailyBudget, formats.families)) {
                is NewcomerGate.Blocked -> {
                    skipped += gate.reason
                    AppLogger.debug(TAG, null) {
                        "Intake newcomers blocked lang=$langCode mode=${mode.logName()} reason=${gate.reason}"
                    }
                }

                is NewcomerGate.Open -> {
                    AppLogger.debug(TAG, null) {
                        "Intake newcomers open lang=$langCode mode=${mode.logName()} dailyBudget=${gate.dailyBudget ?: "none"}"
                    }
                    var activatedLemmaCount = 0
                    for (row in newcomers) {
                        if (mode == IntakeRunMode.CONTINUE_NOW &&
                            activatedLemmaCount >= config.continueNowPendingLemmaLimit
                        ) {
                            AppLogger.debug(TAG, null) {
                                "Intake continue-now lemma limit reached lang=$langCode limit=${config.continueNowPendingLemmaLimit}"
                            }
                            break
                        }
                        val upperBoundCards = row.pending_count.toInt() * familiesPerSense
                        val budget = remainingDailyBudget
                        if (budget != null && upperBoundCards > budget) {
                            skipped += SkipReason.BUDGET_EXHAUSTED
                            AppLogger.debug(TAG, null) {
                                "Intake lemma skipped by budget lang=$langCode lemma=${row.lemma} " +
                                    "upperBoundCards=$upperBoundCards remainingBudget=$budget"
                            }
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
            AppLogger.debug(TAG, null) {
                "Intake invalidated dictionary senses lang=$langCode count=${invalidatedSenses.size}"
            }
        }
        return IntakeResult(activated, skipped, cardsCreated, rerouted)
    }

    /**
     * Where the last run's walk through the stranded senses stopped, per language and
     * selection, so the next run goes on from there instead of re-reading the same first pages;
     * a vocabulary whose first pages cannot be re-entered is still walked to its end.
     */
    private val rerouteCursors = mutableMapOf<String, Uuid>()

    /**
     * Re-enters stranded senses, whose cards all belong to families the learner switched off,
     * one at a time in the first studied family that can play it: such a word could never be shown again,
     * and the ladder, which only runs on a review, could never bring it back. The new card
     * inherits from the strongest one the way an unlock would, so what the word already earned
     * carries over; the cards it has keep their scheduling untouched. Word data is fetched once
     * per lemma and run.
     */
    private inner class Rerouter(
        private val langCode: String,
        private val language: Language,
        private val translationTargets: List<Language>,
        private val formats: StudyFormatSelection,
        private val now: Long,
    ) {
        private val cardsByLemma = mutableMapOf<String, LanguageCard?>()
        private val key = "$langCode|${formats.enabled.sorted()}|${translationTargets.map { it.code }}"

        /**
         * Walks the stranded senses a page at a time, keyed on the last sense seen so the rows
         * it inserts along the way cannot shift the next page, until [budget] is spent or the
         * pages run out. It starts where the previous run stopped and wraps once, so senses that
         * cannot be re-entered never hold the ones behind them back, however many there are.
         */
        suspend fun reenterStranded(budget: Int): RerouteSummary {
            var inserted = 0
            var notPlayable = 0
            var dataUnavailable = 0
            var scanned = 0
            val startedAt = rerouteCursors[key] ?: Uuid.NIL
            var after = startedAt
            var wrapped = startedAt == Uuid.NIL
            val pageSize = config.dailyNewTaskFamilyBudget.toLong()
            for (page in 0 until MAX_REROUTE_PAGES) {
                if (inserted >= budget) break
                val rows = learning.selectStrongestCardOfSensesWithoutFamilies(
                    lang_code = langCode,
                    allowed_families = formats.families,
                    after_sense_id = after,
                    limit = pageSize,
                ).executeAsList()
                if (rows.isEmpty()) {
                    if (wrapped) break
                    wrapped = true
                    after = Uuid.NIL
                    continue
                }
                after = rows.last().sense_id
                scanned += rows.size
                for (source in rows) {
                    if (inserted >= budget) break
                    when (reenter(source)) {
                        RerouteOutcome.INSERTED -> inserted += 1
                        RerouteOutcome.NOT_PLAYABLE -> notPlayable += 1
                        RerouteOutcome.DATA_UNAVAILABLE -> dataUnavailable += 1
                        RerouteOutcome.NOTHING_TO_DO -> Unit
                    }
                }
            }
            rerouteCursors[key] = after
            AppLogger.debug(TAG, null) {
                "Intake reroute done lang=$langCode inserted=$inserted notPlayable=$notPlayable " +
                    "dataUnavailable=$dataUnavailable scanned=$scanned"
            }
            return RerouteSummary(inserted, notPlayable, dataUnavailable)
        }

        private suspend fun reenter(source: DbCard): RerouteOutcome {
            val senseId = source.sense_id.toString()
            val favorite = learning.selectFavoriteWithActivation(senseId, langCode).executeAsOneOrNull()
            if (favorite == null) {
                AppLogger.debug(TAG, null) { "Intake reroute skipped: favorite missing lang=$langCode sense=$senseId" }
                return RerouteOutcome.NOTHING_TO_DO
            }
            // Fetched once per lemma, a missing card included, so a lemma without data is not
            // fetched again for each of its senses.
            if (favorite.lemma !in cardsByLemma) {
                cardsByLemma[favorite.lemma] = dictionary.getLanguageCard(
                    language = language,
                    lemma = favorite.lemma,
                    translationTargets = translationTargets,
                    senseIds = null,
                )
            }
            val sense = cardsByLemma[favorite.lemma]?.entries?.flatMap { it.senses }?.firstOrNull { it.senseId == senseId }
            if (sense == null) {
                // Not remembered: the data may arrive with a download or a recovery pass, and
                // the next run must look again, as activation does.
                AppLogger.debug(TAG, null) {
                    "Intake reroute skipped: card data unavailable lang=$langCode sense=$senseId"
                }
                return RerouteOutcome.DATA_UNAVAILABLE
            }
            // The first studied family that can play this sense; a sense that none can play is
            // not remembered across runs, since the data it lacks may arrive with a fetch or a
            // download, and the run after that must look again.
            val target = formats.families.firstOrNull { family ->
                buildTaskVariants(family, sense, translationTargets, formats).isNotEmpty()
            }
            if (target == null) {
                AppLogger.debug(TAG, null) {
                    "Intake reroute skipped: no playable variant lang=$langCode sense=$senseId families=${formats.families}"
                }
                return RerouteOutcome.NOT_PLAYABLE
            }
            val scheduling = config.reentryScheduling(
                sourceFamily = source.family,
                sourceState = source.state,
                sourceStability = source.stability,
                sourceDifficulty = source.difficulty,
                targetFamily = target,
            )
            val due = now + scheduling.delay.inWholeMilliseconds
            AppLogger.debug(TAG, null) {
                "Intake rerouting sense lang=$langCode sense=$senseId from=${source.family} " +
                    "to=$target inheritedStability=${scheduling.stability} dueIn=${scheduling.delay}"
            }
            // The query already leaves out senses with any card in a studied family; the check
            // inside the transaction is for a card another writer inserted since that read.
            return learning.transactionWithResult {
                val existing = learning.selectCardBySenseAndFamily(source.sense_id, langCode, target).executeAsOneOrNull()
                if (existing != null) {
                    AppLogger.debug(TAG, null) {
                        "Intake reroute skipped: card exists lang=$langCode sense=$senseId family=$target card=${existing.id}"
                    }
                    return@transactionWithResult RerouteOutcome.NOTHING_TO_DO
                }
                learning.insertCard(
                    id = Uuid.random(),
                    sense_id = source.sense_id,
                    lemma_id = source.lemma_id,
                    lang_code = langCode,
                    family = target,
                    state = scheduling.state,
                    stability = scheduling.stability,
                    difficulty = scheduling.difficulty,
                    due = due,
                    last_review = null,
                    reps = 0,
                    lapses = 0,
                    created_at = now,
                    // A paused word stays paused in its new family.
                    available_after = source.available_after,
                    answer_key = source.answer_key,
                    suspended = false,
                )
                RerouteOutcome.INSERTED
            }
        }
    }

    private enum class RerouteOutcome {
        INSERTED,
        NOT_PLAYABLE,
        DATA_UNAVAILABLE,
        NOTHING_TO_DO,
    }

    private data class RerouteSummary(val inserted: Int, val notPlayable: Int, val dataUnavailable: Int)

    @OptIn(ExperimentalTime::class)
    private fun newcomerGate(
        langCode: String,
        mode: IntakeRunMode,
        nowInstant: Instant,
        remainingDailyBudget: Int?,
        studiedFamilies: List<CardFamily>,
    ): NewcomerGate {
        val pauseReason = pendingFavoritePauseReason(langCode, nowInstant, studiedFamilies)
        if (pauseReason != null) return NewcomerGate.Blocked(pauseReason)
        if (mode != IntakeRunMode.DAILY) return NewcomerGate.Open(dailyBudget = null)
        return if (remainingDailyBudget == null || remainingDailyBudget <= 0) NewcomerGate.Blocked(SkipReason.BUDGET_EXHAUSTED)
        else NewcomerGate.Open(dailyBudget = remainingDailyBudget)
    }

    private fun remainingDailyBudget(langCode: String, startOfToday: Long): Int {
        val addedToday = learning.countNewCardsCreatedSince(langCode, startOfToday).executeAsOne()
        return config.dailyNewTaskFamilyBudget - addedToday.toInt()
    }


    @OptIn(ExperimentalTime::class)
    private suspend fun activateLemma(
        lemma: String,
        langCode: String,
        language: Language,
        translationTargets: List<Language>,
        formats: StudyFormatSelection,
        intakeFamilies: List<CardFamily>,
        now: Long,
    ): LemmaActivationOutcome {
        val group = learning.selectPendingActivationByLemma(langCode, lemma).executeAsList()
        if (group.isEmpty()) {
            AppLogger.debug(TAG, null) {
                "Intake lemma skipped: no pending favorites lang=$langCode lemma=$lemma"
            }
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
            AppLogger.debug(TAG, null) {
                "Intake lemma skipped: card data unavailable lang=$langCode lemma=$lemma pendingSenses=${group.size}"
            }
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
                AppLogger.debug(TAG, null) {
                    "Intake sense skipped: sense missing in card data lang=$langCode lemma=$lemma sense=${favorite.sense_id}"
                }
                continue
            }
            val tasksToCreate = intakeFamilies.mapNotNull { family ->
                val variants = buildTaskVariants(family, sense, translationTargets, formats)
                if (variants.isEmpty()) null else family
            }
            if (tasksToCreate.isEmpty()) {
                skipped += SkipReason.NO_TASKS_VARIANT_AVAILABLE
                AppLogger.debug(TAG, null) {
                    "Intake sense skipped: no playable variants lang=$langCode lemma=$lemma sense=${favorite.sense_id}"
                }
                continue
            }
            AppLogger.debug(TAG, null) {
                "Intake sense planned lang=$langCode lemma=$lemma sense=${favorite.sense_id} " +
                    "families=${tasksToCreate.joinToString { it.name }}"
            }
            groupPlan += IntakePlanItem(Uuid.parse(favorite.sense_id), tasksToCreate)
        }

        if (groupPlan.isEmpty()) {
            AppLogger.debug(TAG, null) {
                "Intake lemma skipped: no activatable senses lang=$langCode lemma=$lemma skipped=${skipSummary(skipped)}"
            }
            return LemmaActivationOutcome(0, emptyList(), skipped)
        }
        val groupTotal = groupPlan.sumOf { it.families.size }

        val lemmaId = JsonIngestionBuilder.generateLemmaId(lemma)
        val answerKey = lemma.lowercase()
        learning.transaction {
            groupPlan.forEach { item ->
                AppLogger.debug(TAG, null) {
                    "Intake activating favorite lang=$langCode lemma=$lemma sense=${item.senseId} " +
                        "newCards=${item.families.size} families=${item.families.joinToString { it.name }} " +
                        "state=${CardState.NEW} due=now availableAfter=none"
                }
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
                AppLogger.debug(TAG, null) {
                    "Intake favorite activated lang=$langCode lemma=$lemma sense=${item.senseId} activatedAt=now"
                }
            }
        }
        return LemmaActivationOutcome(
            cardsCreated = groupTotal,
            activatedSenses = groupPlan.map { it.senseId },
            skipped = skipped,
        )
    }

    @OptIn(ExperimentalTime::class)
    private fun pendingFavoritePauseReason(
        langCode: String,
        now: Instant,
        studiedFamilies: List<CardFamily>,
    ): SkipReason? {
        val nowMs = now.toEpochMilliseconds()
        // Only the queue the learner can work through counts: a switched-off family's cards
        // stay due forever and must not pause intake for good.
        val due = learning.countDueCardsByLang(langCode, nowMs, studiedFamilies).executeAsOne()
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
    /** Every card this run inserted, the rerouted ones included. */
    val cardsCreated: Int,
    /** Senses re-entered in a studied family because all their cards were in switched-off ones. */
    val rerouted: Int,
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
private const val MAX_REROUTE_PAGES: Int = 25
