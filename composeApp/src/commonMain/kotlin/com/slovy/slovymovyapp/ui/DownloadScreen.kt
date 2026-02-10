package com.slovy.slovymovyapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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

data class DownloadItem(
    val label: String,
    val sizeBytes: Long,
    val flag: String = ""
)

class DownloadViewModel(
    private val downloadCoordinator: DownloadCoordinator,
    private val downloadKey: String,
    private val download: suspend (onProgress: (DownloadProgress) -> Unit, cancelToken: CancelToken) -> Unit,
    private val onSuccess: () -> Unit,
    private val onCancel: () -> Unit,
    private val onError: (Throwable) -> Unit,
    private val loadItems: (suspend () -> List<DownloadItem>)? = null
) : ViewModel() {

    var state by mutableStateOf(
        if (loadItems != null) DownloadUiState.Loading else DownloadUiState.Idle
    )
        private set

    val hadConfirmation: Boolean = loadItems != null

    private var terminalHandled = false
    private var failedDuringLoadItems = false

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
                } else {
                    failedDuringLoadItems = false
                    state = DownloadUiState.ReadyToDownload(items)
                }
            } catch (e: Exception) {
                failedDuringLoadItems = true
                state = DownloadUiState.Failed(e)
            }
        }
    }

    fun startDownload() {
        failedDuringLoadItems = false
        state = DownloadUiState.Idle
        val downloadFlow = beginDownload()
        observeProgress(downloadFlow)
        attachDownloadCallbacks(downloadFlow)
    }

    private fun beginDownload(): Flow<DownloadEntry?> {
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
    description: String = "Setting up your library",
    onLaterClick: () -> Unit = {}
) {
    DownloadScreenContent(
        state = viewModel.state,
        description = description,
        hadConfirmation = viewModel.hadConfirmation,
        onDownloadClick = { viewModel.startDownload() },
        onLaterClick = onLaterClick,
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
    hadConfirmation: Boolean = false,
    onDownloadClick: () -> Unit = {},
    onLaterClick: () -> Unit = {},
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

            val (title, subtitle) = when (state) {
                is DownloadUiState.ReadyToDownload ->
                    "Ready to Download" to "You need these dictionaries to get all set."

                is DownloadUiState.Failed ->
                    "Download Failed" to "Something went wrong. Please try again."

                is DownloadUiState.Cancelled ->
                    "Download Cancelled" to "You can retry from Settings."

                is DownloadUiState.Done ->
                    "All Set" to "Your library is ready."

                else -> if (hadConfirmation) {
                    "Downloading" to "Getting your library ready.\nThis may take a moment."
                } else {
                    "Setting Up" to "Getting your library ready.\nWill take a moment."
                }
            }

            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(Modifier.height(AppSpacing.sm))

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            // Centered content area
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
                        Icon(
                            imageVector = Icons.Outlined.CloudDownload,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = glowColor
                        )
                    }

                    Spacer(Modifier.height(AppSpacing.xxl))

                    when (state) {
                        is DownloadUiState.Loading -> {
                            CircularProgressIndicator(
                                modifier = Modifier.semantics {
                                    contentDescription = "Loading download information"
                                }
                            )
                            Spacer(Modifier.height(AppSpacing.lg))
                            Text(
                                text = "Preparing...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        is DownloadUiState.ReadyToDownload -> {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                            ) {
                                state.items.forEach { item ->
                                    val itemDescription = "${item.label}, ${formatFileSize(item.sizeBytes)}"
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
                                            Column(modifier = Modifier.weight(1f)) {
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
                                }

                            }
                        }

                        is DownloadUiState.Idle -> {
                            CircularProgressIndicator(
                                modifier = Modifier.semantics {
                                    contentDescription = "Preparing download"
                                }
                            )
                            Spacer(Modifier.height(AppSpacing.lg))
                            Text(
                                text = "Preparing...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        is DownloadUiState.Running -> {
                            val progressDescription = if (state.percent >= 0) {
                                "Downloading, ${state.percent} percent complete"
                            } else {
                                "Downloading"
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
                                text = "$description... $pct",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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

            // Bottom button area
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
                            "Download $totalSize",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }

                    Spacer(Modifier.height(AppSpacing.sm))

                    TextButton(onClick = onLaterClick) {
                        Text(
                            "Later",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

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

                else -> {
                    Spacer(Modifier.height(AppSpacing.xxxl))
                }
            }

            Spacer(Modifier.height(AppSpacing.xxl))
        }
    }
}

sealed interface DownloadUiState {
    data object Loading : DownloadUiState
    data class ReadyToDownload(val items: List<DownloadItem>) : DownloadUiState
    data object Idle : DownloadUiState
    data class Running(val percent: Int, val total: Long?) : DownloadUiState
    data class Failed(val error: Throwable) : DownloadUiState
    data object Cancelled : DownloadUiState
    data object Done : DownloadUiState
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
