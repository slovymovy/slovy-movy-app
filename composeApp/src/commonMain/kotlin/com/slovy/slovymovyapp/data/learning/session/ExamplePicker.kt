package com.slovy.slovymovyapp.data.learning.session

import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.remote.LanguageCardExample
import com.slovy.slovymovyapp.data.util.HtmlTagParser
import com.slovy.slovymovyapp.db.FavoritesQueries
import kotlin.uuid.Uuid

class ExamplePicker(private val learning: FavoritesQueries) {

    fun pick(senseId: Uuid, examples: List<LanguageCardExample>): ExamplePair? {
        return pickFromCandidates(
            senseId = senseId,
            candidates = examples.mapIndexed { index, example ->
                ExampleCandidate(index.toLong(), example.text)
            },
        )
    }

    fun pickTranslation(
        senseId: Uuid,
        examples: List<LanguageCardExample>,
        targetLanguage: Language,
    ): ExamplePair? {
        return pickFromCandidates(
            senseId = senseId,
            candidates = examples.mapIndexedNotNull { index, example ->
                example.targetLangTranslations[targetLanguage]
                    ?.let { ExampleCandidate(index.toLong(), it) }
            },
        )
    }

    private fun pickFromCandidates(senseId: Uuid, candidates: List<ExampleCandidate>): ExamplePair? {
        val clozeCandidates = candidates.mapNotNull { candidate ->
            clozeText(candidate.text)?.let { cloze ->
                ClozeCandidate(candidate.exampleIndex, cloze.text, cloze.range)
            }
        }
        if (clozeCandidates.isEmpty()) return null
        val recentDesc = learning.selectRecentReviewedExampleIdsBySense(senseId, clozeCandidates.size.toLong())
            .executeAsList()
        val recent = recentDesc.toSet()
        val candidate = clozeCandidates.firstOrNull { it.exampleIndex !in recent }
            ?: recentDesc.asReversed().firstNotNullOfOrNull { recentIndex ->
                clozeCandidates.firstOrNull { it.exampleIndex == recentIndex }
            }
            ?: clozeCandidates.first()
        return ExamplePair(candidate.exampleIndex, candidate.text, candidate.range)
    }

    private fun clozeText(text: String): ClozeText? {
        var output = ""
        var range: IntRange? = null
        HtmlTagParser.parseTextSegments(text).forEach { segment ->
            val start = output.length
            output += segment.text
            if (range == null && segment.isTagged && segment.text.isNotBlank()) {
                val leadingWhitespace = segment.text.indexOfFirst { !it.isWhitespace() }
                val trailingWhitespace = segment.text.indexOfLast { !it.isWhitespace() }
                range = (start + leadingWhitespace)..(start + trailingWhitespace)
            }
        }
        return range?.let { ClozeText(text = output, range = it) }
    }

    private data class ClozeText(
        val text: String,
        val range: IntRange,
    )

    private data class ExampleCandidate(
        val exampleIndex: Long,
        val text: String,
    )

    private data class ClozeCandidate(
        val exampleIndex: Long,
        val text: String,
        val range: IntRange,
    )
}
