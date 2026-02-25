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
                expectedPlNounDom,
                resolveFormsSnapshot(repo, Language.POLISH, "dom", DictionaryPos.NOUN),
                "Resolved PL noun views for 'dom' changed"
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

    private val expectedPlNounDom = mapOf(
        "pl_noun:full" to listOf(
            listOf(null, null, null),
            listOf(null, "dom", "domy"),
            listOf(null, "domu", "domów"),
            listOf(null, "domowi", "domom"),
            listOf(null, "dom", "domy"),
            listOf(null, "domem", "domami"),
            listOf(null, "domie", "domach"),
            listOf(null, "domie", "domy")
        )
    )

    private val expectedPlVerbPodawac = mapOf(
        "pl_verb:full" to listOf(
            listOf(null, "podawać"),
            listOf(null),
            listOf(null, null, null),
            listOf(null, "podaję", "podajemy"),
            listOf(null, "podajesz", "podajecie"),
            listOf(null, "podaje", "podają"),
            listOf(null, "podaje się"),
            listOf(null, null, null, null, null, null),
            listOf(null),
            listOf(null, "podawałem", "podawałam", null, "podawaliśmy", "podawałyśmy"),
            listOf(null, "podawałeś", "podawałaś", null, "podawaliście", "podawałyście"),
            listOf(null, "podawał", "podawała", "podawało", "podawali", "podawały"),
            listOf(null, "podawano"),
            listOf(null),
            listOf(null, "będę podawać", "będę podawać", null, "będziemy podawali", "będziemy podawać"),
            listOf(null, "będziesz podawać", "będziesz podawać", null, "będziecie podawali", "będziecie podawać"),
            listOf(null, "będzie podawać", "będzie podawać", "będzie podawać", "będą podawali", "będą podawać"),
            listOf(null),
            listOf(null, "byłbym podawał", "byłabym podawała", null, "bylibyśmy podawali", "byłybyśmy podawały"),
            listOf(null, "byłbyś podawał", "byłabyś podawała", null, "bylibyście podawali", "byłybyście podawały"),
            listOf(null, "by podawał", "by podawała", "by podawało", "by podawali", "by podawały"),
            listOf(null, "podawano by"),
            listOf(null),
            listOf(null, null, null),
            listOf(null, null, "podawajmy"),
            listOf(null, "podawaj", "podawajcie"),
            listOf(null, "niech podaje", "niech podają"),
            listOf(null, "podający", "podająca", "podające", "podający", "podające"),
            listOf(null, "podawany", "podawana", "podawane", "podawani", "podawane"),
            listOf(null, "nie podając"),
            listOf(null, "niepodawanie")
        )
    )

    private val expectedPlAdjectiveOstatni = mapOf(
        "pl_adjective:full" to listOf(
            listOf(null, null, null, null, null, null, null),
            listOf(null, null, null, "ostatnia", "ostatnie", null, "ostatnie"),
            listOf(null, "ostatniego", "ostatniej", "ostatniego", "ostatnich"),
            listOf(null, "ostatniemu", "ostatniej", "ostatniemu", "ostatnim"),
            listOf(null, "ostatniego", "ostatni", "ostatnią", "ostatnie", "ostatnich", "ostatnie"),
            listOf(null, "ostatnim", "ostatnią", "ostatnim", "ostatnimi"),
            listOf(null, "ostatnim", "ostatniej", "ostatnim", "ostatnich")
        )
    )
}
