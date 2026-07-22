package com.slovy.slovymovyapp.data.favorites

import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.learning.CardFamily
import com.slovy.slovymovyapp.data.learning.CardState
import com.slovy.slovymovyapp.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.uuid.Uuid

data class CardScheduleDebugStats(
    val futureScheduledCards: Long,
    val futureScheduledLemmas: Long,
    val availableAfterSuppressedCards: Long,
    val availableAfterSuppressedLemmas: Long,
)

data class CardTableDebugRow(
    val lemma: String?,
    val id: Uuid,
    val senseId: Uuid,
    val lemmaId: Uuid,
    val langCode: String,
    val family: CardFamily,
    val state: CardState,
    val stability: Double,
    val difficulty: Double,
    val due: Long,
    val lastReview: Long?,
    val reps: Long,
    val lapses: Long,
    val createdAt: Long,
    val availableAfter: Long?,
    val answerKey: String,
    val suspended: Boolean,
)

data class CardFamilyDebugCount(
    val family: CardFamily,
    val cardCount: Long,
)

/** Scheduling data of one active (non-suspended) card, used for word-list export. */
data class SenseCardExportRow(
    val senseId: String,
    val state: CardState,
    val stability: Double,
    val due: Long,
    val lastReview: Long?,
    val reps: Long,
    val lapses: Long,
)

data class NewFavorite(
    val senseId: String,
    val lemma: String,
)

class FavoritesRepository(private val db: AppDatabase) {

    private val distinctLemmasByLangCache = mutableMapOf<Language, Set<String>>()
    private val distinctLemmaCacheMutex = Mutex()

    companion object {
        fun normalizeLemma(lemma: String): String = lemma.trim().lowercase()
    }

    @OptIn(ExperimentalTime::class)
    suspend fun add(senseId: String, language: Language, lemma: String) = withContext(Dispatchers.IO) {
        addFavorite(
            senseId = senseId,
            language = language,
            lemma = lemma,
            createdAt = Clock.System.now().toEpochMilliseconds(),
        )
    }

    @OptIn(ExperimentalTime::class)
    suspend fun add(senseId: String, language: Language, lemma: String, createdAt: Long) =
        withContext(Dispatchers.IO) {
            addFavorite(
                senseId = senseId,
                language = language,
                lemma = lemma,
                createdAt = createdAt,
            )
        }

    /** Adds every favorite from [items] that is not stored yet, in a single transaction. */
    @OptIn(ExperimentalTime::class)
    suspend fun addAll(language: Language, items: List<NewFavorite>) = withContext(Dispatchers.IO) {
        if (items.isEmpty()) return@withContext
        val now = Clock.System.now().toEpochMilliseconds()
        db.favoritesQueries.transaction {
            items.forEach { item ->
                insertFavoriteIfMissing(
                    senseId = item.senseId,
                    language = language,
                    lemma = item.lemma,
                    createdAt = now,
                    activationTimestamp = now,
                )
            }
        }
        invalidateDistinctLemmaCache(language)
    }

    /** Removes every favorite from [senseIds] in a single transaction, suspending its learning cards. */
    suspend fun removeAll(senseIds: List<String>, language: Language) = withContext(Dispatchers.IO) {
        if (senseIds.isEmpty()) return@withContext
        db.favoritesQueries.transaction {
            senseIds.forEach { senseId ->
                val existing = db.favoritesQueries.selectFavoriteWithActivation(
                    sense_id = senseId,
                    lang_code = language.code,
                ).executeAsOneOrNull()
                if (existing != null) {
                    db.favoritesQueries.suspendCardsByFavorite(
                        sense_id = Uuid.parse(senseId),
                        lang_code = language.code,
                    )
                    db.favoritesQueries.deleteFavorite(
                        sense_id = senseId,
                        lang_code = language.code,
                    )
                }
            }
        }
        invalidateDistinctLemmaCache(language)
    }

