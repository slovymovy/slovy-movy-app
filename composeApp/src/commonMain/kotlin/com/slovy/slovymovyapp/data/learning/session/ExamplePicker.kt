package com.slovy.slovymovyapp.data.learning.session

import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.remote.LanguageCardExample
import com.slovy.slovymovyapp.data.util.parseClozeFromTaggedText
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
            parseClozeFromTaggedText(candidate.text)
                ?.takeIf { it.answerRanges.isNotEmpty() }
                ?.let { cloze ->
                    ClozeCandidate(candidate.exampleIndex, cloze.plainText, cloze.answerRanges)
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
        return ExamplePair(candidate.exampleIndex, candidate.text, candidate.ranges)
    }

    private data class ExampleCandidate(
        val exampleIndex: Long,
        val text: String,
    )

    private data class ClozeCandidate(
        val exampleIndex: Long,
        val text: String,
        val ranges: List<IntRange>,
    )
}
