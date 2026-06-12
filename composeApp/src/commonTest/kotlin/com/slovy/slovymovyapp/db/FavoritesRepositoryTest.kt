package com.slovy.slovymovyapp.db

import app.cash.sqldelight.db.SqlDriver
import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.db.DatabaseProvider
import com.slovy.slovymovyapp.data.favorites.FavoritesRepository
import com.slovy.slovymovyapp.data.favorites.NewFavorite
import com.slovy.slovymovyapp.data.learning.CardFamily
import com.slovy.slovymovyapp.data.learning.CardKind
import com.slovy.slovymovyapp.data.learning.CardState
import com.slovy.slovymovyapp.data.learning.Rating
import com.slovy.slovymovyapp.test.BaseTest
import com.slovy.slovymovyapp.test.testPlatformDbSupport
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.uuid.Uuid

open class FavoritesRepositoryTest : BaseTest() {
    private val sense1 = "00000000-0000-0000-0000-000000000001"
    private val sense2 = "00000000-0000-0000-0000-000000000002"
    private val sense3 = "00000000-0000-0000-0000-000000000003"
    private val sense4 = "00000000-0000-0000-0000-000000000004"
    private val nonexistent = "00000000-0000-0000-0000-000000009999"

    private fun openApp(): AppHandle {
        val platform = testPlatformDbSupport()
        val driver = platform.createAppDataDriver(platform.getDatabasePath("${Uuid.random()}-favorites-test.db"))
        return AppHandle(driver, DatabaseProvider.createAppDatabase(driver))
    }

    private suspend fun withRepository(block: suspend (FavoritesRepository) -> Unit) {
        val app = openApp()
        try {
            block(FavoritesRepository(app.database))
        } finally {
            app.close()
        }
    }

    @Test
    fun add_and_remove_favorite() = runBlocking {
        withRepository { repo ->
            repo.deleteAll()

            val senseId = sense1
            val language = Language.ENGLISH
            val lemma = "test"

            // Add favorite
            repo.add(senseId, language, lemma)

            // Verify it exists
            assertTrue(repo.exists(senseId, language))

            // Remove favorite
            repo.remove(senseId, language)

            // Verify it's removed
            assertFalse(repo.exists(senseId, language))
        }
    }

    @Test
    fun add_uses_epoch_milliseconds_for_created_at() = runBlocking {
        withRepository { repo ->
            repo.deleteAll()

            repo.add(sense1, Language.ENGLISH, "hello")

            val favorite = repo.getOne(sense1, Language.ENGLISH)
            assertTrue(
                favorite != null && favorite.createdAt > 1_000_000_000_000L,
                "createdAt should be stored as epoch milliseconds, not epoch seconds",
            )
        }
    }

    @Test
    fun getAll_returns_all_favorites() = runBlocking {
        withRepository { repo ->
            repo.deleteAll()

            // Add multiple favorites
            repo.add(sense1, Language.ENGLISH, "hello")
            repo.add(sense2, Language.ENGLISH, "world")
            repo.add(sense3, Language.RUSSIAN, "bonjour")

            val all = repo.getAll()

            assertEquals(3, all.size)
            assertTrue(all.any { it.senseId == sense1 && it.language == Language.ENGLISH && it.lemma == "hello" })
            assertTrue(all.any { it.senseId == sense2 && it.language == Language.ENGLISH && it.lemma == "world" })
            assertTrue(all.any { it.senseId == sense3 && it.language == Language.RUSSIAN && it.lemma == "bonjour" })
        }
    }

    @Test
    fun getByLangAndLemma_filters_correctly() = runBlocking {
        withRepository { repo ->
            repo.deleteAll()

            // Add favorites with different languages and lemmas
            repo.add(sense1, Language.ENGLISH, "hello")
            repo.add(sense2, Language.ENGLISH, "hello")
            repo.add(sense3, Language.ENGLISH, "world")
            repo.add(sense4, Language.RUSSIAN, "hello")

            val results = repo.getByLangAndLemma(Language.ENGLISH, "hello")

            assertEquals(2, results.size)
            assertTrue(results.all { it.language == Language.ENGLISH && it.lemma == "hello" })
            assertTrue(results.any { it.senseId == sense1 })
            assertTrue(results.any { it.senseId == sense2 })
        }
    }

    @Test
    fun add_replaces_existing_favorite() = runBlocking {
        withRepository { repo ->
            repo.deleteAll()

            // Add favorite twice with same senseId and language
            repo.add(sense1, Language.ENGLISH, "hello")
            repo.add(sense1, Language.ENGLISH, "hello")

            val all = repo.getAll()

            // Should only have one entry
            assertEquals(1, all.size)
        }
    }

