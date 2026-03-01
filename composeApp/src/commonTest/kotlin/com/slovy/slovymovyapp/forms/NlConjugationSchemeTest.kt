package com.slovy.slovymovyapp.forms

import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.dictionary.DictionaryPos
import com.slovy.slovymovyapp.test.BaseTest
import com.slovy.slovymovyapp.test.IgnoreIos
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

@IgnoreIos
class NlConjugationSchemeTest : BaseTest() {

    @Test
    fun dutchSnapshots_matchExpectedTables() = runBlocking {
        val (mgr, repo) = createSnapshotRepository()
        try {
            mgr.ensureDictionary(Language.DUTCH)

            assertEquals(
                expectedNlVerbZeggen,
                resolveFormsSnapshot(repo, Language.DUTCH, "zeggen", DictionaryPos.VERB),
                "Resolved NL verb views for 'zeggen' changed"
            )
            assertEquals(
                expectedNlAdjectiveVolslagen,
                resolveFormsSnapshot(repo, Language.DUTCH, "volslagen", DictionaryPos.ADJECTIVE),
                "Resolved NL adjective views for 'volslagen' changed"
            )
            assertEquals(
                expectedNlNounKwartier,
                resolveFormsSnapshot(repo, Language.DUTCH, "kwartier", DictionaryPos.NOUN),
                "Resolved NL noun views for 'kwartier' changed"
            )
            assertEquals(
                expectedNlNounBoom,
                resolveFormsSnapshot(repo, Language.DUTCH, "boom", DictionaryPos.NOUN),
                "Resolved NL noun views for 'boom' changed"
            )
            assertEquals(
                expectedNlAdverbAanschouwelijk,
                resolveFormsSnapshot(repo, Language.DUTCH, "aanschouwelijk", DictionaryPos.ADVERB),
                "Resolved NL adverb views for 'aanschouwelijk' changed"
            )
        } finally {
            mgr.deleteDictionary(Language.DUTCH)
        }
    }

    private val expectedNlVerbZeggen = mapOf(
        "nl_verb:category_summary" to listOf(
            listOf(null, null, null, null),
            listOf(null, "te zeggen", null, "zeg"),
            listOf(null, "zegt", null, "zal zeggen"),
            listOf(null, "zegden\nzeiden", null, "gezegd"),
            listOf(null, "zeggend", null)
        ),
        "nl_verb:full" to listOf(
            listOf(null, "te zeggen"),
            listOf(null, "zeggen"),
            listOf(null, "gezegd"),
            listOf(null, "zeggend", null),
            listOf(null, null, null, null, null),
            listOf(null, "zeg", "zegt", "zegt", "zeggen"),
            listOf(null, "zal zeggen", "zal zeggen", "zal zeggen", "zegden\nzeiden"),
            listOf(null, "zou gezegd worden", "zou gezegd worden", "zouden gezegd worden", "zou gezegd worden"),
            listOf(null, "zal zeggen", "zal zeggen", "zal zeggen", "zullen zeggen"),
            listOf(null, "zeg", null),
            listOf(null, null, null, null, null),
            listOf(null, "ben gezegd", "bent gezegd", "hebben gezegd", null),
            listOf(null, "zou gezegd hebben", "zou gezegd hebben", "zou gezegd hebben", null),
            listOf(null, "zal gezegd hebben", "zal gezegd hebben", "zal gezegd hebben", null)
        )
    )

    private val expectedNlAdjectiveVolslagen = mapOf(
        "nl_adjective:category_summary" to listOf(
            listOf(null, null),
            listOf(null, "volslagen"),
            listOf(null, "volslagen"),
            listOf(null, "volslagener"),
            listOf(null, "volslagenst"),
            listOf(null, "volslagens")
        ),
        "nl_adjective:full" to listOf(
            listOf(null, null, null, null),
            listOf(null, "volslagen", "volslagener", "het volslagenst"),
            listOf(null, "volslagen", "volslagener", "volslagenste"),
            listOf(null, "volslagen", "volslagener", "volslagenste"),
            listOf(null, "volslagen", "volslagener", "volslagenste"),
            listOf(null, "volslagen", "volslagener", "volslagenste"),
            listOf(null, "volslagens", "volslageners", null)
        )
    )

    private val expectedNlNounKwartier = mapOf(
        "nl_noun:short" to listOf(
            listOf(null, null),
            listOf(null, "kwartieren"),
            listOf(null, "kwartiertje"),
            listOf(null, "kwartiertjes")
        )
    )

    // "boom" verifies that diminutive plurals (boompjes) are excluded from the Plural cell
    // and appear only in the Diminutive plural cell, not the regular Plural cell.
    private val expectedNlNounBoom = mapOf(
        "nl_noun:short" to listOf(
            listOf(null, null),
            listOf(null, "bomen"),
            listOf(null, "boompje"),
            listOf(null, "boompjes")
        )
    )

    private val expectedNlAdverbAanschouwelijk = mapOf(
        "nl_adverb:short" to listOf(
            listOf(null, null),
            listOf(null, "aanschouwelijker"),
            listOf(null, "aanschouwelijkst")
        )
    )
}
