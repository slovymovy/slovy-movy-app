package com.slovy.slovymovyapp.data.remote

import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.forms.SchemeView
import org.jetbrains.compose.resources.StringResource
import slovymovyapp.composeapp.generated.resources.Res
import slovymovyapp.composeapp.generated.resources.*

data class LanguageCard(
    val lemma: String,
    val entries: List<LanguageCardPosEntry>,
    val zipfFrequency: Float,
    val wordFamily: List<String> = emptyList(),
    val relatedWords: Map<String, RelatedWord> = emptyMap(),
    val online: Boolean = false
)

data class RelatedWord(val lemma: String, val zipfFrequency: Float, val online: Boolean)

data class FormsSchemeView(
    val view: SchemeView,
    val forms: List<List<String?>>
)

data class LanguageCardPosEntry(
    val pos: PartOfSpeech,
    val formsViews: List<FormsSchemeView>,
    val senses: List<LanguageCardResponseSense>,
)

enum class LearnerLevel {
    A1,
    A2,
    B1,
    B2,
    C1,
    C2;
}

enum class SenseFrequency(val label: StringResource) {
    HIGH(Res.string.sense_frequency_high),
    MIDDLE(Res.string.sense_frequency_middle),
    LOW(Res.string.sense_frequency_low),
    VERY_LOW(Res.string.sense_frequency_very_low);
}

enum class PartOfSpeech(val short: String, val displayName: StringResource) {
    ARTICLE("Art", Res.string.pos_article),
    NOUN("Noun", Res.string.pos_noun),
    NAME("Name", Res.string.pos_name),
    VERB("Verb", Res.string.pos_verb),
    ADJECTIVE("Adj", Res.string.pos_adjective),
    ADVERB("Adv", Res.string.pos_adverb),
    PRONOUN("Pron", Res.string.pos_pronoun),
    PREPOSITION("Prep", Res.string.pos_preposition),
    CONJUNCTION("Conj", Res.string.pos_conjunction),
    INTERJECTION("Interj", Res.string.pos_interjection),
    DETERMINER("Det", Res.string.pos_determiner),
    NUMERAL("Num", Res.string.pos_numeral);

    fun capitalized(): String {
        return name.lowercase()
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
}

enum class NameType(val displayName: StringResource) {
    NO(Res.string.name_type_no),
    PERSON_NAME(Res.string.name_type_person_name),
    PLACE_NAME(Res.string.name_type_place_name),
    GEOGRAPHICAL_FEATURE(Res.string.name_type_geographical_feature),
    ORGANIZATION_NAME(Res.string.name_type_organization_name),
    FICTIONAL_NAME(Res.string.name_type_fictional_name),
    HISTORICAL_NAME(Res.string.name_type_historical_name),
    EVENT_NAME(Res.string.name_type_event_name),
    WORK_OF_ART_NAME(Res.string.name_type_work_of_art_name),
    LANGUAGE_NAME(Res.string.name_type_language_name),
    ETHNIC_GROUP_NAME(Res.string.name_type_ethnic_group_name),
    DEITY_OR_RELIGIOUS_NAME(Res.string.name_type_deity_or_religious_name),
    RELIGION_OR_PHILOSOPHY_NAME(Res.string.name_type_religion_or_philosophy_name),
    ASTRONOMICAL_NAME(Res.string.name_type_astronomical_name),
    TITLE_OR_HONORIFIC_NAME(Res.string.name_type_title_or_honorific_name),
    BRAND_OR_PRODUCT_NAME(Res.string.name_type_brand_or_product_name),
    TECHNOLOGY_OR_SOFTWARE_NAME(Res.string.name_type_technology_or_software_name),
    GAME_OR_SPORT_NAME(Res.string.name_type_game_or_sport_name),
    IDEOLOGY_OR_MOVEMENT_NAME(Res.string.name_type_ideology_or_movement_name),
    MYTHOLOGICAL_OR_ASTROLOGICAL_ENTITY(Res.string.name_type_mythological_or_astrological_entity),
    DOCUMENT_OR_PROGRAM_NAME(Res.string.name_type_document_or_program_name),
    OTHER(Res.string.name_type_other);
}

data class LanguageCardResponseSense(
    val senseId: String,
    val senseDefinition: String,
    val learnerLevel: LearnerLevel,
    val frequency: SenseFrequency,
    val semanticGroupId: String,
    val nameType: NameType? = null,
    val examples: List<LanguageCardExample> = emptyList(),
    val synonyms: List<String> = emptyList(),
    val antonyms: List<String> = emptyList(),
    val commonPhrases: List<String> = emptyList(),
    val traits: List<LanguageCardTrait> = emptyList(),
    val targetLangDefinitions: Map<Language, String> = emptyMap(),
    val translations: Map<Language, List<LanguageCardTranslation>> = emptyMap()
)

data class LanguageCardExample(
    val text: String, val targetLangTranslations: Map<Language, String> = emptyMap()
)

data class LanguageCardTranslation(
    val targetLangWord: String,
    val targetLangSenseClarification: String? = null,
    val idx: Long = 0
)

enum class TraitType(val displayName: StringResource) {
    DATED(Res.string.trait_type_dated),
    COLLOQUIAL(Res.string.trait_type_colloquial),
    OBSOLETE(Res.string.trait_type_obsolete),
    DIALECTAL(Res.string.trait_type_dialectal),
    ARCHAIC(Res.string.trait_type_archaic),
    REGIONAL(Res.string.trait_type_regional),
    SLANG(Res.string.trait_type_slang),
    FORM(Res.string.trait_type_form),
    SURNAME(Res.string.trait_type_surname);
}

data class LanguageCardTrait(
    val traitType: TraitType,
    val comment: String
)
