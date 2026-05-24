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
import slovymovyapp.composeapp.generated.resources.*
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

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

    val studiedSenses = cardData.entries
        .flatMap { it.senses }
        .filter { it.senseId in studiedSenseIds }
        .sortedByDescending { it.senseId == sense.senseId }

    return when (variant.kind) {
        CardKind.WORD_TO_SOURCE_DEFINITION -> {
            val senses = studiedSenses.toSourceSenseUiStates(
                lemma = lemma,
                targetLanguage = null,
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
                ),
            )
        }

        CardKind.WORD_TO_TRANSLATION -> {
            val target = targetLanguage ?: return null
            val answer = sense.translationCue(target) ?: return null
            val definition = sense.translationDef(target) ?: return null
            val senses = studiedSenses.toBilingualSenseUiStates(target)
            StudyCardUiState.Recognition(
                id = card.id.toString(),
                chipLabel = UiText.Plain("${sourceLanguage.studyCode()} -> ${target.studyCode()}"),
                promptWord = lemma,
                promptAudioText = lemma,
                mode = StudyRecognitionMode.BILINGUAL,
                senses = senses,
                activeSenseId = sense.senseId,
                back = StudyCardBackUiState(
                    headline = answer,
                    definition = definition,
                    examples = sense.studyExamples(target),
                    audioText = null,
                ),
            )
        }

        CardKind.SOURCE_DEFINITION_TO_WORD -> {
            val senses = studiedSenses.toSourceSenseUiStates(
                lemma = lemma,
                targetLanguage = null,
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
                ),
            )
        }

        CardKind.TRANSLATION_TO_WORD -> {
            val target = targetLanguage ?: return null
            val cue = sense.translationCue(target) ?: return null
            val senses = studiedSenses.toSourceSenseUiStates(
                lemma = lemma,
                targetLanguage = target,
            )
            StudyCardUiState.Production(
                id = card.id.toString(),
                chipLabel = UiText.Plain("${target.studyCode()} -> ${sourceLanguage.studyCode()}"),
                promptLabel = UiText.Resource(
                    Res.string.study_prompt_translate_to,
                    listOf(sourceLanguage.englishName),
                ),
                promptText = cue,
                firstLetterHint = lemma.firstLetterHint(),
                senses = senses,
                activeSenseId = sense.senseId,
                back = sourceBack(
                    lemma = lemma,
                    sense = sense,
                    targetLanguage = target,
                ),
            )
        }

        CardKind.CLOZE_SOURCE -> {
            val target = targetLanguage ?: return null
            val cloze = example?.toClozeText() ?: return null
            val activeBack = sourceClozeBack(
                lemma = lemma,
                sense = sense,
                targetLanguage = target,
                cloze = cloze.copy(filled = true),
            )
            val senses = studiedSenses.toSourceSenseUiStates(
                lemma = lemma,
                targetLanguage = target,
            ).map { senseUi ->
                if (senseUi.id == sense.senseId) senseUi.copy(back = activeBack) else senseUi
            }
            StudyCardUiState.Cloze(
                id = card.id.toString(),
                chipLabel = UiText.Resource(Res.string.study_chip_fill_in),
                prompt = cloze,
                translationHint = sense.examples
                    .firstOrNull { HtmlTagParser.plainText(it.text) == example.text }
                    ?.targetLangTranslations
                    ?.get(target)
                    ?.toTranslationHintCloze(),
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
            val activeBack = sourceClozeBack(
                lemma = lemma,
                sense = sense,
                targetLanguage = target,
                examples = backExamples,
            )
            val senses = studiedSenses.toSourceSenseUiStates(
                lemma = lemma,
                targetLanguage = target,
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
                translationHint = sourceExample.text.toTranslationHintCloze(),
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
): StudyCardBackUiState =
    StudyCardBackUiState(
        headline = lemma,
        isLemmaHeadline = true,
        secondary = targetLanguage?.let { sense.translationWords(it) },
        definition = sense.senseDefinition,
        cloze = cloze,
        audioText = lemma,
        examples = sense.studyExamples(targetLanguage)
    )

private fun sourceClozeBack(
    lemma: String,
    sense: LanguageCardResponseSense,
    targetLanguage: Language?,
    cloze: StudyClozeTextUiState? = null,
    examples: List<StudyExampleUiState> = emptyList(),
): StudyCardBackUiState =
    StudyCardBackUiState(
        headline = lemma,
        isLemmaHeadline = true,
        secondary = targetLanguage?.let { sense.translationWords(it) },
        definition = sense.senseDefinition,
        examples = examples,
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

private fun List<LanguageCardResponseSense>.toBilingualSenseUiStates(
    targetLanguage: Language,
): List<StudyCardSenseUiState> =
    mapNotNull { sense ->
        val answer = sense.translationCue(targetLanguage) ?: return@mapNotNull null
        val definition = sense.translationDef(targetLanguage) ?: return@mapNotNull null
        Triple(sense, answer, definition)
    }.mapIndexed { index, (sense, answer, definition) ->
        StudyCardSenseUiState(
            id = sense.senseId,
            num = index + 1,
            back = StudyCardBackUiState(
                headline = answer,
                definition = definition,
                examples = sense.studyExamples(targetLanguage),
                audioText = null,
            ),
        )
    }

private fun List<LanguageCardResponseSense>.toSourceSenseUiStates(
    lemma: String,
    targetLanguage: Language?,
): List<StudyCardSenseUiState> =
    mapIndexed { index, sense ->
        StudyCardSenseUiState(
            id = sense.senseId,
            num = index + 1,
            back = sourceBack(
                lemma = lemma,
                sense = sense,
                targetLanguage = targetLanguage,
            ),
        )
    }

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

private fun String.toTranslationHintCloze(): StudyClozeTextUiState? {
    if (isBlank()) return null
    val output = StringBuilder()
    val ranges = mutableListOf<IntRange>()
    HtmlTagParser.parseTextSegments(this).forEach { segment ->
        val start = output.length
        val segmentText = HtmlTagParser.plainText(segment.text)
        output.append(segmentText)
        if (segment.isTagged && segmentText.isNotBlank()) {
            val leading = segmentText.indexOfFirst { !it.isWhitespace() }
            val trailing = segmentText.indexOfLast { !it.isWhitespace() }
            ranges.add((start + leading)..(start + trailing))
        }
    }
    val plain = output.toString()
    if (plain.isBlank()) return null
    return StudyClozeTextUiState(text = plain, answerRanges = ranges)
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
