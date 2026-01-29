package com.slovy.slovymovyapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material3.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slovy.slovymovyapp.data.remote.*
import com.slovy.slovymovyapp.ui.theme.AppSpacing
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

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
    description: String = "Setting up your library"
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
    description: String = "Setting up your library",
    onCancelClick: () -> Unit = {},
    onRetryClick: () -> Unit = {},
    onCloseClick: () -> Unit = {},
) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = AppSpacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(AppSpacing.xxxl))

            Text(
                text = "Setting Up",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(Modifier.height(AppSpacing.sm))

            Text(
                text = "Getting your library ready.\nWill take a moment.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            // Centered progress area
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Glowing icon with soft radial gradient
                    Box(
                        modifier = Modifier.size(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Soft radial glow background
                        val glowColor = MaterialTheme.colorScheme.primary
                        Box(
                            modifier = Modifier
                                .size(120.dp)
                                .background(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            glowColor.copy(alpha = 0.15f),
                                            glowColor.copy(alpha = 0.10f),
                                            glowColor.copy(alpha = 0.06f),
                                            glowColor.copy(alpha = 0.03f),
                                            glowColor.copy(alpha = 0.01f),
                                            glowColor.copy(alpha = 0f)
                                        )
                                    )
                                )
                        )
                        // Main icon
                        Icon(
                            imageVector = Icons.Outlined.CloudDownload,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = glowColor
                        )
                    }

                    Spacer(Modifier.height(AppSpacing.xxl))

                    when (state) {
                        is DownloadUiState.Idle -> {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(AppSpacing.lg))
                            Text(
                                text = "Preparing...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        is DownloadUiState.Running -> {
                            LinearWavyProgressIndicator(progress = { state.percent / 100f })
                            Spacer(Modifier.height(AppSpacing.lg))
                            val pct = if (state.percent >= 0) "${state.percent}%" else ""
                            Text(
                                text = "$description... $pct",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        is DownloadUiState.Failed -> {
                            val message = state.error.message ?: "Unknown error"
                            Text(
                                text = "Download failed",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.height(AppSpacing.sm))
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }

                        is DownloadUiState.Cancelled -> {
                            Text(
                                text = "Download cancelled",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        is DownloadUiState.Done -> {
                            Text(
                                text = "Download completed",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Fixed button area at bottom
            Box(
                modifier = Modifier.height(AppSpacing.xxxl),
                contentAlignment = Alignment.Center
            ) {
                when (state) {
                    is DownloadUiState.Running -> {
                        TextButton(onClick = onCancelClick) {
                            Text(
                                "Cancel",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    is DownloadUiState.Failed -> {
                        OutlinedButton(onClick = onRetryClick) {
                            Text("Retry")
                        }
                    }

                    is DownloadUiState.Cancelled -> {
                        TextButton(onClick = onCloseClick) {
                            Text(
                                "Close",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    else -> {}
                }
            }

            Spacer(Modifier.height(AppSpacing.xxl))
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