    @Test
    fun addAll_adds_only_missing_favorites() = runBlocking {
        withRepository { repo ->
            repo.deleteAll()

            repo.add(sense1, Language.ENGLISH, "hello", 1000L)

            repo.addAll(
                Language.ENGLISH,
                listOf(
                    NewFavorite(senseId = sense1, lemma = "hello"),
                    NewFavorite(senseId = sense2, lemma = "world"),
                    NewFavorite(senseId = sense3, lemma = "again"),
                ),
            )

            assertEquals(3, repo.getAll().size)
            val existing = assertNotNull(repo.getOne(sense1, Language.ENGLISH))
            assertEquals(1000L, existing.createdAt, "pre-existing favorite should keep its createdAt")
            assertTrue(repo.exists(sense2, Language.ENGLISH))
            assertTrue(repo.exists(sense3, Language.ENGLISH))
            assertEquals(
                setOf("hello", "world", "again"),
                repo.getFavoriteLemmas(Language.ENGLISH),
                "lemma cache should be invalidated by addAll",
            )
        }
    }

    @Test
    fun removeAll_removes_only_given_senses() = runBlocking {
        withRepository { repo ->
            repo.deleteAll()

            repo.add(sense1, Language.ENGLISH, "hello")
            repo.add(sense2, Language.ENGLISH, "world")
            repo.add(sense3, Language.ENGLISH, "again")
            repo.add(sense4, Language.RUSSIAN, "privet")

            repo.removeAll(listOf(sense2, sense3, nonexistent), Language.ENGLISH)

            assertTrue(repo.exists(sense1, Language.ENGLISH), "favorite outside the bulk removal should stay")
            assertFalse(repo.exists(sense2, Language.ENGLISH))
            assertFalse(repo.exists(sense3, Language.ENGLISH))
            assertTrue(repo.exists(sense4, Language.RUSSIAN), "favorite in another language should stay")
            assertEquals(
                setOf("hello"),
                repo.getFavoriteLemmas(Language.ENGLISH),
                "lemma cache should be invalidated by removeAll",
            )
        }
    }

    @Test
    fun exists_returns_false_for_nonexistent_favorite() = runBlocking {
        withRepository { repo ->
            repo.deleteAll()

            assertFalse(repo.exists(nonexistent, Language.ENGLISH))
        }
    }

    @Test
    fun getAllGroupedByLangAndLemma_returns_ordered_list() = runBlocking {
        withRepository { repo ->
            repo.deleteAll()

            // Add favorites with explicit timestamps to ensure consistent ordering
            // All added at the same time (timestamp = 1000) to test secondary sorting
            val baseTimestamp = 1000L
            repo.add(sense3, Language.RUSSIAN, "bonjour", baseTimestamp)
            repo.add(sense1, Language.ENGLISH, "hello", baseTimestamp)
            repo.add(sense4, Language.RUSSIAN, "monde", baseTimestamp)
            repo.add(sense2, Language.ENGLISH, "world", baseTimestamp)

            val results = repo.getAllGroupedByLangAndLemma()

            assertEquals(4, results.size)

            // Verify ordering: when all timestamps equal, sorts by lang_code ASC, lemma ASC
            assertEquals(Language.ENGLISH, results[0].language)
            assertEquals("hello", results[0].lemma)
            assertEquals(Language.ENGLISH, results[1].language)
            assertEquals("world", results[1].lemma)
            assertEquals(Language.RUSSIAN, results[2].language)
            assertEquals("bonjour", results[2].lemma)
            assertEquals(Language.RUSSIAN, results[3].language)
            assertEquals("monde", results[3].lemma)
        }
    }

    @Test
    fun getFavoriteLemmas_returns_normalized_lemmas_and_invalidates_on_changes() = runBlocking {
        withRepository { repo ->
            repo.deleteAll()

            repo.add(sense1, Language.ENGLISH, " Hello ")
            assertEquals(setOf("hello"), repo.getFavoriteLemmas(Language.ENGLISH))

            repo.add(sense2, Language.ENGLISH, "World")
            assertEquals(
                setOf("hello", "world"),
                repo.getFavoriteLemmas(Language.ENGLISH),
                "lemma cache should be invalidated by add",
            )

            val snapshot = assertNotNull(repo.remove(sense2, Language.ENGLISH))
            assertEquals(
                setOf("hello"),
                repo.getFavoriteLemmas(Language.ENGLISH),
                "lemma cache should be invalidated by remove",
            )

            repo.restoreForUndo(snapshot)
            assertEquals(
                setOf("hello", "world"),
                repo.getFavoriteLemmas(Language.ENGLISH),
                "lemma cache should be invalidated by restoreForUndo",
            )

            repo.deleteAll()
            assertEquals(
                emptySet(),
                repo.getFavoriteLemmas(Language.ENGLISH),
                "lemma cache should be cleared by deleteAll",
            )
        }
    }

