package com.slovy.slovymovyapp.server.ai.enhancer

import com.slovy.slovymovyapp.ingestion.LanguageCardResponse
import com.slovy.slovymovyapp.server.ai.*
import kotlinx.serialization.json.Json

/**
 * Core translation enhancement logic using AI providers.
 * This class handles the AI completion, schema building, and data merging.
 * For creating requests from extracted data, see the main project's TranslationEnhancer.
 */
class TranslationEnhancer {

    /**
     * Enhances a language card with translation data using AIProvider abstraction.
     *
     * @param request The translation request
     * @param provider The AI provider to use
     * @param targetLanguageName The name of the target language (e.g., "Russian", "Polish")
     * @param systemPrompt The system prompt (use EnhancerPrompts.TRANSLATION_SYSTEM_PROMPT as default)
     * @param temperature Temperature for sampling
     * @param reasoningBudget Reasoning budget for models that support it
     * @param model Model identifier
     * @param seed Random seed for reproducibility
     * @param maxOutputTokens Maximum tokens in output
     * @param topP Top-p sampling parameter
     * @param verbosity Verbosity level for GPT-5 series
     * @param cache AI cache implementation (defaults to NoOpAICache)
     * @param retryStrategy Retry strategy (defaults to NoRetryStrategy)
     */
    fun enhanceWithTranslations(
        request: TranslationRequest,
        provider: AIProvider,
        targetLanguageName: String,
        systemPrompt: String = EnhancerPrompts.TRANSLATION_SYSTEM_PROMPT,
        temperature: Float = 0f,
        reasoningBudget: Int = 2000,
        model: String,
        seed: Int? = null,
        maxOutputTokens: Int = 32768,
        topP: Float = 1.0f,
        verbosity: String? = null,
        cache: AICache = NoOpAICache,
        retryStrategy: RetryStrategy = NoRetryStrategy
    ): TranslationResponse {
        val processedSystemPrompt = systemPrompt.replace("\$TARGET_LANG", targetLanguageName)
        val schema = buildTranslationSchema(request)

        // Select appropriate temperature based on provider
        val providerTemperature = when (provider) {
            is OpenAIProvider -> 1.0f  // OpenAI o4 models require temperature=1 (default)
            else -> temperature        // Use provided temperature for Gemini and other providers
        }

        val parameters = AIParameters(
            parts = listOf(Json.encodeToString(TranslationRequest.serializer(), request)),
            temperature = providerTemperature,
            seed = seed ?: 1234567890,
            reasoningBudget = reasoningBudget,
            model = model,
            schema = schema,
            systemPrompt = processedSystemPrompt,
            maxOutputTokens = maxOutputTokens,
            topP = topP,
            verbosity = verbosity?.let { Verbosity.valueOf(it.uppercase()) }
        )

        val response = provider.complete(
            parameters,
            cache = cache,
            retryStrategy = retryStrategy
        )
        val decodedResponse = Json.decodeFromString<TranslationResponse>(response)
        validateSenseIds(request, decodedResponse)
        return decodedResponse
    }

    /**
     * Builds the JSON schema for the translation response.
     */
    fun buildTranslationSchema(request: TranslationRequest): String {
        val senseIds = request.languageCardData.entries.flatMap { posEntry ->
            posEntry.senses.map { it.senseId }
        }.distinct()

        val senseIdsSchema = if (senseIds.isNotEmpty() && senseIds.size < 30) {
            """,
            "enum": [
              ${senseIds.joinToString(",") { "\"$it\"" }}
            ]"""
        } else {
            ""
        }

        return """
{
  "type": "object",
  "properties": {
    "sense_translations": {
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "sense_id": {
            "type": "string"$senseIdsSchema
          },
          "target_lang_definition": {
            "type": "string"
          },
          "translations": {
            "type": "array",
            "items": {
              "type": "object",
              "properties": {
                "target_lang_word": {
                  "type": "string"
                },
                "target_lang_sense_clarification": {
                  "type": "string"
                }
              },
              "required": ["target_lang_word", "target_lang_sense_clarification"]
            }
          }
        },
        "required": ["sense_id", "target_lang_definition", "translations"]
      }
    },
    "example_translations": {
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "original_text": {
            "type": "string"
          },
          "target_lang_translation": {
            "type": "string"
          }
        },
        "required": ["original_text", "target_lang_translation"]
      }
    }
  },
  "required": ["sense_translations", "example_translations"]
}""".trimIndent()
    }

