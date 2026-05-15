package com.slovy.slovymovyapp.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slovy.slovymovyapp.analytics.Analytics
import com.slovy.slovymovyapp.analytics.AnalyticsEvent
import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.learning.stats.StatsPipelineStage
import com.slovy.slovymovyapp.data.learning.stats.StatsPipelineStageId
import com.slovy.slovymovyapp.data.learning.stats.StatsPracticeDay
import com.slovy.slovymovyapp.data.learning.stats.StatsService
import com.slovy.slovymovyapp.data.learning.stats.StatsYearMonth
import com.slovy.slovymovyapp.data.settings.Setting
import com.slovy.slovymovyapp.data.settings.SettingsRepository
import com.slovy.slovymovyapp.ui.theme.serifFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import slovymovyapp.composeapp.generated.resources.*
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

data class StatsUiState(
    val learningLanguages: List<Language>,
    val selectedLanguage: Language,
    val languageDropdownExpanded: Boolean = false,
    val today: LocalDate,
    val viewMonth: StatsYearMonth,
    val isLoading: Boolean,
    val streakDays: Int,
    val practiceLog: Set<StatsPracticeDay>,
    val reviewsToday: Int,
    val reviewsWeek: Int,
    val wordsTotal: Int,
    val pipeline: List<StatsPipelineStage>,
    val delayedDueLemmaCount: Int = 0,
    val delayedDueCardCount: Int = 0,
) {
    val sensesTotal: Int get() = pipeline.sumOf { it.count }
    val showLanguagePicker: Boolean get() = learningLanguages.size > 1
}

