package com.slovy.slovymovyapp.data.forms.configs

import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.dictionary.DictionaryPos
import com.slovy.slovymovyapp.data.forms.*

object PlConjugationScheme : ConjugationSchemeProvider {

    private object PolishSchemeTagResolver : SchemeTagResolver {
        override fun resolve(dbTags: Iterable<String>): List<FormTag> = TagMapping.resolve(dbTags)

        override fun selectCandidate(candidates: List<SchemeCellCandidate>): SchemeCellCandidate? {
            return candidates.minWithOrNull(
                compareBy<SchemeCellCandidate> { it.missingRequiredTags }
                    .thenBy { -it.matchedSupportingTags }
                    .thenBy { it.extraMappedTags }
                    .thenBy { it.form }
            )
        }
    }

    /**
     * Polish noun declension.
     *
     * Seven cases × two numbers, matching the en.wiktionary table
     * (e.g. książka). Gender is carried on the lemma_pos entry, not here.
     */
    val PL_NOUN: ConjugationScheme = conjugationScheme(
        "pl_noun",
        Language.POLISH,
        DictionaryPos.NOUN,
        tagResolver = PolishSchemeTagResolver
    ) {
        view("full", "Declension") {
            row {
                empty()
                colHeader("singular")
                colHeader("plural")
            }
            row {
                rowHeader("nominative")
                data(Case.NOMINATIVE, Num.SG)
                data(Case.NOMINATIVE, Num.PL)
            }
            row {
                rowHeader("genitive")
                data(Case.GENITIVE, Num.SG)
                data(Case.GENITIVE, Num.PL)
            }
            row {
                rowHeader("dative")
                data(Case.DATIVE, Num.SG)
                data(Case.DATIVE, Num.PL)
            }
            row {
                rowHeader("accusative")
                data(Case.ACCUSATIVE, Num.SG)
                data(Case.ACCUSATIVE, Num.PL)
            }
            row {
                rowHeader("instrumental")
                data(Case.INSTRUMENTAL, Num.SG)
                data(Case.INSTRUMENTAL, Num.PL)
            }
            row {
                rowHeader("locative")
                data(Case.LOCATIVE, Num.SG)
                data(Case.LOCATIVE, Num.PL)
            }
            row {
                rowHeader("vocative")
                data(Case.VOCATIVE, Num.SG)
                data(Case.VOCATIVE, Num.PL)
            }
        }
    }

