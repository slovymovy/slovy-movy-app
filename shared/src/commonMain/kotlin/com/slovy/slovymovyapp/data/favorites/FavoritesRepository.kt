package com.slovy.slovymovyapp.data.favorites

import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.uuid.Uuid

class FavoritesRepository(private val db: AppDatabase) {

    @OptIn(ExperimentalTime::class)
    suspend fun add(senseId: String, language: Language, lemma: String) = withContext(Dispatchers.IO) {
        addFavorite(
            senseId = senseId,
            language = language,
            lemma = lemma,
            createdAt = Clock.System.now().toEpochMilliseconds(),
            restoreImmediately = false,
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
                restoreImmediately = false,
            )
        }

    suspend fun restoreForUndo(senseId: String, language: Language, lemma: String, createdAt: Long) =
        withContext(Dispatchers.IO) {
            addFavorite(
                senseId = senseId,
                language = language,
                lemma = lemma,
                createdAt = createdAt,
                restoreImmediately = true,
            )
        }

    suspend fun remove(senseId: String, language: Language) = withContext(Dispatchers.IO) {
        db.favoritesQueries.transaction {
            db.favoritesQueries.suspendCardsByFavorite(
                sense_id = Uuid.parse(senseId),
                lang_code = language.code,
            )
            db.favoritesQueries.deleteFavorite(
                sense_id = senseId,
                lang_code = language.code
            )
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

    suspend fun getDistinctLemmasByLang(language: Language): Set<String> = withContext(Dispatchers.IO) {
        db.favoritesQueries.selectDistinctLemmasByLang(lang_code = language.code)
            .executeAsList()
            .toSet()
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        db.favoritesQueries.transaction {
            db.favoritesQueries.suspendAllCards()
            db.favoritesQueries.deleteAll()
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
    private fun addFavorite(
        senseId: String,
        language: Language,
        lemma: String,
        createdAt: Long,
        restoreImmediately: Boolean,
    ) {
        val senseUuid = Uuid.parse(senseId)
        val restoredAt = Clock.System.now().toEpochMilliseconds()
        db.favoritesQueries.transaction {
            val existing = db.favoritesQueries.selectFavoriteWithActivation(
                sense_id = senseId,
                lang_code = language.code,
            ).executeAsOneOrNull()
            if (existing != null) return@transaction
            val hasLearningCards = db.favoritesQueries.countCardsByFavorite(
                sense_id = senseUuid,
                lang_code = language.code,
            ).executeAsOne() > 0
            db.favoritesQueries.insertFavorite(
                sense_id = senseId,
                lang_code = language.code,
                lemma = lemma,
                created_at = createdAt,
                activated_at = if (hasLearningCards) restoredAt else null,
            )
            if (hasLearningCards) {
                if (restoreImmediately) {
                    db.favoritesQueries.unsuspendCardsByFavoriteClearingAvailableAfter(
                        sense_id = senseUuid,
                        lang_code = language.code,
                    )
                } else {
                    db.favoritesQueries.unsuspendCardsByFavorite(
                        sense_id = senseUuid,
                        lang_code = language.code,
                    )
                }
            }
        }
    }
}
