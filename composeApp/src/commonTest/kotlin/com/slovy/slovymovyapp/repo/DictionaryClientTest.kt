package com.slovy.slovymovyapp.repo

import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.dictionary.DictionaryPos
import com.slovy.slovymovyapp.data.dictionary.LearnerLevel
import com.slovy.slovymovyapp.data.dictionary.SenseFrequency
import com.slovy.slovymovyapp.data.favorites.FavoritesRepository
import com.slovy.slovymovyapp.data.local.LocalDbManager
import com.slovy.slovymovyapp.data.remote.DictionaryClient
import com.slovy.slovymovyapp.data.remote.DictionaryClientException
import com.slovy.slovymovyapp.data.remote.DictionaryRepository
import com.slovy.slovymovyapp.data.settings.SettingsRepository
import com.slovy.slovymovyapp.ingestion.JsonIngestionBuilder
import com.slovy.slovymovyapp.test.BaseTest
import com.slovy.slovymovyapp.test.IgnoreIos
import com.slovy.slovymovyapp.test.TestContext
import com.slovy.slovymovyapp.test.testPlatformDbSupport
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlin.test.*
import kotlin.uuid.Uuid

@IgnoreIos
class DictionaryClientTest : BaseTest() {

    private fun favoritesRepository(): FavoritesRepository {
        return FavoritesRepository(testAppDatabaseHolder().database)
    }

    private fun createDictionaryRepository(): DictionaryRepository {
        return DictionaryRepository(
            testDataDbManager(),
            testLocalDbManager(),
            favoritesRepository(),
            SettingsRepository(testAppDatabaseHolder().database)
        )
    }

    private fun createClient(baseUrl: String? = null): DictionaryClient {
        val url = baseUrl ?: run {
            val port = TestContext.getCiEnv("TEST_SERVER_PORT") ?: "9090"
            val host = TestContext.testServerHost()
            "http://$host:$port"
        }
        return DictionaryClient(
            platform = testPlatformDbSupport(),
            dictionaryRepository = createDictionaryRepository(),
            localDbManager = testLocalDbManager(),
            dataDbManager = testDataDbManager(),
            baseUrl = url
        )
    }

    @Test
    fun getWord_returnsError_whenRawDataMissing() {
        val platform = testPlatformDbSupport()
        val localMgr = testLocalDbManager()

        // Ensure clean state - no local dictionary data
        val localDictPath = platform.getDatabasePath(LocalDbManager.LOCAL_DICTIONARY_FILENAME)
        if (platform.fileExists(localDictPath)) {
            platform.deleteFile(localDictPath)
        }

        try {
            val client = createClient()

            // Request word that doesn't exist locally
            val results = runBlocking {
                client.getWord(
                    language = Language.ENGLISH,
                    lemma = "nonexistentword12345"
                ).toList()
            }

            // Should have exactly one result with error
            assertEquals(1, results.size, "Should have exactly one emission")
            val result = results.first()
            assertNull(result.card, "Card should be null when raw data is missing")
            assertNotNull(result.error, "Should have error when raw data is missing")
            assertTrue(
                result.error is DictionaryClientException.RawDataMissingException,
                "Error should be RawDataMissingException"
            )
            assertTrue(result.isErrorOnly, "Should be error-only result")
            assertFalse(result.hasData, "Should not have data")
        } finally {
            runBlocking { localMgr.closeAll() }
            if (platform.fileExists(localDictPath)) {
                platform.deleteFile(localDictPath)
            }
        }
    }