    /**
     * Polish verb conjugation (imperfective, e.g. czytać).
     *
     * Polish past tense is gendered, so columns split by gender for sg and
     * virile/non-virile for pl. Sections: present, past, future, conditional,
     * imperative, and non-finite forms (participles + verbal noun).
     *
     * Column layout: masc.sg | fem.sg | neut.sg | virile.pl | non-virile.pl
     */
    val PL_VERB: ConjugationScheme = conjugationScheme(
        "pl_verb",
        Language.POLISH,
        DictionaryPos.VERB,
        tagResolver = PolishSchemeTagResolver
    ) {
        view("full", "Conjugation") {
            row {
                rowHeader("infinitive")
                data(VerbForm.INFINITIVE, colspan = 5)
            }

            // ── Present tense ────────────────────────────────────────────────
            row {
                rowHeader("present tense", colspan = 6)
            }
            row {
                empty()
                colHeader("masc. sg")
                colHeader("fem. sg")
                colHeader("neut. sg")
                colHeader("virile pl")
                colHeader("non-virile pl")
            }
            row {
                rowHeader("1st person")
                data(VerbForm.FINITE, Tense.PRESENT, Person.FIRST, Num.SG, Gender.MASC)
                data(VerbForm.FINITE, Tense.PRESENT, Person.FIRST, Num.SG, Gender.FEM)
                data(VerbForm.FINITE, Tense.PRESENT, Person.FIRST, Num.SG, Gender.NEUT)
                data(VerbForm.FINITE, Tense.PRESENT, Person.FIRST, Num.PL, Gender.VIRILE)
                data(VerbForm.FINITE, Tense.PRESENT, Person.FIRST, Num.PL, Gender.NONVIRILE)
            }
            row {
                rowHeader("2nd person")
                data(VerbForm.FINITE, Tense.PRESENT, Person.SECOND, Num.SG, Gender.MASC)
                data(VerbForm.FINITE, Tense.PRESENT, Person.SECOND, Num.SG, Gender.FEM)
                data(VerbForm.FINITE, Tense.PRESENT, Person.SECOND, Num.SG, Gender.NEUT)
                data(VerbForm.FINITE, Tense.PRESENT, Person.SECOND, Num.PL, Gender.VIRILE)
                data(VerbForm.FINITE, Tense.PRESENT, Person.SECOND, Num.PL, Gender.NONVIRILE)
            }
            row {
                rowHeader("3rd person")
                data(VerbForm.FINITE, Tense.PRESENT, Person.THIRD, Num.SG, Gender.MASC)
                data(VerbForm.FINITE, Tense.PRESENT, Person.THIRD, Num.SG, Gender.FEM)
                data(VerbForm.FINITE, Tense.PRESENT, Person.THIRD, Num.SG, Gender.NEUT)
                data(VerbForm.FINITE, Tense.PRESENT, Person.THIRD, Num.PL, Gender.VIRILE)
                data(VerbForm.FINITE, Tense.PRESENT, Person.THIRD, Num.PL, Gender.NONVIRILE)
            }
            row {
                rowHeader("impersonal")
                data(VerbForm.FINITE, Tense.PRESENT, Mood.IMPERSONAL, colspan = 5)
            }

            // ── Past tense ───────────────────────────────────────────────────
            row {
                rowHeader("past tense", colspan = 6)
            }
            row {
                rowHeader("1st person")
                data(VerbForm.FINITE, Tense.PAST, Person.FIRST, Num.SG, Gender.MASC)
                data(VerbForm.FINITE, Tense.PAST, Person.FIRST, Num.SG, Gender.FEM)
                data(VerbForm.FINITE, Tense.PAST, Person.FIRST, Num.SG, Gender.NEUT)
                data(VerbForm.FINITE, Tense.PAST, Person.FIRST, Num.PL, Gender.VIRILE)
                data(VerbForm.FINITE, Tense.PAST, Person.FIRST, Num.PL, Gender.NONVIRILE)
            }
            row {
                rowHeader("2nd person")
                data(VerbForm.FINITE, Tense.PAST, Person.SECOND, Num.SG, Gender.MASC)
                data(VerbForm.FINITE, Tense.PAST, Person.SECOND, Num.SG, Gender.FEM)
                data(VerbForm.FINITE, Tense.PAST, Person.SECOND, Num.SG, Gender.NEUT)
                data(VerbForm.FINITE, Tense.PAST, Person.SECOND, Num.PL, Gender.VIRILE)
                data(VerbForm.FINITE, Tense.PAST, Person.SECOND, Num.PL, Gender.NONVIRILE)
            }
            row {
                rowHeader("3rd person")
                data(VerbForm.FINITE, Tense.PAST, Person.THIRD, Num.SG, Gender.MASC)
                data(VerbForm.FINITE, Tense.PAST, Person.THIRD, Num.SG, Gender.FEM)
                data(VerbForm.FINITE, Tense.PAST, Person.THIRD, Num.SG, Gender.NEUT)
                data(VerbForm.FINITE, Tense.PAST, Person.THIRD, Num.PL, Gender.VIRILE)
                data(VerbForm.FINITE, Tense.PAST, Person.THIRD, Num.PL, Gender.NONVIRILE)
            }
            row {
                rowHeader("impersonal")
                data(VerbForm.FINITE, Tense.PAST, Mood.IMPERSONAL, colspan = 5)
            }

            // ── Future tense ─────────────────────────────────────────────────
            row {
                rowHeader("future tense", colspan = 6)
            }
            row {
                rowHeader("1st person")
                data(VerbForm.FINITE, Tense.FUTURE, Person.FIRST, Num.SG, Gender.MASC)
                data(VerbForm.FINITE, Tense.FUTURE, Person.FIRST, Num.SG, Gender.FEM)
                data(VerbForm.FINITE, Tense.FUTURE, Person.FIRST, Num.SG, Gender.NEUT)
                data(VerbForm.FINITE, Tense.FUTURE, Person.FIRST, Num.PL, Gender.VIRILE)
                data(VerbForm.FINITE, Tense.FUTURE, Person.FIRST, Num.PL, Gender.NONVIRILE)
            }
            row {
                rowHeader("2nd person")
                data(VerbForm.FINITE, Tense.FUTURE, Person.SECOND, Num.SG, Gender.MASC)
                data(VerbForm.FINITE, Tense.FUTURE, Person.SECOND, Num.SG, Gender.FEM)
                data(VerbForm.FINITE, Tense.FUTURE, Person.SECOND, Num.SG, Gender.NEUT)
                data(VerbForm.FINITE, Tense.FUTURE, Person.SECOND, Num.PL, Gender.VIRILE)
                data(VerbForm.FINITE, Tense.FUTURE, Person.SECOND, Num.PL, Gender.NONVIRILE)
            }
            row {
                rowHeader("3rd person")
                data(VerbForm.FINITE, Tense.FUTURE, Person.THIRD, Num.SG, Gender.MASC)
                data(VerbForm.FINITE, Tense.FUTURE, Person.THIRD, Num.SG, Gender.FEM)
                data(VerbForm.FINITE, Tense.FUTURE, Person.THIRD, Num.SG, Gender.NEUT)
                data(VerbForm.FINITE, Tense.FUTURE, Person.THIRD, Num.PL, Gender.VIRILE)
                data(VerbForm.FINITE, Tense.FUTURE, Person.THIRD, Num.PL, Gender.NONVIRILE)
            }
            row {
                rowHeader("impersonal")
                data(VerbForm.FINITE, Tense.FUTURE, Mood.IMPERSONAL, colspan = 5)
            }

            // ── Conditional ──────────────────────────────────────────────────
            row {
                rowHeader("conditional", colspan = 6)
            }
            row {
                rowHeader("1st person")
                data(Mood.CONDITIONAL, Person.FIRST, Num.SG, Gender.MASC)
                data(Mood.CONDITIONAL, Person.FIRST, Num.SG, Gender.FEM)
                data(Mood.CONDITIONAL, Person.FIRST, Num.SG, Gender.NEUT)
                data(Mood.CONDITIONAL, Person.FIRST, Num.PL, Gender.VIRILE)
                data(Mood.CONDITIONAL, Person.FIRST, Num.PL, Gender.NONVIRILE)
            }
            row {
                rowHeader("2nd person")
                data(Mood.CONDITIONAL, Person.SECOND, Num.SG, Gender.MASC)
                data(Mood.CONDITIONAL, Person.SECOND, Num.SG, Gender.FEM)
                data(Mood.CONDITIONAL, Person.SECOND, Num.SG, Gender.NEUT)
                data(Mood.CONDITIONAL, Person.SECOND, Num.PL, Gender.VIRILE)
                data(Mood.CONDITIONAL, Person.SECOND, Num.PL, Gender.NONVIRILE)
            }
            row {
                rowHeader("3rd person")
                data(Mood.CONDITIONAL, Person.THIRD, Num.SG, Gender.MASC)
                data(Mood.CONDITIONAL, Person.THIRD, Num.SG, Gender.FEM)
                data(Mood.CONDITIONAL, Person.THIRD, Num.SG, Gender.NEUT)
                data(Mood.CONDITIONAL, Person.THIRD, Num.PL, Gender.VIRILE)
                data(Mood.CONDITIONAL, Person.THIRD, Num.PL, Gender.NONVIRILE)
            }
            row {
                rowHeader("impersonal")
                data(Mood.CONDITIONAL, colspan = 5)
            }

            // ── Imperative ───────────────────────────────────────────────────
            row {
                rowHeader("imperative", colspan = 6)
            }
            row {
                rowHeader("1st person")
                data(VerbForm.FINITE, Mood.IMPERATIVE, Person.FIRST, Num.SG, colspan = 5)
            }
            row {
                rowHeader("2nd person")
                data(VerbForm.FINITE, Mood.IMPERATIVE, Person.SECOND, Num.SG)
                empty(colspan = 2)
                data(VerbForm.FINITE, Mood.IMPERATIVE, Person.SECOND, Num.PL, colspan = 2)
            }
            row {
                rowHeader("3rd person")
                data(VerbForm.FINITE, Mood.IMPERATIVE, Person.THIRD, Num.SG, colspan = 5)
            }

            // ── Non-finite forms ─────────────────────────────────────────────
            row {
                rowHeader("active adjectival participle")
                data(VerbForm.PARTICIPLE, Voice.ACTIVE, Gender.MASC)
                data(VerbForm.PARTICIPLE, Voice.ACTIVE, Gender.FEM)
                data(VerbForm.PARTICIPLE, Voice.ACTIVE, Gender.NEUT)
                data(VerbForm.PARTICIPLE, Voice.ACTIVE, Gender.VIRILE)
                data(VerbForm.PARTICIPLE, Voice.ACTIVE, Gender.NONVIRILE)
            }
            row {
                rowHeader("passive adjectival participle")
                data(VerbForm.PARTICIPLE, Voice.PASSIVE, Gender.MASC)
                data(VerbForm.PARTICIPLE, Voice.PASSIVE, Gender.FEM)
                data(VerbForm.PARTICIPLE, Voice.PASSIVE, Gender.NEUT)
                data(VerbForm.PARTICIPLE, Voice.PASSIVE, Gender.VIRILE)
                data(VerbForm.PARTICIPLE, Voice.PASSIVE, Gender.NONVIRILE)
            }
            row {
                rowHeader("contemporary adverbial participle")
                data(VerbForm.ADVERBIAL, Tense.PRESENT, colspan = 5)
            }
            row {
                rowHeader("verbal noun")
                data(VerbForm.GERUND, colspan = 5)
            }
        }
    }

