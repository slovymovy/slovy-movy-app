package com.slovy.slovymovyapp.server.ai.enhancer

import com.slovy.slovymovyapp.server.ai.*
import kotlinx.serialization.json.Json

/**
 * Core language card enhancement logic using AI providers.
 * This class handles the AI completion and schema building.
 * For creating requests from extracted data, see the main project's LanguageCardEnhancer.
 */
class LanguageCardEnhancer {

    /**
     * Sends the request using the AIProvider abstraction and returns the enhanced response.
     *
     * @param request The language card request to enhance
     * @param provider The AI provider to use
     * @param systemPrompt The system prompt (use EnhancerPrompts.LANGUAGE_CARD_SYSTEM_PROMPT as default)
     * @param temperature Temperature for sampling (0 for deterministic)
     * @param reasoningBudget Reasoning budget for models that support it
     * @param seed Random seed for reproducibility
     * @param model Model identifier
     * @param maxOutputTokens Maximum tokens in output
     * @param topP Top-p sampling parameter
     * @param verbosity Verbosity level for GPT-5 series
     * @param cache AI cache implementation (defaults to NoOpAICache)
     * @param retryStrategy Retry strategy (defaults to NoRetryStrategy)
     */
    fun enhance(
        request: LanguageCardRequest,
        provider: AIProvider,
        systemPrompt: String = EnhancerPrompts.LANGUAGE_CARD_SYSTEM_PROMPT,
        temperature: Float = 0f,
        reasoningBudget: Int = 2000,
        seed: Int? = null,
        model: String,
        maxOutputTokens: Int = 32768,
        topP: Float = 1.0f,
        verbosity: String? = null,
        cache: AICache = NoOpAICache,
        retryStrategy: RetryStrategy = NoRetryStrategy
    ): LanguageCardResponse {
        val schema = buildSchema(request)

        // Select appropriate temperature based on provider
        val providerTemperature = when (provider) {
            is OpenAIProvider -> 1.0f  // OpenAI o4 models require temperature=1 (default)
            else -> temperature        // Use provided temperature for Gemini and other providers
        }

        val parameters = AIParameters(
            parts = listOf(Json.encodeToString(LanguageCardRequest.serializer(), request)),
            temperature = providerTemperature,
            seed = seed ?: 1234567890,
            reasoningBudget = reasoningBudget,
            model = model,
            schema = schema,
            systemPrompt = systemPrompt,
            maxOutputTokens = maxOutputTokens,
            topP = topP,
            verbosity = verbosity?.let { Verbosity.valueOf(it.uppercase()) }
        )

        val response = provider.complete(
            parameters,
            cache = cache,
            retryStrategy = retryStrategy
        )
        val decodedResponse = Json.decodeFromString<LanguageCardResponse>(response)
        validateSenseIds(request, decodedResponse)
        return decodedResponse
    }

