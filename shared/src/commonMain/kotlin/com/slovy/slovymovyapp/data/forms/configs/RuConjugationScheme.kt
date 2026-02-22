package com.slovy.slovymovyapp.data.forms.configs

import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.dictionary.DictionaryPos
import com.slovy.slovymovyapp.data.forms.*
import com.slovy.slovymovyapp.data.forms.configs.RuConjugationScheme.RU_VERB_IMPERFECTIVE
import com.slovy.slovymovyapp.data.forms.configs.RuConjugationScheme.RU_VERB_PERFECTIVE
import com.slovy.slovymovyapp.util.stripAccents

object RuConjugationScheme : ConjugationSchemeProvider {

    private fun russianCanonicalTagsForPos(pos: DictionaryPos): List<FormTag> = when (pos) {
        DictionaryPos.NOUN -> listOf(Case.NOMINATIVE, Num.SG)
        DictionaryPos.VERB -> listOf(VerbForm.INFINITIVE)
        DictionaryPos.ADJECTIVE -> listOf(Case.NOMINATIVE, Gender.MASC, Num.SG)
        else -> emptyList()
    }

    private fun russianTagResolver(pos: DictionaryPos): SchemeTagResolver = object : SchemeTagResolver {
        override fun resolve(dbTags: Iterable<String>): List<FormTag> {
            return dbTags
                .asSequence()
                .flatMap { dbTag ->
                    if (dbTag == "canonical") {
                        russianCanonicalTagsForPos(pos).asSequence()
                    } else {
                        TagMapping.resolve(dbTag)?.let { sequenceOf(it) } ?: emptySequence()
                    }
                }
                .distinctBy { it.key to it.value }
                .toList()
        }

        override fun preprocessForms(forms: List<SchemeInputForm>, lemma: String?): List<SchemeInputForm> {
            val cleanedForms = forms.filterNot { it.form.trim() == "*" }
            if (pos != DictionaryPos.VERB) return cleanedForms

            val normalizedLemma = lemma
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let(::stripAccents)
                ?: return cleanedForms

            val hasLemmaAsForm = cleanedForms.any { form ->
                stripAccents(form.form.trim()) == normalizedLemma
            }
            if (hasLemmaAsForm) return cleanedForms

            return cleanedForms + SchemeInputForm(
                tags = listOf("infinitive"),
                form = lemma.trim()
            )
        }

        override fun selectCandidate(candidates: List<SchemeCellCandidate>): SchemeCellCandidate? {
            return candidates.minWithOrNull(
                compareBy<SchemeCellCandidate> { it.missingRequiredTags }
                    .thenBy { -it.matchedPreferredTags }
                    .thenBy { it.extraKnownTags }
                    .thenBy { it.form }
            )
        }

    }

