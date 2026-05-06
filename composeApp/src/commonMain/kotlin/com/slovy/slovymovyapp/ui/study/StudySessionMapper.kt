package com.slovy.slovymovyapp.ui.study

import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.learning.CardKind
import com.slovy.slovymovyapp.data.learning.GradeOutcome
import com.slovy.slovymovyapp.data.learning.Rating
import com.slovy.slovymovyapp.data.learning.session.ExamplePair
import com.slovy.slovymovyapp.data.learning.session.SessionCard
import com.slovy.slovymovyapp.data.learning.session.SessionCardLoadState
import com.slovy.slovymovyapp.data.remote.LanguageCardResponseSense
import com.slovy.slovymovyapp.data.util.HtmlTagParser
import com.slovy.slovymovyapp.i18n.UiText
import kotlin.math.roundToLong
import slovymovyapp.composeapp.generated.resources.Res
import slovymovyapp.composeapp.generated.resources.study_chip_fill_in
import slovymovyapp.composeapp.generated.resources.study_chip_listen
import slovymovyapp.composeapp.generated.resources.study_chip_recall
import slovymovyapp.composeapp.generated.resources.study_chip_source_only
import slovymovyapp.composeapp.generated.resources.study_prompt_recall_word
import slovymovyapp.composeapp.generated.resources.study_prompt_translate_to

