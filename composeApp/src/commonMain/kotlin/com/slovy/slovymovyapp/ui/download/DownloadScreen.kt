package com.slovy.slovymovyapp.ui.download

import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.slovy.slovymovyapp.data.remote.*
import com.slovy.slovymovyapp.i18n.networkErrorUiText
import com.slovy.slovymovyapp.i18n.resolve
import com.slovy.slovymovyapp.ui.components.SpinningProgressIndicator
import com.slovy.slovymovyapp.ui.icons.DownloadScreenTransparent
import com.slovy.slovymovyapp.ui.icons.SlovyIcons
import com.slovy.slovymovyapp.ui.theme.AppSpacing
import com.slovy.slovymovyapp.ui.theme.serifFontFamily
import com.slovy.slovymovyapp.ui.theme.uiItalic
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import slovymovyapp.composeapp.generated.resources.*
import com.slovy.slovymovyapp.ui.formatFileSize

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
        onSkipFinalizingClick = { viewModel.skipFinalizing() },
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
    onSkipFinalizingClick: () -> Unit = {},
    onRetryClick: () -> Unit = {},
    onCloseClick: () -> Unit = {},
    onErrorLaterClick: () -> Unit = {},
) {
    val descriptionText = description ?: stringResource(Res.string.download_description_setting_up_library)
    // Skipping is offered once recovery reports in: it is the long, resumable part of finalizing,
    // whereas the word-list sync before it must not be left half-done.
    val canSkipFinalizing = state is DownloadUiState.Finalizing && state.recovery != null
    val hasActions = state is DownloadUiState.ReadyToDownload ||
            state is DownloadUiState.Running ||
            state is DownloadUiState.Failed ||
            state is DownloadUiState.Cancelled ||
            canSkipFinalizing

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

                        is DownloadUiState.Finalizing -> {
                            if (canSkipFinalizing) {
                                TextButton(onClick = onSkipFinalizingClick) {
                                    Text(
                                        stringResource(Res.string.download_action_skip_recovery),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
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
                    Text(
                        text = networkErrorUiText(state.error).resolve(),
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