@OptIn(ExperimentalTime::class)
class StatsViewModel(
    initialLearningLanguages: List<Language>,
    private val statsService: StatsService,
    private val settingsRepository: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {
    val scrollState = ScrollState(0)

    var state by mutableStateOf(initialStatsState(initialLearningLanguages, clock))
        private set

    private var savedStatsLanguage: Language? = null
    private var savedStatsLanguageLoaded = false
    private var languageSelectedDuringRestore = false
    private var reloadRequestId = 0

    init {
        viewModelScope.launch {
            val savedCode = settingsRepository.getById(Setting.Name.STATS_LANGUAGE)
                ?.value?.jsonPrimitive?.contentOrNull
            if (!languageSelectedDuringRestore) {
                savedStatsLanguage = savedCode?.let { Language.fromCodeOrNull(it) }
            }
            savedStatsLanguageLoaded = true
            val selected = selectedLanguageFor(state.learningLanguages)
            if (selected != state.selectedLanguage) {
                state = state.copy(selectedLanguage = selected)
            }
            scheduleReload()
        }
    }

    fun updateLearningLanguages(languages: List<Language>) {
        val normalized = languages.ifEmpty { listOf(Language.ENGLISH) }.distinct().sortedBy { it.ordinal }
        val selected = selectedLanguageFor(normalized)
        if (normalized == state.learningLanguages && selected == state.selectedLanguage) return
        state = state.copy(learningLanguages = normalized, selectedLanguage = selected)
        scheduleReload()
    }

    fun setSelectedLanguage(language: Language) {
        if (state.selectedLanguage == language) return
        state = state.copy(selectedLanguage = language, languageDropdownExpanded = false)
        savedStatsLanguage = language
        if (!savedStatsLanguageLoaded) {
            languageSelectedDuringRestore = true
        }
        viewModelScope.launch {
            settingsRepository.insert(Setting(Setting.Name.STATS_LANGUAGE, JsonPrimitive(language.code)))
            Analytics.logEvent(
                AnalyticsEvent.SETTING_CHANGED,
                mapOf("setting" to "stats_language", "value" to language.code),
            )
        }
        scheduleReload()
    }

    fun setLanguageDropdownExpanded(expanded: Boolean) {
        state = state.copy(languageDropdownExpanded = expanded)
    }

    fun stepMonth(delta: Int) {
        val current = state.viewMonth
        val next = when (current.monthZeroBased + delta) {
            -1 -> StatsYearMonth(current.year - 1, 11)
            12 -> StatsYearMonth(current.year + 1, 0)
            else -> current.copy(monthZeroBased = current.monthZeroBased + delta)
        }
        val todayMonth = StatsYearMonth(state.today.year, state.today.month.ordinal)
        if (next.year > todayMonth.year || (next.year == todayMonth.year && next.monthZeroBased > todayMonth.monthZeroBased)) return
        state = state.copy(viewMonth = next)
        scheduleReload()
    }

    fun refresh() {
        scheduleReload()
    }

    private fun scheduleReload() {
        if (!savedStatsLanguageLoaded) return
        val today = currentLocalDate(clock)
        val langCode = state.selectedLanguage.code
        val viewMonth = state.viewMonth
        val requestId = ++reloadRequestId
        state = state.copy(today = today, isLoading = true)
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.Default) {
                    statsService.statsScreenData(langCode, viewMonth, today)
                }
            }.onSuccess { data ->
                if (!isCurrentReload(requestId)) return@launch
                state = state.copy(
                    today = today,
                    isLoading = false,
                    streakDays = data.streakDays,
                    practiceLog = data.practiceLog,
                    reviewsToday = data.reviewsToday,
                    reviewsWeek = data.reviewsWeek,
                    wordsTotal = data.wordsTotal,
                    pipeline = data.pipeline,
                    delayedDueLemmaCount = data.delayedDueLemmaCount,
                    delayedDueCardCount = data.delayedDueCardCount,
                )
            }.onFailure {
                if (isCurrentReload(requestId)) {
                    state = state.copy(isLoading = false)
                }
            }
        }
    }

    private fun isCurrentReload(requestId: Int): Boolean =
        reloadRequestId == requestId

    private fun selectedLanguageFor(languages: List<Language>): Language {
        val saved = savedStatsLanguage
        return when {
            saved != null && saved in languages -> saved
            state.selectedLanguage in languages -> state.selectedLanguage
            else -> languages.first()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    viewModel: StatsViewModel,
    learningLanguages: List<Language>,
    wordDetailLabel: String? = null,
    onNavigateToSearch: () -> Unit = {},
    onNavigateToFavorites: () -> Unit = {},
    onNavigateToWordDetail: () -> Unit = {},
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
                "reviews_today" to loaded.reviewsToday.toLong(),
                "reviews_week" to loaded.reviewsWeek.toLong(),
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
        wordDetailLabel = wordDetailLabel,
        onNavigateToSearch = onNavigateToSearch,
        onNavigateToFavorites = onNavigateToFavorites,
        onNavigateToWordDetail = onNavigateToWordDetail,
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
    wordDetailLabel: String? = null,
    onNavigateToSearch: () -> Unit = {},
    onNavigateToFavorites: () -> Unit = {},
    onNavigateToWordDetail: () -> Unit = {},
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
                onNavigateToWordDetail = onNavigateToWordDetail,
                wordDetailLabel = wordDetailLabel,
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
                    .padding(horizontal = 16.dp)
                    .padding(top = 4.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                StreakCard(state = state, onStepMonth = onStepMonth)
                SectionHeader(text = stringResource(Res.string.stats_reviews_section))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCell(
                        value = state.reviewsToday,
                        unit = pluralStringResource(Res.plurals.stats_cards_unit, state.reviewsToday),
                        label = stringResource(Res.string.stats_today_label),
                        isLoading = state.isLoading,
                        modifier = Modifier.weight(1f),
                    )
                    StatCell(
                        value = state.reviewsWeek,
                        unit = pluralStringResource(Res.plurals.stats_cards_unit, state.reviewsWeek),
                        label = stringResource(Res.string.stats_this_week_label),
                        isLoading = state.isLoading,
                        modifier = Modifier.weight(1f),
                    )
                }
                SectionHeader(text = stringResource(Res.string.stats_library_section))
                LibraryCard(state = state)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
        horizontalArrangement = Arrangement.spacedBy(12.dp),
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
            StatsLanguageDropdown(
                languages = state.learningLanguages,
                selectedLanguage = state.selectedLanguage,
                expanded = state.languageDropdownExpanded,
                onExpandedChange = onLanguageDropdownExpandedChange,
                onSelectedLanguageChange = onSelectedLanguageChange,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatsLanguageDropdown(
    languages: List<Language>,
    selectedLanguage: Language,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelectedLanguageChange: (Language) -> Unit,
) {
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = onExpandedChange,
    ) {
        Surface(
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
                .height(56.dp)
                .widthIn(min = 56.dp),
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 1.dp,
            shadowElevation = 1.dp,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = selectedLanguage.flag,
                    style = MaterialTheme.typography.bodyLarge,
                )
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            }
        }
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            modifier = Modifier.width(200.dp),
            shape = MaterialTheme.shapes.small,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 2.dp,
        ) {
            languages.forEach { language ->
                DropdownMenuItem(
                    text = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(language.flag)
                            Text(language.selfName)
                        }
                    },
                    onClick = {
                        onSelectedLanguageChange(language)
                        onExpandedChange(false)
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

@Composable
private fun StreakCard(
    state: StatsUiState,
    onStepMonth: (Int) -> Unit,
) {
    StatsCard(
        shape = RoundedCornerShape(18.dp),
        padding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CountText(
                        text = formatCount(state.streakDays, state.isLoading),
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontFamily = MaterialTheme.serifFontFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 36.sp,
                            lineHeight = 32.sp,
                            letterSpacing = (-0.8).sp,
                        ),
                        color = loadingAwareContentColor(state.isLoading),
                    )
                    Text(
                        text = pluralStringResource(Res.plurals.stats_day_streak, state.streakDays),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = MaterialTheme.serifFontFamily,
                            fontStyle = FontStyle.Italic,
                            fontSize = 13.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 2.dp),
                    )
                }
                MonthStepper(
                    viewMonth = state.viewMonth,
                    today = state.today,
                    onStepMonth = onStepMonth,
                )
            }
            CalendarGrid(state = state)
        }
    }
}