    @OptIn(ExperimentalTime::class)
    suspend fun restoreForUndo(snapshot: RemovedFavoriteSnapshot) = withContext(Dispatchers.IO) {
        val senseUuid = Uuid.parse(snapshot.senseId)
        val restoredAt = Clock.System.now().toEpochMilliseconds()
        db.favoritesQueries.transaction {
            val existing = db.favoritesQueries.selectFavoriteWithActivation(
                sense_id = snapshot.senseId,
                lang_code = snapshot.language.code,
            ).executeAsOneOrNull()
            if (existing != null) return@transaction
            val hasLearningCards = db.favoritesQueries.countCardsByFavorite(
                sense_id = senseUuid,
                lang_code = snapshot.language.code,
            ).executeAsOne() > 0
            db.favoritesQueries.insertFavorite(
                sense_id = snapshot.senseId,
                lang_code = snapshot.language.code,
                lemma = snapshot.lemma,
                created_at = snapshot.createdAt,
                activated_at = if (hasLearningCards) restoredAt else null,
            )
            db.favoritesQueries.applyCardScheduleSnapshots(snapshot.cards)
        }
        invalidateDistinctLemmaCache(snapshot.language)
    }

    suspend fun remove(senseId: String, language: Language): RemovedFavoriteSnapshot? =
        withContext(Dispatchers.IO) {
            val senseUuid = Uuid.parse(senseId)
            db.favoritesQueries.transactionWithResult {
                val favorite = db.favoritesQueries.selectFavoriteWithActivation(
                    sense_id = senseId,
                    lang_code = language.code,
                ).executeAsOneOrNull() ?: return@transactionWithResult null
                val cards = db.favoritesQueries.selectCardsByFavorite(
                    sense_id = senseUuid,
                    lang_code = language.code,
                ).executeAsList().map {
                    CardScheduleSnapshot(
                        id = it.id,
                        suspended = it.suspended,
                        availableAfter = it.available_after,
                    )
                }
                db.favoritesQueries.suspendCardsByFavorite(
                    sense_id = senseUuid,
                    lang_code = language.code,
                )
                db.favoritesQueries.deleteFavorite(
                    sense_id = senseId,
                    lang_code = language.code,
                )
                RemovedFavoriteSnapshot(
                    senseId = senseId,
                    language = language,
                    lemma = favorite.lemma,
                    createdAt = favorite.created_at,
                    cards = cards,
                )
            }.also { snapshot ->
                if (snapshot != null) invalidateDistinctLemmaCache(language)
            }
        }

    suspend fun getAll(): List<Favorite> = withContext(Dispatchers.IO) {
        db.favoritesQueries.selectAll().executeAsList().map { row ->
            Favorite(
                senseId = row.sense_id,
                language = Language.fromCode(row.lang_code),
                lemma = row.lemma,
                createdAt = row.created_at
            )
        }
    }

    suspend fun getByLangAndLemma(language: Language, lemma: String): List<Favorite> =
        withContext(Dispatchers.IO) {
            db.favoritesQueries.selectByLangAndLemma(lang_code = language.code, lemma = lemma)
                .executeAsList()
                .map { row ->
                    Favorite(
                        senseId = row.sense_id,
                        language = Language.fromCode(row.lang_code),
                        lemma = row.lemma,
                        createdAt = row.created_at
                    )
                }
        }

    suspend fun getAllGroupedByLangAndLemma(): List<Favorite> = withContext(Dispatchers.IO) {
        db.favoritesQueries.selectAllOrderedByLangAndLemma().executeAsList().map { row ->
            Favorite(
                senseId = row.sense_id,
                language = Language.fromCode(row.lang_code),
                lemma = row.lemma,
                createdAt = row.created_at
            )
        }
    }

    suspend fun searchByLemma(query: String): List<Favorite> = withContext(Dispatchers.IO) {
        val pattern = "%$query%"
        db.favoritesQueries.selectByLemmaSearch(pattern).executeAsList().map { row ->
            Favorite(
                senseId = row.sense_id,
                language = Language.fromCode(row.lang_code),
                lemma = row.lemma,
                createdAt = row.created_at
            )
        }
    }

