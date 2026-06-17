package com.slovy.slovymovyapp.repo

import app.cash.sqldelight.db.SqlDriver
import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.db.DatabaseProvider
import com.slovy.slovymovyapp.data.lists.ListsService
import com.slovy.slovymovyapp.data.lists.WordList
import com.slovy.slovymovyapp.data.lists.WordListSense
import com.slovy.slovymovyapp.data.lists.WordListsRepository
import com.slovy.slovymovyapp.data.remote.ListsClient
import com.slovy.slovymovyapp.db.AppDatabase
import com.slovy.slovymovyapp.test.BaseTest
import com.slovy.slovymovyapp.test.IgnoreIos
import com.slovy.slovymovyapp.test.TestContext
import com.slovy.slovymovyapp.test.testPlatformDbSupport
import io.ktor.client.request.delete
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * Integration tests for [ListsService] against the in-process test server.
 * List content is staged through `PUT /test/lists/{lang}`, which backs the
 * test-mode `/lists/{lang}` endpoints (see server `TestApplication.kt`).
 */
@IgnoreIos
class ListsServiceServerTest : BaseTest() {

    @Serializable
    private data class TestListSense(
        val senseId: String,
        val lemma: String,
    )

    @Serializable
    private data class TestListContent(
        val id: String,
        val title: Map<String, String>,
        val subtitle: Map<String, String>,
        val labels: Map<String, List<String>>,
        val iconSvg: String? = null,
        val senses: List<TestListSense>,
        val order: Int? = null,
    )

    private fun testSenses(vararg ids: String) = ids.map { TestListSense(senseId = it, lemma = "$it-lemma") }

    @Serializable
    private data class TestListsBundle(
        val version: String,
        val lists: List<TestListContent>,
    )

    private val json = Json

    private fun serverUrl(): String {
        val port = TestContext.getCiEnv("TEST_SERVER_PORT") ?: "9090"
        val host = TestContext.testServerHost()
        return "http://$host:$port"
    }

    // Unroutable port: every connection attempt fails fast, simulating an offline server.
    private fun deadServerUrl(): String = "http://${TestContext.testServerHost()}:59997"

    private val basicContent = TestListContent(
        id = "a1_basic",
        title = mapOf("en" to "Basic words", "nl" to "Basiswoorden"),
        subtitle = mapOf("en" to "Start here"),
        labels = mapOf("en" to listOf("A1", "Basic")),
        iconSvg = """<svg viewBox="0 0 24 24"><path d="M4 4h16v16H4z"/></svg>""",
        senses = testSenses("sense-2", "sense-1"),
    )

    private fun TestListContent.toWordList(language: Language) = WordList(
        id = id,
        title = title,
        subtitle = subtitle,
        labels = labels,
        senses = senses.map { WordListSense(it.senseId, it.lemma, language) },
        iconSvg = iconSvg,
    )

