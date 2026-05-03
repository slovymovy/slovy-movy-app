package com.slovy.slovymovyapp.data.learning.session

import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.learning.CardFamily
import com.slovy.slovymovyapp.data.learning.CardKind
import com.slovy.slovymovyapp.data.learning.CardVariant
import com.slovy.slovymovyapp.data.remote.LanguageCardExample
import com.slovy.slovymovyapp.data.remote.LanguageCardResponseSense
import com.slovy.slovymovyapp.data.util.HtmlTagParser

fun buildTaskVariants(
    family: CardFamily,
    sense: LanguageCardResponseSense,
    translationTargets: List<Language>,
): List<CardVariant> {
    val targets = translationTargets
        .plus(sense.targetLangDefinitions.keys)
        .plus(sense.translations.keys)
        .plus(sense.examples.flatMap { it.targetLangTranslations.keys })
        .distinctBy { it.code }

    return when (family) {
        CardFamily.RECOGNIZE_SENSE -> buildList {
            add(CardVariant(CardKind.WORD_TO_SOURCE_DEFINITION, targetLang = null))
            targets
                .filter { sense.hasTranslationCue(it) }
                .forEach { add(CardVariant(CardKind.WORD_TO_TRANSLATION, targetLang = it.code)) }
        }

        CardFamily.PRODUCE_WORD -> buildList {
            add(CardVariant(CardKind.SOURCE_DEFINITION_TO_WORD, targetLang = null))
            targets
                .filter { sense.hasTranslationCue(it) }
                .forEach { add(CardVariant(CardKind.TRANSLATION_TO_WORD, targetLang = it.code)) }
        }

        CardFamily.PRODUCE_WORD_IN_CONTEXT -> buildList {
            if (sense.examples.any { it.hasCloze() }) {
                add(CardVariant(CardKind.CLOZE_SOURCE, targetLang = null))
            }
            targets
                .filter { target -> sense.examples.any { it.hasTranslatedCloze(target) } }
                .forEach { add(CardVariant(CardKind.CLOZE_TRANSLATION, targetLang = it.code)) }
        }
    }
}

private fun LanguageCardResponseSense.hasTranslationCue(language: Language): Boolean =
    targetLangDefinitions[language] != null || translations[language].orEmpty().isNotEmpty()

private fun LanguageCardExample.hasCloze(): Boolean =
    text.hasTaggedWord()

private fun LanguageCardExample.hasTranslatedCloze(language: Language): Boolean =
    targetLangTranslations[language]?.hasTaggedWord() == true

private fun String.hasTaggedWord(): Boolean =
    HtmlTagParser.parseTextSegments(this).any { it.isTagged && it.text.isNotBlank() }
