package com.slovy.slovymovyapp.data.learning.intake

import com.slovy.slovymovyapp.data.Language
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
        if (reviewCount >= 50L) {
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

        val candidates = learning.selectFavoritesPendingActivation(langCode, (remaining * 10).toLong()).executeAsList()
        val activated = mutableListOf<Uuid>()
        val skipped = mutableListOf<SkipReason>()
        val invalidatedSenses = mutableSetOf<String>()
        var cardsCreated = 0

        for (favorite in candidates) {
            val senseId = Uuid.parse(favorite.sense_id)
            val languageCard = dictionary.getLanguageCard(
                language = language,
                lemma = favorite.lemma,
                translationTargets = translationTargets,
                senseIds = setOf(senseId.toString()),
            )
            if (languageCard == null) {
                skipped += SkipReason.CARD_DATA_UNAVAILABLE
                continue
            }
            val sense = languageCard.entries
                .flatMap { it.senses }
                .firstOrNull { it.senseId == senseId.toString() }
            if (sense == null) {
                skipped += SkipReason.CARD_DATA_UNAVAILABLE
                continue
            }
            val tasksToCreate = config.defaultIntakeFamilies.mapNotNull { family ->
                val variants = buildTaskVariants(family, sense, translationTargets)
                if (variants.isEmpty()) null else family
            }
            if (tasksToCreate.isEmpty()) {
                skipped += SkipReason.NO_EXAMPLES
                continue
            }
            if (tasksToCreate.size > remaining) {
                skipped += SkipReason.BUDGET_EXHAUSTED
                continue
            }

            learning.transaction {
                val lemmaId = JsonIngestionBuilder.generateLemmaId(favorite.lemma)
                val answerKey = favorite.lemma.lowercase()
                tasksToCreate.forEach { family ->
                    val cardId = Uuid.random()
                    learning.insertCard(
                        id = cardId,
                        sense_id = senseId,
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
                learning.markFavoriteActivated(now, favorite.sense_id, favorite.lang_code)
            }
            invalidatedSenses += senseId.toString()
            activated += senseId
            cardsCreated += tasksToCreate.size
            remaining -= tasksToCreate.size
        }

        if (invalidatedSenses.isNotEmpty()) {
            dictionary.invalidateSenses(invalidatedSenses)
        }
        if (cardsCreated == 0 && skipped.isEmpty() && candidates.isNotEmpty() && remaining <= 0) {
            skipped += SkipReason.BUDGET_EXHAUSTED
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
    NO_EXAMPLES,
    CARD_DATA_UNAVAILABLE,
}
