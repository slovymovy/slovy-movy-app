package com.slovy.slovymovyapp.server.ai.enhancer

import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.ingestion.LanguageCardPosEntry
import com.slovy.slovymovyapp.ingestion.LanguageCardResponse
import com.slovy.slovymovyapp.ingestion.LanguageCardResponseSense
import com.slovy.slovymovyapp.server.ai.AICache
import com.slovy.slovymovyapp.server.ai.AIParameters
import com.slovy.slovymovyapp.server.ai.AIProvider
import com.slovy.slovymovyapp.server.ai.ModelInfo
import com.slovy.slovymovyapp.server.ai.RetryStrategy
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * A prompt placeholder that nobody substitutes fails silently: the model is simply never told what
 * language to answer in, or what the input for that language looks like, and the corpus drifts with
 * no error anywhere. `$LANG` was in exactly that state on this path until it was fixed, so both it
 * and the `$INPUT_NOTES` that carries [Language.basePromptNotes] / [Language.translationPromptNotes]
 * are pinned here against the prompt the provider really receives.
 */
class PromptNotesTest {

    @Test
    fun promptsCarryThePlaceholdersTheirSubstitutionsLookFor() {
        assertTrue(
            EnhancerPrompts.LANGUAGE_CARD_SYSTEM_PROMPT.contains(NOTES_PLACEHOLDER),
            "$NOTES_PLACEHOLDER must stay in LANGUAGE_CARD_SYSTEM_PROMPT or basePromptNotes never reaches the model"
        )
        assertTrue(
            EnhancerPrompts.LANGUAGE_CARD_SYSTEM_PROMPT.contains(LANG_PLACEHOLDER),
            "$LANG_PLACEHOLDER must stay in LANGUAGE_CARD_SYSTEM_PROMPT or the prompt stops naming the language"
        )
        assertTrue(
            EnhancerPrompts.TRANSLATION_SYSTEM_PROMPT.contains(NOTES_PLACEHOLDER),
            "$NOTES_PLACEHOLDER must stay in TRANSLATION_SYSTEM_PROMPT or translationPromptNotes never reaches the model"
        )
    }

    @Test
    fun baseEnhancerSubstitutesTheNotesAndNameOfEveryLanguage() {
        for (language in Language.entries) {
            val provider = CapturingProvider(Json.encodeToString(LanguageCardResponse.serializer(), BASE_RESPONSE))

            LanguageCardEnhancer().enhance(
                request = BASE_REQUEST.copy(langCode = language.code),
                provider = provider,
                model = "test-model"
            )

            val prompt = assertNotNull(provider.systemPrompt, "${language.code}: provider was never called")
            assertFullySubstituted(prompt, language.basePromptNotes, language, "base")
            assertTrue(
                !prompt.contains(LANG_PLACEHOLDER),
                "${language.code}: base prompt still contains an unsubstituted $LANG_PLACEHOLDER"
            )
            assertTrue(
                prompt.contains(language.englishName),
                "${language.code}: base prompt never names the language as '${language.englishName}'"
            )
        }
    }

    @Test
    fun translationEnhancerSubstitutesTheNotesOfEveryLanguage() {
        for (language in Language.entries) {
            val provider = CapturingProvider(Json.encodeToString(TranslationResponse.serializer(), TRANSLATION_RESPONSE))

            TranslationEnhancer().enhanceWithTranslations(
                request = TRANSLATION_REQUEST,
                provider = provider,
                targetLanguageName = language.englishName,
                targetLanguageNotes = language.translationPromptNotes,
                model = "test-model"
            )

            val prompt = assertNotNull(provider.systemPrompt, "${language.code}: provider was never called")
            assertFullySubstituted(prompt, language.translationPromptNotes, language, "translation")
        }
    }

    private fun assertFullySubstituted(prompt: String, notes: String, language: Language, promptKind: String) {
        assertTrue(
            !prompt.contains(NOTES_PLACEHOLDER),
            "${language.code}: $promptKind prompt still contains an unsubstituted $NOTES_PLACEHOLDER"
        )
        if (notes.isNotEmpty()) {
            assertTrue(
                prompt.contains(notes),
                "${language.code}: $promptKind prompt does not carry the language's notes verbatim"
            )
        }
    }

    private class CapturingProvider(private val response: String) : AIProvider {
        var systemPrompt: String? = null
            private set

        override fun complete(
            parameters: AIParameters,
            cache: AICache,
            retryStrategy: RetryStrategy
        ): String {
            systemPrompt = parameters.systemPrompt
            return response
        }

        override fun getAvailableModels(): List<ModelInfo> = emptyList()

        override fun isAvailable(): Boolean = true

        override fun countTokens(text: String, model: String): Int = text.split(" ").size
    }

    private companion object {
        const val NOTES_PLACEHOLDER = "\$INPUT_NOTES"
        const val LANG_PLACEHOLDER = "\$LANG"
        const val SENSE_ID = "sense-1"

        val BASE_REQUEST = LanguageCardRequest(
            word = "test",
            langCode = "en",
            entries = listOf(
                LanguageCardEntry(
                    pos = "noun",
                    senses = listOf(LanguageCardSense(senseId = SENSE_ID, glosses = listOf("gloss")))
                )
            )
        )

        val BASE_RESPONSE = LanguageCardResponse(
            wordFamily = emptyList(),
            entries = listOf(
                LanguageCardPosEntry(
                    pos = "noun",
                    senses = listOf(
                        LanguageCardResponseSense(
                            senseId = SENSE_ID,
                            senseDefinition = "definition",
                            learnerLevel = "A1",
                            frequency = "High",
                            semanticGroupId = "group-1",
                            nameType = "no"
                        )
                    )
                )
            )
        )

        val TRANSLATION_REQUEST = TranslationRequest(
            word = "test",
            langCode = "en",
            targetLangCode = "ru",
            languageCardData = BASE_RESPONSE,
            translations = emptyList()
        )

        val TRANSLATION_RESPONSE = TranslationResponse(
            senseTranslations = listOf(
                SenseTranslationData(
                    senseId = SENSE_ID,
                    targetLangDefinition = "определение",
                    translations = emptyList()
                )
            ),
            exampleTranslations = emptyList()
        )
    }
}
