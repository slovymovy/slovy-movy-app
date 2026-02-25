package com.slovy.slovymovyapp.ui.reader

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewModelScope
import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.remote.DictionaryRepository
import com.slovy.slovymovyapp.data.remote.LearnerLevel
import com.slovy.slovymovyapp.ui.ThemePreviewProvider
import com.slovy.slovymovyapp.ui.ThemedPreview
import com.slovy.slovymovyapp.ui.theme.AppSpacing
import com.slovy.slovymovyapp.ui.word.colorsForLevel
import com.slovy.slovymovyapp.util.stripAccents
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.uuid.Uuid

private val TOKEN_REGEX = Regex("[\\p{L}\\p{M}\\-']+|[^\\p{L}\\p{M}\\-']+")
private val PillShape = RoundedCornerShape(6.dp)
private val HeartColor = Color(0xFFC46060)

data class TextToken(
    val text: String,
    val lemma: String? = null,
    val lemmaId: Uuid? = null,
    val level: LearnerLevel? = null,
    val isWord: Boolean
)

data class TextReaderUiState(
    val tokens: List<TextToken> = emptyList(),
    val favoriteLemmas: Set<String> = emptySet(),
    val isAnalyzing: Boolean = false,
    val error: String? = null
) {
    val hasResults: Boolean get() = tokens.isNotEmpty()
}

class TextReaderViewModel(
    private val repository: DictionaryRepository,
    val language: Language
) : ViewModel() {

    var state by mutableStateOf(TextReaderUiState())
        private set

    val scrollState = ScrollState(0)

    fun analyzeText(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            state = state.copy(isAnalyzing = true, error = null)
            try {
                val tokens = withContext(Dispatchers.Default) { tokenize(text) }
                val uniqueForms = tokens.filter { it.isWord }.map { stripAccents(it.text) }.distinct()
                val results = repository.lookupTokensBatch(uniqueForms, language)
                val resolvedTokens = tokens.map { token ->
                    if (!token.isWord) token
                    else {
                        val result = results[stripAccents(token.text)]
                        token.copy(
                            lemma = result?.lemma,
                            lemmaId = result?.lemmaId,
                            level = result?.level
                        )
                    }
                }
                val favoriteLemmas = repository.getFavoriteLemmasByLang(language)
                state = state.copy(tokens = resolvedTokens, favoriteLemmas = favoriteLemmas, isAnalyzing = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state = state.copy(isAnalyzing = false, error = e.message ?: "Error analyzing text")
            }
        }
    }

    fun refreshFavorites() {
        if (state.tokens.isEmpty()) return
        viewModelScope.launch {
            val favoriteLemmas = repository.getFavoriteLemmasByLang(language)
            state = state.copy(favoriteLemmas = favoriteLemmas)
        }
    }
}

