package com.slovy.slovymovyapp.data.forms.configs

import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.dictionary.DictionaryPos
import com.slovy.slovymovyapp.data.dictionary.FormSource
import com.slovy.slovymovyapp.data.forms.*

object EnConjugationScheme : ConjugationSchemeProvider {

    private fun englishTagResolver(pos: DictionaryPos): SchemeTagResolver = object : SchemeTagResolver {
        override fun preprocessForms(forms: List<SchemeInputForm>, lemma: String?): List<SchemeInputForm> {
            fun SchemeInputForm.hasTag(tag: String): Boolean = tags.any { it.equals(tag, ignoreCase = true) }

            if (pos == DictionaryPos.PRONOUN) {
                val baseWord = lemma?.trim().orEmpty()
                if (baseWord.isEmpty()) return forms
                if (forms.isEmpty()) return forms
                val hasSubjective = forms.any { it.hasTag("subjective") }
                if (hasSubjective) return forms
                return forms + SchemeInputForm(tags = listOf("subjective"), form = baseWord, FormSource.HEURISTIC)
            }

            if (pos == DictionaryPos.VERB) {
                val baseWord = lemma?.trim().orEmpty()
                if (baseWord.isEmpty()) return forms
                if (forms.isEmpty()) return forms

                val shouldSkipInference = forms.any { form ->
                    form.hasTag("form-of") || form.hasTag("gerund")
                }
                if (shouldSkipInference) return forms

                val result = forms.toMutableList()

                val hasInfinitive = forms.any { it.hasTag("infinitive") }
                if (!hasInfinitive) {
                    result += SchemeInputForm(tags = listOf("infinitive"), form = baseWord, FormSource.HEURISTIC)
                }

                val presentSingularNonThird = forms.firstOrNull { form ->
                    form.hasTag("present") && form.hasTag("singular") && !form.hasTag("third-person")
                }

                val hasFirstPersonPresent = forms.any {
                    it.hasTag("first-person") && it.hasTag("present") && it.hasTag("singular")
                }
                if (!hasFirstPersonPresent) {
                    val inferredForm = presentSingularNonThird?.form ?: baseWord
                    result += SchemeInputForm(
                        tags = listOf("first-person", "present", "singular"),
                        form = inferredForm,
                        FormSource.HEURISTIC
                    )
                }

                val hasSecondPersonPresent = forms.any {
                    it.hasTag("second-person") && it.hasTag("present") && it.hasTag("singular")
                }
                if (!hasSecondPersonPresent) {
                    val inferredForm = presentSingularNonThird?.form ?: baseWord
                    result += SchemeInputForm(
                        tags = listOf("second-person", "present", "singular"),
                        form = inferredForm,
                        FormSource.HEURISTIC
                    )
                }

                return result
            }

            if (pos != DictionaryPos.NOUN) return forms

            val baseWord = lemma?.trim().orEmpty()
            if (baseWord.isEmpty()) return forms

            val hasSingular = forms.any { it.hasTag("singular") }
            val hasPlural = forms.any { it.hasTag("plural") }
            if (hasSingular == hasPlural) return forms

            val expectedSingular = if (baseWord.endsWith("s") && baseWord.length > 1) {
                baseWord.dropLast(1)
            } else {
                baseWord
            }
            val expectedPlural = if (baseWord.endsWith("s") && baseWord.length > 1) {
                baseWord
            } else {
                "${baseWord}s"
            }

            if (hasPlural) {
                val hasRegularPlural = forms.any { form ->
                    form.hasTag("plural") && form.form.equals(expectedPlural, ignoreCase = true)
                }
                if (!hasRegularPlural) return forms

                val singularAlreadyPresent = forms.any { form ->
                    form.hasTag("singular") && form.form.equals(expectedSingular, ignoreCase = true)
                }
                if (singularAlreadyPresent) return forms

                return forms + SchemeInputForm(tags = listOf("singular"), form = expectedSingular, FormSource.HEURISTIC)
            }

            val hasBaseSingular = forms.any { form ->
                form.hasTag("singular") && form.form.equals(expectedSingular, ignoreCase = true)
            }
            if (!hasBaseSingular) return forms

            val pluralAlreadyPresent = forms.any { form ->
                form.hasTag("plural") && form.form.equals(expectedPlural, ignoreCase = true)
            }
            if (pluralAlreadyPresent) return forms

            return forms + SchemeInputForm(tags = listOf("plural"), form = expectedPlural, FormSource.HEURISTIC)
        }

        override fun selectCandidate(candidates: List<SchemeCellCandidate>): SchemeCellCandidate? {
            if (pos != DictionaryPos.PRONOUN) return candidates.firstOrNull()
            // Pronouns have capitalized duplicates (Him/him) and overlapping forms (her = objective + possessive).
            // Prefer lowercase, then most preferred-tag matches, then fewest extra known tags.
            val prioritized = candidates.filter { it.form.isNotEmpty() && it.form[0].isLowerCase() }
                .ifEmpty { candidates }
            return prioritized
                .sortedWith(
                    compareByDescending<SchemeCellCandidate> { it.matchedPreferredTags }
                        .thenBy { it.extraKnownTags }
                )
                .firstOrNull()
        }
    }

