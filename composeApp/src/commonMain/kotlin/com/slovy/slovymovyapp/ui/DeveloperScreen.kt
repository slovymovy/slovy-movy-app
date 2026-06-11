package com.slovy.slovymovyapp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.ScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeveloperMode
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.export.AppDataImporter
import com.slovy.slovymovyapp.data.favorites.CardFamilyDebugCount
import com.slovy.slovymovyapp.data.favorites.CardScheduleDebugStats
import com.slovy.slovymovyapp.data.favorites.CardTableDebugRow
import com.slovy.slovymovyapp.data.favorites.FavoritesRepository
import com.slovy.slovymovyapp.data.learning.intake.IntakeResult
import com.slovy.slovymovyapp.data.learning.intake.IntakeRunMode
import com.slovy.slovymovyapp.data.learning.intake.LearningIntake
import com.slovy.slovymovyapp.logging.AppLogLevel
import com.slovy.slovymovyapp.logging.AppLogSink
import com.slovy.slovymovyapp.logging.AppLogger
import com.slovy.slovymovyapp.logging.NoOpAppLogSink
import com.slovy.slovymovyapp.ui.study.StudySessionCompleteContent
import com.slovy.slovymovyapp.ui.study.StudySessionCompletePreviewCase
import com.slovy.slovymovyapp.ui.study.StudySessionCompletePreviewData
import com.slovy.slovymovyapp.ui.theme.AppSpacing
import com.slovy.slovymovyapp.ui.theme.serifFontFamily
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import slovymovyapp.composeapp.generated.resources.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

data class TimeShiftOption(val label: String, val duration: Duration)

private val TimeShiftOptions: List<TimeShiftOption> = listOf(
    TimeShiftOption("+1m", 1.minutes),
    TimeShiftOption("+10m", 10.minutes),
    TimeShiftOption("+1h", 1.hours),
    TimeShiftOption("+10h", 10.hours),
    TimeShiftOption("+1d", 1.days),
    TimeShiftOption("+10d", 10.days),
)

private const val CardTablePageSize = 50

private val CardTableColumnWidths = listOf(
    120.dp, // lemma
    72.dp, // lang_code
    180.dp, // family
    120.dp, // state
    96.dp, // stability
    96.dp, // difficulty
    108.dp, // due
    112.dp, // last_review
    72.dp, // reps
    72.dp, // lapses
    112.dp, // created_at
    132.dp, // available_after
    140.dp, // answer_key
    96.dp, // suspended
    260.dp, // sense_id
)

private val CardTableTotalWidth = CardTableColumnWidths
    .fold(0.dp) { total, width -> total + width }
    .let { width ->
        (1 until CardTableColumnWidths.size).fold(width) { total, _ -> total + AppSpacing.sm }
    }

private val IntakeRunMode.displayLabel: String
    get() = when (this) {
        IntakeRunMode.DAILY -> "Daily"
        IntakeRunMode.CONTINUE_NOW -> "Continue now"
    }

data class DeveloperUiState(
    val isBusy: Boolean = false,
    val currentActionLabel: String? = null,
    val terminalLines: List<String> = emptyList(),
    val terminalRevision: Long = 0,
    val scheduleStats: DeveloperScheduleStats = DeveloperScheduleStats(),
    val isStatsLoading: Boolean = false,
    val statsErrorLabel: String? = null,
    val cardTableRows: List<DeveloperCardTableRow> = emptyList(),
    val cardTablePage: DeveloperCardTablePageInfo = DeveloperCardTablePageInfo(),
    val isTableLoading: Boolean = false,
    val tableErrorLabel: String? = null,
    val isAppDataImportSupported: Boolean = false,
)

data class DeveloperScheduleStats(
    val futureScheduledCards: Long = 0,
    val futureScheduledLemmas: Long = 0,
    val availableAfterSuppressedCards: Long = 0,
    val availableAfterSuppressedLemmas: Long = 0,
    val familyCounts: List<DeveloperFamilyCount> = emptyList(),
)

data class DeveloperCardTableRow(
    val cells: List<String>,
)

data class DeveloperCardTablePageInfo(
    val pageIndex: Int = 0,
    val pageSize: Int = CardTablePageSize,
    val totalRows: Long = 0,
) {
    val firstVisibleRow: Long
        get() = if (totalRows == 0L) 0L else pageIndex.toLong() * pageSize.toLong() + 1L

    val lastVisibleRow: Long
        get() = minOf(totalRows, (pageIndex.toLong() + 1L) * pageSize.toLong())

    val canGoPrevious: Boolean
        get() = pageIndex > 0

    val canGoNext: Boolean
        get() = lastVisibleRow < totalRows
}

