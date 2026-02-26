package com.slovy.slovymovyapp.data.forms.configs

import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.dictionary.DictionaryPos
import com.slovy.slovymovyapp.data.dictionary.FormSource
import com.slovy.slovymovyapp.data.forms.*

object PlConjugationScheme : ConjugationSchemeProvider {

    private object PolishSchemeTagResolver : SchemeTagResolver {
        override fun preprocessForms(forms: List<SchemeInputForm>, lemma: String?): List<SchemeInputForm> {
            val extras = forms.flatMap { form ->
                val tags = form.tags
                val result = mutableListOf<SchemeInputForm>()

                // Wiktionary tags virile (masculine-personal) plural forms as "masculine"
                // rather than "virile". Add a heuristic copy with "virile" so scheme
                // cells matching Gender.VIRILE can resolve these forms.
                if ("plural" in tags && "masculine" in tags && "virile" !in tags && "nonvirile" !in tags) {
                    result += form.copy(tags = tags.map { if (it == "masculine") "virile" else it }, source = FormSource.HEURISTIC)
                }

                // Wiktionary tags non-virile plural forms as "feminine,neuter" rather
                // than "nonvirile". Add a heuristic copy with "nonvirile" so scheme
                // cells matching Gender.NONVIRILE can resolve these forms.
                if ("plural" in tags && "feminine" in tags && "neuter" in tags
                    && "masculine" !in tags && "virile" !in tags && "nonvirile" !in tags
                ) {
                    result += form.copy(tags = tags + "nonvirile", source = FormSource.HEURISTIC)
                }

                result
            }
            return if (extras.isEmpty()) forms else forms + extras
        }

