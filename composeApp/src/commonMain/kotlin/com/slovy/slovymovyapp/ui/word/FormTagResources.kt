package com.slovy.slovymovyapp.ui.word

import com.slovy.slovymovyapp.data.forms.*
import org.jetbrains.compose.resources.StringResource
import slovymovyapp.composeapp.generated.resources.Res
import slovymovyapp.composeapp.generated.resources.*

val formTagDisplayNames: Map<FormTag, StringResource> = mapOf(
    // VerbForm
    VerbForm.INFINITIVE to Res.string.tag_verb_form_infinitive,
    VerbForm.FINITE to Res.string.tag_verb_form_finite,
    VerbForm.PARTICIPLE to Res.string.tag_verb_form_participle,
    VerbForm.GERUND to Res.string.tag_verb_form_gerund,
    VerbForm.ADVERBIAL to Res.string.tag_verb_form_adverbial,
    VerbForm.SHORT to Res.string.tag_verb_form_short,
    VerbForm.LONG to Res.string.tag_verb_form_long,
    VerbForm.DIMINUTIVE to Res.string.tag_verb_form_diminutive,
    // Tense
    Tense.PRESENT to Res.string.tag_tense_present,
    Tense.PAST to Res.string.tag_tense_past,
    Tense.FUTURE to Res.string.tag_tense_future,
    // Mood
    Mood.INDICATIVE to Res.string.tag_mood_indicative,
    Mood.SUBJUNCTIVE to Res.string.tag_mood_subjunctive,
    Mood.IMPERATIVE to Res.string.tag_mood_imperative,
    Mood.CONDITIONAL to Res.string.tag_mood_conditional,
    Mood.PREDICATIVE to Res.string.tag_mood_predicative,
    Mood.PARTITIVE to Res.string.tag_mood_partitive,
    Mood.IMPERSONAL to Res.string.tag_mood_impersonal,
    // Aspect
    Aspect.IMPERFECTIVE to Res.string.tag_aspect_imperfective,
    Aspect.PERFECTIVE to Res.string.tag_aspect_perfective,
    // Number
    Num.SG to Res.string.tag_number_singular,
    Num.PL to Res.string.tag_number_plural,
    // Person
    Person.FIRST to Res.string.tag_person_first,
    Person.SECOND to Res.string.tag_person_second,
    Person.THIRD to Res.string.tag_person_third,
    // Gender
    Gender.MASC to Res.string.tag_gender_masculine,
    Gender.FEM to Res.string.tag_gender_feminine,
    Gender.NEUT to Res.string.tag_gender_neuter,
    Gender.COMMON to Res.string.tag_gender_common,
    Gender.VIRILE to Res.string.tag_gender_virile,
    Gender.NONVIRILE to Res.string.tag_gender_nonvirile,
    // Animacy
    Animacy.ANIM to Res.string.tag_animacy_animate,
    Animacy.INANIM to Res.string.tag_animacy_inanimate,
    // Case
    Case.NOMINATIVE to Res.string.tag_case_nominative,
    Case.GENITIVE to Res.string.tag_case_genitive,
    Case.DATIVE to Res.string.tag_case_dative,
    Case.ACCUSATIVE to Res.string.tag_case_accusative,
    Case.INSTRUMENTAL to Res.string.tag_case_instrumental,
    Case.PREPOSITIONAL to Res.string.tag_case_prepositional,
    Case.LOCATIVE to Res.string.tag_case_locative,
    Case.VOCATIVE to Res.string.tag_case_vocative,
    // Degree
    Degree.POSITIVE to Res.string.tag_degree_positive,
    Degree.COMPARATIVE to Res.string.tag_degree_comparative,
    Degree.SUPERLATIVE to Res.string.tag_degree_superlative,
    // Clause
    Clause.MAIN to Res.string.tag_clause_main,
    Clause.SUBORDINATE to Res.string.tag_clause_subordinate,
    // Voice
    Voice.ACTIVE to Res.string.tag_voice_active,
    Voice.PASSIVE to Res.string.tag_voice_passive,
    // Definiteness
    Definiteness.DEFINITE to Res.string.tag_definiteness_definite,
    Definiteness.INDEFINITE to Res.string.tag_definiteness_indefinite,
    // Pronoun
    Pronoun.JIJ to Res.string.tag_pronoun_jij,
    Pronoun.U to Res.string.tag_pronoun_u,
    Pronoun.GIJ to Res.string.tag_pronoun_gij,
    // PronounForm
    PronounForm.SUBJECTIVE to Res.string.tag_pronoun_form_subjective,
    PronounForm.OBJECTIVE to Res.string.tag_pronoun_form_objective,
    PronounForm.REFLEXIVE to Res.string.tag_pronoun_form_reflexive,
    // PossessiveType
    PossessiveType.DEPENDENT to Res.string.tag_possessive_type_dependent,
    PossessiveType.INDEPENDENT to Res.string.tag_possessive_type_independent,
)