    /**
     * Merges translation data into an existing LanguageCardResponse.
     */
    fun mergeTranslationData(
        originalCard: LanguageCardResponse,
        translationResponse: TranslationResponse,
        targetLangCode: String
    ): LanguageCardResponse {
        // Create lookup maps for efficient merging
        val senseTranslationsMap = translationResponse.senseTranslations.associateBy { it.senseId }
        // Create example translations map with normalized text (remove <w> tags and normalize whitespace) for matching
        val exampleTranslationsMap = translationResponse.exampleTranslations.associateBy {
            normalizeTextForMatching(it.originalText)
        }

        val updatedPos = originalCard.entries.map { posEntry ->
            val updatedSenses = posEntry.senses.map { sense ->
                val translationData = senseTranslationsMap[sense.senseId]

                // Update examples with target language translations
                val updatedExamples = sense.examples.map { example ->
                    // Normalize example text for lookup
                    val normalizedExampleText = normalizeTextForMatching(example.text)
                    val exampleTranslation = exampleTranslationsMap[normalizedExampleText]
                        ?: findBestMatchingTranslation(normalizedExampleText, translationResponse.exampleTranslations)
                        ?: throw IllegalArgumentException("Missing translation for example: ${example.text} (normalized: '$normalizedExampleText')")
                    val updatedTranslations =
                        example.targetLangTranslations + (targetLangCode to exampleTranslation.targetLangTranslation)
                    example.copy(targetLangTranslations = updatedTranslations)
                }

                // Update sense translations map
                val updatedTranslations = if (translationData != null) {
                    sense.translations + (targetLangCode to translationData.translations)
                } else {
                    sense.translations
                }

                val updatedTargetLangDefinitions = if (translationData != null) {
                    sense.targetLangDefinitions + (targetLangCode to translationData.targetLangDefinition)
                } else {
                    sense.targetLangDefinitions
                }

                sense.copy(
                    targetLangDefinitions = updatedTargetLangDefinitions,
                    translations = updatedTranslations,
                    examples = updatedExamples
                )
            }

            posEntry.copy(senses = updatedSenses)
        }

        return originalCard.copy(entries = updatedPos)
    }

    private fun validateSenseIds(
        request: TranslationRequest,
        response: TranslationResponse
    ) {
        val sourceSenseIds = request.languageCardData.entries.flatMap { posEntry ->
            posEntry.senses.map { it.senseId }
        }.toSet()
        val responseSenseIds = response.senseTranslations.map { it.senseId }.toSet()
        val unknownSenseIds = responseSenseIds - sourceSenseIds
        if (unknownSenseIds.isNotEmpty()) {
            throw IllegalArgumentException(
                "LLM returned unknown sense_id(s) in translation enhancement: ${unknownSenseIds.sorted()}"
            )
        }
    }

    /**
     * Normalizes text for matching by removing <w> tags and normalizing whitespace.
     */
    private fun normalizeTextForMatching(text: String): String {
        return text
            .replace(Regex("</?w>"), "") // Remove <w> and </w> tags
            .replace(Regex("\\s+"), " ")  // Normalize multiple spaces to single space
            .trim()                       // Remove leading/trailing whitespace
    }

    /**
     * Finds the best matching translation based on text similarity when exact matching fails.
     * Uses Levenshtein distance to find the most similar original text.
     */
    private fun findBestMatchingTranslation(
        targetText: String,
        exampleTranslations: List<ExampleTranslationData>
    ): ExampleTranslationData? {
        if (exampleTranslations.isEmpty()) return null

        val targetTextNormalized = targetText.lowercase()
        var bestMatch: ExampleTranslationData? = null
        var bestSimilarity = 0.0
        val minSimilarityThreshold = 0.7 // 70% similarity threshold

        for (translation in exampleTranslations) {
            val candidateText = normalizeTextForMatching(translation.originalText).lowercase()
            val similarity = calculateTextSimilarity(targetTextNormalized, candidateText)

            if (similarity > bestSimilarity && similarity >= minSimilarityThreshold) {
                bestSimilarity = similarity
                bestMatch = translation
            }
        }

        return bestMatch
    }

    /**
     * Calculates text similarity using a combination of Levenshtein distance and length normalization.
     * Returns a value between 0.0 (no similarity) and 1.0 (identical).
     */
    private fun calculateTextSimilarity(text1: String, text2: String): Double {
        if (text1 == text2) return 1.0
        if (text1.isEmpty() && text2.isEmpty()) return 1.0
        if (text1.isEmpty() || text2.isEmpty()) return 0.0

        val distance = levenshteinDistance(text1, text2)
        val maxLength = maxOf(text1.length, text2.length)
        return 1.0 - (distance.toDouble() / maxLength)
    }

    /**
     * Calculates the Levenshtein distance between two strings.
     */
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }

        // Initialize base cases
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j

        // Fill the DP table
        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,      // deletion
                    dp[i][j - 1] + 1,      // insertion
                    dp[i - 1][j - 1] + cost // substitution
                )
            }
        }

        return dp[s1.length][s2.length]
    }
}
