package com.slovy.slovymovyapp.ui.stats

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slovy.slovymovyapp.analytics.Analytics
import com.slovy.slovymovyapp.analytics.AnalyticsEvent
import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.ui.components.LanguageFilterDropdown
import com.slovy.slovymovyapp.ui.theme.AppSpacing
import com.slovy.slovymovyapp.ui.theme.serifFontFamily
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import org.jetbrains.compose.resources.stringResource
import slovymovyapp.composeapp.generated.resources.*
import com.slovy.slovymovyapp.ui.AppNavigationBar
import com.slovy.slovymovyapp.ui.AppScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    viewModel: StatsViewModel,
    learningLanguages: List<Language>,
    onNavigateToSearch: () -> Unit = {},
    onNavigateToFavorites: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    hasFavoritesToReview: Boolean = false,
) {
    LaunchedEffect(learningLanguages) {
        viewModel.updateLearningLanguages(learningLanguages)
    }
    LaunchedEffect(Unit) {
        viewModel.refresh()
    }
    LaunchedEffect(Unit) {
        val loaded = snapshotFlow { viewModel.state }
            .filter { !it.isLoading }
            .first()
        Analytics.logEvent(
            AnalyticsEvent.STATS_SCREEN_OPEN,
            mapOf(
                "lang" to loaded.selectedLanguage.code,
                "streak_days" to loaded.streakDays.toLong(),
                "active_days_total" to loaded.activeDaysTotal.toLong(),
                "reviews_today" to loaded.reviewsToday.toLong(),
                "reviews_week" to loaded.reviewsWeek.toLong(),
                "minutes_today" to loaded.minutesToday.toLong(),
                "minutes_week" to loaded.minutesWeek.toLong(),
                "words_total" to loaded.wordsTotal.toLong(),
                "senses_total" to loaded.sensesTotal.toLong(),
                "delayed_due_lemma_count" to loaded.delayedDueLemmaCount.toLong(),
                "delayed_due_card_count" to loaded.delayedDueCardCount.toLong(),
            ),
        )
    }

    StatsScreenContent(
        state = viewModel.state,
        scrollState = viewModel.scrollState,
        onSelectedLanguageChange = { viewModel.setSelectedLanguage(it) },
        onLanguageDropdownExpandedChange = { viewModel.setLanguageDropdownExpanded(it) },
        onStepMonth = { viewModel.stepMonth(it) },
        onNavigateToSearch = onNavigateToSearch,
        onNavigateToFavorites = onNavigateToFavorites,
        onNavigateToSettings = onNavigateToSettings,
        hasFavoritesToReview = hasFavoritesToReview,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreenContent(
    state: StatsUiState,
    scrollState: ScrollState = rememberScrollState(),
    onSelectedLanguageChange: (Language) -> Unit = {},
    onLanguageDropdownExpandedChange: (Boolean) -> Unit = {},
    onStepMonth: (Int) -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    onNavigateToFavorites: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    hasFavoritesToReview: Boolean = false,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            AppNavigationBar(
                currentScreen = AppScreen.STATS,
                onNavigateToSearch = onNavigateToSearch,
                onNavigateToFavorites = onNavigateToFavorites,
                onNavigateToStats = {},
                onNavigateToSettings = onNavigateToSettings,
                hasFavoritesToReview = hasFavoritesToReview,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState),
        ) {
            StatsHeader(
                state = state,
                onSelectedLanguageChange = onSelectedLanguageChange,
                onLanguageDropdownExpandedChange = onLanguageDropdownExpandedChange,
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpacing.lg)
                    .padding(top = AppSpacing.xs, bottom = AppSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                StreakCard(state = state, onStepMonth = onStepMonth)
                SectionHeader(text = stringResource(Res.string.stats_effort_section))
                EffortCard(state = state)
                SectionHeader(text = stringResource(Res.string.stats_library_section))
                LibraryCard(state = state)
            }
        }
    }
}

@Composable
private fun StatsHeader(
    state: StatsUiState,
    onSelectedLanguageChange: (Language) -> Unit,
    onLanguageDropdownExpandedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 14.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
    ) {
        Text(
            text = stringResource(Res.string.stats_title),
            style = MaterialTheme.typography.titleLarge.copy(
                fontFamily = MaterialTheme.serifFontFamily,
                fontWeight = FontWeight.Medium,
                letterSpacing = (-0.2).sp,
                lineHeight = 24.sp,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (state.showLanguagePicker) {
            LanguageFilterDropdown(
                languages = state.learningLanguages,
                selectedLanguage = state.selectedLanguage,
                expanded = state.languageDropdownExpanded,
                onExpandedChange = onLanguageDropdownExpandedChange,
                onLanguageSelected = onSelectedLanguageChange,
            )
        }
    }
}