    /**
     * Polish adjective declension (e.g. piękny).
     *
     * Six columns for gender/animacy combinations × six cases.
     * Nominative and vocative share the same forms so they are one row.
     */
    val PL_ADJECTIVE: ConjugationScheme = conjugationScheme(
        "pl_adjective",
        Language.POLISH,
        DictionaryPos.ADJECTIVE,
        tagResolver = PolishSchemeTagResolver
    ) {
        view("full", "Declension") {
            row {
                empty()
                colHeader("masc. animate sg")
                colHeader("masc. inanimate sg")
                colHeader("feminine sg")
                colHeader("neuter sg")
                colHeader("virile pl")
                colHeader("non-virile pl")
            }
            row {
                rowHeader("nominative / vocative")
                data(Case.NOMINATIVE, Gender.MASC, Animacy.ANIM, Num.SG)
                data(Case.NOMINATIVE, Gender.MASC, Animacy.INANIM, Num.SG)
                data(Case.NOMINATIVE, Gender.FEM, Num.SG)
                data(Case.NOMINATIVE, Gender.NEUT, Num.SG)
                data(Case.NOMINATIVE, Gender.VIRILE, Num.PL)
                data(Case.NOMINATIVE, Gender.NONVIRILE, Num.PL)
            }
            row {
                rowHeader("genitive")
                data(Case.GENITIVE, Gender.MASC, Num.SG, colspan = 2)
                data(Case.GENITIVE, Gender.FEM, Num.SG)
                data(Case.GENITIVE, Gender.NEUT, Num.SG)
                data(Case.GENITIVE, Num.PL, colspan = 2)
            }
            row {
                rowHeader("dative")
                data(Case.DATIVE, Gender.MASC, Num.SG, colspan = 2)
                data(Case.DATIVE, Gender.FEM, Num.SG)
                data(Case.DATIVE, Gender.NEUT, Num.SG)
                data(Case.DATIVE, Num.PL, colspan = 2)
            }
            row {
                rowHeader("accusative")
                data(Case.ACCUSATIVE, Gender.MASC, Animacy.ANIM, Num.SG)
                data(Case.ACCUSATIVE, Gender.MASC, Animacy.INANIM, Num.SG)
                data(Case.ACCUSATIVE, Gender.FEM, Num.SG)
                data(Case.ACCUSATIVE, Gender.NEUT, Num.SG)
                data(Case.ACCUSATIVE, Gender.VIRILE, Num.PL)
                data(Case.ACCUSATIVE, Gender.NONVIRILE, Num.PL)
            }
            row {
                rowHeader("instrumental")
                data(Case.INSTRUMENTAL, Gender.MASC, Num.SG, colspan = 2)
                data(Case.INSTRUMENTAL, Gender.FEM, Num.SG)
                data(Case.INSTRUMENTAL, Gender.NEUT, Num.SG)
                data(Case.INSTRUMENTAL, Num.PL, colspan = 2)
            }
            row {
                rowHeader("locative")
                data(Case.LOCATIVE, Gender.MASC, Num.SG, colspan = 2)
                data(Case.LOCATIVE, Gender.FEM, Num.SG)
                data(Case.LOCATIVE, Gender.NEUT, Num.SG)
                data(Case.LOCATIVE, Num.PL, colspan = 2)
            }
        }
    }

    /** All Polish schemes for easy lookup. */
    val ALL: List<ConjugationScheme> = listOf(PL_NOUN, PL_VERB, PL_ADJECTIVE)

    override fun schemesFor(pos: DictionaryPos, forms: List<SchemeInputForm>): List<ConjugationScheme> =
        ALL.filter { it.pos == pos }
}