fun SessionCard.toStudyCardUiState(): StudyCardUiState? {
    if (loadState() != SessionCardLoadState.READY) return null

    val cardData = wordResult.card ?: return null
    val sourceLanguage = Language.fromCodeOrNull(card.langCode) ?: return null
    val sense = cardData.entries
        .flatMap { it.senses }
        .firstOrNull { it.senseId == senseId }
        ?: return null
    val targetLanguage = variant.targetLang?.let(Language::fromCodeOrNull)
    val lemma = cardData.lemma

    return when (variant.kind) {
        CardKind.WORD_TO_SOURCE_DEFINITION -> StudyCardUiState.Recognition(
            id = card.id.toString(),
            chipLabel = UiText.Resource(
                Res.string.study_chip_source_only,
                listOf(sourceLanguage.studyCode()),
            ),
            promptWord = lemma,
            promptAudioText = lemma,
            mode = StudyRecognitionMode.MONOLINGUAL,
            back = sourceBack(
                lemma = lemma,
                sense = sense,
                targetLanguage = null,
            ),
        )

        CardKind.WORD_TO_TRANSLATION -> {
            val target = targetLanguage ?: return null
            val answer = sense.translationCue(target) ?: return null
            val definition = sense.translationDef(target) ?: return null
            StudyCardUiState.Recognition(
                id = card.id.toString(),
                chipLabel = UiText.Plain("${sourceLanguage.studyCode()} -> ${target.studyCode()}"),
                promptWord = lemma,
                promptAudioText = lemma,
                mode = StudyRecognitionMode.BILINGUAL,
                back = StudyCardBackUiState(
                    headline = answer,
                    definition = definition,
                    examples = sense.studyExamples(target),
                    audioText = null,
                ),
            )
        }

        CardKind.SOURCE_DEFINITION_TO_WORD -> StudyCardUiState.Production(
            id = card.id.toString(),
            chipLabel = UiText.Resource(
                Res.string.study_chip_source_only,
                listOf(sourceLanguage.studyCode()),
            ),
            promptLabel = UiText.Resource(Res.string.study_prompt_recall_word),
            promptText = sense.senseDefinition,
            firstLetterHint = lemma.firstLetterHint(),
            back = sourceBack(
                lemma = lemma,
                sense = sense,
                targetLanguage = null,
            ),
        )

        CardKind.TRANSLATION_TO_WORD -> {
            val target = targetLanguage ?: return null
            val cue = sense.translationCue(target) ?: return null
            StudyCardUiState.Production(
                id = card.id.toString(),
                chipLabel = UiText.Plain("${target.studyCode()} -> ${sourceLanguage.studyCode()}"),
                promptLabel = UiText.Resource(
                    Res.string.study_prompt_translate_to,
                    listOf(sourceLanguage.englishName),
                ),
                promptText = cue,
                firstLetterHint = lemma.firstLetterHint(),
                back = sourceBack(
                    lemma = lemma,
                    sense = sense,
                    targetLanguage = target,
                ),
            )
        }

        CardKind.CLOZE_SOURCE -> {
            val cloze = example?.toClozeText() ?: return null
            StudyCardUiState.Cloze(
                id = card.id.toString(),
                chipLabel = UiText.Resource(Res.string.study_chip_fill_in),
                prompt = cloze,
                translationHint = sense.examples
                    .firstOrNull { HtmlTagParser.plainText(it.text) == example.text }
                    ?.targetLangTranslations
                    ?.values
                    ?.firstOrNull(),
                back = sourceBack(
                    lemma = lemma,
                    sense = sense,
                    targetLanguage = null,
                    cloze = cloze.copy(filled = true),
                ),
            )
        }

        CardKind.CLOZE_TRANSLATION -> {
            val target = targetLanguage ?: return null
            val cloze = example?.toClozeText() ?: return null
            val backExamples = sense.examples[example.exampleIndex.toInt()].let {
                listOf(
                    StudyExampleUiState(
                        text = it.text,
                        translation = it.targetLangTranslations[target],
                    )
                )
            }
            StudyCardUiState.Cloze(
                id = card.id.toString(),
                chipLabel = UiText.Resource(
                    Res.string.study_chip_recall,
                    listOf(sourceLanguage.studyCode()),
                ),
                prompt = cloze.copy(filled = true),
                translationHint = null,
                back = sourceBack(
                    lemma = lemma,
                    sense = sense,
                    targetLanguage = target,
                    examples = backExamples,
                ),
            )
        }

        CardKind.LISTENING_TRANSLATION -> {
            val target = targetLanguage ?: return null
            StudyCardUiState.Listening(
                id = card.id.toString(),
                chipLabel = UiText.Resource(Res.string.study_chip_listen),
                promptAudioText = lemma,
                back = sourceBack(
                    lemma = lemma,
                    sense = sense,
                    targetLanguage = target,
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
    cloze: StudyClozeTextUiState? = null,
    examples: List<StudyExampleUiState>? = null,
): StudyCardBackUiState =
    StudyCardBackUiState(
        headline = lemma,
        isLemmaHeadline = true,
        secondary = targetLanguage?.let { sense.translationWords(it) },
        definition = sense.senseDefinition,
        examples = examples ?: sense.studyExamples(targetLanguage),
        cloze = cloze,
        audioText = lemma,
    )

private fun LanguageCardResponseSense.translationCue(language: Language): String? =
    translationWords(language)
private fun LanguageCardResponseSense.translationDef(language: Language): String? =
    targetLangDefinitions[language]

private fun LanguageCardResponseSense.translationWords(language: Language): String? =
    translations[language]
        .orEmpty()
        .map { it.targetLangWord }
        .filter { it.isNotBlank() }
        .distinct()
        .joinToString(", ")
        .takeIf { it.isNotBlank() }

private fun LanguageCardResponseSense.studyExamples(targetLanguage: Language?): List<StudyExampleUiState> =
    examples.take(1).map { example ->
        StudyExampleUiState(
            text = example.text,
            translation = targetLanguage?.let { example.targetLangTranslations[it] }
        )
    }

private fun ExamplePair.toClozeText(): StudyClozeTextUiState? {
    if (text.isBlank()) return null
    val start = clozeRange.first.coerceIn(0, text.length)
    val endExclusive = (clozeRange.last + 1).coerceIn(start, text.length)
    if (start == endExclusive) return null
    return StudyClozeTextUiState(
        prefix = text.substring(0, start),
        answer = text.substring(start, endExclusive),
        suffix = text.substring(endExclusive),
    )
}

internal fun String.firstLetterHint(): FirstLetterHint? {
    val first = firstOrNull { it.isLetter() } ?: return null
    val letterCount = count { it.isLetter() }
    val dotCount = letterCount.minus(1).coerceAtLeast(3)
    return FirstLetterHint(letter = first, letterCount = letterCount, dotCount = dotCount)
}

private fun Language.studyCode(): String = code.uppercase()

private fun GradeOutcome.intervalLabel(): String {
    val minutes = intervalMillis / 60_000
    val hours = intervalMillis / 3_600_000
    return when {
        intervalMillis < 60_000 -> "< 1m"
        minutes < 60 -> "${minutes.coerceAtLeast(1)}m"
        hours < 24 -> "${hours.coerceAtLeast(1)}h"
        intervalDays < 30 -> "${intervalDays.coerceAtLeast(1)}d"
        intervalDays < 365 -> "${(intervalDays / 30.0).roundToLong().coerceAtLeast(1)}mo"
        else -> "${(intervalDays / 365.0).roundToLong().coerceAtLeast(1)}y"
    }
}
