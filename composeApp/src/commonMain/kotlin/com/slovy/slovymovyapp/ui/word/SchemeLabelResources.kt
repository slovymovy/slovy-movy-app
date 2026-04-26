package com.slovy.slovymovyapp.ui.word

import org.jetbrains.compose.resources.StringResource
import slovymovyapp.composeapp.generated.resources.Res
import slovymovyapp.composeapp.generated.resources.*

/**
 * Maps scheme-specific composite labels (row/col headers and view descriptions) to localized resources.
 *
 * Used as a second lookup after [com.slovy.slovymovyapp.data.forms.TagMapping.resolve] fails
 * for labels that don't correspond to a single [com.slovy.slovymovyapp.data.forms.FormTag].
 *
 * Cell label keys are lowercased; lookup must lowercase the cell label too.
 * View description keys match the source string verbatim (preserving casing).
 *
 * Coverage is enforced by `SchemeLabelCoverageTest` — every label appearing in any scheme config
 * must either resolve via TagMapping (case-insensitive) or be present here.
 */
val schemeLabelResources: Map<String, StringResource> = mapOf(
    // ── View descriptions (case preserved) ──────────────────────────────────
    "Declension" to Res.string.scheme_view_declension,
    "Imperfective conjugation" to Res.string.scheme_view_imperfective_conjugation,
    "Perfective conjugation" to Res.string.scheme_view_perfective_conjugation,
    "Essentials" to Res.string.scheme_view_essentials,
    "Full table" to Res.string.scheme_view_full_table,
    "Degrees of comparison" to Res.string.scheme_view_degrees_of_comparison,
    "Principal parts" to Res.string.scheme_view_principal_parts,
    "Number" to Res.string.scheme_view_number,
    "Cases" to Res.string.scheme_view_cases,
    "Forms" to Res.string.scheme_view_forms,
    "Conjugation table" to Res.string.scheme_view_conjugation_table,
    "Advanced" to Res.string.scheme_view_advanced,
    "Declension and comparison" to Res.string.scheme_view_declension_and_comparison,

    // ── Cell labels (lowercase keys; lookup must lowercase too) ─────────────
    "category" to Res.string.scheme_label_category,
    "form" to Res.string.scheme_label_form,
    "present tense" to Res.string.scheme_label_present_tense,
    "past tense" to Res.string.scheme_label_past_tense,
    "future tense" to Res.string.scheme_label_future_tense,
    "active participle" to Res.string.scheme_label_active_participle,
    "passive participle" to Res.string.scheme_label_passive_participle,
    "adverbial participle" to Res.string.scheme_label_adverbial_participle,
    "past participle" to Res.string.scheme_label_past_participle,
    "present participle" to Res.string.scheme_label_present_participle,
    "present (i)" to Res.string.scheme_label_present_i,
    "present (you)" to Res.string.scheme_label_present_you,
    "present (he/she/it)" to Res.string.scheme_label_present_he_she_it,
    "present (ik)" to Res.string.scheme_label_present_ik,
    "present (jij/je)" to Res.string.scheme_label_present_jij,
    "present (hij/zij/het)" to Res.string.scheme_label_present_hij,
    "past simple" to Res.string.scheme_label_past_simple,
    "past singular" to Res.string.scheme_label_past_singular,
    "past plural" to Res.string.scheme_label_past_plural,
    "1st singular" to Res.string.scheme_label_first_singular,
    "2nd singular" to Res.string.scheme_label_second_singular,
    "3rd singular" to Res.string.scheme_label_third_singular,
    "present perfect" to Res.string.scheme_label_present_perfect,
    "future perfect" to Res.string.scheme_label_future_perfect,
    "conditional perfect" to Res.string.scheme_label_conditional_perfect,
    "subject" to Res.string.scheme_label_subject,
    "object" to Res.string.scheme_label_object,
    "possessive adjective" to Res.string.scheme_label_possessive_adjective,
    "possessive pronoun" to Res.string.scheme_label_possessive_pronoun,
    "diminutive plural" to Res.string.scheme_label_diminutive_plural,
    "positive (base)" to Res.string.scheme_label_positive_base,
    "inflected (+e)" to Res.string.scheme_label_inflected,
    "predicative / adverbial" to Res.string.scheme_label_predicative_adverbial,
    "indefinite m./f. sing." to Res.string.scheme_label_indefinite_mf_sing,
    "indefinite neut. sing." to Res.string.scheme_label_indefinite_neut_sing,
    "masc. sg" to Res.string.scheme_label_masc_sg,
    "fem. sg" to Res.string.scheme_label_fem_sg,
    "neut. sg" to Res.string.scheme_label_neut_sg,
    "virile pl" to Res.string.scheme_label_virile_pl,
    "non-virile pl" to Res.string.scheme_label_nonvirile_pl,
    "virile plural" to Res.string.scheme_label_virile_pl,
    "non-virile plural" to Res.string.scheme_label_nonvirile_pl,
    "nominative / vocative" to Res.string.scheme_label_nominative_vocative,
    "masc. animate sg" to Res.string.scheme_label_masc_animate_sg,
    "masc. inanimate sg" to Res.string.scheme_label_masc_inanimate_sg,
    "feminine sg" to Res.string.scheme_label_feminine_sg,
    "neuter sg" to Res.string.scheme_label_neuter_sg,
    "active adjectival participle" to Res.string.scheme_label_active_adjectival_participle,
    "passive adjectival participle" to Res.string.scheme_label_passive_adjectival_participle,
    "contemporary adverbial participle" to Res.string.scheme_label_contemporary_adverbial_participle,
    "verbal noun" to Res.string.scheme_label_verbal_noun,

    // ── Reused tag_* resources for compound labels semantically equal to a single FormTag ──
    "1st person" to Res.string.tag_person_first,
    "2nd person" to Res.string.tag_person_second,
    "3rd person" to Res.string.tag_person_third,
    "short form" to Res.string.tag_verb_form_short,
)
