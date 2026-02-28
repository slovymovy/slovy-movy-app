package com.slovy.slovymovyapp.data.forms.configs

import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.dictionary.DictionaryPos
import com.slovy.slovymovyapp.data.dictionary.FormSource
import com.slovy.slovymovyapp.data.forms.*

object NlConjugationScheme : ConjugationSchemeProvider {

    private object DutchSchemeTagResolver : SchemeTagResolver {
        override fun preprocessForms(forms: List<SchemeInputForm>, lemma: String?): List<SchemeInputForm> {
            // Drop forms that are purely parenthetical (e.g. "(kindeke)") or trailing-comma data
            // errors (e.g. "kindjes,").  ASCII '(' = 40 sorts before all letters, so parenthetical-
            // only forms incorrectly win the alphabetical tiebreaker over standard modern forms.
            // Diminutive forms with optional-suffix notation (e.g. "manneke(n)") are normalized by
            // stripping the parenthetical part rather than dropped, so the cell isn't left empty
            // when no non-parenthetical variant exists.
            val cleanForms = forms
                .map { form ->
                    if ('(' in form.form && "diminutive" in form.tags)
                        form.copy(form = form.form.substringBefore('(').trim(), source = FormSource.HEURISTIC)
                    else
                        form.copy(form = form.form.trim())
                }
                .filter { form ->
                    form.form.isNotEmpty() && !form.form.startsWith('(') && !form.form.endsWith(',')
                }

            // Canonicalize diminutive number tags using Dutch morphology (-je = singular, -jes = plural).
            // Always strip both number tags first and re-add the canonical one, so conflicting
            // combinations (e.g. -je + plural, or -jes + singular + plural) are fully corrected.
            val correctedForms = cleanForms.map { form ->
                val tags = form.tags
                when {
                    "diminutive" in tags && form.form.endsWith("jes") ->
                        form.copy(tags = (tags - "singular" - "plural") + "plural", source = FormSource.HEURISTIC)
                    "diminutive" in tags && form.form.endsWith("je") ->
                        form.copy(tags = (tags - "singular" - "plural") + "singular", source = FormSource.HEURISTIC)
                    else -> form
                }
            }

            val nonFiniteTags = setOf("infinitive", "gerund", "participle", "adverbial")
            val extras = correctedForms.flatMap { form ->
                val tags = form.tags
                val result = mutableListOf<SchemeInputForm>()

                // Wiktionary tags some Dutch common-gender forms with both "masculine" and
                // "feminine" instead of "common". Add a heuristic copy with "common" so
                // scheme cells matching Gender.COMMON can resolve them.
                if ("masculine" in tags && "feminine" in tags && "common" !in tags) {
                    result += form.copy(tags = tags + "common", source = FormSource.HEURISTIC)
                }

                // "imperfect" on non-finite forms signals imperfective aspect, not past tense.
                // Add a heuristic copy with "imperfective" so infinitive ranking prefers the
                // basic "te <verb>" form over the imperfect stem.
                if ("imperfect" in tags && tags.any { it in nonFiniteTags }) {
                    result += form.copy(
                        tags = tags.map { if (it == "imperfect") "imperfective" else it },
                        source = FormSource.HEURISTIC
                    )
                }

                // Dutch conditional simple forms (zou/zouden + infinitive) are tagged
                // "conditional + imperfect + indicative" with no "past" tag.
                // Inject a heuristic "past" so they match scheme cells using
                // Mood.CONDITIONAL + Tense.PAST (which distinguishes conditional simple from
                // conditional perfect, whose "perfect" tag has no tense mapping).
                if ("conditional" in tags && "imperfect" in tags && "past" !in tags && "perfect" !in tags) {
                    result += form.copy(tags = tags + "past", source = FormSource.HEURISTIC)
                }

                result
            }
            return if (extras.isEmpty()) correctedForms else correctedForms + extras
        }

        override fun selectCandidate(candidates: List<SchemeCellCandidate>): SchemeCellCandidate? {
            return candidates.minWithOrNull(
                compareBy<SchemeCellCandidate> { -it.matchedPreferredTags }
                    .thenBy { it.extraKnownTags }
                    // Prefer shorter forms: archaic "gij" forms (kwaamt, laast, naamt, zaagt)
                    // share identical tags with modern forms but are longer due to the -t suffix.
                    .thenBy { it.form.length }
                    .thenBy { it.form }
            )
        }

    }

