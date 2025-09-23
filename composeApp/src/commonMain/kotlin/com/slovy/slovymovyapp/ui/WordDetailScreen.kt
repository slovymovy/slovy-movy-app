package com.slovy.slovymovyapp.ui

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.slovy.slovymovyapp.data.remote.*
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.text.Typography.bullet

@Composable
private fun SectionLabel(text: String) {
    HighlightedText(
        text = text,
        style = MaterialTheme.typography.labelLarge.copy(
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
}

@Composable
private fun KeyValue(label: String, value: String) {
    if (value.isBlank()) return
    SectionLabel(label)
    HighlightedText(text = value, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun FormsList(forms: List<LanguageCardForm>) {
    if (forms.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        forms.forEach { form ->
            val tags = if (form.tags.isNotEmpty()) {
                form.tags.joinToString(separator = ", ", prefix = " (", postfix = ")")
            } else {
                ""
            }
            HighlightedText(
                text = "$bullet ${form.form}$tags",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}

@Composable
private fun ExpandableSection(
    title: String,
    modifier: Modifier = Modifier,
    stateKey: String,
    startExpanded: Boolean = true,
    supportingText: String? = null,
    headlineStyle: TextStyle = MaterialTheme.typography.titleMedium,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by rememberSaveable(stateKey) { mutableStateOf(startExpanded) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { expanded = !expanded },
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    HighlightedText(text = title, style = headlineStyle)
                    supportingText?.takeIf { !expanded }?.let {
                        HighlightedText(
                            text = it,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) ExpandLessVector else ExpandMoreVector,
                    contentDescription = if (expanded) {
                        "Collapse $title"
                    } else {
                        "Expand $title"
                    },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                content()
            }
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
                    HighlightedText(
                        text = card.lemma,
                        style = MaterialTheme.typography.displaySmall,
                        textAlign = TextAlign.Center
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
                card.entries.forEachIndexed { index, entry ->
                    EntryCard(entry = entry, entryIndex = index)
                }
            }
        }
    }
}

@Composable
private fun EntryCard(entry: LanguageCardPosEntry, entryIndex: Int) {
    var expanded by rememberSaveable("entry_${entryIndex}_${entry.pos}") { mutableStateOf(true) }
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically
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

                Spacer(modifier = Modifier.width(16.dp))

                val summaryParts = buildList {
                    add("${entry.senses.size} sense${pluralEnding(entry.senses)}")
                    if (entry.forms.isNotEmpty()) {
                        add("${entry.forms.size} form${pluralEnding(entry.forms)}")
                    }
                }.joinToString(" $bullet ")

                Column(modifier = Modifier.weight(1f)) {
                    AnimatedVisibility(
                        visible = !expanded,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            HighlightedText(
                                text = summaryParts,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
                            Text(
                                text = "Tap to expand",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Icon(
                    imageVector = if (expanded) ExpandLessVector else ExpandMoreVector,
                    contentDescription = if (expanded) {
                        "Collapse ${entry.pos} entry"
                    } else {
                        "Expand ${entry.pos} entry"
                    },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 0.dp)
                        .padding(bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    if (entry.forms.isNotEmpty()) {
                        val formsSummary = entry.forms
                            .flatMap { it.tags }
                            .toSet()
                            .sortedBy { it }
                            .joinToString(separator = ", ")
                        ExpandableSection(
                            title = "Forms (${entry.forms.size})",
                            supportingText = formsSummary.ifEmpty { null },
                            stateKey = "entry_${entryIndex}_forms",
                            startExpanded = false
                        ) {
                            SectionLabel("Forms")
                            FormsList(entry.forms)
                        }
                    }

                    val groupEntries = entry.senses.groupBy { it.semanticGroupId }.entries.toList()
                    groupEntries.forEachIndexed { groupIndex, (groupId, senseList) ->
                        if (groupEntries.size > 1 && groupIndex > 0) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            if (groupEntries.size > 1 && groupId.isNotBlank()) {
                                SectionLabel("Group: $groupId")
                            }

                            senseList.forEachIndexed { senseIndex, sense ->
                                SenseCard(sense = sense)
                                if (senseIndex < senseList.lastIndex) {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun pluralEnding(someList: List<*>): String = if (someList.size == 1) "" else "s"

@Composable
private fun SenseCard(sense: LanguageCardResponseSense) {
    var expanded by rememberSaveable("expanded_${sense.senseId}") {
        mutableStateOf(sense.frequency == SenseFrequency.HIGH || sense.frequency == SenseFrequency.MIDDLE)
    }

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HighlightedText(
                        text = sense.senseDefinition,
                        style = MaterialTheme.typography.titleLarge
                    )

                    LevelAndFrequencyRow(level = sense.learnerLevel, frequency = sense.frequency)

                    val frequencyLabel = sense.frequency.name.lowercase().replaceFirstChar { it.titlecase() }
                    val levelLabel = sense.learnerLevel.name
                    val summaryParts = buildList {
                        add("$frequencyLabel frequency")
                        add("Level $levelLabel")
                        if (sense.synonyms.isNotEmpty()) {
                            add("${sense.synonyms.size} synonym${pluralEnding(sense.synonyms)}")
                        }
                        val translationsCount = sense.translations.values.sumOf { it.size }
                        if (translationsCount > 0) {
                            add("$translationsCount translation${pluralEnding(sense.translations.values.flatten())}")
                        }
                        if (sense.examples.isNotEmpty()) {
                            add("${sense.examples.size} example${pluralEnding(sense.examples)}")
                        }
                    }.joinToString(" $bullet ")

                    AnimatedVisibility(
                        visible = !expanded,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        HighlightedText(
                            text = summaryParts,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }

                Icon(
                    imageVector = if (expanded) ExpandLessVector else ExpandMoreVector,
                    contentDescription = if (expanded) {
                        "Collapse sense"
                    } else {
                        "Expand sense"
                    },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 0.dp)
                        .padding(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (sense.examples.isNotEmpty()) {
                        val examplePreview = sense.examples.first().text
                            .replace("\n", " ")
                        ExpandableSection(
                            title = "Examples (${sense.examples.size})",
                            supportingText = examplePreview.ifBlank { null },
                            stateKey = "${sense.senseId}_examples",
                            startExpanded = true,
                            headlineStyle = MaterialTheme.typography.titleSmall
                        ) {
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
                    if (sense.synonyms.isNotEmpty()) {
                        KeyValue("Synonyms", bulletList(sense.synonyms))
                    }
                    if (sense.antonyms.isNotEmpty()) {
                        KeyValue("Antonyms", bulletList(sense.antonyms))
                    }
                    if (sense.commonPhrases.isNotEmpty()) {
                        KeyValue("Phrases", bulletList(sense.commonPhrases))
                    }

                    val languageOrder = buildList {
                        sense.targetLangDefinitions.keys.forEach { add(it) }
                        sense.translations.keys.forEach { if (!contains(it)) add(it) }
                    }

                    languageOrder.forEach { lang ->
                        LanguageSection(languageCode = lang, sense = sense, startExpanded = expanded)
                    }

                }
            }
        }
    }
}

@Composable
private fun LanguageSection(languageCode: String, sense: LanguageCardResponseSense, startExpanded: Boolean) {
    val languageLabel = codeToLanguage.getOrElse(languageCode) { languageCode }
    val definition = sense.targetLangDefinitions[languageCode]
    val translations = sense.translations[languageCode].orEmpty()
    val translationCount = translations.size
    val supportingParts = buildList {
        if (definition != null) add("Definition")
        if (translationCount > 0) {
            add("$translationCount translation${pluralEnding(translations)}")
        }
    }.joinToString(" $bullet ")

    ExpandableSection(
        title = languageLabel,
        supportingText = supportingParts.ifEmpty { null },
        stateKey = "${sense.senseId}_${languageCode}_translations",
        startExpanded = startExpanded,
        headlineStyle = MaterialTheme.typography.titleSmall
    ) {
        definition?.let {
            HighlightedText(
                text = it,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        if (translationCount > 0) {
            SectionLabel("Translations")
            TranslationList(translations = translations)
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
private fun HighlightedText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    textAlign: TextAlign? = null
) {
    val highlight = SpanStyle(
        color = MaterialTheme.colorScheme.secondary,
        fontWeight = FontWeight.Light
    )
    val annotated = buildAnnotatedString {
        appendTextWithW(this, text, highlight)
    }
    Text(text = annotated, style = style, modifier = modifier, textAlign = textAlign)
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

private val ExpandMoreVector: ImageVector = ImageVector.Builder(
    name = "ExpandableChevronDown",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    path(
        fill = SolidColor(Color.Black),
        pathFillType = PathFillType.NonZero
    ) {
        moveTo(12f, 16f)
        lineTo(5.5f, 9.5f)
        lineTo(6.91f, 8.09f)
        lineTo(12f, 13.17f)
        lineTo(17.09f, 8.09f)
        lineTo(18.5f, 9.5f)
        close()
    }
}.build()

private val ExpandLessVector: ImageVector = ImageVector.Builder(
    name = "ExpandableChevronUp",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    path(
        fill = SolidColor(Color.Black),
        pathFillType = PathFillType.NonZero
    ) {
        moveTo(5.5f, 14.5f)
        lineTo(6.91f, 15.91f)
        lineTo(12f, 10.83f)
        lineTo(17.09f, 15.91f)
        lineTo(18.5f, 14.5f)
        lineTo(12f, 8.0f)
        close()
    }
}.build()

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
