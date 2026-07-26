package com.slovy.slovymovyapp.ui

import androidx.lifecycle.ViewModelStore
import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.favorites.FavoritesRepository
import com.slovy.slovymovyapp.data.lists.ListsService
import com.slovy.slovymovyapp.data.lists.WordList
import com.slovy.slovymovyapp.data.lists.WordListSense
import com.slovy.slovymovyapp.data.lists.WordListsRepository
import com.slovy.slovymovyapp.data.local.LocalDbManager
import com.slovy.slovymovyapp.data.remote.DictionaryRepository
import com.slovy.slovymovyapp.data.remote.LemmaRecovery
import com.slovy.slovymovyapp.data.remote.ListsClient
import com.slovy.slovymovyapp.data.settings.Setting
import com.slovy.slovymovyapp.data.settings.SettingsRepository
import com.slovy.slovymovyapp.speech.FakeSpeechPlayer
import com.slovy.slovymovyapp.speech.VoiceFilterHelper
import com.slovy.slovymovyapp.test.BaseTest
import com.slovy.slovymovyapp.test.IgnoreIos
import com.slovy.slovymovyapp.test.TestContext
import com.slovy.slovymovyapp.test.testPlatformDbSupport
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

/**
 * Covers the list-detail translation repair: a sense that resolves from the downloaded
 * dictionary but has no data for a requested translation language must be fetched from the
 * server (faked here through [LemmaRecovery]'s lambda constructor) and its row updated once
 * the translations land in the local translation DB.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@IgnoreIos
class ListDetailViewModelTest : BaseTest() {

    private companion object {
        const val LIST_ID = "test_translation_gap_list"
        const val LEMMA = "simultaneously"
        val LANGUAGE = Language.ENGLISH
        val TARGET = Language.RUSSIAN
    }

    private val viewModelStore = ViewModelStore()

    private data class FetchCall(
        val language: Language,
        val lemma: String,
        val translationTargets: List<Language>,
    )

    private data class SenseRef(
        val lemmaId: Uuid,
        val lemmaPosId: Uuid,
        val senseId: Uuid,
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        viewModelStore.clear()
        Dispatchers.resetMain()
        runBlocking {
            SettingsRepository(testAppDatabaseHolder().database).deleteById(Setting.Name.LANGUAGE)
        }
        deleteLocalTranslationDb()
    }

    private fun deleteLocalTranslationDb() {
        val platform = testPlatformDbSupport()
        runBlocking { testLocalDbManager().closeAll() }
        val localTransPath = platform.getDatabasePath(LocalDbManager.LOCAL_TRANSLATION_FILENAME)
        if (platform.fileExists(localTransPath)) {
            platform.deleteFile(localTransPath)
        }
    }

    /**
     * Downloads the read-only dictionary, removes every source of RU translations, configures
     * RU as the translation target, and stages a word list around [senseCount] senses of
     * [lemma]. Returns the senses the list points at.
     */
    private suspend fun stageListWithTranslationGap(lemma: String, senseCount: Int): List<SenseRef> {
        val mgr = testDataDbManager()
        mgr.ensureDictionary(LANGUAGE)
        mgr.deleteTranslation(LANGUAGE, TARGET)
        deleteLocalTranslationDb()

        val senseRefs = mgr.withDictionaryReadOnly(LANGUAGE) { roDb ->
            val lemmaRow = roDb.dictionaryQueries.selectLemmasByWord(LANGUAGE.code, lemma)
                .executeAsList().firstOrNull()
            assertNotNull(lemmaRow, "Fixture dictionary must contain '$lemma'")
            val refs = roDb.dictionaryQueries.selectLemmaPosIdByLemmaId(lemmaRow.id)
                .executeAsList()
                .flatMap { lemmaPosId ->
                    roDb.dictionaryQueries.selectSensesByLemmaPosId(lemmaPosId).executeAsList()
                        .map { SenseRef(lemmaRow.id, lemmaPosId, it.sense_id) }
                }
            assertTrue(
                refs.size >= senseCount,
                "Fixture lemma '$lemma' must have at least $senseCount senses but has ${refs.size}",
            )
            refs.take(senseCount)
        }

        val settingsRepo = SettingsRepository(testAppDatabaseHolder().database)
        settingsRepo.insert(
            Setting(id = Setting.Name.LANGUAGE, value = JsonArray(listOf(JsonPrimitive(TARGET.code))))
        )

        WordListsRepository(testAppDatabaseHolder().database).replaceLists(
            LANGUAGE,
            version = "v-test",
            lists = listOf(
                WordList(
                    id = LIST_ID,
                    title = mapOf("en" to "Translation gap"),
                    subtitle = emptyMap(),
                    labels = emptyMap(),
                    senses = senseRefs.map {
                        WordListSense(senseId = it.senseId.toString(), lemma = lemma, language = LANGUAGE)
                    },
                    iconSvg = null,
                )
            ),
        )
        return senseRefs
    }

    private fun insertLocalTranslation(senseRef: SenseRef, word: String) {
        val tq = testLocalDbManager().openLocalTranslation().translationQueries
        tq.insertSenseTargetDefinition(
            sense_id = senseRef.senseId,
            from_lang_code = LANGUAGE.code,
            target_lang_code = TARGET.code,
            definition = "Определение для теста",
        )
        tq.insertSenseTranslation(
            sense_id = senseRef.senseId,
            from_lang_code = LANGUAGE.code,
            target_lang_code = TARGET.code,
            idx = 0,
            target_lang_word = word,
            target_lang_word_normalized = word.lowercase(),
            target_lang_sense_clarification = null,
            lemma_id = senseRef.lemmaId,
            lemma_pos_id = senseRef.lemmaPosId,
        )
    }

    private fun dictionaryRepository(): DictionaryRepository {
        return DictionaryRepository(
            testDataDbManager(),
            testLocalDbManager(),
            FavoritesRepository(testAppDatabaseHolder().database),
            SettingsRepository(testAppDatabaseHolder().database),
        )
    }

    /**
     * A [LemmaRecovery] whose server fetch is [fetchLemma]; everything else is wired to the
     * real repository exactly like the production constructor.
     */
    private fun lemmaRecovery(
        repo: DictionaryRepository,
        fetchLemma: suspend (Language, String, List<Language>) -> Unit,
    ): LemmaRecovery = LemmaRecovery(
        itemsProvider = { emptyList() },
        hasDownloadedDictionary = { true },
        downloadedLemmasNeedingRecovery = { _, lemmas -> lemmas },
        downloadedSensesNeedingTranslationRecovery = { _, _, _ -> emptySet() },
        translationTargetsProvider = { language -> repo.defaultTranslationTargets(language) },
        fetchLemma = fetchLemma,
        resolveSenses = { language, lemma, senseIds ->
            repo.getSenses(
                language = language,
                lemma = lemma,
                senseIds = senseIds,
                translationTargets = repo.defaultTranslationTargets(language),
            )
        },
    )

    private fun createViewModel(repo: DictionaryRepository, recovery: LemmaRecovery): ListDetailViewModel {
        val platform = testPlatformDbSupport()
        // Unroutable server: the list is staged in the DB, so no lists request must ever fire.
        val listsService = ListsService(
            WordListsRepository(testAppDatabaseHolder().database),
            ListsClient(platform, "http://${TestContext.testServerHost()}:59997"),
        )
        val vm = ListDetailViewModel(
            listId = LIST_ID,
            language = LANGUAGE,
            repository = repo,
            favoritesRepository = FavoritesRepository(testAppDatabaseHolder().database),
            listsService = listsService,
            lemmaRecovery = recovery,
            speechPlayer = FakeSpeechPlayer(),
            voiceFilterHelper = VoiceFilterHelper(SettingsRepository(testAppDatabaseHolder().database)),
            onFavoriteChanged = {},
        )
        viewModelStore.put("test", vm)
        return vm
    }

    private suspend fun awaitCondition(
        message: String,
        timeout: Duration = 30.seconds,
        condition: () -> Boolean,
    ) = withContext(Dispatchers.Default) {
        withTimeout(timeout) {
            while (!condition()) delay(25.milliseconds)
        }
        assertTrue(condition(), message)
    }

    private fun singleItem(vm: ListDetailViewModel): ListWordItem {
        assertEquals(1, vm.state.items.size, "Staged list must resolve to exactly one row")
        return vm.state.items.single()
    }

    @Test
    fun missingTranslation_isFetchedAndRowRepaired() = runBlocking {
        val senseRef = stageListWithTranslationGap(LEMMA, senseCount = 1).single()
        val repo = dictionaryRepository()
        val fetches = mutableListOf<FetchCall>()
        val recovery = lemmaRecovery(repo) { language, lemma, targets ->
            fetches += FetchCall(language, lemma, targets)
            // Mimic DictionaryClient's server fetch: ingest translations into the local
            // translation DB and invalidate the cached (translation-less) sense.
            insertLocalTranslation(senseRef, word = "одновременно")
            repo.invalidateSenses(setOf(senseRef.senseId.toString()))
        }

        val vm = createViewModel(repo, recovery)

        awaitCondition("Row must end up with the repaired $TARGET translation") {
            val item = vm.state.items.singleOrNull()
            item?.sense?.translations?.get(TARGET)?.isNotEmpty() == true
        }
        val item = singleItem(vm)
        assertEquals(
            "одновременно",
            item.sense?.translations?.get(TARGET)?.first()?.targetLangWord,
            "Row must render the translation ingested by the repair fetch",
        )
        assertNull(item.error, "A successful repair must not surface an error")
        assertEquals(
            listOf(FetchCall(LANGUAGE, LEMMA, listOf(TARGET))),
            fetches,
            "Exactly one repair fetch must go out, for the list's lemma with the configured target",
        )
    }

    @Test
    fun repairInFlight_marksRowTranslationLoading() = runBlocking {
        val senseRef = stageListWithTranslationGap(LEMMA, senseCount = 1).single()
        val repo = dictionaryRepository()
        // Holds the fetch open so the in-flight state is observable rather than a race.
        val releaseFetch = CompletableDeferred<Unit>()
        val recovery = lemmaRecovery(repo) { _, _, _ ->
            releaseFetch.await()
            insertLocalTranslation(senseRef, word = "одновременно")
            repo.invalidateSenses(setOf(senseRef.senseId.toString()))
        }

        val vm = createViewModel(repo, recovery)

        awaitCondition("Row must report translationLoading while the repair fetch is in flight") {
            vm.state.items.singleOrNull()?.translationLoading == true
        }
        assertNull(
            singleItem(vm).error,
            "An in-flight repair must not surface an error on the row",
        )
        releaseFetch.complete(Unit)

        awaitCondition("translationLoading must clear once the repaired translation arrives") {
            val item = vm.state.items.singleOrNull()
            item?.translationLoading == false &&
                item.sense?.translations?.get(TARGET)?.isNotEmpty() == true
        }
    }

    @Test
    fun failedRepair_clearsTranslationLoading() = runBlocking {
        stageListWithTranslationGap(LEMMA, senseCount = 1)
        val repo = dictionaryRepository()
        val recovery = lemmaRecovery(repo) { _, _, _ ->
            throw RuntimeException("simulated server failure")
        }

        val vm = createViewModel(repo, recovery)

        awaitCondition("A failed repair must still clear the row's translationLoading flag") {
            val item = vm.state.items.singleOrNull()
            item?.sense != null && item.translationLoading == false
        }
        // Give a late/stuck flag a chance to appear before asserting it stays clear.
        delay(300.milliseconds)
        assertEquals(
            false,
            singleItem(vm).translationLoading,
            "translationLoading must not remain set after the repair failed",
        )
    }

    @Test
    fun presentTranslation_doesNotTriggerFetch() = runBlocking {
        val senseRef = stageListWithTranslationGap(LEMMA, senseCount = 1).single()
        insertLocalTranslation(senseRef, word = "одновременно")
        val repo = dictionaryRepository()
        val fetches = mutableListOf<FetchCall>()
        val recovery = lemmaRecovery(repo) { language, lemma, targets ->
            fetches += FetchCall(language, lemma, targets)
        }

        val vm = createViewModel(repo, recovery)

        awaitCondition("Row must load its sense with the local translation") {
            val item = vm.state.items.singleOrNull()
            item?.sense?.translations?.get(TARGET)?.isNotEmpty() == true
        }
        // Give any spurious background repair a moment to fire before asserting it didn't.
        delay(300.milliseconds)
        assertEquals(
            emptyList(),
            fetches,
            "A row whose translation resolves locally must not trigger a server fetch",
        )
    }

    @Test
    fun failedTranslationFetch_keepsLocalContentWithoutError() = runBlocking {
        val senseRef = stageListWithTranslationGap(LEMMA, senseCount = 1).single()
        val repo = dictionaryRepository()
        val fetches = mutableListOf<FetchCall>()
        val recovery = lemmaRecovery(repo) { language, lemma, targets ->
            fetches += FetchCall(language, lemma, targets)
            throw RuntimeException("simulated server failure")
        }

        val vm = createViewModel(repo, recovery)

        awaitCondition("The repair fetch must be attempted") { fetches.size == 1 }
        // Let the failed recovery finish reporting before asserting the row survived it.
        delay(300.milliseconds)
        val item = singleItem(vm)
        assertNotNull(item.sense, "Row must keep the locally resolved sense after a failed repair")
        assertNull(item.error, "A failed translation repair must not surface an error on the row")
        assertTrue(
            item.sense?.translations?.get(TARGET).isNullOrEmpty(),
            "Sense stays without the translation when the fetch fails",
        )
        assertEquals(senseRef.senseId.toString(), item.senseId)
    }

    @Test
    fun cachedTranslationlessSense_isRepairedOnScreenLoad() = runBlocking {
        val senseRef = stageListWithTranslationGap(LEMMA, senseCount = 1).single()
        val repo = dictionaryRepository()
        // Poison the sense cache the way a previous visit without connectivity would:
        // the sense resolves and is cached without the RU translation.
        val prefetch = repo.getSenses(
            language = LANGUAGE,
            lemma = LEMMA,
            senseIds = setOf(senseRef.senseId.toString()),
            translationTargets = listOf(TARGET),
        )
        val cachedSense = prefetch.getValue(senseRef.senseId.toString()).sense
        assertNotNull(cachedSense, "Precondition: the sense must resolve from the dictionary")
        assertTrue(
            cachedSense.sense.translations[TARGET].isNullOrEmpty(),
            "Precondition: the cached sense must lack the $TARGET translation",
        )

        val fetches = mutableListOf<FetchCall>()
        val recovery = lemmaRecovery(repo) { language, lemma, targets ->
            fetches += FetchCall(language, lemma, targets)
            insertLocalTranslation(senseRef, word = "одновременно")
            repo.invalidateSenses(setOf(senseRef.senseId.toString()))
        }

        val vm = createViewModel(repo, recovery)

        awaitCondition("Cache-seeded row must be repaired on screen load") {
            val item = vm.state.items.singleOrNull()
            item?.sense?.translations?.get(TARGET)?.isNotEmpty() == true
        }
        assertEquals(
            listOf(FetchCall(LANGUAGE, LEMMA, listOf(TARGET))),
            fetches,
            "The load-time gap pass must fetch the cached translation-less sense exactly once",
        )
    }

    @Test
    fun multipleSensesOfOneLemma_areRepairedWithSingleFetch() = runBlocking {
        val lemma = "directions"
        val senseRefs = stageListWithTranslationGap(lemma, senseCount = 2)
        val repo = dictionaryRepository()
        // Seed the cache with both translation-less senses so the load-time pass batches them
        // into one recoverSenses call, which groups by lemma.
        repo.getSenses(
            language = LANGUAGE,
            lemma = lemma,
            senseIds = senseRefs.map { it.senseId.toString() }.toSet(),
            translationTargets = listOf(TARGET),
        )
        val fetches = mutableListOf<FetchCall>()
        val recovery = lemmaRecovery(repo) { language, fetchedLemma, targets ->
            fetches += FetchCall(language, fetchedLemma, targets)
            senseRefs.forEach { insertLocalTranslation(it, word = "направления") }
            repo.invalidateSenses(senseRefs.map { it.senseId.toString() }.toSet())
        }

        val vm = createViewModel(repo, recovery)

        awaitCondition("Both rows of the shared lemma must be repaired") {
            vm.state.items.size == 2 && vm.state.items.all {
                it.sense?.translations?.get(TARGET)?.isNotEmpty() == true
            }
        }
        assertEquals(
            listOf(FetchCall(LANGUAGE, lemma, listOf(TARGET))),
            fetches,
            "Two gap senses of one lemma must be repaired by a single grouped fetch",
        )
    }
}
