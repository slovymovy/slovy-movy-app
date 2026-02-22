package com.slovy.slovymovyapp.data.forms.configs

import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.dictionary.DictionaryPos
import com.slovy.slovymovyapp.data.forms.ConjugationScheme
import com.slovy.slovymovyapp.data.forms.ConjugationSchemeProvider
import com.slovy.slovymovyapp.data.forms.SchemeInputForm

object SchemeRegistry {
    private val selectors: Map<Language, ConjugationSchemeProvider> = mapOf(
        Language.ENGLISH to EnConjugationScheme,
        Language.RUSSIAN to RuConjugationScheme,
        Language.DUTCH to NlConjugationScheme,
        Language.POLISH to PlConjugationScheme,
    )

    /**
     * Returns the conjugation schemes to use for [language] + [pos], optionally filtered
     * by the word's raw input [forms] (DB tag strings before preprocessing).
     *
     * When [forms] is empty the language selector still performs POS filtering but cannot
     * apply form-based narrowing (e.g. aspect detection for Russian verbs).
     */
    fun findSchemes(
        language: Language,
        pos: DictionaryPos,
        forms: List<SchemeInputForm> = emptyList(),
    ): List<ConjugationScheme> = selectors[language]?.schemesFor(pos, forms) ?: emptyList()
}