@Composable
private fun MonthStepper(
    viewMonth: StatsYearMonth,
    today: LocalDate,
    onStepMonth: (Int) -> Unit,
) {
    val atCurrentMonth = viewMonth.year == today.year && viewMonth.monthZeroBased == today.month.ordinal
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = { onStepMonth(-1) },
            modifier = Modifier.size(26.dp),
        ) {
            Text(
                text = "‹",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = "${statsMonthName(viewMonth.monthZeroBased)} ${viewMonth.year}",
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.5.sp,
                letterSpacing = 0.1.sp,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(94.dp),
        )
        IconButton(
            onClick = { onStepMonth(1) },
            enabled = !atCurrentMonth,
            modifier = Modifier.size(26.dp),
        ) {
            Text(
                text = "›",
                style = MaterialTheme.typography.bodyMedium,
                color = if (atCurrentMonth) {
                    MaterialTheme.colorScheme.outlineVariant
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun CalendarGrid(state: StatsUiState) {
    val cells = calendarCells(state.viewMonth)
    val weekdays = listOf(
        stringResource(Res.string.stats_weekday_initial_monday),
        stringResource(Res.string.stats_weekday_initial_tuesday),
        stringResource(Res.string.stats_weekday_initial_wednesday),
        stringResource(Res.string.stats_weekday_initial_thursday),
        stringResource(Res.string.stats_weekday_initial_friday),
        stringResource(Res.string.stats_weekday_initial_saturday),
        stringResource(Res.string.stats_weekday_initial_sunday),
    )
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                weekdays.forEach { day ->
                    Text(
                        text = day,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            cells.chunked(7).forEach { week ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    week.forEach { day ->
                        CalendarCell(day = day, state = state, modifier = Modifier.weight(1f))
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LegendDot(
                    color = MaterialTheme.colorScheme.primary,
                    label = stringResource(Res.string.stats_legend_today),
                    borderStroke = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                )
                LegendDot(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    label = stringResource(Res.string.stats_legend_practiced),
                    borderStroke = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                )
                LegendDot(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    label = stringResource(Res.string.stats_legend_missed),
                    borderStroke = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                )
            }
        }
    }
}

@Composable
private fun CalendarCell(
    day: Int?,
    state: StatsUiState,
    modifier: Modifier = Modifier,
) {
    if (day == null) {
        Spacer(modifier = modifier.aspectRatio(1f))
        return
    }

    val date = LocalDate(state.viewMonth.year, state.viewMonth.monthZeroBased + 1, day)
    val isToday = date == state.today
    val isFuture = date > state.today
    val practiced = StatsPracticeDay(state.viewMonth.year, state.viewMonth.monthZeroBased, day) in state.practiceLog
    val shape = RoundedCornerShape(7.dp)
    val background: Color
    val content: Color
    val weight: FontWeight
    when {
        state.isLoading -> {
            background = MaterialTheme.colorScheme.surfaceContainerHighest
            content = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            weight = FontWeight.Medium
        }

        isToday -> {
            background = MaterialTheme.colorScheme.primary
            content = MaterialTheme.colorScheme.onPrimary
            weight = FontWeight.Bold
        }

        practiced -> {
            background = MaterialTheme.colorScheme.primaryContainer
            content = MaterialTheme.colorScheme.onPrimaryContainer
            weight = FontWeight.SemiBold
        }

        isFuture -> {
            background = Color.Transparent
            content = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
            weight = FontWeight.Medium
        }

        else -> {
            background = MaterialTheme.colorScheme.surfaceContainerHighest
            content = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            weight = FontWeight.Medium
        }
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(background)
            .then(
                if (!state.isLoading && isToday) {
                    Modifier.border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                        shape = shape,
                    )
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = day.toString(),
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = MaterialTheme.serifFontFamily,
                fontSize = 12.sp,
                lineHeight = 12.sp,
                fontWeight = weight,
            ),
            color = content,
        )
    }
}

@Composable
private fun LegendDot(color: Color, label: String, borderStroke: BorderStroke) {
    val shape = RoundedCornerShape(3.dp)
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(11.dp)
                .clip(shape)
                .background(color)
                .border(
                    borderStroke,
                    shape,
                ),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            ),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall.copy(
            fontFamily = MaterialTheme.serifFontFamily,
            fontStyle = FontStyle.Italic,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            letterSpacing = 0.1.sp,
        ),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 0.dp),
    )
}

@Composable
private fun StatCell(
    value: Int,
    unit: String,
    label: String,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
) {
    StatsCard(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                CountText(
                    text = formatCount(value, isLoading),
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontFamily = MaterialTheme.serifFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 32.sp,
                        lineHeight = 32.sp,
                        letterSpacing = (-0.5).sp,
                    ),
                    color = loadingAwareContentColor(isLoading),
                )
                Text(
                    text = unit,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = MaterialTheme.serifFontFamily,
                        fontStyle = FontStyle.Italic,
                        fontSize = 13.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = MaterialTheme.serifFontFamily,
                    fontStyle = FontStyle.Italic,
                    fontSize = 12.5.sp,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LibraryCard(state: StatsUiState) {
    StatsCard(
        shape = RoundedCornerShape(16.dp),
        padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                LibraryMetric(
                    value = state.wordsTotal,
                    label = pluralStringResource(Res.plurals.stats_words_label, state.wordsTotal),
                    isLoading = state.isLoading,
                    modifier = Modifier.weight(1f),
                )
                LibraryMetric(
                    value = state.sensesTotal,
                    label = pluralStringResource(Res.plurals.stats_senses_label, state.sensesTotal),
                    isLoading = state.isLoading,
                    modifier = Modifier.weight(1f),
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
            PipelineBars(pipeline = state.pipeline, isLoading = state.isLoading)
            PipelineCaption()
        }
    }
}

@Composable
private fun LibraryMetric(
    value: Int,
    label: String,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        CountText(
            text = formatCount(value, isLoading),
            style = MaterialTheme.typography.headlineMedium.copy(
                fontFamily = MaterialTheme.serifFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 30.sp,
                lineHeight = 30.sp,
                letterSpacing = (-0.5).sp,
            ),
            color = loadingAwareContentColor(isLoading),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = MaterialTheme.serifFontFamily,
                fontStyle = FontStyle.Italic,
                fontSize = 13.sp,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PipelineBars(pipeline: List<StatsPipelineStage>, isLoading: Boolean) {
    val maxCount = pipeline.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
    val countColumnWidth = if (pipeline.any { formatCount(it.count, isLoading = false).length > 3 }) 52.dp else 36.dp
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        pipeline.forEach { stage ->
            val pct = if (isLoading) {
                loadingPipelineWidth(stage.id)
            } else {
                stage.count.toFloat() / maxCount.toFloat()
            }
            val animatedPct by animateFloatAsState(
                targetValue = pct,
                animationSpec = tween(durationMillis = STATS_REVEAL_ANIMATION_MS),
                label = "statsPipelineWidth",
            )
            val targetColor = if (isLoading) {
                MaterialTheme.colorScheme.outlineVariant
            } else {
                stageColor(stage.id)
            }
            val animatedColor by animateColorAsState(
                targetValue = targetColor,
                animationSpec = tween(durationMillis = STATS_REVEAL_ANIMATION_MS),
                label = "statsPipelineColor",
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stageLabel(stage.id).uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 0.4.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.width(72.dp),
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedPct)
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(animatedColor),
                    )
                }
                CountText(
                    text = formatCount(stage.count, isLoading),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = MaterialTheme.serifFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                        lineHeight = 15.sp,
                        letterSpacing = (-0.1).sp,
                    ),
                    color = loadingAwareContentColor(isLoading),
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(countColumnWidth),
                )
            }
        }
    }
}

