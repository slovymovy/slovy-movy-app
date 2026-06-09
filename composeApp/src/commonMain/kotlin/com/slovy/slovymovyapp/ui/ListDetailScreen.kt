package com.slovy.slovymovyapp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.favorites.FavoritesRepository
import com.slovy.slovymovyapp.data.lists.ListsService
import com.slovy.slovymovyapp.data.lists.WordList
import com.slovy.slovymovyapp.data.remote.DictionaryRepository
import com.slovy.slovymovyapp.data.remote.LanguageCardResponseSense
import com.slovy.slovymovyapp.data.remote.LearnerLevel
import com.slovy.slovymovyapp.data.remote.PartOfSpeech
import com.slovy.slovymovyapp.data.remote.RelatedWord
import com.slovy.slovymovyapp.data.remote.SenseFrequency
import com.slovy.slovymovyapp.i18n.UiText
import com.slovy.slovymovyapp.i18n.resolve
import com.slovy.slovymovyapp.ui.theme.LocalIsDarkTheme
import com.slovy.slovymovyapp.ui.theme.serifFontFamily
import com.slovy.slovymovyapp.ui.word.SenseCard
import com.slovy.slovymovyapp.ui.word.SenseCardData
import com.slovy.slovymovyapp.ui.word.SenseUiState
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import slovymovyapp.composeapp.generated.resources.Res
import slovymovyapp.composeapp.generated.resources.common_retry
import slovymovyapp.composeapp.generated.resources.favorites_error_meaning_not_found
import slovymovyapp.composeapp.generated.resources.list_detail_empty
import slovymovyapp.composeapp.generated.resources.list_detail_load_error
import slovymovyapp.composeapp.generated.resources.search_list_word_count

data class ListWordItem(
    val senseId: String,
    val lemma: String,
    val definition: String? = null,
    val learnerLevel: LearnerLevel? = null,
    val frequency: SenseFrequency? = null,
    val sense: LanguageCardResponseSense? = null,
    val relatedWords: Map<String, RelatedWord> = emptyMap(),
    val pos: PartOfSpeech? = null,
    val expanded: Boolean = false,
    val loading: Boolean = false,
    val error: UiText? = null,
    val isFavorited: Boolean = false,
)

data class ListDetailUiState(
    val items: List<ListWordItem> = emptyList(),
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val favoriteLemmas: Set<String> = emptySet(),
)

