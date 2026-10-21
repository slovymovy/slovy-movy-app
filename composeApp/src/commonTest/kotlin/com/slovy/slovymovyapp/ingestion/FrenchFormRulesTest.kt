package com.slovy.slovymovyapp.ingestion

import com.slovy.slovymovyapp.data.dictionary.FormSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.uuid.Uuid

class FrenchFormRulesTest {

    private val entryId = Uuid.random()

    private fun form(text: String, vararg tags: String) =
        ExtractedWordForm(formId = Uuid.random(), entryId = entryId, tags = tags.toMutableList(), form = text)

    private fun entry(word: String, pos: String) = ExtractedWordEntry(
        entryId = entryId, word = word, pos = pos, langCode = "fr",
        forms = mutableListOf(), senses = mutableListOf(), translations = mutableListOf(), wordLinkages = mutableListOf()
    )

    private val parler = entry("parler", "verb")

    @Test
    fun select_stripsVerbPronounsAndTagsThePersonTheyGave() {
        val withPronoun = form("je parle", "indicative", "present")
        val selected = FrenchFormRules.select(parler, withPronoun)
        assertEquals("parle", selected?.form, "the pronoun is not part of the form")
        assertEquals(listOf("indicative", "present", "first-person", "singular"), selected?.tags?.toList(), "the person moves into the tags")
        assertEquals(withPronoun.formId, selected?.formId, "the form keeps its id")

        listOf(
            form("il/elle/on parle", "indicative", "present") to listOf("third-person", "singular"),
            form("que tu parles", "present", "subjunctive") to listOf("second-person", "singular"),
            form("qu’ils/elles parlent", "present", "subjunctive") to listOf("third-person", "plural"),
            form("nous parlions", "imperfect", "indicative") to listOf("first-person", "plural"),
            form("vous parlerez", "future", "indicative") to listOf("second-person", "plural"),
        ).forEach { (f, person) ->
            val s = FrenchFormRules.select(parler, f)
            assertEquals(f.form.substringAfterLast(' '), s?.form, "'${f.form}'")
            assertEquals(f.tags + person, s?.tags?.toList(), "'${f.form}' gives its person")
        }
    }

    @Test
    fun select_stripsElidedAndReflexivePronouns() {
        assertEquals("abstrais", FrenchFormRules.select(entry("s’abstraire", "verb"), form("je m’abstrais", "indicative", "present"))?.form)
        assertEquals("levons", FrenchFormRules.select(entry("se lever", "verb"), form("nous nous levons", "indicative", "present"))?.form)
        assertEquals("aime", FrenchFormRules.select(entry("aimer", "verb"), form("j’aime", "indicative", "present"))?.form)
        assertEquals("lever", FrenchFormRules.select(entry("se lever", "verb"), form("se lever", "infinitive", "present"))?.form)
        assertEquals("drache", FrenchFormRules.select(entry("dracher", "verb"), form("il drache", "indicative", "present"))?.form, "impersonal 'il'")

        // The English edition writes only the reflexive pronoun, and already tags the person.
        val english = form("me blessais", "first-person", "imperfect", "indicative", "reflexive", "singular")
        val selected = FrenchFormRules.select(entry("se blesser", "verb"), english)
        assertEquals("blessais", selected?.form)
        assertEquals(english.tags, selected?.tags, "a tagged person is not added twice")
    }

    @Test
    fun select_dropsCompoundTensesAndGerunds() {
        listOf(
            form("j’ai parlé", "indicative", "multiword-construction", "past"),
            form("tu avais parlé", "indicative", "pluperfect"),
            form("que j’aie parlé", "past", "subjunctive"),
            form("avoir parlé", "infinitive", "past"),
            form("en parlant", "gerund", "present"),
            form("en ayant parlé", "gerund", "past"),
            form("aie parlé", "imperative", "past"),
            form("s’être abstrait", "infinitive", "past"),
        ).forEach { f ->
            assertNull(FrenchFormRules.select(parler, f), "'${f.form}' ${f.tags} is not stored")
        }
    }

