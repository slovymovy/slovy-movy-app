package com.slovy.slovymovyapp.forms

import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.dictionary.DictionaryPos
import com.slovy.slovymovyapp.test.BaseTest
import com.slovy.slovymovyapp.test.IgnoreIos
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

@IgnoreIos
class PlConjugationSchemeTest : BaseTest() {

    @Test
    fun polishSnapshots_matchExpectedTables() = runBlocking {
        val (mgr, repo) = createSnapshotRepository()
        try {
            mgr.ensureDictionary(Language.POLISH)

            assertEquals(
                expectedPlNounTestowanie,
                resolveFormsSnapshot(repo, Language.POLISH, "testowanie", DictionaryPos.NOUN),
                "Resolved PL noun views for 'testowanie' changed"
            )
            assertEquals(
                expectedPlVerbPodawac,
                resolveFormsSnapshot(repo, Language.POLISH, "podawać", DictionaryPos.VERB),
                "Resolved PL verb views for 'podawać' changed"
            )
            assertEquals(
                expectedPlAdjectiveOstatni,
                resolveFormsSnapshot(repo, Language.POLISH, "ostatni", DictionaryPos.ADJECTIVE),
                "Resolved PL adjective views for 'ostatni' changed"
            )
        } finally {
            mgr.deleteDictionary(Language.POLISH)
        }
    }

    private val expectedPlNounTestowanie = mapOf(
        "pl_noun:full" to listOf(
            listOf(null, null, null),
            listOf(null, null, null),
            listOf(null, "testowania", null),
            listOf(null, "testowaniu", null),
            listOf(null, null, null),
            listOf(null, "testowaniem", null),
            listOf(null, "testowaniu", null),
            listOf(null, null, null)
        )
    )

    private val expectedPlVerbPodawac = mapOf(
        "pl_verb:full" to listOf(
            listOf(null, "podawać się"),
            listOf(null),
            listOf(null, null, null, null, null, null),
            listOf(null, null, null, null, null, null),
            listOf(null, null, null, null, null, null),
            listOf(null, null, null, null, null, null),
            listOf(null, null),
            listOf(null),
            listOf(null, null, null, null, null, null),
            listOf(null, null, null, null, null, null),
            listOf(null, null, null, null, null, null),
            listOf(null, null),
            listOf(null),
            listOf(null, null, null, null, null, null),
            listOf(null, null, null, null, null, null),
            listOf(null, null, null, null, null, null),
            listOf(null, null),
            listOf(null),
            listOf(null, "byłbym podawał", "byłabym podawała", "byłobym podawało", null, null),
            listOf(null, "byłbyś podawał", "byłabyś podawała", "byłobyś podawało", null, null),
            listOf(null, "byłby podawał", "byłaby podawała", "byłoby podawało", null, null),
            listOf(null, "byliby podawali"),
            listOf(null),
            listOf(null, null),
            listOf(null, null, null, null),
            listOf(null, null),
            listOf(null, "niepodający", "niepodająca", "niepodające", null, null),
            listOf(null, "niepodawani", "niepodawana", "niepodawane", null, null),
            listOf(null, null),
            listOf(null, "niepodawanie")
        )
    )

    private val expectedPlAdjectiveOstatni = mapOf(
        "pl_adjective:full" to listOf(
            listOf(null, null, null, null, null, null, null),
            listOf(null, null, null, "ostatnia", "ostatnie", null, "ostatnie"),
            listOf(null, "ostatniego", "ostatniej", "ostatniego", "ostatnich"),
            listOf(null, "ostatniemu", "ostatniej", "ostatniemu", "ostatnim"),
            listOf(null, "ostatniego", null, "ostatnią", "ostatnie", null, "ostatnie"),
            listOf(null, "ostatnim", "ostatnią", "ostatnim", "ostatnimi"),
            listOf(null, "ostatnim", "ostatniej", "ostatnim", "ostatnich")
        )
    )
}
