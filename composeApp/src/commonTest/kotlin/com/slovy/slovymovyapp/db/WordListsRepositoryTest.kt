package com.slovy.slovymovyapp.db

import app.cash.sqldelight.db.SqlDriver
import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.db.DatabaseProvider
import com.slovy.slovymovyapp.data.lists.WordList
import com.slovy.slovymovyapp.data.lists.WordListSense
import com.slovy.slovymovyapp.data.lists.WordListsRepository
import com.slovy.slovymovyapp.test.BaseTest
import com.slovy.slovymovyapp.test.testPlatformDbSupport
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.uuid.Uuid

open class WordListsRepositoryTest : BaseTest() {

    // A WordListSense carries the language of the list it belongs to, so fixtures are built per
    // language to round-trip correctly against the language they are stored under.
    private fun senses(language: Language, vararg ids: String) =
        ids.map { WordListSense(senseId = it, lemma = "$it-lemma", language = language) }

    private val basicList = WordList(
        id = "nl_a1_basic",
        title = mapOf("en" to "500 first Dutch words", "nl" to "500 eerste Nederlandse woorden"),
        subtitle = mapOf("en" to "This is where your journey begins"),
        labels = mapOf("en" to listOf("A1", "Basic"), "nl" to listOf("A1")),
        senses = senses(Language.DUTCH, "sense-3", "sense-1", "sense-2"),
        iconSvg = null,
    )

    private val travelList = WordList(
        id = "nl_travel",
        title = mapOf("en" to "Travel words"),
        subtitle = emptyMap(),
        labels = emptyMap(),
        senses = senses(Language.DUTCH, "sense-9"),
        iconSvg = null,
    )

    // Same travel list authored under Polish, for cross-language isolation tests.
    private val travelListPl = travelList.copy(senses = senses(Language.POLISH, "sense-9"))

    private fun openApp(): AppHandle {
        val platform = testPlatformDbSupport()
        val driver = platform.createAppDataDriver(platform.getDatabasePath("${Uuid.random()}-word-lists-test.db"))
        return AppHandle(driver, DatabaseProvider.createAppDatabase(driver))
    }

    private suspend fun withRepository(block: suspend (WordListsRepository) -> Unit) {
        val app = openApp()
        try {
            block(WordListsRepository(app.database))
        } finally {
            app.close()
        }
    }

    @Test
    fun empty_db_has_no_lists_and_no_version() = runBlocking {
        withRepository { repo ->
            assertNull(repo.getVersion(Language.DUTCH), "no version should be stored initially")
            assertEquals(emptyList(), repo.getLists(Language.DUTCH))
            assertNull(repo.getList(Language.DUTCH, "nl_a1_basic"))
        }
    }

    @Test
    fun replace_and_get_roundtrip() = runBlocking {
        withRepository { repo ->
            repo.replaceLists(Language.DUTCH, "v1", listOf(basicList, travelList))

            assertEquals("v1", repo.getVersion(Language.DUTCH))

            val lists = repo.getLists(Language.DUTCH)
            assertEquals(listOf(basicList, travelList), lists, "feed order and content must round-trip")

            assertEquals(basicList, repo.getList(Language.DUTCH, basicList.id))
            assertEquals(travelList, repo.getList(Language.DUTCH, travelList.id))
            assertNull(repo.getList(Language.DUTCH, "unknown"))
        }
    }

    @Test
    fun replace_overwrites_previous_content() = runBlocking {
        withRepository { repo ->
            repo.replaceLists(Language.DUTCH, "v1", listOf(basicList, travelList))

            val updatedBasic = basicList.copy(
                title = basicList.title + ("ru" to "500 первых слов"),
                senses = senses(Language.DUTCH, "sense-1"),
            )
            repo.replaceLists(Language.DUTCH, "v2", listOf(updatedBasic))

            assertEquals("v2", repo.getVersion(Language.DUTCH))
            assertEquals(listOf(updatedBasic), repo.getLists(Language.DUTCH))
            assertNull(repo.getList(Language.DUTCH, travelList.id), "lists absent from the new bundle must be removed")
        }
    }

