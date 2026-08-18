package com.slovy.slovymovyapp.ui.developer

import com.slovy.slovymovyapp.ui.ThemePreviewProvider
import com.slovy.slovymovyapp.ui.ThemedPreview
import com.slovy.slovymovyapp.ui.developerLogSignature
import com.slovy.slovymovyapp.ui.toDeveloperTerminalLine

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.DeveloperMode
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.slovy.slovymovyapp.logging.AppLogger
import com.slovy.slovymovyapp.ui.theme.AppElevation
import com.slovy.slovymovyapp.ui.theme.AppSpacing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import slovymovyapp.composeapp.generated.resources.Res
import slovymovyapp.composeapp.generated.resources.developer_terminal_clear
import slovymovyapp.composeapp.generated.resources.developer_terminal_close
import slovymovyapp.composeapp.generated.resources.developer_terminal_open
import slovymovyapp.composeapp.generated.resources.developer_terminal_ready
import slovymovyapp.composeapp.generated.resources.developer_terminal_title
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

private const val TAG = "DeveloperTerminalOverlay"

private data class OverlayTimeShiftOption(
    val label: String,
    val duration: Duration,
)

private val OverlayTimeShiftOptions = listOf(
    OverlayTimeShiftOption("+5m", 5.minutes),
    OverlayTimeShiftOption("+1h", 1.hours),
    OverlayTimeShiftOption("+1d", 1.days),
    OverlayTimeShiftOption("+1w", 7.days),
)

@Composable
fun DeveloperTerminalOverlay(
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onShiftLearningTime: suspend (Duration) -> Unit = { _ -> },
) {
    var isTerminalOpen by remember { mutableStateOf(false) }
    var isActionRunning by remember { mutableStateOf(false) }
    var terminalLines by remember { mutableStateOf(emptyList<String>()) }
    var terminalRevision by remember { mutableStateOf(0L) }
    var terminalLogSignature by remember { mutableStateOf("") }
    val terminalScrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    fun syncTerminalLogs() {
        val recentLogs = AppLogger.recentDeveloperLogs()
        val signature = recentLogs.developerLogSignature()
        if (signature != terminalLogSignature) {
            terminalLogSignature = signature
            terminalLines = recentLogs.map { it.toDeveloperTerminalLine() }
            terminalRevision += 1
        }
    }

    LaunchedEffect(enabled) {
        if (!enabled) {
            isTerminalOpen = false
        }
    }

    if (!enabled) return

    LaunchedEffect(isTerminalOpen) {
        if (!isTerminalOpen) return@LaunchedEffect
        syncTerminalLogs()
        while (true) {
            delay(500.milliseconds)
            syncTerminalLogs()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        DeveloperTerminalHandle(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 16.dp, bottom = 88.dp),
            onOpen = {
                syncTerminalLogs()
                isTerminalOpen = true
            },
        )
    }

    if (isTerminalOpen) {
        DeveloperTerminalSheet(
            terminalLines = terminalLines,
            terminalRevision = terminalRevision,
            scrollState = terminalScrollState,
            isActionRunning = isActionRunning,
            onShift = { option ->
                if (!isActionRunning) {
                    isActionRunning = true
                    AppLogger.info(TAG, "Action started: Shift ${option.label}", null)
                    syncTerminalLogs()
                    coroutineScope.launch {
                        try {
                            onShiftLearningTime(option.duration)
                            AppLogger.info(TAG, "Action finished: Shifted learning time ${option.label}", null)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            AppLogger.warn(TAG, "Shift ${option.label} failed: ${e.toLogLabel()}", e)
                        } finally {
                            isActionRunning = false
                            syncTerminalLogs()
                        }
                    }
                }
            },
            onClear = {
                AppLogger.clearDeveloperLogs()
                terminalLogSignature = ""
                syncTerminalLogs()
            },
            onDismiss = { isTerminalOpen = false },
        )
    }
}

@Composable
private fun DeveloperTerminalHandle(
    modifier: Modifier,
    onOpen: () -> Unit,
) {
    val openLabel = stringResource(Res.string.developer_terminal_open)
    Surface(
        modifier = modifier
            .width(52.dp)
            .height(28.dp)
            .clickable(onClickLabel = openLabel, onClick = onOpen)
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, dragAmount ->
                    if (dragAmount < -8f) onOpen()
                }
            },
        shape = RoundedCornerShape(percent = 50),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        tonalElevation = AppElevation.level3,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Outlined.DeveloperMode,
                contentDescription = openLabel,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeveloperTerminalSheet(
    terminalLines: List<String>,
    terminalRevision: Long,
    scrollState: ScrollState,
    isActionRunning: Boolean,
    onShift: (OverlayTimeShiftOption) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        LaunchedEffect(terminalRevision) {
            scrollState.scrollTo(scrollState.maxValue)
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(1f)
                .padding(horizontal = AppSpacing.lg)
                .padding(bottom = AppSpacing.lg)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
            ) {
                Text(
                    text = stringResource(Res.string.developer_terminal_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onClear) {
                    Text(stringResource(Res.string.developer_terminal_clear))
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(Res.string.developer_terminal_close),
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
            ) {
                OverlayTimeShiftOptions.forEach { option ->
                    ScaryTerminalButton(
                        text = option.label,
                        onClick = { onShift(option) },
                        enabled = !isActionRunning,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                SelectionContainer {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState),
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
                    ) {
                        val visibleLines = if (terminalLines.isEmpty()) {
                            listOf(stringResource(Res.string.developer_terminal_ready))
                        } else {
                            terminalLines
                        }
                        visibleLines.forEach { line ->
                            Text(
                                text = line,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScaryTerminalButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier,
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Text(text)
    }
}

private fun Throwable.toLogLabel(): String =
    "${this::class.simpleName ?: "Error"}: ${message ?: "no message"}"

@Preview
@Composable
private fun DeveloperTerminalHandlePreview(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpacing.lg),
            contentAlignment = Alignment.Center,
        ) {
            DeveloperTerminalHandle(onOpen = {}, modifier = Modifier)
        }
    }
}