    val NL_VERB: ConjugationScheme = conjugationScheme(
        "nl_verb",
        Language.DUTCH,
        DictionaryPos.VERB,
        tagResolver = DutchSchemeTagResolver
    ) {

        view("category_summary", "Essentials") {
            row {
                colHeader("Category")
                colHeader("Form")
            }
            row {
                rowHeader("Infinitive")
                data(
                    VerbForm.INFINITIVE,
                    supporting = setOf(Voice.ACTIVE, VerbForm.LONG, Tense.PRESENT, Aspect.IMPERFECTIVE)
                )
            }
            row {
                rowHeader("Present (ik)")
                data(Tense.PRESENT, Person.FIRST, Num.SG, supporting = setOf(Mood.INDICATIVE))
            }
            row {
                rowHeader("Present (jij/hij/u)")
                data(Tense.PRESENT, Person.THIRD, Num.SG, supporting = setOf(Mood.INDICATIVE))
            }
            row {
                rowHeader("Past singular")
                data(Tense.PAST, Num.SG, supporting = setOf(Mood.INDICATIVE))
            }
            row {
                rowHeader("Past plural")
                data(Tense.PAST, Num.PL, supporting = setOf(Mood.INDICATIVE))
            }
            row {
                rowHeader("Past participle")
                data(VerbForm.PARTICIPLE, Tense.PAST)
            }
            row {
                rowHeader("Present participle")
                data(VerbForm.PARTICIPLE, supporting = setOf(Tense.PRESENT, Aspect.IMPERFECTIVE))
            }
        }

        view("full", "Conjugation table") {
            row {
                empty()
                colHeader("1st singular")
                colHeader("2nd singular")
                colHeader("3rd singular")
                colHeader("plural")
            }
            row {
                rowHeader("Present")
                data(Tense.PRESENT, Person.FIRST, Num.SG, supporting = setOf(Mood.INDICATIVE))
                data(Tense.PRESENT, Person.SECOND, Num.SG, supporting = setOf(Mood.INDICATIVE))
                data(Tense.PRESENT, Person.THIRD, Num.SG, supporting = setOf(Mood.INDICATIVE))
                data(Tense.PRESENT, Num.PL, supporting = setOf(Mood.INDICATIVE))
            }
            row {
                rowHeader("Past")
                data(Tense.PAST, Person.FIRST, Num.SG, supporting = setOf(Mood.INDICATIVE))
                data(Tense.PAST, Person.SECOND, Num.SG, supporting = setOf(Mood.INDICATIVE))
                data(Tense.PAST, Person.THIRD, Num.SG, supporting = setOf(Mood.INDICATIVE))
                data(Tense.PAST, Num.PL, supporting = setOf(Mood.INDICATIVE))
            }
            row {
                rowHeader("Conditional")
                data(Mood.CONDITIONAL, Tense.PAST, Person.FIRST, Num.SG, supporting = setOf(Mood.INDICATIVE))
                data(Mood.CONDITIONAL, Tense.PAST, Person.SECOND, Num.SG, supporting = setOf(Mood.INDICATIVE))
                data(Mood.CONDITIONAL, Tense.PAST, Person.THIRD, Num.SG, supporting = setOf(Mood.INDICATIVE))
                data(Mood.CONDITIONAL, Tense.PAST, Num.PL, supporting = setOf(Mood.INDICATIVE))
            }
            row {
                rowHeader("Future")
                data(Tense.FUTURE, Person.FIRST, Num.SG, supporting = setOf(Mood.INDICATIVE))
                data(Tense.FUTURE, Person.SECOND, Num.SG, supporting = setOf(Mood.INDICATIVE))
                data(Tense.FUTURE, Person.THIRD, Num.SG, supporting = setOf(Mood.INDICATIVE))
                data(Tense.FUTURE, Num.PL, supporting = setOf(Mood.INDICATIVE))
            }
            row {
                rowHeader("Imperative")
                data(Mood.IMPERATIVE, supporting = setOf(Num.SG))
                empty(colspan = 3)
            }
            row {
                empty()
                colHeader("1st person")
                colHeader("2nd person")
                colHeader("3rd person")
                empty()
            }
            row {
                rowHeader("Present perfect")
                data(Tense.PRESENT, Person.FIRST)
                data(Tense.PRESENT, Person.SECOND)
                data(Tense.PRESENT, Person.THIRD)
                empty()
            }
            row {
                rowHeader("Conditional perfect")
                data(Mood.CONDITIONAL, Person.FIRST)
                data(Mood.CONDITIONAL, Person.SECOND)
                data(Mood.CONDITIONAL, Person.THIRD)
                empty()
            }
            row {
                rowHeader("Future perfect")
                data(Tense.FUTURE, Person.FIRST)
                data(Tense.FUTURE, Person.SECOND)
                data(Tense.FUTURE, Person.THIRD)
                empty()
            }
        }
    }

