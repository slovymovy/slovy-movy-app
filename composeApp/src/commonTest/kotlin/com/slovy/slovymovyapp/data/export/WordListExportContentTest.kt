package com.slovy.slovymovyapp.data.export

import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.favorites.SenseCardExportRow
import com.slovy.slovymovyapp.data.remote.LearnerLevel
import com.slovy.slovymovyapp.data.learning.CardState
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WordListExportContentTest {

    private val utc = TimeZone.UTC

    // 2026-03-05T10:15:00Z
    private val addedAt = 1_772_705_700_000L

    private fun row(
        lemma: String = "huis",
        definition: String? = "a building for living in",
        translations: Map<Language, String> = mapOf(Language.ENGLISH to "house"),
        example: String? = "Ik ben thuis in mijn huis.",
        stats: WordListExportStats? = null,
    ) = WordListExportRow(
        lemma = lemma,
        pos = "noun",
        definition = definition,
        level = LearnerLevel.A1,
        translations = translations,
        example = example,
        addedAtEpochMs = addedAt,
        stats = stats,
    )

    private fun content(
        format: WordListExportFormat,
        translationLanguages: List<Language> = listOf(Language.ENGLISH),
        includeStats: Boolean = false,
    ) = WordListExportContent(
        format = format,
        translationLanguages = translationLanguages,
        includeStats = includeStats,
        timeZone = utc,
    )

    @Test
    fun csvStartsWithBomAndHeader() {
        val rendered = content(WordListExportFormat.CSV).render(listOf(row()))
        assertTrue(
            rendered.startsWith(WordListExportContent.UTF8_BOM),
            "CSV must start with a UTF-8 BOM for Excel compatibility"
        )
        val header = rendered.removePrefix(WordListExportContent.UTF8_BOM).lineSequence().first()
        assertEquals(
            "lemma,part_of_speech,definition,level,translation_en,example,added",
            header.trimEnd('\r'),
            "Unexpected CSV header row"
        )
    }

    @Test
    fun csvRowContainsValuesAndIsoDate() {
        val rendered = content(WordListExportFormat.CSV).render(listOf(row()))
        val dataLine = rendered.split("\r\n")[1]
        assertEquals(
            "huis,noun,a building for living in,A1,house,Ik ben thuis in mijn huis.,2026-03-05",
            dataLine,
            "Unexpected CSV data row"
        )
    }

    @Test
    fun csvQuotesSeparatorsQuotesAndNewlines() {
        val rendered = content(WordListExportFormat.CSV).render(
            listOf(
                row(
                    definition = "one, two \"three\"",
                    example = "line1\nline2",
                )
            )
        )
        val body = rendered.removePrefix(WordListExportContent.UTF8_BOM)
        assertTrue(
            body.contains("\"one, two \"\"three\"\"\""),
            "Comma/quote values must be quoted with doubled inner quotes; got: $body"
        )
        assertTrue(
            body.contains("\"line1\nline2\""),
            "Values with newlines must be quoted; got: $body"
        )
    }

    @Test
    fun tsvStartsWithAnkiHeadersInsteadOfHeaderRow() {
        val rendered = content(WordListExportFormat.TSV).render(listOf(row()))
        val lines = rendered.trimEnd('\n').split("\n")
        assertEquals("#separator:tab", lines[0], "First TSV line must declare the Anki separator")
        assertEquals("#html:false", lines[1], "Second TSV line must declare plain-text content")
        assertEquals(
            "#columns:lemma\tpart_of_speech\tdefinition\tlevel\ttranslation_en\texample\tadded",
            lines[2],
            "Third TSV line must name the columns for Anki"
        )
        assertEquals(4, lines.size, "TSV must contain exactly the headers plus one data line")
        assertEquals(
            "huis\tnoun\ta building for living in\tA1\thouse\tIk ben thuis in mijn huis.\t2026-03-05",
            lines[3],
            "Unexpected TSV data row"
        )
    }

    @Test
    fun tsvReplacesTabsAndNewlinesInValues() {
        val rendered = content(WordListExportFormat.TSV).render(
            listOf(row(definition = "with\ttab", example = "with\nnewline"))
        )
        val dataLine = rendered.trimEnd('\n').split("\n").last()
        assertEquals(
            "huis\tnoun\twith tab\tA1\thouse\twith newline\t2026-03-05",
            dataLine,
            "Tabs and newlines inside TSV values must become spaces"
        )
    }

    @Test
    fun translationColumnsFollowConfiguredLanguagesAndMissingOnesStayEmpty() {
        val rendered = content(
            WordListExportFormat.CSV,
            translationLanguages = listOf(Language.ENGLISH, Language.RUSSIAN),
        ).render(
            listOf(row(translations = mapOf(Language.RUSSIAN to "дом; здание")))
        )
        val body = rendered.removePrefix(WordListExportContent.UTF8_BOM)
        val header = body.split("\r\n")[0]
        val dataLine = body.split("\r\n")[1]
        assertEquals(
            "lemma,part_of_speech,definition,level,translation_en,translation_ru,example,added",
            header,
            "Header must contain one column per configured translation language, in order"
        )
        assertEquals(
            "huis,noun,a building for living in,A1,,дом; здание,Ik ben thuis in mijn huis.,2026-03-05",
            dataLine,
            "Missing translation languages must render as empty cells"
        )
    }

    @Test
    fun unresolvedSenseExportsLemmaAndDateWithEmptyDetailColumns() {
        val unresolved = WordListExportRow(
            lemma = "fiets",
            pos = null,
            definition = null,
            level = null,
            translations = emptyMap(),
            example = null,
            addedAtEpochMs = addedAt,
            stats = null,
        )
        val rendered = content(WordListExportFormat.CSV).render(listOf(unresolved))
        val dataLine = rendered.removePrefix(WordListExportContent.UTF8_BOM).split("\r\n")[1]
        assertEquals("fiets,,,,,,2026-03-05", dataLine, "Unresolved senses must keep lemma and date")
    }

    @Test
    fun statsColumnsAppendedWhenEnabled() {
        val stats = WordListExportStats(
            cardCount = 3,
            state = "review",
            reps = 12,
            lapses = 1,
            maxStability = 15.25,
            nextDueEpochMs = addedAt,
            lastReviewEpochMs = addedAt,
        )
        val rendered = content(WordListExportFormat.CSV, includeStats = true)
            .render(listOf(row(stats = stats)))
        val body = rendered.removePrefix(WordListExportContent.UTF8_BOM)
        val header = body.split("\r\n")[0]
        val dataLine = body.split("\r\n")[1]
        assertEquals(
            "lemma,part_of_speech,definition,level,translation_en,example,added," +
                "cards,state,reps,lapses,stability,next_due,last_review",
            header,
            "Stats columns must be appended after the content columns"
        )
        assertTrue(
            dataLine.endsWith("2026-03-05,3,review,12,1,15.25,2026-03-05,2026-03-05"),
            "Stats values must render in column order; got: $dataLine"
        )
    }

    @Test
    fun statsCellsStayEmptyForSensesWithoutCards() {
        val rendered = content(WordListExportFormat.CSV, includeStats = true)
            .render(listOf(row(stats = null)))
        val dataLine = rendered.removePrefix(WordListExportContent.UTF8_BOM).split("\r\n")[1]
        assertTrue(
            dataLine.endsWith("2026-03-05,,,,,,,"),
            "Senses without cards must render empty stats cells; got: $dataLine"
        )
    }

    @Test
    fun aggregateSummarizesAcrossFamilies() {
        val cards = listOf(
            cardRow(state = CardState.REVIEW, stability = 20.0, due = 300L, lastReview = 250L, reps = 8, lapses = 1),
            cardRow(state = CardState.LEARNING, stability = 2.5, due = 100L, lastReview = null, reps = 3, lapses = 0),
        )
        val stats = WordListExportStats.aggregate(cards)
        assertEquals(2, stats?.cardCount, "Card count must include every active card")
        assertEquals("review", stats?.state, "State summary must reflect the furthest stage")
        assertEquals(11L, stats?.reps, "Reps must sum across cards")
        assertEquals(1L, stats?.lapses, "Lapses must sum across cards")
        assertEquals(20.0, stats?.maxStability, "Stability must be the maximum across cards")
        assertEquals(100L, stats?.nextDueEpochMs, "Next due must be the earliest due")
        assertEquals(250L, stats?.lastReviewEpochMs, "Last review must be the latest review")
    }

    @Test
    fun aggregateStateFallsBackToLearningThenNew() {
        val learning = WordListExportStats.aggregate(
            listOf(cardRow(state = CardState.RELEARNING), cardRow(state = CardState.NEW))
        )
        assertEquals("learning", learning?.state, "Relearning cards must summarize as learning")

        val new = WordListExportStats.aggregate(listOf(cardRow(state = CardState.NEW)))
        assertEquals("new", new?.state, "Only-new cards must summarize as new")
    }

    @Test
    fun aggregateOfNoCardsIsNull() {
        assertNull(WordListExportStats.aggregate(emptyList()), "No cards must aggregate to null stats")
    }

    private fun cardRow(
        state: CardState,
        stability: Double = 1.0,
        due: Long = 0L,
        lastReview: Long? = null,
        reps: Long = 0L,
        lapses: Long = 0L,
    ) = SenseCardExportRow(
        senseId = "00000000-0000-0000-0000-000000000001",
        state = state,
        stability = stability,
        due = due,
        lastReview = lastReview,
        reps = reps,
        lapses = lapses,
    )
}
