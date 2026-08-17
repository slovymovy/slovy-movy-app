package com.slovy.slovymovyapp.ui.download

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slovy.slovymovyapp.analytics.Analytics
import com.slovy.slovymovyapp.analytics.AnalyticsEvent
import com.slovy.slovymovyapp.data.remote.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
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
    private var finalizeJob: Job? = null

    /** Guards [onSuccess] against running twice when a skip races the finalizing step finishing. */
    private var successHandled = false

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
                            startFinalizing()
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

    /**
     * Runs the post-download work in its own job so [skipFinalizing] can stop waiting for it
     * without touching the download collector.
     */
    private fun startFinalizing() {
        finalizeJob = viewModelScope.launch {
            try {
                state = DownloadUiState.Finalizing()
                finalize(::updateRecoveryProgress, ::updateWordListsSyncing)
                for (i in 3 downTo 1) {
                    state = DownloadUiState.Done(countdown = i)
                    delay(1_000.milliseconds)
                }
                finishSuccessfully()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state = DownloadUiState.Failed(e)
            } finally {
                releaseKeepAlive()
            }
        }
    }

    /**
     * Leaves the finalizing step without waiting for it to end. Only this screen's wait is
     * cancelled — lemma recovery runs in `LemmaRecoveryController`'s own scope with its own process
     * keep-alive, so it carries on in the background.
     */
    fun skipFinalizing() {
        if (state !is DownloadUiState.Finalizing || successHandled) return
        Analytics.logEvent(AnalyticsEvent.DOWNLOAD_RECOVERY_SKIP_CLICK, analyticsParams)
        finalizeJob?.cancel()
        finalizeJob = null
        releaseKeepAlive()
        viewModelScope.launch { finishSuccessfully() }
    }

    private suspend fun finishSuccessfully() {
        if (successHandled) return
        successHandled = true
        onSuccess()
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

