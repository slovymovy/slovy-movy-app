package com.slovy.slovymovyapp.data.forms.configs

import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.dictionary.DictionaryPos
import com.slovy.slovymovyapp.data.forms.*

object NlConjugationScheme : ConjugationSchemeProvider {

    private object DutchSchemeTagResolver : SchemeTagResolver {
        override fun resolve(dbTags: Iterable<String>): List<FormTag> {
            val rawTags = dbTags.toList()
            val hasNonFinite = rawTags.any { it in setOf("infinitive", "gerund", "participle", "adverbial") }
            val resolved = rawTags.mapNotNull { dbTag ->
                if (dbTag == "imperfect" && hasNonFinite) {
                    // For non-finite Dutch forms, treat "imperfect" as an aspect-like signal
                    // so infinitive ranking can prefer the basic "te <verb>" form.
                    Aspect.IMPERFECTIVE
                } else {
                    TagMapping.resolve(dbTag)
                }
            }
            val hasMasculine = rawTags.any { it == "masculine" }
            val hasFeminine = rawTags.any { it == "feminine" }
            val hasCommon = rawTags.any { it == "common" }
            return if (hasMasculine && hasFeminine && !hasCommon) {
                resolved + Gender.COMMON
            } else {
                resolved
            }
        }

        override fun selectCandidate(candidates: List<SchemeCellCandidate>): SchemeCellCandidate? {
            return candidates.minWithOrNull(
                compareBy<SchemeCellCandidate> { it.missingRequiredTags }
                    .thenBy { -it.matchedSupportingTags }
                    .thenBy { it.extraMappedTags }
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

        view("category_summary", "Compact") {
            row {
                colHeader("Category")
                colHeader("Form")
                colHeader("Category")
                colHeader("Form")
            }
            row {
                rowHeader("Infinitive")
                data(
                    VerbForm.INFINITIVE,
                    supporting = setOf(Voice.ACTIVE, VerbForm.LONG, Tense.PRESENT, Aspect.IMPERFECTIVE)
                )
                rowHeader("Present (ik)")
                data(Tense.PRESENT, Person.FIRST, Num.SG, supporting = setOf(Mood.INDICATIVE))
            }
            row {
                rowHeader("Present (jij/hij)")
                data(Tense.PRESENT, Person.THIRD, Num.SG, supporting = setOf(Mood.INDICATIVE))
                rowHeader("Past Singular")
                data(Tense.PAST, Num.SG, supporting = setOf(Mood.INDICATIVE))
            }
            row {
                rowHeader("Past Plural")
                data(Tense.PAST, Num.PL, supporting = setOf(Mood.INDICATIVE))
                rowHeader("Past Participle")
                data(VerbForm.PARTICIPLE, Tense.PAST)
            }
            row {
                rowHeader("Pres. Participle")
                data(VerbForm.PARTICIPLE, supporting = setOf(Tense.PRESENT, Aspect.IMPERFECTIVE))
                empty(colspan = 2)
            }
        }

        view("full", "Full conjugation table") {
            row {
                rowHeader("infinitive")
                data(
                    VerbForm.INFINITIVE,
                    colspan = 4,
                    supporting = setOf(Voice.ACTIVE, VerbForm.LONG, Aspect.IMPERFECTIVE)
                )
            }
            row {
                rowHeader("gerund")
                data(VerbForm.GERUND, supporting = setOf(Gender.NEUT), colspan = 4)
            }
            row {
                rowHeader("past participle")
                data(VerbForm.PARTICIPLE, Tense.PAST, colspan = 4)
            }
            row {
                rowHeader("present participle")
                data(VerbForm.PARTICIPLE, supporting = setOf(Tense.PRESENT, Aspect.IMPERFECTIVE))
                empty(colspan = 3)
            }
            row {
                empty()
                colHeader("1st singular")
                colHeader("2nd singular")
                colHeader("3rd singular")
                colHeader("plural")
            }
            row {
                rowHeader("indicative present")
                data(Tense.PRESENT, Person.FIRST, Num.SG, supporting = setOf(Mood.INDICATIVE))
                data(Tense.PRESENT, Person.SECOND, Num.SG, supporting = setOf(Mood.INDICATIVE))
                data(Tense.PRESENT, Person.THIRD, Num.SG, supporting = setOf(Mood.INDICATIVE))
                data(Tense.PRESENT, Num.PL, supporting = setOf(Mood.INDICATIVE))
            }
            row {
                rowHeader("indicative past")
                data(Tense.PAST, Person.FIRST, Num.SG, supporting = setOf(Mood.INDICATIVE))
                data(Tense.PAST, Person.SECOND, Num.SG, supporting = setOf(Mood.INDICATIVE))
                data(Tense.PAST, Person.THIRD, Num.SG, supporting = setOf(Mood.INDICATIVE))
                data(Tense.PAST, Num.PL, supporting = setOf(Mood.INDICATIVE))
            }
            row {
                rowHeader("conditional")
                data(Mood.CONDITIONAL, Tense.PAST, Person.FIRST, Num.SG)
                data(Mood.CONDITIONAL, Tense.PAST, Person.SECOND, Num.SG)
                data(Mood.CONDITIONAL, Tense.PAST, Person.THIRD, Num.SG)
                data(Mood.CONDITIONAL, Tense.PAST, Num.PL)
            }
            row {
                rowHeader("future")
                data(Tense.FUTURE, Person.FIRST, Num.SG, supporting = setOf(Mood.INDICATIVE))
                data(Tense.FUTURE, Person.SECOND, Num.SG, supporting = setOf(Mood.INDICATIVE))
                data(Tense.FUTURE, Person.THIRD, Num.SG, supporting = setOf(Mood.INDICATIVE))
                data(Tense.FUTURE, Num.PL, supporting = setOf(Mood.INDICATIVE))
            }
            row {
                rowHeader("imperative")
                data(Mood.IMPERATIVE, supporting = setOf(Num.SG))
                data(Mood.IMPERATIVE, Num.PL)
                empty(colspan = 2)
            }
            row {
                empty()
                colHeader("1st person")
                colHeader("2nd person")
                colHeader("3rd person")
                empty()
            }
            row {
                rowHeader("present perfect")
                data(Tense.PRESENT, Person.FIRST)
                data(Tense.PRESENT, Person.SECOND)
                data(Tense.PRESENT, Person.THIRD)
                empty()
            }
            row {
                rowHeader("conditional perfect")
                data(Mood.CONDITIONAL, Person.FIRST)
                data(Mood.CONDITIONAL, Person.SECOND)
                data(Mood.CONDITIONAL, Person.THIRD)
                empty()
            }
            row {
                rowHeader("future perfect")
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
        view("short", "Number and diminutive") {
            row {
                rowHeader("singular")
                data(Num.SG)
            }
            row {
                rowHeader("plural")
                data(Num.PL)
            }
            row {
                rowHeader("diminutive singular")
                data(Num.SG, VerbForm.DIMINUTIVE)
            }
            row {
                rowHeader("diminutive plural")
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
                colHeader("Category")
                colHeader("Form")
            }
            row {
                rowHeader("Positive (Base)")
                data(Degree.POSITIVE, Mood.PREDICATIVE)
                rowHeader("Inflected (+e)")
                data(Degree.POSITIVE, Definiteness.DEFINITE)
            }
            row {
                rowHeader("Comparative")
                data(Degree.COMPARATIVE)
                rowHeader("Superlative")
                data(Degree.SUPERLATIVE)
            }
            row {
                rowHeader("Partitive")
                data(Mood.PARTITIVE, Degree.POSITIVE)
                empty(colspan = 2)
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

    /** All Dutch schemes for easy lookup. */
    val ALL: List<ConjugationScheme> = listOf(NL_VERB, NL_NOUN, NL_ADJECTIVE)

    override fun schemesFor(pos: DictionaryPos, forms: List<SchemeInputForm>): List<ConjugationScheme> =
        ALL.filter { it.pos == pos }
}
