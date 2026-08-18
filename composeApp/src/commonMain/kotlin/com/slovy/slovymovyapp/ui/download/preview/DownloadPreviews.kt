package com.slovy.slovymovyapp.ui.download

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.slovy.slovymovyapp.data.remote.*
import slovymovyapp.composeapp.generated.resources.*
import com.slovy.slovymovyapp.ui.ThemePreviewProvider
import com.slovy.slovymovyapp.ui.ThemedPreview

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

