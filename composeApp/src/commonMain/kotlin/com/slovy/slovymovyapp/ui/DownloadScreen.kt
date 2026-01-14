package com.slovy.slovymovyapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slovy.slovymovyapp.data.remote.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.jetbrains.compose.ui.tooling.preview.PreviewParameter

class DownloadViewModel(
    private val downloadCoordinator: DownloadCoordinator,
    private val downloadKey: String,
    private val download: suspend (onProgress: (DownloadProgress) -> Unit, cancelToken: CancelToken) -> Unit,
    private val onSuccess: () -> Unit,
    private val onCancel: () -> Unit,
    private val onError: (Throwable) -> Unit
) : ViewModel() {

    var state by mutableStateOf<DownloadUiState>(DownloadUiState.Idle)
        private set

    private var terminalHandled = false

    init {
        val downloadFlow = startDownload()
        observeProgress(downloadFlow)
        attachDownloadCallbacks(downloadFlow)
    }

    private fun startDownload(): Flow<DownloadEntry?> {
        return downloadCoordinator.startDownload(downloadKey, download)
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
                        state = DownloadUiState.Running(percent, progress?.totalBytes)
                    }

                    else -> Unit
                }
            }
        }
    }

    private fun attachDownloadCallbacks(downloadFlow: Flow<DownloadEntry?>) {
        viewModelScope.launch {
            downloadFlow.collect { entry ->
                when (entry?.status ?: DownloadStatus.Idle) {
                    DownloadStatus.Done -> {
                        state = DownloadUiState.Done
                        if (!terminalHandled) {
                            terminalHandled = true
                            onSuccess()
                            downloadCoordinator.clear(downloadKey)
                        }
                    }

                    DownloadStatus.Cancelled -> {
                        state = DownloadUiState.Cancelled
                        if (!terminalHandled) {
                            terminalHandled = true
                            onCancel()
                            downloadCoordinator.clear(downloadKey)
                        }
                    }

                    DownloadStatus.Failed -> {
                        val error = entry?.error ?: Throwable("Unknown error")
                        state = DownloadUiState.Failed(error)
                        if (!terminalHandled) {
                            terminalHandled = true
                            onError(error)
                            downloadCoordinator.clear(downloadKey)
                        }
                    }

                    else -> Unit
                }
            }
        }
    }

    fun cancelDownload() {
        downloadCoordinator.cancel(downloadKey)
    }

    fun retry() {
        terminalHandled = false
        state = DownloadUiState.Idle
        startDownload()
    }
}

@Composable
fun DownloadScreen(
    viewModel: DownloadViewModel,
    description: String = "Downloading data"
) {
    DownloadScreenContent(
        state = viewModel.state,
        description = description,
        onCancelClick = { viewModel.cancelDownload() },
        onRetryClick = { viewModel.retry() },
        onCloseClick = { viewModel.cancelDownload() }
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DownloadScreenContent(
    state: DownloadUiState,
    description: String = "Downloading data",
    onCancelClick: () -> Unit = {},
    onRetryClick: () -> Unit = {},
    onCloseClick: () -> Unit = {},
) {
    Surface(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when (state) {
                is DownloadUiState.Idle -> Text("Preparing download…")
                is DownloadUiState.Running -> {
                    LinearWavyProgressIndicator(progress = { state.percent / 100f })
                    Spacer(Modifier.height(16.dp))
                    val pct = if (state.percent >= 0) "${state.percent}%" else "…"
                    Text("$description $pct", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(16.dp))
                    FilledTonalButton(onClick = onCancelClick) { Text("Cancel") }
                }

                is DownloadUiState.Failed -> {
                    val message = state.error.message ?: "Unknown error"
                    Text("Download failed: $message")
                    Spacer(Modifier.height(16.dp))
                    FilledTonalButton(onClick = onRetryClick) { Text("Retry") }
                }

                is DownloadUiState.Cancelled -> {
                    Text("Download cancelled")
                    Spacer(Modifier.height(16.dp))
                    FilledTonalButton(onClick = onCloseClick) { Text("Close") }
                }

                is DownloadUiState.Done -> {
                    Text("Download completed")
                }
            }
        }
    }
}

sealed interface DownloadUiState {
    data object Idle : DownloadUiState
    data class Running(val percent: Int, val total: Long?) : DownloadUiState
    data class Failed(val error: Throwable) : DownloadUiState
    data object Cancelled : DownloadUiState
    data object Done : DownloadUiState
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
        DownloadScreenContent(state = DownloadUiState.Done)
    }
}