class ListDetailViewModel(
    val listId: String,
    val language: Language,
    private val repository: DictionaryRepository,
    private val favoritesRepository: FavoritesRepository,
    private val listsService: ListsService,
    private val onFavoriteChanged: (added: Boolean) -> Unit,
) : ViewModel() {
    var list by mutableStateOf<WordList?>(null)
        private set
    var state by mutableStateOf(ListDetailUiState())
        private set
    val scrollState = LazyListState()
    private var loadJob: Job? = null

    init {
        load()
    }

    fun retry() {
        load()
    }

    private fun load() {
        loadJob?.cancel()
        state = state.copy(isLoading = true, loadFailed = false)
        loadJob = viewModelScope.launch {
            // Lists are served from the DB; fall back to a server sync when the id is
            // not stored yet (e.g. the stored feed predates this list).
            val resolvedList = listsService.getList(language, listId)
                ?: run {
                    listsService.sync(language)
                    listsService.getList(language, listId)
                }
            if (resolvedList == null) {
                state = state.copy(isLoading = false, loadFailed = true)
                return@launch
            }
            list = resolvedList
            val senses = repository.getListSenses(language, resolvedList.senseIds)
            val allFavorites = favoritesRepository.getAll().filter { it.language == language }
            val favoritedIds = allFavorites.map { it.senseId }.toSet()
            val favoriteLemmas = allFavorites.map { it.lemma }.toSet()
            val items = senses.map { sense ->
                ListWordItem(
                    senseId = sense.senseId,
                    lemma = sense.lemma,
                    definition = sense.definition,
                    learnerLevel = sense.learnerLevel,
                    frequency = sense.frequency,
                    isFavorited = sense.senseId in favoritedIds,
                )
            }
            state = ListDetailUiState(
                items = items,
                isLoading = false,
                favoriteLemmas = favoriteLemmas,
            )
        }
    }

    private fun updateItem(senseId: String, transform: (ListWordItem) -> ListWordItem) {
        state = state.copy(items = state.items.map { if (it.senseId == senseId) transform(it) else it })
    }

    private fun findItem(senseId: String): ListWordItem? = state.items.find { it.senseId == senseId }

    fun toggleSense(senseId: String) {
        val item = findItem(senseId) ?: return
        val wasExpanded = item.expanded
        val shouldLoad = !wasExpanded && item.sense == null && !item.loading && item.error == null
        updateItem(senseId) { it.copy(expanded = !wasExpanded, error = null) }
        if (shouldLoad) {
            viewModelScope.launch { loadSense(item) }
        }
    }

    private suspend fun loadSense(item: ListWordItem) {
        if (item.sense != null || item.loading) return
        updateItem(item.senseId) { it.copy(loading = true, error = null) }
        try {
            val loaded = repository.getSenses(language, item.lemma, setOf(item.senseId))
            val result = loaded[item.senseId]
            val senseWithPos = result?.sense
            val error = if (senseWithPos == null) {
                result?.missingReason?.toFavoriteSenseLoadError(language)
                    ?: UiText.Resource(Res.string.favorites_error_meaning_not_found)
            } else null
            updateItem(item.senseId) {
                it.copy(
                    sense = senseWithPos?.sense,
                    relatedWords = senseWithPos?.relatedWords ?: it.relatedWords,
                    pos = senseWithPos?.pos,
                    loading = false,
                    error = error,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            updateItem(item.senseId) {
                it.copy(
                    loading = false,
                    error = e.message?.let(UiText::Plain)
                        ?: UiText.Resource(Res.string.favorites_error_meaning_not_found),
                )
            }
        }
    }

    fun toggleFavorite(senseId: String) {
        val item = findItem(senseId) ?: return
        viewModelScope.launch {
            val added = if (item.isFavorited) {
                favoritesRepository.remove(senseId, language)
                updateItem(senseId) { it.copy(isFavorited = false) }
                state = state.copy(favoriteLemmas = state.favoriteLemmas - item.lemma)
                false
            } else {
                favoritesRepository.add(senseId, language, item.lemma)
                updateItem(senseId) { it.copy(isFavorited = true) }
                state = state.copy(favoriteLemmas = state.favoriteLemmas + item.lemma)
                true
            }
            onFavoriteChanged(added)
        }
    }

    fun reloadFavorites() {
        viewModelScope.launch {
            val allFavorites = favoritesRepository.getAll().filter { it.language == language }
            val favoritedIds = allFavorites.map { it.senseId }.toSet()
            val favoriteLemmas = allFavorites.map { it.lemma }.toSet()
            state = state.copy(
                items = state.items.map { it.copy(isFavorited = it.senseId in favoritedIds) },
                favoriteLemmas = favoriteLemmas,
            )
        }
    }
}

@Composable
fun ListDetailScreen(
    viewModel: ListDetailViewModel,
    onBack: () -> Unit,
    onNavigateToWordDetail: (language: Language, lemma: String, senseId: String?) -> Unit,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.reloadFavorites()
        }
    }
    val list = viewModel.list
    when {
        list != null -> ListDetailContent(
            list = list,
            state = viewModel.state,
            scrollState = viewModel.scrollState,
            onBack = onBack,
            onSenseToggle = viewModel::toggleSense,
            onFavoriteToggle = viewModel::toggleFavorite,
            onNavigateToWordDetail = { lemma, senseId ->
                onNavigateToWordDetail(viewModel.language, lemma, senseId)
            },
        )

        viewModel.state.isLoading -> ListDetailLoadingScreen(onBack = onBack)

        else -> ListDetailErrorScreen(onBack = onBack, onRetry = viewModel::retry)
    }
}

