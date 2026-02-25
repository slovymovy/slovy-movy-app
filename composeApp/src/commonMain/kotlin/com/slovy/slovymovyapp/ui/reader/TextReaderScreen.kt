package com.slovy.slovymovyapp.ui.reader

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.remote.DictionaryRepository
import com.slovy.slovymovyapp.data.remote.LearnerLevel
import com.slovy.slovymovyapp.ui.ThemedPreview
import com.slovy.slovymovyapp.ui.theme.AppSpacing
import com.slovy.slovymovyapp.util.stripAccents
import com.slovy.slovymovyapp.ui.ThemePreviewProvider
import com.slovy.slovymovyapp.ui.word.colorsForLevel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.uuid.Uuid

private val TOKEN_REGEX = Regex("[\\p{L}\\p{M}\\-']+|[^\\p{L}\\p{M}\\-']+")

data class TextToken(
    val text: String,
    val lemma: String? = null,
    val lemmaId: Uuid? = null,
    val level: LearnerLevel? = null,
    val isFavorite: Boolean = false,
    val isWord: Boolean
)

data class TextReaderUiState(
    val inputText: String = "",
    val tokens: List<TextToken> = emptyList(),
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
                            level = result?.level,
                            isFavorite = result?.isFavorite ?: false
                        )
                    }
                }
                state = state.copy(inputText = text, tokens = resolvedTokens, isAnalyzing = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state = state.copy(isAnalyzing = false, error = e.message ?: "Error analyzing text")
            }
        }
    }

    fun resetToInput() {
        state = state.copy(tokens = emptyList(), error = null)
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
    TextReaderContent(
        state = viewModel.state,
        scrollState = viewModel.scrollState,
        language = viewModel.language,
        onAnalyze = { viewModel.analyzeText(it) },
        onReset = { viewModel.resetToInput() },
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
    onReset: () -> Unit = {},
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
                        "Text reader",
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
                onReset = onReset,
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
                text = "Paste text to analyze word levels",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            if (isAnalyzing) {
                CircularProgressIndicator()
            } else {
                FilledTonalButton(
                    onClick = { onAnalyze(clipboardManager.getText()?.text ?: "") }
                ) {
                    Text("Paste")
                }
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

@Composable
private fun ResultView(
    state: TextReaderUiState,
    scrollState: ScrollState,
    language: Language,
    onWordClick: (language: Language, lemma: String) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Resolve level background colors inside the Composable (theme-aware)
    val levelBgColors: Map<LearnerLevel, Color> = mapOf(
        LearnerLevel.A1 to colorsForLevel(LearnerLevel.A1).first,
        LearnerLevel.A2 to colorsForLevel(LearnerLevel.A2).first,
        LearnerLevel.B1 to colorsForLevel(LearnerLevel.B1).first,
        LearnerLevel.B2 to colorsForLevel(LearnerLevel.B2).first,
        LearnerLevel.C1 to colorsForLevel(LearnerLevel.C1).first,
        LearnerLevel.C2 to colorsForLevel(LearnerLevel.C2).first
    )
    // Color for words found in the DB but not yet enriched with CEFR level data
    val unenrichedBg = MaterialTheme.colorScheme.surfaceContainerHighest
    val primaryColor = MaterialTheme.colorScheme.primary

    val annotatedText = buildAnnotatedString {
        state.tokens.forEach { token ->
            if (!token.isWord || token.lemma == null || token.lemmaId == null) {
                append(token.text)
            } else {
                // Known word: use level color if available, unenriched color otherwise
                val bgColor = token.level?.let { levelBgColors[it] } ?: unenrichedBg
                val spanStyle = if (token.isFavorite) {
                    SpanStyle(background = bgColor, color = primaryColor, fontWeight = FontWeight.SemiBold)
                } else {
                    SpanStyle(background = bgColor)
                }

                val lemmaCapture = token.lemma
                val link = LinkAnnotation.Clickable(
                    tag = "WORD_${token.lemmaId}",
                    linkInteractionListener = { onWordClick(language, lemmaCapture) }
                )
                withLink(link) {
                    withStyle(spanStyle) { append(token.text) }
                }
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = annotatedText,
            style = MaterialTheme.typography.bodyLarge,
            lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.6f,
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.md)
        )

        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.sm),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LearnerLevel.entries.forEach { level ->
                    val bg = levelBgColors[level] ?: return@forEach
                    Surface(
                        color = bg,
                        shape = MaterialTheme.shapes.extraSmall
                    ) {
                        Text(
                            text = level.name,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Surface(
                    color = unenrichedBg,
                    shape = MaterialTheme.shapes.extraSmall
                ) {
                    Text(
                        text = "?",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                TextButton(onClick = onReset) {
                    Text("Edit", style = MaterialTheme.typography.labelMedium)
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
                inputText = "De kinderen liepen door het park.",
                tokens = listOf(
                    TextToken("De", lemma = "de", lemmaId = Uuid.parse("00000000-0000-0000-0000-000000000001"), level = LearnerLevel.A1, isWord = true),
                    TextToken(" ", isWord = false),
                    TextToken("kinderen", lemma = "kind", lemmaId = Uuid.parse("00000000-0000-0000-0000-000000000002"), level = LearnerLevel.A1, isWord = true),
                    TextToken(" ", isWord = false),
                    TextToken("liepen", lemma = "lopen", lemmaId = Uuid.parse("00000000-0000-0000-0000-000000000003"), level = LearnerLevel.A2, isFavorite = true, isWord = true),
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
                )
            ),
            language = Language.DUTCH
        )
    }
}
