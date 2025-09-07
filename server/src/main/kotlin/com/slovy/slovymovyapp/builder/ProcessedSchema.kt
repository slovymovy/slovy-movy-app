package com.slovy.slovymovyapp.builder

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
data class LanguageCardResponse(
    val entries: List<LanguageCardPosEntry>
)

@Serializable
data class LanguageCardPosEntry(
    val pos: String,
    val senses: List<LanguageCardResponseSense>
)

@Serializable
data class LanguageCardResponseSense(
    @SerialName("sense_id") val senseId: String,
    @SerialName("sense_definition") val senseDefinition: String,
    @SerialName("learner_level") val learnerLevel: String,
    val frequency: String,
    @SerialName("semantic_group_id") val semanticGroupId: String,
    @SerialName("name_type") val nameType: String? = null,
    val examples: List<LanguageCardExample> = emptyList(),
    val synonyms: List<String> = emptyList(),
    val antonyms: List<String> = emptyList(),
    @SerialName("common_phrases") val commonPhrases: List<String> = emptyList(),
    val traits: List<LanguageCardTrait> = emptyList(),
    @SerialName("target_lang_definitions") val targetLangDefinitions: Map<String, String> = emptyMap(),
    val translations: Map<String, List<LanguageCardTranslation>> = emptyMap()
)

@Serializable
data class LanguageCardExample(
    val text: String,
    @SerialName("target_lang_translations") val targetLangTranslations: Map<String, String> = emptyMap()
)

@Serializable
data class LanguageCardTranslation(
    @SerialName("target_lang_word") val targetLangWord: String,
    @SerialName("target_lang_sense_clarification") val targetLangSenseClarification: String? = null
)

@Serializable
enum class TraitType {
    @SerialName("dated")
    DATED,

    @SerialName("colloquial")
    COLLOQUIAL,

    @SerialName("obsolete")
    OBSOLETE,

    @SerialName("dialectal")
    DIALECTAL,

    @SerialName("archaic")
    ARCHAIC,

    @SerialName("regional")
    REGIONAL,

    @SerialName("slang")
    SLANG,

    @SerialName("form")
    FORM,

    @SerialName("surname")
    SURNAME
}

@Serializable
data class LanguageCardTrait(
    @SerialName("trait_type") val traitType: TraitType,
    val comment: String
)
