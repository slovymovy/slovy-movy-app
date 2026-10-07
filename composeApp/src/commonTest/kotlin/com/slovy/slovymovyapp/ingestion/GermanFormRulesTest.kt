package com.slovy.slovymovyapp.ingestion

import com.slovy.slovymovyapp.data.dictionary.FormSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.uuid.Uuid

class GermanFormRulesTest {

    private val entryId = Uuid.random()

    private fun form(text: String, vararg tags: String) =
        ExtractedWordForm(formId = Uuid.random(), entryId = entryId, tags = tags.toMutableList(), form = text)

    private fun entry(word: String, pos: String) = ExtractedWordEntry(
        entryId = entryId, word = word, pos = pos, langCode = "de",
        forms = mutableListOf(), senses = mutableListOf(), translations = mutableListOf(), wordLinkages = mutableListOf()
    )

    private val kaufen = entry("kaufen", "verb")
    private val anfangen = entry("anfangen", "verb")

    @Test
    fun select_stripsVerbPronounAndExclamationMarkButKeepsTags() {
        val withPronoun = form("ich kaufe", "active", "first-person", "indicative", "present", "singular")
        val selected = GermanFormRules.select(kaufen, withPronoun)
        assertEquals("kaufe", selected?.form, "the pronoun is not part of the form")
        assertEquals(withPronoun.tags, selected?.tags, "tags stay as they are")
        assertEquals(withPronoun.formId, selected?.formId, "the form keeps its id")

        assertEquals("kauf", GermanFormRules.select(kaufen, form("kauf!", "imperative", "singular"))?.form, "imperative without '!'")
        assertEquals(
            "kauft",
            GermanFormRules.select(kaufen, form("er/sie/es kauft", "indicative", "present", "singular", "third-person"))?.form,
            "the 'er/sie/es' cell"
        )
    }

    @Test
    fun select_keepsSeparableMainClauseFormsAndJoinedForms() {
        listOf(
            form("fängt an", "present", "singular", "third-person"),
            form("ich fange an", "first-person", "present", "singular"),
            form("fang an!", "imperative", "second-person", "singular"),
            form("er/sie/es anfängt", "present", "singular", "subordinate-clause", "third-person"),
            form("anzufangen", "extended", "infinitive"),
            form("anzufangende", "gerundive", "participle"),
            form("angefangen", "participle", "perfect"),
        ).forEach { f ->
            val selected = GermanFormRules.select(anfangen, f)
            assertEquals(
                f.form.removePrefix("ich ").removePrefix("er/sie/es ").removeSuffix("!"), selected?.form,
                "'${f.form}' is a cell of anfangen"
            )
        }
    }

    @Test
    fun select_dropsVerbPhrasesAndAuxiliaryMarkers() {
        listOf(
            form("ich habe gekauft", "active", "first-person", "indicative", "perfect", "singular"),
            form("gekauft haben", "infinitive", "perfect"),
            form("du wirst kaufen", "future-i", "indicative", "second-person", "singular"),
            form("habe gekauft", "first-person", "multiword-construction", "perfect", "singular", "subjunctive"),
            form("sie werden gekauft", "indicative", "plural", "present", "processual-passive", "third-person"),
            form("zu kaufen", "infinitive"),
            form("haben", "auxiliary", "perfect"),
            form("haben", "auxiliary"),
            form("haben or sein", "auxiliary"),
            form("kaufen Sie!", "honorific", "imperative", "present"),
            form("zu kaufende", "gerundive", "participle"),
            form("gekauft zu haben", "active", "extended", "infinitive"),
        ).forEach { f ->
            assertNull(GermanFormRules.select(kaufen, f), "'${f.form}' ${f.tags} is not stored")
        }
        assertNull(
            GermanFormRules.select(entry("siegen", "verb"), form("siegen Sie!", "honorific", "imperative", "present")),
            "'Sie' is never a separated prefix, even when the verb starts with it"
        )
    }

    private val gut = entry("gut", "adj")

