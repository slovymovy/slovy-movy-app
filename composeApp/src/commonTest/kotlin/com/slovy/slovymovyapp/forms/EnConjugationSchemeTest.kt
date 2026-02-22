package com.slovy.slovymovyapp.forms

import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.dictionary.DictionaryPos
import com.slovy.slovymovyapp.test.BaseTest
import com.slovy.slovymovyapp.test.IgnoreIos
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

@IgnoreIos
class EnConjugationSchemeTest : BaseTest() {

    @Test
    fun englishSnapshots_matchExpectedTables() = runBlocking {
        val (mgr, repo) = createSnapshotRepository()
        try {
            mgr.ensureDictionary(Language.ENGLISH)

            assertEquals(
                expectedEnNounBook,
                resolveFormsSnapshot(repo, Language.ENGLISH, "book", DictionaryPos.NOUN),
                "Resolved EN noun views for 'book' changed"
            )
            assertEquals(
                expectedEnVerbTest,
                resolveFormsSnapshot(repo, Language.ENGLISH, "test", DictionaryPos.VERB),
                "Resolved EN verb views for 'test' changed"
            )
            assertEquals(
                expectedEnAdjectiveIrrelevant,
                resolveFormsSnapshot(repo, Language.ENGLISH, "irrelevant", DictionaryPos.ADJECTIVE),
                "Resolved EN adjective views for 'irrelevant' changed"
            )
        } finally {
            mgr.deleteDictionary(Language.ENGLISH)
        }
    }

    private val expectedEnNounBook = mapOf(
        "en_noun:short" to listOf(
            listOf(null, "book"),
            listOf(null, "books")
        )
    )

    private val expectedEnVerbTest = mapOf(
        "en_verb:short" to listOf(
            listOf(null, "test"),
            listOf(null, "tests"),
            listOf(null, "tested"),
            listOf(null, "tested"),
            listOf(null, "testing")
        )
    )

    private val expectedEnAdjectiveIrrelevant = mapOf(
        "en_adjective:short" to listOf(
            listOf(null, null),
            listOf("more irrelevant", "most irrelevant")
        )
    )
}
