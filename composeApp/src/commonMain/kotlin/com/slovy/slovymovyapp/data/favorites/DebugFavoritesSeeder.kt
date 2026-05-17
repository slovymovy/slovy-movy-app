package com.slovy.slovymovyapp.data.favorites

import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.learning.CardKind
import com.slovy.slovymovyapp.data.learning.CardState
import com.slovy.slovymovyapp.data.learning.Rating
import com.slovy.slovymovyapp.data.learning.fsrs.FsrsConfig
import com.slovy.slovymovyapp.data.learning.fsrs.FsrsDefaults
import com.slovy.slovymovyapp.data.remote.DataDbManager
import com.slovy.slovymovyapp.db.AppDatabase
import com.slovy.slovymovyapp.ingestion.JsonIngestionBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.ExperimentalTime
import kotlin.uuid.Uuid

class DebugFavoritesSeeder(
    private val appDatabase: AppDatabase,
    private val dataDbManager: DataDbManager,
    private val fsrsConfig: FsrsConfig = FsrsDefaults.config(),
) {

    data class Result(
        val requested: Int,
        val available: Int,
        val favoritesInserted: Int,
        val cardsInserted: Int,
        val reviewLogsInserted: Int = 0,
    )

    @OptIn(ExperimentalTime::class)
    suspend fun seed(language: Language, count: Int): Result = withContext(Dispatchers.IO) {
        require(count > 0) { "count must be > 0, was $count" }
        val rows = fetchRows(language, count)
        val now = Clock.System.now().toEpochMilliseconds()
        val favoritesQueries = appDatabase.favoritesQueries
        val before = favoritesQueries.countFavorites().executeAsOne()
        favoritesQueries.transaction {
            rows.forEachIndexed { index, row ->
                favoritesQueries.insertFavorite(
                    sense_id = row.sense_id.toString(),
                    lang_code = language.code,
                    lemma = row.lemma,
                    created_at = now - index.toLong(),
                    activated_at = null,
                )
            }
        }
        val after = favoritesQueries.countFavorites().executeAsOne()
        Result(
            requested = count,
            available = rows.size,
            favoritesInserted = (after - before).toInt(),
            cardsInserted = 0,
        )
    }

    /**
     * Like [seed] but also writes a NEW card per [FsrsConfig.defaultIntakeFamilies] for each
     * inserted favorite, marking the favorite as activated. Bypasses [IntakeService] entirely
     * so the full card pool exists immediately for stressing session selection — variants are
     * NOT validated, so some cards may surface as load errors in study.
     */
    @OptIn(ExperimentalTime::class)
    suspend fun seedWithCards(language: Language, count: Int): Result = withContext(Dispatchers.IO) {
        require(count > 0) { "count must be > 0, was $count" }
        val rows = fetchRows(language, count)
        val now = Clock.System.now().toEpochMilliseconds()
        val favoritesQueries = appDatabase.favoritesQueries
        val favoritesBefore = favoritesQueries.countFavorites().executeAsOne()
        val cardsBefore = favoritesQueries.countCardsByLang(language.code).executeAsOne()
        val families = fsrsConfig.defaultIntakeFamilies
        favoritesQueries.transaction {
            rows.forEachIndexed { index, row ->
                val senseIdString = row.sense_id.toString()
                val lemmaId = JsonIngestionBuilder.generateLemmaId(row.lemma)
                val answerKey = row.lemma.lowercase()
                val createdAt = now - index.toLong()
                favoritesQueries.insertFavorite(
                    sense_id = senseIdString,
                    lang_code = language.code,
                    lemma = row.lemma,
                    created_at = createdAt,
                    activated_at = now,
                )
                families.forEach { family ->
                    favoritesQueries.insertCard(
                        id = Uuid.random(),
                        sense_id = row.sense_id,
                        lemma_id = lemmaId,
                        lang_code = language.code,
                        family = family,
                        state = CardState.NEW,
                        stability = 0.0,
                        difficulty = 0.0,
                        due = now,
                        last_review = null,
                        reps = 0,
                        lapses = 0,
                        created_at = createdAt,
                        available_after = null,
                        answer_key = answerKey,
                        suspended = false,
                    )
                }
            }
        }
        val favoritesAfter = favoritesQueries.countFavorites().executeAsOne()
        val cardsAfter = favoritesQueries.countCardsByLang(language.code).executeAsOne()
        Result(
            requested = count,
            available = rows.size,
            favoritesInserted = (favoritesAfter - favoritesBefore).toInt(),
            cardsInserted = (cardsAfter - cardsBefore).toInt(),
        )
    }

    /**
     * Seeds favorites + REVIEW-state cards that look like they have been studied at least
     * once: non-zero stability/difficulty/reps and one [review_log] row per card. Half the
     * cards land due-now (`due <= now`) and half due-future, so both `selectDueCards` and
     * the non-due fraction of `countCardsByLang` get exercised.
     *
     * Values are derived from a stable per-index pattern (no Random) so re-running produces
     * the same shape.
     */
    @OptIn(ExperimentalTime::class)
    suspend fun seedReviewedCards(language: Language, count: Int): Result = withContext(Dispatchers.IO) {
        require(count > 0) { "count must be > 0, was $count" }
        val rows = fetchRows(language, count)
        val now = Clock.System.now().toEpochMilliseconds()
        val favoritesQueries = appDatabase.favoritesQueries
        val favoritesBefore = favoritesQueries.countFavorites().executeAsOne()
        val cardsBefore = favoritesQueries.countCardsByLang(language.code).executeAsOne()
        val families = fsrsConfig.defaultIntakeFamilies
        var reviewLogsInserted = 0
        favoritesQueries.transaction {
            rows.forEachIndexed { index, row ->
                val senseIdString = row.sense_id.toString()
                val lemmaId = JsonIngestionBuilder.generateLemmaId(row.lemma)
                val answerKey = row.lemma.lowercase()
                val createdAt = now - (index + 1).toLong() * 1000L

                val stabilityDays = 5.0 + (index % 36)
                val difficulty = 3.0 + (index % 5)
                val reps = 1L + (index % 15)
                val lapses = (index % 3).toLong()
                val daysSinceLastReview = 1L + (index % 30)
                val lastReview = now - (daysSinceLastReview.days).inWholeMilliseconds
                val due = if (index % 2 == 0) {
                    // Past — due now.
                    now - ((index % 7).days).inWholeMilliseconds
                } else {
                    // Future — not due yet.
                    now + ((1L + index % 30).days).inWholeMilliseconds
                }

                favoritesQueries.insertFavorite(
                    sense_id = senseIdString,
                    lang_code = language.code,
                    lemma = row.lemma,
                    created_at = createdAt,
                    activated_at = now,
                )
                families.forEach { family ->
                    val cardId = Uuid.random()
                    favoritesQueries.insertCard(
                        id = cardId,
                        sense_id = row.sense_id,
                        lemma_id = lemmaId,
                        lang_code = language.code,
                        family = family,
                        state = CardState.REVIEW,
                        stability = stabilityDays,
                        difficulty = difficulty,
                        due = due,
                        last_review = lastReview,
                        reps = reps,
                        lapses = lapses,
                        created_at = createdAt,
                        available_after = null,
                        answer_key = answerKey,
                        suspended = false,
                    )
                    favoritesQueries.insertReviewLog(
                        id = Uuid.random(),
                        card_id = cardId,
                        reviewed_at = lastReview,
                        rating = Rating.GOOD,
                        variant_kind = CardKind.WORD_TO_SOURCE_DEFINITION,
                        variant_target_lang = null,
                        example_id = null,
                        state_before = CardState.REVIEW,
                        stability_before = stabilityDays * 0.8,
                        difficulty_before = difficulty,
                        elapsed_days = daysSinceLastReview,
                        scheduled_days = stabilityDays.toLong(),
                        stability_after = stabilityDays,
                        difficulty_after = difficulty,
                        duration_ms = 5_000L,
                    )
                    reviewLogsInserted += 1
                }
            }
        }
        val favoritesAfter = favoritesQueries.countFavorites().executeAsOne()
        val cardsAfter = favoritesQueries.countCardsByLang(language.code).executeAsOne()
        Result(
            requested = count,
            available = rows.size,
            favoritesInserted = (favoritesAfter - favoritesBefore).toInt(),
            cardsInserted = (cardsAfter - cardsBefore).toInt(),
            reviewLogsInserted = reviewLogsInserted,
        )
    }

    private suspend fun fetchRows(language: Language, count: Int) =
        dataDbManager.withDictionaryReadOnly(language) { db ->
            db.dictionaryQueries
                .selectFrequentSenseIdsForSeed(lang_code = language.code, max_rows = count.toLong())
                .executeAsList()
        }
}
