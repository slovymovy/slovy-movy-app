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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.slovy.slovymovyapp.data.remote.*
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.text.Typography.bullet

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun KeyValue(label: String, value: String) {
    if (value.isBlank()) return
    SectionLabel(label)
    HighlightedText(text = value, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun EntryForms(forms: List<LanguageCardForm>) {
    if (forms.isEmpty()) return
    SectionLabel("Forms")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        forms.forEach { form ->
            val tags = if (form.tags.isNotEmpty()) {
                form.tags.joinToString(separator = ", ", prefix = " (", postfix = ")")
            } else {
                ""
            }
            Text(
                text = "$bullet ${form.form}$tags",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
fun WordDetailScreen(
    card: LanguageCard = sampleCard(),
    onBack: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = card.lemma,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.displaySmall
                    )
                },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text(
                            text = "Back",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            horizontalAlignment = Alignment.Start
        ) {
            if (card.entries.isEmpty()) {
                Text(
                    text = "No entries available.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                card.entries.forEach { entry ->
                    EntryCard(entry = entry)
                }
            }
        }
    }
}

@Composable
private fun EntryCard(entry: LanguageCardPosEntry) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = entry.pos.uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            if (entry.forms.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                EntryForms(entry.forms)
            }

            val groupEntries = entry.senses.groupBy { it.semanticGroupId }.entries.toList()
            if (groupEntries.isNotEmpty()) {
                Spacer(modifier = Modifier.height(if (entry.forms.isNotEmpty()) 24.dp else 16.dp))
                groupEntries.forEachIndexed { groupIndex, (groupId, senseList) ->
                    if (groupEntries.size > 1 && groupIndex > 0) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    if (groupEntries.size > 1 && groupId.isNotBlank()) {
                        SectionLabel("Group: $groupId")
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    senseList.forEachIndexed { senseIndex, sense ->
                        SenseCard(sense = sense)
                        if (senseIndex < senseList.lastIndex) {
                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SenseCard(sense: LanguageCardResponseSense) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            HighlightedText(
                text = sense.senseDefinition,
                style = MaterialTheme.typography.titleLarge
            )

            LevelAndFrequencyRow(level = sense.learnerLevel, frequency = sense.frequency)

            if (sense.synonyms.isNotEmpty()) {
                KeyValue("Synonyms", bulletList(sense.synonyms))
            }
            if (sense.antonyms.isNotEmpty()) {
                KeyValue("Antonyms", bulletList(sense.antonyms))
            }
            if (sense.commonPhrases.isNotEmpty()) {
                KeyValue("Phrases", bulletList(sense.commonPhrases))
            }

            val targetLanguages = sense.targetLangDefinitions.keys + sense.translations.keys

            targetLanguages.forEach { lang ->
                SectionLabel(codeToLanguage.getOrElse(lang) { lang })
                sense.targetLangDefinitions[lang]?.let { definition ->
                    HighlightedText(
                        text = definition,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                sense.translations[lang]?.takeIf { it.isNotEmpty() }?.let { translations ->
                    SectionLabel("Translations")
                    TranslationList(translations = translations)
                }
            }

            if (sense.examples.isNotEmpty()) {
                SectionLabel("Examples")
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    sense.examples.forEach { ex ->
                        BulletHighlightedText(
                            text = ex.text,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (ex.targetLangTranslations.isNotEmpty()) {
                            ex.targetLangTranslations.forEach { (_, translation) ->
                                PrefixedHighlightedText(
                                    prefix = "– ",
                                    text = translation,
                                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                                    modifier = Modifier.padding(start = 24.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TranslationList(translations: List<LanguageCardTranslation>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        translations.forEach { translation ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                BulletHighlightedText(
                    text = translation.targetLangWord,
                    style = MaterialTheme.typography.bodyMedium
                )
                translation.targetLangSenseClarification?.takeIf { it.isNotBlank() }?.let { clarification ->
                    Text(
                        text = clarification,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 24.dp)
                    )
                }
            }
        }
    }
}

private fun bulletList(strings: List<String>): String =
    strings.joinToString(separator = "\n") { "$bullet $it" }

@Composable
private fun Badge(text: String, container: Color, content: Color) {
    Surface(color = container, contentColor = content, shape = RoundedCornerShape(12.dp)) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun colorsForLevel(level: LearnerLevel): Pair<Color, Color> = when (level) {
    LearnerLevel.A1 -> Color(0xFFE0F7D4) to Color(0xFF215732)
    LearnerLevel.A2 -> Color(0xFFBFEBD7) to Color(0xFF0F4C3C)
    LearnerLevel.B1 -> Color(0xFFCCE3FF) to Color(0xFF0F3D7A)
    LearnerLevel.B2 -> Color(0xFFB7D2FF) to Color(0xFF0F3566)
    LearnerLevel.C1 -> Color(0xFFFFE2C6) to Color(0xFF7A3E00)
    LearnerLevel.C2 -> Color(0xFFFBD0D9) to Color(0xFF7A1232)
}

@Composable
private fun colorsForFrequency(f: SenseFrequency): Pair<Color, Color> = when (f) {
    SenseFrequency.HIGH -> Color(0xFFDFF6DD) to Color(0xFF1C5E20)
    SenseFrequency.MIDDLE -> Color(0xFFFFF1C5) to Color(0xFF6C4A00)
    SenseFrequency.LOW -> Color(0xFFFFE0B2) to Color(0xFF8C4513)
    SenseFrequency.VERY_LOW -> Color(0xFFE7E9F0) to Color(0xFF3F4856)
}

@Composable
private fun LevelAndFrequencyRow(level: LearnerLevel, frequency: SenseFrequency) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
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
        color = MaterialTheme.colorScheme.secondary,
        fontWeight = FontWeight.Light
    )
    val annotated = buildAnnotatedString {
        appendTextWithW(this, text, highlight)
    }
    Text(text = annotated, style = style)
}

@Composable
private fun BulletHighlightedText(text: String, style: TextStyle) {
    val highlight = SpanStyle(
        color = MaterialTheme.colorScheme.secondary,
        fontWeight = FontWeight.Light
    )
    val annotated = buildAnnotatedString {
        append(bullet)
        appendTextWithW(this, text, highlight)
    }
    Text(text = annotated, style = style)
}

@Composable
private fun PrefixedHighlightedText(
    prefix: String,
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier
) {
    val highlight = SpanStyle(
        color = MaterialTheme.colorScheme.secondary,
        fontWeight = FontWeight.Light
    )
    val annotated = buildAnnotatedString {
        append(prefix)
        appendTextWithW(this, text, highlight)
    }
    Text(text = annotated, style = style, modifier = modifier)
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

private fun sampleCard(): LanguageCard {
    return LanguageCard(
        lemma = "testing",
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
