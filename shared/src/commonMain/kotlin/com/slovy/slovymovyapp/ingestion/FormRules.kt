@file:OptIn(ExperimentalUuidApi::class)

package com.slovy.slovymovyapp.ingestion

import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.dictionary.FormSource
import com.slovy.slovymovyapp.util.stripAccents
import kotlin.uuid.ExperimentalUuidApi

/**
 * Every rule deciding which raw forms of a word are ingested: the rules shared by all languages plus
 * the [LanguageFormRules] of the word's language. Ingestion sees forms only through this class, so
 * POS clustering and storage work on the same forms.
 *
 * Wiktionary editions differ in how much of a paradigm they write out; the language rules keep one
 * row per table cell and every word a learner can type or meet in text, and drop the rest.
 */
class IngestibleForms private constructor(private val language: LanguageFormRules) {

    /** The forms of [entry] that are ingested, rewritten where the language rules say so. */
    fun of(entry: ExtractedWordEntry): List<ExtractedWordForm> =
        entry.forms.mapNotNull { language.select(entry, it) }

    /**
     * The forms stored for one lemma_pos, from the ingestible forms of all its entries in insertion
     * order (native-edition forms first). [entryPos] is the raw part of speech of the lemma_pos.
     *
     * A form repeating another with the same text, normalized text, tags and source is dropped
     * first; source-specific forms are never collapsed. The language rules then drop forms that
     * repeat another table cell.
     */
    fun merge(
        entryPos: String,
        forms: List<Pair<ExtractedWordForm, FormSource>>
    ): List<Pair<ExtractedWordForm, FormSource>> {
        val unique = forms.distinctBy { (f, source) -> FormKey(f.form, stripAccents(f.form), f.tags.toSet(), source) }
        return language.dropRepeated(entryPos, unique)
    }

    private data class FormKey(
        val form: String,
        val formNormalized: String,
        val tags: Set<String>,
        val source: FormSource
    )

    companion object {
        fun forLanguage(langCode: String): IngestibleForms = IngestibleForms(
            when (langCode) {
                Language.GERMAN.code -> GermanFormRules
                Language.DUTCH.code -> DutchFormRules
                else -> LanguageFormRules.None
            }
        )
    }
}

/** One language's rules for which raw forms are ingested. The defaults keep every form. */
interface LanguageFormRules {
    /**
     * The form of [entry] as it is ingested, or null to drop it. May rewrite the text
     * (e.g. "ich kaufe" -> "kaufe"); the tags are never changed.
     */
    fun select(entry: ExtractedWordEntry, form: ExtractedWordForm): ExtractedWordForm? = form

    /**
     * Removes forms of one lemma_pos that repeat another of its forms, given in insertion order
     * (native-edition forms first). [entryPos] is the raw part of speech of the lemma_pos (e.g. "verb").
     */
    fun dropRepeated(
        entryPos: String,
        forms: List<Pair<ExtractedWordForm, FormSource>>
    ): List<Pair<ExtractedWordForm, FormSource>> = forms

    /** A language whose forms are all ingested as extracted. */
    object None : LanguageFormRules
}

/**
 * The Dutch Wiktionary lists the binary, hexadecimal and Roman spellings of numerals as forms
 * ("1010" of tien), marking them with a note; they are notations, not words.
 */
object DutchFormRules : LanguageFormRules {
    private val notationNotes = setOf("binair", "hexadecimaal", "romeins")

    override fun select(entry: ExtractedWordEntry, form: ExtractedWordForm): ExtractedWordForm? =
        form.takeUnless { form.note?.trim()?.lowercase() in notationNotes }
}

/**
 * The German Wiktionary writes out every compound tense in both passive voices, each person with its
 * pronoun, and the weak and mixed adjective declensions with their articles; the English edition
 * repeats most of it. A verb carried ~300 forms against ~30 in Russian.
 *
 * Verbs keep one row per cell of the simple tenses (present, past, subjunctive I and II), the
 * imperative, the infinitive and the participles, as single words plus the split main-clause form
 * of separable verbs ("fängt an"). Adjectives keep one row per cell of the three base forms
 * ("gut", "besser", "am besten") and the strong declension, which holds every declined word.
 * Nouns keep the native cells with their articles plus one bare row per word for search.
 */