    /**
     * Builds the JSON schema for the language card response based on the request data.
     */
    fun buildSchema(request: LanguageCardRequest): String {
        val synonyms: List<String> = request.entries.flatMap { entry ->
            entry.wordLinkages.filter { it.linkageType == LinkageType.SYNONYMS }.map { it.word }
        }.filter { !it.contains('"') }.distinct()

        val antonyms: List<String> = request.entries.flatMap { entry ->
            entry.wordLinkages.filter { it.linkageType == LinkageType.ANTONYMS }.map { it.word }
        }.filter { !it.contains('"') }.distinct()

        val synonymsSchema = if (synonyms.isNotEmpty() && synonyms.size < 30) {
            """,
            "enum": [
              ${synonyms.joinToString(",") { "\"$it\"" }}
            ]"""
        } else {
            ""
        }

        val antonymsSchema = if (antonyms.isNotEmpty() && antonyms.size < 30) {
            """,
            "enum": [
              ${antonyms.joinToString(",") { "\"$it\"" }}
            ]"""
        } else {
            ""
        }

        val senseIds = request.entries.flatMap { it.senses.map { sense -> sense.senseId } }.distinct()
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
    "word_family": {
      "type": "array",
      "items": {
        "type": "string"
      }
    },
    "entries": {
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "pos": {
            "type": "string",
            "enum": [
              "article",
              "noun",
              "name",
              "verb",
              "adjective",
              "adverb",
              "pronoun",
              "preposition",
              "conjunction",
              "interjection",
              "determiner",
              "numeral"
            ]
          },
          "senses": {
            "type": "array",
            "items": {
              "type": "object",
              "properties": {
                "sense_id": {
                    "type": "string"$senseIdsSchema
                },
                "sense_definition": {
                  "type": "string"
                },
                "learner_level": {
                  "type": "string",
                  "enum": [
                    "A1",
                    "A2",
                    "B1",
                    "B2",
                    "C1",
                    "C2"
                  ]
                },
                "frequency": {
                  "type": "string",
                  "enum": [
                    "High",
                    "Middle",
                    "Low",
                    "VeryLow"
                  ]
                },
                "semantic_group_id": {
                  "type": "string"
                },
                "name_type": {
                  "type": "string",
                   "enum": [
                     "no",
                     "person_name",
                     "place_name",
                     "geographical_feature",
                     "organization_name",
                     "fictional_name",
                     "historical_name",
                     "event_name",
                     "work_of_art_name",
                     "language_name",
                     "ethnic_group_name",
                     "deity_or_religious_name",
                     "religion_or_philosophy_name",
                     "astronomical_name",
                     "title_or_honorific_name",
                     "brand_or_product_name",
                     "technology_or_software_name",
                     "game_or_sport_name",
                     "ideology_or_movement_name",
                     "mythological_or_astrological_entity",
                     "document_or_program_name",
                     "other"
                   ]
                },
                "examples": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "properties": {
                      "text": {
                        "type": "string"
                      }
                    },
                    "required": [
                      "text"
                    ]
                  }
                },
                "synonyms": {
                  "type": "array",
                  "items": {
                    "type": "string"$synonymsSchema

                  }
                },
                "antonyms": {
                  "type": "array",
                  "items": {
                    "type": "string"$antonymsSchema
                  }
                },
                "common_phrases": {
                  "type": "array",
                  "items": {
                    "type": "string"
                  }
                },
                "traits": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "properties": {
                      "trait_type": {
                        "type": "string",
                        "enum": [
                          ${com.slovy.slovymovyapp.ingestion.TraitType.entries.joinToString(", ") { "\"${it.name.lowercase()}\"" }}
                        ]
                      },
                      "comment": {
                        "type": "string"
                      }
                    },
                    "required": [
                      "trait_type",
                      "comment"
                    ]
                  }
                }
              },
              "required": [
                "sense_id",
                "sense_definition",
                "learner_level",
                "frequency",
                "semantic_group_id",
                "name_type",
                "examples",
                "traits",
                "synonyms",
                "antonyms",
                "common_phrases"
              ]
            }
          }
        },
        "required": [
          "senses",
          "pos"
        ]
      }
    }
  },
  "required": [
    "word_family",
    "entries"
  ]
}""".trimIndent()
    }

    private fun validateSenseIds(
        request: LanguageCardRequest,
        response: LanguageCardResponse
    ) {
        val sourceSenseIds = request.entries.flatMap { entry ->
            entry.senses.map { it.senseId }
        }.toSet()
        val responseSenseIds = response.entries.flatMap { entry ->
            entry.senses.map { it.senseId }
        }.toSet()
        val unknownSenseIds = responseSenseIds - sourceSenseIds
        if (unknownSenseIds.isNotEmpty()) {
            throw IllegalArgumentException(
                "LLM returned unknown sense_id(s) in language card enhancement: ${unknownSenseIds.sorted()}"
            )
        }
    }
}
