package com.slovy.slovymovyapp.server.github

import com.slovy.slovymovyapp.ingestion.*
import kotlin.test.Test
import kotlin.test.assertEquals

class WordDataMergerTest {

    @Test
    fun merge_addsNewTranslationLanguages() {
        val existingTranslations = mapOf(
            "ru" to listOf(LanguageCardTranslation("тест", "общее значение"))
        )
        val incomingTranslations = mapOf(
            "ru" to listOf(LanguageCardTranslation("другой", null)),
            "pl" to listOf(LanguageCardTranslation("test", "ogólne znaczenie"))
        )

        val existing = createResponse(translations = existingTranslations)
        val incoming = createResponse(translations = incomingTranslations)

        val result = WordDataMerger.merge(existing, incoming)

        val resultTranslations = result.entries.first().senses.first().translations
        assertEquals(2, resultTranslations.size, "Should have 2 language codes")
        assertEquals(
            "тест",
            resultTranslations["ru"]?.first()?.targetLangWord,
            "ru translation should be from existing"
        )
        assertEquals(
            "test",
            resultTranslations["pl"]?.first()?.targetLangWord,
            "pl translation should be from incoming"
        )
    }

    @Test
    fun merge_preservesExistingTranslations() {
        val existingTranslations = mapOf(
            "ru" to listOf(
                LanguageCardTranslation("оригинальный", "первое значение"),
                LanguageCardTranslation("существующий", "второе значение")
            )
        )
        val incomingTranslations = mapOf(
            "ru" to listOf(LanguageCardTranslation("новый", "другое значение"))
        )

        val existing = createResponse(translations = existingTranslations)
        val incoming = createResponse(translations = incomingTranslations)

        val result = WordDataMerger.merge(existing, incoming)

        val ruTranslations = result.entries.first().senses.first().translations["ru"]
        assertEquals(2, ruTranslations?.size, "Should preserve both existing translations")
        assertEquals("оригинальный", ruTranslations?.get(0)?.targetLangWord)
        assertEquals("существующий", ruTranslations?.get(1)?.targetLangWord)
    }

    @Test
    fun merge_handlesEmptyExistingTranslations() {
        val existing = createResponse(translations = emptyMap())
        val incoming = createResponse(
            translations = mapOf(
                "ru" to listOf(LanguageCardTranslation("новый", null)),
                "pl" to listOf(LanguageCardTranslation("nowy", null))
            )
        )

        val result = WordDataMerger.merge(existing, incoming)

        val resultTranslations = result.entries.first().senses.first().translations
        assertEquals(2, resultTranslations.size, "Should add all incoming translations")
        assertEquals("новый", resultTranslations["ru"]?.first()?.targetLangWord)
        assertEquals("nowy", resultTranslations["pl"]?.first()?.targetLangWord)
    }

    @Test
    fun merge_handlesEmptyIncomingTranslations() {
        val existing = createResponse(
            translations = mapOf(
                "ru" to listOf(LanguageCardTranslation("существующий", null))
            )
        )
        val incoming = createResponse(translations = emptyMap())

        val result = WordDataMerger.merge(existing, incoming)

        val resultTranslations = result.entries.first().senses.first().translations
        assertEquals(1, resultTranslations.size, "Should preserve existing translations")
        assertEquals("существующий", resultTranslations["ru"]?.first()?.targetLangWord)
    }

    @Test
    fun merge_mergesTargetLangDefinitions() {
        val existingDefinitions = mapOf("ru" to "Русское определение")
        val incomingDefinitions = mapOf(
            "ru" to "Другое русское определение",
            "pl" to "Polskie definicja"
        )

        val existing = createResponse(targetLangDefinitions = existingDefinitions)
        val incoming = createResponse(targetLangDefinitions = incomingDefinitions)

        val result = WordDataMerger.merge(existing, incoming)

        val resultDefinitions = result.entries.first().senses.first().targetLangDefinitions
        assertEquals(2, resultDefinitions.size, "Should have 2 language codes")
        assertEquals("Русское определение", resultDefinitions["ru"], "ru definition should be from existing")
        assertEquals("Polskie definicja", resultDefinitions["pl"], "pl definition should be from incoming")
    }

