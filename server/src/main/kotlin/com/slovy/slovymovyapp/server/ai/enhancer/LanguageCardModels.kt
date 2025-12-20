package com.slovy.slovymovyapp.server.ai.enhancer

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

@Serializable
data class LanguageCardResponse(
    @SerialName("word_family")
    val wordFamily: List<String>? = null,
    val entries: List<LanguageCardPosEntry>
)

@Serializable
data class LanguageCardPosEntry(
    val pos: String? = null,
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
    // --- Temporal / Historical ---
    @SerialName("archaic")
    ARCHAIC,       // Common in old texts; rarely used today

    @SerialName("obsolete")
    OBSOLETE,      // No longer in active use

    @SerialName("dated")
    DATED,         // Old-fashioned but still recognizable

    // --- Register / Style ---
    @SerialName("formal")
    FORMAL,        // Used in polite, official, or literary contexts

    @SerialName("informal")
    INFORMAL,      // Casual or everyday speech

    @SerialName("colloquial")
    COLLOQUIAL,    // Informal and conversational

    @SerialName("slang")
    SLANG,         // Very informal; often group- or generation-specific

    @SerialName("vulgar")
    VULGAR,        // Offensive or taboo

    @SerialName("poetic")
    POETIC,        // Literary or expressive; typical in poetry

    @SerialName("literary")
    LITERARY,      // Elevated or bookish; used in literature, not daily speech

    // --- Regional / Dialectal ---
    @SerialName("regional")
    REGIONAL,      // Used in or specific to a region or variety of the language

    @SerialName("dialectal")
    DIALECTAL,     // Belongs to a dialect or local speech form

    // --- Domain / Field of Use ---
    @SerialName("professional")
    PROFESSIONAL,  // Used mainly in occupational or professional contexts

    @SerialName("technical")
    TECHNICAL,     // Specialized jargon within any field

    @SerialName("scientific")
    SCIENTIFIC,    // Science, research, or academia

    @SerialName("medical")
    MEDICAL,       // Health and medicine

    @SerialName("legal")
    LEGAL,         // Law, government, or regulation

    @SerialName("religious")
    RELIGIOUS,     // Faith, theology, or spirituality

    @SerialName("computing")
    COMPUTING,     // IT and computer science

    @SerialName("business")
    BUSINESS,      // Economics, commerce, or management

    @SerialName("military")
    MILITARY,      // Armed forces, defense, or strategy

    @SerialName("culinary")
    CULINARY,      // Cooking, food, or gastronomy

    @SerialName("musical")
    MUSICAL,       // Music, instruments, or performance

    @SerialName("nautical")
    NAUTICAL,      // Navigation, ships, or maritime contexts

    @SerialName("administrative")
    ADMINISTRATIVE,// Bureaucratic, governmental, or institutional language

    @SerialName("engineering")
    ENGINEERING,   // Mechanics, construction, or industrial use

    // --- Figurative / Frequency / Usage ---
    @SerialName("figurative")
    FIGURATIVE,    // Used metaphorically or symbolically

    @SerialName("rare")
    RARE,          // Uncommon or seldom used

    @SerialName("specialized")
    SPECIALIZED,   // Limited to a specific domain or expert group

    // --- Form / Lexical Function ---
    @SerialName("form")
    FORM,          // Specific morphological or grammatical form

    @SerialName("surname")
    SURNAME,       // Family name

    @SerialName("given_name")
    GIVEN_NAME,    // First or personal name

    @SerialName("place_name")
    PLACE_NAME,    // Toponymic or geographical name

    @SerialName("brand")
    BRAND,         // Brand or product name

    @SerialName("trademark")
    TRADEMARK,     // Legally protected brand name

    // --- Sociocultural / Contextual ---
    @SerialName("internet")
    INTERNET,      // Online slang, meme language, or chat use

    @SerialName("youth")
    YOUTH,         // Used mainly by younger speakers

    @SerialName("rural")
    RURAL,         // Common in countryside or rural areas

    @SerialName("urban")
    URBAN          // Typical of cities or urban slang
}

@Serializable
data class LanguageCardTrait(
    @SerialName("trait_type") val traitType: TraitType,
    val comment: String
)
