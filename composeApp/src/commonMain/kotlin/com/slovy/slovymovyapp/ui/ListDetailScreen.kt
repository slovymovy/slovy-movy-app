package com.slovy.slovymovyapp.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.slovy.slovymovyapp.analytics.Analytics
import com.slovy.slovymovyapp.analytics.AnalyticsEvent
import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.favorites.FavoritesRepository
import com.slovy.slovymovyapp.data.favorites.NewFavorite
import com.slovy.slovymovyapp.data.lists.ListsService
import com.slovy.slovymovyapp.data.lists.WordList
import com.slovy.slovymovyapp.data.lists.WordListSense
import com.slovy.slovymovyapp.data.remote.DictionaryRepository
import com.slovy.slovymovyapp.data.remote.LanguageCardResponseSense
import com.slovy.slovymovyapp.data.remote.LearnerLevel
import com.slovy.slovymovyapp.data.remote.LemmaRecovery
import com.slovy.slovymovyapp.data.remote.PartOfSpeech
import com.slovy.slovymovyapp.data.remote.RelatedWord
import com.slovy.slovymovyapp.data.remote.SenseFrequency
import com.slovy.slovymovyapp.i18n.UiText
import com.slovy.slovymovyapp.i18n.resolve
import com.slovy.slovymovyapp.speech.LemmaAudioControl
import com.slovy.slovymovyapp.speech.RowAudioActions
import com.slovy.slovymovyapp.speech.RowAudioController
import com.slovy.slovymovyapp.speech.RowAudioUiState
import com.slovy.slovymovyapp.speech.SpeechPlayer
import com.slovy.slovymovyapp.speech.VoiceFilterHelper
import com.slovy.slovymovyapp.ui.theme.LocalIsDarkTheme
import com.slovy.slovymovyapp.ui.theme.serifFontFamily
import com.slovy.slovymovyapp.ui.word.ChapterRule
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
import slovymovyapp.composeapp.generated.resources.list_detail_add_all
import slovymovyapp.composeapp.generated.resources.list_detail_empty
import slovymovyapp.composeapp.generated.resources.list_detail_in_my_words_count
import slovymovyapp.composeapp.generated.resources.list_detail_load_error
import slovymovyapp.composeapp.generated.resources.list_detail_remove_all
import slovymovyapp.composeapp.generated.resources.search_list_word_count

data class ListWordItem(
    val senseId: String,
    val lemma: String,
    val definition: String? = null,
    val learnerLevel: LearnerLevel? = null,
    val frequency: SenseFrequency? = null,
    override val sense: LanguageCardResponseSense? = null,
    val relatedWords: Map<String, RelatedWord> = emptyMap(),
    val pos: PartOfSpeech? = null,
    val expanded: Boolean = false,
    override val loading: Boolean = false,
    override val error: UiText? = null,
    val isFavorited: Boolean = false,
) : PrefetchableSenseItem

data class ListDetailUiState(
    val items: List<ListWordItem> = emptyList(),
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val favoriteLemmas: Set<String> = emptySet(),
    val bulkActionInProgress: Boolean = false,
) {
    val inMyWordsCount: Int get() = items.count { it.isFavorited }
    val allInMyWords: Boolean get() = items.isNotEmpty() && items.all { it.isFavorited }
}

