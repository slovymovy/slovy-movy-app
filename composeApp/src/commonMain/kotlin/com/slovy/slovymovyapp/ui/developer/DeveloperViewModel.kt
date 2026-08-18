package com.slovy.slovymovyapp.ui.developer

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.ScrollState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.favorites.CardFamilyDebugCount
import com.slovy.slovymovyapp.data.favorites.CardScheduleDebugStats
import com.slovy.slovymovyapp.data.favorites.CardTableDebugRow
import com.slovy.slovymovyapp.data.favorites.FavoritesRepository
import com.slovy.slovymovyapp.data.learning.intake.IntakeResult
import com.slovy.slovymovyapp.data.learning.intake.IntakeRunMode
import com.slovy.slovymovyapp.data.learning.intake.LearningIntake
import com.slovy.slovymovyapp.data.lists.ListsService
import com.slovy.slovymovyapp.logging.AppLogLevel
import com.slovy.slovymovyapp.logging.AppLogSink
import com.slovy.slovymovyapp.logging.AppLogger
import com.slovy.slovymovyapp.logging.NoOpAppLogSink
import com.slovy.slovymovyapp.ui.theme.AppSpacing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import slovymovyapp.composeapp.generated.resources.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import com.slovy.slovymovyapp.ui.formatDeveloperRelativeTime
import com.slovy.slovymovyapp.ui.developerLogSignature
import com.slovy.slovymovyapp.ui.toDeveloperTerminalLine
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

data class TimeShiftOption(val label: String, val duration: Duration)

internal val TimeShiftOptions: List<TimeShiftOption> = listOf(
    TimeShiftOption("+1m", 1.minutes),
    TimeShiftOption("+10m", 10.minutes),
    TimeShiftOption("+1h", 1.hours),
    TimeShiftOption("+10h", 10.hours),
    TimeShiftOption("+1d", 1.days),
    TimeShiftOption("+10d", 10.days),
)

internal const val CardTablePageSize = 50

internal val CardTableColumnWidths = listOf(
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

internal val CardTableTotalWidth = CardTableColumnWidths
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
    private val listsService: ListsService,
    private val learningLanguagesProvider: suspend () -> List<Language>,
) : ViewModel() {

    private val previousDeveloperLogger = AppLogger.developerLogger
    private val developerLogSink = object : AppLogSink {
        override fun log(level: AppLogLevel, tag: String, message: String, throwable: Throwable?) {
            viewModelScope.launch {
                syncTerminalLogs()
            }
        }
    }

    var state by mutableStateOf(DeveloperUiState())
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

    fun clearListsCache() {
        runAction(actionName = "Clear lists cache") {
            val removed = listsService.clearCache()
            "Cleared word lists cache ($removed lists); next sync refetches from the server"
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
            } catch (e: CancellationException) {
                throw e
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
            } catch (e: CancellationException) {
                throw e
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
