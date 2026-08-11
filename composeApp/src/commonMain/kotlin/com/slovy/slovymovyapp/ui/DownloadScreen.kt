package com.slovy.slovymovyapp.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slovy.slovymovyapp.analytics.Analytics
import com.slovy.slovymovyapp.analytics.AnalyticsEvent
import com.slovy.slovymovyapp.data.remote.*
import com.slovy.slovymovyapp.ui.components.SpinningProgressIndicator
import com.slovy.slovymovyapp.ui.icons.DownloadScreenTransparent
import com.slovy.slovymovyapp.ui.icons.SlovyIcons
import com.slovy.slovymovyapp.ui.theme.AppSpacing
import com.slovy.slovymovyapp.ui.theme.serifFontFamily
import com.slovy.slovymovyapp.ui.theme.uiItalic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import slovymovyapp.composeapp.generated.resources.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime

data class DownloadItem(
    val label: String,
    val sizeBytes: Long,
    val flag: String = ""
)

class DownloadViewModel(
    private val downloadCoordinator: DownloadCoordinator,
    private val downloadKey: String,
    private val download: suspend (onProgress: (DownloadProgress) -> Unit, cancelToken: CancelToken) -> Unit,
    private val finalize: suspend (
        onRecoveryProgress: (LemmaRecoveryProgress) -> Unit,
        onWordListsSync: (Boolean) -> Unit,
    ) -> Unit = { _, _ -> },
    private val onSuccess: suspend () -> Unit,
    private val onCancel: () -> Unit,
    private val onError: (Throwable) -> Unit,
    private val loadItems: (suspend () -> List<DownloadItem>)? = null,
    private val platform: PlatformDbSupport? = null,
    private val analyticsParams: Map<String, Any> = emptyMap(),
) : ViewModel() {

    var state by mutableStateOf(
        if (loadItems != null) DownloadUiState.Loading else DownloadUiState.Idle
    )
        private set

    val hadConfirmation: Boolean = loadItems != null
    val scrollState = ScrollState(0)

    private var terminalHandled = false
    private var failedDuringLoadItems = false
    private var downloadStartedAtMs: Long = 0L

    // Held for the lifetime of one download attempt — from beginDownload() until the terminal
    // handler runs. Bridges the gap between the last per-file download (which would otherwise
    // stop the foreground service) and finalize(), so on Android 12+ we never have to attempt
    // a fresh startForegroundService from the background-observer coroutine.
    private var keepAliveHandle: ProcessKeepAlive? = null

    init {
        if (loadItems != null) {
            fetchItems()
        } else {
            val downloadFlow = beginDownload()
            observeProgress(downloadFlow)
            attachDownloadCallbacks(downloadFlow)
        }
    }

    private fun fetchItems() {
        state = DownloadUiState.Loading
        viewModelScope.launch {
            try {
                val items = loadItems!!.invoke()
                if (items.isEmpty()) {
                    startDownload()
                    return@launch
                }
                failedDuringLoadItems = false
                state = DownloadUiState.ReadyToDownload(items)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failedDuringLoadItems = true
                state = DownloadUiState.Failed(e)
            }
        }
    }

    fun startDownload() {
        if (state is DownloadUiState.Idle || state is DownloadUiState.Running) return
        terminalHandled = false
        failedDuringLoadItems = false
        state = DownloadUiState.Idle
        val downloadFlow = beginDownload()
        observeProgress(downloadFlow)
        attachDownloadCallbacks(downloadFlow)
    }

    @OptIn(ExperimentalTime::class)
    private fun beginDownload(): Flow<DownloadEntry?> {
        Analytics.logEvent(AnalyticsEvent.DOWNLOAD_DICTIONARY_CLICK, analyticsParams)
        downloadStartedAtMs = Clock.System.now().toEpochMilliseconds()
        acquireKeepAlive()
        return downloadCoordinator.startDownload(downloadKey, download)
    }

    private fun acquireKeepAlive() {
        val platform = platform ?: return
        if (keepAliveHandle != null) return
        keepAliveHandle = platform.acquireProcessKeepAlive()
    }

    private fun releaseKeepAlive() {
        val handle = keepAliveHandle ?: return
        keepAliveHandle = null
        handle.release()
    }

    private fun observeProgress(downloadFlow: Flow<DownloadEntry?>) {
        viewModelScope.launch {
            downloadFlow.collect { entry ->
                when (entry?.status ?: DownloadStatus.Idle) {
                    DownloadStatus.Idle -> {
                        if (!terminalHandled) {
                            state = DownloadUiState.Idle
                        }
                    }

                    DownloadStatus.Running -> {
                        val progress = entry?.progress
                        val percent = progress?.percent?.coerceAtLeast(0) ?: 0
                        state = DownloadUiState.Running(percent, progress?.totalBytes, progress?.currentFile)
                    }

                    // Terminal states are owned by attachDownloadCallbacks
                    else -> Unit
                }
            }
        }
    }

    @OptIn(ExperimentalTime::class)
    private fun attachDownloadCallbacks(downloadFlow: Flow<DownloadEntry?>) {
        viewModelScope.launch {
            downloadFlow.collect { entry ->
                when (entry?.status ?: DownloadStatus.Idle) {
                    DownloadStatus.Done -> {
                        if (!terminalHandled) {
                            terminalHandled = true
                            downloadCoordinator.clear(downloadKey)
                            Analytics.logEvent(
                                AnalyticsEvent.DOWNLOAD_COMPLETED,
                                analyticsParams + mapOf(
                                    "duration_ms" to (Clock.System.now().toEpochMilliseconds() - downloadStartedAtMs),
                                    "bytes" to (entry?.progress?.totalBytes ?: 0L),
                                ),
                            )
                            try {
                                state = DownloadUiState.Finalizing()
                                finalize(::updateRecoveryProgress, ::updateWordListsSyncing)
                                for (i in 3 downTo 1) {
                                    state = DownloadUiState.Done(countdown = i)
                                    delay(1_000.milliseconds)
                                }
                                onSuccess()
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                state = DownloadUiState.Failed(e)
                            } finally {
                                releaseKeepAlive()
                            }
                        }
                    }

                    DownloadStatus.Cancelled -> {
                        if (!terminalHandled) {
                            terminalHandled = true
                            state = DownloadUiState.Cancelled
                            downloadCoordinator.clear(downloadKey)
                            releaseKeepAlive()
                        }
                    }

                    DownloadStatus.Failed -> {
                        if (!terminalHandled) {
                            terminalHandled = true
                            val error = entry?.error ?: Throwable("Unknown error")
                            Analytics.logEvent(
                                AnalyticsEvent.DOWNLOAD_FAILED,
                                analyticsParams + mapOf(
                                    "duration_ms" to (Clock.System.now().toEpochMilliseconds() - downloadStartedAtMs),
                                    "error" to (error.message ?: error::class.simpleName ?: "unknown"),
                                ),
                            )
                            state = DownloadUiState.Failed(error)
                            downloadCoordinator.clear(downloadKey)
                            releaseKeepAlive()
                        }
                    }

                    else -> Unit
                }
            }
        }
    }

    fun updateRecoveryProgress(progress: LemmaRecoveryProgress) {
        // Late controller emissions can arrive after finalization has moved to a terminal state.
        val current = state
        if (current is DownloadUiState.Finalizing) {
            state = current.copy(recovery = progress)
        }
    }

    fun updateWordListsSyncing(active: Boolean) {
        val current = state
        if (current is DownloadUiState.Finalizing) {
            state = current.copy(updatingWordLists = active)
        }
    }

    fun onDismissCancel() {
        onCancel()
    }

    fun onDismissError() {
        onError((state as? DownloadUiState.Failed)?.error ?: Throwable("Unknown error"))
    }

    fun cancelDownload() {
        Analytics.logEvent(AnalyticsEvent.DOWNLOAD_CANCEL_CLICK)
        downloadCoordinator.cancel(downloadKey)
    }

    override fun onCleared() {
        super.onCleared()
        downloadCoordinator.cancel(downloadKey)
        releaseKeepAlive()
    }

    fun retry() {
        terminalHandled = false
        if (failedDuringLoadItems) {
            fetchItems()
        } else {
            state = DownloadUiState.Idle
            val downloadFlow = beginDownload()
            observeProgress(downloadFlow)
            attachDownloadCallbacks(downloadFlow)
        }
    }
}

