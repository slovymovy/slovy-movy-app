package com.slovy.slovymovyapp.forms

import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.dictionary.DictionaryPos
import com.slovy.slovymovyapp.test.BaseTest
import com.slovy.slovymovyapp.test.IgnoreIos
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

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
            /* TODO
              assertEquals(
                expectedRuVerbSkazat,
                resolveFormsSnapshot(repo, Language.RUSSIAN, "сказать", DictionaryPos.VERB),
                "Resolved RU verb views for 'сказать' changed"
            )*/
            assertEquals(
                expectedRuAdjectiveKrasivyj,
                resolveFormsSnapshot(repo, Language.RUSSIAN, "красивый", DictionaryPos.ADJECTIVE),
                "Resolved RU adjective views for 'красивый' changed"
            )
        } finally {
            mgr.deleteDictionary(Language.RUSSIAN)
        }
    }

    private val expectedRuNounKniga = mapOf(
        "ru_noun:full" to listOf(
            listOf(null, null, null),
            listOf(null, "кни́га", "кни́ги"),
            listOf(null, "кни́ги", "книг"),
            listOf(null, "кни́ге", "кни́гам"),
            listOf(null, "кни́гу", "кни́ги"),
            listOf(null, "кни́гой", "кни́гами"),
            listOf(null, "кни́ге", "кни́гах")
        )
    )

    private val expectedRuVerbCitat = mapOf(
        "ru_verb_imperfective:full" to listOf(
            listOf(null, "читать"),
            listOf(null, null, null),
            listOf(null, "чита́ющий", "чита́вший"),
            listOf(null, "чита́емый", "чи́танный"),
            listOf(null, "чита́я", "чита́в"),
            listOf(null, null, null),
            listOf(null, "чита́ю", null),
            listOf(null, "чита́ешь", null),
            listOf(null, "чита́ет", null),
            listOf(null, "чита́ем", null),
            listOf(null, "чита́ете", null),
            listOf(null, "чита́ют", null),
            listOf(null, null, null),
            listOf(null, null, null),
            listOf(null, null, null),
            listOf(null, "чита́л", null),
            listOf(null, "чита́ла"),
            listOf(null, "чита́ло")
        ),
    )

    private val expectedRuVerbSkazat = mapOf(
        "ru_verb_perfective:full" to listOf(
            listOf(null, null),
            listOf(null, null, null),
            listOf(null, "сказа́вший", "ска́занный"),
            listOf(null, "сказа́в"),
            listOf(null),
            listOf(null, "скажу́"),
            listOf(null, "ска́жешь"),
            listOf(null, "ска́жет"),
            listOf(null, "ска́жем"),
            listOf(null, "ска́жете"),
            listOf(null, "ска́жут"),
            listOf(null, null, null),
            listOf(null, null, null),
            listOf(null, null, null),
            listOf(null, "сказа́л", null),
            listOf(null, "сказа́ла"),
            listOf(null, "сказа́ло")
        )
    )

    private val expectedRuAdjectiveKrasivyj = mapOf(
        "ru_adjective:full" to listOf(
            listOf(null, null, null, null, null),
            listOf(null, "краси́вый", "краси́вое", "краси́вая", "краси́вые"),
            listOf(null, "краси́вого", "краси́вого", "краси́вой", "краси́вых"),
            listOf(null, "краси́вому", "краси́вой", "краси́вым"),
            listOf(null, null, "краси́вого", "краси́вое", "краси́вую", "краси́вых"),
            listOf(null, "краси́вый", "краси́вые"),
            listOf(null, "краси́вым", "краси́вым", "краси́вой", "краси́выми"),
            listOf(null, "краси́вом", "краси́вой", "краси́вых"),
            listOf(null, "краси́в", "краси́во", "краси́ва", "краси́вы")
        )
    )
}