    suspend fun exists(senseId: String, language: Language): Boolean = withContext(Dispatchers.IO) {
        db.favoritesQueries.countBySenseIdAndLang(sense_id = senseId, lang_code = language.code)
            .executeAsOne() > 0
    }

    suspend fun getOne(senseId: String, language: Language): Favorite? = withContext(Dispatchers.IO) {
        db.favoritesQueries.selectFavoriteSummary(sense_id = senseId, lang_code = language.code)
            .executeAsOneOrNull()
            ?.let { row ->
                Favorite(
                    senseId = row.sense_id,
                    language = Language.fromCode(row.lang_code),
                    lemma = row.lemma,
                    createdAt = row.created_at
                )
            }
    }

    /** Distinct favorite lemmas for [language], keyed by [normalizeLemma]. Cached until favorites change. */
    suspend fun getFavoriteLemmas(language: Language): Set<String> = withContext(Dispatchers.IO) {
        distinctLemmaCacheMutex.withLock {
            distinctLemmasByLangCache.getOrPut(language) {
                db.favoritesQueries.selectDistinctLemmasByLang(lang_code = language.code)
                    .executeAsList()
                    .mapTo(HashSet()) { normalizeLemma(it) }
            }
        }
    }

    suspend fun getCardScheduleDebugStats(language: Language, nowEpochMs: Long): CardScheduleDebugStats =
        withContext(Dispatchers.IO) {
            CardScheduleDebugStats(
                futureScheduledCards = db.favoritesQueries.countFutureScheduledCardsByLang(
                    lang_code = language.code,
                    now = nowEpochMs,
                ).executeAsOne(),
                futureScheduledLemmas = db.favoritesQueries.countFutureScheduledLemmasByLang(
                    lang_code = language.code,
                    now = nowEpochMs,
                ).executeAsOne(),
                availableAfterSuppressedCards = db.favoritesQueries.countDelayedDueCardsByLang(
                    lang_code = language.code,
                    now = nowEpochMs,
                ).executeAsOne(),
                availableAfterSuppressedLemmas = db.favoritesQueries.countDelayedDueLemmasByLang(
                    lang_code = language.code,
                    now = nowEpochMs,
                ).executeAsOne(),
            )
        }

    suspend fun countCardTableDebugRows(): Long = withContext(Dispatchers.IO) {
        db.favoritesQueries.countDeveloperCardTableRows().executeAsOne()
    }

    suspend fun getCardTableDebugRows(pageSize: Long, pageOffset: Long): List<CardTableDebugRow> =
        withContext(Dispatchers.IO) {
            require(pageSize > 0) { "pageSize must be positive" }
            require(pageOffset >= 0) { "pageOffset must be non-negative" }
            db.favoritesQueries.selectDeveloperCardTable(
                page_size = pageSize,
                page_offset = pageOffset,
            ).executeAsList().map { row ->
                CardTableDebugRow(
                    lemma = row.lemma,
                    id = row.id,
                    senseId = row.sense_id,
                    lemmaId = row.lemma_id,
                    langCode = row.lang_code,
                    family = row.family,
                    state = row.state,
                    stability = row.stability,
                    difficulty = row.difficulty,
                    due = row.due,
                    lastReview = row.last_review,
                    reps = row.reps,
                    lapses = row.lapses,
                    createdAt = row.created_at,
                    availableAfter = row.available_after,
                    answerKey = row.answer_key,
                    suspended = row.suspended,
                )
            }
        }

    /** Active-card scheduling rows for [language], grouped by canonical sense-ID string. */
    suspend fun getCardExportRowsBySense(language: Language): Map<String, List<SenseCardExportRow>> =
        withContext(Dispatchers.IO) {
            db.favoritesQueries.selectCardExportRowsByLang(lang_code = language.code)
                .executeAsList()
                .map { row ->
                    SenseCardExportRow(
                        senseId = row.sense_id.toString(),
                        state = row.state,
                        stability = row.stability,
                        due = row.due,
                        lastReview = row.last_review,
                        reps = row.reps,
                        lapses = row.lapses,
                    )
                }
                .groupBy { it.senseId }
        }