@Composable
private fun PipelineCaption() {
    val learned = stringResource(Res.string.stats_stage_learned)
    val captionText = stringResource(Res.string.stats_caption_learned, learned)
    val learnedStart = captionText.indexOf(learned)
    val caption = buildAnnotatedString {
        if (learnedStart == -1) {
            append(captionText)
            return@buildAnnotatedString
        }
        append(captionText.substring(0, learnedStart))
        withStyle(
            SpanStyle(
                color = MaterialTheme.colorScheme.onSurface,
                fontStyle = FontStyle.Normal,
                fontWeight = FontWeight.Medium,
            )
        ) {
            append(learned)
        }
        append(captionText.substring(learnedStart + learned.length))
    }
    Text(
        text = caption,
        style = MaterialTheme.typography.bodySmall.copy(
            fontFamily = MaterialTheme.serifFontFamily,
            fontStyle = FontStyle.Italic,
            fontSize = 12.sp,
            lineHeight = 17.sp,
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 2.dp),
    )
}

@Composable
private fun CountText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    textAlign: TextAlign? = null,
) {
    Crossfade(
        targetState = CountTextTarget(text, color),
        animationSpec = tween(durationMillis = STATS_REVEAL_ANIMATION_MS),
        modifier = modifier,
        label = "statsCountText",
    ) { target ->
        Text(
            text = target.text,
            style = style,
            color = target.color,
            maxLines = 1,
            softWrap = false,
            textAlign = textAlign,
            modifier = if (textAlign == null) Modifier else Modifier.fillMaxWidth(),
        )
    }
}

