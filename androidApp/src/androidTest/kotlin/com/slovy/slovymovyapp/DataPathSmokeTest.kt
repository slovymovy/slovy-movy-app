package com.slovy.slovymovyapp

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.favorites.FavoritesRepository
import com.slovy.slovymovyapp.data.local.LocalDbManager
import com.slovy.slovymovyapp.data.remote.DataDbManager
import com.slovy.slovymovyapp.data.remote.DictionaryClient
import com.slovy.slovymovyapp.data.remote.DictionaryRepository
import com.slovy.slovymovyapp.data.remote.PlatformDbSupport
import com.slovy.slovymovyapp.data.remote.RemoteDataProvider
import com.slovy.slovymovyapp.data.remote.RemoteFile
import com.slovy.slovymovyapp.data.settings.SettingsRepository
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DataPathSmokeTest {
    @Test
    fun downloadsRepositoryFixtures() = withSmokeEnv { env ->
        runBlocking {
            env.dataDbManager.ensureDictionary(Language.ENGLISH)
            env.dataDbManager.ensureTranslation(Language.ENGLISH, Language.RUSSIAN)
        }

        assertTrue(
            "English dictionary should be installed.",
            env.repository.installedDictionaries().contains(Language.ENGLISH)
        )
        assertTrue(
            "English to Russian translation should be installed.",
            env.repository.installedTranslationTargets(Language.ENGLISH).contains(Language.RUSSIAN)
        )
    }

    @Test
    fun fetchesWordFromDownloadedDictionary() = withSmokeEnv { env ->
        runBlocking {
            env.dataDbManager.ensureDictionary(Language.ENGLISH)
            env.dataDbManager.ensureTranslation(Language.ENGLISH, Language.RUSSIAN)
        }

        val searchResults = runBlocking {
            env.repository.search("bu", dictionaryLanguage = Language.ENGLISH)
        }
        assertTrue("Downloaded English dictionary should return search results.", searchResults.isNotEmpty())

        val first = searchResults.first()
        val card = runBlocking {
            env.repository.getLanguageCard(Language.ENGLISH, first.lemma, listOf(Language.RUSSIAN))
        }

        assertNotNull("Repository should build a card for a downloaded lemma.", card)
        assertEquals(first.lemma, card!!.lemma)
        assertTrue("Fetched card should include at least one POS entry.", card.entries.isNotEmpty())
        assertFalse("Downloaded fixture card should not be online-only.", card.online)
    }

    @Test
    fun dictionaryClientFetchesMissingTranslation() = withSmokeEnv { env ->
        runBlocking {
            env.dataDbManager.ensureDictionary(Language.ENGLISH)
        }

        val results = runBlocking {
            env.dictionaryClient.getWord(
                language = Language.ENGLISH,
                lemma = "simultaneously",
                translationTargets = listOf(Language.POLISH),
            ).toList()
        }

        assertTrue("DictionaryClient should emit at least one result.", results.isNotEmpty())

        val firstCard = results.first().card
        assertNotNull("First emission should include local dictionary data.", firstCard)
        assertEquals("simultaneously", firstCard!!.lemma)

        val last = results.last()
        assertNull("Last DictionaryClient emission should not contain an error.", last.error)
        assertNotNull("Last DictionaryClient emission should include a card.", last.card)
        assertFalse("Last DictionaryClient emission should not still be loading.", last.isTranslationLoading)

        val targetLanguages = last.card!!.entries
            .flatMap { entry -> entry.senses }
            .flatMap { sense -> sense.translations.keys + sense.targetLangDefinitions.keys }
            .toSet()
        assertTrue(
            "Fetched card should contain Polish translation data.",
            Language.POLISH in targetLanguages
        )
    }

    private fun withSmokeEnv(block: (SmokeEnv) -> Unit) {
        val env = SmokeEnv.create()
        try {
            env.reset()
            block(env)
        } finally {
            env.reset()
            env.close()
        }
    }

    private class SmokeEnv(
        private val appDatabaseHolder: com.slovy.slovymovyapp.data.remote.AppDatabaseHolder,
        val dataDbManager: DataDbManager,
        private val localDbManager: LocalDbManager,
        val repository: DictionaryRepository,
        val dictionaryClient: DictionaryClient,
    ) : AutoCloseable {
        fun reset() {
            runBlocking {
                dataDbManager.deleteAllDownloadedData()
                localDbManager.deleteAll()
            }
        }

        override fun close() {
            dataDbManager.closeAllReadOnlyDatabases()
            runBlocking {
                localDbManager.closeAll()
            }
            appDatabaseHolder.close()
        }

        companion object {
            fun create(): SmokeEnv {
                val context = ApplicationProvider.getApplicationContext<Context>()
                val platform = PlatformDbSupport(context)
                val appDatabaseHolder = DataDbManager.openAppDatabaseHolder(platform)
                val settingsRepository = SettingsRepository(appDatabaseHolder.database)
                val dataDbManager = DataDbManager(
                    platform = platform,
                    settingsRepository = settingsRepository,
                    remoteDataProvider = AndroidTestServerDataProvider(testServerBaseUrl()),
                )
                val localDbManager = LocalDbManager(platform)
                val repository = DictionaryRepository(
                    dataDbManager = dataDbManager,
                    localDbManager = localDbManager,
                    favoritesRepository = FavoritesRepository(appDatabaseHolder.database),
                    settingsRepository = settingsRepository,
                )
                val dictionaryClient = DictionaryClient(
                    platform = platform,
                    dictionaryRepository = repository,
                    localDbManager = localDbManager,
                    dataDbManager = dataDbManager,
                    baseUrl = testServerBaseUrl(),
                )

                return SmokeEnv(
                    appDatabaseHolder = appDatabaseHolder,
                    dataDbManager = dataDbManager,
                    localDbManager = localDbManager,
                    repository = repository,
                    dictionaryClient = dictionaryClient,
                )
            }

            private fun testServerBaseUrl(): String {
                val args = InstrumentationRegistry.getArguments()
                val port = args.getString("TEST_SERVER_PORT")?.takeIf { it.isNotBlank() } ?: "9090"
                return "http://10.0.2.2:$port"
            }
        }
    }

    private class AndroidTestServerDataProvider(
        private val baseUrl: String,
    ) : RemoteDataProvider {
        override suspend fun listFiles(platform: PlatformDbSupport): List<RemoteFile> = withContext(Dispatchers.IO) {
            val connection = URL("$baseUrl/test/db/list").openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 5_000
                connection.readTimeout = 30_000
                connection.requestMethod = "GET"
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val items = JSONArray(body)
                List(items.length()) { index ->
                    val item = items.getJSONObject(index)
                    RemoteFile(
                        name = item.getString("name"),
                        sizeBytes = item.getLong("sizeBytes"),
                    )
                }
            } finally {
                connection.disconnect()
            }
        }

        override fun downloadUrlFor(fileName: String): String {
            val encodedName = URLEncoder.encode(fileName, "UTF-8")
            return "$baseUrl/test/db/file/$encodedName"
        }

        override fun headersForHttp(): Map<String, String> = emptyMap()
    }
}
