package com.slovy.slovymovyapp.db

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
        val targetLang = "en"
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
        repo.add("sense1", "en", "hello")
        repo.add("sense2", "en", "world")
        repo.add("sense3", "fr", "bonjour")

        val all = repo.getAll()

        assertEquals(3, all.size)
        assertTrue(all.any { it.senseId == "sense1" && it.targetLang == "en" && it.lemma == "hello" })
        assertTrue(all.any { it.senseId == "sense2" && it.targetLang == "en" && it.lemma == "world" })
        assertTrue(all.any { it.senseId == "sense3" && it.targetLang == "fr" && it.lemma == "bonjour" })
    }

    @Test
    fun getByLangAndLemma_filters_correctly() {
        val db: AppDatabase = DataDbManager(platformDbSupport()).openAppDatabase()
        val repo = FavoritesRepository(db)
        repo.deleteAll()

        // Add favorites with different languages and lemmas
        repo.add("sense1", "en", "hello")
        repo.add("sense2", "en", "hello")
        repo.add("sense3", "en", "world")
        repo.add("sense4", "fr", "hello")

        val results = repo.getByLangAndLemma("en", "hello")

        assertEquals(2, results.size)
        assertTrue(results.all { it.targetLang == "en" && it.lemma == "hello" })
        assertTrue(results.any { it.senseId == "sense1" })
        assertTrue(results.any { it.senseId == "sense2" })
    }

    @Test
    fun getGroupedByLangAndLemma_returns_correct_groups() {
        val db: AppDatabase = DataDbManager(platformDbSupport()).openAppDatabase()
        val repo = FavoritesRepository(db)
        repo.deleteAll()

        // Add favorites
        repo.add("sense1", "en", "hello")
        repo.add("sense2", "en", "hello")
        repo.add("sense3", "en", "world")
        repo.add("sense4", "fr", "bonjour")
        repo.add("sense5", "fr", "bonjour")
        repo.add("sense6", "fr", "bonjour")

        val groups = repo.getGroupedByLangAndLemma()

        assertEquals(3, groups.size)

        val enHello = groups.find { it.targetLang == "en" && it.lemma == "hello" }
        assertEquals(2L, enHello?.count)

        val enWorld = groups.find { it.targetLang == "en" && it.lemma == "world" }
        assertEquals(1L, enWorld?.count)

        val frBonjour = groups.find { it.targetLang == "fr" && it.lemma == "bonjour" }
        assertEquals(3L, frBonjour?.count)
    }

    @Test
    fun add_replaces_existing_favorite() {
        val db: AppDatabase = DataDbManager(platformDbSupport()).openAppDatabase()
        val repo = FavoritesRepository(db)
        repo.deleteAll()

        // Add favorite twice with same senseId and targetLang
        repo.add("sense1", "en", "hello")
        repo.add("sense1", "en", "hello")

        val all = repo.getAll()

        // Should only have one entry
        assertEquals(1, all.size)
    }

    @Test
    fun exists_returns_false_for_nonexistent_favorite() {
        val db: AppDatabase = DataDbManager(platformDbSupport()).openAppDatabase()
        val repo = FavoritesRepository(db)
        repo.deleteAll()

        assertFalse(repo.exists("nonexistent", "en"))
    }

    @Test
    fun getAllGroupedByLangAndLemma_returns_ordered_list() {
        val db: AppDatabase = DataDbManager(platformDbSupport()).openAppDatabase()
        val repo = FavoritesRepository(db)
        repo.deleteAll()

        // Add favorites in mixed order
        repo.add("sense3", "fr", "bonjour")
        repo.add("sense1", "en", "hello")
        repo.add("sense4", "fr", "monde")
        repo.add("sense2", "en", "world")

        val results = repo.getAllGroupedByLangAndLemma()

        assertEquals(4, results.size)

        // Verify ordering: by targetLang ASC, then lemma ASC
        assertEquals("en", results[0].targetLang)
        assertEquals("hello", results[0].lemma)
        assertEquals("en", results[1].targetLang)
        assertEquals("world", results[1].lemma)
        assertEquals("fr", results[2].targetLang)
        assertEquals("bonjour", results[2].lemma)
        assertEquals("fr", results[3].targetLang)
        assertEquals("monde", results[3].lemma)
    }
}
