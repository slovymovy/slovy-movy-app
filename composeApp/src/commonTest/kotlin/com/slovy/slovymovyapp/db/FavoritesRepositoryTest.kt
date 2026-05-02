package com.slovy.slovymovyapp.db

import app.cash.sqldelight.db.SqlDriver
import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.db.DatabaseProvider
import com.slovy.slovymovyapp.data.favorites.FavoritesRepository
import com.slovy.slovymovyapp.test.BaseTest
import com.slovy.slovymovyapp.test.testPlatformDbSupport
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
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

    private class AppHandle(
        private val driver: SqlDriver,
        val database: AppDatabase,
    ) {
        fun close() {
            driver.close()
        }
    }
}