    /**
     * English verb paradigm.
     *
     * English verbs have very little inflection; this short view covers the
     * five principal parts shown on en.wiktionary.org.
     */
    val EN_VERB: ConjugationScheme = conjugationScheme(
        "en_verb",
        Language.ENGLISH,
        DictionaryPos.VERB,
        tagResolver = englishTagResolver(DictionaryPos.VERB)
    ) {
        view("short", "Principal parts") {
            row {
                colHeader("Category")
                colHeader("Form")
            }

            row {
                rowHeader("infinitive")
                data(VerbForm.INFINITIVE)
            }
            row {
                rowHeader("present (I)")
                data(Person.FIRST, Tense.PRESENT, Num.SG)
            }
            row {
                rowHeader("present (you)")
                data(Person.SECOND, Tense.PRESENT, Num.SG)
            }
            row {
                rowHeader("present (he/she/it)")
                data(Person.THIRD, Tense.PRESENT, Num.SG)
            }
            row {
                rowHeader("present participle")
                data(VerbForm.PARTICIPLE, Tense.PRESENT)
            }
            row {
                rowHeader("past simple")
                data(Tense.PAST, supporting = setOf(VerbForm.FINITE))
            }
            row {
                rowHeader("past participle")
                data(VerbForm.PARTICIPLE, Tense.PAST)
            }
        }
    }

    /**
     * English noun paradigm.
     *
     * English nouns only distinguish singular and plural in most cases.
     */
    val EN_NOUN: ConjugationScheme = conjugationScheme(
        "en_noun",
        Language.ENGLISH,
        DictionaryPos.NOUN,
        tagResolver = englishTagResolver(DictionaryPos.NOUN)
    ) {
        view("short", "Number") {
            row {
                colHeader("Category")
                colHeader("Form")
            }

            row {
                rowHeader("singular")
                data(Num.SG)
            }
            row {
                rowHeader("plural")
                data(Num.PL)
            }
        }
    }

    /**
     * English adjective paradigm.
     *
     * Gradable adjectives form comparative and superlative; this view shows
     * all three degrees in one row so the comparison is obvious.
     */
    val EN_ADJECTIVE: ConjugationScheme = conjugationScheme(
        "en_adjective",
        Language.ENGLISH,
        DictionaryPos.ADJECTIVE,
        tagResolver = englishTagResolver(DictionaryPos.ADJECTIVE)
    ) {
        view("short", "Degrees of comparison") {
            row {
                colHeader("Category")
                colHeader("Form")
            }

            row {
                rowHeader("comparative")
                data(Degree.COMPARATIVE)
            }
            row {
                rowHeader("superlative")
                data(Degree.SUPERLATIVE)
            }
        }
    }

    /**
     * English adverb paradigm.
     *
     * Gradable adverbs form comparative and superlative degrees.
     */
    val EN_ADVERB: ConjugationScheme = conjugationScheme(
        "en_adverb",
        Language.ENGLISH,
        DictionaryPos.ADVERB,
        tagResolver = englishTagResolver(DictionaryPos.ADVERB)
    ) {
        view("short", "Degrees of comparison") {
            row {
                colHeader("Category")
                colHeader("Form")
            }

            row {
                rowHeader("comparative")
                data(Degree.COMPARATIVE)
            }
            row {
                rowHeader("superlative")
                data(Degree.SUPERLATIVE)
            }
        }
    }

    /**
     * English pronoun paradigm.
     *
     * Personal pronouns inflect for case (subjective, objective, possessive, reflexive).
     * The subjective form is the lemma itself, injected by preprocessForms.
     * Two possessive rows distinguish the determiner ("my dog") from the independent pronoun ("mine").
     * Two reflexive rows show both singular and plural forms where both exist (e.g. yourself / yourselves).
     *
     * Known data gaps: "hers" and "theirs" lack the tags needed to appear in the possessive pronoun row.
     */
    val EN_PRONOUN: ConjugationScheme = conjugationScheme(
        "en_pronoun",
        Language.ENGLISH,
        DictionaryPos.PRONOUN,
        tagResolver = englishTagResolver(DictionaryPos.PRONOUN)
    ) {
        view("short", "Cases") {
            row {
                colHeader("Category")
                colHeader("Form")
            }

            row {
                rowHeader("subject")
                data(PronounForm.SUBJECTIVE)
            }
            row {
                rowHeader("object")
                data(PronounForm.OBJECTIVE)
            }
            row {
                rowHeader("possessive adjective")
                // supporting=OBJECTIVE makes "her" (oblique+possessive) preferred over "hers" (possessive only)
                data(PossessiveType.DEPENDENT, supporting = setOf(PronounForm.OBJECTIVE))
            }
            row {
                rowHeader("possessive pronoun")
                data(PossessiveType.INDEPENDENT)
            }
            row {
                rowHeader("reflexive")
                data(PronounForm.REFLEXIVE)
            }
        }
    }

    /** All English schemes for easy lookup. */
    val ALL: List<ConjugationScheme> = listOf(EN_VERB, EN_NOUN, EN_ADJECTIVE, EN_ADVERB, EN_PRONOUN)

    override fun schemeFor(pos: DictionaryPos, forms: List<SchemeInputForm>): ConjugationScheme? {
        if (pos == DictionaryPos.PRONOUN) {
            // Only show the paradigm table for head pronouns (i, he, she, we, you, they).
            // Possessive-only lemmas like "his" or "their" are forms of other pronouns, not paradigm heads.
            val hasFullParadigm = forms.any { form ->
                form.tags.any { it.equals("objective", ignoreCase = true)
                        || it.equals("oblique", ignoreCase = true)
                        || it.equals("reflexive", ignoreCase = true) }
            }
            return if (hasFullParadigm) EN_PRONOUN else null
        }
        return ALL.firstOrNull { it.pos == pos }
    }
}
