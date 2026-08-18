package com.slovy.slovymovyapp.ui.listdetail

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.ui.components.vibrantColorsForList
import com.slovy.slovymovyapp.data.lists.WordList
import com.slovy.slovymovyapp.i18n.resolve
import com.slovy.slovymovyapp.speech.LemmaAudioControl
import com.slovy.slovymovyapp.speech.RowAudioActions
import com.slovy.slovymovyapp.speech.RowAudioUiState
import com.slovy.slovymovyapp.ui.components.SpinningProgressIndicator
import com.slovy.slovymovyapp.ui.theme.AppSpacing
import com.slovy.slovymovyapp.ui.theme.LocalIsDarkTheme
import com.slovy.slovymovyapp.ui.theme.serifFontFamily
import com.slovy.slovymovyapp.ui.word.ChapterRule
import com.slovy.slovymovyapp.ui.word.SenseCard
import com.slovy.slovymovyapp.ui.word.SenseCardData
import com.slovy.slovymovyapp.ui.word.SenseUiState
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import slovymovyapp.composeapp.generated.resources.Res
import slovymovyapp.composeapp.generated.resources.common_list_separator
import slovymovyapp.composeapp.generated.resources.common_retry
import slovymovyapp.composeapp.generated.resources.list_detail_add_all
import slovymovyapp.composeapp.generated.resources.list_detail_empty
import slovymovyapp.composeapp.generated.resources.list_detail_in_my_words_count
import slovymovyapp.composeapp.generated.resources.list_detail_load_error
import slovymovyapp.composeapp.generated.resources.list_detail_remove_all
import slovymovyapp.composeapp.generated.resources.search_list_word_count
import com.slovy.slovymovyapp.ui.WordListIcon
import com.slovy.slovymovyapp.ui.RowAudioVoiceSetupHost
import com.slovy.slovymovyapp.ui.PrefetchVisibleRangeEffect
import com.slovy.slovymovyapp.ui.components.vibrantColorsForList

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
            viewModel.rowAudio.refreshAvailability()
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
        ) { SpinningProgressIndicator() }
    }
}

@Composable
internal fun ListDetailErrorScreen(onBack: () -> Unit, onRetry: () -> Unit) {
    ListDetailScaffold(onBack = onBack) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = AppSpacing.xxl),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.lg, Alignment.CenterVertically),
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
                bottom = innerPadding.calculateBottomPadding() + AppSpacing.lg,
                start = AppSpacing.lg,
                end = AppSpacing.lg,
            ),
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding()),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
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
                        modifier = Modifier.fillMaxWidth().padding(top = AppSpacing.xxl),
                        contentAlignment = Alignment.Center
                    ) {
                        SpinningProgressIndicator()
                    }
                }
            } else if (state.items.isEmpty()) {
                item {
                    Text(
                        text = stringResource(Res.string.list_detail_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = AppSpacing.xxl, start = AppSpacing.lg, end = AppSpacing.lg),
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
            translationLoading = item.translationLoading,
            error = item.error,
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
            .padding(top = AppSpacing.xs, bottom = AppSpacing.xs),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.lg),
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
                        modifier = Modifier.padding(top = AppSpacing.xs),
                    )
                }
                val inMyWordsText = stringResource(Res.string.list_detail_in_my_words_count, inMyWordsCount)
                // The two counts are separate resources because only the second one's number is
                // bolded, so the joiner has to be localized on its own.
                val listSeparator = stringResource(Res.string.common_list_separator)
                Text(
                    text = buildAnnotatedString {
                        append(pluralStringResource(Res.plurals.search_list_word_count, wordCount, wordCount))
                        append(listSeparator)
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
                    modifier = Modifier.padding(top = AppSpacing.xsPlus),
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
                        SpinningProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(AppSpacing.sm))
                    } else if (!allInMyWords) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(AppSpacing.xs))
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

        Spacer(Modifier.height(AppSpacing.xs))
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

