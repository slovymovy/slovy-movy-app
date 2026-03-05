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
    fun preprocessForms_orphanClosingParen_droppedBalancedRetained() {
        val resolver = NlConjugationScheme.NL_ADJECTIVE.tagResolver

        // "attributief)" is the orphaned second half of a split "(alleen attributief)"
        // DB entry. It has no opening '(' so it must be dropped entirely.
        val orphan = SchemeInputForm(
            tags = listOf("positive", "uninflected"),
            form = "attributief)",
            source = FormSource.NATIVE,
        )
        val orphanResult = resolver.preprocessForms(listOf(orphan), null)
        assertTrue(orphanResult.isEmpty(), "Orphaned closing-paren form must be filtered out")

        // "iets (formeel)" has balanced parentheses and must survive.
        val balanced = SchemeInputForm(
            tags = listOf("positive", "uninflected"),
            form = "iets (formeel)",
            source = FormSource.NATIVE,
        )
        val balancedResult = resolver.preprocessForms(listOf(balanced), null)
        assertEquals(1, balancedResult.size, "Balanced parenthetical form must be retained")
        assertEquals("iets (formeel)", balancedResult.first().form)
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
            // Pure zijn-verb (only zijn-auxiliary compound perfect forms in Wiktionary).
            // The dutchAuxiliaryPriority tiebreaker must prefer "bent" (priority 1) over
            // "is" (priority 2) for the 2nd-person present-perfect cell.
            assertEquals(
                expectedNlVerbOnderstromen,
                resolveFormsSnapshot(repo, Language.DUTCH, "onderstromen", DictionaryPos.VERB),
                "Resolved NL verb views for 'onderstromen' changed"
            )
            // Archaic gij collision (non-separable): wrongt uit finite-token "wrongt" ends in -t,
            // must lose to wrong uit (modern).
            assertEquals(
                expectedNlVerbUitwringen,
                resolveFormsSnapshot(repo, Language.DUTCH, "uitwringen", DictionaryPos.VERB),
                "Resolved NL verb views for 'uitwringen' changed"
            )
            // Separable-verb archaic gij: "spraakt af" has finite token "spraakt" ending in -t.
            // Without finite-token detection the form ends in particle "af" and is not marked
            // archaic, so it would win alphabetically over "sprak af" (position 4: 'a' < 'k').
            assertEquals(
                expectedNlVerbAfspreken,
                resolveFormsSnapshot(repo, Language.DUTCH, "afspreken", DictionaryPos.VERB),
                "Resolved NL verb views for 'afspreken' changed"
            )
        } finally {
            mgr.deleteDictionary(Language.DUTCH)
        }
    }

    private val expectedNlVerbZeggen = mapOf(
        "nl_verb:essentials" to listOf(
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
            listOf(null, "zal zeggen", "zal zeggen", "zal zeggen", "zullen zeggen"),
            listOf(null, "zou zeggen", "zou zeggen", "zou zeggen", "zouden zeggen"),
            listOf(null, "zeg", null),
            listOf(null, "heb gezegd", "hebt gezegd", "heeft gezegd", null),
            listOf(null, "zal gezegd hebben", "zal gezegd hebben", "zal gezegd hebben", null),
            listOf(null, "zou gezegd hebben", "zou gezegd hebben", "zou gezegd hebben", null)
        )
    )

    // "volslagen" is attributive-only (no predicative form in Wiktionary). Positive (Base) showing
    // "volslagen" exercises the predicative-supporting fallback: without it the cell would be null.
    private val expectedNlAdjectiveVolslagen = mapOf(
        "nl_adjective:essentials" to listOf(
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

    // uitwringen has "wrongt uit" (archaic 2nd-sg-past, ends in -t) alongside "wrong uit" (modern).
    // The gij heuristic must inject "gij" into the archaic form so it loses to the modern one.
    // Present/conditional/future perfect must show active hebben-auxiliary forms, not zijn-based ones.
    private val expectedNlVerbUitwringen = mapOf(
        "nl_verb:essentials" to listOf(
            listOf(null, null),
            listOf(null, "uit te wringen"),    // infinitive
            listOf(null, "wring uit"),          // present (ik)
            listOf(null, "wringt uit"),         // present (jij/hij/u)
            listOf(null, "wrong uit"),          // past singular — modern form wins over archaic wrongt uit
            listOf(null, "wrongen uit"),        // past plural
            listOf(null, "uitgewrongen"),       // past participle
            listOf(null, "uitwringend")         // present participle
        ),
        "nl_verb:full" to listOf(
            listOf(null, null, null, null, null),
            listOf(null, "wring uit", "wringt uit", "wringt uit", "wringen uit"),
            listOf(null, "wrong uit", "wrong uit", "wrong uit", "wrongen uit"),
            listOf(null, "zal uitwringen", "zal uitwringen", "zal uitwringen", "zullen uitwringen"),
            listOf(null, "zou uitwringen", "zou uitwringen", "zou uitwringen", "zouden uitwringen"),
            listOf(null, "wring uit", null),
            listOf(null, "heb uitgewrongen", "hebt uitgewrongen", "heeft uitgewrongen", null),
            listOf(null, "zal uitgewrongen hebben", "zal uitgewrongen hebben", "zal uitgewrongen hebben", null),
            listOf(null, "zou uitgewrongen hebben", "zou uitgewrongen hebben", "zou uitgewrongen hebben", null)
        )
    )

    // afspreken has "spraakt af" (archaic 2nd-sg-past) vs "sprak af" (modern).
    // The whole string ends in particle "af", not "t", so finite-token detection
    // ("spraakt".endsWith("t")) is required for the gij heuristic to fire.
    // Without it "spraakt af" would win alphabetically (position 4: 'a' < 'k').
    private val expectedNlVerbAfspreken = mapOf(
        "nl_verb:essentials" to listOf(
            listOf(null, null),
            listOf(null, "af te spreken"),     // infinitive
            listOf(null, "spreek af"),          // present (ik)
            listOf(null, "spreekt af"),         // present (jij/hij/u)
            listOf(null, "sprak af"),           // past singular — modern form wins over archaic spraakt af
            listOf(null, "spraken af"),         // past plural
            listOf(null, "afgesproken"),        // past participle
            listOf(null, "afsprekend")          // present participle
        ),
        "nl_verb:full" to listOf(
            listOf(null, null, null, null, null),
            listOf(null, "spreek af", "spreekt af", "spreekt af", "spreken af"),
            listOf(null, "sprak af", "sprak af", "sprak af", "spraken af"),
            listOf(null, "zal afspreken", "zal afspreken", "zal afspreken", "zullen afspreken"),
            listOf(null, "zou afspreken", "zou afspreken", "zou afspreken", "zouden afspreken"),
            listOf(null, "spreek af", null),
            listOf(null, "heb afgesproken", "hebt afgesproken", "heeft afgesproken", null),
            listOf(null, "zal afgesproken hebben", "zal afgesproken hebben", "zal afgesproken hebben", null),
            listOf(null, "zou afgesproken hebben", "zou afgesproken hebben", "zou afgesproken hebben", null)
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

    // onderstromen is a pure zijn-verb: Wiktionary lists only zijn-auxiliary compound perfect
    // forms (no "heb/hebt/heeft" counterpart). The zijn-passive heuristic therefore does NOT
    // fire, and the dutchAuxiliaryPriority tiebreaker must pick "bent" (priority 1) over
    // "is" (priority 2) for the 2nd-person present-perfect cell.
    private val expectedNlVerbOnderstromen = mapOf(
        "nl_verb:essentials" to listOf(
            listOf(null, null),
            listOf(null, "onder te stromen"),  // infinitive (long-form te-infinitive wins)
            listOf(null, "stroom onder"),      // present (ik) — main-clause form
            listOf(null, "stroomt onder"),     // present (jij/hij/u)
            listOf(null, "stroomde onder"),    // past singular
            listOf(null, "stroomden onder"),   // past plural
            listOf(null, "ondergestroomd"),    // past participle
            listOf(null, "onderstromend")      // present participle
        ),
        "nl_verb:full" to listOf(
            listOf(null, null, null, null, null),
            listOf(null, "stroom onder", "stroomt onder", "stroomt onder", "stromen onder"),
            listOf(null, "stroomde onder", "stroomde onder", "stroomde onder", "stroomden onder"),
            listOf(null, "zal onderstromen", "zal onderstromen", "zal onderstromen", "zullen onderstromen"),
            listOf(null, "zou onderstromen", "zou onderstromen", "zou onderstromen", "zouden onderstromen"),
            listOf(null, "onderstroom", null),
            // zijn-verb: heuristic does not fire; dutchAuxiliaryPriority selects ben/bent/is
            listOf(null, "ben ondergestroomd", "bent ondergestroomd", "is ondergestroomd", null),
            listOf(null, "zal ondergestroomd zijn", "zal ondergestroomd zijn", "zal ondergestroomd zijn", null),
            listOf(null, "zou ondergestroomd zijn", "zou ondergestroomd zijn", "zou ondergestroomd zijn", null)
        )
    )

}
