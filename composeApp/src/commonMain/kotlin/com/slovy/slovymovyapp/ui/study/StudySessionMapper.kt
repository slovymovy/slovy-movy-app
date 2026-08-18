package com.slovy.slovymovyapp.ui.study

import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.favorites.FavoritesRepository.Companion.normalizeLemma
import com.slovy.slovymovyapp.data.learning.CardKind
import com.slovy.slovymovyapp.data.learning.GradeOutcome
import com.slovy.slovymovyapp.data.learning.Rating
import com.slovy.slovymovyapp.data.learning.session.ExamplePair
import com.slovy.slovymovyapp.data.learning.session.SessionCard
import com.slovy.slovymovyapp.data.learning.session.SessionCardLoadState
import com.slovy.slovymovyapp.data.learning.session.satisfiesTranslationData
import com.slovy.slovymovyapp.data.remote.LanguageCardResponseSense
import com.slovy.slovymovyapp.data.util.parseClozeFromTaggedText
import com.slovy.slovymovyapp.i18n.UiText
import com.slovy.slovymovyapp.ui.word.joinLanguageLines
import com.slovy.slovymovyapp.ui.word.translationLines
import slovymovyapp.composeapp.generated.resources.*
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

fun SessionCard.toStudyCardUiState(favoriteLemmas: Set<String>): StudyCardUiState? {
    if (loadState() != SessionCardLoadState.READY) return null

    val cardData = wordResult.card ?: return null
    val sourceLanguage = Language.fromCodeOrNull(card.langCode) ?: return null
    val sense = cardData.entries
        .flatMap { it.senses }
        .firstOrNull { it.senseId == senseId }
        ?: return null
    val targetLanguage = variant.targetLang?.let(Language::fromCodeOrNull)
    val lemma = cardData.lemma

    val studiedSenses = cardData.entries
        .flatMap { it.senses }
        .filter { it.senseId in studiedSenseIds }
        .sortedByDescending { it.senseId == sense.senseId }

    return when (variant.kind) {
        CardKind.WORD_TO_SOURCE_DEFINITION -> {
            val senses = studiedSenses.toSourceSenseUiStates(
                lemma = lemma,
                targetLanguage = null,
                translationTargets = translationTargets,
                favoriteLemmas = favoriteLemmas,
            )
            StudyCardUiState.Recognition(
                id = card.id.toString(),
                chipLabel = UiText.Resource(
                    Res.string.study_chip_source_only,
                    listOf(sourceLanguage.studyCode()),
                ),
                promptWord = lemma,
                promptAudioText = lemma,
                mode = StudyRecognitionMode.MONOLINGUAL,
                senses = senses,
                activeSenseId = sense.senseId,
                back = sourceBack(
                    lemma = lemma,
                    sense = sense,
                    targetLanguage = null,
                    translationTargets = translationTargets,
                    favoriteLemmas = favoriteLemmas,
                    examples = sense.studyExamples(targetLanguage = null),
                ),
            )
        }

        CardKind.WORD_TO_TRANSLATION -> {
            val target = targetLanguage ?: return null
            val back = bilingualBack(
                lemma = lemma,
                sense = sense,
                targetLanguage = target,
                translationTargets = translationTargets,
                favoriteLemmas = favoriteLemmas,
            ) ?: return null
            val senses = studiedSenses.toBilingualSenseUiStates(
                lemma = lemma,
                targetLanguage = target,
                translationTargets = translationTargets,
                favoriteLemmas = favoriteLemmas,
            )
            StudyCardUiState.Recognition(
                id = card.id.toString(),
                chipLabel = UiText.Plain("${sourceLanguage.studyCode()} -> ${target.studyCode()}"),
                promptWord = lemma,
                promptAudioText = lemma,
                mode = StudyRecognitionMode.BILINGUAL,
                senses = senses,
                activeSenseId = sense.senseId,
                back = back,
            )
        }

        CardKind.SOURCE_DEFINITION_TO_WORD -> {
            val senses = studiedSenses.toSourceSenseUiStates(
                lemma = lemma,
                targetLanguage = null,
                translationTargets = translationTargets,
                favoriteLemmas = favoriteLemmas,
            )
            StudyCardUiState.Production(
                id = card.id.toString(),
                chipLabel = UiText.Resource(
                    Res.string.study_chip_source_only,
                    listOf(sourceLanguage.studyCode()),
                ),
                promptLabel = UiText.Resource(Res.string.study_prompt_recall_word),
                promptText = sense.senseDefinition,
                firstLetterHint = lemma.firstLetterHint(),
                isDefinitionPrompt = true,
                senses = senses,
                activeSenseId = sense.senseId,
                back = sourceBack(
                    lemma = lemma,
                    sense = sense,
                    targetLanguage = null,
                    translationTargets = translationTargets,
                    favoriteLemmas = favoriteLemmas,
                    examples = sense.studyExamples(targetLanguage = null),
                ),
            )
        }

        CardKind.TRANSLATION_TO_WORD -> {
            val target = targetLanguage ?: return null
            val cue = sense.translationCue(target) ?: return null
            val senses = studiedSenses.toSourceSenseUiStates(
                lemma = lemma,
                targetLanguage = target,
                translationTargets = translationTargets,
                favoriteLemmas = favoriteLemmas,
            )
            StudyCardUiState.Production(
                id = card.id.toString(),
                chipLabel = UiText.Plain("${target.studyCode()} -> ${sourceLanguage.studyCode()}"),
                promptLabel = UiText.Resource(
                    Res.string.study_prompt_translate_to,
                    listOf(sourceLanguage.selfName),
                ),
                promptText = cue,
                firstLetterHint = lemma.firstLetterHint(),
                senses = senses,
                activeSenseId = sense.senseId,
                back = sourceBack(
                    lemma = lemma,
                    sense = sense,
                    targetLanguage = target,
                    translationTargets = translationTargets,
                    favoriteLemmas = favoriteLemmas,
                    examples = sense.studyExamples(target),
                ),
            )
        }

        CardKind.CLOZE_SOURCE -> {
            val target = targetLanguage ?: return null
            val cloze = example?.toClozeText() ?: return null
            val clozeTranslation = sense.examples[example.exampleIndex.toInt()]
                .targetLangTranslations[target]
                ?.let { toTranslationHintCloze(it)?.copy(filled = true) }
            val activeBack = sourceBack(
                lemma = lemma,
                sense = sense,
                targetLanguage = target,
                translationTargets = translationTargets,
                favoriteLemmas = favoriteLemmas,
                examples = emptyList(),
                cloze = cloze.copy(filled = true),
                clozeTranslation = clozeTranslation,
            )
            val senses = studiedSenses.toSourceSenseUiStates(
                lemma = lemma,
                targetLanguage = target,
                translationTargets = translationTargets,
                favoriteLemmas = favoriteLemmas,
            ).map { senseUi ->
                if (senseUi.id == sense.senseId) senseUi.copy(back = activeBack) else senseUi
            }
            // Prefer the translation hint only when it actually highlights the answer word
            // (the example translation is tagged). CLOZE_SOURCE can be eligible without a
            // tagged/present example translation, so fall back to the first-letter hint to
            // guarantee a usable hint.
            val highlightedTranslation = clozeTranslation?.takeIf { it.answerRanges.isNotEmpty() }
            StudyCardUiState.Cloze(
                id = card.id.toString(),
                chipLabel = UiText.Resource(Res.string.study_chip_fill_in),
                prompt = cloze,
                translationHint = highlightedTranslation,
                firstLetterHint = if (highlightedTranslation == null) {
                    lemma.firstLetterHint()
                } else {
                    null
                },
                senses = senses,
                activeSenseId = sense.senseId,
                back = activeBack,
            )
        }

        CardKind.CLOZE_TRANSLATION -> {
            val target = targetLanguage ?: return null
            val cloze = example?.toClozeText() ?: return null
            val sourceExample = sense.examples[example.exampleIndex.toInt()]
            val backExamples = listOf(
                StudyExampleUiState(
                    text = sourceExample.text,
                    translation = sourceExample.targetLangTranslations[target],
                )
            )
            val activeBack = sourceBack(
                lemma = lemma,
                sense = sense,
                targetLanguage = target,
                translationTargets = translationTargets,
                favoriteLemmas = favoriteLemmas,
                examples = backExamples,
            )
            val senses = studiedSenses.toSourceSenseUiStates(
                lemma = lemma,
                targetLanguage = target,
                translationTargets = translationTargets,
                favoriteLemmas = favoriteLemmas,
            ).map { senseUi ->
                if (senseUi.id == sense.senseId) senseUi.copy(back = activeBack) else senseUi
            }
            StudyCardUiState.Cloze(
                id = card.id.toString(),
                chipLabel = UiText.Resource(
                    Res.string.study_chip_recall,
                    listOf(sourceLanguage.studyCode()),
                ),
                prompt = cloze.copy(filled = true),
                // The recalled answer is the word itself, so hint from the lemma.
                firstLetterHint = lemma.firstLetterHint(),
                senses = senses,
                activeSenseId = sense.senseId,
                back = activeBack,
            )
        }

        CardKind.LISTENING_TRANSLATION -> {
            val target = targetLanguage ?: return null
            val senses = studiedSenses.toSourceSenseUiStates(
                lemma = lemma,
                targetLanguage = target,
                translationTargets = translationTargets,
                favoriteLemmas = favoriteLemmas,
            )
            StudyCardUiState.Listening(
                id = card.id.toString(),
                chipLabel = UiText.Resource(Res.string.study_chip_listen),
                promptAudioText = lemma,
                senses = senses,
                activeSenseId = sense.senseId,
                back = sourceBack(
                    lemma = lemma,
                    sense = sense,
                    targetLanguage = target,
                    translationTargets = translationTargets,
                    favoriteLemmas = favoriteLemmas,
                    examples = sense.studyExamples(target),
                ),
            )
        }
    }
}

