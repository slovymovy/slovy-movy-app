package com.slovy.slovymovyapp.data.remote

import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.dictionary.DictionaryPos
import com.slovy.slovymovyapp.data.dictionary.FormSource
import com.slovy.slovymovyapp.data.favorites.FavoritesRepository
import com.slovy.slovymovyapp.data.local.LocalDbManager
import com.slovy.slovymovyapp.data.settings.SettingsRepository
import com.slovy.slovymovyapp.test.BaseTest
import com.slovy.slovymovyapp.test.testPlatformDbSupport
import kotlinx.coroutines.runBlocking
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/**
 * Tests for [DictionaryRepository.lookupTokens] match ranking against a seeded local
 * dictionary DB: exact lemma > exact form > accent-insensitive lemma > accent-insensitive
 * form, with corpus frequency only as the tie-break.
 */
class LookupTokensTest : BaseTest() {

    private lateinit var localMgr: LocalDbManager
    private lateinit var repository: DictionaryRepository

    @BeforeTest
    fun setUpRepository() {
        val platform = testPlatformDbSupport()
        localMgr = testLocalDbManager()
        val localDictPath = platform.getDatabasePath(LocalDbManager.LOCAL_DICTIONARY_FILENAME)
        runBlocking { localMgr.closeAll() }
        if (platform.fileExists(localDictPath)) platform.deleteFile(localDictPath)

        val appDb = testAppDatabaseHolder().database
        repository = DictionaryRepository(
            testDataDbManager(),
            localMgr,
            FavoritesRepository(appDb),
            SettingsRepository(appDb)
        )
    }

    private fun insertLemma(langCode: String, lemma: String, lemmaNormalized: String, zipf: Double): Uuid {
        val lemmaId = Uuid.random()
        localMgr.openLocalDictionary().dictionaryQueries.insertLemma(lemmaId, langCode, lemma, lemmaNormalized, zipf, false)
        return lemmaId
    }

    private fun insertForm(lemmaId: Uuid, form: String, formNormalized: String) {
        val q = localMgr.openLocalDictionary().dictionaryQueries
        val posId = Uuid.random()
        q.insertLemmaPos(posId, lemmaId, DictionaryPos.NOUN)
        q.insertForm(Uuid.random(), posId, form, formNormalized, FormSource.NATIVE)
    }

    private fun lookup(words: List<String>, language: Language): Map<String, DictionaryRepository.TokenResult> =
        runBlocking { repository.lookupTokens(words, language) }

    @Test
    fun exact_lemma_beats_exact_form_of_a_more_frequent_lemma() {
        val watsId = insertLemma("nl", "wats", "wats", 5.5)
        insertForm(watsId, "wat", "wat")
        insertLemma("nl", "wat", "wat", 4.0)

        val results = lookup(listOf("wat", "Wat"), Language.DUTCH)
        assertEquals("wat", results.getValue("wat").lemma, "'wat' must resolve to its own lemma, not a form of 'wats'")
        assertEquals("wat", results.getValue("Wat").lemma, "exact matching must ignore case")
    }

    @Test
    fun exact_spelling_beats_accent_insensitive_match_regardless_of_frequency() {
        insertLemma("pl", "łaska", "laska", 3.5)
        insertLemma("pl", "laska", "laska", 4.5)

        val results = lookup(listOf("łaska", "laska"), Language.POLISH)
        assertEquals("łaska", results.getValue("łaska").lemma, "'łaska' must keep its own spelling despite lower frequency")
        assertEquals("laska", results.getValue("laska").lemma, "'laska' must keep its own spelling")
    }

    @Test
    fun inflected_form_resolves_to_its_parent_lemma() {
        val lopenId = insertLemma("nl", "lopen", "lopen", 5.0)
        insertForm(lopenId, "liepen", "liepen")

        val results = lookup(listOf("liepen"), Language.DUTCH)
        assertEquals("lopen", results.getValue("liepen").lemma)
    }

    @Test
    fun accent_insensitive_lemma_match_is_used_when_no_exact_spelling_exists() {
        insertLemma("nl", "café", "cafe", 4.2)

        val results = lookup(listOf("cafe"), Language.DUTCH)
        assertEquals("café", results.getValue("cafe").lemma)
    }

    @Test
    fun higher_frequency_wins_between_equal_rank_form_matches() {
        val bootId = insertLemma("nl", "boot", "boot", 5.0)
        insertForm(bootId, "boten", "boten")
        val botId = insertLemma("nl", "bot", "bot", 3.0)
        insertForm(botId, "boten", "boten")

        val results = lookup(listOf("boten"), Language.DUTCH)
        assertEquals("boot", results.getValue("boten").lemma, "at equal match rank the more frequent lemma must win")
    }

    @Test
    fun unknown_words_are_absent_from_the_result() {
        insertLemma("nl", "stad", "stad", 5.0)

        val results = lookup(listOf("stad", "xyzzy"), Language.DUTCH)
        assertEquals("stad", results.getValue("stad").lemma)
        assertNull(results["xyzzy"], "a word with no dictionary entry must not be in the result map")
    }
}
