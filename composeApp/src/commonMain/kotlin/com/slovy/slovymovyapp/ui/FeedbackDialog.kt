package com.slovy.slovymovyapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import slovymovyapp.composeapp.generated.resources.*

@Composable
fun FeedbackDialog(
    title: String,
    commentPlaceholder: String,
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
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = stringResource(Res.string.feedback_dialog_sent_title),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(Res.string.feedback_dialog_sent_message),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    TextButton(
                        onClick = { uriHandler.openUri(resultUrl) },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(stringResource(Res.string.feedback_dialog_track_on_github))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(Res.string.common_close))
                }
            }
        )
    } else {
        // Local TextFieldValue state preserves cursor position across recompositions.
        // Plain String → TextField causes cursor jumps on iOS due to the UIKit bridge
        // recreating cursor state on every recomposition triggered by ViewModel updates.
        // Hoisted outside AlertDialog so confirmButton can read commentValue.text.
        var commentValue by remember { mutableStateOf(TextFieldValue(comment)) }
        var emailValue by remember { mutableStateOf(TextFieldValue(email)) }
        AlertDialog(
            onDismissRequest = {
                if (!isSending) onDismiss()
            },
            title = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.SemiBold
                    )
                )
            },
            text = {
                val fieldColors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    focusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = commentValue,
                        onValueChange = { commentValue = it; onCommentChange(it.text) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 6,
                        singleLine = false,
                        enabled = !isSending,
                        label = { Text(stringResource(Res.string.feedback_dialog_comment_label)) },
                        placeholder = {
                            Text(
                                text = commentPlaceholder,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        shape = MaterialTheme.shapes.small,
                        colors = fieldColors,
                        isError = error != null
                    )
                    OutlinedTextField(
                        value = emailValue,
                        onValueChange = { emailValue = it; onEmailChange(it.text) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isSending,
                        label = { Text(stringResource(Res.string.feedback_dialog_email_label)) },
                        placeholder = {
                            Text(
                                text = stringResource(Res.string.feedback_dialog_email_label),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        shape = MaterialTheme.shapes.small,
                        colors = fieldColors,
                        supportingText = {
                            Text(stringResource(Res.string.feedback_dialog_email_supporting))
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
                    enabled = !isSending && commentValue.text.isNotBlank()
                ) {
                    if (isSending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(Res.string.feedback_dialog_sending))
                    } else {
                        Text(stringResource(Res.string.feedback_dialog_send))
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismiss,
                    enabled = !isSending,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Text(stringResource(Res.string.common_cancel))
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
            commentPlaceholder = "Share your thoughts…",
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
            commentPlaceholder = "Share your thoughts…",
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
            commentPlaceholder = "Share your thoughts…",
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
            commentPlaceholder = "Share your thoughts…",
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