    /**
     * Dutch noun paradigm.
     *
     * Dutch nouns distinguish singular / plural and optionally a diminutive.
     */
    val NL_NOUN: ConjugationScheme = conjugationScheme(
        "nl_noun",
        Language.DUTCH,
        DictionaryPos.NOUN,
        tagResolver = DutchSchemeTagResolver
    ) {
        view("short", "Forms") {
            row {
                colHeader("Category")
                colHeader("Form")
            }
            row {
                rowHeader("Plural")
                data(Num.PL, forbidden = setOf(VerbForm.DIMINUTIVE))
            }
            row {
                rowHeader("Diminutive")
                data(Num.SG, VerbForm.DIMINUTIVE)
            }
            row {
                rowHeader("Diminutive plural")
                data(Num.PL, VerbForm.DIMINUTIVE)
            }
        }
    }

    /**
     * Dutch adjective paradigm.
     *
     * Based on the wiktionary table for "mooi":
     * rows: predicative/adverbial, indefinite m./f. sg, indefinite neut. sg,
     *       plural, definite, partitive.
     * cols: positive, comparative, superlative.
     */
    val NL_ADJECTIVE: ConjugationScheme = conjugationScheme(
        "nl_adjective",
        Language.DUTCH,
        DictionaryPos.ADJECTIVE,
        tagResolver = DutchSchemeTagResolver
    ) {
        view("category_summary", "Compact") {
            row {
                colHeader("Category")
                colHeader("Form")
            }
            row {
                rowHeader("Positive (Base)")
                data(Degree.POSITIVE, supporting = setOf(Mood.PREDICATIVE), forbidden = setOf(Mood.PARTITIVE))
            }
            row {
                rowHeader("Inflected (+e)")
                data(Degree.POSITIVE, Definiteness.DEFINITE)
            }
            row {
                rowHeader("Comparative")
                data(Degree.COMPARATIVE)
            }
            row {
                rowHeader("Superlative")
                data(Degree.SUPERLATIVE)
            }
            row {
                rowHeader("Partitive")
                data(Mood.PARTITIVE, Degree.POSITIVE)
            }
        }

        view("full", "Declension and comparison") {
            row {
                empty()
                colHeader("positive")
                colHeader("comparative")
                colHeader("superlative")
            }
            row {
                rowHeader("predicative / adverbial")
                data(Degree.POSITIVE, Mood.PREDICATIVE)
                data(Degree.COMPARATIVE, Mood.PREDICATIVE)
                data(Degree.SUPERLATIVE, Mood.PREDICATIVE)
            }
            row {
                rowHeader("indefinite m./f. sing.")
                data(Degree.POSITIVE, Definiteness.INDEFINITE, Gender.COMMON, Num.SG)
                data(Degree.COMPARATIVE, Definiteness.INDEFINITE, Gender.COMMON, Num.SG)
                data(Degree.SUPERLATIVE, Definiteness.INDEFINITE, Gender.COMMON, Num.SG)
            }
            row {
                rowHeader("indefinite neut. sing.")
                data(Degree.POSITIVE, Definiteness.INDEFINITE, Gender.NEUT, Num.SG)
                data(Degree.COMPARATIVE, Definiteness.INDEFINITE, Gender.NEUT, Num.SG)
                data(Degree.SUPERLATIVE, Definiteness.INDEFINITE, Gender.NEUT, Num.SG)
            }
            row {
                rowHeader("plural")
                data(Degree.POSITIVE, Num.PL)
                data(Degree.COMPARATIVE, Num.PL)
                data(Degree.SUPERLATIVE, Num.PL)
            }
            row {
                rowHeader("definite")
                data(Degree.POSITIVE, Definiteness.DEFINITE)
                data(Degree.COMPARATIVE, Definiteness.DEFINITE)
                data(Degree.SUPERLATIVE, Definiteness.DEFINITE)
            }
            row {
                rowHeader("partitive")
                data(Degree.POSITIVE, Mood.PARTITIVE)
                data(Degree.COMPARATIVE, Mood.PARTITIVE)
                empty()
            }
        }
    }

    val NL_ADVERB: ConjugationScheme = conjugationScheme(
        "nl_adverb",
        Language.DUTCH,
        DictionaryPos.ADVERB,
        tagResolver = DutchSchemeTagResolver
    ) {
        view("short", "Forms") {
            row {
                colHeader("Category")
                colHeader("Form")
            }
            row {
                rowHeader("Comparative")
                data(Degree.COMPARATIVE)
            }
            row {
                rowHeader("Superlative")
                data(Degree.SUPERLATIVE)
            }
        }
    }

    /** All Dutch schemes for easy lookup. */
    val ALL: List<ConjugationScheme> = listOf(NL_VERB, NL_NOUN, NL_ADJECTIVE, NL_ADVERB)

    override fun schemeFor(pos: DictionaryPos, forms: List<SchemeInputForm>): ConjugationScheme? =
        ALL.firstOrNull { it.pos == pos }
}