fun List<GradeOutcome>.toStudyRatings(): List<StudyRatingUiState> =
    map { outcome ->
        StudyRatingUiState(
            rating = outcome.rating.toStudyRating(),
            intervalLabel = outcome.intervalLabel(),
        )
    }

fun StudyRating.toDomainRating(): Rating =
    when (this) {
        StudyRating.AGAIN -> Rating.AGAIN
        StudyRating.HARD -> Rating.HARD
        StudyRating.GOOD -> Rating.GOOD
        StudyRating.EASY -> Rating.EASY
    }

private fun Rating.toStudyRating(): StudyRating =
    when (this) {
        Rating.AGAIN -> StudyRating.AGAIN
        Rating.HARD -> StudyRating.HARD
        Rating.GOOD -> StudyRating.GOOD
        Rating.EASY -> StudyRating.EASY
    }

private fun sourceBack(
    lemma: String,
    sense: LanguageCardResponseSense,
    targetLanguage: Language?,
    translationTargets: List<Language>,
    favoriteLemmas: Set<String>,
    examples: List<StudyExampleUiState>,
    cloze: StudyClozeTextUiState? = null,
    clozeTranslation: StudyClozeTextUiState? = null,
): StudyCardBackUiState {
    val blocks = sense.languageBlocks(targetLanguage, translationTargets)
    return StudyCardBackUiState(
        headline = lemma,
        isLemmaHeadline = true,
        translations = blocks.translations,
        definition = sense.senseDefinition,
        definitionTranslation = blocks.definitions,
        examples = examples,
        synonyms = sense.toStudySynonyms(favoriteLemmas),
        cloze = cloze,
        clozeTranslation = clozeTranslation,
        audioText = lemma,
    )
}

