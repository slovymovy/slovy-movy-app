package com.slovy.slovymovyapp.server.ai.enhancer

import com.slovy.slovymovyapp.ingestion.ExtractedTranslation
import com.slovy.slovymovyapp.ingestion.LanguageCardResponse
import com.slovy.slovymovyapp.ingestion.LanguageCardTranslation
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TranslationRequest(
    val word: String,
    @SerialName("lang_code") val langCode: String,
    @SerialName("target_lang_code") val targetLangCode: String,
    @SerialName("language_card_data") val languageCardData: LanguageCardResponse,
    val translations: List<ExtractedTranslation>
)

@Serializable
data class TranslationResponse(
    @SerialName("sense_translations") val senseTranslations: List<SenseTranslationData>,
    @SerialName("example_translations") val exampleTranslations: List<ExampleTranslationData>
)

@Serializable
data class SenseTranslationData(
    @SerialName("sense_id") val senseId: String,
    @SerialName("target_lang_definition") val targetLangDefinition: String,
    val translations: List<LanguageCardTranslation>
)

@Serializable
data class ExampleTranslationData(
    @SerialName("original_text") val originalText: String,
    @SerialName("target_lang_translation") val targetLangTranslation: String
)
