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
            assertEquals(
                expectedEnAdverbWell,
                resolveFormsSnapshot(repo, Language.ENGLISH, "well", DictionaryPos.ADVERB),
                "Resolved EN adverb views for 'well' changed"
            )
            kotlin.test.assertNull(
                repo.getLanguageCard(Language.ENGLISH, "i"),
                "Expected card for 'i' to be null in current state"
            )
            kotlin.test.assertNull(
                repo.getLanguageCard(Language.ENGLISH, "we"),
                "Expected card for 'we' to be null in current state"
            )
            kotlin.test.assertNull(
                repo.getLanguageCard(Language.ENGLISH, "you"),
                "Expected card for 'you' to be null in current state"
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

    private val expectedEnPronounI = mapOf(
        "en_pronoun:short" to listOf(
            listOf(null, "i"),       // subjective (injected from lemma)
            listOf(null, "me"),      // objective
            listOf(null, "my"),      // possessive determiner
            listOf(null, "mine"),    // possessive pronoun
            listOf(null, "myself"),  // reflexive
        )
    )

    private val expectedEnPronounWe = mapOf(
        "en_pronoun:short" to listOf(
            listOf(null, "we"),        // subjective
            listOf(null, "us"),        // objective
            listOf(null, "our"),       // possessive determiner
            listOf(null, "ours"),      // possessive pronoun
            listOf(null, "ourselves"), // reflexive
        )
    )

    private val expectedEnPronounYou = mapOf(
        "en_pronoun:short" to listOf(
            listOf(null, "you"),       // subjective
            listOf(null, null),        // objective (same form as subjective, not in DB)
            listOf(null, "your"),      // possessive determiner
            listOf(null, "yours"),     // possessive pronoun
            listOf(null, "yourself"),  // reflexive
        )
    )
}
