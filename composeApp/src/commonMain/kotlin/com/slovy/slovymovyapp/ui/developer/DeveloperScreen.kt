package com.slovy.slovymovyapp.ui.developer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.ScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeveloperMode
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.slovy.slovymovyapp.data.learning.intake.IntakeRunMode
import com.slovy.slovymovyapp.ui.study.StudySessionCompleteContent
import com.slovy.slovymovyapp.ui.study.StudySessionCompletePreviewCase
import com.slovy.slovymovyapp.ui.study.StudySessionCompletePreviewData
import com.slovy.slovymovyapp.ui.theme.AppSpacing
import com.slovy.slovymovyapp.ui.theme.serifFontFamily
import org.jetbrains.compose.resources.stringResource
import slovymovyapp.composeapp.generated.resources.*
import com.slovy.slovymovyapp.ui.SectionHeader
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

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
        onClearListsCache = viewModel::clearListsCache,
        onRemoveSuspendedCards = viewModel::removeSuspendedLearningCards,
        onRemoveAllLearningCards = viewModel::removeAllLearningCards,
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
    onClearListsCache: () -> Unit = {},
    onRemoveSuspendedCards: () -> Unit = {},
    onRemoveAllLearningCards: () -> Unit = {},
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
            respectReduceMotion = false,
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
                        title = stringResource(Res.string.developer_caches_title),
                        modifier = Modifier.padding(top = AppSpacing.sm),
                    )
                }
                item {
                    DeveloperCachesCard(
                        isBusy = state.isBusy,
                        onClearListsCache = onClearListsCache,
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
                        onRemoveSuspendedCards = onRemoveSuspendedCards,
                        onRemoveAllLearningCards = onRemoveAllLearningCards,
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
