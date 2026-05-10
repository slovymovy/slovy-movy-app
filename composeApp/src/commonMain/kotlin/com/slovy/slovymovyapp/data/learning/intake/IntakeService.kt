package com.slovy.slovymovyapp.data.learning.intake

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
import kotlin.uuid.Uuid

interface LearningIntake {
    suspend fun runIntake(langCode: String): IntakeResult
}

class IntakeService(
    private val learning: FavoritesQueries,
    private val dictionary: DictionaryRepository,
    private val config: FsrsConfig = FsrsDefaults.config(),
    private val clock: Clock,
) : LearningIntake {
    @OptIn(ExperimentalTime::class)
    override suspend fun runIntake(langCode: String): IntakeResult = withContext(Dispatchers.IO) {
        val language = Language.fromCodeOrNull(langCode)
            ?: return@withContext IntakeResult(emptyList(), emptyList(), 0)
        val translationTargets = dictionary.defaultTranslationTargets(language)
            .filter { it != language }
            .distinctBy { it.code }
        val nowInstant = clock.now()
        val now = nowInstant.toEpochMilliseconds()

        val due = learning.countDueCardsByLang(langCode, now).executeAsOne()
        if (due > config.pauseIntakeIfQueueAbove) {
            return@withContext IntakeResult(emptyList(), listOf(SkipReason.QUEUE_TOO_FULL), 0)
        }

        val weekAgo = (nowInstant - 7.days).toEpochMilliseconds()
        val reviewCount = learning.countReviewsSince(langCode, weekAgo).executeAsOne()
        if (reviewCount >= config.pauseIntakeRetentionMinReviews) {
            val successful = learning.countSuccessfulReviewsSince(langCode, weekAgo).executeAsOne()
            val retention = successful.toDouble() / reviewCount
            if (retention < config.pauseIntakeIfRetentionBelow) {
                return@withContext IntakeResult(emptyList(), listOf(SkipReason.RETENTION_TOO_LOW), 0)
            }
        }

        val timeZone = TimeZone.currentSystemDefault()
        val startOfToday = nowInstant.toLocalDateTime(timeZone).date
            .atStartOfDayIn(timeZone)
            .toEpochMilliseconds()
        val addedToday = learning.countNewCardsCreatedSince(langCode, startOfToday).executeAsOne()
        var remaining = config.dailyNewTaskFamilyBudget - addedToday.toInt()
        if (remaining <= 0) {
            return@withContext IntakeResult(emptyList(), listOf(SkipReason.BUDGET_EXHAUSTED), 0)
        }

        val familiesPerSense = config.defaultIntakeFamilies.size
        val pendingLemmas = learning
            .selectPendingActivationLemmas(langCode, (remaining * 10).toLong())
            .executeAsList()
        val activated = mutableListOf<Uuid>()
        val skipped = mutableListOf<SkipReason>()
        val invalidatedSenses = mutableSetOf<String>()
        var cardsCreated = 0

        for (row in pendingLemmas) {
            val upperBoundCards = row.pending_count.toInt() * familiesPerSense
            if (upperBoundCards > remaining) {
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
            remaining -= groupTotal
        }

        if (invalidatedSenses.isNotEmpty()) {
            dictionary.invalidateSenses(invalidatedSenses)
        }
        IntakeResult(activated, skipped, cardsCreated)
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