    @Test
    fun getWord_returnsLocalData_whenCompleteDataExists() {
        val platform = testPlatformDbSupport()
        val localMgr = testLocalDbManager()

        // Clean local DB files first
        val localDictPath = platform.getDatabasePath(LocalDbManager.LOCAL_DICTIONARY_FILENAME)
        if (platform.fileExists(localDictPath)) {
            platform.deleteFile(localDictPath)
        }

        try {
            // Insert complete test data into local DB
            val localDb = localMgr.openLocalDictionary()
            val q = localDb.dictionaryQueries

            val lemmaId = Uuid.random()
            val lemmaPosId = Uuid.random()
            val senseId = Uuid.random()

            q.insertLemma(lemmaId, "en", "testlocal", "testlocal", 5.0, false)
            q.insertLemmaPos(lemmaPosId, lemmaId, DictionaryPos.NOUN)
            q.insertSense(
                sense_id = senseId,
                lemma_pos_id = lemmaPosId,
                sense_definition = "A test definition",
                learner_level = LearnerLevel.B1,
                frequency = SenseFrequency.MIDDLE,
                semantic_group_id = "group1",
                name_type = null
            )

            val client = createClient()

            // Request word with no translation targets (complete local data)
            val results = runBlocking {
                client.getWord(
                    language = Language.ENGLISH,
                    lemma = "testlocal",
                    translationTargets = emptyList()
                ).toList()
            }

            // Should return single result with complete data
            assertEquals(1, results.size, "Should have exactly one emission for complete local data")
            val result = results.first()
            val card = assertNotNull(result.card, "Should have card data")
            assertNull(result.error, "Should not have error")
            assertFalse(result.isTranslationLoading, "Should not be loading translations")
            assertTrue(result.hasData, "Should have data")
            assertEquals("testlocal", card.lemma, "Lemma should match")
        } finally {
            runBlocking { localMgr.closeAll() }
            if (platform.fileExists(localDictPath)) {
                platform.deleteFile(localDictPath)
            }
        }
    }

    @Test
    fun dictionaryClientException_hierarchy_isCorrect() {
        val rawMissing = DictionaryClientException.RawDataMissingException("word", Language.ENGLISH)
        // Verify properties of RawDataMissingException
        assertEquals("word", rawMissing.lemma)
        assertEquals(Language.ENGLISH, rawMissing.language)
        assertTrue(rawMissing.message!!.contains("word"), "Message should contain lemma")

        val network = DictionaryClientException.NetworkException("connection failed")
        assertEquals("connection failed", network.message)

        val server = DictionaryClientException.ServerException(500, "Internal error")
        assertEquals(500, server.statusCode)
        assertEquals("Internal error", server.body)
        assertTrue(server.message!!.contains("500"), "Message should contain status code")
    }

    @Test
    fun getWord_returnsImmediately_whenWordAndTranslationsExistLocally() {
        val platform = testPlatformDbSupport()
        val localMgr = testLocalDbManager()

        // Clean local DB files first
        val localDictPath = platform.getDatabasePath(LocalDbManager.LOCAL_DICTIONARY_FILENAME)
        val localTransPath = platform.getDatabasePath(LocalDbManager.LOCAL_TRANSLATION_FILENAME)
        if (platform.fileExists(localDictPath)) platform.deleteFile(localDictPath)
        if (platform.fileExists(localTransPath)) platform.deleteFile(localTransPath)

        try {
            // Insert complete test data with translations into local DB
            val localDb = localMgr.openLocalDictionary()
            val q = localDb.dictionaryQueries

            val lemmaId = Uuid.random()
            val lemmaPosId = Uuid.random()
            val senseId = Uuid.random()

            q.insertLemma(lemmaId, "en", "withtranslation", "withtranslation", 5.0, false)
            q.insertLemmaPos(lemmaPosId, lemmaId, DictionaryPos.NOUN)
            q.insertSense(
                sense_id = senseId,
                lemma_pos_id = lemmaPosId,
                sense_definition = "A test definition",
                learner_level = LearnerLevel.B1,
                frequency = SenseFrequency.MIDDLE,
                semantic_group_id = "group1",
                name_type = null
            )

            // Insert translation for Russian
            val localTransDb = localMgr.openLocalTranslation()
            val tq = localTransDb.translationQueries
            tq.insertSenseTargetDefinition(
                sense_id = senseId,
                from_lang_code = "en",
                target_lang_code = "ru",
                definition = "Тестовое определение"
            )
            tq.insertSenseTranslation(
                sense_id = senseId,
                from_lang_code = "en",
                target_lang_code = "ru",
                idx = 0,
                target_lang_word = "перевод",
                target_lang_word_normalized = "перевод",
                target_lang_sense_clarification = null,
                lemma_id = lemmaId,
                lemma_pos_id = lemmaPosId
            )

            val client = createClient()

            // Request word WITH translation targets that exist locally
            val results = runBlocking {
                client.getWord(
                    language = Language.ENGLISH,
                    lemma = "withtranslation",
                    translationTargets = listOf(Language.RUSSIAN)
                ).toList()
            }

            // Should return single result - no server call needed
            assertEquals(1, results.size, "Should have exactly one emission when translations exist locally")
            val result = results.first()
            val card = assertNotNull(result.card, "Should have card data")
            assertNull(result.error, "Should not have error")
            assertFalse(result.isTranslationLoading, "Should not be loading translations")
            assertEquals("withtranslation", card.lemma, "Lemma should match")
            assertFalse(card.online, "Card should not be marked as online")

            // Verify translation exists
            val hasRussianTranslation = card.entries.flatMap { it.senses }
                .any { it.translations.containsKey(Language.RUSSIAN) }
            assertTrue(hasRussianTranslation, "Should have Russian translation")
        } finally {
            runBlocking { localMgr.closeAll() }
            if (platform.fileExists(localDictPath)) platform.deleteFile(localDictPath)
            if (platform.fileExists(localTransPath)) platform.deleteFile(localTransPath)
        }
    }

