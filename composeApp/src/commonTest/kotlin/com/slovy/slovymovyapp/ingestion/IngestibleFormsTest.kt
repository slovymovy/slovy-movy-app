package com.slovy.slovymovyapp.ingestion

import com.slovy.slovymovyapp.data.dictionary.FormSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

class IngestibleFormsTest {

    private val entryId = Uuid.random()

    private fun form(text: String, vararg tags: String, note: String? = null) =
        ExtractedWordForm(formId = Uuid.random(), entryId = entryId, tags = tags.toMutableList(), form = text, note = note)

    private fun entry(word: String, pos: String, langCode: String, forms: List<ExtractedWordForm>) = ExtractedWordEntry(
        entryId = entryId, word = word, pos = pos, langCode = langCode,
        forms = forms.toMutableList(), senses = mutableListOf(), translations = mutableListOf(), wordLinkages = mutableListOf()
    )

    @Test
    fun of_keepsEveryFormForLanguagesWithoutRules() {
        listOf("en", "ru", "pl").forEach { code ->
            val forms = listOf(form("ten", "plural"), form("1010", note = "binair"), form("ich kaufe", "first-person"))
            assertEquals(
                forms,
                IngestibleForms.forLanguage(code).of(entry("tien", "verb", code, forms)),
                "$code forms are ingested as extracted"
            )
        }
    }

    @Test
    fun of_dropsDutchNumeralNotations() {
        val kept = form("tienen", "plural")
        val notations = listOf(form("1010", note = "binair"), form("A", note = " Hexadecimaal "), form("X", note = "Romeins"))
        val forms = IngestibleForms.forLanguage("nl").of(entry("tien", "num", "nl", listOf(kept) + notations))
        assertEquals(listOf(kept), forms, "binary, hexadecimal and Roman notations are not Dutch words")
    }

    @Test
    fun of_appliesGermanRules() {
        val forms = IngestibleForms.forLanguage("de").of(
            entry(
                "kaufen", "verb", "de",
                listOf(form("ich kaufe", "first-person", "present", "singular"), form("habe gekauft", "perfect"))
            )
        )
        assertEquals(listOf("kaufe"), forms.map { it.form }, "the pronoun is stripped and the compound tense dropped")
    }

    @Test
    fun merge_dropsRepeatsButKeepsSourceSpecificForms() {
        val native = form("tienen", "plural")
        val nativeRepeat = form("tienen", "plural")
        val english = form("tienen", "plural")
        val otherTags = form("tienen", "plural", "dative")
        val merged = IngestibleForms.forLanguage("nl").merge(
            "num",
            listOf(
                native to FormSource.NATIVE,
                nativeRepeat to FormSource.NATIVE,
                english to FormSource.EN,
                otherTags to FormSource.NATIVE
            )
        )
        assertEquals(
            listOf(native to FormSource.NATIVE, english to FormSource.EN, otherTags to FormSource.NATIVE),
            merged,
            "the first of each text/tags/source repeat is kept; another source or tag set is a different row"
        )
    }
}
