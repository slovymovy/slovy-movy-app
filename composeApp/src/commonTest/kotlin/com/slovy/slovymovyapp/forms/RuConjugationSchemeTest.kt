package com.slovy.slovymovyapp.forms

import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.dictionary.DictionaryPos
import com.slovy.slovymovyapp.data.dictionary.FormSource
import com.slovy.slovymovyapp.data.forms.SchemeInputForm
import com.slovy.slovymovyapp.data.forms.configs.RuConjugationScheme
import com.slovy.slovymovyapp.test.BaseTest
import com.slovy.slovymovyapp.test.IgnoreIos
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@IgnoreIos
class RuConjugationSchemeTest : BaseTest() {

    @Test
    fun russianSnapshots_matchExpectedTables() = runBlocking {
        val (mgr, repo) = createSnapshotRepository()
        try {
            mgr.ensureDictionary(Language.RUSSIAN)

            assertEquals(
                expectedRuNounKniga,
                resolveFormsSnapshot(repo, Language.RUSSIAN, "книга", DictionaryPos.NOUN),
                "Resolved RU noun views for 'книга' changed"
            )
            assertEquals(
                expectedRuVerbCitat,
                resolveFormsSnapshot(repo, Language.RUSSIAN, "читать", DictionaryPos.VERB),
                "Resolved RU verb views for 'читать' changed"
            )
            assertEquals(
                expectedRuVerbSkazat,
                resolveFormsSnapshot(repo, Language.RUSSIAN, "сказать", DictionaryPos.VERB),
                "Resolved RU verb views for 'сказать' changed"
            )
            assertEquals(
                expectedRuAdjectiveKrasivyj,
                resolveFormsSnapshot(repo, Language.RUSSIAN, "красивый", DictionaryPos.ADJECTIVE),
                "Resolved RU adjective views for 'красивый' changed"
            )
            assertEquals(
                expectedRuAdverbBystro,
                resolveFormsSnapshot(repo, Language.RUSSIAN, "быстро", DictionaryPos.ADVERB),
                "Resolved RU adverb views for 'быстро' changed"
            )
            assertNull(
                RuConjugationScheme.schemeFor(
                    DictionaryPos.ADVERB,
                    listOf(SchemeInputForm(listOf("canonical"), "абсолю́тно", FormSource.NATIVE))
                ),
                "Non-gradable RU adverb with no comparative tag should produce no scheme"
            )
        } finally {
            mgr.deleteDictionary(Language.RUSSIAN)
        }
    }

    private val expectedRuNounKniga = mapOf(
        "ru_noun:full" to listOf(
            listOf(null, null, null),
            listOf(null, "кни́га", "кни́ги"),
            listOf(null, "кни́ги", "кни́г"),
            listOf(null, "кни́ге", "кни́гам"),
            listOf(null, "кни́гу", "кни́ги"),
            listOf(null, "кни́гой", "кни́гами"),
            listOf(null, "кни́ге", "кни́гах")
        )
    )

    private val expectedRuVerbCitat = mapOf(
        "ru_verb_imperfective:full" to listOf(
            listOf(null, "чита́ть"),                              // infinitive
            listOf(null, null, null),                              // col headers: singular | plural
            listOf(null),                                          // present tense
            listOf(null, "чита́ю", "чита́ем"),                    // 1st person
            listOf(null, "чита́ешь", "чита́ете"),                  // 2nd person
            listOf(null, "чита́ет", "чита́ют"),                    // 3rd person
            listOf(null),                                          // past tense
            listOf(null, "чита́л", "чита́ли"),                    // masculine (plural rowspan)
            listOf(null, "чита́ла"),                               // feminine
            listOf(null, "чита́ло"),                               // neuter
            listOf(null),                                          // future tense
            listOf(null, "бу́ду чита́ть", "бу́дем чита́ть"),      // 1st person
            listOf(null, "бу́дешь чита́ть", "бу́дете чита́ть"),   // 2nd person
            listOf(null, "бу́дет чита́ть", "бу́дут чита́ть"),     // 3rd person
            listOf(null),                                          // imperative
            listOf(null, "чита́й", "чита́йте"),                   // 2nd person
            listOf(null, null, null),                              // col headers: present | past
            listOf(null, "чита́ющий", "чита́вший"),               // active participle
            listOf(null, "чита́емый", "чи́танный"),               // passive participle
            listOf(null, "чита́я", "чита́в")                      // adverbial participle
        ),
    )

    private val expectedRuVerbSkazat = mapOf(
        "ru_verb_perfective:full" to listOf(
            listOf(null, "сказа́ть"),                             // infinitive
            listOf(null, null, null),                              // col headers: singular | plural
            listOf(null),                                          // future tense
            listOf(null, "скажу́", "ска́жем"),                    // 1st person
            listOf(null, "ска́жешь", "ска́жете"),                 // 2nd person
            listOf(null, "ска́жет", "ска́жут"),                   // 3rd person
            listOf(null),                                          // past tense
            listOf(null, "сказа́л", "сказа́ли"),                  // masculine (plural rowspan)
            listOf(null, "сказа́ла"),                              // feminine
            listOf(null, "сказа́ло"),                              // neuter
            listOf(null),                                          // imperative
            listOf(null, "скажи́", "скажи́те"),                   // 2nd person
            listOf(null, null, null),                              // col headers: active | passive
            listOf(null, "сказа́вший", "ска́занный"),             // past participle
            listOf(null, "сказа́в")                               // adverbial participle (colspan=2)
        )
    )

    private val expectedRuAdverbBystro = mapOf(
        "ru_adverb:short" to listOf(
            listOf(null, null),           // header row
            listOf(null, "(по)быстре́е") // comparative
        )
    )

    private val expectedRuAdjectiveKrasivyj = mapOf(
        "ru_adjective:essentials" to listOf(
            listOf(null, null),            // header row
            listOf(null, "краси́вый"),  // masculine
            listOf(null, "краси́вая"),  // feminine
            listOf(null, "краси́вое"),  // neuter
            listOf(null, "краси́вые")   // plural
        ),
        "ru_adjective:full" to listOf(
            listOf(null, null, null, null, null),                                    // col headers: masculine | feminine | neuter | plural
            listOf(null, "краси́вый", "краси́вая", "краси́вое", "краси́вые"),        // nominative
            listOf(null, "краси́вого", "краси́вой", "краси́вого", "краси́вых"),      // genitive (neuter rowspan=2)
            listOf(null, "краси́вому", "краси́вой", "краси́вым"),                    // dative (neuter covered)
            listOf(null, null, "краси́вого", "краси́вую", "краси́вое", "краси́вых"), // accusative animate (fem+neuter rowspan=2)
            listOf(null, "краси́вый", "краси́вые"),                                  // accusative inanimate (fem+neuter covered)
            listOf(null, "краси́вым", "краси́вой", "краси́вым", "краси́выми"),       // instrumental (neuter rowspan=2)
            listOf(null, "краси́вом", "краси́вой", "краси́вых"),                     // prepositional (neuter covered)
            listOf(null, "краси́в", "краси́ва", "краси́во", "краси́вы")              // short form
        )
    )
}
