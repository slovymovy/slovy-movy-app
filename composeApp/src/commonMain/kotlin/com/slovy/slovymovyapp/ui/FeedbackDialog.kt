package com.slovy.slovymovyapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp

@Composable
fun FeedbackDialog(
    title: String,
    comment: String,
    email: String,
    isSending: Boolean,
    error: String?,
    resultUrl: String?,
    onCommentChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSend: () -> Unit
) {
    if (resultUrl != null) {
        val uriHandler = LocalUriHandler.current
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Feedback sent!") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Thank you for your feedback.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    TextButton(
                        onClick = { uriHandler.openUri(resultUrl) },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("View on GitHub")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        )
    } else {
        AlertDialog(
            onDismissRequest = {
                if (!isSending) onDismiss()
            },
            title = {
                Text(title)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = comment,
                        onValueChange = onCommentChange,
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 6,
                        singleLine = false,
                        enabled = !isSending,
                        label = { Text("Comment") },
                        isError = error != null
                    )
                    OutlinedTextField(
                        value = email,
                        onValueChange = onEmailChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isSending,
                        label = { Text("Email (optional)") },
                        supportingText = {
                            Text("May be publicly visible on GitHub")
                        }
                    )
                    if (error != null) {
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = onSend,
                    enabled = !isSending && comment.isNotBlank()
                ) {
                    if (isSending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Send")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismiss,
                    enabled = !isSending
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Preview
@Composable
private fun FeedbackDialogInputPreview(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        FeedbackDialog(
            title = "App feedback",
            comment = "",
            email = "",
            isSending = false,
            error = null,
            resultUrl = null,
            onCommentChange = {},
            onEmailChange = {},
            onDismiss = {},
            onSend = {}
        )
    }
}

@Preview
@Composable
private fun FeedbackDialogSendingPreview(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        FeedbackDialog(
            title = "App feedback",
            comment = "Great app!",
            email = "user@example.com",
            isSending = true,
            error = null,
            resultUrl = null,
            onCommentChange = {},
            onEmailChange = {},
            onDismiss = {},
            onSend = {}
        )
    }
}

@Preview
@Composable
private fun FeedbackDialogErrorPreview(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        FeedbackDialog(
            title = "App feedback",
            comment = "",
            email = "",
            isSending = false,
            error = "Comment is required",
            resultUrl = null,
            onCommentChange = {},
            onEmailChange = {},
            onDismiss = {},
            onSend = {}
        )
    }
}

@Preview
@Composable
private fun FeedbackDialogSuccessPreview(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        FeedbackDialog(
            title = "App feedback",
            comment = "",
            email = "",
            isSending = false,
            error = null,
            resultUrl = "https://github.com/slovymovy/slovy-movy-app/discussions/1",
            onCommentChange = {},
            onEmailChange = {},
            onDismiss = {},
            onSend = {}
        )
    }
}