private data class CountTextTarget(
    val text: String,
    val color: Color,
)

@Composable
private fun StatsCard(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape,
    padding: PaddingValues,
    content: @Composable () -> Unit,
) {
    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline),
    ) {
        Box(modifier = Modifier.padding(padding)) {
            content()
        }
    }
}

@Composable
private fun statsMonthName(month: Int): String = when (month) {
    0 -> stringResource(Res.string.stats_month_january)
    1 -> stringResource(Res.string.stats_month_february)
    2 -> stringResource(Res.string.stats_month_march)
    3 -> stringResource(Res.string.stats_month_april)
    4 -> stringResource(Res.string.stats_month_may)
    5 -> stringResource(Res.string.stats_month_june)
    6 -> stringResource(Res.string.stats_month_july)
    7 -> stringResource(Res.string.stats_month_august)
    8 -> stringResource(Res.string.stats_month_september)
    9 -> stringResource(Res.string.stats_month_october)
    10 -> stringResource(Res.string.stats_month_november)
    else -> stringResource(Res.string.stats_month_december)
}

@Composable
private fun stageLabel(stage: StatsPipelineStageId): String = when (stage) {
    StatsPipelineStageId.QUEUE -> stringResource(Res.string.stats_stage_queue)
    StatsPipelineStageId.NEW -> stringResource(Res.string.stats_stage_new)
    StatsPipelineStageId.FRESH -> stringResource(Res.string.stats_stage_fresh)
    StatsPipelineStageId.MIDDLE -> stringResource(Res.string.stats_stage_middle)
    StatsPipelineStageId.STRONG -> stringResource(Res.string.stats_stage_strong)
    StatsPipelineStageId.LEARNED -> stringResource(Res.string.stats_stage_learned)
}