private fun tokenize(text: String): List<TextToken> {
    return TOKEN_REGEX.findAll(text).map { match ->
        val value = match.value
        TextToken(text = value, isWord = value.any { it.isLetter() })
    }.toList()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextReaderScreen(
    viewModel: TextReaderViewModel,
    onWordClick: (language: Language, lemma: String) -> Unit,
    onBack: () -> Unit
) {
    LifecycleResumeEffect(Unit) {
        viewModel.refreshFavorites()
        onPauseOrDispose {}
    }
    TextReaderContent(
        state = viewModel.state,
        scrollState = viewModel.scrollState,
        language = viewModel.language,
        onAnalyze = { viewModel.analyzeText(it) },
        onWordClick = onWordClick,
        onBack = onBack
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextReaderContent(
    state: TextReaderUiState,
    scrollState: ScrollState = rememberScrollState(),
    language: Language = Language.DUTCH,
    onAnalyze: (String) -> Unit = {},
    onWordClick: (language: Language, lemma: String) -> Unit = { _, _ -> },
    onBack: () -> Unit = {}
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = {
                    Text(
                        "Text reader · ${language.selfName}",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        if (state.hasResults) {
            ResultView(
                state = state,
                scrollState = scrollState,
                language = language,
                onWordClick = onWordClick,
                modifier = Modifier.padding(innerPadding)
            )
        } else {
            InputView(
                isAnalyzing = state.isAnalyzing,
                error = state.error,
                onAnalyze = onAnalyze,
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}

@Composable
private fun InputView(
    isAnalyzing: Boolean,
    error: String?,
    onAnalyze: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    var clipboardEmpty by remember { mutableStateOf(false) }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppSpacing.lg),
            modifier = Modifier.padding(horizontal = AppSpacing.xxl)
        ) {
            Text(
                text = "Copy any text to your clipboard and paste it here with the button below",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Each word is looked up individually — phrases and multi-word expressions are not recognized",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            if (isAnalyzing) {
                CircularProgressIndicator(
                    modifier = Modifier.semantics { contentDescription = "Analyzing text" }
                )
            } else {
                FilledTonalButton(
                    onClick = {
                        val text = clipboardManager.getText()?.text.orEmpty()
                        if (text.isBlank()) {
                            clipboardEmpty = true
                        } else {
                            clipboardEmpty = false
                            onAnalyze(text)
                        }
                    }
                ) {
                    Text("Paste")
                }
            }

            if (clipboardEmpty) {
                Text(
                    text = "Clipboard is empty. Copy something awesome and paste here",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            if (error != null) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResultView(
    state: TextReaderUiState,
    scrollState: ScrollState,
    language: Language,
    onWordClick: (language: Language, lemma: String) -> Unit,
    modifier: Modifier = Modifier
) {
    // Full (bg, text) color pairs per level — used as solid pill backgrounds
    val levelColors: Map<LearnerLevel, Pair<Color, Color>> = mapOf(
        LearnerLevel.A1 to colorsForLevel(LearnerLevel.A1),
        LearnerLevel.A2 to colorsForLevel(LearnerLevel.A2),
        LearnerLevel.B1 to colorsForLevel(LearnerLevel.B1),
        LearnerLevel.B2 to colorsForLevel(LearnerLevel.B2),
        LearnerLevel.C1 to colorsForLevel(LearnerLevel.C1),
        LearnerLevel.C2 to colorsForLevel(LearnerLevel.C2),
    )
    // Neutral pill for words not in the DB or without a CEFR level yet
    val neutralBg = MaterialTheme.colorScheme.surfaceContainerHigh
    val neutralText = MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier = modifier.fillMaxSize()) {
        FlowRow(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(1.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            state.tokens.forEach { token ->
                if (token.text.contains('\n')) {
                    // Force a new row for each newline
                    repeat(token.text.count { it == '\n' }) {
                        Spacer(modifier = Modifier.fillMaxWidth())
                    }
                } else if (!token.isWord) {
                    // Spaces, punctuation, numbers — plain text, no pill
                    // Vertical padding matches pill padding so baselines stay aligned
                    Text(
                        text = token.text,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                } else {
                    // Every word token gets a pill
                    val (bgColor, textColor) = levelColors[token.level] ?: (neutralBg to neutralText)
                    val lemmaCapture = token.lemma
                    val isFavorite = lemmaCapture != null &&
                            state.favoriteLemmas.contains(lemmaCapture.lowercase())

                    val semanticDescription = buildString {
                        if (isFavorite) append("Saved, ")
                        append(token.text)
                        token.level?.let { append(", ${it.name} level") }
                    }
                    Box(
                        modifier = Modifier
                            .semantics { contentDescription = semanticDescription }
                            .clip(PillShape)
                            .background(bgColor)
                            .then(
                                if (lemmaCapture != null) Modifier.clickable(
                                    onClickLabel = "Look up",
                                    role = Role.Button
                                ) { onWordClick(language, lemmaCapture) }
                                else Modifier
                            )
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isFavorite) {
                                Text(
                                    text = "\u2665",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = HeartColor,
                                    modifier = Modifier.clearAndSetSemantics {}
                                )
                            }
                            Text(
                                text = token.text,
                                style = MaterialTheme.typography.bodyLarge,
                                color = textColor
                            )
                        }
                    }
                }
            }
        }

        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.md)
                    .semantics(mergeDescendants = false) {
                        contentDescription = "Level legend"
                    },
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LearnerLevel.entries.forEach { level ->
                    val (bg, fg) = levelColors[level] ?: return@forEach
                    Surface(
                        color = bg,
                        shape = MaterialTheme.shapes.extraSmall,
                        modifier = Modifier.clearAndSetSemantics {}
                    ) {
                        Text(
                            text = level.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = fg,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Surface(
                    color = neutralBg,
                    shape = MaterialTheme.shapes.extraSmall,
                    modifier = Modifier.clearAndSetSemantics {}
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "\u2665",
                            style = MaterialTheme.typography.labelSmall,
                            color = HeartColor
                        )
                        Text(
                            text = "Saved",
                            style = MaterialTheme.typography.labelSmall,
                            color = neutralText
                        )
                    }
                }
            }
        }
    }
}

// ──────────────────────────────── Previews ────────────────────────────────

@Preview
@Composable
private fun TextReaderInputEmptyPreview(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        TextReaderContent(
            state = TextReaderUiState(),
            language = Language.DUTCH
        )
    }
}

@Preview
@Composable
private fun TextReaderInputAnalyzingPreview(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        TextReaderContent(
            state = TextReaderUiState(isAnalyzing = true),
            language = Language.DUTCH
        )
    }
}

@Preview
@Composable
private fun TextReaderResultPreview(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        TextReaderContent(
            state = TextReaderUiState(
                tokens = listOf(
                    TextToken("De", lemma = "de", lemmaId = Uuid.parse("00000000-0000-0000-0000-000000000001"), level = LearnerLevel.A1, isWord = true),
                    TextToken(" ", isWord = false),
                    TextToken("kinderen", lemma = "kind", lemmaId = Uuid.parse("00000000-0000-0000-0000-000000000002"), level = LearnerLevel.A1, isWord = true),
                    TextToken(" ", isWord = false),
                    TextToken("liepen", lemma = "lopen", lemmaId = Uuid.parse("00000000-0000-0000-0000-000000000003"), level = LearnerLevel.A2, isWord = true),
                    TextToken(" ", isWord = false),
                    TextToken("door", lemma = "door", lemmaId = Uuid.parse("00000000-0000-0000-0000-000000000004"), level = LearnerLevel.A1, isWord = true),
                    TextToken(" ", isWord = false),
                    TextToken("het", lemma = "het", lemmaId = Uuid.parse("00000000-0000-0000-0000-000000000005"), level = LearnerLevel.A1, isWord = true),
                    TextToken(" ", isWord = false),
                    TextToken("park", lemma = "park", lemmaId = Uuid.parse("00000000-0000-0000-0000-000000000006"), level = LearnerLevel.B1, isWord = true),
                    TextToken(".", isWord = false),
                    TextToken("\n\n", isWord = false),
                    TextToken("onbekend", isWord = true),
                    TextToken(" ", isWord = false),
                    TextToken("beleggen", lemma = "beleggen", lemmaId = Uuid.parse("00000000-0000-0000-0000-000000000009"), level = null, isWord = true),
                    TextToken(" ", isWord = false),
                    TextToken("woorden", lemma = "woord", lemmaId = Uuid.parse("00000000-0000-0000-0000-000000000007"), level = LearnerLevel.C1, isWord = true),
                    TextToken(" ", isWord = false),
                    TextToken("complexe", lemma = "complex", lemmaId = Uuid.parse("00000000-0000-0000-0000-000000000008"), level = LearnerLevel.C2, isWord = true),
                ),
                favoriteLemmas = setOf("lopen")
            ),
            language = Language.DUTCH
        )
    }
}