    @Test
    fun merge_mergesExampleTranslations() {
        val existingExamples = listOf(
            LanguageCardExample(
                text = "This is a test.",
                targetLangTranslations = mapOf("ru" to "Это тест.")
            )
        )
        val incomingExamples = listOf(
            LanguageCardExample(
                text = "This is a test.",
                targetLangTranslations = mapOf(
                    "ru" to "Другой перевод.",
                    "pl" to "To jest test."
                )
            )
        )

        val existing = createResponse(examples = existingExamples)
        val incoming = createResponse(examples = incomingExamples)

        val result = WordDataMerger.merge(existing, incoming)

        val exampleTranslations = result.entries.first().senses.first().examples.first().targetLangTranslations
        assertEquals(2, exampleTranslations.size, "Should have 2 language codes")
        assertEquals("Это тест.", exampleTranslations["ru"], "ru translation should be from existing")
        assertEquals("To jest test.", exampleTranslations["pl"], "pl translation should be from incoming")
    }

    @Test
    fun merge_matchesSensesBySenseId() {
        val existing = LanguageCardResponse(
            wordFamily = null,
            entries = listOf(
                LanguageCardPosEntry(
                    pos = "noun",
                    senses = listOf(
                        createSense(
                            senseId = "sense-1",
                            translations = mapOf("ru" to listOf(LanguageCardTranslation("смысл1", null)))
                        ),
                        createSense(
                            senseId = "sense-2",
                            translations = mapOf("ru" to listOf(LanguageCardTranslation("смысл2", null)))
                        )
                    )
                )
            )
        )

        val incoming = LanguageCardResponse(
            wordFamily = null,
            entries = listOf(
                LanguageCardPosEntry(
                    pos = "noun",
                    senses = listOf(
                        createSense(
                            senseId = "sense-2",
                            translations = mapOf("pl" to listOf(LanguageCardTranslation("sens2", null)))
                        )
                    )
                )
            )
        )

        val result = WordDataMerger.merge(existing, incoming)

        val sense1 = result.entries.first().senses.first { it.senseId == "sense-1" }
        val sense2 = result.entries.first().senses.first { it.senseId == "sense-2" }

        assertEquals(1, sense1.translations.size, "sense-1 should only have ru")
        assertEquals(setOf("ru"), sense1.translations.keys)

        assertEquals(2, sense2.translations.size, "sense-2 should have both ru and pl")
        assertEquals(setOf("ru", "pl"), sense2.translations.keys)
    }

    @Test
    fun merge_matchesExamplesWithNormalizedText() {
        val existingExamples = listOf(
            LanguageCardExample(
                text = "This is a <w>test</w>.",
                targetLangTranslations = mapOf("ru" to "Это тест.")
            )
        )
        val incomingExamples = listOf(
            LanguageCardExample(
                text = "This is a test.",
                targetLangTranslations = mapOf("pl" to "To jest test.")
            )
        )

        val existing = createResponse(examples = existingExamples)
        val incoming = createResponse(examples = incomingExamples)

        val result = WordDataMerger.merge(existing, incoming)

        val exampleTranslations = result.entries.first().senses.first().examples.first().targetLangTranslations
        assertEquals(2, exampleTranslations.size, "Should match despite <w> tag differences")
        assertEquals("Это тест.", exampleTranslations["ru"])
        assertEquals("To jest test.", exampleTranslations["pl"])
    }

    private fun createResponse(
        translations: Map<String, List<LanguageCardTranslation>> = emptyMap(),
        targetLangDefinitions: Map<String, String> = emptyMap(),
        examples: List<LanguageCardExample> = emptyList()
    ): LanguageCardResponse {
        return LanguageCardResponse(
            wordFamily = null,
            entries = listOf(
                LanguageCardPosEntry(
                    pos = "noun",
                    senses = listOf(
                        createSense(
                            translations = translations,
                            targetLangDefinitions = targetLangDefinitions,
                            examples = examples
                        )
                    )
                )
            )
        )
    }

    private fun createSense(
        senseId: String = "test-sense-id",
        translations: Map<String, List<LanguageCardTranslation>> = emptyMap(),
        targetLangDefinitions: Map<String, String> = emptyMap(),
        examples: List<LanguageCardExample> = emptyList()
    ): LanguageCardResponseSense {
        return LanguageCardResponseSense(
            senseId = senseId,
            senseDefinition = "Test definition",
            learnerLevel = "B1",
            frequency = "High",
            semanticGroupId = "semantic-1",
            nameType = null,
            examples = examples,
            synonyms = emptyList(),
            antonyms = emptyList(),
            commonPhrases = emptyList(),
            traits = emptyList(),
            targetLangDefinitions = targetLangDefinitions,
            translations = translations
        )
    }
}