private fun stageColor(stage: StatsPipelineStageId): Color = when (stage) {
    StatsPipelineStageId.QUEUE -> Color(0xFF9B8FB8)
    StatsPipelineStageId.NEW -> Color(0xFFC8B59A)
    StatsPipelineStageId.FRESH -> Color(0xFFD9866C)
    StatsPipelineStageId.MIDDLE -> Color(0xFFD6A85C)
    StatsPipelineStageId.STRONG -> Color(0xFF6E9CB0)
    StatsPipelineStageId.LEARNED -> Color(0xFF7CB078)
}

// Visual-only skeleton proportions; the loaded values come from the stats pipeline.
private fun loadingPipelineWidth(stage: StatsPipelineStageId): Float = when (stage) {
    StatsPipelineStageId.QUEUE -> 0.66f
    StatsPipelineStageId.NEW -> 0.74f
    StatsPipelineStageId.FRESH -> 0.58f
    StatsPipelineStageId.MIDDLE -> 0.82f
    StatsPipelineStageId.STRONG -> 0.48f
    StatsPipelineStageId.LEARNED -> 0.64f
}

@Composable
private fun loadingAwareContentColor(isLoading: Boolean): Color =
    if (isLoading) {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    } else {
        MaterialTheme.colorScheme.onSurface
    }

private fun calendarCells(viewMonth: StatsYearMonth): List<Int?> {
    val first = LocalDate(viewMonth.year, viewMonth.monthZeroBased + 1, 1)
    val leading = first.dayOfWeek.ordinal
    val cells = mutableListOf<Int?>()
    repeat(leading) { cells += null }
    for (day in 1..daysInMonth(viewMonth.year, viewMonth.monthZeroBased)) {
        cells += day
    }
    while (cells.size % 7 != 0) cells += null
    return cells
}

private fun daysInMonth(year: Int, month: Int): Int = when (month) {
    0, 2, 4, 6, 7, 9, 11 -> 31
    3, 5, 8, 10 -> 30
    else -> if (isLeapYear(year)) 29 else 28
}

private fun isLeapYear(year: Int): Boolean =
    (year % 4 == 0 && year % 100 != 0) || year % 400 == 0

@OptIn(ExperimentalTime::class)
private fun initialStatsState(
    languages: List<Language>,
    clock: Clock,
): StatsUiState {
    val normalized = languages.ifEmpty { listOf(Language.ENGLISH) }.distinct().sortedBy { it.ordinal }
    val today = currentLocalDate(clock)
    return StatsUiState(
        learningLanguages = normalized,
        selectedLanguage = normalized.first(),
        today = today,
        viewMonth = StatsYearMonth(today.year, today.month.ordinal),
        isLoading = true,
        streakDays = 0,
        practiceLog = emptySet(),
        reviewsToday = 0,
        reviewsWeek = 0,
        wordsTotal = 0,
        pipeline = StatsPipelineStageId.entries.map { StatsPipelineStage(it, 0) },
    )
}

