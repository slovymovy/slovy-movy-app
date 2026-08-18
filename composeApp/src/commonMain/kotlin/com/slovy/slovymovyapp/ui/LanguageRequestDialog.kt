package com.slovy.slovymovyapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.slovy.slovymovyapp.ui.components.SpinningProgressIndicator
import com.slovy.slovymovyapp.ui.theme.AppSpacing
import com.slovy.slovymovyapp.ui.theme.serifFontFamily
import org.jetbrains.compose.resources.stringResource
import slovymovyapp.composeapp.generated.resources.*

/**
 * Language request dialog for the language setup screen.
 *
 * Unlike [FeedbackDialog] this collects two named slots instead of one free comment, so a request
 * says whether the missing language is wanted for studying or for translations. Either slot alone
 * is a valid request; the caller composes both into the feedback body.
 */
@Composable
fun LanguageRequestDialog(
    learnLanguage: String,
    translateLanguage: String,
    isSending: Boolean,
    error: String?,
    discussionUrl: String?,
    onLearnLanguageChange: (String) -> Unit,
    onTranslateLanguageChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSend: () -> Unit
) {
    if (discussionUrl != null) {
        LanguageRequestSentDialog(discussionUrl = discussionUrl, onDismiss = onDismiss)
        return
    }

    // Local TextFieldValue state preserves cursor position across recompositions.
    // Plain String -> TextField causes cursor jumps on iOS because the UIKit bridge recreates
    // cursor state on every recomposition triggered by ViewModel updates.
    // Hoisted outside AlertDialog so confirmButton can read the current text.
    var learnValue by remember { mutableStateOf(TextFieldValue(learnLanguage)) }
    var translateValue by remember { mutableStateOf(TextFieldValue(translateLanguage)) }
    val canSend = learnValue.text.isNotBlank() || translateValue.text.isNotBlank()

    AlertDialog(
        // Dismissable even mid-send so a slow network never traps the user; the caller cancels
        // the in-flight submission, so backing out here abandons the request rather than
        // completing it in the background.
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        title = {
            Text(
                text = stringResource(Res.string.language_setup_request_language_title),
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
                LanguageRequestRoadmapText()

                LanguageRequestField(
                    label = stringResource(Res.string.language_setup_request_language_learn_label),
                    value = learnValue,
                    enabled = !isSending,
                    isError = error != null,
                    onValueChange = {
                        learnValue = it
                        onLearnLanguageChange(it.text)
                    }
                )

                LanguageRequestField(
                    label = stringResource(Res.string.language_setup_request_language_translate_label),
                    value = translateValue,
                    enabled = !isSending,
                    isError = error != null,
                    onValueChange = {
                        translateValue = it
                        onTranslateLanguageChange(it.text)
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
            Button(
                onClick = onSend,
                enabled = !isSending && canSend,
                shape = MaterialTheme.shapes.large
            ) {
                if (isSending) {
                    SpinningProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(AppSpacing.sm))
                    Text(stringResource(Res.string.feedback_dialog_sending))
                } else {
                    Text(stringResource(Res.string.feedback_dialog_send))
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(stringResource(Res.string.common_cancel))
            }
        }
    )
}

@Composable
private fun LanguageRequestSentDialog(
    discussionUrl: String,
    onDismiss: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
            ) {
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = stringResource(Res.string.language_setup_request_language_sent_title),
                    style = MaterialTheme.typography.headlineSmall
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                Text(
                    text = stringResource(Res.string.language_setup_request_language_sent_message),
                    style = MaterialTheme.typography.bodyMedium
                )
                TextButton(
                    onClick = { uriHandler.openUri(discussionUrl) },
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
}

/**
 * Renders the roadmap sentence with its link word styled in place.
 *
 * The base string keeps the whole sentence so translators can move the link word wherever their
 * grammar needs it; splitting on the placeholder is what locates it, rather than concatenating
 * before/after fragments.
 */
@Composable
private fun LanguageRequestRoadmapText() {
    val uriHandler = LocalUriHandler.current
    val template = stringResource(Res.string.language_setup_request_language_roadmap)
    val linkText = stringResource(Res.string.language_setup_request_language_roadmap_link)
    val accentColor = MaterialTheme.colorScheme.primary
    val currentUriHandler by rememberUpdatedState(uriHandler)

    val text = remember(template, linkText, accentColor) {
        val parts = template.split(ROADMAP_PLACEHOLDER, limit = 2)
        buildAnnotatedString {
            append(parts[0])
            val link = LinkAnnotation.Clickable(
                tag = ROADMAP_LINK_TAG,
                linkInteractionListener = { currentUriHandler.openUri(ROADMAP_URL) }
            )
            withLink(link) {
                withStyle(SpanStyle(color = accentColor, textDecoration = TextDecoration.Underline)) {
                    append(linkText)
                }
            }
            if (parts.size > 1) {
                append(parts[1])
            }
        }
    }

    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium.copy(
            fontFamily = MaterialTheme.serifFontFamily
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun LanguageRequestField(
    label: String,
    value: TextFieldValue,
    enabled: Boolean,
    isError: Boolean,
    onValueChange: (TextFieldValue) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
        // The label sits above the field to match the design, so it is decorative for
        // assistive tech and the field below carries the accessible name instead. Without
        // this the two fields read as indistinguishable edit boxes.
        Text(
            text = label,
            modifier = Modifier.clearAndSetSemantics {},
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.SemiBold
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = label },
            singleLine = true,
            enabled = enabled,
            isError = isError,
            shape = MaterialTheme.shapes.large,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
    }
}

private const val ROADMAP_LINK_TAG = "language_request_roadmap"
private const val ROADMAP_PLACEHOLDER = "%1\$s"
// The page is not published yet: openwords.ai serves its homepage for unknown paths, so this
// currently lands users on the homepage rather than 404ing. Shipped deliberately ahead of the
// page going live; nothing here needs to change once it does.
private const val ROADMAP_URL = "https://openwords.ai/roadmap"

@Preview
@Composable
private fun LanguageRequestDialogEmptyPreview(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        LanguageRequestDialog(
            learnLanguage = "",
            translateLanguage = "",
            isSending = false,
            error = null,
            discussionUrl = null,
            onLearnLanguageChange = {},
            onTranslateLanguageChange = {},
            onDismiss = {},
            onSend = {}
        )
    }
}

@Preview
@Composable
private fun LanguageRequestDialogFilledPreview(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        LanguageRequestDialog(
            learnLanguage = "Ukrainian",
            translateLanguage = "",
            isSending = false,
            error = null,
            discussionUrl = null,
            onLearnLanguageChange = {},
            onTranslateLanguageChange = {},
            onDismiss = {},
            onSend = {}
        )
    }
}

@Preview
@Composable
private fun LanguageRequestDialogSendingPreview(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        LanguageRequestDialog(
            learnLanguage = "Ukrainian",
            translateLanguage = "Polski",
            isSending = true,
            error = null,
            discussionUrl = null,
            onLearnLanguageChange = {},
            onTranslateLanguageChange = {},
            onDismiss = {},
            onSend = {}
        )
    }
}

@Preview
@Composable
private fun LanguageRequestDialogErrorPreview(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        LanguageRequestDialog(
            learnLanguage = "",
            translateLanguage = "",
            isSending = false,
            error = "Enter at least one language",
            discussionUrl = null,
            onLearnLanguageChange = {},
            onTranslateLanguageChange = {},
            onDismiss = {},
            onSend = {}
        )
    }
}

@Preview
@Composable
private fun LanguageRequestDialogSentPreview(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        LanguageRequestDialog(
            learnLanguage = "Ukrainian",
            translateLanguage = "",
            isSending = false,
            error = null,
            discussionUrl = "https://github.com/slovymovy/slovy-movy-app/discussions/1",
            onLearnLanguageChange = {},
            onTranslateLanguageChange = {},
            onDismiss = {},
            onSend = {}
        )
    }
}