    suspend fun getCardFamilyDebugCounts(): List<CardFamilyDebugCount> = withContext(Dispatchers.IO) {
        db.favoritesQueries.countCardsByFamily().executeAsList().map { row ->
            CardFamilyDebugCount(
                family = row.family,
                cardCount = row.card_count,
            )
        }
    }

    suspend fun removeSuspendedLearningCards(): Long = withContext(Dispatchers.IO) {
        val cardCount = db.favoritesQueries.countSuspendedCards().executeAsOne()
        db.favoritesQueries.transaction {
            db.favoritesQueries.deleteReviewLogsForSuspendedCards()
            db.favoritesQueries.deleteSuspendedCards()
        }
        cardCount
    }

    suspend fun removeAllLearningCards(): Long = withContext(Dispatchers.IO) {
        val cardCount = db.favoritesQueries.countAllCards().executeAsOne()
        db.favoritesQueries.transaction {
            db.favoritesQueries.deleteAllLearning()
            db.favoritesQueries.deleteAllCards()
            db.favoritesQueries.resetFavoriteActivations()
        }
        cardCount
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        db.favoritesQueries.transaction {
            db.favoritesQueries.suspendAllCards()
            db.favoritesQueries.deleteAll()
        }
        distinctLemmaCacheMutex.withLock {
            distinctLemmasByLangCache.clear()
        }
    }

    // Simulates `shift` of elapsed wall-clock time by moving every stored learning timestamp
    // backwards by that amount. Tuning/debug only — has no callers in production paths.
    suspend fun shiftLearningTimestampsBack(shift: Duration) = withContext(Dispatchers.IO) {
        require(shift.isPositive()) { "shift must be positive" }
        val shiftMs = shift.inWholeMilliseconds
        db.favoritesQueries.transaction {
            db.favoritesQueries.shiftFavoritesCreatedAtBack(shiftMs)
            db.favoritesQueries.shiftFavoritesActivatedAtBack(shiftMs)
            db.favoritesQueries.shiftCardTimestampsBack(shiftMs)
            db.favoritesQueries.shiftReviewLogReviewedAtBack(shiftMs)
        }
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun addFavorite(
        senseId: String,
        language: Language,
        lemma: String,
        createdAt: Long,
    ) {
        val restoredAt = Clock.System.now().toEpochMilliseconds()
        db.favoritesQueries.transaction {
            insertFavoriteIfMissing(
                senseId = senseId,
                language = language,
                lemma = lemma,
                createdAt = createdAt,
                activationTimestamp = restoredAt,
            )
        }
        invalidateDistinctLemmaCache(language)
    }

    /** Per-item insert logic shared by [addFavorite] and [addAll]; must run inside a transaction. */
    private fun insertFavoriteIfMissing(
        senseId: String,
        language: Language,
        lemma: String,
        createdAt: Long,
        activationTimestamp: Long,
    ) {
        val senseUuid = Uuid.parse(senseId)
        val existing = db.favoritesQueries.selectFavoriteWithActivation(
            sense_id = senseId,
            lang_code = language.code,
        ).executeAsOneOrNull()
        if (existing != null) return
        val hasLearningCards = db.favoritesQueries.countCardsByFavorite(
            sense_id = senseUuid,
            lang_code = language.code,
        ).executeAsOne() > 0
        db.favoritesQueries.insertFavorite(
            sense_id = senseId,
            lang_code = language.code,
            lemma = lemma,
            created_at = createdAt,
            activated_at = if (hasLearningCards) activationTimestamp else null,
        )
        if (hasLearningCards) {
            db.favoritesQueries.unsuspendCardsByFavorite(
                sense_id = senseUuid,
                lang_code = language.code,
            )
        }
    }

    private suspend fun invalidateDistinctLemmaCache(language: Language) {
        distinctLemmaCacheMutex.withLock {
            distinctLemmasByLangCache.remove(language)
        }
    }
}
