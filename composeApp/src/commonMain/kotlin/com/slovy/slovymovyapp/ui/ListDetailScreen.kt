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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.favorites.FavoritesRepository
import com.slovy.slovymovyapp.data.remote.DictionaryRepository
import com.slovy.slovymovyapp.data.remote.LanguageCardResponseSense
import com.slovy.slovymovyapp.data.remote.PartOfSpeech
import com.slovy.slovymovyapp.data.remote.RelatedWord
import com.slovy.slovymovyapp.data.remote.RemoteList
import com.slovy.slovymovyapp.i18n.UiText
import com.slovy.slovymovyapp.i18n.resolve
import com.slovy.slovymovyapp.ui.theme.LocalIsDarkTheme
import com.slovy.slovymovyapp.ui.theme.serifFontFamily
import com.slovy.slovymovyapp.ui.word.SenseCard
import com.slovy.slovymovyapp.ui.word.SenseCardData
import com.slovy.slovymovyapp.ui.word.SenseUiState
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import slovymovyapp.composeapp.generated.resources.Res
import slovymovyapp.composeapp.generated.resources.favorites_error_meaning_not_found
import slovymovyapp.composeapp.generated.resources.search_list_word_count

data class ListWordItem(
    val senseId: String,
    val lemma: String,
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
    val favoriteLemmas: Set<String> = emptySet(),
)

class ListDetailViewModel(
    val list: RemoteList,
    val language: Language,
    private val repository: DictionaryRepository,
    private val favoritesRepository: FavoritesRepository,
) : ViewModel() {
    var state by mutableStateOf(ListDetailUiState())
        private set
    val scrollState = LazyListState()

    init {
        viewModelScope.launch {
            val senses = repository.getListSenses(language, list.senseIds)
            val allFavorites = favoritesRepository.getAll().filter { it.language == language }
            val favoritedIds = allFavorites.map { it.senseId }.toSet()
            val favoriteLemmas = allFavorites.map { it.lemma }.toSet()
            val items = senses.map { sense ->
                ListWordItem(
                    senseId = sense.senseId,
                    lemma = sense.lemma,
                    isFavorited = sense.senseId in favoritedIds,
                )
            }
            state = ListDetailUiState(
                items = items,
                isLoading = false,
                favoriteLemmas = favoriteLemmas,
            )
            items.forEach { item -> launch { loadSense(item) } }
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
            if (item.isFavorited) {
                favoritesRepository.remove(senseId, language)
                updateItem(senseId) { it.copy(isFavorited = false) }
                state = state.copy(favoriteLemmas = state.favoriteLemmas - item.lemma)
            } else {
                favoritesRepository.add(senseId, language, item.lemma)
                updateItem(senseId) { it.copy(isFavorited = true) }
                state = state.copy(favoriteLemmas = state.favoriteLemmas + item.lemma)
            }
        }
    }
}

@Composable
fun ListDetailScreen(
    viewModel: ListDetailViewModel,
    onBack: () -> Unit,
    onNavigateToWordDetail: (language: Language, lemma: String, senseId: String?) -> Unit,
) {
    ListDetailContent(
        list = viewModel.list,
        language = viewModel.language,
        state = viewModel.state,
        scrollState = viewModel.scrollState,
        onBack = onBack,
        onSenseToggle = viewModel::toggleSense,
        onFavoriteToggle = viewModel::toggleFavorite,
        onNavigateToWordDetail = { lemma, senseId -> onNavigateToWordDetail(viewModel.language, lemma, senseId) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListDetailContent(
    list: RemoteList,
    language: Language,
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
    val wordCount = list.senseIds.size
    val (bgColor, fgColor) = vibrantColorsForList(list.id, isDark)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { innerPadding ->
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
        ),
        state = SenseUiState(
            senseId = item.senseId,
            expanded = item.expanded,
            examplesExpanded = false,
            languageExpanded = emptyMap(),
            favorite = item.isFavorited,
            showFavoriteToggle = item.expanded,
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
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(bgColor),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.size(36.dp).alpha(0.92f)) {
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

        Spacer(Modifier.height(4.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontFamily = MaterialTheme.serifFontFamily,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.4).sp,
            ),
        )
        if (subtitle.isNotEmpty()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontStyle = FontStyle.Italic,
                    fontFamily = MaterialTheme.serifFontFamily,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = pluralStringResource(Res.plurals.search_list_word_count, wordCount, wordCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