    private suspend fun putServerLists(language: Language, bundle: TestListsBundle) {
        val client = testPlatformDbSupport().createHttpClient()
        try {
            val response = client.put("${serverUrl()}/test/lists/${language.code}") {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(TestListsBundle.serializer(), bundle))
            }
            assertTrue(
                response.status.isSuccess(),
                "Staging test lists for ${language.code} failed: HTTP ${response.status.value}",
            )
        } finally {
            client.close()
        }
    }

    private suspend fun deleteServerLists(language: Language) {
        val client = testPlatformDbSupport().createHttpClient()
        try {
            client.delete("${serverUrl()}/test/lists/${language.code}")
        } finally {
            client.close()
        }
    }

    private suspend fun withService(
        baseUrl: String,
        block: suspend (service: ListsService, repository: WordListsRepository) -> Unit,
    ) {
        val platform = testPlatformDbSupport()
        val driver = platform.createAppDataDriver(platform.getDatabasePath("${Uuid.random()}-lists-service-test.db"))
        try {
            val repository = WordListsRepository(DatabaseProvider.createAppDatabase(driver))
            val service = ListsService(repository, ListsClient(platform, baseUrl))
            block(service, repository)
        } finally {
            driver.close()
        }
    }

    @Test
    fun sync_storesServerBundleInDb_andServesFromDb() = runBlocking {
        val language = Language.TURKISH
        try {
            putServerLists(language, TestListsBundle(version = "v1", lists = listOf(basicContent)))
            withService(serverUrl()) { service, repository ->
                assertTrue(service.sync(language), "first sync must store new content")

                assertEquals("v1", repository.getVersion(language))
                assertEquals(listOf(basicContent.toWordList(language)), service.getLists(language))
                assertEquals(basicContent.toWordList(language), service.getList(language, basicContent.id))
            }
        } finally {
            deleteServerLists(language)
        }
    }

    @Test
    fun sync_skipsRefetch_whenVersionUnchanged() = runBlocking {
        val language = Language.CZECH
        try {
            putServerLists(language, TestListsBundle(version = "v1", lists = listOf(basicContent)))
            withService(serverUrl()) { service, repository ->
                assertTrue(service.sync(language), "first sync must store new content")

                // Same version, different content: the version gate must skip the refetch,
                // so the stored rows stay as they were.
                val changedContent = basicContent.copy(senses = testSenses("sense-99"))
                putServerLists(language, TestListsBundle(version = "v1", lists = listOf(changedContent)))

                assertFalse(service.sync(language), "sync must report no update for an unchanged version")
                assertEquals(
                    listOf(basicContent.toWordList(language)),
                    repository.getLists(language),
                    "stored lists must not change when the version is unchanged",
                )
            }
        } finally {
            deleteServerLists(language)
        }
    }

    @Test
    fun sync_replacesStoredBundle_whenVersionChanges() = runBlocking {
        val language = Language.SPANISH
        try {
            putServerLists(language, TestListsBundle(version = "v1", lists = listOf(basicContent)))
            withService(serverUrl()) { service, repository ->
                assertTrue(service.sync(language))

                val updatedContent = basicContent.copy(
                    title = basicContent.title + ("ru" to "Базовые слова"),
                    senses = testSenses("sense-7"),
                )
                val newContent = TestListContent(
                    id = "a2_next",
                    title = mapOf("en" to "Next steps"),
                    subtitle = mapOf("en" to "Keep going"),
                    labels = emptyMap(),
                    senses = testSenses("sense-8"),
                )
                putServerLists(language, TestListsBundle(version = "v2", lists = listOf(updatedContent, newContent)))

                assertTrue(service.sync(language), "sync must store content for a new version")
                assertEquals("v2", repository.getVersion(language))
                assertEquals(
                    listOf(updatedContent.toWordList(language), newContent.toWordList(language)),
                    service.getLists(language),
                )
            }
        } finally {
            deleteServerLists(language)
        }
    }

    @Test
    fun sync_ordersListsByOrderField_withAbsentLast() = runBlocking {
        val language = Language.POLISH
        try {
            // Staged out of feed order: the client must sort by ascending `order`, with the
            // unordered list (`order = null`) falling to the end and id as the tiebreaker.
            val second = TestListContent(
                id = "z_second",
                title = mapOf("en" to "Second"),
                subtitle = mapOf("en" to ""),
                labels = emptyMap(),
                senses = testSenses("sense-2"),
                order = 2,
            )
            val first = TestListContent(
                id = "a_first",
                title = mapOf("en" to "First"),
                subtitle = mapOf("en" to ""),
                labels = emptyMap(),
                senses = testSenses("sense-1"),
                order = 1,
            )
            val unordered = TestListContent(
                id = "m_unordered",
                title = mapOf("en" to "Unordered"),
                subtitle = mapOf("en" to ""),
                labels = emptyMap(),
                senses = testSenses("sense-3"),
                order = null,
            )
            putServerLists(
                language,
                TestListsBundle(version = "v1", lists = listOf(unordered, second, first)),
            )
            withService(serverUrl()) { service, _ ->
                assertTrue(service.sync(language), "first sync must store new content")
                assertEquals(
                    listOf(first.id, second.id, unordered.id),
                    service.getLists(language).map { it.id },
                )
            }
        } finally {
            deleteServerLists(language)
        }
    }

    @Test
    fun sync_servesCommittedListsFromDisk() = runBlocking {
        // No staged bundle for nl: the test server falls back to the committed
        // `.test-lists-files/nl/` directory (TEST_LISTS_DIR), pairing `{id}.svg`
        // icons by file name like the production GitHub loader does.
        val language = Language.DUTCH
        deleteServerLists(language)
        withService(serverUrl()) { service, repository ->
            assertTrue(service.sync(language), "sync must store the committed nl lists")
            val version = repository.getVersion(language)
            assertTrue(!version.isNullOrBlank(), "directory-backed bundle must carry a content-hash version")
            val lists = service.getLists(language)
            assertTrue(lists.isNotEmpty(), "committed nl lists must be served from disk")
            for (list in lists) {
                assertTrue(list.senseIds.isNotEmpty(), "list '${list.id}' must carry sense ids")
            }
            assertTrue(
                lists.any { it.iconSvg != null },
                "committed nl lists include svg icons; at least one must survive the round trip",
            )
        }
    }

    @Test
    fun sync_returnsFalse_whenLanguageUnknownOnServer() = runBlocking {
        val language = Language.ITALIAN
        deleteServerLists(language)
        withService(serverUrl()) { service, repository ->
            assertFalse(service.sync(language), "sync must report no update for a 404")
            assertNull(repository.getVersion(language))
            assertEquals(emptyList(), service.getLists(language))
        }
    }

    @Test
    fun sync_keepsStoredLists_whenServerUnreachable() = runBlocking {
        val language = Language.FRENCH
        withService(deadServerUrl()) { service, repository ->
            repository.replaceLists(language, "v1", listOf(basicContent.toWordList(language)))

            assertFalse(service.sync(language), "sync must report no update when the server is unreachable")
            assertEquals("v1", repository.getVersion(language))
            assertEquals(
                listOf(basicContent.toWordList(language)),
                service.getLists(language),
                "stored lists must survive a failed sync",
            )
            assertEquals(
                basicContent.toWordList(language),
                service.getList(language, basicContent.id),
                "list detail reads must not require the network",
            )
        }
    }
}