    @Test
    fun getWord_fetchesTranslations_whenWordExistsButTranslationsMissing() {
        val platform = testPlatformDbSupport()
        val mgr = testDataDbManager()
        val localMgr = testLocalDbManager()

        // Clean local translation DB to ensure no translations exist
        val localTransPath = platform.getDatabasePath(LocalDbManager.LOCAL_TRANSLATION_FILENAME)
        if (platform.fileExists(localTransPath)) platform.deleteFile(localTransPath)

        // Download English dictionary (without translations)
        runBlocking {
            mgr.deleteDictionary(Language.ENGLISH)
            mgr.deleteTranslation(Language.ENGLISH, Language.POLISH)
        }

        val dictPath = runBlocking { mgr.ensureDictionary(Language.ENGLISH) }

        try {
            assertTrue(platform.fileExists(dictPath), "Dictionary file should exist")

            val client = createClient()

            // Use "simultaneously" - present in downloaded English dictionary
            val results = runBlocking {
                client.getWord(
                    language = Language.ENGLISH,
                    lemma = "simultaneously",
                    translationTargets = listOf(Language.POLISH)
                ).toList()
            }

            // Should have at least 2 emissions: local card first, then server response
            assertTrue(results.size >= 2, "Should have at least 2 emissions for progressive loading")

            // First emission should be local data with loading=true
            val firstResult = results.first()
            val firstCard = assertNotNull(firstResult.card, "First emission should have local card")
            assertEquals("simultaneously", firstCard.lemma, "First emission lemma should match")
            assertTrue(firstResult.isTranslationLoading, "First emission should indicate translation loading")

            // Last result should have data and not be loading
            val lastResult = results.last()
            val lastCard = assertNotNull(lastResult.card, "Last emission should have card")
            assertEquals("simultaneously", lastCard.lemma, "Last emission lemma should match")
            assertNull(lastResult.error, "Last emission should not have error")
            assertFalse(lastResult.isTranslationLoading, "Last emission should not be loading")

            // Should have senses
            assertTrue(lastCard.entries.isNotEmpty(), "Should have entries")
            assertTrue(
                lastCard.entries.flatMap { it.senses }.isNotEmpty(),
                "Should have senses"
            )
        } finally {
            runBlocking {
                mgr.deleteDictionary(Language.ENGLISH)
            }
            runBlocking { localMgr.closeAll() }
            if (platform.fileExists(localTransPath)) platform.deleteFile(localTransPath)
        }
    }

