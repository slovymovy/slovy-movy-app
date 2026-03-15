package com.slovy.slovymovyapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.getPlatform
import com.slovy.slovymovyapp.ui.theme.AppSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceSetupBottomSheet(
    language: Language,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        VoiceSetupBottomSheetContent(
            language = language,
            onOpenSettings = onOpenSettings,
            onDismiss = onDismiss
        )
    }
}

@Composable
fun VoiceSetupBottomSheetContent(
    language: Language,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    val platform = getPlatform().name
    val isIos = platform.contains("iOS", ignoreCase = true)
    val isAndroid = platform.contains("Android", ignoreCase = true)

    val step1Instruction = when {
        isAndroid -> "Open TTS settings and download a high-quality voice for ${language.selfName}."
        isIos -> "Go to Settings → Accessibility → Spoken Content → Voices and download a voice for ${language.selfName}."
        else -> "Download a high-quality voice for ${language.selfName} from your system settings."
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.xl)
            .padding(bottom = AppSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.VolumeUp,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = "Get better pronunciation",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
            )
        }

        Text(
            text = "The default voice may sound robotic. Here's how to get a better one:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(AppSpacing.sm))

        StepRow(number = 1, text = step1Instruction)
        StepRow(number = 2, text = "Come back to Settings → Voices and enable the new voice.")

        Spacer(modifier = Modifier.height(AppSpacing.sm))

        if (isAndroid || isIos) {
            Button(
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Open Settings")
            }
        }

        TextButton(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Later")
        }
    }
}

@Composable
private fun StepRow(number: Int, text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
        modifier = Modifier.fillMaxWidth()
    ) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(24.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = number.toString(),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
    }
}

@Preview
@Composable
private fun VoiceSetupBottomSheetPreview(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        Surface {
            VoiceSetupBottomSheetContent(
                language = Language.DUTCH,
                onOpenSettings = {},
                onDismiss = {}
            )
        }
    }
}