object GermanFormRules : LanguageFormRules {

    private const val VERB = "verb"
    private const val ADJECTIVE = "adj"
    private const val NOUN = "noun"

    override fun select(entry: ExtractedWordEntry, form: ExtractedWordForm): ExtractedWordForm? = when (entry.pos) {
        VERB -> selectVerbForm(entry.word, form)
        ADJECTIVE -> form.takeUnless { isDroppedAdjectiveCell(it.tags) }
        else -> form
    }

    override fun dropRepeated(
        entryPos: String,
        forms: List<Pair<ExtractedWordForm, FormSource>>
    ): List<Pair<ExtractedWordForm, FormSource>> = when (entryPos) {
        VERB -> dropRepeatedCells(forms, ::verbCell)
        ADJECTIVE -> dropRepeatedCells(forms, ::adjectiveCell)
        NOUN -> dropRepeatedNounCells(forms)
        else -> dropCoveredEnglishForms(forms)
    }

    private fun isMultiWord(text: String) = ' ' in text.trim()

    /**
     * Keeps one row per cell: a form is dropped when another form with the same text fills the same
     * cell (the earlier one wins, so native beats English) or a more specific one
     * ("kaufe!" [imperative, singular] next to [imperative, second-person, singular]).
     */
    private fun dropRepeatedCells(
        forms: List<Pair<ExtractedWordForm, FormSource>>,
        cellOf: (List<String>) -> Set<String>
    ): List<Pair<ExtractedWordForm, FormSource>> {
        val cells = forms.map { (f, _) -> f.form to cellOf(f.tags) }
        return forms.filterIndexed { i, _ ->
            val (text, cell) = cells[i]
            cells.indices.none { j ->
                j != i && cells[j].first == text && cells[j].second.containsAll(cell) &&
                    (cells[j].second.size > cell.size || j < i)
            }
        }
    }

    // --- nouns

    private val nounCellTags = setOf("nominative", "genitive", "dative", "accusative", "singular", "plural")

    private fun nounCell(tags: List<String>): Set<String> = tags.filterTo(mutableSetOf()) { it in nounCellTags }

    /**
     * The native edition writes each case with its article ("des Spindoktors"), the only place a noun's
     * gender is recorded, and those rows are the table. The English edition repeats the cells bare
     * ("Spindoktors"); where a native row fills the cell, a bare row only serves search and the reader,
     * so one is kept per distinct word. Nouns the native edition does not decline keep their bare cells.
     */
    private fun dropRepeatedNounCells(
        forms: List<Pair<ExtractedWordForm, FormSource>>
    ): List<Pair<ExtractedWordForm, FormSource>> {
        val cells = dropRepeatedCells(forms, ::nounCell)
        val nativeCells = cells.filter { it.second == FormSource.NATIVE }.map { nounCell(it.first.tags) }
            .filterTo(mutableSetOf()) { it.isNotEmpty() }
        val keptTexts = mutableSetOf<String>()
        return cells.filter { (f, source) ->
            val searchOnly = source == FormSource.EN && nounCell(f.tags) in nativeCells
            keptTexts.add(f.form) || !searchOnly
        }
    }

    // --- adjectives

    // Weak and mixed declensions repeat the strong declension's words ("dem schmaleren", "keine guten");
    // the predicative does not inflect, so its m/f/n/pl rows repeat the base form ("er ist gut").
    private val droppedAdjectiveTags = setOf("weak", "mixed", "predicative")

    private fun isDroppedAdjectiveCell(tags: List<String>) = tags.any { it in droppedAdjectiveTags }

    private val adjectiveCellTags = setOf(
        "positive", "comparative", "superlative", "strong",
        "nominative", "genitive", "dative", "accusative", "masculine", "feminine", "neuter", "singular", "plural"
    )

    private fun adjectiveCell(tags: List<String>): Set<String> {
        val cell = tags.filterTo(mutableSetOf()) { it in adjectiveCellTags }
        // the English edition leaves the positive degree unmarked
        if ("comparative" !in cell && "superlative" !in cell) cell += "positive"
        return cell
    }