@Composable
fun DownloadScreen(
    viewModel: DownloadViewModel,
    description: String? = null,
    onLaterClick: () -> Unit = {}
) {
    DownloadScreenContent(
        state = viewModel.state,
        scrollState = viewModel.scrollState,
        description = description,
        hadConfirmation = viewModel.hadConfirmation,
        onDownloadClick = { viewModel.startDownload() },
        onLaterClick = onLaterClick,
        onCancelClick = { viewModel.cancelDownload() },
        onRetryClick = { viewModel.retry() },
        onCloseClick = { viewModel.onDismissCancel() },
        onErrorLaterClick = { viewModel.onDismissError() }
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DownloadScreenContent(
    state: DownloadUiState,
    scrollState: ScrollState = ScrollState(0),
    description: String? = null,
    hadConfirmation: Boolean = false,
    onDownloadClick: () -> Unit = {},
    onLaterClick: () -> Unit = {},
    onCancelClick: () -> Unit = {},
    onRetryClick: () -> Unit = {},
    onCloseClick: () -> Unit = {},
    onErrorLaterClick: () -> Unit = {},
) {
    val descriptionText = description ?: stringResource(Res.string.download_description_setting_up_library)
    val hasActions = state is DownloadUiState.ReadyToDownload ||
            state is DownloadUiState.Running ||
            state is DownloadUiState.Failed ||
            state is DownloadUiState.Cancelled

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (hasActions) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = AppSpacing.xl)
                        .padding(bottom = AppSpacing.xxl),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when (state) {
                        is DownloadUiState.ReadyToDownload -> {
                            val totalSize = formatFileSize(state.items.sumOf { it.sizeBytes })
                            Button(
                                onClick = onDownloadClick,
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                shape = RoundedCornerShape(28.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text(
                                    stringResource(Res.string.download_action_download_size, totalSize),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                            }

                            Spacer(Modifier.height(AppSpacing.sm))

                            TextButton(onClick = onLaterClick) {
                                Text(
                                    stringResource(Res.string.common_later),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        is DownloadUiState.Running -> {
                            TextButton(onClick = onCancelClick) {
                                Text(
                                    stringResource(Res.string.common_cancel),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        is DownloadUiState.Failed -> {
                            OutlinedButton(onClick = onRetryClick) {
                                Text(stringResource(Res.string.common_retry))
                            }

                            Spacer(Modifier.height(AppSpacing.sm))

                            TextButton(onClick = onErrorLaterClick) {
                                Text(
                                    stringResource(Res.string.common_later),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        is DownloadUiState.Cancelled -> {
                            TextButton(onClick = onCloseClick) {
                                Text(
                                    stringResource(Res.string.common_close),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = AppSpacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(AppSpacing.xxxl))

            val (title, subtitle) = when (state) {
                is DownloadUiState.ReadyToDownload ->
                    stringResource(Res.string.download_title_ready) to stringResource(Res.string.download_subtitle_ready)

                is DownloadUiState.Failed ->
                    stringResource(Res.string.download_title_failed) to stringResource(Res.string.download_subtitle_failed)

                is DownloadUiState.Cancelled ->
                    stringResource(Res.string.download_title_cancelled) to stringResource(Res.string.download_subtitle_cancelled)

                is DownloadUiState.Done ->
                    stringResource(Res.string.download_title_done) to stringResource(Res.string.download_subtitle_done)

                is DownloadUiState.Finalizing ->
                    stringResource(Res.string.download_title_setting_up) to stringResource(Res.string.download_subtitle_setting_up)

                else -> if (hadConfirmation) {
                    stringResource(Res.string.download_title_downloading) to stringResource(Res.string.download_subtitle_downloading)
                } else {
                    stringResource(Res.string.download_title_setting_up) to stringResource(Res.string.download_subtitle_setting_up)
                }
            }

            Text(
                text = title,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = FontFamily.Default,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(AppSpacing.sm))

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = MaterialTheme.serifFontFamily,
                    fontStyle = MaterialTheme.uiItalic
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                minLines = 2
            )

            Spacer(Modifier.height(AppSpacing.xxl))

            Image(
                imageVector = SlovyIcons.DownloadScreenTransparent,
                contentDescription = null,
                modifier = Modifier.size(180.dp)
            )

            Spacer(Modifier.height(AppSpacing.xxl))

            when (state) {
                is DownloadUiState.Loading -> {
                    val loadingInfoContentDescription = stringResource(Res.string.download_content_desc_loading_info)
                    SpinningProgressIndicator(
                        modifier = Modifier.semantics {
                            contentDescription = loadingInfoContentDescription
                        }
                    )
                    Spacer(Modifier.height(AppSpacing.lg))
                    Text(
                        text = stringResource(Res.string.download_preparing),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                is DownloadUiState.ReadyToDownload -> {
                    state.items.forEach { item ->
                        val itemDescription = stringResource(
                            Res.string.download_item_accessibility,
                            item.label,
                            formatFileSize(item.sizeBytes)
                        )
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics(mergeDescendants = true) {
                                    contentDescription = itemDescription
                                },
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        horizontal = AppSpacing.lg,
                                        vertical = AppSpacing.md
                                    ),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (item.flag.isNotEmpty()) {
                                    Text(
                                        text = item.flag,
                                        modifier = Modifier.clearAndSetSemantics {},
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Spacer(Modifier.width(AppSpacing.md))
                                }
                                Column {
                                    Text(
                                        text = item.label,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = formatFileSize(item.sizeBytes),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(AppSpacing.sm))
                    }
                }

                is DownloadUiState.Idle,
                is DownloadUiState.Finalizing -> {
                    val preparingContentDescription = stringResource(Res.string.download_content_desc_preparing)
                    SpinningProgressIndicator(
                        modifier = Modifier.semantics {
                            contentDescription = preparingContentDescription
                        }
                    )
                    Spacer(Modifier.height(AppSpacing.lg))
                    Text(
                        text = stringResource(Res.string.download_preparing),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if ((state as? DownloadUiState.Finalizing)?.updatingWordLists == true) {
                        Spacer(Modifier.height(AppSpacing.sm))
                        Text(
                            text = stringResource(Res.string.download_finalizing_updating_lists),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                    val recovery = (state as? DownloadUiState.Finalizing)?.recovery
                    if (recovery != null && recovery.total > 0) {
                        Spacer(Modifier.height(AppSpacing.sm))
                        val recoveryProgressText = if (recovery.failed > 0) {
                            pluralStringResource(
                                Res.plurals.download_finalizing_recovering_progress_with_failures,
                                recovery.failed,
                                recovery.completed,
                                recovery.total,
                                recovery.failed,
                            )
                        } else {
                            stringResource(
                                Res.string.download_finalizing_recovering_progress,
                                recovery.completed,
                                recovery.total,
                            )
                        }
                        Text(
                            text = recoveryProgressText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        recovery.currentLemma?.takeIf { it.isNotBlank() }?.let { lemma ->
                            Spacer(Modifier.height(AppSpacing.xs))
                            Text(
                                text = lemma,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }

                is DownloadUiState.Running -> {
                    val progressDescription = if (state.percent >= 0) {
                        stringResource(Res.string.download_progress_with_percent, state.percent)
                    } else {
                        stringResource(Res.string.download_title_downloading)
                    }
                    LinearWavyProgressIndicator(
                        progress = { state.percent / 100f },
                        modifier = Modifier.semantics {
                            contentDescription = progressDescription
                        }
                    )
                    Spacer(Modifier.height(AppSpacing.lg))
                    val pct = if (state.percent >= 0) "${state.percent}%" else ""
                    Text(
                        text = stringResource(Res.string.download_running_status, descriptionText, pct),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (state.currentFile != null) {
                        Spacer(Modifier.height(AppSpacing.xs))
                        Text(
                            text = state.currentFile,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                is DownloadUiState.Failed -> {
                    val classified = NetworkErrorClassifier.classify(state.error)
                    Text(
                        text = classified.userMessage,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                is DownloadUiState.Cancelled -> {
                    Text(
                        text = stringResource(Res.string.download_state_cancelled),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                is DownloadUiState.Done -> {
                    Text(
                        text = stringResource(Res.string.download_state_completed),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${state.countdown}",
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

sealed interface DownloadUiState {
    data object Loading : DownloadUiState
    data class ReadyToDownload(val items: List<DownloadItem>) : DownloadUiState
    data object Idle : DownloadUiState
    data class Running(val percent: Int, val total: Long?, val currentFile: String? = null) : DownloadUiState
    data class Finalizing(
        val recovery: LemmaRecoveryProgress? = null,
        val updatingWordLists: Boolean = false,
    ) : DownloadUiState
    data class Failed(val error: Throwable) : DownloadUiState
    data object Cancelled : DownloadUiState
    data class Done(val countdown: Int) : DownloadUiState
}

@Preview
@Composable
private fun DownloadScreenPreviewLoading(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        DownloadScreenContent(state = DownloadUiState.Loading)
    }
}

@Preview
@Composable
private fun DownloadScreenPreviewReadyToDownload(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        DownloadScreenContent(
            state = DownloadUiState.ReadyToDownload(
                items = listOf(
                    DownloadItem("Nederlands Dictionary", 156_000_000L, "\uD83C\uDDF3\uD83C\uDDF1"),
                    DownloadItem("Nederlands \u2192 English", 42_000_000L, "\uD83C\uDDEC\uD83C\uDDE7"),
                    DownloadItem("Nederlands \u2192 Русский", 38_000_000L, "\uD83C\uDDF7\uD83C\uDDFA")
                )
            )
        )
    }
}

@Preview
@Composable
private fun DownloadScreenPreviewIdle(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        DownloadScreenContent(state = DownloadUiState.Idle)
    }
}

@Preview
@Composable
private fun DownloadScreenPreviewRunning(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        DownloadScreenContent(state = DownloadUiState.Running(percent = 42, total = 1000L))
    }
}

@Preview
@Composable
private fun DownloadScreenPreviewFinalizing(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        DownloadScreenContent(
            state = DownloadUiState.Finalizing(
                LemmaRecoveryProgress(currentLemma = "test", completed = 1, total = 3, failed = 0)
            )
        )
    }
}

@Preview
@Composable
private fun DownloadScreenPreviewFinalizingWordLists(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        DownloadScreenContent(
            state = DownloadUiState.Finalizing(updatingWordLists = true)
        )
    }
}

@Preview
@Composable
private fun DownloadScreenPreviewFailed(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        DownloadScreenContent(state = DownloadUiState.Failed(Throwable("Network error")))
    }
}

@Preview
@Composable
private fun DownloadScreenPreviewCancelled(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        DownloadScreenContent(state = DownloadUiState.Cancelled)
    }
}

@Preview
@Composable
private fun DownloadScreenPreviewDone(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        DownloadScreenContent(state = DownloadUiState.Done(countdown = 3))
    }
}
