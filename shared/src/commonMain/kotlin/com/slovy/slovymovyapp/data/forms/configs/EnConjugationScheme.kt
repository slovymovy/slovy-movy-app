package com.slovy.slovymovyapp.data.forms.configs

import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.dictionary.DictionaryPos
import com.slovy.slovymovyapp.data.dictionary.FormSource
import com.slovy.slovymovyapp.data.forms.*

object EnConjugationScheme : ConjugationSchemeProvider {

    private fun englishTagResolver(pos: DictionaryPos): SchemeTagResolver = object : SchemeTagResolver {
        override fun preprocessForms(forms: List<SchemeInputForm>, lemma: String?): List<SchemeInputForm> {
            fun SchemeInputForm.hasTag(tag: String): Boolean = tags.any { it.equals(tag, ignoreCase = true) }

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

                val hasFirstPersonPresent = forms.any {
                    it.hasTag("first-person") && it.hasTag("present") && it.hasTag("singular")
                }
                if (!hasFirstPersonPresent) {
                    val presentSingularNonThird = forms.firstOrNull { form ->
                        form.hasTag("present") && form.hasTag("singular") && !form.hasTag("third-person")
                    }
                    val inferredForm = presentSingularNonThird?.form ?: baseWord
                    result += SchemeInputForm(
                        tags = listOf("first-person", "present", "singular"),
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
                rowHeader("infinitive")
                data(VerbForm.INFINITIVE)
            }
            row {
                rowHeader("present (I)")
                data(Person.FIRST, Tense.PRESENT, Num.SG)
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
                colHeader("comparative")
                colHeader("superlative")
            }
            row {
                data(Degree.COMPARATIVE)
                data(Degree.SUPERLATIVE)
            }
        }
    }

    /** All English schemes for easy lookup. */
    val ALL: List<ConjugationScheme> = listOf(EN_VERB, EN_NOUN, EN_ADJECTIVE)

    override fun schemeFor(pos: DictionaryPos, forms: List<SchemeInputForm>): ConjugationScheme? =
        ALL.firstOrNull { it.pos == pos }
}
