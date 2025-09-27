package com.slovy.slovymovyapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun ErrorScreen(
    message: String = "An unexpected error occurred.",
    onOkay: () -> Unit = {}
) {
    ErrorScreenContent(
        state = ErrorUiState(message = message),
        onOkay = onOkay
    )
}

@Composable
fun ErrorScreenContent(
    state: ErrorUiState,
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

data class ErrorUiState(val message: String)

@Preview
@Composable
private fun ErrorScreenPreviewDefault() {
    ErrorScreenContent(state = ErrorUiState("An unexpected error occurred."))
}

@Preview
@Composable
private fun ErrorScreenPreviewLongMessage() {
    ErrorScreenContent(
        state = ErrorUiState(
            "We couldn't complete the download. Please check your connection and try again."
        )
    )
}