// The translation-language sections of a card back, in the exact bullet-per-language format the
// word-details screen uses. Monolingual variants (targetLanguage == null) keep a translation-free
// back; bilingual variants confirm the answer in every configured translation language, all
// rendered equally. If either section spans several languages, both carry bullets so one card
// never mixes formats.
private class LanguageBlocks(
    val translations: String?,
    val definitions: String?,
    val isMultiLanguage: Boolean,
)

private fun LanguageCardResponseSense.languageBlocks(
    targetLanguage: Language?,
    translationTargets: List<Language>,
): LanguageBlocks {
    val languages = if (targetLanguage == null) {
        emptySet()
    } else {
        (listOf(targetLanguage) + translationTargets).distinctBy { it.code }.toSet()
    }
    val translationLines = translationLines(languages)
    val definitionLines = definitionLines(languages)
    val bulleted = translationLines.size > 1 || definitionLines.size > 1
    return LanguageBlocks(
        translations = joinLanguageLines(translationLines, bulleted),
        definitions = joinLanguageLines(definitionLines, bulleted),
        isMultiLanguage = translationLines.size > 1,
    )
}

private fun LanguageCardResponseSense.definitionLines(languages: Set<Language>): List<String> =
    targetLangDefinitions.entries
        .filter { it.key in languages && it.value.isNotBlank() }
        .sortedBy { it.key }
        .map { it.value }

// The TRANSLATION_TO_WORD prompt: the tested language's words through the same formatter as the
// card backs, so cue and answer agree on filtering and word order.
private fun LanguageCardResponseSense.translationCue(language: Language): String? =
    translationLines(setOf(language)).firstOrNull()

private fun List<LanguageCardResponseSense>.toBilingualSenseUiStates(
    lemma: String,
    targetLanguage: Language,
    translationTargets: List<Language>,
    favoriteLemmas: Set<String>,
): List<StudyCardSenseUiState> =
    mapNotNull { sense ->
        bilingualBack(
            lemma = lemma,
            sense = sense,
            targetLanguage = targetLanguage,
            translationTargets = translationTargets,
            favoriteLemmas = favoriteLemmas,
        )?.let { back -> sense.senseId to back }
    }.mapIndexed { index, (senseId, back) ->
        StudyCardSenseUiState(id = senseId, num = index + 1, back = back)
    }

