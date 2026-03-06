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
            assertEquals(
                expectedPlAdjectiveStaranny,
                resolveFormsSnapshot(repo, Language.POLISH, "staranny", DictionaryPos.ADJECTIVE),
                "Resolved PL adjective views for 'staranny' changed"
            )
            assertEquals(
                expectedPlAdverbDobrze,
                resolveFormsSnapshot(repo, Language.POLISH, "dobrze", DictionaryPos.ADVERB),
                "Resolved PL adverb views for 'dobrze' changed"
            )
            assertEquals(
                expectedPlPronounJa,
                resolveFormsSnapshot(repo, Language.POLISH, "ja", DictionaryPos.PRONOUN),
                "Resolved PL pronoun views for 'ja' changed"
            )
            assertEquals(
                expectedPlPronounTa,
                resolveFormsSnapshot(repo, Language.POLISH, "ta", DictionaryPos.PRONOUN),
                "Resolved PL pronoun views for 'ta' changed"
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
        "pl_verb:essentials" to listOf(
            listOf(null, "podawać"),                                                                          // infinitive
            listOf(null, null, null),                                                                         // singular / plural col headers
            listOf(null),                                                                                     // "present tense" header
            listOf(null, "podaję", "podajemy"),                                                               // 1st person present
            listOf(null, "podajesz", "podajecie"),                                                            // 2nd person present
            listOf(null, "podaje", "podają"),                                                                 // 3rd person present
            listOf(null, "podaje się"),                                                                       // impersonal present
            listOf(null, null, null, null, null, null),                                                       // gendered col headers
            listOf(null),                                                                                     // "past tense" header
            listOf(null, "podawałem", "podawałam", null, "podawaliśmy", "podawałyśmy"),                      // 1st person past
            listOf(null, "podawałeś", "podawałaś", null, "podawaliście", "podawałyście"),                    // 2nd person past
            listOf(null, "podawał", "podawała", "podawało", "podawali", "podawały"),                         // 3rd person past
            listOf(null, "podawano"),                                                                         // impersonal past
            listOf(null),                                                                                     // "future tense" header
            listOf(null, "będę podawać", "będę podawać", null, "będziemy podawali", "będziemy podawać"),     // 1st person future
            listOf(null, "będziesz podawać", "będziesz podawać", null, "będziecie podawali", "będziecie podawać"), // 2nd person future
            listOf(null, "będzie podawać", "będzie podawać", "będzie podawać", "będą podawali", "będą podawać"),   // 3rd person future
            listOf(null, null, null),                                                                         // singular / plural col headers
            listOf(null),                                                                                     // "imperative" header
            listOf(null, null, "podawajmy"),                                                                  // 1st person imperative
            listOf(null, "podawaj", "podawajcie"),                                                            // 2nd person imperative
            listOf(null, "niech podaje", "niech podają")                                                      // 3rd person imperative
        ),
        "pl_verb:advanced" to listOf(
            listOf(null, null, null, null, null, null),                                                       // gendered col headers
            listOf(null),                                                                                     // "conditional" header
            listOf(null, "byłbym podawał", "byłabym podawała", null, "bylibyśmy podawali", "byłybyśmy podawały"),   // 1st person conditional
            listOf(null, "byłbyś podawał", "byłabyś podawała", null, "bylibyście podawali", "byłybyście podawały"), // 2nd person conditional
            listOf(null, "by podawał", "by podawała", "by podawało", "by podawali", "by podawały"),                 // 3rd person conditional
            listOf(null, "podawano by"),                                                                      // impersonal conditional
            listOf(null, "podający", "podająca", "podające", "podający", "podające"),                        // active adjectival participle
            listOf(null, "podawany", "podawana", "podawane", "podawani", "podawane"),                        // passive adjectival participle
            listOf(null, "nie podając"),                                                                      // contemporary adverbial participle
            listOf(null, "niepodawanie")                                                                      // verbal noun
        )
    )

    // "ta" has no vocative forms — both vocative cells resolve to null,
    // guarding against regressions in absent-cell handling.
    private val expectedPlPronounTa = mapOf(
        "pl_pronoun:full" to listOf(
            listOf(null, null, null),        // col headers
            listOf(null, "ta", "te"),        // nominative
            listOf(null, "tej", "tych"),     // genitive
            listOf(null, "tej", "tym"),      // dative
            listOf(null, "tę", "te"),        // accusative
            listOf(null, "tą", "tymi"),      // instrumental
            listOf(null, "tej", "tych"),     // locative
            listOf(null, null, null)         // vocative — legitimately absent
        )
    )

    private val expectedPlPronounJa = mapOf(
        "pl_pronoun:full" to listOf(
            listOf(null, null, null),          // col headers
            listOf(null, "ja", "my"),          // nominative
            listOf(null, "mię", "nas"),        // genitive
            listOf(null, "akcentowane mnie\nnieakcentowane mi", "nam"), // dative (data artifact; clean up later)
            listOf(null, "mię", "nas"),        // accusative
            listOf(null, "mną", "nami"),       // instrumental
            listOf(null, "mnie", "nas"),       // locative
            listOf(null, "—", null)            // vocative
        )
    )

    private val expectedPlAdverbDobrze = mapOf(
        "pl_adverb:short" to listOf(
            listOf(null, null),        // header row
            listOf(null, "lepiej"),    // comparative
            listOf(null, "najlepiej") // superlative
        )
    )

    private val expectedPlAdjectiveStaranny = mapOf(
        "pl_adjective:essentials" to listOf(
            listOf(null, null),
            listOf(null, "staranny"),   // masculine
            listOf(null, "staranna"),   // feminine
            listOf(null, "staranne"),   // neuter
            listOf(null, "staranni"),   // virile plural
            listOf(null, "staranne")    // non-virile plural
        ),
        "pl_adjective:full" to listOf(
            listOf(null, null, null, null, null, null, null),
            listOf(null, "staranny", "staranny", "staranna", "staranne", "staranni", "staranne"),
            listOf(null, "starannego", "starannej", "starannego", "starannych"),
            listOf(null, "starannemu", "starannej", "starannemu", "starannym"),
            listOf(null, "starannego", "staranny", "staranną", "staranne", "starannych", "staranne"),
            listOf(null, "starannym", "staranną", "starannym", "starannymi"),
            listOf(null, "starannym", "starannej", "starannym", "starannych")
        )
    )

    private val expectedPlAdjectiveOstatni = mapOf(
        "pl_adjective:essentials" to listOf(
            listOf(null, null),
            listOf(null, null),          // masculine — absent in DB for ostatni
            listOf(null, "ostatnia"),    // feminine
            listOf(null, "ostatnie"),    // neuter
            listOf(null, null),          // virile plural — absent in DB for ostatni
            listOf(null, "ostatnie")     // non-virile plural
        ),
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