    @Test
    fun getWord_fetchesFromServer_whenOnlineOnlyWithoutTranslations() {
        val platform = testPlatformDbSupport()
        val localMgr = testLocalDbManager()

        // Clean local DB
        val localDictPath = platform.getDatabasePath(LocalDbManager.LOCAL_DICTIONARY_FILENAME)
        if (platform.fileExists(localDictPath)) platform.deleteFile(localDictPath)

        try {
            // Insert online_only lemma (raw data only, no senses)
            val localDb = localMgr.openLocalDictionary()
            val q = localDb.dictionaryQueries

            // Use deterministic lemma ID matching ingestion logic
            val lemmaId = JsonIngestionBuilder.generateLemmaId("hello")
            val lemmaPosId = Uuid.random() // Can be random - ingestion looks up by lemma_id

            // Word "hello" should exist on the server
            q.insertLemma(lemmaId, "en", "hello", "hello", 6.0, true) // online_only = true
            q.insertLemmaPos(lemmaPosId, lemmaId, DictionaryPos.INTERJECTION)
            // No senses inserted - this is online_only raw data

            val client = createClient()

            val results = runBlocking {
                client.getWord(
                    language = Language.ENGLISH,
                    lemma = "hello",
                    translationTargets = emptyList() // No translations requested
                ).toList()
            }

            // For online_only, we should NOT emit local first (since it has no real data)
            // Then emit after server returns base data
            assertTrue(results.isNotEmpty(), "Should have at least one emission")

            // The emissions should all have data from server (after ingestion)
            val lastResult = results.last()
            val lastCard = assertNotNull(lastResult.card, "Last emission should have card")
            assertEquals("hello", lastCard.lemma, "Lemma should match")
            assertFalse(lastResult.isTranslationLoading, "Should not be loading (no translations requested)")
            assertNull(lastResult.error, "Should not have error")

            // After ingestion, the card should have senses
            assertTrue(lastCard.entries.isNotEmpty(), "Should have entries after server fetch")
            assertTrue(
                lastCard.entries.flatMap { it.senses }.isNotEmpty(),
                "Should have senses after server fetch"
            )
        } finally {
            runBlocking { localMgr.closeAll() }
            if (platform.fileExists(localDictPath)) platform.deleteFile(localDictPath)
        }
    }

    /**
     * Looks up a non-online-only lemma in the downloaded Russian dictionary.
     * Returns the lemma string, or null if the dictionary is empty.
     */
    private fun findRussianLemma(mgr: com.slovy.slovymovyapp.data.remote.DataDbManager): String? {
        return runBlocking {
            mgr.withDictionaryReadOnly(Language.RUSSIAN) { db ->
                val rows = db.dictionaryQueries
                    .selectLemmasNormalizedLike("ru", "", "\uFFFF", 10)
                    .executeAsList()
                rows.firstOrNull { !it.online_only }?.lemma
            }
        }
    }

