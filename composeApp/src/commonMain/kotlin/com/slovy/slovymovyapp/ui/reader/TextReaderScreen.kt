package com.slovy.slovymovyapp.ui.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewModelScope
import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.remote.DictionaryRepository
import com.slovy.slovymovyapp.data.remote.SenseFrequency
import com.slovy.slovymovyapp.ui.ThemePreviewProvider
import com.slovy.slovymovyapp.ui.ThemedPreview
import com.slovy.slovymovyapp.ui.theme.serifFontFamily
import com.slovy.slovymovyapp.ui.word.ClipboardVector
import com.slovy.slovymovyapp.ui.word.FavoriteAccentColor
import com.slovy.slovymovyapp.ui.word.colorsForFrequency
import com.slovy.slovymovyapp.util.stripAccents
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import slovymovyapp.composeapp.generated.resources.*
import kotlin.uuid.Uuid

private val TOKEN_REGEX = Regex("[\\p{L}\\p{M}\\-']+|\\s+|[^\\p{L}\\p{M}\\-'\\s]+")

// Corpus-frequency bands used to colour the passage. Derived from each lemma's Zipf
// frequency (~1 rare … ~7 extremely common). Thresholds are deliberately simple and
// meant to be tuned against real passages.
private const val HIGH_ZIPF_THRESHOLD = 4.3
private const val MID_ZIPF_THRESHOLD = 3.3

enum class FreqBand { HIGH, MID, LOW }

private fun bandForZipf(zipf: Double?): FreqBand? = when {
    zipf == null -> null
    zipf >= HIGH_ZIPF_THRESHOLD -> FreqBand.HIGH
    zipf >= MID_ZIPF_THRESHOLD -> FreqBand.MID
    else -> FreqBand.LOW
}

// Band labels reuse the app-wide frequency strings (sense_frequency_*).
@Composable
private fun bandLabel(band: FreqBand): String = stringResource(
    when (band) {
        FreqBand.HIGH -> Res.string.sense_frequency_high
        FreqBand.MID -> Res.string.sense_frequency_middle
        FreqBand.LOW -> Res.string.sense_frequency_low
    }
)

// Reuse the app-wide frequency chip palette (matches the design's --freq-* tokens).
// Returns (background, foreground); the passage uses the background as a soft word wash and
// the foreground for the legend swatches.
@Composable
private fun colorsForBand(band: FreqBand): Pair<Color, Color> = colorsForFrequency(
    when (band) {
        FreqBand.HIGH -> SenseFrequency.HIGH
        FreqBand.MID -> SenseFrequency.MIDDLE
        FreqBand.LOW -> SenseFrequency.LOW
    }
)

data class TextToken(
    val text: String,
    val lemma: String? = null,
    val lemmaId: Uuid? = null,
    val band: FreqBand? = null,
    val isWord: Boolean
)

data class TextReaderUiState(
    val tokens: List<TextToken> = emptyList(),
    val favoriteLemmas: Set<String> = emptySet(),
    val isAnalyzing: Boolean = false,
    val isError: Boolean = false
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
            state = state.copy(isAnalyzing = true, isError = false)
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
                            band = bandForZipf(result?.zipf)
                        )
                    }
                }
                val favoriteLemmas = repository.getFavoriteLemmasByLang(language)
                state = state.copy(tokens = resolvedTokens, favoriteLemmas = favoriteLemmas, isAnalyzing = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state = state.copy(isAnalyzing = false, isError = true)
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
    val result = mutableListOf<TextToken>()
    for (match in TOKEN_REGEX.findAll(text)) {
        val value = match.value
        if (!value.any { it.isLetter() }) {
            result.add(TextToken(text = value, isWord = false))
            continue
        }
        // Strip leading/trailing hyphens and apostrophes so that bullet-style
        // "-word" and quoted "'rijk'" are looked up without the surrounding symbol.
        // Mid-word occurrences (you're, arm-rijk) are preserved unchanged.
        val trimmedStart = value.trimStart('-', '\'')
        val leading = value.length - trimmedStart.length
        val core = trimmedStart.trimEnd('-', '\'')
        val trailing = trimmedStart.length - core.length
        if (leading > 0) result.add(TextToken(text = value.take(leading), isWord = false))
        result.add(TextToken(text = core, isWord = core.any { it.isLetter() }))
        if (trailing > 0) result.add(TextToken(text = trimmedStart.takeLast(trailing), isWord = false))
    }
    return result
}

