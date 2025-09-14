package com.slovy.slovymovyapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.slovy.slovymovyapp.data.remote.CancelToken
import com.slovy.slovymovyapp.data.remote.DataDbManager

@Composable
fun DownloadDataScreen(
    manager: DataDbManager,
    // For demo and tests we download the smallest files: dictionary_en and translation_nl_en
    onSuccess: () -> Unit,
    onCancel: () -> Unit,
    onError: (Throwable) -> Unit,
) {
    var state by remember { mutableStateOf<State>(State.Idle) }
    val cancel = remember { CancelToken() }

    LaunchedEffect(Unit) {
        state = State.Running(0, null)
        try {
            // Download dictionary_en
            manager.ensureDictionary(
                lang = "en",
                onProgress = { p -> state = State.Running(p.percent.coerceAtLeast(0), p.totalBytes) },
                cancelToken = cancel
            )
            // Download translation_nl_en
            manager.ensureTranslation(
                src = "nl", tgt = "en",
                onProgress = { p -> state = State.Running(p.percent.coerceAtLeast(0), p.totalBytes) },
                cancelToken = cancel
            )
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
                    val pct = if (s.percent >= 0) "${'$'}{s.percent}%" else "…"
                    Text("Downloading data ${'$'}pct", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(onClick = { cancel.cancel() }) { Text("Cancel") }
                }

                is State.Failed -> {
                    Text("Download failed: ${'$'}{s.error.message}")
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