    @Test
    fun getWord_fetchesTranslations_ru_to_german() {
        val platform = testPlatformDbSupport()
        val mgr = testDataDbManager()
        val localMgr = testLocalDbManager()

        val localTransPath = platform.getDatabasePath(LocalDbManager.LOCAL_TRANSLATION_FILENAME)
        if (platform.fileExists(localTransPath)) platform.deleteFile(localTransPath)

        runBlocking {
            mgr.deleteDictionary(Language.RUSSIAN)
            mgr.deleteTranslation(Language.RUSSIAN, Language.GERMAN)
        }

        val dictPath = runBlocking { mgr.ensureDictionary(Language.RUSSIAN) }

        try {
            assertTrue(platform.fileExists(dictPath), "Russian dictionary file should exist")
            assertFalse(
                mgr.hasTranslation(Language.RUSSIAN, Language.GERMAN),
                "RU->DE translation should not be downloaded"
            )

            val lemma = assertNotNull(
                findRussianLemma(mgr),
                "Russian dictionary should contain at least one non-online lemma"
            )

            val client = createClient()

            val results = runBlocking {
                client.getWord(
                    language = Language.RUSSIAN,
                    lemma = lemma,
                    translationTargets = listOf(Language.GERMAN)
                ).toList()
            }

            assertTrue(results.isNotEmpty(), "Should have at least one emission")

            // First emission should be the local card (from downloaded dictionary)
            val firstResult = results.first()
            val firstCard = assertNotNull(firstResult.card, "First emission should have local card")
            assertEquals(lemma, firstCard.lemma, "Lemma should match")

            // If multiple emissions, first should show translation loading
            if (results.size >= 2) {
                assertTrue(
                    firstResult.isTranslationLoading,
                    "First emission should indicate translation loading"
                )
            }

            // Last result should not be loading and must contain German translations
            val lastResult = results.last()
            val lastCard = assertNotNull(lastResult.card, "Last emission should have card")
            assertFalse(lastResult.isTranslationLoading, "Last emission should not be loading translations")

            val allTranslationLanguages = lastCard.entries
                .flatMap { it.senses }
                .flatMap { sense -> sense.translations.keys + sense.targetLangDefinitions.keys }
                .toSet()
            assertTrue(
                Language.GERMAN in allTranslationLanguages,
                "Last card should contain German translations but found: $allTranslationLanguages"
            )
        } finally {
            runBlocking { mgr.deleteDictionary(Language.RUSSIAN) }
            runBlocking { localMgr.closeAll() }
            if (platform.fileExists(localTransPath)) platform.deleteFile(localTransPath)
        }
    }

    @Test
    fun getWord_fetchesTranslations_ru_to_english_and_german() {
        val platform = testPlatformDbSupport()
        val mgr = testDataDbManager()
        val localMgr = testLocalDbManager()

        val localTransPath = platform.getDatabasePath(LocalDbManager.LOCAL_TRANSLATION_FILENAME)
        if (platform.fileExists(localTransPath)) platform.deleteFile(localTransPath)

        runBlocking {
            mgr.deleteDictionary(Language.RUSSIAN)
            mgr.deleteTranslation(Language.RUSSIAN, Language.ENGLISH)
            mgr.deleteTranslation(Language.RUSSIAN, Language.GERMAN)
        }

        val dictPath = runBlocking { mgr.ensureDictionary(Language.RUSSIAN) }

        try {
            assertTrue(platform.fileExists(dictPath), "Russian dictionary file should exist")
            assertFalse(
                mgr.hasTranslation(Language.RUSSIAN, Language.ENGLISH),
                "RU->EN translation should not be downloaded"
            )
            assertFalse(
                mgr.hasTranslation(Language.RUSSIAN, Language.GERMAN),
                "RU->DE translation should not be downloaded"
            )

            val lemma = assertNotNull(
                findRussianLemma(mgr),
                "Russian dictionary should contain at least one non-online lemma"
            )

            val client = createClient()

            val results = runBlocking {
                client.getWord(
                    language = Language.RUSSIAN,
                    lemma = lemma,
                    translationTargets = listOf(Language.ENGLISH, Language.GERMAN)
                ).toList()
            }

            assertTrue(results.isNotEmpty(), "Should have at least one emission")

            val firstResult = results.first()
            val firstCard = assertNotNull(firstResult.card, "First emission should have local card")
            assertEquals(lemma, firstCard.lemma, "Lemma should match")

            // With two translation targets, first emission should indicate loading
            if (results.size >= 2) {
                assertTrue(
                    firstResult.isTranslationLoading,
                    "First emission should indicate translation loading for multiple targets"
                )
            }

            // Last result should have complete data with both translation languages
            val lastResult = results.last()
            val lastCard = assertNotNull(lastResult.card, "Last emission should have card")
            assertEquals(lemma, lastCard.lemma, "Last emission lemma should match")
            assertFalse(lastResult.isTranslationLoading, "Last emission should not be loading")

            val allTranslationLanguages = lastCard.entries
                .flatMap { it.senses }
                .flatMap { sense -> sense.translations.keys + sense.targetLangDefinitions.keys }
                .toSet()
            assertTrue(
                Language.ENGLISH in allTranslationLanguages,
                "Last card should contain English translations but found: $allTranslationLanguages"
            )
            assertTrue(
                Language.GERMAN in allTranslationLanguages,
                "Last card should contain German translations but found: $allTranslationLanguages"
            )
        } finally {
            runBlocking { mgr.deleteDictionary(Language.RUSSIAN) }
            runBlocking { localMgr.closeAll() }
            if (platform.fileExists(localTransPath)) platform.deleteFile(localTransPath)
        }
    }