// The headline is the answer: translation words in every configured language, in the exact same
// bullet-per-language block the word-details screen uses, so no language looks subordinate. The
// definition follows the same rule — one line per configured language — with the source-language
// definition below, as before. The studied word sits under the headline so the back also answers
// "which word was this?" without flipping back. Returns null when the tested language cannot be
// rendered, judged by the same satisfiesTranslationData gate that variant building and load-state
// checks use.
private fun bilingualBack(
    lemma: String,
    sense: LanguageCardResponseSense,
    targetLanguage: Language,
    translationTargets: List<Language>,
    favoriteLemmas: Set<String>,
): StudyCardBackUiState? {
    if (!sense.satisfiesTranslationData(CardKind.WORD_TO_TRANSLATION, targetLanguage)) return null
    val blocks = sense.languageBlocks(targetLanguage, translationTargets)
    val headline = blocks.translations ?: return null
    val definition = blocks.definitions ?: return null
    return StudyCardBackUiState(
        headline = headline,
        sourceWord = lemma,
        isMultiLanguageHeadline = blocks.isMultiLanguage,
        definition = definition,
        definitionTranslation = sense.senseDefinition.takeIf { it.isNotBlank() },
        examples = sense.studyExamples(targetLanguage),
        synonyms = sense.toStudySynonyms(favoriteLemmas),
        // No speaker on this back: the front of a word -> translation card already voices the word.
        audioText = null,
    )
}

private fun List<LanguageCardResponseSense>.toSourceSenseUiStates(
    lemma: String,
    targetLanguage: Language?,
    translationTargets: List<Language>,
    favoriteLemmas: Set<String>,
): List<StudyCardSenseUiState> =
    mapIndexed { index, sense ->
        StudyCardSenseUiState(
            id = sense.senseId,
            num = index + 1,
            back = sourceBack(
                lemma = lemma,
                sense = sense,
                targetLanguage = targetLanguage,
                translationTargets = translationTargets,
                favoriteLemmas = favoriteLemmas,
                examples = sense.studyExamples(targetLanguage),
            ),
        )
    }

private fun LanguageCardResponseSense.toStudySynonyms(
    favoriteLemmas: Set<String>,
): List<StudySynonymUiState> =
    synonyms
        .filter { it.isNotBlank() }
        .distinctBy { synonym -> normalizeLemma(synonym) }
        .map { synonym ->
            StudySynonymUiState(
                word = synonym,
                known = normalizeLemma(synonym) in favoriteLemmas,
            )
        }
        .sortedByDescending { it.known }

private fun LanguageCardResponseSense.studyExamples(targetLanguage: Language?): List<StudyExampleUiState> {
    val example = examples.randomOrNull() ?: return emptyList()
    return listOf(
        StudyExampleUiState(
            text = example.text,
            translation = targetLanguage?.let { example.targetLangTranslations[it] },
        ),
    )
}

private fun ExamplePair.toClozeText(): StudyClozeTextUiState? {
    if (text.isBlank()) return null
    if (clozeRanges.isEmpty()) return null
    return StudyClozeTextUiState(
        text = text,
        answerRanges = clozeRanges,
    )
}

private fun toTranslationHintCloze(text: String): StudyClozeTextUiState? {
    val parsed = parseClozeFromTaggedText(text) ?: return null
    return StudyClozeTextUiState(text = parsed.plainText, answerRanges = parsed.answerRanges)
}

internal fun String.firstLetterHint(): FirstLetterHint? {
    val first = firstOrNull { it.isLetter() } ?: return null
    val letterCount = count { it.isLetter() }
    if (letterCount <= 1) return null
    val dotCount = letterCount - 1
    return FirstLetterHint(letter = first, letterCount = letterCount, dotCount = dotCount)
}

private fun Language.studyCode(): String = code.uppercase()

private fun GradeOutcome.intervalLabel(): String {
    val interval = intervalMillis.milliseconds
    val minutes = interval.inWholeMinutes
    val hours = interval.inWholeHours
    val days = interval.inWholeDays
    return when {
        interval < 1.minutes -> "< 1min"
        interval < 1.hours -> "${minutes.coerceAtLeast(1)}min"
        interval < 1.days -> "${hours.coerceAtLeast(1)}h"
        days < 30 -> "${days.coerceAtLeast(1)}d"
        days < 365 -> "${(days / 30.0).roundToLong().coerceAtLeast(1)}mo"
        else -> "${(days / 365.0).roundToLong().coerceAtLeast(1)}y"
    }
}