class ListDetailViewModel(
    val listId: String,
    val language: Language,
    private val repository: DictionaryRepository,
    private val favoritesRepository: FavoritesRepository,
    private val listsService: ListsService,
    private val lemmaRecovery: LemmaRecovery,
    speechPlayer: SpeechPlayer,
    voiceFilterHelper: VoiceFilterHelper,
    private val onFavoriteChanged: (added: Boolean) -> Unit,
) : ViewModel() {
    var list by mutableStateOf<WordList?>(null)
        private set
    var state by mutableStateOf(ListDetailUiState())
        private set
    val scrollState = LazyListState()
    private var loadJob: Job? = null
    private val prefetcher = SensePrefetcher(viewModelScope, ::loadSense)

    val rowAudio = RowAudioController(
        speechPlayer = speechPlayer,
        voiceFilterHelper = voiceFilterHelper,
        scope = viewModelScope,
        analyticsSource = "list_detail",
    )

    val rowAudioActions = RowAudioActions(
        onToggle = ::toggleAudio,
        onOpenVoiceSettings = rowAudio::openVoiceSettings,
        onDismissVoiceSetup = rowAudio::dismissVoiceSetup,
        onDismissVoiceSetupAndPlay = rowAudio::dismissVoiceSetupAndPlay,
    )

    fun toggleAudio(senseId: String) {
        val item = findItem(senseId) ?: return
        rowAudio.toggle(senseId, item.lemma, language)
    }

    override fun onCleared() {
        super.onCleared()
        rowAudio.dispose()
    }

    // SenseIds added by "Add all" in this session; "Remove all" only removes these so
    // favorites that existed before the bulk add survive. When empty (e.g. the screen
    // was opened with everything already favorited), "Remove all" removes all items.
    private var sessionBulkAddedSenseIds: Set<String> = emptySet()

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
            val resolved = repository.getListSenses(language, resolvedList.senseIds)
                .associateBy { it.senseId }
            val allFavorites = favoritesRepository.getAll().filter { it.language == language }
            val favoritedIds = allFavorites.map { it.senseId }.toSet()
            val favoriteLemmas = allFavorites.map { it.lemma }.toSet()
            // Build an item for every list sense in server order. Senses present in the
            // local dictionary render immediately; senses missing from it become loading
            // placeholders carrying the bundle lemma so they can be fetched + favorited.
            // A sense missing locally with a blank lemma (legacy senseIds-only bundle or a
            // pre-lemma cached row) is skipped: it can be neither fetched nor turned into a
            // usable favorite (all fetch/recovery/intake paths are keyed by lemma), so it must
            // not reach "Add all". This matches the pre-lemma behavior of dropping such senses.
            val items = resolvedList.senses.mapNotNull { listSense ->
                val sense = resolved[listSense.senseId]
                when {
                    sense != null -> {
                        // Seed from cache so already-loaded senses render their translation
                        // immediately, mirroring FavoritesViewModel.buildSenseItem.
                        val cached = repository.getCachedSense(sense.senseId)
                        ListWordItem(
                            senseId = sense.senseId,
                            lemma = sense.lemma,
                            definition = sense.definition,
                            learnerLevel = sense.learnerLevel,
                            frequency = sense.frequency,
                            sense = cached?.sense,
                            relatedWords = cached?.relatedWords ?: emptyMap(),
                            pos = cached?.pos,
                            isFavorited = sense.senseId in favoritedIds,
                        )
                    }

                    listSense.lemma.isNotBlank() -> ListWordItem(
                        senseId = listSense.senseId,
                        lemma = listSense.lemma,
                        isFavorited = listSense.senseId in favoritedIds,
                        loading = true,
                    )

                    else -> null
                }
            }
            state = ListDetailUiState(
                items = items,
                isLoading = false,
                favoriteLemmas = favoriteLemmas,
            )
            // Prefetch the first screenful so translations show without expanding,
            // like the favorites list. The rest is prefetched on scroll.
            prefetcher.prefetchHead(items)
            // Fetch the senses missing from the local dictionary from the server in the
            // background; resolved items are already on screen, so this never blocks the UI.
            // Only senses with a usable lemma are fetchable.
            val missing = resolvedList.senses.filter { it.senseId !in resolved && it.lemma.isNotBlank() }
            if (missing.isNotEmpty()) fetchMissingSenses(missing)
        }
    }

    private suspend fun fetchMissingSenses(missing: List<WordListSense>) {
        // Hand the missing senses to LemmaRecovery, which fetches (server stream + ingest, with
        // retry/backoff, parallelism cap and cancellation) and resolves each one, calling back as
        // results arrive. WordListSense is itself a RecoverableSense, so no mapping is needed; the
        // screen only maps each outcome to its per-item UI state.
        lemmaRecovery.recoverSenses(missing) { recovered ->
            val lookup = recovered.result
            val senseWithPos = lookup?.sense
            val error = when {
                senseWithPos != null -> null
                lookup?.missingReason != null -> lookup.missingReason.toFavoriteSenseLoadError(language)
                else -> recovered.error?.message?.let(UiText::Plain)
                    ?: UiText.Resource(Res.string.favorites_error_meaning_not_found)
            }
            updateItem(recovered.senseId) {
                it.copy(
                    sense = senseWithPos?.sense,
                    relatedWords = senseWithPos?.relatedWords ?: it.relatedWords,
                    pos = senseWithPos?.pos,
                    loading = false,
                    error = error,
                )
            }
        }
    }

    fun prefetchVisibleRange(items: List<ListWordItem>, range: IntRange) {
        prefetcher.prefetchVisibleRange(items, range)
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
            refreshFavoriteFlags()
        }
    }

    fun addAllToMyWords() {
        if (state.isLoading || state.bulkActionInProgress) return
        val toAdd = state.items.filter { !it.isFavorited }
        if (toAdd.isEmpty()) return
        Analytics.logEvent(
            AnalyticsEvent.LIST_ADD_ALL,
            mapOf("lang" to language.code, "list_id" to listId)
        )
        state = state.copy(bulkActionInProgress = true)
        viewModelScope.launch {
            favoritesRepository.addAll(language, toAdd.map { NewFavorite(senseId = it.senseId, lemma = it.lemma) })
            sessionBulkAddedSenseIds = sessionBulkAddedSenseIds + toAdd.map { it.senseId }
            refreshFavoriteFlags()
            state = state.copy(bulkActionInProgress = false)
            onFavoriteChanged(true)
        }
    }

    fun removeAllFromMyWords() {
        if (state.isLoading || state.bulkActionInProgress) return
        val favorited = state.items.filter { it.isFavorited }
        val targets = if (sessionBulkAddedSenseIds.isNotEmpty()) {
            favorited.filter { it.senseId in sessionBulkAddedSenseIds }
        } else {
            favorited
        }
        if (targets.isEmpty()) return
        Analytics.logEvent(
            AnalyticsEvent.LIST_REMOVE_ALL,
            mapOf("lang" to language.code, "list_id" to listId)
        )
        state = state.copy(bulkActionInProgress = true)
        viewModelScope.launch {
            favoritesRepository.removeAll(targets.map { it.senseId }, language)
            sessionBulkAddedSenseIds = emptySet()
            refreshFavoriteFlags()
            state = state.copy(bulkActionInProgress = false)
            onFavoriteChanged(false)
        }
    }

    private suspend fun refreshFavoriteFlags() {
        val allFavorites = favoritesRepository.getAll().filter { it.language == language }
        val favoritedIds = allFavorites.map { it.senseId }.toSet()
        val favoriteLemmas = allFavorites.map { it.lemma }.toSet()
        state = state.copy(
            items = state.items.map { it.copy(isFavorited = it.senseId in favoritedIds) },
            favoriteLemmas = favoriteLemmas,
        )
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
            // Same on-visible trigger as My words: don't touch the TTS engine until the user can
            // see a speaker.
            viewModel.rowAudio.ensureAvailabilityLoaded()
            viewModel.reloadFavorites()
        }
    }
    val list = viewModel.list
    when {
        list != null -> ListDetailContent(
            list = list,
            language = viewModel.language,
            state = viewModel.state,
            scrollState = viewModel.scrollState,
            onBack = onBack,
            onSenseToggle = viewModel::toggleSense,
            onFavoriteToggle = viewModel::toggleFavorite,
            onAddAll = viewModel::addAllToMyWords,
            onRemoveAll = viewModel::removeAllFromMyWords,
            onPrefetchVisible = viewModel::prefetchVisibleRange,
            onNavigateToWordDetail = { lemma, senseId ->
                onNavigateToWordDetail(viewModel.language, lemma, senseId)
            },
            rowAudio = viewModel.rowAudio.uiState,
            rowAudioActions = viewModel.rowAudioActions,
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
    title: String = "",
    titleAlpha: Float = 0f,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Medium,
                                letterSpacing = (-0.3).sp,
                                fontFamily = MaterialTheme.serifFontFamily,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.graphicsLayer { alpha = titleAlpha },
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = titleAlpha * 0.6f),
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        content = content,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListDetailContent(
    list: WordList,
    language: Language,
    state: ListDetailUiState,
    scrollState: LazyListState = LazyListState(),
    onBack: () -> Unit = {},
    onSenseToggle: (String) -> Unit = {},
    onFavoriteToggle: (String) -> Unit = {},
    onAddAll: () -> Unit = {},
    onRemoveAll: () -> Unit = {},
    onPrefetchVisible: (List<ListWordItem>, IntRange) -> Unit = { _, _ -> },
    onNavigateToWordDetail: (lemma: String, senseId: String?) -> Unit = { _, _ -> },
    rowAudio: RowAudioUiState = RowAudioUiState(),
    rowAudioActions: RowAudioActions = RowAudioActions(),
) {
    val isDark = LocalIsDarkTheme.current

    // Prefetch full senses (with translations) for the visible range as the user
    // scrolls. Index 0 is the header item.
    PrefetchVisibleRangeEffect(
        items = state.items,
        scrollState = scrollState,
        headerItemCount = 1,
        onPrefetchVisible = onPrefetchVisible,
    )
    val localeCode = androidx.compose.ui.text.intl.Locale.current.language
    val title = list.title[localeCode] ?: list.title["en"] ?: list.id
    val subtitle = list.subtitle[localeCode] ?: list.subtitle["en"] ?: ""
    val wordCount = list.senseIds.size
    val (bgColor, fgColor) = vibrantColorsForList(list.id, isDark)
    val showTitleInBar by remember(scrollState) {
        derivedStateOf { scrollState.firstVisibleItemIndex > 0 }
    }
    val titleAlpha by animateFloatAsState(
        targetValue = if (showTitleInBar) 1f else 0f,
        animationSpec = tween(durationMillis = 150),
        label = "titleAlpha",
    )

    ListDetailScaffold(onBack = onBack, title = title, titleAlpha = titleAlpha) { innerPadding ->
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
                    inMyWordsCount = state.inMyWordsCount,
                    showBulkAction = !state.isLoading && state.items.isNotEmpty(),
                    allInMyWords = state.allInMyWords,
                    bulkActionInProgress = state.bulkActionInProgress,
                    bgColor = bgColor,
                    fgColor = fgColor,
                    iconSvg = list.iconSvg,
                    isDark = isDark,
                    onAddAll = onAddAll,
                    onRemoveAll = onRemoveAll,
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
                        lemmaAudio = rowAudio.controlFor(
                            senseId = item.senseId,
                            language = language,
                            actions = rowAudioActions,
                        ),
                    )
                }
            }
        }
    }

    RowAudioVoiceSetupHost(state = rowAudio, actions = rowAudioActions)
}