@Composable
private fun ListDetailLoadingScreen(onBack: () -> Unit) {
    ListDetailScaffold(onBack = onBack) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator() }
    }
}

@Composable
private fun ListDetailErrorScreen(onBack: () -> Unit, onRetry: () -> Unit) {
    ListDetailScaffold(onBack = onBack) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(Res.string.list_detail_load_error),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onRetry) {
                Text(stringResource(Res.string.common_retry))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListDetailScaffold(
    onBack: () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
        content = content,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListDetailContent(
    list: WordList,
    state: ListDetailUiState,
    scrollState: LazyListState = LazyListState(),
    onBack: () -> Unit = {},
    onSenseToggle: (String) -> Unit = {},
    onFavoriteToggle: (String) -> Unit = {},
    onNavigateToWordDetail: (lemma: String, senseId: String?) -> Unit = { _, _ -> },
) {
    val isDark = LocalIsDarkTheme.current
    val localeCode = androidx.compose.ui.text.intl.Locale.current.language
    val title = list.title[localeCode] ?: list.title["en"] ?: list.id
    val subtitle = list.subtitle[localeCode] ?: list.subtitle["en"] ?: ""
    // Show the count of words actually available locally once they are loaded; sense ids
    // missing from the downloaded dictionary are dropped by getListSenses.
    val wordCount = if (state.isLoading) list.senseIds.size else state.items.size
    val (bgColor, fgColor) = vibrantColorsForList(list.id, isDark)

    ListDetailScaffold(onBack = onBack) { innerPadding ->
        LazyColumn(
            state = scrollState,
            contentPadding = PaddingValues(
                top = 0.dp,
                bottom = innerPadding.calculateBottomPadding() + 16.dp,
                start = 16.dp,
                end = 16.dp,
            ),
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                ListDetailHeader(
                    title = title,
                    subtitle = subtitle,
                    wordCount = wordCount,
                    bgColor = bgColor,
                    fgColor = fgColor,
                    isDark = isDark,
                )
            }

            if (state.isLoading) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else if (state.items.isEmpty()) {
                item {
                    Text(
                        text = stringResource(Res.string.list_detail_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 32.dp, start = 16.dp, end = 16.dp),
                    )
                }
            } else {
                items(state.items, key = { it.senseId }) { item ->
                    ListWordSenseCard(
                        item = item,
                        favoriteLemmas = state.favoriteLemmas,
                        onToggle = { onSenseToggle(item.senseId) },
                        onFavoriteToggle = { onFavoriteToggle(item.senseId) },
                        onViewFullDetails = { onNavigateToWordDetail(item.lemma, item.senseId) },
                        onWordClick = { word -> onNavigateToWordDetail(word, null) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ListWordSenseCard(
    item: ListWordItem,
    favoriteLemmas: Set<String>,
    onToggle: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onViewFullDetails: () -> Unit,
    onWordClick: (String) -> Unit,
) {
    SenseCard(
        data = SenseCardData(
            senseId = item.senseId,
            lemma = item.lemma,
            showLemma = true,
            sense = item.sense,
            pos = item.pos,
            loading = item.loading,
            error = item.error?.resolve(),
            collapsedDefinition = item.definition,
            collapsedLearnerLevel = item.learnerLevel,
            collapsedFrequency = item.frequency,
        ),
        state = SenseUiState(
            senseId = item.senseId,
            expanded = item.expanded,
            examplesExpanded = false,
            languageExpanded = emptyMap(),
            favorite = item.isFavorited,
            showFavoriteToggle = true,
            pos = item.pos,
        ),
        onToggle = onToggle,
        onFavoriteToggle = onFavoriteToggle,
        onViewFullDetails = onViewFullDetails,
        relatedWords = item.relatedWords,
        onWordClick = onWordClick,
        favoriteLemmas = favoriteLemmas,
    )
}

@Composable
private fun ListDetailHeader(
    title: String,
    subtitle: String,
    wordCount: Int,
    bgColor: Color,
    fgColor: Color,
    isDark: Boolean,
) {
    val shadowColor = if (isDark) Color.Black.copy(alpha = 0.40f) else Color.Black.copy(alpha = 0.12f)
    val shadowElevation = if (isDark) 3.dp else 4.dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Emblem — same vibrant color as the feed card, with matching shadow
        Box(
            modifier = Modifier
                .size(64.dp)
                .shadow(
                    elevation = shadowElevation,
                    shape = RoundedCornerShape(16.dp),
                    ambientColor = shadowColor,
                    spotColor = shadowColor,
                )
                .background(bgColor),
            contentAlignment = Alignment.Center,
        ) {
            WordListEmblem(fgColor = fgColor, modifier = Modifier.size(48.dp).alpha(0.82f))
        }

        Spacer(Modifier.height(6.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontFamily = MaterialTheme.serifFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 24.sp,
                letterSpacing = (-0.3).sp,
            ),
        )
        if (subtitle.isNotEmpty()) {
            Text(
                text = subtitle,
                fontSize = 13.5.sp,
                fontStyle = FontStyle.Italic,
                fontFamily = MaterialTheme.serifFontFamily,
                lineHeight = (13.5f * 1.38f).sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = pluralStringResource(Res.plurals.search_list_word_count, wordCount, wordCount),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            modifier = Modifier.padding(top = 2.dp),
        )

        Spacer(Modifier.height(12.dp))
        HorizontalDivider(
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        )
    }
}

// Hand-drawn stack-of-rows emblem shared by the feed tiles and the detail header.
@Composable
internal fun WordListEmblem(fgColor: Color, modifier: Modifier) {
    Canvas(modifier = modifier) {
        val scale = size.width / 96f
        val strokeWidth = 2f * scale
        listOf(
            Triple(16f, 62f, 64f),
            Triple(16f, 44f, 42f),
            Triple(16f, 26f, 26f),
        ).forEach { (x, y, w) ->
            drawRoundRect(
                color = fgColor,
                topLeft = Offset(x * scale, y * scale),
                size = Size(w * scale, 14f * scale),
                cornerRadius = CornerRadius(7f * scale),
                style = Stroke(width = strokeWidth),
            )
        }
    }
}

private fun previewWordList() = WordList(
    id = "nl_a1_basic",
    title = mapOf("en" to "500 first Dutch words"),
    subtitle = mapOf("en" to "This is where your journey begins"),
    labels = mapOf("en" to listOf("A1", "Basic")),
    senseIds = List(3) { it.toString() },
)

@Preview
@Composable
private fun ListDetailPreviewContent(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        ListDetailContent(
            list = previewWordList(),
            state = ListDetailUiState(
                items = listOf(
                    ListWordItem(
                        senseId = "1",
                        lemma = "huis",
                        definition = "a building where people live",
                        learnerLevel = LearnerLevel.A1,
                        frequency = SenseFrequency.HIGH,
                        isFavorited = true,
                    ),
                    ListWordItem(
                        senseId = "2",
                        lemma = "fiets",
                        definition = "a vehicle with two wheels that you ride by pushing pedals",
                        learnerLevel = LearnerLevel.A1,
                        frequency = SenseFrequency.MIDDLE,
                    ),
                    ListWordItem(
                        senseId = "3",
                        lemma = "gezellig",
                        expanded = true,
                        loading = true,
                    ),
                ),
                isLoading = false,
                favoriteLemmas = setOf("huis"),
            ),
        )
    }
}

@Preview
@Composable
private fun ListDetailPreviewLoading(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        ListDetailContent(
            list = previewWordList(),
            state = ListDetailUiState(isLoading = true),
        )
    }
}

@Preview
@Composable
private fun ListDetailPreviewEmpty(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        ListDetailContent(
            list = previewWordList(),
            state = ListDetailUiState(isLoading = false),
        )
    }
}

@Preview
@Composable
private fun ListDetailPreviewError(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        ListDetailErrorScreen(onBack = {}, onRetry = {})
    }
}