data class DeveloperFamilyCount(
    val family: String,
    val cardCount: Long,
)

private data class DeveloperCardTablePageResult(
    val rows: List<DeveloperCardTableRow>,
    val pageInfo: DeveloperCardTablePageInfo,
)

class DeveloperViewModel(
    private val favoritesRepository: FavoritesRepository,
    private val intake: LearningIntake,
    private val learningLanguagesProvider: suspend () -> List<Language>,
    private val appDataImporter: AppDataImporter,
) : ViewModel() {

    private val previousDeveloperLogger = AppLogger.developerLogger
    private val developerLogSink = object : AppLogSink {
        override fun log(level: AppLogLevel, tag: String, message: String, throwable: Throwable?) {
            viewModelScope.launch {
                syncTerminalLogs()
            }
        }
    }

    var state by mutableStateOf(
        DeveloperUiState(
            isAppDataImportSupported = appDataImporter.isSupported,
        )
    )
        private set

    val scrollState = LazyListState()
    val terminalScrollState = ScrollState(0)
    val tableHorizontalScrollState = ScrollState(0)
    val snackbarHostState = SnackbarHostState()

    val timeShiftOptions: List<TimeShiftOption> = TimeShiftOptions
    val intakeModes: List<IntakeRunMode> = IntakeRunMode.entries
    private var tableLoadJob: Job? = null

    init {
        AppLogger.developerLogger = developerLogSink
        syncTerminalLogs()
        startTerminalLogPolling()
        refreshDeveloperData()
    }

    fun shiftTimeBack(option: TimeShiftOption) {
        runAction(actionName = "Shift ${option.label}") {
            favoritesRepository.shiftLearningTimestampsBack(option.duration)
            "Shifted learning time ${option.label}"
        }
    }

    fun runIntake(mode: IntakeRunMode) {
        runAction(actionName = "${mode.displayLabel} intake") {
            val languages = learningLanguagesProvider()
            if (languages.isEmpty()) {
                "${mode.displayLabel} intake: no learning languages installed"
            } else {
                val results = languages.map { lang -> lang.code to runIntakeFor(mode, lang.code) }
                "${mode.displayLabel} - ${formatIntakeSummary(results)}"
            }
        }
    }

    fun removeSuspendedLearningCards() {
        runAction(actionName = "Remove suspended cards") {
            val removed = favoritesRepository.removeSuspendedLearningCards()
            "Removed $removed suspended cards"
        }
    }

    fun removeAllLearningCards() {
        runAction(actionName = "Remove all learning cards") {
            val removed = favoritesRepository.removeAllLearningCards()
            "Removed $removed learning cards"
        }
    }

    fun stageAppDataImport() {
        runAction(actionName = "Stage data import") {
            val result = appDataImporter.stageAppDataImport()
            if (result == null) {
                "Import cancelled."
            } else {
                "Import staged from ${result.artifactName}. Restart the app to apply it."
            }
        }
    }

    fun clearTerminalLogs() {
        AppLogger.clearDeveloperLogs()
        terminalLogSignature = ""
        syncTerminalLogs()
    }

    fun previousCardTablePage() {
        if (state.cardTablePage.canGoPrevious) {
            refreshCardTableRows(state.cardTablePage.pageIndex - 1)
        }
    }

    fun nextCardTablePage() {
        if (state.cardTablePage.canGoNext) {
            refreshCardTableRows(state.cardTablePage.pageIndex + 1)
        }
    }

    private fun runAction(actionName: String, work: suspend () -> String) {
        if (state.isBusy) return
        AppLogger.info(TAG, "Action started: $actionName", null)
        state = state.copy(isBusy = true, currentActionLabel = actionName)
        viewModelScope.launch {
            var message: String
            var duration: SnackbarDuration
            try {
                // Work runs on IO so heavy callers (intake, bulk SQL) don't block the UI thread,
                // even if their internals already dispatch.
                val result = withContext(Dispatchers.IO) { work() }
                AppLogger.info(TAG, "Action finished: $result", null)
                message = result
                duration = SnackbarDuration.Long
            } catch (e: Exception) {
                val error = "$actionName failed: ${describe(e)}"
                AppLogger.warn(TAG, error, e)
                message = error
                duration = SnackbarDuration.Long
            } finally {
                // Clear before showing the snackbar so "Working…" disappears first.
                state = state.copy(isBusy = false, currentActionLabel = null)
            }
            refreshDeveloperData()
            snackbarHostState.showSnackbar(message = message, duration = duration)
        }
    }

    private fun describe(e: Throwable): String =
        "${e::class.simpleName ?: "Error"}: ${e.message ?: "no message"}"

    private var terminalLogSignature: String = ""

    private fun syncTerminalLogs() {
        val recentLogs = AppLogger.recentDeveloperLogs()
        val signature = recentLogs.developerLogSignature()
        if (signature != terminalLogSignature) {
            terminalLogSignature = signature
            state = state.copy(
                terminalLines = recentLogs.map { it.toDeveloperTerminalLine() },
                terminalRevision = state.terminalRevision + 1,
            )
        }
    }

    private fun startTerminalLogPolling() {
        viewModelScope.launch {
            while (true) {
                syncTerminalLogs()
                delay(500.milliseconds)
            }
        }
    }

    private suspend fun runIntakeFor(mode: IntakeRunMode, langCode: String): IntakeResult =
        when (mode) {
            IntakeRunMode.DAILY -> intake.runIntake(langCode)
            IntakeRunMode.CONTINUE_NOW -> intake.continueWithPendingFavoritesNow(langCode)
        }

    private fun formatIntakeSummary(results: List<Pair<String, IntakeResult>>): String {
        val perLang = results.joinToString(", ") { (code, r) ->
            "$code:+${r.cardsCreated}/${r.activated.size}"
        }
        val totalCards = results.sumOf { it.second.cardsCreated }
        val totalSenses = results.sumOf { it.second.activated.size }
        val skipBreakdown = results.flatMap { it.second.skipped }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .joinToString(", ") { (reason, count) -> "${count}x${reason.name}" }
        val skipPart = if (skipBreakdown.isEmpty()) "" else " | skipped: $skipBreakdown"
        return "$perLang | total: ${totalCards}c/${totalSenses}s$skipPart"
    }

    private fun refreshDeveloperData() {
        refreshSchedulingStats()
        refreshCardTableRows()
    }

    private fun refreshSchedulingStats() {
        state = state.copy(isStatsLoading = true, statsErrorLabel = null)
        viewModelScope.launch {
            try {
                val stats = withContext(Dispatchers.IO) { loadSchedulingStats() }
                state = state.copy(
                    scheduleStats = stats,
                    isStatsLoading = false,
                    statsErrorLabel = null,
                )
            } catch (e: Exception) {
                AppLogger.warn(TAG, "Card schedule stats failed", e)
                state = state.copy(
                    isStatsLoading = false,
                    statsErrorLabel = describe(e),
                )
            }
        }
    }

    private fun refreshCardTableRows(pageIndex: Int = state.cardTablePage.pageIndex) {
        tableLoadJob?.cancel()
        val requestedPageIndex = pageIndex.coerceAtLeast(0)
        state = state.copy(
            cardTablePage = state.cardTablePage.copy(pageIndex = requestedPageIndex),
            isTableLoading = true,
            tableErrorLabel = null,
        )
        tableLoadJob = viewModelScope.launch {
            try {
                val now = Clock.System.now().toEpochMilliseconds()
                val page = withContext(Dispatchers.IO) {
                    loadCardTablePage(now, requestedPageIndex)
                }
                state = state.copy(
                    cardTableRows = page.rows,
                    cardTablePage = page.pageInfo,
                    isTableLoading = false,
                    tableErrorLabel = null,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.warn(TAG, "Card table load failed", e)
                state = state.copy(
                    isTableLoading = false,
                    tableErrorLabel = describe(e),
                )
            }
        }
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun loadSchedulingStats(): DeveloperScheduleStats {
        val languages = learningLanguagesProvider()
        val now = Clock.System.now().toEpochMilliseconds()
        val perLanguage: List<CardScheduleDebugStats> = languages.map { language ->
            favoritesRepository.getCardScheduleDebugStats(language, now)
        }
        val familyCounts = favoritesRepository.getCardFamilyDebugCounts().map { it.toDeveloperFamilyCount() }
        return DeveloperScheduleStats(
            futureScheduledCards = perLanguage.sumOf { it.futureScheduledCards },
            futureScheduledLemmas = perLanguage.sumOf { it.futureScheduledLemmas },
            availableAfterSuppressedCards = perLanguage.sumOf { it.availableAfterSuppressedCards },
            availableAfterSuppressedLemmas = perLanguage.sumOf { it.availableAfterSuppressedLemmas },
            familyCounts = familyCounts,
        )
    }

    private fun CardFamilyDebugCount.toDeveloperFamilyCount(): DeveloperFamilyCount =
        DeveloperFamilyCount(
            family = family.name,
            cardCount = cardCount,
        )

    private suspend fun loadCardTablePage(
        nowEpochMs: Long,
        requestedPageIndex: Int,
    ): DeveloperCardTablePageResult {
        val totalRows = favoritesRepository.countCardTableDebugRows()
        val pageIndex = requestedPageIndex.coerceAtMost(lastCardTablePageIndex(totalRows))
        val rows = if (totalRows == 0L) {
            emptyList()
        } else {
            favoritesRepository.getCardTableDebugRows(
                pageSize = CardTablePageSize.toLong(),
                pageOffset = pageIndex.toLong() * CardTablePageSize.toLong(),
            ).map { row -> row.toDeveloperCardTableRow(nowEpochMs) }
        }
        return DeveloperCardTablePageResult(
            rows = rows,
            pageInfo = DeveloperCardTablePageInfo(
                pageIndex = pageIndex,
                pageSize = CardTablePageSize,
                totalRows = totalRows,
            ),
        )
    }

    private fun lastCardTablePageIndex(totalRows: Long): Int =
        if (totalRows <= 0L) {
            0
        } else {
            ((totalRows - 1L) / CardTablePageSize.toLong())
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt()
        }

    private fun CardTableDebugRow.toDeveloperCardTableRow(nowEpochMs: Long): DeveloperCardTableRow =
        DeveloperCardTableRow(
            cells = listOf(
                lemma ?: "-",
                langCode,
                family.name,
                state.name,
                formatDouble(stability),
                formatDouble(difficulty),
                formatDeveloperRelativeTime(due, nowEpochMs),
                formatDeveloperRelativeTime(lastReview, nowEpochMs),
                reps.toString(),
                lapses.toString(),
                formatDeveloperRelativeTime(createdAt, nowEpochMs),
                formatDeveloperRelativeTime(availableAfter, nowEpochMs),
                answerKey,
                suspended.toString(),
                senseId.toString(),
            ),
        )

    private fun formatDouble(value: Double): String =
        ((value * 100.0).toLong() / 100.0).toString()

    companion object {
        private const val TAG = "DeveloperViewModel"
    }

    override fun onCleared() {
        super.onCleared()
        if (AppLogger.developerLogger === developerLogSink) {
            AppLogger.developerLogger = previousDeveloperLogger.takeUnless { it === developerLogSink }
                ?: NoOpAppLogSink
        }
    }
}

@Composable
fun DeveloperOptionsCard(onClick: () -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(AppSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.DeveloperMode,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Box(modifier = Modifier.size(AppSpacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.settings_developer_options_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(Res.string.settings_developer_options_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperScreen(
    viewModel: DeveloperViewModel,
    isDebugBuild: Boolean,
    onBack: () -> Unit = {},
) {
    DeveloperScreenContent(
        state = viewModel.state,
        isDebugBuild = isDebugBuild,
        timeShiftOptions = viewModel.timeShiftOptions,
        intakeModes = viewModel.intakeModes,
        scrollState = viewModel.scrollState,
        terminalScrollState = viewModel.terminalScrollState,
        tableHorizontalScrollState = viewModel.tableHorizontalScrollState,
        snackbarHostState = viewModel.snackbarHostState,
        onShiftTimeBack = viewModel::shiftTimeBack,
        onRunIntake = viewModel::runIntake,
        onRemoveSuspendedCards = viewModel::removeSuspendedLearningCards,
        onRemoveAllLearningCards = viewModel::removeAllLearningCards,
        onStageAppDataImport = viewModel::stageAppDataImport,
        onClearTerminalLogs = viewModel::clearTerminalLogs,
        onPreviousCardTablePage = viewModel::previousCardTablePage,
        onNextCardTablePage = viewModel::nextCardTablePage,
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperScreenContent(
    state: DeveloperUiState,
    isDebugBuild: Boolean = true,
    timeShiftOptions: List<TimeShiftOption> = TimeShiftOptions,
    intakeModes: List<IntakeRunMode> = IntakeRunMode.entries,
    scrollState: LazyListState = LazyListState(),
    terminalScrollState: ScrollState = ScrollState(0),
    tableHorizontalScrollState: ScrollState = ScrollState(0),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onShiftTimeBack: (TimeShiftOption) -> Unit = {},
    onRunIntake: (IntakeRunMode) -> Unit = {},
    onRemoveSuspendedCards: () -> Unit = {},
    onRemoveAllLearningCards: () -> Unit = {},
    onStageAppDataImport: () -> Unit = {},
    onClearTerminalLogs: () -> Unit = {},
    onPreviousCardTablePage: () -> Unit = {},
    onNextCardTablePage: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    val backLabel = stringResource(Res.string.common_back)
    var selectedCompletionPreview by remember { mutableStateOf<StudySessionCompletePreviewCase?>(null) }
    selectedCompletionPreview?.let { preview ->
        StudySessionCompleteContent(
            reward = preview.reward,
            scrollState = rememberScrollState(),
            onClose = { selectedCompletionPreview = null },
            snackbarHostState = remember { SnackbarHostState() },
        )
        return
    }
    Surface(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            stringResource(Res.string.settings_section_developer),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontFamily = MaterialTheme.serifFontFamily,
                                fontWeight = FontWeight.Medium,
                            ),
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = backLabel,
                            )
                        }
                    },
                )
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                state = scrollState,
                contentPadding = PaddingValues(AppSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
            ) {
                if (!isDebugBuild) {
                    item { DeveloperNonDebugWarningCard() }
                }

                item {
                    DeveloperTerminalCard(
                        isBusy = state.isBusy,
                        currentActionLabel = state.currentActionLabel,
                        terminalLines = state.terminalLines,
                        terminalRevision = state.terminalRevision,
                        scrollState = terminalScrollState,
                        onClear = onClearTerminalLogs,
                    )
                }

                item {
                    SectionHeader(
                        title = stringResource(Res.string.developer_completion_gallery_title),
                        modifier = Modifier.padding(top = AppSpacing.sm),
                    )
                }
                item {
                    CompletionGalleryCard(
                        cases = StudySessionCompletePreviewData.cases,
                        onOpen = { selectedCompletionPreview = it },
                    )
                }

                item { SectionHeader(title = stringResource(Res.string.developer_time_machine_title)) }
                item {
                    TimeMachineCard(
                        options = timeShiftOptions,
                        isBusy = state.isBusy,
                        onShift = onShiftTimeBack,
                    )
                }

                item {
                    SectionHeader(
                        title = stringResource(Res.string.developer_intake_title),
                        modifier = Modifier.padding(top = AppSpacing.sm),
                    )
                }
                item {
                    IntakeCard(
                        modes = intakeModes,
                        isBusy = state.isBusy,
                        onRun = onRunIntake,
                    )
                }

                item {
                    SectionHeader(
                        title = stringResource(Res.string.developer_danger_title),
                        modifier = Modifier.padding(top = AppSpacing.sm),
                    )
                }
                item {
                    DeveloperDangerCard(
                        isDebugBuild = isDebugBuild,
                        isBusy = state.isBusy,
                        isImportSupported = state.isAppDataImportSupported,
                        onRemoveSuspendedCards = onRemoveSuspendedCards,
                        onRemoveAllLearningCards = onRemoveAllLearningCards,
                        onStageAppDataImport = onStageAppDataImport,
                    )
                }

                item {
                    SectionHeader(
                        title = stringResource(Res.string.developer_stats_title),
                        modifier = Modifier.padding(top = AppSpacing.sm),
                    )
                }
                item {
                    SchedulingStatsCard(
                        stats = state.scheduleStats,
                        isLoading = state.isStatsLoading,
                        errorLabel = state.statsErrorLabel,
                    )
                }

                item {
                    SectionHeader(
                        title = stringResource(Res.string.developer_tables_title),
                        modifier = Modifier.padding(top = AppSpacing.sm),
                    )
                }
                item {
                    CardTableCard(
                        rows = state.cardTableRows,
                        pageInfo = state.cardTablePage,
                        isLoading = state.isTableLoading,
                        errorLabel = state.tableErrorLabel,
                        horizontalScrollState = tableHorizontalScrollState,
                        onPreviousPage = onPreviousCardTablePage,
                        onNextPage = onNextCardTablePage,
                    )
                }
            }
        }
    }
}

@Composable
private fun TimeMachineCard(
    options: List<TimeShiftOption>,
    isBusy: Boolean,
    onShift: (TimeShiftOption) -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        ) {
            Text(
                text = stringResource(Res.string.developer_time_machine_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            options.chunked(3).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                ) {
                    row.forEach { option ->
                        ScaryFilledTonalButton(
                            text = option.label,
                            onClick = { onShift(option) },
                            enabled = !isBusy,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    // Pad incomplete rows so buttons keep their column widths.
                    repeat(3 - row.size) {
                        Box(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun DeveloperNonDebugWarningCard() {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
        ) {
            Icon(
                imageVector = Icons.Outlined.Warning,
                contentDescription = stringResource(Res.string.developer_non_debug_warning_icon),
            )
            Text(
                text = stringResource(Res.string.developer_non_debug_warning),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun DeveloperTerminalCard(
    isBusy: Boolean,
    currentActionLabel: String?,
    terminalLines: List<String>,
    terminalRevision: Long,
    scrollState: ScrollState,
    onClear: () -> Unit,
) {
    LaunchedEffect(terminalRevision) {
        scrollState.scrollTo(scrollState.maxValue)
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
            ) {
                Text(
                    text = stringResource(Res.string.developer_terminal_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onClear) {
                    Text(stringResource(Res.string.developer_terminal_clear))
                }
            }
            if (isBusy) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    Text(
                        text = stringResource(
                            Res.string.developer_terminal_running,
                            currentActionLabel ?: stringResource(Res.string.developer_working_status),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            SelectionContainer {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
                ) {
                    val visibleLines = if (terminalLines.isEmpty()) {
                        listOf(stringResource(Res.string.developer_terminal_ready))
                    } else {
                        terminalLines
                    }
                    visibleLines.forEach { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompletionGalleryCard(
    cases: List<StudySessionCompletePreviewCase>,
    onOpen: (StudySessionCompletePreviewCase) -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        ) {
            Text(
                text = stringResource(Res.string.developer_completion_gallery_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            cases.forEach { preview ->
                FilledTonalButton(
                    onClick = { onOpen(preview) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.Start,
                    ) {
                        Text(
                            text = preview.label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = preview.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.78f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IntakeCard(
    modes: List<IntakeRunMode>,
    isBusy: Boolean,
    onRun: (IntakeRunMode) -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        ) {
            Text(
                text = stringResource(Res.string.developer_intake_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
            ) {
                modes.forEach { mode ->
                    if (mode == IntakeRunMode.CONTINUE_NOW) {
                        ScaryFilledTonalButton(
                            text = intakeModeLabel(mode),
                            onClick = { onRun(mode) },
                            enabled = !isBusy,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        FilledTonalButton(
                            onClick = { onRun(mode) },
                            enabled = !isBusy,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(intakeModeLabel(mode))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeveloperDangerCard(
    isDebugBuild: Boolean,
    isBusy: Boolean,
    isImportSupported: Boolean,
    onRemoveSuspendedCards: () -> Unit,
    onRemoveAllLearningCards: () -> Unit,
    onStageAppDataImport: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        ) {
            Text(
                text = stringResource(Res.string.developer_danger_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ScaryFilledTonalButton(
                text = stringResource(Res.string.developer_remove_suspended_cards),
                onClick = onRemoveSuspendedCards,
                enabled = !isBusy,
                modifier = Modifier.fillMaxWidth(),
            )
            ScaryFilledTonalButton(
                text = stringResource(Res.string.developer_remove_all_learning_cards),
                onClick = onRemoveAllLearningCards,
                enabled = !isBusy && isDebugBuild,
                modifier = Modifier.fillMaxWidth(),
            )
            FilledTonalButton(
                onClick = onStageAppDataImport,
                enabled = !isBusy && isDebugBuild && isImportSupported,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.UploadFile,
                        contentDescription = null,
                    )
                    Text(stringResource(Res.string.developer_import_app_data))
                }
            }
            if (!isImportSupported) {
                Text(
                    text = stringResource(Res.string.developer_import_app_data_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ScaryFilledTonalButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier,
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Text(text)
    }
}

@Composable
private fun SchedulingStatsCard(
    stats: DeveloperScheduleStats,
    isLoading: Boolean,
    errorLabel: String?,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        ) {
            if (isLoading) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    Text(
                        text = stringResource(Res.string.developer_stats_loading),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                SchedulingStatsTable(stats = stats)
                if (stats.familyCounts.isNotEmpty()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    FamilyCountsTable(familyCounts = stats.familyCounts)
                }
            }
            if (errorLabel != null) {
                Text(
                    text = stringResource(Res.string.developer_stats_error, errorLabel),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun FamilyCountsTable(familyCounts: List<DeveloperFamilyCount>) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
    ) {
        FamilyCountsTableRow(
            family = stringResource(Res.string.developer_stats_family_header),
            count = stringResource(Res.string.developer_stats_cards_header),
            isHeader = true,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        familyCounts.forEach { row ->
            FamilyCountsTableRow(
                family = row.family,
                count = row.cardCount.toString(),
                isHeader = false,
            )
        }
    }
}

@Composable
private fun FamilyCountsTableRow(
    family: String,
    count: String,
    isHeader: Boolean,
) {
    val textStyle = if (isHeader) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium
    val valueStyle = if (isHeader) MaterialTheme.typography.labelMedium else MaterialTheme.typography.titleMedium
    val color = if (isHeader) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
    ) {
        Text(
            text = family,
            style = textStyle,
            color = color,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = count,
            style = valueStyle,
            fontWeight = if (isHeader) FontWeight.Medium else FontWeight.SemiBold,
            color = color,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.45f),
        )
    }
}

@Composable
private fun SchedulingStatsTable(stats: DeveloperScheduleStats) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
    ) {
        SchedulingStatsTableRow(
            label = stringResource(Res.string.developer_stats_metric_header),
            cards = stringResource(Res.string.developer_stats_cards_header),
            lemmas = stringResource(Res.string.developer_stats_lemmas_header),
            isHeader = true,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        SchedulingStatsTableRow(
            label = stringResource(Res.string.developer_stats_future_label),
            cards = stats.futureScheduledCards.toString(),
            lemmas = stats.futureScheduledLemmas.toString(),
            isHeader = false,
        )
        SchedulingStatsTableRow(
            label = stringResource(Res.string.developer_stats_suppressed_label),
            cards = stats.availableAfterSuppressedCards.toString(),
            lemmas = stats.availableAfterSuppressedLemmas.toString(),
            isHeader = false,
        )
    }
}

@Composable
private fun SchedulingStatsTableRow(
    label: String,
    cards: String,
    lemmas: String,
    isHeader: Boolean,
) {
    val textStyle = if (isHeader) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium
    val valueStyle = if (isHeader) MaterialTheme.typography.labelMedium else MaterialTheme.typography.titleMedium
    val color = if (isHeader) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
    ) {
        Text(
            text = label,
            style = textStyle,
            color = labelColor,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = cards,
            style = valueStyle,
            fontWeight = if (isHeader) FontWeight.Medium else FontWeight.SemiBold,
            color = color,
            modifier = Modifier.weight(0.45f),
            textAlign = TextAlign.End,
        )
        Text(
            text = lemmas,
            style = valueStyle,
            fontWeight = if (isHeader) FontWeight.Medium else FontWeight.SemiBold,
            color = color,
            modifier = Modifier.weight(0.45f),
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun CardTableCard(
    rows: List<DeveloperCardTableRow>,
    pageInfo: DeveloperCardTablePageInfo,
    isLoading: Boolean,
    errorLabel: String?,
    horizontalScrollState: ScrollState,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        ) {
            CardTablePager(
                pageInfo = pageInfo,
                isLoading = isLoading,
                onPreviousPage = onPreviousPage,
                onNextPage = onNextPage,
            )
            when {
                isLoading -> LoadingStatusRow(text = stringResource(Res.string.developer_tables_loading))
                rows.isEmpty() -> Text(
                    text = stringResource(Res.string.developer_tables_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                else -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(horizontalScrollState),
                ) {
                    Column(
                        modifier = Modifier.width(CardTableTotalWidth),
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
                    ) {
                        CardTableRow(
                            cells = cardTableHeaders(),
                            isHeader = true,
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        rows.forEach { row ->
                            CardTableRow(
                                cells = row.cells,
                                isHeader = false,
                            )
                        }
                    }
                }
            }
            if (errorLabel != null) {
                Text(
                    text = stringResource(Res.string.developer_tables_error, errorLabel),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun CardTablePager(
    pageInfo: DeveloperCardTablePageInfo,
    isLoading: Boolean,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
    ) {
        Text(
            text = stringResource(
                Res.string.developer_tables_page_status,
                pageInfo.firstVisibleRow,
                pageInfo.lastVisibleRow,
                pageInfo.totalRows,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(
            onClick = onPreviousPage,
            enabled = pageInfo.canGoPrevious && !isLoading,
        ) {
            Text(stringResource(Res.string.developer_tables_previous_page))
        }
        TextButton(
            onClick = onNextPage,
            enabled = pageInfo.canGoNext && !isLoading,
        ) {
            Text(stringResource(Res.string.developer_tables_next_page))
        }
    }
}

@Composable
private fun cardTableHeaders(): List<String> = listOf(
    stringResource(Res.string.developer_card_table_col_lemma),
    stringResource(Res.string.developer_card_table_col_lang_code),
    stringResource(Res.string.developer_card_table_col_family),
    stringResource(Res.string.developer_card_table_col_state),
    stringResource(Res.string.developer_card_table_col_stability),
    stringResource(Res.string.developer_card_table_col_difficulty),
    stringResource(Res.string.developer_card_table_col_due),
    stringResource(Res.string.developer_card_table_col_last_review),
    stringResource(Res.string.developer_card_table_col_reps),
    stringResource(Res.string.developer_card_table_col_lapses),
    stringResource(Res.string.developer_card_table_col_created_at),
    stringResource(Res.string.developer_card_table_col_available_after),
    stringResource(Res.string.developer_card_table_col_answer_key),
    stringResource(Res.string.developer_card_table_col_suspended),
    stringResource(Res.string.developer_card_table_col_sense_id),
)

@Composable
private fun CardTableRow(cells: List<String>, isHeader: Boolean) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        cells.forEachIndexed { index, cell ->
            Text(
                text = cell,
                style = if (isHeader) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodySmall,
                fontWeight = if (isHeader) FontWeight.Medium else FontWeight.Normal,
                color = if (isHeader) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(CardTableColumnWidths[index]),
            )
        }
    }
}

@Composable
private fun LoadingStatusRow(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun intakeModeLabel(mode: IntakeRunMode): String = when (mode) {
    IntakeRunMode.DAILY -> stringResource(Res.string.developer_intake_daily)
    IntakeRunMode.CONTINUE_NOW -> stringResource(Res.string.developer_intake_continue_now)
}

// === Previews ===

@Preview
@Composable
private fun DeveloperScreenPreviewIdle(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean,
) {
    ThemedPreview(darkTheme = isDark) {
        DeveloperScreenContent(state = DeveloperUiState())
    }
}

@Preview
@Composable
private fun DeveloperScreenPreviewAfterShift(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean,
) {
    ThemedPreview(darkTheme = isDark) {
        DeveloperScreenContent(
            state = DeveloperUiState(
                terminalLines = listOf("now INFO/DeveloperViewModel: Action finished: Shifted learning time +1d"),
            ),
        )
    }
}

@Preview
@Composable
private fun DeveloperScreenPreviewAfterIntake(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean,
) {
    ThemedPreview(darkTheme = isDark) {
        DeveloperScreenContent(
            state = DeveloperUiState(
                terminalLines = listOf(
                    "now INFO/DeveloperViewModel: Action finished: Daily - en:+12/4, pl:+0/0 | total: 12c/4s",
                ),
            ),
        )
    }
}

@Preview
@Composable
private fun DeveloperScreenPreviewNonDebug(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean,
) {
    ThemedPreview(darkTheme = isDark) {
        DeveloperScreenContent(
            state = DeveloperUiState(),
            isDebugBuild = false,
        )
    }
}

@Preview
@Composable
private fun DeveloperScreenPreviewBusy(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean,
) {
    ThemedPreview(darkTheme = isDark) {
        DeveloperScreenContent(
            state = DeveloperUiState(
                isBusy = true,
                currentActionLabel = "Shift +10h",
                terminalLines = listOf("now INFO/DeveloperViewModel: Action started: Shift +10h"),
            ),
        )
    }
}

@Preview
@Composable
private fun DeveloperOptionsCardPreview(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean,
) {
    ThemedPreview(darkTheme = isDark) {
        DeveloperOptionsCard(onClick = {})
    }
}

@Preview
@Composable
private fun CardTableCardPreviewWithRows(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean,
) {
    ThemedPreview(darkTheme = isDark) {
        CardTableCard(
            rows = listOf(
                DeveloperCardTableRow(
                    cells = listOf(
                        "rennen",
                        "nl",
                        "PRODUCE_WORD",
                        "REVIEW",
                        "12.34",
                        "5.67",
                        "+2h 15m",
                        "-1d 4h",
                        "8",
                        "1",
                        "-12d",
                        "+45m",
                        "rennen",
                        "false",
                        "018f4a1b-75dd-73a2-8a0e-84b53aa5e235",
                    ),
                ),
                DeveloperCardTableRow(
                    cells = listOf(
                        "house",
                        "en",
                        "RECOGNIZE_SENSE",
                        "NEW",
                        "0.0",
                        "0.0",
                        "-3m",
                        "-",
                        "0",
                        "0",
                        "-5m",
                        "-",
                        "house",
                        "false",
                        "018f4a1b-a230-70f1-a0a0-26bb6e56011a",
                    ),
                ),
            ),
            pageInfo = DeveloperCardTablePageInfo(
                pageIndex = 0,
                pageSize = CardTablePageSize,
                totalRows = 2,
            ),
            isLoading = false,
            errorLabel = null,
            horizontalScrollState = ScrollState(0),
            onPreviousPage = {},
            onNextPage = {},
        )
    }
}
