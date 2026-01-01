package com.slovy.slovymovyapp.server.ai.enhancer

import com.slovy.slovymovyapp.ingestion.LanguageCardExample
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LanguageCardRequest(
    val word: String,
    @SerialName("lang_code") val langCode: String,
    val entries: List<LanguageCardEntry>
)

@Serializable
data class LanguageCardEntry(
    val pos: String,
    val forms: List<LanguageCardForm> = emptyList(),
    val senses: List<LanguageCardSense>,
    @SerialName("word_linkages") val wordLinkages: List<LanguageCardWordLinkage> = emptyList()
)

@Serializable
data class LanguageCardForm(
    val form: String,
    val tags: List<String>? = null,
    val note: String? = null
)

@Serializable
data class LanguageCardSense(
    @SerialName("sense_id")
    val senseId: String,
    val glosses: List<String>,
    val examples: List<LanguageCardExample> = emptyList()
)

@Serializable
enum class LinkageType {
    @SerialName("synonyms")
    SYNONYMS,

    @SerialName("antonyms")
    ANTONYMS,

    @SerialName("hypernyms")
    HYPERNYMS,

    @SerialName("hyponyms")
    HYPONYMS,

    @SerialName("holonyms")
    HOLONYMS,

    @SerialName("meronyms")
    MERONYMS,

    @SerialName("coordinate_terms")
    COORDINATE_TERMS,

    @SerialName("related")
    RELATED,

    @SerialName("derived")
    DERIVED,

    @SerialName("troponyms")
    TROPONYMS,

    @SerialName("metonyms")
    METONYMS,

    @SerialName("cognates")
    COGNATES,

    @SerialName("variants")
    VARIANTS,

    @SerialName("compounds")
    COMPOUNDS,

    @SerialName("anagrams")
    ANAGRAMS,

    @SerialName("paronyms")
    PARONYMS,

    @SerialName("instances")
    INSTANCES
}

@Serializable
data class LanguageCardWordLinkage(
    val word: String,
    @SerialName("linkage_type") val linkageType: LinkageType,
    @SerialName("source_sense_description") val sourceSenseDescription: String? = null
)