@OptIn(ExperimentalTime::class)
private fun currentLocalDate(clock: Clock): LocalDate =
    clock.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

private fun previewStatsState(languages: List<Language>, today: LocalDate): StatsUiState {
    val normalized = languages.ifEmpty { listOf(Language.ENGLISH) }.distinct().sortedBy { it.ordinal }
    val selected = normalized.first()
    val viewMonth = StatsYearMonth(today.year, today.month.ordinal)
    val variant = selected.ordinal % 4
    return StatsUiState(
        learningLanguages = normalized,
        selectedLanguage = selected,
        today = today,
        viewMonth = viewMonth,
        isLoading = false,
        streakDays = 12 + variant,
        practiceLog = previewPracticeLog(today),
        reviewsToday = 28 + variant * 3,
        reviewsWeek = 184 + variant * 11,
        wordsTotal = 3_008 + variant * 19,
        pipeline = previewPipeline(variant),
    )
}

private fun previewPipeline(variant: Int): List<StatsPipelineStage> = listOf(
    StatsPipelineStage(StatsPipelineStageId.QUEUE, 3_485 + variant),
    StatsPipelineStage(StatsPipelineStageId.NEW, 47 + variant * 2),
    StatsPipelineStage(StatsPipelineStageId.FRESH, 86 + variant * 3),
    StatsPipelineStage(StatsPipelineStageId.MIDDLE, 132 + variant * 4),
    StatsPipelineStage(StatsPipelineStageId.STRONG, 91 + variant * 3),
    StatsPipelineStage(StatsPipelineStageId.LEARNED, 64 + variant * 2),
)

private fun previewPracticeLog(today: LocalDate): Set<StatsPracticeDay> {
    val currentMonth = today.month.ordinal
    val previousMonth = if (currentMonth == 0) 11 else currentMonth - 1
    val previousYear = if (currentMonth == 0) today.year - 1 else today.year
    val previousMonthDays = daysInMonth(previousYear, previousMonth)
    val log = mutableSetOf<StatsPracticeDay>()

    for (day in 1..today.day) {
        if (day % 4 != 0) {
            log += StatsPracticeDay(today.year, currentMonth, day)
        }
    }
    val startPrevious = (previousMonthDays - 26).coerceAtLeast(1)
    for (day in startPrevious..previousMonthDays) {
        if (day % 7 != 0) {
            log += StatsPracticeDay(previousYear, previousMonth, day)
        }
    }
    return log
}

private fun formatCount(value: Int, isLoading: Boolean): String {
    if (isLoading) return "--"
    val raw = value.toString()
    if (raw.length <= 3) return raw
    return raw.reversed().chunked(3).joinToString(",").reversed()
}

private const val STATS_REVEAL_ANIMATION_MS = 260

@Preview
@Composable
private fun StatsScreenPreview(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean,
) {
    ThemedPreview(darkTheme = isDark) {
        StatsScreenContent(
            state = previewStatsState(
                languages = listOf(Language.DUTCH, Language.GERMAN),
                today = LocalDate(2026, 5, 8),
            ),
        )
    }
}

@Preview
@Composable
private fun StatsScreenSingleLanguagePreview(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean,
) {
    ThemedPreview(darkTheme = isDark) {
        StatsScreenContent(
            state = previewStatsState(
                languages = listOf(Language.DUTCH),
                today = LocalDate(2026, 5, 8),
            ),
        )
    }
}

@Preview
@Composable
private fun StatsScreenLoadingPreview(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean,
) {
    ThemedPreview(darkTheme = isDark) {
        StatsScreenContent(
            state = initialStatsState(
                languages = listOf(Language.DUTCH, Language.GERMAN),
                clock = Clock.System,
            ),
        )
    }
}