    @Test
    fun getWord_fetchesBothWordAndTranslations_whenOnlineOnlyWithTranslations() {
        val platform = testPlatformDbSupport()
        val localMgr = testLocalDbManager()

        // Clean local DBs
        val localDictPath = platform.getDatabasePath(LocalDbManager.LOCAL_DICTIONARY_FILENAME)
        val localTransPath = platform.getDatabasePath(LocalDbManager.LOCAL_TRANSLATION_FILENAME)
        if (platform.fileExists(localDictPath)) platform.deleteFile(localDictPath)
        if (platform.fileExists(localTransPath)) platform.deleteFile(localTransPath)

        try {
            // Insert online_only lemma (raw data only)
            val localDb = localMgr.openLocalDictionary()
            val q = localDb.dictionaryQueries

            // Use deterministic lemma ID matching ingestion logic
            // Use "zooidiophilous" - a rare word without pre-stored translations
            val lemmaId = JsonIngestionBuilder.generateLemmaId("zooidiophilous")
            val lemmaPosId = Uuid.random() // Can be random - ingestion looks up by lemma_id

            q.insertLemma(lemmaId, "en", "zooidiophilous", "zooidiophilous", 1.0, true) // online_only = true
            q.insertLemmaPos(lemmaPosId, lemmaId, DictionaryPos.ADJECTIVE)
            // No senses - online_only raw data

            val client = createClient()

            val results = runBlocking {
                client.getWord(
                    language = Language.ENGLISH,
                    lemma = "zooidiophilous",
                    translationTargets = listOf(Language.RUSSIAN)
                ).toList()
            }

            // For online_only with translations, we expect:
            // 1. Base data from server (isTranslationLoading=true)
            // 2. Translated data from server (isTranslationLoading=false)
            assertTrue(results.isNotEmpty(), "Should have at least one emission")

            // Check first result after base stage
            val firstServerResult = results.firstOrNull { it.card != null && it.card.entries.isNotEmpty() }
            if (firstServerResult != null && results.size > 1) {
                // If we got multiple results, first server result should indicate more coming
                val idx = results.indexOf(firstServerResult)
                if (idx < results.lastIndex) {
                    assertTrue(
                        firstServerResult.isTranslationLoading,
                        "First server result should indicate more translations coming"
                    )
                }
            }

            // Last result should have complete data
            val lastResult = results.last()
            val lastCard = assertNotNull(lastResult.card, "Last emission should have card")
            assertEquals("zooidiophilous", lastCard.lemma, "Lemma should match")
            assertNull(lastResult.error, "Should not have error")
            assertFalse(lastResult.isTranslationLoading, "Last emission should not be loading")

            // Should have senses after server fetch
            assertTrue(lastCard.entries.isNotEmpty(), "Should have entries")
            assertTrue(
                lastCard.entries.flatMap { it.senses }.isNotEmpty(),
                "Should have senses after server fetch"
            )
        } finally {
            runBlocking { localMgr.closeAll() }
            if (platform.fileExists(localDictPath)) platform.deleteFile(localDictPath)
            if (platform.fileExists(localTransPath)) platform.deleteFile(localTransPath)
        }
    }
}