    @Test
    fun shiftLearningTimestampsBack_shifts_all_columns_by_exact_amount() = runBlocking {
        val app = openApp()
        try {
            val repo = FavoritesRepository(app.database)
            val q = app.database.favoritesQueries

            val senseId = sense1
            val lemmaId = Uuid.random()
            val cardId = Uuid.random()
            val reviewLogId = Uuid.random()
            val lang = Language.ENGLISH.code

            val favCreatedAt = 1_700_000_000_000L
            val favActivatedAt = 1_700_000_010_000L
            val cardCreatedAt = 1_700_000_020_000L
            val cardDue = 1_700_500_000_000L
            val cardLastReview = 1_700_000_030_000L
            val cardAvailableAfter = 1_700_000_040_000L
            val reviewedAt = 1_700_000_050_000L

            q.insertFavorite(
                sense_id = senseId,
                lang_code = lang,
                lemma = "hello",
                created_at = favCreatedAt,
                activated_at = favActivatedAt,
            )
            q.insertCard(
                id = cardId,
                sense_id = Uuid.parse(senseId),
                lemma_id = lemmaId,
                lang_code = lang,
                family = CardFamily.RECOGNIZE_SENSE,
                state = CardState.REVIEW,
                stability = 5.0,
                difficulty = 4.0,
                due = cardDue,
                last_review = cardLastReview,
                reps = 3,
                lapses = 1,
                created_at = cardCreatedAt,
                available_after = cardAvailableAfter,
                answer_key = "hello",
                suspended = false,
            )
            q.insertReviewLog(
                id = reviewLogId,
                card_id = cardId,
                reviewed_at = reviewedAt,
                rating = Rating.GOOD,
                variant_kind = CardKind.SOURCE_DEFINITION_TO_WORD,
                variant_target_lang = null,
                example_id = null,
                state_before = CardState.LEARNING,
                stability_before = 1.0,
                difficulty_before = 4.0,
                elapsed_days = 0,
                scheduled_days = 1,
                stability_after = 5.0,
                difficulty_after = 4.0,
                duration_ms = 1_200,
            )

            val shift = 2.days
            val shiftMs = shift.inWholeMilliseconds
            repo.shiftLearningTimestampsBack(shift)

            val fav = q.selectFavoriteWithActivation(senseId, lang).executeAsOne()
            assertEquals(favCreatedAt - shiftMs, fav.created_at, "favorites.created_at not shifted")
            assertEquals(favActivatedAt - shiftMs, fav.activated_at, "favorites.activated_at not shifted")

            val card = q.selectCardById(cardId).executeAsOne()
            assertEquals(cardCreatedAt - shiftMs, card.created_at, "card.created_at not shifted")
            assertEquals(cardDue - shiftMs, card.due, "card.due not shifted")
            assertEquals(cardLastReview - shiftMs, card.last_review, "card.last_review not shifted")
            assertEquals(cardAvailableAfter - shiftMs, card.available_after, "card.available_after not shifted")

            val log = q.selectReviewLogsByCard(card_id = cardId, limit = 1).executeAsOne()
            assertEquals(reviewedAt - shiftMs, log.reviewed_at, "review_log.reviewed_at not shifted")
        } finally {
            app.close()
        }
    }

    @Test
    fun shiftLearningTimestampsBack_preserves_nulls_in_nullable_columns() = runBlocking {
        val app = openApp()
        try {
            val repo = FavoritesRepository(app.database)
            val q = app.database.favoritesQueries

            val senseId = sense1
            val cardId = Uuid.random()
            val lang = Language.ENGLISH.code

            // Favorite with NULL activated_at (pending intake), card with NULL last_review + available_after.
            q.insertFavorite(
                sense_id = senseId,
                lang_code = lang,
                lemma = "hello",
                created_at = 1_700_000_000_000L,
                activated_at = null,
            )
            q.insertCard(
                id = cardId,
                sense_id = Uuid.parse(senseId),
                lemma_id = Uuid.random(),
                lang_code = lang,
                family = CardFamily.RECOGNIZE_SENSE,
                state = CardState.NEW,
                stability = 0.0,
                difficulty = 0.0,
                due = 1_700_000_000_000L,
                last_review = null,
                reps = 0,
                lapses = 0,
                created_at = 1_700_000_000_000L,
                available_after = null,
                answer_key = "hello",
                suspended = false,
            )

            repo.shiftLearningTimestampsBack(1.days)

            val fav = q.selectFavoriteWithActivation(senseId, lang).executeAsOne()
            assertNull(fav.activated_at, "favorites.activated_at must stay NULL")

            val card = q.selectCardById(cardId).executeAsOne()
            assertNull(card.last_review, "card.last_review must stay NULL")
            assertNull(card.available_after, "card.available_after must stay NULL")
        } finally {
            app.close()
        }
    }

    @Test
    fun shiftLearningTimestampsBack_rejects_non_positive_duration() = runBlocking {
        withRepository { repo ->
            assertFailsWith<IllegalArgumentException>("zero shift should be rejected") {
                repo.shiftLearningTimestampsBack(Duration.ZERO)
            }
            assertFailsWith<IllegalArgumentException>("negative shift should be rejected") {
                repo.shiftLearningTimestampsBack(-(1.days))
            }
        }
    }

    private class AppHandle(
        private val driver: SqlDriver,
        val database: AppDatabase,
    ) {
        fun close() {
            driver.close()
        }
    }
}
