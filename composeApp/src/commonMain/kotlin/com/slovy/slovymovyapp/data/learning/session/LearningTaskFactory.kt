package com.slovy.slovymovyapp.data.learning.session

import com.slovy.slovymovyapp.analytics.PerformanceMonitoring
import com.slovy.slovymovyapp.analytics.putAttributes
import com.slovy.slovymovyapp.analytics.use
import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.learning.CardFamily
import com.slovy.slovymovyapp.data.learning.CardKind
import com.slovy.slovymovyapp.data.learning.CardVariant
import com.slovy.slovymovyapp.data.remote.LanguageCardExample
import com.slovy.slovymovyapp.data.remote.LanguageCardResponseSense
import com.slovy.slovymovyapp.data.util.HtmlTagParser
import kotlin.time.Duration

fun buildTaskVariants(
    family: CardFamily,
    sense: LanguageCardResponseSense,
    translationTargets: List<Language>,
): List<CardVariant> {
    // Only the user's configured translation languages may cue a card; languages that merely
    // happen to exist in cached sense data must not be tested.
    val targets = translationTargets.distinctBy { it.code }

    return when (family) {
        CardFamily.RECOGNIZE_SENSE -> buildList {
            add(CardVariant(CardKind.WORD_TO_SOURCE_DEFINITION, targetLang = null))
            targets
                .filter { sense.satisfiesTranslationData(CardKind.WORD_TO_TRANSLATION, it) }
                .forEach { add(CardVariant(CardKind.WORD_TO_TRANSLATION, targetLang = it.code)) }
        }

        CardFamily.PRODUCE_WORD -> buildList {
            if (!sense.senseDefinition.hasTaggedWord()) {
            add(CardVariant(CardKind.SOURCE_DEFINITION_TO_WORD, targetLang = null))
            }
            targets
                .filter { sense.satisfiesTranslationData(CardKind.TRANSLATION_TO_WORD, it) }
                .forEach { add(CardVariant(CardKind.TRANSLATION_TO_WORD, targetLang = it.code)) }
        }

        CardFamily.PRODUCE_WORD_IN_CONTEXT -> buildList {
            if (sense.examples.any { it.hasCloze() }) {
                targets
                    .filter { sense.satisfiesTranslationData(CardKind.CLOZE_SOURCE, it) }
                    .forEach { add(CardVariant(CardKind.CLOZE_SOURCE, targetLang = it.code)) }
            }
            targets
                .filter { target -> sense.examples.any { it.hasTranslatedCloze(target) } }
                .forEach { add(CardVariant(CardKind.CLOZE_TRANSLATION, targetLang = it.code)) }
        }

        CardFamily.RECOGNIZE_VOICE -> buildList {
            targets
                .filter { sense.satisfiesTranslationData(CardKind.LISTENING_TRANSLATION, it) }
                .forEach { add(CardVariant(CardKind.LISTENING_TRANSLATION, targetLang = it.code)) }
        }
    }
}

// The data a variant needs before it is playable in a given target language. This is the single
// source of truth shared by variant building (here), fetch-result readiness (SessionCard.loadState),
// and the bilingual back renderer (bilingualBack in StudySessionMapper) so a card can never be
// selected in a shape its renderer rejects: WORD_TO_TRANSLATION renders translation words as the
// answer headline and the target-language definition as the body, TRANSLATION_TO_WORD cues from
// translation words, and the remaining bilingual kinds only need some translation cue for the
// card back.
internal fun LanguageCardResponseSense.satisfiesTranslationData(kind: CardKind, language: Language): Boolean =
    when (kind) {
        CardKind.WORD_TO_TRANSLATION -> hasTranslationWords(language) && hasTranslationDefinition(language)
        CardKind.TRANSLATION_TO_WORD -> hasTranslationWords(language)
        else -> hasTranslationWords(language) || hasTranslationDefinition(language)
    }

private fun LanguageCardResponseSense.hasTranslationWords(language: Language): Boolean =
    translations[language].orEmpty().any { it.targetLangWord.isNotBlank() }

private fun LanguageCardResponseSense.hasTranslationDefinition(language: Language): Boolean =
    !targetLangDefinitions[language].isNullOrBlank()

fun selectVariantsForReview(
    family: CardFamily,
    sense: LanguageCardResponseSense,
    translationTargets: List<Language>,
    cardStability: Duration,
    lastVariant: CardVariant?,
): List<CardVariant> = PerformanceMonitoring.startTrace("learning_select_variants_for_review").use { trace ->
    trace.putAttributes(
        mapOf(
            "family" to family.name.lowercase(),
            "last_variant_present" to (lastVariant != null),
        ),
    )
    trace.putMetric("translation_targets", translationTargets.distinctBy { it.code }.size.toLong())
    trace.putMetric("examples", sense.examples.size.toLong())
    trace.putMetric("stability_ms", cardStability.inWholeMilliseconds)

    val all = buildTaskVariants(family, sense, translationTargets)
    val gated = all.filter { it.kind.minStability <= cardStability }
    val fallbackToAll = gated.isEmpty() && all.isNotEmpty()
    val pool = if (fallbackToAll) all else gated

    trace.putMetric("all_variants", all.size.toLong())
    trace.putMetric("gated_variants", gated.size.toLong())
    trace.putMetric("selected_variants", pool.size.toLong())
    trace.putAttribute("fallback_to_all", fallbackToAll.toString())

    pool.sortedWith(
        compareBy<CardVariant> { variant ->
            if (lastVariant != null &&
                lastVariant.kind == variant.kind &&
                lastVariant.targetLang == variant.targetLang
            ) 1 else 0
        }
            .thenBy { it.kind.priority }
            .thenBy { it.targetLang ?: "" }
    )
}

private fun LanguageCardExample.hasCloze(): Boolean =
    text.hasTaggedWord()

private fun LanguageCardExample.hasTranslatedCloze(language: Language): Boolean =
    targetLangTranslations[language]?.hasTaggedWord() == true

private fun String.hasTaggedWord(): Boolean =
    HtmlTagParser.parseTextSegments(this).any { it.isTagged && it.text.isNotBlank() }
