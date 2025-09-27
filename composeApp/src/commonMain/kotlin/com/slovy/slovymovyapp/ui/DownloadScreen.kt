package com.slovy.slovymovyapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.slovy.slovymovyapp.data.remote.CancelToken
import com.slovy.slovymovyapp.data.remote.DownloadProgress
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun DownloadScreen(
    onSuccess: () -> Unit = {},
    onCancel: () -> Unit = {},
    onError: (Throwable) -> Unit = {},
    download: suspend (onProgress: (DownloadProgress) -> Unit, cancelToken: CancelToken) -> Unit = { _, _ -> },
    description: String = "Downloading data"
) {
    var state by remember { mutableStateOf<DownloadUiState>(DownloadUiState.Idle) }
    val cancel = remember { CancelToken() }

    LaunchedEffect(Unit) {
        state = DownloadUiState.Running(0, null)
        try {
            val onProgress: (DownloadProgress) -> Unit =
                { p -> state = DownloadUiState.Running(p.percent.coerceAtLeast(0), p.totalBytes) }

            download(onProgress, cancel)
            state = DownloadUiState.Done
            onSuccess()
        } catch (t: Throwable) {
            if (cancel.isCancelled) {
                state = DownloadUiState.Cancelled
                onCancel()
            } else {
                state = DownloadUiState.Failed(t)
                onError(t)
            }
        }
    }

    DownloadScreenContent(
        state = state,
        description = description,
        onCancelClick = { cancel.cancel() },
        onRetryClick = { state = DownloadUiState.Idle },
        onCloseClick = onCancel
    )
}

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
            when (val s = state) {
                is DownloadUiState.Idle -> Text("Preparing download…")
                is DownloadUiState.Running -> {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    val pct = if (s.percent >= 0) "${s.percent}%" else "…"
                    Text("$description $pct", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(onClick = onCancelClick) { Text("Cancel") }
                }

                is DownloadUiState.Failed -> {
                    val message = s.error.message ?: "Unknown error"
                    Text("Download failed: $message")
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onRetryClick) { Text("Retry") }
                }

                is DownloadUiState.Cancelled -> {
                    Text("Download cancelled")
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onCloseClick) { Text("Close") }
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
private fun DownloadScreenPreviewIdle() {
    DownloadScreenContent(state = DownloadUiState.Idle)
}

@Preview
@Composable
private fun DownloadScreenPreviewRunning() {
    DownloadScreenContent(state = DownloadUiState.Running(percent = 42, total = 1000L))
}

@Preview
@Composable
private fun DownloadScreenPreviewFailed() {
    DownloadScreenContent(state = DownloadUiState.Failed(Throwable("Network error")))
}

@Preview
@Composable
private fun DownloadScreenPreviewCancelled() {
    DownloadScreenContent(state = DownloadUiState.Cancelled)
}

@Preview
@Composable
private fun DownloadScreenPreviewDone() {
    DownloadScreenContent(state = DownloadUiState.Done)
}
