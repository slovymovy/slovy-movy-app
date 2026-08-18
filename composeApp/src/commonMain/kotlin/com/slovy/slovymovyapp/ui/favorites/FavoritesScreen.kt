package com.slovy.slovymovyapp.ui.favorites

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.slovy.slovymovyapp.analytics.Analytics
import com.slovy.slovymovyapp.analytics.AnalyticsEvent
import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.favorites.Favorite
import com.slovy.slovymovyapp.data.remote.*
import com.slovy.slovymovyapp.speech.RowAudioActions
import com.slovy.slovymovyapp.speech.RowAudioUiState
import com.slovy.slovymovyapp.ui.components.AppSearchBar
import com.slovy.slovymovyapp.ui.components.EmptyState
import com.slovy.slovymovyapp.ui.components.LanguageFilterDropdown
import com.slovy.slovymovyapp.ui.icons.NoFavsImage
import com.slovy.slovymovyapp.ui.icons.SearchOtter
import com.slovy.slovymovyapp.ui.icons.SlovyIcons
import com.slovy.slovymovyapp.ui.theme.AppSpacing
import com.slovy.slovymovyapp.ui.theme.serifFontFamily
import com.slovy.slovymovyapp.ui.theme.uiItalic
import com.slovy.slovymovyapp.ui.word.LoadingPlaceholder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.compose.resources.stringResource
import slovymovyapp.composeapp.generated.resources.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import com.slovy.slovymovyapp.ui.AppNavigationBar
import com.slovy.slovymovyapp.ui.AppScreen
import com.slovy.slovymovyapp.ui.PrefetchVisibleRangeEffect
import com.slovy.slovymovyapp.ui.RowAudioVoiceSetupHost

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    viewModel: FavoritesViewModel,
    onNavigateToSearch: () -> Unit = {},
    onSearchInDictionary: (String) -> Unit = {},
    onNavigateToWordDetail: (Language, String, String?) -> Unit = { _, _, _ -> },
    onNavigateToSettings: () -> Unit = {},
    onNavigateToStats: () -> Unit = {},
    onStartStudy: (Language) -> Unit = {},
    onContinueStudyingNow: (Language, FavoritesStudyDoneAction) -> Unit = { _, _ -> },
    onFavoritesChanged: (Language) -> Unit = {},
    onRefreshReviewState: () -> Unit = {},
) {
    val focusManager = LocalFocusManager.current
    val removedMessage = stringResource(Res.string.favorites_removed_message)
    val undoLabel = stringResource(Res.string.favorites_removed_undo)

    LifecycleResumeEffect(viewModel) {
        viewModel.rowAudio.refreshAvailability()
        viewModel.loadFavorites()
        onRefreshReviewState()
        onPauseOrDispose { }
    }

    LaunchedEffect(viewModel.scrollState.isScrollInProgress) {
        if (viewModel.scrollState.isScrollInProgress) {
            focusManager.clearFocus()
        }
    }

    val scrollToTop = (viewModel.state as? FavoritesUiState.Content)?.scrollToTop ?: false
    LaunchedEffect(scrollToTop) {
        if (scrollToTop) {
            viewModel.scrollState.scrollToItem(0)
            viewModel.consumeScrollToTop()
        }
    }

    val isEmptyState = (viewModel.state as? FavoritesUiState.Content)?.hasAnyFavorites == false
    LaunchedEffect(isEmptyState) {
        if (isEmptyState) {
            viewModel.emptyStateScrollState.scrollTo(0)
        }
    }

    val nextReviewAtEpochMs = (viewModel.state as? FavoritesUiState.Content)
        ?.studyDone?.nextReviewAtEpochMs
    LaunchedEffect(nextReviewAtEpochMs) {
        val target = nextReviewAtEpochMs ?: return@LaunchedEffect
        val remaining = target - Clock.System.now().toEpochMilliseconds()
        if (remaining > 0) delay(remaining.milliseconds)
        onRefreshReviewState()
    }

    FavoritesScreenContent(
        state = viewModel.state,
        scrollState = viewModel.scrollState,
        emptyStateScrollState = viewModel.emptyStateScrollState,
        snackbarHostState = viewModel.snackbarHostState,
        onNavigateToSearch = onNavigateToSearch,
        onSearchInDictionary = onSearchInDictionary,
        onQueryChange = { viewModel.updateQuery(it) },
        onSenseToggle = { viewModel.toggleSense(it) },
        onFavoriteToggle = { viewModel.toggleFavorite(it, removedMessage, undoLabel, onFavoritesChanged) },
        onNavigateToWordDetail = onNavigateToWordDetail,
        onNavigateToSettings = onNavigateToSettings,
        onNavigateToStats = onNavigateToStats,
        onPrefetchVisible = { senses, range -> viewModel.prefetchVisibleRange(senses, range) },
        onLanguageSelected = { viewModel.setSelectedLanguage(it) },
        onSetLanguageDropdownExpanded = { viewModel.setLanguageDropdownExpanded(it) },
        onStartStudy = onStartStudy,
        onContinueStudyingNow = onContinueStudyingNow,
        rowAudio = viewModel.rowAudio.uiState,
        rowAudioActions = viewModel.rowAudioActions,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreenContent(
    state: FavoritesUiState,
    scrollState: LazyListState = LazyListState(),
    emptyStateScrollState: ScrollState? = null,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onNavigateToSearch: () -> Unit = {},
    onSearchInDictionary: (String) -> Unit = {},
    onQueryChange: (String) -> Unit = {},
    onSenseToggle: (String) -> Unit = {},
    onFavoriteToggle: (String) -> Unit = {},
    onNavigateToWordDetail: (Language, String, String?) -> Unit = { _, _, _ -> },
    onNavigateToSettings: () -> Unit = {},
    onNavigateToStats: () -> Unit = {},
    onPrefetchVisible: (List<FavoriteSenseItem>, IntRange) -> Unit = { _, _ -> },
    onLanguageSelected: (Language) -> Unit = {},
    onSetLanguageDropdownExpanded: (Boolean) -> Unit = {},
    onStartStudy: (Language) -> Unit = {},
    onContinueStudyingNow: (Language, FavoritesStudyDoneAction) -> Unit = { _, _ -> },
    rowAudio: RowAudioUiState = RowAudioUiState(),
    rowAudioActions: RowAudioActions = RowAudioActions(),
) {
    val focusManager = LocalFocusManager.current
    val resolvedEmptyStateScrollState = emptyStateScrollState ?: remember { ScrollState(0) }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(Res.string.favorites_title),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = MaterialTheme.serifFontFamily,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            )
        },
        bottomBar = {
            AppNavigationBar(
                currentScreen = AppScreen.FAVORITES,
                onNavigateToSearch = onNavigateToSearch,
                onNavigateToFavorites = {},
                onNavigateToStats = onNavigateToStats,
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
                    LoadingPlaceholder(label = stringResource(Res.string.favorites_loading))
                }
            }

            is FavoritesUiState.Content -> {
                PrefetchVisibleRangeEffect(
                    items = state.senses,
                    scrollState = scrollState,
                    headerItemCount = 0,
                    onPrefetchVisible = onPrefetchVisible,
                )
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (state.hasAnyFavorites) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = AppSpacing.lg),
                            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppSearchBar(
                                query = state.query,
                                onQueryChange = onQueryChange,
                                modifier = Modifier.weight(1f),
                                placeholder = stringResource(Res.string.favorites_search_placeholder)
                            )

                            if (state.showLanguagePicker) {
                                LanguageFilterDropdown(
                                    languages = state.availableLanguages,
                                    selectedLanguage = state.selectedLanguage,
                                    expanded = state.isLanguageDropdownExpanded,
                                    onExpandedChange = onSetLanguageDropdownExpanded,
                                    onLanguageSelected = onLanguageSelected,
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures(onTap = {
                                    focusManager.clearFocus()
                                })
                            }
                    ) {
                        when {
                            !state.hasAnyFavorites -> {
                                ScrollableEmptyStateContainer(
                                    scrollState = resolvedEmptyStateScrollState,
                                ) {
                                    FavoritesEmptyState(onNavigateToSearch = onNavigateToSearch)
                                }
                            }

                            state.showNoResults -> {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = BiasAlignment(horizontalBias = 0f, verticalBias = -0.3f)
                                ) {
                                    EmptyState(
                                        iconContent = {
                                            Image(
                                                imageVector = SlovyIcons.SearchOtter,
                                                contentDescription = null,
                                                modifier = Modifier.size(140.dp)
                                            )
                                        },
                                        title = stringResource(Res.string.favorites_no_results_title, state.query),
                                        description = stringResource(Res.string.favorites_no_results_description),
                                        action = {
                                            Button(
                                                onClick = { onSearchInDictionary(state.query) },
                                                shape = CircleShape,
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = MaterialTheme.colorScheme.primary,
                                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                                ),
                                                contentPadding = PaddingValues(horizontal = 28.dp),
                                                modifier = Modifier
                                                    .height(56.dp)
                                                    .widthIn(min = 220.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Search,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Text(stringResource(Res.string.favorites_no_results_action_search_dictionary))
                                            }
                                        }
                                    )
                                }
                            }

                            else -> {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    val words = state.senses.distinctBy { it.lemma }
                                    state.study?.let { study ->
                                        StudyDueCard(
                                            study = study,
                                            onStartStudy = {
                                                Analytics.logEvent(
                                                    AnalyticsEvent.FAVORITES_STUDY_DUE_CLICK,
                                                    mapOf(
                                                        "lang" to study.language.code,
                                                        "due_count" to study.dueCount.toLong(),
                                                        "delayed_due_card_count" to study.delayedDueCardCount.toLong(),
                                                    ),
                                                )
                                                onStartStudy(study.language)
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.sm),
                                        )
                                    } ?: state.studyDone?.let { studyDone ->
                                        StudyDoneCard(
                                            studyDone = studyDone,
                                            onContinueStudyingNow = {
                                                studyDone.action?.let { action ->
                                                    Analytics.logEvent(
                                                        when (action) {
                                                            FavoritesStudyDoneAction.REVIEW_MORE ->
                                                                AnalyticsEvent.FAVORITES_STUDY_DONE_REVIEW_MORE_CLICK

                                                            FavoritesStudyDoneAction.STUDY_NEW ->
                                                                AnalyticsEvent.FAVORITES_STUDY_DONE_STUDY_NEW_CLICK
                                                        },
                                                        mapOf(
                                                            "lang" to studyDone.language.code,
                                                            "due_count" to state.reviewDueCount.toLong(),
                                                            "delayed_due_card_count" to studyDone.delayedDueCardCount.toLong(),
                                                        ),
                                                    )
                                                    onContinueStudyingNow(studyDone.language, action)
                                                }
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.sm),
                                        )
                                    }
                                    Text(
                                        text = stringResource(
                                            Res.string.favorites_stats,
                                            state.senses.size,
                                            words.size
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(
                                            start = AppSpacing.lg,
                                            top = AppSpacing.sm,
                                            bottom = AppSpacing.sm,
                                        )
                                    )

                                    LazyColumn(
                                        state = scrollState,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = AppSpacing.lg),
                                        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                                        contentPadding = PaddingValues(bottom = AppSpacing.lg)
                                    ) {
                                        items(
                                            state.senses,
                                            key = { it.senseId },
                                            contentType = { "sense_card" }) { item ->
                                            FavoriteSenseCard(
                                                item = item,
                                                onToggle = { onSenseToggle(item.senseId) },
                                                onFavoriteToggle = { onFavoriteToggle(item.senseId) },
                                                onViewFullDetails = {
                                                    onNavigateToWordDetail(item.targetLang, item.lemma, item.senseId)
                                                },
                                                onWordClick = { word ->
                                                    Analytics.logEvent(AnalyticsEvent.FAVORITES_WORD_SHOW)
                                                    onNavigateToWordDetail(item.targetLang, word, null)
                                                },
                                                favoriteLemmas = state.favoriteLemmas,
                                                lemmaAudio = rowAudio.controlFor(
                                                    senseId = item.senseId,
                                                    language = item.targetLang,
                                                    actions = rowAudioActions,
                                                ),
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

    RowAudioVoiceSetupHost(state = rowAudio, actions = rowAudioActions)
}

@Composable
private fun ScrollableEmptyStateContainer(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = maxHeight)
                .verticalScroll(scrollState),
            contentAlignment = BiasAlignment(horizontalBias = 0f, verticalBias = -0.18f),
            content = content
        )
    }
}

@Composable
private fun FavoritesEmptyState(
    onNavigateToSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val heartIconId = "favorite-heart"
    val descriptionBeforeHeart = stringResource(Res.string.favorites_empty_description_before_heart_icon)
    val descriptionAfterHeart = stringResource(Res.string.favorites_empty_description_after_heart_icon)
    val favoriteLabel = stringResource(Res.string.search_content_desc_favorite)
    val descriptionText = buildAnnotatedString {
        append(descriptionBeforeHeart)
        appendInlineContent(heartIconId, favoriteLabel)
        append(descriptionAfterHeart)
    }
    val inlineContent = mapOf(
        heartIconId to InlineTextContent(
            placeholder = Placeholder(
                width = 18.sp,
                height = 18.sp,
                placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
            ),
        ) {
            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.9f),
                modifier = Modifier.fillMaxSize()
            )
        }
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            imageVector = SlovyIcons.NoFavsImage,
            contentDescription = null,
            modifier = Modifier.size(204.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = stringResource(Res.string.favorites_empty_title),
            style = MaterialTheme.typography.headlineMedium.copy(
                fontFamily = MaterialTheme.serifFontFamily,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 34.sp,
            ),
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 340.dp)
        )

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = descriptionText,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontFamily = MaterialTheme.serifFontFamily,
                fontStyle = MaterialTheme.uiItalic,
                lineHeight = 24.sp,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            inlineContent = inlineContent,
            modifier = Modifier.widthIn(max = 320.dp)
        )

        Spacer(modifier = Modifier.height(36.dp))

        Button(
            onClick = onNavigateToSearch,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            contentPadding = PaddingValues(horizontal = 28.dp),
            modifier = Modifier
                .height(56.dp)
                .widthIn(min = 220.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = stringResource(Res.string.favorites_empty_action_start_searching),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            )
        }
    }
}

