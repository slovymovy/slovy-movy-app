package com.slovy.slovymovyapp.server.github

import com.slovy.slovymovyapp.ingestion.LanguageCardExample
import com.slovy.slovymovyapp.ingestion.LanguageCardResponse
import com.slovy.slovymovyapp.ingestion.LanguageCardResponseSense
import com.slovy.slovymovyapp.ingestion.LanguageCardTranslation

/**
 * Utility for merging LanguageCardResponse data.
 *
 * Merge strategy: Existing data wins - only NEW translations (by language code)
 * from incoming data are added. This ensures existing translations are never
 * overwritten during concurrent updates.
 */
object WordDataMerger {

    /**
     * Merges incoming word data with existing data.
     *
     * Strategy:
     * - Preserve all existing translations
     * - Add only NEW translations from incoming data (by language code)
     * - Match senses by sense_id
     * - Match examples by text content (normalized)
     *
     * @param existing The current data from the repository
     * @param incoming The new data from the request
     * @return Merged LanguageCardResponse
     */
    fun merge(existing: LanguageCardResponse, incoming: LanguageCardResponse): LanguageCardResponse {
        // Build lookup for incoming senses by senseId
        val incomingSensesById = incoming.entries
            .flatMap { it.senses }
            .associateBy { it.senseId }

        val mergedEntries = existing.entries.map { existingEntry ->
            val mergedSenses = existingEntry.senses.map { existingSense ->
                val incomingSense = incomingSensesById[existingSense.senseId]
                if (incomingSense != null) {
                    mergeSense(existingSense, incomingSense)
                } else {
                    existingSense
                }
            }
            existingEntry.copy(senses = mergedSenses)
        }

        return existing.copy(entries = mergedEntries)
    }

    /**
     * Merges a single sense, adding only new translation languages.
     */
    private fun mergeSense(
        existing: LanguageCardResponseSense,
        incoming: LanguageCardResponseSense
    ): LanguageCardResponseSense {
        // Merge translations: existing wins, add new language codes only
        val mergedTranslations = mergeTranslationsMap(existing.translations, incoming.translations)

        // Merge target language definitions: existing wins, add new language codes only
        val mergedDefinitions = mergeStringMap(existing.targetLangDefinitions, incoming.targetLangDefinitions)

        // Merge examples
        val mergedExamples = mergeExamples(existing.examples, incoming.examples)

        return existing.copy(
            translations = mergedTranslations,
            targetLangDefinitions = mergedDefinitions,
            examples = mergedExamples
        )
    }

    /**
     * Merges translation maps. Existing keys are preserved; only new keys from incoming are added.
     */
    private fun mergeTranslationsMap(
        existing: Map<String, List<LanguageCardTranslation>>,
        incoming: Map<String, List<LanguageCardTranslation>>
    ): Map<String, List<LanguageCardTranslation>> {
        val newKeys = incoming.filterKeys { it !in existing.keys }
        return existing + newKeys
    }

    /**
     * Merges string maps. Existing keys are preserved; only new keys from incoming are added.
     */
    private fun mergeStringMap(
        existing: Map<String, String>,
        incoming: Map<String, String>
    ): Map<String, String> {
        val newKeys = incoming.filterKeys { it !in existing.keys }
        return existing + newKeys
    }

    /**
     * Merges example lists, adding new translations to matched examples.
     */
    private fun mergeExamples(
        existing: List<LanguageCardExample>,
        incoming: List<LanguageCardExample>
    ): List<LanguageCardExample> {
        // Build lookup for incoming examples by normalized text
        val incomingByText = incoming.associateBy { normalizeText(it.text) }

        return existing.map { existingExample ->
            val normalizedText = normalizeText(existingExample.text)
            val incomingExample = incomingByText[normalizedText]
            if (incomingExample != null) {
                val mergedTranslations = mergeStringMap(
                    existingExample.targetLangTranslations,
                    incomingExample.targetLangTranslations
                )
                existingExample.copy(targetLangTranslations = mergedTranslations)
            } else {
                existingExample
            }
        }
    }

    /**
     * Normalizes text for matching by removing markup and normalizing whitespace.
     */
    private fun normalizeText(text: String): String {
        return text
            .replace(Regex("</?w>"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .lowercase()
    }
}