    @Test
    fun select_keepsTheFirstOfTwoSpellings() {
        assertEquals(
            "abcéderai",
            FrenchFormRules.select(entry("abcéder", "verb"), form("j’abcéderai ou abcèderai", "future", "indicative"))?.form
        )
    }

    @Test
    fun select_dropsPronunciationsAndPlaceholdersButKeepsLigatures() {
        listOf(
            form("/paʁl/", "first-person", "indicative", "present", "singular"),
            form("paʁl", "first-person", "indicative", "present", "singular"),
            form("avoir + past participle", "infinitive", "multiword-construction"),
        ).forEach { f -> assertNull(FrenchFormRules.select(parler, f), "'${f.form}' is not a form") }

        val alsacien = entry("halstatien", "adj")
        assertNull(FrenchFormRules.select(alsacien, form("^((h aspiré))\\al.sta.tjɛ̃\\", "singular", "masculine")))

        val coeurs = form("cœurs", "plural")
        assertSame(coeurs, FrenchFormRules.select(entry("cœur", "noun"), coeurs), "œ is a French letter")
    }

    @Test
    fun select_leavesOtherPartsOfSpeechAlone() {
        val chevaux = form("chevaux", "plural")
        assertSame(chevaux, FrenchFormRules.select(entry("cheval", "noun"), chevaux))
        val abjecte = form("abjecte", "feminine", "singular")
        assertSame(abjecte, FrenchFormRules.select(entry("abject", "adj"), abjecte))
    }

    @Test
    fun dropRepeated_keepsOneVerbRowPerCell() {
        val native = FrenchFormRules.select(parler, form("je parle", "indicative", "present"))!! to FormSource.NATIVE
        val english = form("parle", "first-person", "indicative", "present", "singular") to FormSource.EN
        val nativePasseSimple = FrenchFormRules.select(parler, form("je parlai", "indicative", "past"))!! to FormSource.NATIVE
        val englishPasseSimple = form("parlai", "first-person", "historic", "indicative", "past", "singular") to FormSource.EN
        val nativeConditional = FrenchFormRules.select(parler, form("tu parlerais", "conditional", "present"))!! to FormSource.NATIVE
        val englishConditional = form("parlerais", "conditional", "second-person", "singular") to FormSource.EN
        // The native imperative carries no person, so the English row is the more specific one.
        val nativeImperative = form("parle", "imperative", "present") to FormSource.NATIVE
        val englishImperative = form("parle", "imperative", "second-person", "singular") to FormSource.EN
        val feminineParticiple = form("parlée", "feminine", "participle", "past", "singular") to FormSource.EN

        val result = FrenchFormRules.dropRepeated(
            "verb",
            listOf(native, nativePasseSimple, nativeConditional, nativeImperative, english, englishPasseSimple,
                englishConditional, englishImperative, feminineParticiple)
        )

        assertEquals(
            listOf(native, nativePasseSimple, nativeConditional, englishImperative, feminineParticiple),
            result,
            "each cell keeps one row, native first"
        )
    }

    @Test
    fun dropRepeated_dropsEnglishRowsANativeRowCovers() {
        val nativeFeminine = form("abjecte", "feminine", "singular") to FormSource.NATIVE
        val englishFeminine = form("abjecte", "feminine") to FormSource.EN
        val nativePlural = form("abjects", "masculine", "plural") to FormSource.NATIVE
        val englishPlural = form("abjects", "masculine", "plural") to FormSource.EN
        val englishOnly = form("abjectement", "adverb") to FormSource.EN

        val result = FrenchFormRules.dropRepeated(
            "adj", listOf(nativeFeminine, nativePlural, englishFeminine, englishPlural, englishOnly)
        )

        assertEquals(listOf(nativeFeminine, nativePlural, englishOnly), result)
    }
}
