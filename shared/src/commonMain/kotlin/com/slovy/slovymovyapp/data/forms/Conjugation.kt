package com.slovy.slovymovyapp.data.forms

import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.dictionary.DictionaryPos

/** Resolved key→value map stored inside [GridCell.Data]; built from [FormTag] enum values. */
typealias FormTags = Map<String, String>

/** A complete inflection/conjugation template for a language+POS combination. */
data class ConjugationScheme(
    val templateId: String,
    val language: Language,
    val pos: DictionaryPos,
    val tagResolver: SchemeTagResolver = DefaultSchemeTagResolver,
    val views: List<SchemeView>,
)

/** One table view within a scheme (e.g. a short summary or a full paradigm table). */
data class SchemeView(
    val viewId: String,
    val description: String,
    val grid: List<List<GridCell>>,
)

/** A single cell in a scheme grid. */
sealed class GridCell {
    abstract val rowspan: Int
    abstract val colspan: Int

    data class RowHeader(
        val label: String,
        override val rowspan: Int = 1,
        override val colspan: Int = 1,
    ) : GridCell()

    data class ColHeader(
        val label: String,
        override val rowspan: Int = 1,
        override val colspan: Int = 1,
    ) : GridCell()

    data class Data(
        val requiredTags: FormTags,
        val preferredTags: FormTags = emptyMap(),
        override val rowspan: Int = 1,
        override val colspan: Int = 1,
    ) : GridCell()

    data class Empty(
        override val rowspan: Int = 1,
        override val colspan: Int = 1,
    ) : GridCell()
}

// ── DSL builders ────────────────────────────────────────────────────────────

fun conjugationScheme(
    templateId: String,
    language: Language,
    pos: DictionaryPos,
    tagResolver: SchemeTagResolver = DefaultSchemeTagResolver,
    block: SchemeBuilder.() -> Unit,
): ConjugationScheme = SchemeBuilder(templateId, language, pos, tagResolver).apply(block).build()

class SchemeBuilder(
    private val id: String,
    private val lang: Language,
    private val pos: DictionaryPos,
    private val tagResolver: SchemeTagResolver,
) {
    private val views = mutableListOf<SchemeView>()

    fun view(viewId: String, description: String, block: ViewBuilder.() -> Unit) {
        views += ViewBuilder(viewId, description).apply(block).build()
    }

    fun build() = ConjugationScheme(id, lang, pos, tagResolver, views)
}

class ViewBuilder(private val viewId: String, private val desc: String) {
    private val grid = mutableListOf<List<GridCell>>()

    fun row(block: RowBuilder.() -> Unit) {
        grid += RowBuilder().apply(block).build()
    }

    fun build() = SchemeView(viewId, desc, grid)
}

class RowBuilder {
    private val cells = mutableListOf<GridCell>()

    fun rowHeader(label: String, rowspan: Int = 1, colspan: Int = 1) {
        cells += GridCell.RowHeader(label, rowspan, colspan)
    }

    fun colHeader(label: String, rowspan: Int = 1, colspan: Int = 1) {
        cells += GridCell.ColHeader(label, rowspan, colspan)
    }

    fun data(
        vararg tags: FormTag,
        rowspan: Int = 1,
        colspan: Int = 1,
        supporting: Set<FormTag> = emptySet(),
    ) {
        cells += GridCell.Data(
            requiredTags = tags.associate { it.key to it.value },
            preferredTags = supporting.associate { it.key to it.value },
            rowspan = rowspan,
            colspan = colspan
        )
    }

    fun empty(rowspan: Int = 1, colspan: Int = 1) {
        cells += GridCell.Empty(rowspan, colspan)
    }

    fun build(): List<GridCell> = cells.toList()
}

/**
 * Per-language strategy for selecting which [ConjugationScheme]s apply to a given word.
 *
 * Implementations can inspect raw input forms (DB tag strings) to narrow the candidate
 * schemes beyond simple POS matching — e.g., choosing between imperfective and perfective
 * verb templates in Russian.
 */
fun interface ConjugationSchemeProvider {
    /**
     * Returns the schemes that should be tried for [pos] given the word's [forms].
     *
     * [forms] are provided *before* any preprocessing, as raw DB tag strings, so
     * implementations can check for aspect, animacy, or other discriminating markers.
     */
    fun schemeFor(pos: DictionaryPos, forms: List<SchemeInputForm>): ConjugationScheme?
}

/**
 * Per-scheme strategy for turning raw DB tag strings into typed [FormTag] values.
 *
 * This allows language-specific normalization rules while keeping grid matching logic generic.
 */
data class SchemeInputForm(
    val tags: List<String> = emptyList(),
    val form: String
)

data class SchemeCellCandidate(
    val missingRequiredTags: Int,
    val matchedPreferredTags: Int,
    val extraKnownTags: Int,
    val form: String
)

fun interface SchemeTagResolver {
    fun resolve(dbTags: Iterable<String>): List<FormTag>

    fun preprocessForms(forms: List<SchemeInputForm>, lemma: String?): List<SchemeInputForm> = forms

    fun selectCandidate(candidates: List<SchemeCellCandidate>): SchemeCellCandidate? =
        candidates.firstOrNull()
}

object DefaultSchemeTagResolver : SchemeTagResolver {
    override fun resolve(dbTags: Iterable<String>): List<FormTag> = TagMapping.resolve(dbTags)
}