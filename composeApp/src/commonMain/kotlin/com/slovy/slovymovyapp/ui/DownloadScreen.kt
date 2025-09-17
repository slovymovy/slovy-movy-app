package com.slovy.slovymovyapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.slovy.slovymovyapp.data.remote.CancelToken
import com.slovy.slovymovyapp.data.remote.DownloadProgress
import kotlinx.io.files.Path
import org.jetbrains.compose.ui.tooling.preview.Preview

@Preview
@Composable
fun DownloadScreen(
    onSuccess: () -> Unit,
    onCancel: () -> Unit,
    onError: (Throwable) -> Unit,
    download: suspend (onProgress: (DownloadProgress) -> Unit, cancelToken: CancelToken) -> Unit,
    description: String = "Downloading data"
) {
    var state by remember { mutableStateOf<State>(State.Idle) }
    val cancel = remember { CancelToken() }

    LaunchedEffect(Unit) {
        state = State.Running(0, null)
        try {
            val onProgress: (DownloadProgress) -> Unit =
                { p -> state = State.Running(p.percent.coerceAtLeast(0), p.totalBytes) }

            download(onProgress, cancel)
            state = State.Done
            onSuccess()
        } catch (t: Throwable) {
            if (cancel.isCancelled) {
                state = State.Cancelled
                onCancel()
            } else {
                state = State.Failed(t)
                onError(t)
            }
        }
    }

    Surface(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when (val s = state) {
                is State.Idle -> Text("Preparing download…")
                is State.Running -> {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    val pct = if (s.percent >= 0) "${s.percent}%" else "…"
                    Text("$description $pct", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(onClick = { cancel.cancel() }) { Text("Cancel") }
                }

                is State.Failed -> {
                    Text("Download failed: ${s.error.message}")
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { state = State.Idle }) { Text("Retry") }
                }

                is State.Cancelled -> {
                    Text("Download cancelled")
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onCancel) { Text("Close") }
                }

                is State.Done -> {
                    Text("Download completed")
                }
            }
        }
    }
}

private sealed interface State {
    data object Idle : State
    data class Running(val percent: Int, val total: Long?) : State
    data class Failed(val error: Throwable) : State
    data object Cancelled : State
    data object Done : State
}
