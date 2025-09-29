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
    senses = senses.map { it.toSenseUiState(senses.size < 2) }
)

private fun LanguageCardResponseSense.toSenseUiState(expanded: Boolean): SenseUiState {
    val languages = collectLanguageCodes()
    val languageStates = languages.associateWith { false }
    val examplesExpanded = examples.isNotEmpty()
    return SenseUiState(
        senseId = senseId,
        expanded = expanded,
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EntryList(
    label: String, values: List<String>, containerColor: Color, contentColor: Color
) {
    if (values.isEmpty()) return
    SectionLabel(label)
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        values.forEach {
            Badge(
                text = it,
                containerColor = containerColor,
                contentColor = contentColor
            )
        }
    }
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
                        ExpandableSection(
                            title = "Grammar",
                            expanded = entryState.formsExpanded,
                            onToggle = onFormsToggle,
                            supportingText = null,
                            headlineStyle = MaterialTheme.typography.titleSmall
                        ) {
                            SectionLabel("Grammar")
                            FormsList(entry.forms)
                        }
                    }

                    val groupEntries = entry.senses.groupBy { it.semanticGroupId }.entries.toList()
                    groupEntries.forEachIndexed { groupIndex, (groupId, senseList) ->
                        val showGroup = groupEntries.size > 1 && groupIndex > 0 && senseList.size > 1
                        if (showGroup) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            if (showGroup) {
                                SectionLabel("Group: $groupId")
                            }

                            senseList.forEachIndexed { senseIndex, sense ->
                                val senseState = entryState.senses.find { it.senseId == sense.senseId }
                                    ?: sense.toSenseUiState(false)
                                SenseCard(
                                    sense = sense,
                                    state = senseState,
                                    translationBasedHeader = sense.translationsHeader(),
                                    translationHeaderSuffix = translationsHeaderSuffix(sense, entry.senses),
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
    translationBasedHeader: String?,
    translationHeaderSuffix: String?,
    onToggle: () -> Unit,
    onExamplesToggle: () -> Unit,
    onLanguageToggle: (String) -> Unit,
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
                    if (translationBasedHeader == null) {
                        HighlightedText(
                            text = sense.senseDefinition,
                            style = MaterialTheme.typography.titleMedium
                        )
                    } else {
                        HighlightedText(
                            text = translationBasedHeader,
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (translationHeaderSuffix != null) {
                            HighlightedText(
                                text = translationHeaderSuffix,
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                    }

                    LevelAndFrequencyRow(
                        level = sense.learnerLevel,
                        frequency = sense.frequency,
                        nameType = sense.nameType
                    )
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
                    if (sense.targetLangDefinitions.isNotEmpty()) {
                        OutlinedCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            if (translationBasedHeader != null) {
                                Text(
                                    text = sense.senseDefinition,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            if (translationHeaderSuffix == null) {
                                sense.targetLangDefinitions.forEach {
                                    Text(
                                        text = it.value,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (sense.traits.isNotEmpty()) {
                        TraitsList(traits = sense.traits)
                    }

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
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    if (ex.targetLangTranslations.isNotEmpty()) {
                                        ex.targetLangTranslations.forEach { (_, translation) ->
                                            PrefixedHighlightedText(
                                                prefix = "– ",
                                                text = translation,
                                                style = MaterialTheme.typography.bodySmall,
                                                modifier = Modifier.padding(start = 24.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    EntryList(
                        "Common phrases",
                        sense.commonPhrases,
                        MaterialTheme.colorScheme.tertiaryContainer,
                        MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    EntryList(
                        "Synonyms",
                        sense.synonyms,
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    EntryList(
                        "Antonyms",
                        sense.antonyms,
                        MaterialTheme.colorScheme.errorContainer,
                        MaterialTheme.colorScheme.onErrorContainer
                    )

                    val languageOrder = buildList {
                        sense.targetLangDefinitions.keys.forEach { add(it) }
                        sense.translations.keys.forEach { if (!contains(it)) add(it) }
                    }

                    languageOrder.forEach { lang ->
                        if (sense.translations[lang]?.isNotEmpty() ?: false) {
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
}

private fun translationsHeaderSuffix(
    sense: LanguageCardResponseSense,
    allSenses: List<LanguageCardResponseSense>
): String? {
    if (sense.translations.isEmpty()) {
        return null
    } else {
        val eachCount = allSenses.map { it.translationsHeader() }.groupingBy { it }.eachCount()
        val translationsHeader = sense.translationsHeader()
        return if (eachCount[translationsHeader] == 1) {
            null
        } else {
            sense.targetLangDefinitions.map { definition -> definition.value.replaceFirstChar { if (it.isUpperCase()) it.lowercase() else it.toString() } }
                .joinToString(",")
        }
    }
}

private fun LanguageCardResponseSense.translationsHeader(): String? {
    if (translations.isEmpty()) {
        return null
    }
    val prefix = if (translations.keys.size > 1) "$bullet " else ""
    return translations.entries.sortedBy { it.key }.joinToString(separator = "\n") {
        prefix + it.value.map { translation -> translation.targetLangWord }.sortedBy { text -> text }
            .joinToString(separator = ", ")
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
    val translations = sense.translations[languageCode].orEmpty()

    ExpandableSection(
        title = languageLabel,
        expanded = expanded,
        onToggle = onToggle,
        supportingText = null,
        headlineStyle = MaterialTheme.typography.titleSmall
    ) {
        TranslationList(translations = translations)
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

@Composable
private fun Badge(text: String, containerColor: Color, contentColor: Color) {
    Surface(color = containerColor, contentColor = contentColor, shape = RoundedCornerShape(12.dp)) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TraitsList(traits: List<LanguageCardTrait>) {
    if (traits.isEmpty()) return
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SectionLabel("Notes")
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            traits.forEach { trait ->
                val (tc, tcc) = colorsForTraitType(trait.traitType)

                OutlinedCard(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.outlinedCardColors(containerColor = tc.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = trait.traitType.displayName,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = tcc
                            )
                        )
                        if (trait.comment.isNotBlank()) {
                            Text(
                                text = trait.comment,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LevelAndFrequencyRow(level: LearnerLevel, frequency: SenseFrequency, nameType: NameType?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val (lc, lcc) = colorsForLevel(level)
        val (fc, fcc) = colorsForFrequency(frequency)
        Badge(text = level.name, containerColor = lc, contentColor = lcc)
        Badge(text = frequency.label, containerColor = fc, contentColor = fcc)
        if (nameType != null && nameType != NameType.NO) {
            val (nc, ncc) = colorsForNameType(nameType)
            Badge(text = nameType.displayName, containerColor = nc, contentColor = ncc)
        }
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