// Groups tokens so that each word stays with its immediately surrounding punctuation,
// but groups never span more than one word. This prevents long runs of non-whitespace
// tokens (e.g. URLs) from forming a single oversized FlowRow item.
private fun groupTokensForRendering(tokens: List<TextToken>): List<List<TextToken>> {
    val result = mutableListOf<List<TextToken>>()
    val current = mutableListOf<TextToken>()
    for (token in tokens) {
        when {
            !token.isWord && token.text.all { it.isWhitespace() } -> {
                if (current.isNotEmpty()) { result.add(current.toList()); current.clear() }
                result.add(listOf(token))
            }
            token.isWord && current.any { it.isWord } -> {
                // Second word in the same run — flush and start a new group
                result.add(current.toList()); current.clear()
                current.add(token)
            }
            else -> current.add(token)
        }
    }
    if (current.isNotEmpty()) result.add(current.toList())
    return result
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextReaderScreen(
    viewModel: TextReaderViewModel,
    onWordClick: (language: Language, lemma: String) -> Unit,
    onBack: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    // Whether the clipboard holds text — checked here in the stateful layer (not in the
    // stateless content) and refreshed on resume. We only read the clipboard's *content*
    // when the user actually taps to paste, never eagerly.
    var clipboardHasText by remember { mutableStateOf(runCatching { clipboard.hasText() }.getOrDefault(true)) }
    LifecycleResumeEffect(Unit) {
        viewModel.refreshFavorites()
        clipboardHasText = runCatching { clipboard.hasText() }.getOrDefault(true)
        onPauseOrDispose {}
    }
    TextReaderContent(
        state = viewModel.state,
        scrollState = viewModel.scrollState,
        language = viewModel.language,
        clipboardHasText = clipboardHasText,
        onPaste = {
            val text = clipboard.getText()?.text.orEmpty()
            if (text.isNotBlank()) viewModel.analyzeText(text) else clipboardHasText = false
        },
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
    clipboardHasText: Boolean = true,
    onPaste: () -> Unit = {},
    onWordClick: (language: Language, lemma: String) -> Unit = { _, _ -> },
    onBack: () -> Unit = {}
) {
    val title = if (state.hasResults) {
        stringResource(Res.string.reader_title_with_lang, language.code.uppercase())
    } else {
        stringResource(Res.string.reader_title)
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = {
                    Text(
                        title,
                        style = TextStyle(
                            fontFamily = MaterialTheme.serifFontFamily,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.1.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.common_back)
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
                isError = state.isError,
                clipboardHasText = clipboardHasText,
                onPaste = onPaste,
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}

@Composable
private fun InputView(
    isAnalyzing: Boolean,
    isError: Boolean,
    clipboardHasText: Boolean,
    onPaste: () -> Unit,
    modifier: Modifier = Modifier
) {
    val canPaste = !isAnalyzing && clipboardHasText
    val contentAlpha = if (clipboardHasText || isAnalyzing) 1f else 0.45f

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        val dashColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .drawBehind {
                    val stroke = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(9.dp.toPx(), 7.dp.toPx()))
                    )
                    drawRoundRect(
                        color = dashColor,
                        cornerRadius = CornerRadius(24.dp.toPx()),
                        style = stroke
                    )
                }
                .clickable(
                    enabled = canPaste,
                    onClickLabel = stringResource(Res.string.reader_paste_from_clipboard),
                    role = Role.Button
                ) { onPaste() }
                .padding(horizontal = 22.dp, vertical = 24.dp)
                .semantics(mergeDescendants = true) {}
        ) {
            if (isAnalyzing) {
                val analyzingLabel = stringResource(Res.string.reader_analyzing)
                CircularProgressIndicator(
                    modifier = Modifier.semantics { contentDescription = analyzingLabel }
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .alpha(contentAlpha)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = ClipboardVector,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(30.dp)
                    )
                }
                Text(
                    text = stringResource(Res.string.reader_tap_to_paste),
                    style = TextStyle(
                        fontFamily = MaterialTheme.serifFontFamily,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = (-0.2).sp,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.alpha(contentAlpha)
                )
                Text(
                    text = if (clipboardHasText) {
                        stringResource(Res.string.reader_paste_blurb)
                    } else {
                        stringResource(Res.string.reader_clipboard_empty)
                    },
                    style = TextStyle(
                        fontFamily = MaterialTheme.serifFontFamily,
                        fontSize = 14.5.sp,
                        fontStyle = FontStyle.Italic,
                        lineHeight = 22.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 250.dp)
                )
            }
        }

        if (isError) {
            Text(
                text = stringResource(Res.string.reader_error_analyzing),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
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
    val passageStyle = TextStyle(
        fontFamily = MaterialTheme.serifFontFamily,
        fontSize = 18.5.sp,
        lineHeight = 35.5.sp,
        letterSpacing = 0.1.sp,
        color = MaterialTheme.colorScheme.onSurface
    )

    // Resolved once and shared by every word rather than per-word inside HighlightedWord.
    val lookUpLabel = stringResource(Res.string.reader_look_up)

    Column(modifier = modifier.fillMaxSize()) {
        FrequencyLegend()

        // Note: FlowRow composes every token eagerly (it is not lazy), so a very large paste
        // builds the whole passage up front. Fine for normal paragraphs; revisit if we ever
        // need to support book-length input.
        FlowRow(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(1.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val groups = remember(state.tokens) { groupTokensForRendering(state.tokens) }
            groups.forEach { group ->
                val single = group.singleOrNull()
                if (single != null && !single.isWord && single.text.all { it.isWhitespace() }) {
                    // Whitespace/newline group
                    if (single.text.contains('\n')) {
                        repeat(single.text.count { it == '\n' }) {
                            Spacer(modifier = Modifier.fillMaxWidth())
                        }
                    } else {
                        Text(text = single.text, style = passageStyle)
                    }
                } else if (group.none { it.isWord }) {
                    // Pure punctuation/number group
                    group.forEach { token -> Text(text = token.text, style = passageStyle) }
                } else {
                    // Word + adjacent punctuation group — wrap in Row so FlowRow never splits them
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        group.forEach { token ->
                            if (!token.isWord || token.lemma == null) {
                                // Inert punctuation, or a word not found in the dictionary — plain text.
                                Text(text = token.text, style = passageStyle)
                            } else {
                                HighlightedWord(
                                    token = token,
                                    style = passageStyle,
                                    isFavorite = state.favoriteLemmas.contains(token.lemma.lowercase()),
                                    lookUpLabel = lookUpLabel,
                                    onClick = { onWordClick(language, token.lemma) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A single tappable content word: soft frequency-band background wash + optional saved heart. */
@Composable
private fun HighlightedWord(
    token: TextToken,
    style: TextStyle,
    isFavorite: Boolean,
    lookUpLabel: String,
    onClick: () -> Unit
) {
    val bandBackground = token.band?.let { colorsForBand(it).first }
    val savedWord = if (isFavorite) stringResource(Res.string.reader_a11y_saved, token.text) else token.text
    val bandLabelText = token.band?.let { bandLabel(it) }
    val semanticDescription = listOfNotNull(savedWord, bandLabelText).joinToString(", ")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier
            .semantics { contentDescription = semanticDescription }
            .clip(RoundedCornerShape(4.dp))
            .then(if (bandBackground != null) Modifier.background(bandBackground) else Modifier)
            .clickable(onClickLabel = lookUpLabel, role = Role.Button) { onClick() }
            .padding(horizontal = 4.dp, vertical = 1.dp)
    ) {
        if (isFavorite) {
            Text(
                text = "♥",
                fontSize = 11.sp,
                color = FavoriteAccentColor,
                modifier = Modifier.clearAndSetSemantics {}
            )
        }
        Text(text = token.text, style = style)
    }
}

/** Slim static row decoding the passage's frequency colours. */
@Composable
private fun FrequencyLegend() {
    val legendLabel = stringResource(Res.string.reader_frequency_legend)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 11.dp)
            .semantics { contentDescription = legendLabel },
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(Res.string.reader_frequency).uppercase(),
            style = TextStyle(
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            modifier = Modifier.clearAndSetSemantics {}
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(13.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clearAndSetSemantics {}
        ) {
            FreqBand.entries.forEach { band ->
                val (_, fg) = colorsForBand(band)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(fg)
                    )
                    Text(
                        text = bandLabel(band),
                        style = TextStyle(
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.2.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
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
                    TextToken("Gisteren", lemma = "gisteren", lemmaId = Uuid.parse("00000000-0000-0000-0000-000000000001"), band = FreqBand.HIGH, isWord = true),
                    TextToken(" ", isWord = false),
                    TextToken("bleef", lemma = "blijven", lemmaId = Uuid.parse("00000000-0000-0000-0000-000000000002"), band = FreqBand.HIGH, isWord = true),
                    TextToken(" ik laat op om te ", isWord = false),
                    TextToken("lezen", lemma = "lezen", lemmaId = Uuid.parse("00000000-0000-0000-0000-000000000003"), band = FreqBand.HIGH, isWord = true),
                    TextToken(". De ", isWord = false),
                    TextToken("stad", lemma = "stad", lemmaId = Uuid.parse("00000000-0000-0000-0000-000000000004"), band = FreqBand.HIGH, isWord = true),
                    TextToken(" was ", isWord = false),
                    TextToken("stil", lemma = "stil", lemmaId = Uuid.parse("00000000-0000-0000-0000-000000000005"), band = FreqBand.MID, isWord = true),
                    TextToken(", en door het ", isWord = false),
                    TextToken("raam", lemma = "raam", lemmaId = Uuid.parse("00000000-0000-0000-0000-000000000006"), band = FreqBand.MID, isWord = true),
                    TextToken(" zag ik de eerste ", isWord = false),
                    TextToken("merkwaardige", lemma = "merkwaardig", lemmaId = Uuid.parse("00000000-0000-0000-0000-000000000007"), band = FreqBand.LOW, isWord = true),
                    TextToken(" ", isWord = false),
                    TextToken("gezelligheid", lemma = "gezelligheid", lemmaId = Uuid.parse("00000000-0000-0000-0000-000000000008"), band = FreqBand.LOW, isWord = true),
                    TextToken(". Sommige ", isWord = false),
                    TextToken("onbekend", isWord = true),
                    TextToken(" ", isWord = false),
                    TextToken("herinneringen", lemma = "herinnering", lemmaId = Uuid.parse("00000000-0000-0000-0000-000000000009"), band = FreqBand.MID, isWord = true),
                    TextToken(".", isWord = false),
                ),
                favoriteLemmas = setOf("blijven", "gezelligheid", "herinnering")
            ),
            language = Language.DUTCH
        )
    }
}
