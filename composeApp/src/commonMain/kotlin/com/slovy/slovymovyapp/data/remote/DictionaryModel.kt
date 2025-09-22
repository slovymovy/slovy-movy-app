package com.slovy.slovymovyapp.data.remote

data class LanguageCard(
    val lemma: String,
    val entries: List<LanguageCardPosEntry>
)

data class LanguageCardPosEntry(
    val pos: String,
    val forms: MutableList<LanguageCardForm>,
    val senses: List<LanguageCardResponseSense>
)

data class LanguageCardForm(
    val tags: List<String> = emptyList(),
    val form: String
)

enum class LearnerLevel {
    A1,
    A2,
    B1,
    B2,
    C1,
    C2;
}

enum class SenseFrequency {
    HIGH,
    MIDDLE,
    LOW,
    VERY_LOW;
}

data class LanguageCardResponseSense(
    val senseId: String,
    val senseDefinition: String,
    val learnerLevel: LearnerLevel,
    val frequency: SenseFrequency,
    val semanticGroupId: String,
    val nameType: String? = null,
    val examples: List<LanguageCardExample> = emptyList(),
    val synonyms: List<String> = emptyList(),
    val antonyms: List<String> = emptyList(),
    val commonPhrases: List<String> = emptyList(),
    val traits: List<LanguageCardTrait> = emptyList(),
    val targetLangDefinitions: Map<String, String> = emptyMap(),
    val translations: Map<String, List<LanguageCardTranslation>> = emptyMap()
)

data class LanguageCardExample(
    val text: String, val targetLangTranslations: Map<String, String> = emptyMap()
)

data class LanguageCardTranslation(
    val targetLangWord: String, val targetLangSenseClarification: String? = null
)

enum class TraitType {
    DATED,
    COLLOQUIAL,
    OBSOLETE,
    DIALECTAL,
    ARCHAIC,
    REGIONAL,
    SLANG,
    FORM,
    SURNAME
}

data class LanguageCardTrait(
    val traitType: TraitType,
    val comment: String
)
