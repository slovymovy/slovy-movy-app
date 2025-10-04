package com.slovy.slovymovyapp.ui.word

import androidx.compose.animation.*
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import com.slovy.slovymovyapp.data.remote.DictionaryRepository
import com.slovy.slovymovyapp.data.remote.LanguageCard
import com.slovy.slovymovyapp.data.remote.LanguageCardPosEntry
import com.slovy.slovymovyapp.data.remote.LanguageCardResponseSense
import com.slovy.slovymovyapp.ui.AppNavigationBar
import com.slovy.slovymovyapp.ui.AppScreen

sealed interface WordDetailUiState {
    data class Empty(val lemma: String? = null, val message: String = "No entries available.") : WordDetailUiState
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

internal fun LanguageCard.toContentUiState(targetSenseId: String? = null): WordDetailUiState.Content =
    WordDetailUiState.Content(
        card = this,
        entries = entries.mapIndexed { index, entry -> entry.toEntryUiState(index, targetSenseId) }
    )

private fun LanguageCardPosEntry.toEntryUiState(index: Int, targetSenseId: String? = null): EntryUiState = EntryUiState(
    entryId = "${pos.name.lowercase()}_$index",
    expanded = true,
    formsExpanded = false,
    senses = senses.map {
        val expanded = if (targetSenseId != null) {
            it.senseId == targetSenseId
        } else {
            senses.size < 2
        }
        it.toSenseUiState(expanded)
    }
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

class WordDetailViewModel(
    repository: DictionaryRepository,
    dictionaryLanguage: String = "",
    lemma: String = "",
    targetSenseId: String? = null
) : ViewModel() {
    var state by mutableStateOf<WordDetailUiState>(WordDetailUiState.Empty())
        private set

    val scrollState = ScrollState(0)
    val targetSenseId: String? = targetSenseId
    var sensePositions by mutableStateOf<Map<String, Float>>(emptyMap())
        private set

    private var hasScrolledToTarget = false

    init {
        val card = repository.getLanguageCard(dictionaryLanguage, lemma)
        state =
            card?.toContentUiState(targetSenseId) ?: WordDetailUiState.Empty(lemma = lemma, message = "Word not found")
    }

    fun setScrollPosition(position: Int) {
        // This will be called when restoring from saved state
        scrollState.dispatchRawDelta(-scrollState.value.toFloat())
        scrollState.dispatchRawDelta(position.toFloat())
    }

    fun updateSensePosition(senseId: String, yOffset: Float) {
        sensePositions = sensePositions + (senseId to yOffset)
    }

    fun scrollToTargetSenseIfNeeded() {
        if (hasScrolledToTarget) return
        val target = targetSenseId ?: return
        val position = sensePositions[target] ?: return
        scrollState.dispatchRawDelta(position - scrollState.value.toFloat())
        hasScrolledToTarget = true
    }

    fun toggleEntry(index: Int) {
        val current = state
        if (current is WordDetailUiState.Content) {
            state = current.toggleEntry(index)
        }
    }

    fun toggleForms(index: Int) {
        val current = state
        if (current is WordDetailUiState.Content) {
            state = current.toggleForms(index)
        }
    }

    fun toggleSense(entryIndex: Int, senseId: String) {
        val current = state
        if (current is WordDetailUiState.Content) {
            state = current.toggleSense(entryIndex, senseId)
        }
    }

    fun toggleSenseExamples(entryIndex: Int, senseId: String) {
        val current = state
        if (current is WordDetailUiState.Content) {
            state = current.toggleSenseExamples(entryIndex, senseId)
        }
    }

    fun toggleLanguage(entryIndex: Int, senseId: String, languageCode: String) {
        val current = state
        if (current is WordDetailUiState.Content) {
            state = current.toggleSenseLanguage(entryIndex, senseId, languageCode)
        }
    }
}

@Composable
internal fun ExpandableSection(
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
    viewModel: WordDetailViewModel,
    onBack: () -> Unit = {},
    onNavigateToSearch: () -> Unit = {}
) {
    // Restore scroll position after process death
    val savedScrollPosition = rememberSaveable { viewModel.scrollState.value }

    LaunchedEffect(savedScrollPosition) {
        if (viewModel.scrollState.value == 0 && savedScrollPosition > 0) {
            viewModel.setScrollPosition(savedScrollPosition)
        }
    }

    // Scroll to target sense once positions are available (only once)
    LaunchedEffect(viewModel.targetSenseId, viewModel.sensePositions) {
        if (viewModel.targetSenseId != null && viewModel.sensePositions.containsKey(viewModel.targetSenseId)) {
            viewModel.scrollToTargetSenseIfNeeded()
        }
    }

    WordDetailScreenContent(
        state = viewModel.state,
        scrollState = viewModel.scrollState,
        onBack = onBack,
        onNavigateToSearch = onNavigateToSearch,
        onEntryToggle = { index -> viewModel.toggleEntry(index) },
        onFormsToggle = { index -> viewModel.toggleForms(index) },
        onSenseToggle = { entryIndex, senseId -> viewModel.toggleSense(entryIndex, senseId) },
        onSenseExamplesToggle = { entryIndex, senseId -> viewModel.toggleSenseExamples(entryIndex, senseId) },
        onLanguageToggle = { entryIndex, senseId, languageCode ->
            viewModel.toggleLanguage(entryIndex, senseId, languageCode)
        },
        onSensePositioned = { senseId, yOffset -> viewModel.updateSensePosition(senseId, yOffset) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordDetailScreenContent(
    state: WordDetailUiState,
    scrollState: ScrollState = ScrollState(0),
    onBack: () -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    onEntryToggle: (Int) -> Unit = {},
    onFormsToggle: (Int) -> Unit = {},
    onSenseToggle: (Int, String) -> Unit = { _, _ -> },
    onSenseExamplesToggle: (Int, String) -> Unit = { _, _ -> },
    onLanguageToggle: (Int, String, String) -> Unit = { _, _, _ -> },
    onSensePositioned: (String, Float) -> Unit = { _, _ -> },
) {
    val fallbackTitle = "Word Details"
    val titleText = when (state) {
        is WordDetailUiState.Content -> state.card.lemma
        is WordDetailUiState.Empty -> state.lemma ?: fallbackTitle
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    when (state) {
                        is WordDetailUiState.Content ->
                            BasicText(
                                text = state.card.lemma,
                                style = MaterialTheme.typography.headlineSmall,
                                autoSize = TextAutoSize.StepBased(
                                    minFontSize = 5.sp,
                                    maxFontSize = MaterialTheme.typography.headlineSmall.fontSize,
                                    stepSize = 1.sp
                                ),
                                maxLines = 1
                            )

                        is WordDetailUiState.Empty -> HighlightedText(
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
        bottomBar = {
            AppNavigationBar(
                currentScreen = AppScreen.WORD_DETAIL,
                isWordDetailAvailable = true,
                onNavigateToSearch = onNavigateToSearch,
                onNavigateToWordDetail = {}
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        when (state) {
            is WordDetailUiState.Content -> {
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
                    onLanguageToggle = onLanguageToggle,
                    onSensePositioned = onSensePositioned
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
    onLanguageToggle: (Int, String, String) -> Unit,
    onSensePositioned: (String, Float) -> Unit = { _, _ -> }
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
                    },
                    onSensePositioned = onSensePositioned
                )
            }
        }
    }
}