@Composable
private fun ListWordSenseCard(
    item: ListWordItem,
    favoriteLemmas: Set<String>,
    onToggle: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onViewFullDetails: () -> Unit,
    onWordClick: (String) -> Unit,
    lemmaAudio: LemmaAudioControl?,
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
        lemmaAudio = lemmaAudio,
    )
}

@Composable
private fun ListDetailHeader(
    title: String,
    subtitle: String,
    wordCount: Int,
    inMyWordsCount: Int,
    showBulkAction: Boolean,
    allInMyWords: Boolean,
    bulkActionInProgress: Boolean,
    bgColor: Color,
    fgColor: Color,
    iconSvg: String?,
    isDark: Boolean,
    onAddAll: () -> Unit,
    onRemoveAll: () -> Unit,
) {
    val shadowColor = if (isDark) Color.Black.copy(alpha = 0.40f) else Color.Black.copy(alpha = 0.12f)
    val shadowElevation = if (isDark) 3.dp else 4.dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 4.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top,
            modifier = Modifier.fillMaxWidth(),
        ) {
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
                WordListIcon(
                    iconSvg = iconSvg,
                    fgColor = fgColor,
                    modifier = Modifier.size(48.dp).alpha(0.82f),
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontFamily = MaterialTheme.serifFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 24.sp,
                        lineHeight = 28.sp,
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
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                val inMyWordsText = stringResource(Res.string.list_detail_in_my_words_count, inMyWordsCount)
                Text(
                    text = buildAnnotatedString {
                        append(pluralStringResource(Res.plurals.search_list_word_count, wordCount, wordCount))
                        append(", ")
                        // Bold only the number inside the localized "%1$d in My words" segment.
                        val number = inMyWordsCount.toString()
                        val numberStart = inMyWordsText.indexOf(number)
                        if (numberStart >= 0) {
                            append(inMyWordsText.substring(0, numberStart))
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(number) }
                            append(inMyWordsText.substring(numberStart + number.length))
                        } else {
                            append(inMyWordsText)
                        }
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }

        if (showBulkAction) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    onClick = if (allInMyWords) onRemoveAll else onAddAll,
                    enabled = !bulkActionInProgress,
                ) {
                    if (bulkActionInProgress) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                    } else if (!allInMyWords) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        text = stringResource(
                            if (allInMyWords) Res.string.list_detail_remove_all else Res.string.list_detail_add_all
                        ),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        ChapterRule()
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
    senses = List(3) { WordListSense(senseId = it.toString(), lemma = "woord$it", language = Language.DUTCH) },
    iconSvg = null,
)

@Preview
@Composable
private fun ListDetailPreviewContent(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        ListDetailContent(
            list = previewWordList(),
            language = Language.DUTCH,
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
private fun ListDetailPreviewRowPlaying(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        ListDetailContent(
            list = previewWordList(),
            language = Language.DUTCH,
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
                ),
                isLoading = false,
                favoriteLemmas = setOf("huis"),
            ),
            // Row 1 is speaking (stop glyph); tapping any other row would move playback there.
            rowAudio = RowAudioUiState(playingSenseId = "1"),
        )
    }
}

@Preview
@Composable
private fun ListDetailPreviewAllInMyWords(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        ListDetailContent(
            list = previewWordList(),
            language = Language.DUTCH,
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
                        isFavorited = true,
                    ),
                ),
                isLoading = false,
                favoriteLemmas = setOf("huis", "fiets"),
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
            language = Language.DUTCH,
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
            language = Language.DUTCH,
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
