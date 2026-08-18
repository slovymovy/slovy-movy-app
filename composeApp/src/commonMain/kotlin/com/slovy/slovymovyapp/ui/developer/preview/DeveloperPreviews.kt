package com.slovy.slovymovyapp.ui.developer.preview

import com.slovy.slovymovyapp.ui.developer.CardTableCard
import com.slovy.slovymovyapp.ui.developer.DeveloperOptionsCard
import com.slovy.slovymovyapp.ui.developer.DeveloperScreenContent
import com.slovy.slovymovyapp.ui.developer.DeveloperUiState
import com.slovy.slovymovyapp.ui.developer.DeveloperCardTableRow
import com.slovy.slovymovyapp.ui.developer.DeveloperCardTablePageInfo
import com.slovy.slovymovyapp.ui.developer.CardTablePageSize

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import slovymovyapp.composeapp.generated.resources.*
import com.slovy.slovymovyapp.ui.ThemePreviewProvider
import com.slovy.slovymovyapp.ui.ThemedPreview

// === Previews ===

@Preview
@Composable
private fun DeveloperScreenPreviewIdle(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean,
) {
    ThemedPreview(darkTheme = isDark) {
        DeveloperScreenContent(state = DeveloperUiState())
    }
}

@Preview
@Composable
private fun DeveloperScreenPreviewAfterShift(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean,
) {
    ThemedPreview(darkTheme = isDark) {
        DeveloperScreenContent(
            state = DeveloperUiState(
                terminalLines = listOf("now INFO/DeveloperViewModel: Action finished: Shifted learning time +1d"),
            ),
        )
    }
}

@Preview
@Composable
private fun DeveloperScreenPreviewAfterIntake(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean,
) {
    ThemedPreview(darkTheme = isDark) {
        DeveloperScreenContent(
            state = DeveloperUiState(
                terminalLines = listOf(
                    "now INFO/DeveloperViewModel: Action finished: Daily - en:+12/4, pl:+0/0 | total: 12c/4s",
                ),
            ),
        )
    }
}

@Preview
@Composable
private fun DeveloperScreenPreviewNonDebug(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean,
) {
    ThemedPreview(darkTheme = isDark) {
        DeveloperScreenContent(
            state = DeveloperUiState(),
            isDebugBuild = false,
        )
    }
}

@Preview
@Composable
private fun DeveloperScreenPreviewBusy(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean,
) {
    ThemedPreview(darkTheme = isDark) {
        DeveloperScreenContent(
            state = DeveloperUiState(
                isBusy = true,
                currentActionLabel = "Shift +10h",
                terminalLines = listOf("now INFO/DeveloperViewModel: Action started: Shift +10h"),
            ),
        )
    }
}

@Preview
@Composable
private fun DeveloperOptionsCardPreview(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean,
) {
    ThemedPreview(darkTheme = isDark) {
        DeveloperOptionsCard(onClick = {})
    }
}

@Preview
@Composable
private fun CardTableCardPreviewWithRows(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean,
) {
    ThemedPreview(darkTheme = isDark) {
        CardTableCard(
            rows = listOf(
                DeveloperCardTableRow(
                    cells = listOf(
                        "rennen",
                        "nl",
                        "PRODUCE_WORD",
                        "REVIEW",
                        "12.34",
                        "5.67",
                        "+2h 15m",
                        "-1d 4h",
                        "8",
                        "1",
                        "-12d",
                        "+45m",
                        "rennen",
                        "false",
                        "018f4a1b-75dd-73a2-8a0e-84b53aa5e235",
                    ),
                ),
                DeveloperCardTableRow(
                    cells = listOf(
                        "house",
                        "en",
                        "RECOGNIZE_SENSE",
                        "NEW",
                        "0.0",
                        "0.0",
                        "-3m",
                        "-",
                        "0",
                        "0",
                        "-5m",
                        "-",
                        "house",
                        "false",
                        "018f4a1b-a230-70f1-a0a0-26bb6e56011a",
                    ),
                ),
            ),
            pageInfo = DeveloperCardTablePageInfo(
                pageIndex = 0,
                pageSize = CardTablePageSize,
                totalRows = 2,
            ),
            isLoading = false,
            errorLabel = null,
            horizontalScrollState = ScrollState(0),
            onPreviousPage = {},
            onNextPage = {},
        )
    }
}
