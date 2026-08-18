package com.slovy.slovymovyapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import com.slovy.slovymovyapp.ui.theme.AppSpacing
import org.jetbrains.compose.resources.stringResource
import slovymovyapp.composeapp.generated.resources.*

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
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(AppSpacing.xxl),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = state.message,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = onOkay) {
                Text(stringResource(Res.string.common_got_it))
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun ErrorScreenPreviewDefault(
    @androidx.compose.ui.tooling.preview.PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        ErrorScreenContent(state = ErrorViewModel("An unexpected error occurred."))
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun ErrorScreenPreviewLongMessage(
    @androidx.compose.ui.tooling.preview.PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        ErrorScreenContent(
            state = ErrorViewModel(
                "We couldn't complete the download. Please check your connection and try again."
            )
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun ErrorScreenPreviewDataVersionMismatch(
    @androidx.compose.ui.tooling.preview.PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        ErrorScreenContent(
            state = ErrorViewModel(
                "We've updated the dictionary format. You'll need to re-download your dictionaries — your saved words are safe."
            )
        )
    }
}
