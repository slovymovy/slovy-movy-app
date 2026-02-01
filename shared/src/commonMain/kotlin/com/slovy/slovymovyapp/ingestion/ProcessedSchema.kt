package com.slovy.slovymovyapp.ingestion

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder


@Serializable
data class LanguageCardResponse(
    @SerialName("word_family")
    val wordFamily: List<String>? = null,
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

@Serializable(with = TraitTypeSerializer::class)
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
    SURNAME,

    @SerialName("unknown")
    UNKNOWN
}

@Serializable
data class LanguageCardTrait(
    @SerialName("trait_type") val traitType: TraitType,
    val comment: String
)

object TraitTypeSerializer : KSerializer<TraitType> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("TraitType", PrimitiveKind.STRING)

    private val byJsonName: Map<String, TraitType> = mapOf(
        "dated" to TraitType.DATED,
        "colloquial" to TraitType.COLLOQUIAL,
        "obsolete" to TraitType.OBSOLETE,
        "dialectal" to TraitType.DIALECTAL,
        "archaic" to TraitType.ARCHAIC,
        "regional" to TraitType.REGIONAL,
        "slang" to TraitType.SLANG,
        "form" to TraitType.FORM,
        "surname" to TraitType.SURNAME
    )

    private val toJsonName: Map<TraitType, String> = mapOf(
        TraitType.DATED to "dated",
        TraitType.COLLOQUIAL to "colloquial",
        TraitType.OBSOLETE to "obsolete",
        TraitType.DIALECTAL to "dialectal",
        TraitType.ARCHAIC to "archaic",
        TraitType.REGIONAL to "regional",
        TraitType.SLANG to "slang",
        TraitType.FORM to "form",
        TraitType.SURNAME to "surname",
        TraitType.UNKNOWN to "unknown"
    )

    override fun deserialize(decoder: Decoder): TraitType {
        val raw = decoder.decodeString().trim().lowercase()
        return byJsonName[raw] ?: TraitType.UNKNOWN
    }

    override fun serialize(encoder: Encoder, value: TraitType) {
        encoder.encodeString(toJsonName[value] ?: "unknown")
    }
}