        override fun selectCandidate(candidates: List<SchemeCellCandidate>): SchemeCellCandidate? {
            return candidates.minWithOrNull(
                compareBy<SchemeCellCandidate> { -it.matchedPreferredTags }
                    .thenBy { it.extraKnownTags }
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
     * Present tense is not gendered (one form per person/number).
     * Past, future, and conditional are gendered: masc/fem sg per person,
     * neut sg for 3rd person only, virile/non-virile pl for all persons.
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

            // ── Present tense (not gendered) ─────────────────────────────────
            row {
                rowHeader("present tense", colspan = 6)
            }
            row {
                empty()
                colHeader("singular", colspan = 3)
                colHeader("plural", colspan = 2)
            }
            row {
                rowHeader("1st person")
                data(Tense.PRESENT, Person.FIRST, Num.SG, colspan = 3)
                data(Tense.PRESENT, Person.FIRST, Num.PL, colspan = 2)
            }
            row {
                rowHeader("2nd person")
                data(Tense.PRESENT, Person.SECOND, Num.SG, colspan = 3)
                data(Tense.PRESENT, Person.SECOND, Num.PL, colspan = 2)
            }
            row {
                rowHeader("3rd person")
                data(Tense.PRESENT, Person.THIRD, Num.SG, colspan = 3)
                data(Tense.PRESENT, Person.THIRD, Num.PL, colspan = 2)
            }
            row {
                rowHeader("impersonal")
                data(Tense.PRESENT, Mood.IMPERSONAL, colspan = 5)
            }

            // ── Shared gendered column headers ────────────────────────────────
            row {
                empty()
                colHeader("masc. sg")
                colHeader("fem. sg")
                colHeader("neut. sg")
                colHeader("virile pl")
                colHeader("non-virile pl")
            }

            // ── Past tense ───────────────────────────────────────────────────
            row {
                rowHeader("past tense", colspan = 6)
            }
            row {
                rowHeader("1st person")
                data(Tense.PAST, Person.FIRST, Num.SG, Gender.MASC)
                data(Tense.PAST, Person.FIRST, Num.SG, Gender.FEM)
                empty()
                data(Tense.PAST, Person.FIRST, Num.PL, Gender.VIRILE)
                data(Tense.PAST, Person.FIRST, Num.PL, Gender.NONVIRILE)
            }
            row {
                rowHeader("2nd person")
                data(Tense.PAST, Person.SECOND, Num.SG, Gender.MASC)
                data(Tense.PAST, Person.SECOND, Num.SG, Gender.FEM)
                empty()
                data(Tense.PAST, Person.SECOND, Num.PL, Gender.VIRILE)
                data(Tense.PAST, Person.SECOND, Num.PL, Gender.NONVIRILE)
            }
            row {
                rowHeader("3rd person")
                data(Tense.PAST, Person.THIRD, Num.SG, Gender.MASC)
                data(Tense.PAST, Person.THIRD, Num.SG, Gender.FEM)
                data(Tense.PAST, Person.THIRD, Num.SG, Gender.NEUT)
                data(Tense.PAST, Person.THIRD, Num.PL, Gender.VIRILE)
                data(Tense.PAST, Person.THIRD, Num.PL, Gender.NONVIRILE)
            }
            row {
                rowHeader("impersonal")
                data(Tense.PAST, Mood.IMPERSONAL, colspan = 5)
            }

            // ── Future tense ─────────────────────────────────────────────────
            row {
                rowHeader("future tense", colspan = 6)
            }
            row {
                rowHeader("1st person")
                data(Tense.FUTURE, Person.FIRST, Num.SG, Gender.MASC)
                data(Tense.FUTURE, Person.FIRST, Num.SG, Gender.FEM)
                empty()
                data(Tense.FUTURE, Person.FIRST, Num.PL, Gender.VIRILE)
                data(Tense.FUTURE, Person.FIRST, Num.PL, Gender.NONVIRILE)
            }
            row {
                rowHeader("2nd person")
                data(Tense.FUTURE, Person.SECOND, Num.SG, Gender.MASC)
                data(Tense.FUTURE, Person.SECOND, Num.SG, Gender.FEM)
                empty()
                data(Tense.FUTURE, Person.SECOND, Num.PL, Gender.VIRILE)
                data(Tense.FUTURE, Person.SECOND, Num.PL, Gender.NONVIRILE)
            }
            row {
                rowHeader("3rd person")
                data(Tense.FUTURE, Person.THIRD, Num.SG, Gender.MASC)
                data(Tense.FUTURE, Person.THIRD, Num.SG, Gender.FEM)
                data(Tense.FUTURE, Person.THIRD, Num.SG, Gender.NEUT)
                data(Tense.FUTURE, Person.THIRD, Num.PL, Gender.VIRILE)
                data(Tense.FUTURE, Person.THIRD, Num.PL, Gender.NONVIRILE)
            }

            // ── Conditional ──────────────────────────────────────────────────
            row {
                rowHeader("conditional", colspan = 6)
            }
            row {
                rowHeader("1st person")
                data(Mood.CONDITIONAL, Person.FIRST, Num.SG, Gender.MASC)
                data(Mood.CONDITIONAL, Person.FIRST, Num.SG, Gender.FEM)
                empty()
                data(Mood.CONDITIONAL, Person.FIRST, Num.PL, Gender.VIRILE)
                data(Mood.CONDITIONAL, Person.FIRST, Num.PL, Gender.NONVIRILE)
            }
            row {
                rowHeader("2nd person")
                data(Mood.CONDITIONAL, Person.SECOND, Num.SG, Gender.MASC)
                data(Mood.CONDITIONAL, Person.SECOND, Num.SG, Gender.FEM)
                empty()
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
                empty()
                colHeader("singular", colspan = 3)
                colHeader("plural", colspan = 2)
            }
            row {
                rowHeader("1st person")
                empty(colspan = 3)
                data(Mood.IMPERATIVE, Person.FIRST, Num.PL, colspan = 2)
            }
            row {
                rowHeader("2nd person")
                data(Mood.IMPERATIVE, Person.SECOND, Num.SG, colspan = 3)
                data(Mood.IMPERATIVE, Person.SECOND, Num.PL, colspan = 2)
            }
            row {
                rowHeader("3rd person")
                data(Mood.IMPERATIVE, Person.THIRD, Num.SG, colspan = 3)
                data(Mood.IMPERATIVE, Person.THIRD, Num.PL, colspan = 2)
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
                data(VerbForm.ADVERBIAL, colspan = 5, supporting = setOf(Tense.PRESENT))
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

    override fun schemeFor(pos: DictionaryPos, forms: List<SchemeInputForm>): ConjugationScheme? =
        ALL.firstOrNull { it.pos == pos }
}
