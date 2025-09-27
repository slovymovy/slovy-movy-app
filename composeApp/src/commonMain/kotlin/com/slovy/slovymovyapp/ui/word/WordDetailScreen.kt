package com.slovy.slovymovyapp.ui.word

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.slovy.slovymovyapp.ui.codeToLanguage
import kotlin.text.Typography.bullet

sealed interface WordDetailUiState {
    object Loading : WordDetailUiState
    data class Empty(val lemma: String? = null, val message: String = "No entries available.") : WordDetailUiState
    data class Error(val message: String, val showRetry: Boolean = true) : WordDetailUiState
    data class Content(val card: LanguageCard, val entries: List<EntryUiState>) : WordDetailUiState
}

data class EntryUiState(
    val entryId: String,
    val expanded: Boolean = true,
    val formsExpanded: Boolean = false,
    val senses: List<SenseUiState> = emptyList()
)

data class SenseUiState(
    val senseId: String,
    val expanded: Boolean = true,
    val examplesExpanded: Boolean = true,
    val languageExpanded: Map<String, Boolean> = emptyMap()
)

internal fun LanguageCard.toContentUiState(): WordDetailUiState.Content = WordDetailUiState.Content(
    card = this,
    entries = entries.mapIndexed { index, entry -> entry.toEntryUiState(index) }
)

private fun LanguageCardPosEntry.toEntryUiState(index: Int): EntryUiState = EntryUiState(
    entryId = "${pos.name.lowercase()}_$index",
    expanded = true,
    formsExpanded = false,
    senses = senses.map { it.toSenseUiState() }
)

private fun LanguageCardResponseSense.toSenseUiState(): SenseUiState {
    val defaultExpanded = frequency == SenseFrequency.HIGH || frequency == SenseFrequency.MIDDLE
    val languages = collectLanguageCodes()
    val languageStates = languages.associateWith { defaultExpanded }
    val examplesExpanded = if (examples.isEmpty()) false else true
    return SenseUiState(
        senseId = senseId,
        expanded = defaultExpanded,
        examplesExpanded = examplesExpanded,
        languageExpanded = languageStates
    )
}

private fun LanguageCardResponseSense.collectLanguageCodes(): List<String> {
    val ordered = linkedSetOf<String>()
    ordered += targetLangDefinitions.keys
    ordered += translations.keys
    examples.forEach { ex -> ordered += ex.targetLangTranslations.keys }
    return ordered.toList()
}

private fun WordDetailUiState.Content.toggleEntry(entryIndex: Int): WordDetailUiState.Content =
    updateEntry(entryIndex) { entry -> entry.copy(expanded = !entry.expanded) }

private fun WordDetailUiState.Content.toggleForms(entryIndex: Int): WordDetailUiState.Content =
    updateEntry(entryIndex) { entry -> entry.copy(formsExpanded = !entry.formsExpanded) }

private fun WordDetailUiState.Content.toggleSense(entryIndex: Int, senseId: String): WordDetailUiState.Content =
    updateEntry(entryIndex) { entry -> entry.updateSense(senseId) { sense -> sense.copy(expanded = !sense.expanded) } }

private fun WordDetailUiState.Content.toggleSenseExamples(entryIndex: Int, senseId: String): WordDetailUiState.Content =
    updateEntry(entryIndex) { entry -> entry.updateSense(senseId) { sense -> sense.copy(examplesExpanded = !sense.examplesExpanded) } }

private fun WordDetailUiState.Content.toggleSenseLanguage(
    entryIndex: Int,
    senseId: String,
    languageCode: String
): WordDetailUiState.Content =
    updateEntry(entryIndex) { entry ->
        entry.updateSense(senseId) { sense -> sense.toggleLanguage(languageCode) }
    }

private inline fun WordDetailUiState.Content.updateEntry(
    entryIndex: Int,
    transform: (EntryUiState) -> EntryUiState
): WordDetailUiState.Content {
    if (entryIndex !in entries.indices) return this
    val updated = entries.mapIndexed { idx, entry ->
        if (idx == entryIndex) transform(entry) else entry
    }
    return copy(entries = updated)
}

