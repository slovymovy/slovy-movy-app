package com.slovy.slovymovyapp.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.favorites.Favorite
import com.slovy.slovymovyapp.data.favorites.FavoritesRepository
import com.slovy.slovymovyapp.data.remote.*
import com.slovy.slovymovyapp.ui.word.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.*
import kotlinx.datetime.TimeZone.Companion.currentSystemDefault
import kotlinx.datetime.format.char
import kotlin.time.Instant
import kotlin.uuid.Uuid

data class FavoriteSenseItem(
    val senseId: String,
    val targetLang: Language,
    val lemma: String,
    val createdAt: Long,
    val sense: LanguageCardResponseSense? = null,
    val pos: PartOfSpeech? = null,
    val expanded: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null
)

sealed interface FavoritesUiState {
    data object Loading : FavoritesUiState

    data class Content(
        val senses: List<FavoriteSenseItem>,
        val query: String = "",
        val hasAnyFavorites: Boolean = false
    ) : FavoritesUiState {
        val showNoResults: Boolean get() = senses.isEmpty() && query.isNotEmpty()
    }
}

class FavoritesViewModel(
    private val favoritesRepository: FavoritesRepository,
    private val dictionaryRepository: DictionaryRepository
) : ViewModel() {

    var state by mutableStateOf<FavoritesUiState>(FavoritesUiState.Loading)
        private set

    val scrollState = LazyListState()
    val snackbarHostState = SnackbarHostState()

    private val queryFlow = MutableStateFlow(QueryState("", Uuid.random()))

    private data class QueryState(val query: String, val force: Uuid)

    companion object {
        private const val QUERY_DEBOUNCE_MS = 200L
        private const val PREFETCH_LIMIT = 16
    }

    init {
        viewModelScope.launch {
            @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
            queryFlow
                .debounce(QUERY_DEBOUNCE_MS)
                .flatMapLatest { queryState ->
                    flow { emit(loadFavoritesInternal(queryState.query)) }
                        .flowOn(Dispatchers.Default)
                }
                .collect { }
        }
    }

    fun updateQuery(newQuery: String) {
        val content = state as? FavoritesUiState.Content ?: return
        state = content.copy(query = newQuery)
        queryFlow.value = QueryState(newQuery, Uuid.random())
    }

    fun loadFavorites() {
        val currentQuery = (state as? FavoritesUiState.Content)?.query ?: ""
        queryFlow.value = QueryState(currentQuery, Uuid.random())
    }

    private suspend fun loadFavoritesInternal(query: String) {
        val currentSenses = (state as? FavoritesUiState.Content)?.senses.orEmpty()
        val currentById = currentSenses.associateBy { it.senseId }

        val allFavorites = favoritesRepository.getAllGroupedByLangAndLemma()
        val hasAnyFavorites = allFavorites.isNotEmpty()

        val trimmedQuery = query.trim()
        val favorites = if (trimmedQuery.isEmpty()) {
            allFavorites
        } else {
            favoritesRepository.searchByLemma(trimmedQuery)
        }

        val senses = favorites.map { favorite ->
            val existing = currentById[favorite.senseId]
            val cached = dictionaryRepository.getCachedSense(favorite.senseId)
            buildSenseItem(favorite, cached, existing)
        }

        state = FavoritesUiState.Content(
            senses = senses,
            query = query,
            hasAnyFavorites = hasAnyFavorites
        )

        prefetchSenses(senses.take(PREFETCH_LIMIT))
    }

    private fun buildSenseItem(
        favorite: Favorite,
        cached: DictionaryRepository.SenseWithPos?,
        existing: FavoriteSenseItem?
    ): FavoriteSenseItem {
        return FavoriteSenseItem(
            senseId = favorite.senseId,
            targetLang = favorite.targetLang,
            lemma = favorite.lemma,
            createdAt = favorite.createdAt,
            sense = cached?.sense ?: existing?.sense,
            pos = cached?.pos ?: existing?.pos,
            expanded = existing?.expanded ?: false,
            loading = existing?.loading == true && cached == null,
            error = if (cached != null) null else existing?.error
        )
    }

    private fun updateSense(senseId: String, transform: (FavoriteSenseItem) -> FavoriteSenseItem) {
        val content = state as? FavoritesUiState.Content ?: return
        state = content.copy(
            senses = content.senses.map { if (it.senseId == senseId) transform(it) else it }
        )
    }

    private fun findSense(senseId: String): FavoriteSenseItem? {
        val content = state as? FavoritesUiState.Content ?: return null
        return content.senses.find { it.senseId == senseId }
    }

    fun toggleSense(senseId: String) {
        val item = findSense(senseId) ?: return
        val wasExpanded = item.expanded
        val shouldLoad = !wasExpanded && item.sense == null && !item.loading && item.error == null

        updateSense(senseId) { it.copy(expanded = !wasExpanded, error = null) }

        if (shouldLoad) {
            viewModelScope.launch { loadSense(item) }
        }
    }

    fun toggleFavorite(senseId: String) {
        val item = findSense(senseId) ?: return
        viewModelScope.launch {
            // Fetch the favorite to get its createdAt before removal
            val favorite = favoritesRepository.getOne(senseId, item.targetLang) ?: return@launch

            // Remove from repository and UI
            favoritesRepository.remove(senseId, favorite.targetLang)
            val content = state as? FavoritesUiState.Content ?: return@launch
            state = content.copy(senses = content.senses.filter { it.senseId != senseId })

            // Show snackbar with an undo option
            val result = snackbarHostState.showSnackbar(
                message = "Removed from favorites",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )

            if (result == SnackbarResult.ActionPerformed) {
                // Re-add with the original createdAt to preserve position
                favoritesRepository.add(senseId, favorite.targetLang, favorite.lemma, favorite.createdAt)
                loadFavorites()
            }
        }
    }

    private suspend fun loadSense(item: FavoriteSenseItem) {
        updateSense(item.senseId) { it.copy(loading = true, error = null) }
        try {
            val loaded = dictionaryRepository.getSenses(item.targetLang, item.lemma, setOf(item.senseId))
            val result = loaded[item.senseId]
            updateSense(item.senseId) {
                it.copy(
                    sense = result?.sense,
                    pos = result?.pos,
                    loading = false,
                    error = if (result == null) "Meaning not found" else null
                )
            }
        } catch (e: Throwable) {
            updateSense(item.senseId) {
                it.copy(loading = false, error = e.message ?: "Failed to load meaning")
            }
        }
    }

    private fun prefetchSenses(items: List<FavoriteSenseItem>) {
        val toLoad = items.filter { it.sense == null && !it.loading && it.error == null }
        toLoad.forEach { item ->
            viewModelScope.launch { loadSense(item) }
        }
    }

    fun prefetchVisibleRange(senses: List<FavoriteSenseItem>, range: IntRange) {
        if (senses.isEmpty() || range.isEmpty()) return
        val safeRange = range.first.coerceAtLeast(0)..minOf(range.last, senses.lastIndex)
        if (safeRange.isEmpty()) return
        prefetchSenses(senses.slice(safeRange).take(PREFETCH_LIMIT))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    viewModel: FavoritesViewModel,
    onNavigateToSearch: () -> Unit = {},
    wordDetailLabel: String? = null,
    onNavigateToLastWordDetail: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {}
) {
    val focusManager = LocalFocusManager.current

    LaunchedEffect(viewModel.scrollState.isScrollInProgress) {
        if (viewModel.scrollState.isScrollInProgress) {
            focusManager.clearFocus()
        }
    }

    FavoritesScreenContent(
        state = viewModel.state,
        scrollState = viewModel.scrollState,
        snackbarHostState = viewModel.snackbarHostState,
        onNavigateToSearch = onNavigateToSearch,
        onQueryChange = { viewModel.updateQuery(it) },
        onSenseToggle = { viewModel.toggleSense(it) },
        onFavoriteToggle = { viewModel.toggleFavorite(it) },
        wordDetailLabel = wordDetailLabel,
        onNavigateToLastWordDetail = onNavigateToLastWordDetail,
        onNavigateToSettings = onNavigateToSettings,
        onPrefetchVisible = { senses, range -> viewModel.prefetchVisibleRange(senses, range) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreenContent(
    state: FavoritesUiState,
    scrollState: LazyListState = LazyListState(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onNavigateToSearch: () -> Unit = {},
    onQueryChange: (String) -> Unit = {},
    onSenseToggle: (String) -> Unit = {},
    onFavoriteToggle: (String) -> Unit = {},
    wordDetailLabel: String? = null,
    onNavigateToLastWordDetail: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onPrefetchVisible: (List<FavoriteSenseItem>, IntRange) -> Unit = { _, _ -> }
) {
    val focusManager = LocalFocusManager.current
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    focusManager.clearFocus()
                })
            },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("My words") }
            )
        },
        bottomBar = {
            AppNavigationBar(
                currentScreen = AppScreen.FAVORITES,
                onNavigateToSearch = onNavigateToSearch,
                onNavigateToFavorites = {},
                onNavigateToWordDetail = onNavigateToLastWordDetail,
                wordDetailLabel = wordDetailLabel,
                onNavigateToSettings = onNavigateToSettings
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        when (state) {
            is FavoritesUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingPlaceholder(label = "Loading favorites...")
                }
            }

            is FavoritesUiState.Content -> {
                LaunchedEffect(state.senses, scrollState) {
                    snapshotFlow { scrollState.layoutInfo.visibleItemsInfo.map { it.index } }
                        .distinctUntilChanged()
                        .collectLatest { indices ->
                            if (indices.isEmpty()) return@collectLatest
                            val first = indices.minOrNull() ?: return@collectLatest
                            val last = indices.maxOrNull() ?: return@collectLatest
                            val lookahead = 5
                            val end = minOf(last + lookahead, state.senses.lastIndex)
                            onPrefetchVisible(state.senses, first..end)
                        }
                }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (state.hasAnyFavorites) {
                        com.slovy.slovymovyapp.ui.components.AppSearchBar(
                            query = state.query,
                            onQueryChange = onQueryChange,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            placeholder = "Type a word..."
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    when {
                        state.senses.isEmpty() && state.query.isEmpty() -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Surface(
                                    shape = MaterialTheme.shapes.extraLarge,
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                    modifier = Modifier.size(80.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Outlined.FavoriteBorder,
                                            contentDescription = null,
                                            modifier = Modifier.size(40.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "No Favorites Yet",
                                    style = MaterialTheme.typography.titleMedium,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Search a word and tap the heart icon",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 32.dp)
                                )
                            }
                        }

                        state.showNoResults -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No favorites match your search",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        else -> {
                            Column(modifier = Modifier.fillMaxSize()) {
                                val words = state.senses.distinctBy { it.lemma }
                                Text(
                                    text = "${state.senses.size} meaning${pluralEnding(state.senses)} (${words.size} word${
                                        pluralEnding(
                                            words
                                        )
                                    })",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp)
                                )

                                LazyColumn(
                                    state = scrollState,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    contentPadding = PaddingValues(bottom = 16.dp)
                                ) {
                                    items(state.senses, key = { it.senseId }, contentType = { "sense_card" }) { item ->
                                        FavoriteSenseCard(
                                            item = item,
                                            onToggle = { onSenseToggle(item.senseId) },
                                            onFavoriteToggle = { onFavoriteToggle(item.senseId) }
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
}

@Composable
private fun FavoriteSenseCard(
    item: FavoriteSenseItem,
    onToggle: () -> Unit,
    onFavoriteToggle: () -> Unit
) {
    val senseState = SenseUiState(
        senseId = item.senseId,
        expanded = item.expanded,
        examplesExpanded = false,
        languageExpanded = emptyMap(),
        favorite = true,
        showFavoriteToggle = item.expanded,
        pos = item.pos
    )
    SenseCard(
        data =  SenseCardData(
            senseId = item.senseId,
            lemma = item.lemma,
            showLemma = true,
            sense = item.sense,
            pos = item.pos,
            loading = item.loading,
            error = item.error,
            diagnosticInfoOnError = buildDiagnosticInfo(item.senseId, item.createdAt)
        ),
        state = senseState,
        onToggle = onToggle,
        onFavoriteToggle = onFavoriteToggle
    )
}

private val dateFormat = LocalDateTime.Format { date(LocalDate.Formats.ISO); char(' '); time(LocalTime.Formats.ISO) }

private fun buildDiagnosticInfo(senseId: String, createdAt: Long): String {
    val instant = Instant.fromEpochSeconds(createdAt)
    val timeZone = currentSystemDefault()
    val localDateTime: LocalDateTime = instant.toLocalDateTime(timeZone)
    val dateStr = localDateTime.format(dateFormat)
    return "ID: $senseId\nAdded: $dateStr"
}

// Preview helpers
private fun createMockSense(
    id: String,
    definition: String,
    level: LearnerLevel = LearnerLevel.B1,
    frequency: SenseFrequency = SenseFrequency.MIDDLE,
    examples: List<LanguageCardExample> = emptyList(),
    synonyms: List<String> = emptyList(),
    antonyms: List<String> = emptyList(),
): LanguageCardResponseSense {
    return LanguageCardResponseSense(
        senseId = id,
        senseDefinition = definition,
        learnerLevel = level,
        frequency = frequency,
        semanticGroupId = "group1",
        nameType = null,
        examples = examples,
        synonyms = synonyms,
        antonyms = antonyms,
        commonPhrases = emptyList(),
        traits = emptyList(),
        targetLangDefinitions = mapOf(Language.ENGLISH to definition),
    )
}

private fun createSenseItem(
    senseId: String,
    lemma: String,
    targetLang: Language = Language.ENGLISH,
    createdAt: Long = 1704067200L,
    sense: LanguageCardResponseSense? = null,
    pos: PartOfSpeech? = null,
    expanded: Boolean = false,
    loading: Boolean = false,
    error: String? = null
) = FavoriteSenseItem(
    senseId = senseId,
    targetLang = targetLang,
    lemma = lemma,
    createdAt = createdAt,
    sense = sense,
    pos = pos,
    expanded = expanded,
    loading = loading,
    error = error
)

@androidx.compose.ui.tooling.preview.Preview
@Composable
fun PreviewFavoritesScreenLoading(
    @androidx.compose.ui.tooling.preview.PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        FavoritesScreenContent(state = FavoritesUiState.Loading)
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
fun PreviewFavoritesScreenEmpty(
    @androidx.compose.ui.tooling.preview.PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        FavoritesScreenContent(
            state = FavoritesUiState.Content(senses = emptyList(), hasAnyFavorites = false)
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
fun PreviewFavoritesScreenCollapsed(
    @androidx.compose.ui.tooling.preview.PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        val state = FavoritesUiState.Content(
            senses = listOf(
                createSenseItem(
                    senseId = "run-1",
                    lemma = "run",
                    sense = createMockSense("run-1", "to move swiftly on foot", LearnerLevel.A1, SenseFrequency.HIGH),
                    pos = PartOfSpeech.VERB
                ),
                createSenseItem(
                    senseId = "book-1",
                    lemma = "book",
                    sense = createMockSense(
                        "book-1",
                        "a written or printed work",
                        LearnerLevel.A1,
                        SenseFrequency.HIGH
                    ),
                    pos = PartOfSpeech.NOUN
                )
            ),
            hasAnyFavorites = true
        )
        FavoritesScreenContent(state = state)
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
fun PreviewFavoritesScreenExpanded(
    @androidx.compose.ui.tooling.preview.PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        val state = FavoritesUiState.Content(
            senses = listOf(
                createSenseItem(
                    senseId = "happy-1",
                    lemma = "happy",
                    sense = createMockSense(
                        "happy-1",
                        "feeling or showing pleasure",
                        LearnerLevel.A2,
                        SenseFrequency.HIGH,
                        examples = listOf(
                            LanguageCardExample(
                                "I'm happy",
                                mapOf(Language.POLISH to "Jestem szczęśliwy")
                            )
                        )
                    ),
                    pos = PartOfSpeech.ADJECTIVE,
                    expanded = true
                )
            ),
            hasAnyFavorites = true
        )
        FavoritesScreenContent(state = state)
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
fun PreviewFavoritesScreenWithSearch(
    @androidx.compose.ui.tooling.preview.PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        val state = FavoritesUiState.Content(
            senses = listOf(
                createSenseItem(
                    senseId = "run-1",
                    lemma = "run",
                    sense = createMockSense("run-1", "to move swiftly on foot"),
                    pos = PartOfSpeech.VERB
                )
            ),
            query = "run",
            hasAnyFavorites = true
        )
        FavoritesScreenContent(state = state)
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
fun PreviewFavoritesScreenNoResults(
    @androidx.compose.ui.tooling.preview.PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        FavoritesScreenContent(
            state = FavoritesUiState.Content(senses = emptyList(), query = "xyz", hasAnyFavorites = true)
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
fun PreviewFavoritesScreenLoadingAndError(
    @androidx.compose.ui.tooling.preview.PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        val state = FavoritesUiState.Content(
            senses = listOf(
                createSenseItem(
                    senseId = "ready-1",
                    lemma = "ready",
                    sense = createMockSense("ready-1", "completely prepared"),
                    pos = PartOfSpeech.ADJECTIVE
                ),
                createSenseItem(
                    senseId = "ready-2",
                    lemma = "ready",
                    loading = true
                ),
                createSenseItem(
                    senseId = "ready-3",
                    lemma = "ready",
                    error = "Failed to load meaning"
                )
            ),
            hasAnyFavorites = true
        )
        FavoritesScreenContent(state = state)
    }
}
