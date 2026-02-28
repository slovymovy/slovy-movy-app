package com.slovy.slovymovyapp.forms

import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.dictionary.DictionaryPos
import com.slovy.slovymovyapp.data.dictionary.FormSource
import com.slovy.slovymovyapp.data.forms.SchemeInputForm
import com.slovy.slovymovyapp.data.forms.configs.NlConjugationScheme
import com.slovy.slovymovyapp.test.BaseTest
import com.slovy.slovymovyapp.test.IgnoreIos
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@IgnoreIos
class NlConjugationSchemeTest : BaseTest() {

    @Test
    fun preprocessForms_parentheticalDiminutives_strippedAndTrimmed() {
        val resolver = NlConjugationScheme.NL_NOUN.tagResolver

        // "kindjes (rare)" has a space before "(": substringBefore strips to "kindjes " —
        // trim() must remove the trailing space so -jes suffix check still fires.
        val spacedParen = SchemeInputForm(
            tags = listOf("diminutive", "singular"),
            form = "kindjes (rare)",
            source = FormSource.NATIVE,
        )
        val result = resolver.preprocessForms(listOf(spacedParen), null)
            .first { "diminutive" in it.tags }
        assertEquals("kindjes", result.form, "Trailing space must be trimmed after stripping parenthetical")
        assertTrue("plural" in result.tags, "Trimmed -jes form must get plural tag")
        assertFalse("singular" in result.tags, "Wrong singular tag must be removed from -jes form")

        // "kindeke(dialectal)" → "kindeke" after strip+trim. The -ke suffix does NOT trigger
        // the -je/-jes canonicalization, so no number tag is added. The form therefore cannot
        // win cells that require both DIMINUTIVE and a number tag.
        val dialectal = SchemeInputForm(
            tags = listOf("diminutive", "neuter"),
            form = "kindeke(dialectal)",
            source = FormSource.NATIVE,
        )
        val resultDialectal = resolver.preprocessForms(listOf(dialectal), null)
            .first { "diminutive" in it.tags }
        assertEquals("kindeke", resultDialectal.form, "Embedded parenthetical must be stripped")
        assertFalse("singular" in resultDialectal.tags, "Non -je/-jes suffix must not gain a number tag")
        assertFalse("plural" in resultDialectal.tags, "Non -je/-jes suffix must not gain a number tag")
    }

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

        } finally {
            mgr.deleteDictionary(Language.DUTCH)
        }
    }

    private val expectedNlVerbZeggen = mapOf(
        "nl_verb:category_summary" to listOf(
            listOf(null, null),
            listOf(null, "te zeggen"),    // infinitive
            listOf(null, "zeg"),           // present (ik)
            listOf(null, "zegt"),          // present (jij/hij/u)
            listOf(null, "zegde\nzei"),    // past singular
            listOf(null, "zegden\nzeiden"), // past plural
            listOf(null, "gezegd"),        // past participle
            listOf(null, "zeggend")        // present participle
        ),
        "nl_verb:full" to listOf(
            listOf(null, null, null, null, null),
            listOf(null, "zeg", "zegt", "zegt", "zeggen"),
            listOf(null, "zegde\nzei", "zegde\nzei", "zegde\nzei", "zegden\nzeiden"),
            listOf(null, "zou zeggen", "zou zeggen", "zou zeggen", "zouden zeggen"),
            listOf(null, "zal zeggen", "zal zeggen", "zal zeggen", "zullen zeggen"),
            listOf(null, "zeg", null),
            listOf(null, null, null, null, null),
            listOf(null, "ben gezegd", "is gezegd", "is gezegd", null),
            listOf(null, "zou gezegd zijn", "zou gezegd zijn", "zou gezegd zijn", null),
            listOf(null, "zal gezegd zijn", "zult gezegd zijn", "zal gezegd zijn", null)
        )
    )

    // "volslagen" is attributive-only (no predicative form in Wiktionary). Positive (Base) showing
    // "volslagen" exercises the predicative-supporting fallback: without it the cell would be null.
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

}
