package com.slovy.slovymovyapp.ui.search


import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import com.slovy.slovymovyapp.data.lists.WordList
import com.slovy.slovymovyapp.ui.word.DownloadVector
import androidx.compose.ui.unit.dp
import com.slovy.slovymovyapp.analytics.Analytics
import com.slovy.slovymovyapp.analytics.AnalyticsEvent
import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.remote.DictionaryRepository
import com.slovy.slovymovyapp.i18n.resolve
import com.slovy.slovymovyapp.ui.components.AppSearchBar
import com.slovy.slovymovyapp.ui.components.LanguageFilterDropdown
import com.slovy.slovymovyapp.ui.theme.LocalIsDarkTheme
import com.slovy.slovymovyapp.ui.theme.serifFontFamily
import com.slovy.slovymovyapp.ui.word.Badge
import com.slovy.slovymovyapp.ui.word.colorForLemma
import kotlinx.coroutines.flow.*
import org.jetbrains.compose.resources.stringResource
import slovymovyapp.composeapp.generated.resources.*
import com.slovy.slovymovyapp.ui.AppNavigationBar
import com.slovy.slovymovyapp.ui.AppScreen
import com.slovy.slovymovyapp.ui.FeedbackDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onWordSelected: (DictionaryRepository.SearchItem) -> Unit = { _ -> },
    onSuggestionSelected: (language: Language, lemma: String) -> Unit = { _, _ -> },
    onNavigateToFavorites: () -> Unit = {},
    onNavigateToStats: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToTextReader: (Language) -> Unit = {},
    hasFavoritesToReview: Boolean = false,
    onListClick: (WordList) -> Unit = {},
) {
    val focusManager = LocalFocusManager.current
    // restore after process death
    val savedQuery = rememberSaveable { viewModel.state.query }

    // Refresh language indicators, recent favorites, search results, and lists when screen is opened
    LaunchedEffect(Unit) {
        viewModel.refreshLanguageIndicators()
        viewModel.refreshRecentFavorites()
        viewModel.refreshResults()
        viewModel.refreshLists()
    }

    LaunchedEffect(savedQuery) {
        if (viewModel.state.query.isBlank() && savedQuery.isNotBlank()) {
            viewModel.updateQuery(savedQuery)
        }
    }

    // Clear focus when scrolling starts
    LaunchedEffect(viewModel.state.scrollState.isScrollInProgress) {
        if (viewModel.state.scrollState.isScrollInProgress) {
            focusManager.clearFocus()
        }
    }

    val isNoDictionaryState = viewModel.state.availableLanguages.isEmpty()
    LaunchedEffect(isNoDictionaryState) {
        if (isNoDictionaryState) {
            viewModel.noDictionaryScrollState.scrollTo(0)
        }
    }

    SearchScreenContent(
        state = viewModel.state,
        noDictionaryScrollState = viewModel.noDictionaryScrollState,
        onQueryChange = { viewModel.updateQuery(it) },
        onResultSelected = { item ->
            focusManager.clearFocus()
            Analytics.logEvent(AnalyticsEvent.WORD_SEARCH_RESULT_SHOW)
            onWordSelected(item)
        },
        onSuggestionSelected = { word ->
            focusManager.clearFocus()
            viewModel.state.selectedLanguage?.let { language ->
                Analytics.logEvent(AnalyticsEvent.WORD_SEARCH_SUGGESTION_CLICK)
                onSuggestionSelected(language, word)
            }
        },
        onLanguageSelected = { language ->
            viewModel.setShowLanguageIndicators(language == null && viewModel.state.availableLanguages.size > 1)
            viewModel.setSelectedLanguage(language)
            // TODO: search is not relaunched or query
        },
        onSetLanguageDropdownExpanded = { viewModel.setLanguageDropdownExpanded(it) },
        onRefreshSuggestions = { viewModel.refreshSuggestionsFromPull() },
        onListClick = onListClick,
        onSuggestListClick = { viewModel.openListSuggestionDialog() },
        onListSuggestionCommentChange = { viewModel.updateListSuggestionComment(it) },
        onListSuggestionEmailChange = { viewModel.updateListSuggestionEmail(it) },
        onDismissListSuggestion = { viewModel.dismissListSuggestionDialog() },
        onSubmitListSuggestion = { viewModel.submitListSuggestion() },
        onNavigateToFavorites = onNavigateToFavorites,
        onNavigateToStats = onNavigateToStats,
        onNavigateToSettings = onNavigateToSettings,
        onNavigateToTextReader = {
            viewModel.state.selectedLanguage?.let { language ->
                Analytics.logEvent(AnalyticsEvent.READER_OPEN_CLICK, mapOf("lang" to language.code))
                onNavigateToTextReader(language)
            }
        },
        hasFavoritesToReview = hasFavoritesToReview,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreenContent(
    state: SearchUiState,
    noDictionaryScrollState: ScrollState? = null,
    onQueryChange: (String) -> Unit = {},
    onResultSelected: (DictionaryRepository.SearchItem) -> Unit = {},
    onSuggestionSelected: (String) -> Unit = {},
    onLanguageSelected: (Language?) -> Unit = {},
    onSetLanguageDropdownExpanded: (Boolean) -> Unit = {},
    onRefreshSuggestions: () -> Unit = {},
    onListClick: (WordList) -> Unit = {},
    onSuggestListClick: () -> Unit = {},
    onListSuggestionCommentChange: (String) -> Unit = {},
    onListSuggestionEmailChange: (String) -> Unit = {},
    onDismissListSuggestion: () -> Unit = {},
    onSubmitListSuggestion: () -> Unit = {},
    onNavigateToFavorites: () -> Unit = {},
    onNavigateToStats: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToTextReader: () -> Unit = {},
    hasFavoritesToReview: Boolean = false,
) {
    val focusManager = LocalFocusManager.current
    val resolvedNoDictionaryScrollState = noDictionaryScrollState ?: remember { ScrollState(0) }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            AppNavigationBar(
                currentScreen = AppScreen.SEARCH,
                onNavigateToSearch = {},
                onNavigateToFavorites = onNavigateToFavorites,
                onNavigateToStats = onNavigateToStats,
                onNavigateToSettings = onNavigateToSettings,
                hasFavoritesToReview = hasFavoritesToReview,
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
                // Search field with language dropdown on the same row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Search field
                    AppSearchBar(
                        query = state.query,
                        onQueryChange = onQueryChange,
                        modifier = Modifier.weight(1f),
                        placeholder = stringResource(Res.string.search_placeholder)
                    )

                    // Language filter dropdown
                    if (state.availableLanguages.size > 1) {
                        LanguageFilterDropdown(
                            languages = state.availableLanguages,
                            selectedLanguage = state.selectedLanguage,
                            expanded = state.isLanguageDropdownExpanded,
                            onExpandedChange = onSetLanguageDropdownExpanded,
                            onLanguageSelected = onLanguageSelected,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = {
                                focusManager.clearFocus()
                            })
                        }
                ) {
                    // Result area
                    when {
                        state.availableLanguages.isEmpty() -> {
                            NoDictionaryState(
                                scrollState = resolvedNoDictionaryScrollState,
                                onNavigateToSettings = onNavigateToSettings
                            )
                        }

                        state.query.isBlank() -> {
                            PullToRefreshBox(
                                modifier = Modifier.fillMaxSize(),
                                isRefreshing = state.isSuggestionsRefreshing,
                                onRefresh = onRefreshSuggestions
                            ) {
                                EmptySearchState(
                                    wordSuggestions = state.wordSuggestions,
                                    favoriteLemmas = state.favoriteLemmas,
                                    curatedLists = state.curatedLists,
                                    isLoading = state.isEmptyStateLoading,
                                    onWordClick = onSuggestionSelected,
                                    onListClick = onListClick,
                                    onSuggestListClick = onSuggestListClick,
                                    onNavigateToTextReader = onNavigateToTextReader
                                )
                            }
                        }

                        state.showNoResults -> {
                            NoResultsState(query = state.query)
                        }

                        else -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(16.dp),
                                state = state.scrollState
                            ) {
                                items(state.results) { item ->
                                    SearchResultCard(
                                        item = item,
                                        showLanguageIndicator = state.showLanguageIndicators,
                                        onClick = { onResultSelected(item) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

    if (state.listSuggestion.dialogVisible) {
        FeedbackDialog(
            title = stringResource(Res.string.search_suggest_list_dialog_title),
            commentPlaceholder = stringResource(Res.string.search_suggest_list_placeholder),
            commentLabel = stringResource(Res.string.feedback_dialog_comment_label),
            comment = state.listSuggestion.comment,
            email = state.listSuggestion.email,
            isSending = state.listSuggestion.submitting,
            error = state.listSuggestion.error?.resolve(),
            resultUrl = state.listSuggestion.resultUrl,
            onCommentChange = onListSuggestionCommentChange,
            onEmailChange = onListSuggestionEmailChange,
            onDismiss = onDismissListSuggestion,
            onSend = onSubmitListSuggestion
        )
    }
}

@Composable
private fun SearchResultCard(
    item: DictionaryRepository.SearchItem,
    showLanguageIndicator: Boolean = false,
    onClick: () -> Unit
) {
    val containerColor = colorForLemma(item.lemma, MaterialTheme.colorScheme.surface, LocalIsDarkTheme.current)
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { role = Role.Button }
            .clickable(
                onClickLabel = stringResource(Res.string.search_open_item, item.display),
                onClick = onClick
            ),
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.outlinedCardColors(containerColor = containerColor),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = item.display,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = MaterialTheme.serifFontFamily
                ),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showLanguageIndicator) {
                    Badge(
                        text = item.language.selfName,
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        shape = RoundedCornerShape(4.dp)
                    )
                }

                if (item.isFavorite) {
                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = stringResource(Res.string.search_content_desc_favorite),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
                if (item.onlineOnly) {
                    Icon(
                        imageVector = DownloadVector,
                        contentDescription = stringResource(Res.string.search_content_desc_not_downloaded),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

