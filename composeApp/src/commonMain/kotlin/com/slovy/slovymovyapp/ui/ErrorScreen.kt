package com.slovy.slovymovyapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.jetbrains.compose.ui.tooling.preview.PreviewParameter

class ErrorViewModel(val message: String) : ViewModel()

@Composable
fun ErrorScreen(
    viewModel: ErrorViewModel,
    onOkay: () -> Unit = {}
) {
    ErrorScreenContent(
        state = viewModel,
        onOkay = onOkay
    )
}

@Composable
fun ErrorScreenContent(
    state: ErrorViewModel,
    onOkay: () -> Unit = {}
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = state.message,
                style = MaterialTheme.typography.bodyLarge
            )
            Button(onClick = onOkay) {
                Text("Okay")
            }
        }
    }
}

@Preview
@Composable
private fun ErrorScreenPreviewDefault(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        ErrorScreenContent(state = ErrorViewModel("An unexpected error occurred."))
    }
}

@Preview
@Composable
private fun ErrorScreenPreviewLongMessage(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        ErrorScreenContent(
            state = ErrorViewModel(
                "We couldn't complete the download. Please check your connection and try again."
            )
        )
    }
}