private inline fun EntryUiState.updateSense(
    senseId: String,
    transform: (SenseUiState) -> SenseUiState
): EntryUiState {
    val idx = senses.indexOfFirst { it.senseId == senseId }
    if (idx == -1) return this
    val updatedSenses = senses.mapIndexed { index, sense ->
        if (index == idx) transform(sense) else sense
    }
    return copy(senses = updatedSenses)
}

private fun SenseUiState.toggleLanguage(languageCode: String): SenseUiState {
    val current = languageExpanded[languageCode] ?: expanded
    val updated = languageExpanded.toMutableMap().apply { put(languageCode, !current) }
    return copy(languageExpanded = updated)
}

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
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    headlineStyle: TextStyle = MaterialTheme.typography.titleMedium,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onToggle),
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
@Composable
fun WordDetailScreen(
    card: LanguageCard,
    onBack: () -> Unit = {},
    onRetry: () -> Unit = {}
) {
    var internalState by remember(card) {
        mutableStateOf(
            card.toContentUiState()
        )
    }

    fun updateContent(transform: (WordDetailUiState.Content) -> WordDetailUiState.Content) {
        val current = internalState
        internalState = transform(current)
    }

    WordDetailScreenContent(
        state = internalState,
        onBack = onBack,
        onRetry = onRetry,
        onEntryToggle = { index -> updateContent { it.toggleEntry(index) } },
        onFormsToggle = { index -> updateContent { it.toggleForms(index) } },
        onSenseToggle = { entryIndex, senseId -> updateContent { it.toggleSense(entryIndex, senseId) } },
        onSenseExamplesToggle = { entryIndex, senseId ->
            updateContent { it.toggleSenseExamples(entryIndex, senseId) }
        },
        onLanguageToggle = { entryIndex, senseId, languageCode ->
            updateContent { it.toggleSenseLanguage(entryIndex, senseId, languageCode) }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordDetailScreenContent(
    state: WordDetailUiState,
    onBack: () -> Unit = {},
    onRetry: () -> Unit = {},
    onEntryToggle: (Int) -> Unit = {},
    onFormsToggle: (Int) -> Unit = {},
    onSenseToggle: (Int, String) -> Unit = { _, _ -> },
    onSenseExamplesToggle: (Int, String) -> Unit = { _, _ -> },
    onLanguageToggle: (Int, String, String) -> Unit = { _, _, _ -> },
) {
    val fallbackTitle = "Word Details"
    val titleText = when (state) {
        is WordDetailUiState.Content -> state.card.lemma
        is WordDetailUiState.Empty -> state.lemma ?: fallbackTitle
        else -> fallbackTitle
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    when (state) {
                        is WordDetailUiState.Content -> HighlightedText(
                            text = state.card.lemma,
                            style = MaterialTheme.typography.displaySmall,
                            textAlign = TextAlign.Center
                        )

                        is WordDetailUiState.Empty -> HighlightedText(
                            text = titleText,
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center
                        )

                        else -> Text(
                            text = titleText,
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center
                        )
                    }
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
        when (state) {
            is WordDetailUiState.Content -> {
                val scrollState = rememberScrollState()
                WordDetailContent(
                    card = state.card,
                    entryStates = state.entries,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(innerPadding)
                        .padding(horizontal = 16.dp, vertical = 24.dp),
                    onEntryToggle = onEntryToggle,
                    onFormsToggle = onFormsToggle,
                    onSenseToggle = onSenseToggle,
                    onSenseExamplesToggle = onSenseExamplesToggle,
                    onLanguageToggle = onLanguageToggle
                )
            }

            is WordDetailUiState.Empty -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            is WordDetailUiState.Error -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                    if (state.showRetry) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onRetry) {
                            Text("Retry")
                        }
                    }
                }
            }

            WordDetailUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
private fun WordDetailContent(
    card: LanguageCard,
    entryStates: List<EntryUiState>,
    modifier: Modifier = Modifier,
    onEntryToggle: (Int) -> Unit,
    onFormsToggle: (Int) -> Unit,
    onSenseToggle: (Int, String) -> Unit,
    onSenseExamplesToggle: (Int, String) -> Unit,
    onLanguageToggle: (Int, String, String) -> Unit
) {
    Column(
        modifier = modifier,
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
                val entryState = entryStates.getOrNull(index) ?: entry.toEntryUiState(index)
                EntryCard(
                    entry = entry,
                    entryState = entryState,
                    onEntryToggle = { onEntryToggle(index) },
                    onFormsToggle = { onFormsToggle(index) },
                    onSenseToggle = { senseId -> onSenseToggle(index, senseId) },
                    onSenseExamplesToggle = { senseId -> onSenseExamplesToggle(index, senseId) },
                    onLanguageToggle = { senseId, languageCode ->
                        onLanguageToggle(index, senseId, languageCode)
                    }
                )
            }
        }
    }
}

