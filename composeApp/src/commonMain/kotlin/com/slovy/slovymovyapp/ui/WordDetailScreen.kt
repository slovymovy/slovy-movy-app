package com.slovy.slovymovyapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.slovy.slovymovyapp.data.remote.*
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
private fun KeyValue(label: String, value: String) {
    if (value.isBlank()) return
    Text(text = label, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold))
    HighlightedText(text = value, style = MaterialTheme.typography.bodyMedium)
}

@Preview
@Composable
fun WordDetailScreen(
    card: LanguageCard = sampleCard(),
    lemma: String? = null,
    onBack: () -> Unit = {}
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = "Word details",
                style = MaterialTheme.typography.headlineSmall
            )
            if (lemma != null) {
                Text(text = lemma, style = MaterialTheme.typography.titleLarge)
            }

            card.entries.forEachIndexed { idx, entry ->
                if (idx > 0) HorizontalDivider()

                Text(
                    text = entry.pos,
                    style = MaterialTheme.typography.titleMedium
                )

                if (entry.forms.isNotEmpty()) {
                    val formsJoined = entry.forms.joinToString(separator = ", ") { it.form }
                    KeyValue(label = "Forms", value = formsJoined)
                }

                val sensesByGroup = entry.senses.groupBy { it.semanticGroupId }
                sensesByGroup.forEach { (groupId, senseList) ->
                    if (sensesByGroup.size > 1) {
                        Text(
                            text = "Group: $groupId",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                    senseList.forEach { sense ->
                        HorizontalDivider()
                        HighlightedText(text = sense.senseDefinition, style = MaterialTheme.typography.bodyLarge)

                        // Level + Frequency badges
                        LevelAndFrequencyRow(level = sense.learnerLevel, frequency = sense.frequency)

                        if (sense.synonyms.isNotEmpty()) {
                            KeyValue("Synonyms", sense.synonyms.joinToString())
                        }
                        if (sense.antonyms.isNotEmpty()) {
                            KeyValue("Antonyms", sense.antonyms.joinToString())
                        }
                        if (sense.commonPhrases.isNotEmpty()) {
                            KeyValue("Phrases", sense.commonPhrases.joinToString())
                        }

                        if (sense.translations.isNotEmpty()) {
                            sense.translations.forEach { (tgt, list) ->
                                val joined = list.joinToString {
                                    it.targetLangWord + (it.targetLangSenseClarification?.let { c -> " ($c)" } ?: "")
                                }
                                KeyValue("Translations [$tgt]", joined)
                            }
                        }
                        if (sense.targetLangDefinitions.isNotEmpty()) {
                            sense.targetLangDefinitions.forEach { (tgt, def) ->
                                KeyValue("Definition [$tgt]", def)
                            }
                        }

                        if (sense.examples.isNotEmpty()) {
                            Text(
                                text = "Examples",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                            )
                            sense.examples.forEach { ex ->
                                BulletHighlightedText(text = ex.text, style = MaterialTheme.typography.bodyMedium)
                                if (ex.targetLangTranslations.isNotEmpty()) {
                                    ex.targetLangTranslations.forEach { (tgt, tr) ->
                                        PrefixedHighlightedText(
                                            prefix = "  [$tgt] ",
                                            text = tr,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Button(onClick = onBack) {
                Text("Back")
            }
        }
    }
}

private fun sampleCard(): LanguageCard {
    return LanguageCard(
        entries = listOf(
            // Verb entry
            LanguageCardPosEntry(
                pos = "verb",
                forms = mutableListOf(),
                senses = listOf(
                    LanguageCardResponseSense(
                        senseId = "f8731e0a-3d06-4a0f-af1c-98de00309b76",
                        senseDefinition = "The present participle or gerund form of the verb 'to test', meaning to perform an examination or evaluation.",
                        learnerLevel = LearnerLevel.A2,
                        frequency = SenseFrequency.HIGH,
                        semanticGroupId = "action_of_testing",
                        nameType = "no",
                        examples = listOf(
                            LanguageCardExample(
                                text = "The scientist is <w>testing</w> the new hypothesis in the lab.",
                                targetLangTranslations = mapOf("ru" to "Учёный <w>проверяет</w> новую гипотезу в лаборатории.")
                            ),
                            LanguageCardExample(
                                text = "<w>Testing</w> the water before swimming is a good idea.",
                                targetLangTranslations = mapOf("ru" to "<w>Проверять</w> воду перед плаванием — хорошая идея.")
                            ),
                            LanguageCardExample(
                                text = "They spent all morning <w>testing</w> out the new equipment.",
                                targetLangTranslations = mapOf("ru" to "Они всё утро <w>проверяли</w> новое оборудование.")
                            )
                        ),
                        synonyms = listOf("examining", "evaluating", "checking", "trying out"),
                        commonPhrases = listOf("testing out", "testing for", "testing positive/negative"),
                        traits = listOf(
                            LanguageCardTrait(
                                TraitType.FORM,
                                "present participle and gerund form of 'test'"
                            )
                        ),
                        targetLangDefinitions = mapOf(
                            "ru" to "Причастие настоящего времени или герундий от глагола 'to test', означающее выполнение проверки или оценки."
                        ),
                        translations = mapOf(
                            "ru" to listOf(
                                LanguageCardTranslation(
                                    targetLangWord = "тестирование",
                                    targetLangSenseClarification = "Процесс проверки или испытания чего-либо. Используется как отглагольное существительное (герундий)."
                                ),
                                LanguageCardTranslation(
                                    targetLangWord = "проверка",
                                    targetLangSenseClarification = "Более общее слово для обозначения процесса контроля или испытания, часто используется как герундий."
                                )
                            )
                        )
                    )
                )
            ),
            // Noun entry
            LanguageCardPosEntry(
                pos = "noun",
                forms = mutableListOf(),
                senses = listOf(
                    LanguageCardResponseSense(
                        senseId = "16f3dcde-882f-464a-8f53-0843847294b2",
                        senseDefinition = "The process of checking something to see if it works correctly or meets certain standards.",
                        learnerLevel = LearnerLevel.B2,
                        frequency = SenseFrequency.HIGH,
                        semanticGroupId = "evaluation_process",
                        nameType = "no",
                        examples = listOf(
                            LanguageCardExample(
                                text = "The software is currently undergoing rigorous <w>testing</w> before its release.",
                                targetLangTranslations = mapOf("ru" to "В настоящее время программное обеспечение проходит тщательное <w>тестирование</w> перед выпуском.")
                            ),
                            LanguageCardExample(
                                text = "Quality <w>testing</w> is essential to ensure product reliability.",
                                targetLangTranslations = mapOf("ru" to "<w>Тестирование</w> качества необходимо для обеспечения надёжности продукта.")
                            ),
                            LanguageCardExample(
                                text = "The new car went through extensive road <w>testing</w> to ensure its safety.",
                                targetLangTranslations = mapOf("ru" to "Новый автомобиль прошёл длительные дорожные <w>испытания</w> для обеспечения его безопасности.")
                            )
                        ),
                        synonyms = listOf("examination", "evaluation", "trial", "assessment"),
                        commonPhrases = listOf(
                            "product testing",
                            "software testing",
                            "road testing",
                            "quality testing"
                        ),
                        targetLangDefinitions = mapOf(
                            "ru" to "Процесс проверки чего-либо с целью убедиться, что оно работает правильно или соответствует определённым стандартам."
                        ),
                        translations = mapOf(
                            "ru" to listOf(
                                LanguageCardTranslation(
                                    targetLangWord = "тестирование",
                                    targetLangSenseClarification = "Процесс испытания или проверки чего-либо, особенно в техническом или научном контексте (например, программного обеспечения, оборудования)."
                                ),
                                LanguageCardTranslation(
                                    targetLangWord = "испытание",
                                    targetLangSenseClarification = "Проверка свойств, качеств кого-либо или чего-либо, часто в сложных или экстремальных условиях (например, дорожные испытания)."
                                ),
                                LanguageCardTranslation(
                                    targetLangWord = "проверка",
                                    targetLangSenseClarification = "Более общее слово, означающее контроль или обследование с целью выяснения правильности, состояния чего-либо."
                                )
                            )
                        )
                    )
                )
            ),
            // Adjective entry
            LanguageCardPosEntry(
                pos = "adjective",
                forms = mutableListOf(),
                senses = listOf(
                    LanguageCardResponseSense(
                        senseId = "5e1a3249-2d90-42a3-ab1d-c18a2c92f4d0",
                        senseDefinition = "Causing difficulty or requiring a lot of effort and ability.",
                        learnerLevel = LearnerLevel.B1,
                        frequency = SenseFrequency.MIDDLE,
                        semanticGroupId = "difficulty",
                        nameType = "no",
                        examples = listOf(
                            LanguageCardExample(
                                text = "The exam was very <w>testing</w>, but I think I did well.",
                                targetLangTranslations = mapOf("ru" to "Экзамен был очень <w>сложным</w>, но, думаю, я справился хорошо.")
                            ),
                            LanguageCardExample(
                                text = "She faced a <w>testing</w> challenge when she started her new job.",
                                targetLangTranslations = mapOf("ru" to "Она столкнулась с <w>трудной</w> задачей, когда начала работать на новой работе.")
                            ),
                            LanguageCardExample(
                                text = "It was a <w>testing</w> time for everyone involved in the project.",
                                targetLangTranslations = mapOf("ru" to "Это было <w>напряжённое</w> время для всех, кто участвовал в проекте.")
                            )
                        ),
                        synonyms = listOf("challenging", "difficult", "demanding"),
                        antonyms = listOf("easy", "simple"),
                        commonPhrases = listOf("a testing time", "a testing period", "a testing challenge"),
                        targetLangDefinitions = mapOf(
                            "ru" to "Трудный, требующий больших усилий, напряжения или способностей."
                        ),
                        translations = mapOf(
                            "ru" to listOf(
                                LanguageCardTranslation(
                                    targetLangWord = "сложный",
                                    targetLangSenseClarification = "Наиболее общий и частый перевод, означает 'трудный для выполнения, понимания или решения'."
                                ),
                                LanguageCardTranslation(
                                    targetLangWord = "трудный",
                                    targetLangSenseClarification = "Синоним 'сложный', подчёркивает необходимость приложить усилия и преодолеть препятствия."
                                ),
                                LanguageCardTranslation(
                                    targetLangWord = "напряжённый",
                                    targetLangSenseClarification = "Описывает период времени или ситуацию, требующую больших физических или умственных усилий и вызывающую стресс (например, 'a testing time')."
                                )
                            )
                        )
                    )
                )
            )
        )
    )
}


@Composable
private fun Badge(text: String, container: Color, content: Color) {
    Surface(color = container, contentColor = content, shape = RoundedCornerShape(8.dp)) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun colorsForLevel(level: LearnerLevel): Pair<Color, Color> = when (level) {
    LearnerLevel.A1, LearnerLevel.A2 ->
        MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer

    LearnerLevel.B1 -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    LearnerLevel.B2 -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
    LearnerLevel.C1 -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    LearnerLevel.C2 -> MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.onError
}

@Composable
private fun colorsForFrequency(f: SenseFrequency): Pair<Color, Color> = when (f) {
    SenseFrequency.HIGH -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
    SenseFrequency.MIDDLE -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    SenseFrequency.LOW -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    SenseFrequency.VERY_LOW -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun LevelAndFrequencyRow(level: LearnerLevel, frequency: SenseFrequency) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val (lc, lcc) = colorsForLevel(level)
        val (fc, fcc) = colorsForFrequency(frequency)
        Badge(text = "Level: ${level.name}", container = lc, content = lcc)
        val freqLabel = frequency.name.lowercase().replaceFirstChar { it.titlecase() }
        Badge(text = "Frequency: $freqLabel", container = fc, content = fcc)
    }
}


// Helpers to render <w>word</w> with special highlight style across all displayed text
@Composable
private fun HighlightedText(text: String, style: TextStyle) {
    val highlight = SpanStyle(
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold
    )
    val annotated = buildAnnotatedString {
        appendTextWithW(this, text, highlight)
    }
    Text(text = annotated, style = style)
}

@Composable
private fun BulletHighlightedText(text: String, style: TextStyle) {
    val highlight = SpanStyle(
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold
    )
    val annotated = buildAnnotatedString {
        append("• ")
        appendTextWithW(this, text, highlight)
    }
    Text(text = annotated, style = style)
}

@Composable
private fun PrefixedHighlightedText(prefix: String, text: String, style: TextStyle) {
    val highlight = SpanStyle(
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold
    )
    val annotated = buildAnnotatedString {
        append(prefix)
        appendTextWithW(this, text, highlight)
    }
    Text(text = annotated, style = style)
}

private fun appendTextWithW(builder: AnnotatedString.Builder, input: String, highlight: SpanStyle) {
    var i = 0
    while (i < input.length) {
        val start = input.indexOf("<w>", i)
        if (start == -1) {
            builder.append(input.substring(i))
            break
        }
        // Append text before <w>
        if (start > i) builder.append(input.substring(i, start))
        val end = input.indexOf("</w>", start + 3)
        if (end == -1) {
            // No closing tag – append the rest verbatim
            builder.append(input.substring(start))
            break
        }
        val word = input.substring(start + 3, end)
        builder.withStyle(highlight) { builder.append(word) }
        i = end + 4 // move after </w>
    }
}