    @Test
    fun getAllSenses_returns_every_stored_sense_with_lemma_across_languages() = runBlocking {
        withRepository { repo ->
            repo.replaceLists(Language.DUTCH, "v1", listOf(basicList))
            repo.replaceLists(Language.POLISH, "p7", listOf(travelListPl))

            val all = repo.getAllSenses().toSet()
            assertEquals(
                setOf(
                    WordListSense("sense-3", "sense-3-lemma", Language.DUTCH),
                    WordListSense("sense-1", "sense-1-lemma", Language.DUTCH),
                    WordListSense("sense-2", "sense-2-lemma", Language.DUTCH),
                    WordListSense("sense-9", "sense-9-lemma", Language.POLISH),
                ),
                all,
                "getAllSenses must carry (language, lemma, senseId) for every stored list sense",
            )
        }
    }

    @Test
    fun languages_are_isolated() = runBlocking {
        withRepository { repo ->
            repo.replaceLists(Language.DUTCH, "v1", listOf(basicList))
            repo.replaceLists(Language.POLISH, "p7", listOf(travelListPl))

            assertEquals(listOf(basicList), repo.getLists(Language.DUTCH))
            assertEquals(listOf(travelListPl), repo.getLists(Language.POLISH))
            assertEquals("v1", repo.getVersion(Language.DUTCH))
            assertEquals("p7", repo.getVersion(Language.POLISH))
            assertNull(repo.getList(Language.POLISH, basicList.id))

            repo.replaceLists(Language.DUTCH, "v2", emptyList())
            assertEquals(emptyList(), repo.getLists(Language.DUTCH))
            assertEquals(listOf(travelListPl), repo.getLists(Language.POLISH), "other languages must stay untouched")
        }
    }

    @Test
    fun clearAll_dropsListsAndVersionsForEveryLanguage() = runBlocking {
        withRepository { repo ->
            repo.replaceLists(Language.DUTCH, "v1", listOf(basicList))
            repo.replaceLists(Language.POLISH, "p7", listOf(travelList))

            val removed = repo.clearAll()

            assertEquals(2L, removed, "clearAll must report the number of removed lists")
            assertNull(repo.getVersion(Language.DUTCH), "version must be dropped so the next sync refetches")
            assertNull(repo.getVersion(Language.POLISH))
            assertEquals(emptyList(), repo.getLists(Language.DUTCH))
            assertEquals(emptyList(), repo.getLists(Language.POLISH))
        }
    }

    @Test
    fun sense_order_is_preserved() = runBlocking {
        withRepository { repo ->
            val manySenses = basicList.copy(
                senses = (0 until 500).map {
                    WordListSense(senseId = "sense-$it", lemma = "lemma-$it", language = Language.DUTCH)
                },
            )
            repo.replaceLists(Language.DUTCH, "v1", listOf(manySenses))

            val stored = repo.getList(Language.DUTCH, manySenses.id)
            assertEquals(manySenses.senses, stored?.senses, "senses (id + lemma) must keep server order")
        }
    }

    @Test
    fun replace_rejects_duplicate_sense_ids_within_a_list() = runBlocking {
        withRepository { repo ->
            repo.replaceLists(Language.DUTCH, "v1", listOf(basicList))

            val duplicated = travelList.copy(senses = senses(Language.DUTCH, "sense-9", "sense-9"))
            assertFailsWith<Exception>("a duplicate sense id within a list must violate the unique index") {
                repo.replaceLists(Language.DUTCH, "v2", listOf(basicList, duplicated))
            }

            assertEquals("v1", repo.getVersion(Language.DUTCH), "failed replace must roll back the version")
            assertEquals(listOf(basicList), repo.getLists(Language.DUTCH), "failed replace must roll back the lists")
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