@Composable
private fun EntryCard(
    entry: LanguageCardPosEntry,
    entryState: EntryUiState,
    onEntryToggle: () -> Unit,
    onFormsToggle: () -> Unit,
    onSenseToggle: (String) -> Unit,
    onSenseExamplesToggle: (String) -> Unit,
    onLanguageToggle: (String, String) -> Unit
) {
    val expanded = entryState.expanded
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
                    .clickable(onClick = onEntryToggle)
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val (pc, pcc) = colorsForPos(entry.pos)
                Surface(
                    color = pc,
                    contentColor = pcc,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = entry.pos.name.lowercase()
                            .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "(${entry.senses.size} meaning${pluralEnding(entry.senses)})",
                    style = MaterialTheme.typography.labelLarge.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )

                Spacer(modifier = Modifier.width(16.dp))

                val summaryParts = buildList {
                    if (entry.forms.isNotEmpty()) {
                        add("${entry.forms.size} form${pluralEnding(entry.forms)}")
                    }
                }.joinToString(" $bullet ")

                Column(modifier = Modifier.weight(1f)) {
                    AnimatedVisibility(
                        visible = !expanded && summaryParts.isNotBlank(),
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
                            expanded = entryState.formsExpanded,
                            onToggle = onFormsToggle,
                            supportingText = formsSummary.ifEmpty { null },
                            headlineStyle = MaterialTheme.typography.titleMedium
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
                                val senseState = entryState.senses.find { it.senseId == sense.senseId }
                                    ?: sense.toSenseUiState()
                                SenseCard(
                                    sense = sense,
                                    state = senseState,
                                    onToggle = { onSenseToggle(sense.senseId) },
                                    onExamplesToggle = { onSenseExamplesToggle(sense.senseId) },
                                    onLanguageToggle = { languageCode ->
                                        onLanguageToggle(sense.senseId, languageCode)
                                    }
                                )
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
private fun SenseCard(
    sense: LanguageCardResponseSense,
    state: SenseUiState,
    onToggle: () -> Unit,
    onExamplesToggle: () -> Unit,
    onLanguageToggle: (String) -> Unit
) {
    val expanded = state.expanded
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
                    .clickable(onClick = onToggle)
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

                    val summaryParts = buildList {
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
                        visible = !expanded && summaryParts.isNotBlank(),
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
                            title = "Examples",
                            expanded = state.examplesExpanded,
                            onToggle = onExamplesToggle,
                            supportingText = examplePreview.ifBlank { null },
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
                        val langExpanded = state.languageExpanded[lang] ?: expanded
                        LanguageSection(
                            languageCode = lang,
                            sense = sense,
                            expanded = langExpanded,
                            onToggle = { onLanguageToggle(lang) }
                        )
                    }

                }
            }
        }
    }
}

@Composable
private fun LanguageSection(
    languageCode: String,
    sense: LanguageCardResponseSense,
    expanded: Boolean,
    onToggle: () -> Unit
) {
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
        expanded = expanded,
        onToggle = onToggle,
        supportingText = supportingParts.ifEmpty { null },
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
private fun LevelAndFrequencyRow(level: LearnerLevel, frequency: SenseFrequency) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val (lc, lcc) = colorsForLevel(level)
        val (fc, fcc) = colorsForFrequency(frequency)
        Badge(text = level.name, container = lc, content = lcc)
        val freqLabel = frequency.name.lowercase().replaceFirstChar { it.titlecase() }
        Badge(text = freqLabel, container = fc, content = fcc)
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