    /**
     * Russian noun declension.
     *
     * Six cases × two numbers, matching the canonical en.wiktionary table
     * (e.g. книга).
     */
    val RU_NOUN: ConjugationScheme = conjugationScheme(
        "ru_noun",
        Language.RUSSIAN,
        DictionaryPos.NOUN,
        tagResolver = russianTagResolver(DictionaryPos.NOUN)
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
                rowHeader("prepositional")
                data(Case.PREPOSITIONAL, Num.SG)
                data(Case.PREPOSITIONAL, Num.PL)
            }
        }
    }

    /**
     * Russian imperfective verb conjugation.
     *
     * Sections: present tense, future analytic (быть + inf), past tense,
     * imperative, and participles — matching en.wiktionary (e.g. читать).
     */
    val RU_VERB_IMPERFECTIVE: ConjugationScheme =
        conjugationScheme(
            "ru_verb_imperfective",
            Language.RUSSIAN,
            DictionaryPos.VERB,
            tagResolver = russianTagResolver(DictionaryPos.VERB)
        ) {
            view("full", "Imperfective conjugation") {
                row {
                    rowHeader("infinitive")
                    data(VerbForm.INFINITIVE, supporting = setOf(Aspect.IMPERFECTIVE), colspan = 2)
                }

                // Participles header block
                row {
                    empty()
                    colHeader("present tense")
                    colHeader("past tense")
                }
                row {
                    rowHeader("active participle")
                    data(VerbForm.PARTICIPLE, Voice.ACTIVE, Tense.PRESENT)
                    data(VerbForm.PARTICIPLE, Voice.ACTIVE, Tense.PAST)
                }
                row {
                    rowHeader("passive participle")
                    data(VerbForm.PARTICIPLE, Voice.PASSIVE, Tense.PRESENT)
                    data(VerbForm.PARTICIPLE, Voice.PASSIVE, Tense.PAST)
                }
                row {
                    rowHeader("adverbial participle")
                    data(VerbForm.ADVERBIAL, Tense.PRESENT)
                    data(VerbForm.ADVERBIAL, Tense.PAST)
                }

                // Finite forms header
                row {
                    empty()
                    colHeader("present tense")
                    colHeader("future tense")
                }
                row {
                    rowHeader("1st singular (я)")
                    data(Tense.PRESENT, Person.FIRST, Num.SG, supporting = setOf(VerbForm.FINITE))
                    data(Tense.FUTURE, Person.FIRST, Num.SG, supporting = setOf(VerbForm.FINITE))
                }
                row {
                    rowHeader("2nd singular (ты)")
                    data(Tense.PRESENT, Person.SECOND, Num.SG, supporting = setOf(VerbForm.FINITE))
                    data(Tense.FUTURE, Person.SECOND, Num.SG, supporting = setOf(VerbForm.FINITE))
                }
                row {
                    rowHeader("3rd singular (он/она/оно)")
                    data(Tense.PRESENT, Person.THIRD, Num.SG, supporting = setOf(VerbForm.FINITE))
                    data(Tense.FUTURE, Person.THIRD, Num.SG, supporting = setOf(VerbForm.FINITE))
                }
                row {
                    rowHeader("1st plural (мы)")
                    data(Tense.PRESENT, Person.FIRST, Num.PL, supporting = setOf(VerbForm.FINITE))
                    data(Tense.FUTURE, Person.FIRST, Num.PL, supporting = setOf(VerbForm.FINITE))
                }
                row {
                    rowHeader("2nd plural (вы)")
                    data(Tense.PRESENT, Person.SECOND, Num.PL, supporting = setOf(VerbForm.FINITE))
                    data(Tense.FUTURE, Person.SECOND, Num.PL, supporting = setOf(VerbForm.FINITE))
                }
                row {
                    rowHeader("3rd plural (они)")
                    data(Tense.PRESENT, Person.THIRD, Num.PL, supporting = setOf(VerbForm.FINITE))
                    data(Tense.FUTURE, Person.THIRD, Num.PL, supporting = setOf(VerbForm.FINITE))
                }

                // Imperative
                row {
                    rowHeader("imperative")
                    colHeader("singular")
                    colHeader("plural")
                }
                row {
                    rowHeader("2nd (ты / вы)")
                    data(Mood.IMPERATIVE, Num.SG, supporting = setOf(VerbForm.FINITE, Person.SECOND))
                    data(Mood.IMPERATIVE, Num.PL, supporting = setOf(VerbForm.FINITE, Person.SECOND))
                }

                // Past tense — gendered
                row {
                    rowHeader("past tense")
                    colHeader("singular")
                    colHeader("plural (мы/вы/они)")
                }
                row {
                    rowHeader("masculine (я/ты/он)")
                    data(Tense.PAST, Gender.MASC, supporting = setOf(VerbForm.FINITE, Num.SG))
                    data(Tense.PAST, Num.PL, rowspan = 3, supporting = setOf(VerbForm.FINITE))
                }
                row {
                    rowHeader("feminine (я/ты/она)")
                    data(Tense.PAST, Gender.FEM, supporting = setOf(VerbForm.FINITE, Num.SG))
                }
                row {
                    rowHeader("neuter (оно)")
                    data(Tense.PAST, Gender.NEUT, supporting = setOf(VerbForm.FINITE, Num.SG))
                }
            }
        }

    /**
     * Russian perfective verb conjugation.
     *
     * Perfective verbs have no present tense; what looks like "present" forms
     * are future tense. Past tense and imperative follow the same pattern.
     */
    val RU_VERB_PERFECTIVE: ConjugationScheme =
        conjugationScheme(
            "ru_verb_perfective",
            Language.RUSSIAN,
            DictionaryPos.VERB,
            tagResolver = russianTagResolver(DictionaryPos.VERB)
        ) {
            view("full", "Perfective conjugation") {
                row {
                    rowHeader("infinitive")
                    data(VerbForm.INFINITIVE, Aspect.PERFECTIVE, colspan = 2)
                }

                // Participles — perfective has no present active/passive participles
                row {
                    empty()
                    colHeader("active")
                    colHeader("passive")
                }
                row {
                    rowHeader("past participle")
                    data(VerbForm.PARTICIPLE, Voice.ACTIVE, Tense.PAST)
                    data(VerbForm.PARTICIPLE, Voice.PASSIVE, Tense.PAST)
                }
                row {
                    rowHeader("adverbial participle")
                    data(VerbForm.ADVERBIAL, Tense.PAST, colspan = 2)
                }

                // Future tense (perfective present = simple future)
                row {
                    rowHeader("future tense", colspan = 2)
                }
                row {
                    rowHeader("1st singular (я)")
                    data(Tense.FUTURE, Person.FIRST, Num.SG, supporting = setOf(VerbForm.FINITE))
                }
                row {
                    rowHeader("2nd singular (ты)")
                    data(Tense.FUTURE, Person.SECOND, Num.SG, supporting = setOf(VerbForm.FINITE))
                }
                row {
                    rowHeader("3rd singular (он/она/оно)")
                    data(Tense.FUTURE, Person.THIRD, Num.SG, supporting = setOf(VerbForm.FINITE))
                }
                row {
                    rowHeader("1st plural (мы)")
                    data(Tense.FUTURE, Person.FIRST, Num.PL, supporting = setOf(VerbForm.FINITE))
                }
                row {
                    rowHeader("2nd plural (вы)")
                    data(Tense.FUTURE, Person.SECOND, Num.PL, supporting = setOf(VerbForm.FINITE))
                }
                row {
                    rowHeader("3rd plural (они)")
                    data(Tense.FUTURE, Person.THIRD, Num.PL, supporting = setOf(VerbForm.FINITE))
                }

                // Imperative
                row {
                    rowHeader("imperative")
                    colHeader("singular")
                    colHeader("plural")
                }
                row {
                    rowHeader("2nd (ты / вы)")
                    data(Mood.IMPERATIVE, Num.SG, supporting = setOf(VerbForm.FINITE))
                    data(Mood.IMPERATIVE, Num.PL, supporting = setOf(VerbForm.FINITE))
                }

                // Past tense
                row {
                    rowHeader("past tense")
                    colHeader("singular")
                    colHeader("plural")
                }
                row {
                    rowHeader("masculine (я/ты/он)")
                    data(Tense.PAST, Gender.MASC, supporting = setOf(VerbForm.FINITE, Num.SG))
                    data(Tense.PAST, Num.PL, rowspan = 3, supporting = setOf(VerbForm.FINITE))
                }
                row {
                    rowHeader("feminine (я/ты/она)")
                    data(Tense.PAST, Gender.FEM, supporting = setOf(VerbForm.FINITE, Num.SG))
                }
                row {
                    rowHeader("neuter (оно)")
                    data(Tense.PAST, Gender.NEUT, supporting = setOf(VerbForm.FINITE, Num.SG))
                }
            }
        }

    /**
     * Russian adjective declension.
     *
     * Four gender/number columns × six cases + short forms.
     * Accusative is split into animate and inanimate sub-rows.
     * Matches the en.wiktionary table for красивый.
     */
    val RU_ADJECTIVE: ConjugationScheme = conjugationScheme(
        "ru_adjective",
        Language.RUSSIAN,
        DictionaryPos.ADJECTIVE,
        tagResolver = russianTagResolver(DictionaryPos.ADJECTIVE)
    ) {
        view("full", "Declension") {
            row {
                empty(colspan = 2)
                colHeader("masculine")
                colHeader("neuter")
                colHeader("feminine")
                colHeader("plural")
            }
            row {
                rowHeader("nominative", colspan = 2)
                data(Case.NOMINATIVE, Gender.MASC)
                data(Case.NOMINATIVE, Gender.NEUT)
                data(Case.NOMINATIVE, Gender.FEM)
                data(Case.NOMINATIVE, Num.PL)
            }
            row {
                rowHeader("genitive", colspan = 2)
                data(Case.GENITIVE, Gender.MASC)
                data(Case.GENITIVE, Gender.NEUT, rowspan = 2)
                data(Case.GENITIVE, Gender.FEM)
                data(Case.GENITIVE, Num.PL)
            }
            row {
                rowHeader("dative", colspan = 2)
                data(Case.DATIVE, Gender.MASC)
                // neuter cell already covered by rowspan above
                data(Case.DATIVE, Gender.FEM)
                data(Case.DATIVE, Num.PL)
            }
            // Accusative: two sub-rows for animate / inanimate
            row {
                rowHeader("accusative", rowspan = 2)
                rowHeader("animate")
                data(Case.ACCUSATIVE, Animacy.ANIM, Gender.MASC)
                data(Case.ACCUSATIVE, Gender.NEUT, rowspan = 2)
                data(Case.ACCUSATIVE, Animacy.ANIM, Gender.FEM, rowspan = 2)
                data(Case.ACCUSATIVE, Animacy.ANIM, Num.PL)
            }
            row {
                rowHeader("inanimate")
                data(Case.ACCUSATIVE, Animacy.INANIM, Gender.MASC)
                data(Case.ACCUSATIVE, Animacy.INANIM, Num.PL)
            }
            row {
                rowHeader("instrumental", colspan = 2)
                data(Case.INSTRUMENTAL, Gender.MASC)
                data(Case.INSTRUMENTAL, Gender.NEUT, rowspan = 2)
                data(Case.INSTRUMENTAL, Gender.FEM)
                data(Case.INSTRUMENTAL, Num.PL)
            }
            row {
                rowHeader("prepositional", colspan = 2)
                data(Case.PREPOSITIONAL, Gender.MASC)
                // neuter covered by rowspan
                data(Case.PREPOSITIONAL, Gender.FEM)
                data(Case.PREPOSITIONAL, Num.PL)
            }
            row {
                rowHeader("short form", colspan = 2)
                data(VerbForm.SHORT, Gender.MASC)
                data(VerbForm.SHORT, Gender.NEUT)
                data(VerbForm.SHORT, Gender.FEM)
                data(VerbForm.SHORT, Num.PL)
            }
        }
    }

    /** All Russian schemes for easy lookup. */
    val ALL: List<ConjugationScheme> = listOf(RU_NOUN, RU_VERB_IMPERFECTIVE, RU_VERB_PERFECTIVE, RU_ADJECTIVE)

    /**
     * Selects the applicable schemes for [pos] given the word's raw input [forms].
     *
     * For verbs, the aspect tags `"perfective"` and `"imperfective"` are used to narrow
     * the selection to the matching template:
     * - Only `"perfective"` present → [RU_VERB_PERFECTIVE]
     * - Only `"imperfective"` present → [RU_VERB_IMPERFECTIVE]
     * - Both or neither → [RU_VERB_IMPERFECTIVE] by predefined default order
     */
    override fun schemeFor(pos: DictionaryPos, forms: List<SchemeInputForm>): ConjugationScheme? {
        val byPos = ALL.firstOrNull { it.pos == pos }
        if (pos != DictionaryPos.VERB) return byPos
        val hasPerfective = forms.any { "perfective" in it.tags }
        val hasImperfective = forms.any { "imperfective" in it.tags }
        return when {
            hasPerfective && !hasImperfective -> RU_VERB_PERFECTIVE
            hasImperfective && !hasPerfective -> RU_VERB_IMPERFECTIVE
            else -> RU_VERB_IMPERFECTIVE
        }
    }
}
