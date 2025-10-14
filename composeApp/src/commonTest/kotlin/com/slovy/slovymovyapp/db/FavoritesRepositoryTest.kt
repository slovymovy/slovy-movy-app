package com.slovy.slovymovyapp.db

import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.favorites.FavoritesRepository
import com.slovy.slovymovyapp.data.remote.DataDbManager
import com.slovy.slovymovyapp.test.BaseTest
import com.slovy.slovymovyapp.test.platformDbSupport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

open class FavoritesRepositoryTest : BaseTest() {

    @Test
    fun add_and_remove_favorite() {
        val db: AppDatabase = DataDbManager(platformDbSupport()).openAppDatabase()
        val repo = FavoritesRepository(db)
        repo.deleteAll()

        val senseId = "sense123"
        val targetLang = Language.ENGLISH
        val lemma = "test"

        // Add favorite
        repo.add(senseId, targetLang, lemma)

        // Verify it exists
        assertTrue(repo.exists(senseId, targetLang))

        // Remove favorite
        repo.remove(senseId, targetLang)

        // Verify it's removed
        assertFalse(repo.exists(senseId, targetLang))
    }

    @Test
    fun getAll_returns_all_favorites() {
        val db: AppDatabase = DataDbManager(platformDbSupport()).openAppDatabase()
        val repo = FavoritesRepository(db)
        repo.deleteAll()

        // Add multiple favorites
        repo.add("sense1", Language.ENGLISH, "hello")
        repo.add("sense2", Language.ENGLISH, "world")
        repo.add("sense3", Language.RUSSIAN, "bonjour")

        val all = repo.getAll()

        assertEquals(3, all.size)
        assertTrue(all.any { it.senseId == "sense1" && it.targetLang == Language.ENGLISH && it.lemma == "hello" })
        assertTrue(all.any { it.senseId == "sense2" && it.targetLang == Language.ENGLISH && it.lemma == "world" })
        assertTrue(all.any { it.senseId == "sense3" && it.targetLang == Language.RUSSIAN && it.lemma == "bonjour" })
    }

    @Test
    fun getByLangAndLemma_filters_correctly() {
        val db: AppDatabase = DataDbManager(platformDbSupport()).openAppDatabase()
        val repo = FavoritesRepository(db)
        repo.deleteAll()

        // Add favorites with different languages and lemmas
        repo.add("sense1", Language.ENGLISH, "hello")
        repo.add("sense2", Language.ENGLISH, "hello")
        repo.add("sense3", Language.ENGLISH, "world")
        repo.add("sense4", Language.RUSSIAN, "hello")

        val results = repo.getByLangAndLemma(Language.ENGLISH, "hello")

        assertEquals(2, results.size)
        assertTrue(results.all { it.targetLang == Language.ENGLISH && it.lemma == "hello" })
        assertTrue(results.any { it.senseId == "sense1" })
        assertTrue(results.any { it.senseId == "sense2" })
    }

    @Test
    fun add_replaces_existing_favorite() {
        val db: AppDatabase = DataDbManager(platformDbSupport()).openAppDatabase()
        val repo = FavoritesRepository(db)
        repo.deleteAll()

        // Add favorite twice with same senseId and targetLang
        repo.add("sense1", Language.ENGLISH, "hello")
        repo.add("sense1", Language.ENGLISH, "hello")

        val all = repo.getAll()

        // Should only have one entry
        assertEquals(1, all.size)
    }

    @Test
    fun exists_returns_false_for_nonexistent_favorite() {
        val db: AppDatabase = DataDbManager(platformDbSupport()).openAppDatabase()
        val repo = FavoritesRepository(db)
        repo.deleteAll()

        assertFalse(repo.exists("nonexistent", Language.ENGLISH))
    }

    @Test
    fun getAllGroupedByLangAndLemma_returns_ordered_list() {
        val db: AppDatabase = DataDbManager(platformDbSupport()).openAppDatabase()
        val repo = FavoritesRepository(db)
        repo.deleteAll()

        // Add favorites in mixed order
        repo.add("sense3", Language.RUSSIAN, "bonjour")
        repo.add("sense1", Language.ENGLISH, "hello")
        repo.add("sense4", Language.RUSSIAN, "monde")
        repo.add("sense2", Language.ENGLISH, "world")

        val results = repo.getAllGroupedByLangAndLemma()

        assertEquals(4, results.size)

        // Verify ordering
        assertEquals(Language.ENGLISH, results[0].targetLang)
        assertEquals("hello", results[0].lemma)
        assertEquals(Language.ENGLISH, results[1].targetLang)
        assertEquals("world", results[1].lemma)
        assertEquals(Language.RUSSIAN, results[2].targetLang)
        assertEquals("bonjour", results[2].lemma)
        assertEquals(Language.RUSSIAN, results[3].targetLang)
        assertEquals("monde", results[3].lemma)
    }
}