    @Test
    fun select_keepsAdjectiveBaseFormsAndStrongDeclension() {
        listOf(
            form("gut", "positive"),
            form("besser", "comparative"),
            form("am besten", "superlative"),
            form("guten", "accusative", "masculine", "positive", "singular", "strong"),
            form("besserem", "comparative", "dative", "masculine", "singular", "strong"),
        ).forEach { f ->
            assertSame(f, GermanFormRules.select(gut, f), "'${f.form}' ${f.tags} is a base form or strong cell")
        }
    }

    @Test
    fun select_dropsAdjectiveWeakMixedAndPredicativeRows() {
        listOf(
            form("dem besseren", "comparative", "dative", "masculine", "singular", "weak"),
            form("(keine) guten", "mixed", "nominative", "plural", "positive"),
            form("guten", "indefinite", "mixed", "nominative", "plural"),
            form("er ist gut", "masculine", "positive", "predicative", "singular"),
            form("gut", "feminine", "predicative", "singular"),
            form("am besten", "masculine", "predicative", "singular", "superlative"),
        ).forEach { f ->
            assertNull(GermanFormRules.select(gut, f), "'${f.form}' ${f.tags} repeats a base form or strong cell")
        }
    }

    @Test
    fun dropRepeated_keepsOneAdjectiveRowPerCell() {
        val native = form("guter", "masculine", "nominative", "positive", "singular", "strong") to FormSource.NATIVE
        val english = form("guter", "masculine", "nominative", "singular", "strong", "without-article") to FormSource.EN
        val englishHeadword = form("guter", "masculine", "nominative", "singular", "strong") to FormSource.EN
        val otherCell = form("guter", "feminine", "genitive", "positive", "singular", "strong") to FormSource.NATIVE
        val comparative = form("besserer", "comparative", "masculine", "nominative", "singular", "strong") to FormSource.EN

        val result = GermanFormRules.dropRepeated("adj", listOf(native, english, englishHeadword, otherCell, comparative))

        assertEquals(listOf(native, otherCell, comparative), result, "each cell keeps one row, native first")
    }

    @Test
    fun select_leavesOtherPartsOfSpeechAlone() {
        val haus = entry("Haus", "noun")
        val genitive = form("des Hauses", "genitive", "singular")
        assertSame(genitive, GermanFormRules.select(haus, genitive), "noun forms with articles are kept")
    }

    @Test
    fun dropRepeated_keepsOneVerbRowPerCell() {
        val native1sg = form("kaufe", "active", "first-person", "indicative", "present", "singular") to FormSource.NATIVE
        val english1sg = form("kaufe", "first-person", "indicative", "present", "singular") to FormSource.EN
        val summary = form("kaufe", "present") to FormSource.NATIVE
        val imperativeVague = form("kauf", "imperative", "singular") to FormSource.NATIVE
        val imperative = form("kauf", "imperative", "present", "second-person", "singular") to FormSource.NATIVE
        val participleNative = form("gekauft", "participle", "perfect") to FormSource.NATIVE
        val participleEnglish = form("gekauft", "participle", "past") to FormSource.EN
        val past1sg = form("kaufte", "first-person", "indicative", "past", "singular") to FormSource.NATIVE
        val past3sg = form("kaufte", "indicative", "past", "singular", "third-person") to FormSource.NATIVE
        val pastEnglish = form("kaufte", "preterite") to FormSource.EN
        val subjunctive1sg = form("kaufte", "first-person", "past", "singular", "subjunctive-ii") to FormSource.NATIVE
        val subjunctiveFormalRare =
            form("kaufte", "first-person", "formal", "rare", "singular", "subjunctive", "subjunctive-ii") to FormSource.EN

        val result = GermanFormRules.dropRepeated(
            "verb",
            listOf(native1sg, english1sg, summary, imperativeVague, imperative, participleNative, participleEnglish,
                past1sg, past3sg, pastEnglish, subjunctive1sg, subjunctiveFormalRare)
        )

        assertEquals(
            listOf(native1sg, imperative, participleNative, past1sg, past3sg, subjunctive1sg), result,
            "a cell keeps its first, most specific row; different cells with the same text all stay"
        )
    }

