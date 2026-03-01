package com.slovy.slovymovyapp.forms

import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.dictionary.DictionaryPos
import com.slovy.slovymovyapp.data.dictionary.FormSource
import com.slovy.slovymovyapp.data.forms.SchemeInputForm
import com.slovy.slovymovyapp.data.forms.configs.EnConjugationScheme
import com.slovy.slovymovyapp.test.BaseTest
import com.slovy.slovymovyapp.test.IgnoreIos
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@IgnoreIos
class EnConjugationSchemeTest : BaseTest() {

    @Test
    fun pronounScheme_selectedForFullParadigm_nullForPossessiveOnly() {
        // A head pronoun (e.g. "he") has objective/reflexive forms → scheme is selected.
        val headForms = listOf(
            SchemeInputForm(tags = listOf("nominative"), form = "he", source = FormSource.NATIVE),
            SchemeInputForm(tags = listOf("objective"), form = "him", source = FormSource.NATIVE),
            SchemeInputForm(tags = listOf("reflexive"), form = "himself", source = FormSource.NATIVE),
        )
        assertNotNull(
            EnConjugationScheme.schemeFor(DictionaryPos.PRONOUN, headForms),
            "Head pronoun with objective/reflexive forms must get a pronoun scheme"
        )

        // A possessive-only lemma (e.g. "his") has no objective/oblique/reflexive forms → no scheme.
        val possessiveForms = listOf(
            SchemeInputForm(tags = listOf("possessive", "singular"), form = "his", source = FormSource.NATIVE),
        )
        assertNull(
            EnConjugationScheme.schemeFor(DictionaryPos.PRONOUN, possessiveForms),
            "Possessive-only lemma must not get a pronoun scheme"
        )
    }

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
            assertEquals(
                expectedEnAdverbWell,
                resolveFormsSnapshot(repo, Language.ENGLISH, "well", DictionaryPos.ADVERB),
                "Resolved EN adverb views for 'well' changed"
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
            listOf(null, "test"),    // infinitive
            listOf(null, "test"),    // present (I)
            listOf(null, "tests"),   // present (he/she/it)
            listOf(null, "testing"), // present participle
            listOf(null, "tested"),  // past simple
            listOf(null, "tested"),  // past participle
        )
    )

    private val expectedEnAdjectiveIrrelevant = mapOf(
        "en_adjective:short" to listOf(
            listOf(null, "more irrelevant"), // comparative
            listOf(null, "most irrelevant"), // superlative
        )
    )

    private val expectedEnAdverbWell = mapOf(
        "en_adverb:short" to listOf(
            listOf(null, "better"), // comparative
            listOf(null, "best"),   // superlative
        )
    )

}
