package com.slovy.slovymovyapp.data.export

import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.favorites.SenseCardExportRow
import com.slovy.slovymovyapp.data.remote.LearnerLevel
import com.slovy.slovymovyapp.data.learning.CardState
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

enum class WordListExportFormat(val fileExtension: String, val mimeType: String) {
    CSV("csv", "text/csv"),
    TSV("tsv", "text/tab-separated-values"),
}

/** One exported word-list line: a favorited sense with whatever detail could be resolved locally. */
data class WordListExportRow(
    val lemma: String,
    val pos: String?,
    val definition: String?,
    val level: LearnerLevel?,
    val translations: Map<Language, String>,
    val example: String?,
    val addedAtEpochMs: Long,
    val stats: WordListExportStats?,
) {
    val hasDetails: Boolean get() = definition != null
}

/** Learning-progress columns aggregated over one sense's active cards. */
data class WordListExportStats(
    val cardCount: Int,
    val state: String,
    val reps: Long,
    val lapses: Long,
    val maxStability: Double,
    val nextDueEpochMs: Long,
    val lastReviewEpochMs: Long?,
) {
    companion object {
        /**
         * Collapses per-family cards into one row of progress values. The state summary is the
         * sense's furthest stage: `review` if any card graduated, else `learning`, else `new`.
         */
        fun aggregate(cards: List<SenseCardExportRow>): WordListExportStats? {
            if (cards.isEmpty()) return null
            val state = when {
                cards.any { it.state == CardState.REVIEW } -> "review"
                cards.any { it.state == CardState.LEARNING || it.state == CardState.RELEARNING } -> "learning"
                else -> "new"
            }
            return WordListExportStats(
                cardCount = cards.size,
                state = state,
                reps = cards.sumOf { it.reps },
                lapses = cards.sumOf { it.lapses },
                maxStability = cards.maxOf { it.stability },
                nextDueEpochMs = cards.minOf { it.due },
                lastReviewEpochMs = cards.mapNotNull { it.lastReview }.maxOrNull(),
            )
        }
    }
}

/**
 * Renders export rows into CSV or TSV text.
 *
 * Column names are a stable, machine-oriented protocol (spreadsheet formulas and Anki field
 * mappings break if they follow the UI locale), so they are intentionally not localized.
 *
 * CSV output targets spreadsheet apps: UTF-8 BOM so Excel decodes non-ASCII correctly,
 * RFC 4180 quoting, CRLF row separators, and a header row.
 *
 * TSV output targets flashcard imports (Anki, Quizlet): `#`-prefixed Anki file headers instead
 * of a header row, and tab/newline characters inside values replaced by spaces because TSV has
 * no quoting convention those importers agree on.
 */
class WordListExportContent(
    private val format: WordListExportFormat,
    private val translationLanguages: List<Language>,
    private val includeStats: Boolean,
    private val timeZone: TimeZone,
) {

    fun render(rows: List<WordListExportRow>): String {
        val columns = columnNames()
        return buildString {
            when (format) {
                WordListExportFormat.CSV -> {
                    append(UTF8_BOM)
                    appendCsvRow(columns)
                }

                WordListExportFormat.TSV -> {
                    append("#separator:tab\n")
                    append("#html:false\n")
                    append("#columns:${columns.joinToString("\t")}\n")
                }
            }
            rows.forEach { row ->
                val values = cellValues(row)
                when (format) {
                    WordListExportFormat.CSV -> appendCsvRow(values)
                    WordListExportFormat.TSV -> append(values.joinToString("\t") { sanitizeTsv(it) }).append('\n')
                }
            }
        }
    }

    fun columnNames(): List<String> = buildList {
        add("lemma")
        add("part_of_speech")
        add("definition")
        add("level")
        translationLanguages.forEach { add("translation_${it.code}") }
        add("example")
        add("added")
        if (includeStats) {
            add("cards")
            add("state")
            add("reps")
            add("lapses")
            add("stability")
            add("next_due")
            add("last_review")
        }
    }

    private fun cellValues(row: WordListExportRow): List<String> = buildList {
        add(row.lemma)
        add(row.pos.orEmpty())
        add(row.definition.orEmpty())
        add(row.level?.name.orEmpty())
        translationLanguages.forEach { add(row.translations[it].orEmpty()) }
        add(row.example.orEmpty())
        add(formatDate(row.addedAtEpochMs))
        if (includeStats) {
            val stats = row.stats
            add(stats?.cardCount?.toString().orEmpty())
            add(stats?.state.orEmpty())
            add(stats?.reps?.toString().orEmpty())
            add(stats?.lapses?.toString().orEmpty())
            add(stats?.let { formatStability(it.maxStability) }.orEmpty())
            add(stats?.let { formatDate(it.nextDueEpochMs) }.orEmpty())
            add(stats?.lastReviewEpochMs?.let { formatDate(it) }.orEmpty())
        }
    }

    private fun StringBuilder.appendCsvRow(values: List<String>) {
        append(values.joinToString(",") { quoteCsv(it) }).append("\r\n")
    }

    private fun quoteCsv(value: String): String {
        val needsQuoting = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        if (!needsQuoting) return value
        return "\"${value.replace("\"", "\"\"")}\""
    }

    private fun sanitizeTsv(value: String): String =
        value.replace(TSV_UNSAFE, " ")

    @OptIn(ExperimentalTime::class)
    private fun formatDate(epochMs: Long): String {
        val date = Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(timeZone).date
        return date.toString()
    }

    private fun formatStability(stability: Double): String {
        val hundredths = (stability * 100).toLong()
        return "${hundredths / 100}.${(hundredths % 100).toString().padStart(2, '0')}"
    }

    companion object {
        const val UTF8_BOM = "\uFEFF"
        private val TSV_UNSAFE = Regex("[\t\n\r]")
    }
}