    @Test
    fun dropRepeated_keepsNativeNounCellsAndOneBareRowPerWord() {
        val native = listOf(
            form("der Spindoktor", "nominative", "singular"),
            form("die Spindoktoren", "nominative", "plural"),
            form("des Spindoktors", "genitive", "singular"),
            form("der Spindoktoren", "genitive", "plural"),
            form("dem Spindoktor", "dative", "singular"),
            form("den Spindoktoren", "dative", "plural"),
            form("den Spindoktor", "accusative", "singular"),
            form("die Spindoktoren", "accusative", "plural"),
        ).map { it to FormSource.NATIVE }
        val summaryGenitive = form("Spindoktors", "genitive") to FormSource.EN
        val summaryPlural = form("Spindoktoren", "plural") to FormSource.EN
        val feminine = form("Spindoktorin", "feminine") to FormSource.EN
        val bareNominative = form("Spindoktor", "nominative", "singular") to FormSource.EN
        val bareNominativePlural = form("Spindoktoren", "definite", "nominative", "plural") to FormSource.EN
        val bareGenitive = form("Spindoktors", "genitive", "singular") to FormSource.EN
        val bareRepeats = listOf(
            form("Spindoktoren", "definite", "genitive", "plural"),
            form("Spindoktor", "dative", "singular"),
            form("Spindoktoren", "dative", "definite", "plural"),
            form("Spindoktor", "accusative", "singular"),
            form("Spindoktoren", "accusative", "definite", "plural"),
        ).map { it to FormSource.EN }

        val result = GermanFormRules.dropRepeated(
            "noun",
            native + listOf(summaryGenitive, summaryPlural, feminine, bareNominative, bareNominativePlural, bareGenitive) + bareRepeats
        )

        assertEquals(
            native + listOf(feminine, bareNominative, bareNominativePlural, bareGenitive), result,
            "the native cells with articles stay, plus each bare word once"
        )
    }

    @Test
    fun dropRepeated_keepsBareNounCellsWhenTheNativeEditionHasNoTable() {
        val english = listOf(
            form("Spindoktor", "nominative", "singular"),
            form("Spindoktors", "genitive", "singular"),
            form("Spindoktor", "dative", "singular"),
            form("Spindoktor", "accusative", "singular"),
        ).map { it to FormSource.EN }

        assertEquals(english, GermanFormRules.dropRepeated("noun", english), "bare cells are the table when nothing else fills them")
    }

    @Test
    fun dropRepeated_dropsEnglishFormsTheNativeEditionCovers() {
        val native = listOf(
            form("Spindoktoren", "nominative", "plural") to FormSource.NATIVE,
            form("Spindoktors", "genitive", "singular") to FormSource.NATIVE,
        )
        val coveredEnglish = listOf(
            form("Spindoktoren", "definite", "nominative", "plural") to FormSource.EN,
            form("Spindoktors", "genitive") to FormSource.EN,
        )
        val keptEnglish = listOf(
            form("Spindoktoren", "dative", "plural") to FormSource.EN,
            form("Spindoktorin", "feminine") to FormSource.EN,
        )

        val result = GermanFormRules.dropRepeated("name", native + coveredEnglish + keptEnglish)

        assertEquals(native + keptEnglish, result, "English forms whose text and grammar the native edition has are dropped")
    }

    @Test
    fun dropRepeated_keepsBareEnglishFormWhenNativeHasItOnlyInsideAPhrase() {
        val native = listOf(form("die Spindoktoren", "nominative", "plural") to FormSource.NATIVE)
        val english = listOf(form("Spindoktoren", "nominative", "plural") to FormSource.EN)

        val result = GermanFormRules.dropRepeated("name", native + english)

        assertEquals(native + english, result, "a bare word stays searchable when the native edition only has it with an article")
    }
}