    // --- verbs

    // The pronoun the German edition writes into each person cell ("ich kaufe", "er/sie/es kauft").
    private val leadingPronoun = Regex("^(ich|du|er/sie/es|sie/er/es|er|sie|es|wir|ihr)\\s+", RegexOption.IGNORE_CASE)

    // Tags that place a verb form in a table cell; the rest (active, auxiliary, main-clause, …) do not.
    private val verbCellTags = setOf(
        "present", "past", "preterite", "subjunctive-i", "subjunctive-ii", "imperative", "infinitive",
        "participle", "indicative", "first-person", "second-person", "third-person", "singular", "plural", "perfect"
    )
    private val verbTenseTags = setOf("present", "past", "preterite", "subjunctive-i", "subjunctive-ii", "imperative", "infinitive", "participle")

    private fun selectVerbForm(lemma: String, form: ExtractedWordForm): ExtractedWordForm? {
        val tags = form.tags
        // "haben", "sein", "haben or sein": which auxiliary the perfect takes, not a form of the verb
        if ("auxiliary" in tags && tags.none { it in verbTenseTags }) return null
        val text = form.form.trim().replace(leadingPronoun, "").trimEnd('!').trim()
        // compound tenses and passives ("habe gekauft", "wird gekauft"), "zu kaufen", "zu kaufende",
        // "gekauft zu haben", "kaufen Sie!"; the one-word forms of separable verbs ("anzufangen",
        // "anzufangende") are words in their own right and stay
        if (isMultiWord(text) && !isSeparableSplit(lemma, text)) return null
        return if (text == form.form) form else form.copy(form = text)
    }

    /** "fängt an" of anfangen: the finite verb followed by the separated prefix. */
    private fun isSeparableSplit(lemma: String, text: String): Boolean {
        val parts = text.split(' ')
        if (parts.size != 2) return false
        val particle = parts[1].lowercase()
        val word = lemma.lowercase()
        // "siegen Sie!" is a polite imperative, not "sie" split off "siegen"
        return particle != "sie" && particle.length < word.length && word.startsWith(particle)
    }

    private fun verbCell(tags: List<String>): Set<String> {
        val cell = tags.filter { it in verbCellTags }.mapTo(mutableSetOf()) { if (it == "preterite") "past" else it }
        // the English edition calls Partizip II "past", the German edition "perfect"
        if ("participle" in cell && cell.remove("perfect")) cell += "past"
        return cell
    }

    // --- everything else

    // English-edition tags that describe how a cell is written rather than its grammar.
    private val englishLayoutTags = setOf(
        "definite", "indefinite", "includes-article", "without-article", "with-article",
        "usually-without-article", "multiword-construction", "canonical", "future", "subjunctive"
    )
    private val tagSynonyms = mapOf("preterite" to "past", "participle-2" to "participle")

    private fun grammar(tags: List<String>, ignored: Set<String>): Set<String> =
        tags.filterNot { it in ignored }.mapTo(mutableSetOf()) { tagSynonyms[it] ?: it }

    /**
     * Drops an English-edition form when a kept native form has exactly its text and all its grammar
     * tags. The text must match as written: search and the reader match whole forms, so a bare
     * "Spindoktoren" is not covered by the native "die Spindoktoren".
     */
    private fun dropCoveredEnglishForms(
        forms: List<Pair<ExtractedWordForm, FormSource>>
    ): List<Pair<ExtractedWordForm, FormSource>> {
        val nativeGrammar = mutableMapOf<String, MutableList<Set<String>>>()
        forms.filter { it.second == FormSource.NATIVE }.forEach { (f, _) ->
            nativeGrammar.getOrPut(f.form.trim()) { mutableListOf() } += grammar(f.tags, emptySet())
        }
        return forms.filterNot { (f, source) ->
            source == FormSource.EN && nativeGrammar[f.form.trim()].orEmpty().any { native ->
                native.containsAll(grammar(f.tags, englishLayoutTags))
            }
        }
    }
}
