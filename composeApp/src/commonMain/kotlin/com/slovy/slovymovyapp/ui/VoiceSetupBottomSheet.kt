package com.slovy.slovymovyapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import org.jetbrains.compose.resources.stringResource
import slovymovyapp.composeapp.generated.resources.Res
import slovymovyapp.composeapp.generated.resources.common_later
import slovymovyapp.composeapp.generated.resources.common_open_system_settings
import slovymovyapp.composeapp.generated.resources.voice_setup_description
import slovymovyapp.composeapp.generated.resources.voice_setup_step_android
import slovymovyapp.composeapp.generated.resources.voice_setup_step_ios
import slovymovyapp.composeapp.generated.resources.voice_setup_step_other
import slovymovyapp.composeapp.generated.resources.voice_setup_step_two
import slovymovyapp.composeapp.generated.resources.voice_setup_title

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceSetupBottomSheet(
    language: Language,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
    onLater: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        VoiceSetupBottomSheetContent(
            language = language,
            onOpenSettings = onOpenSettings,
            onLater = onLater
        )
    }
}

@Composable
fun VoiceSetupBottomSheetContent(
    language: Language,
    onOpenSettings: () -> Unit,
    onLater: () -> Unit
) {
    val platform = getPlatform().name
    val isIos = platform.contains("iOS", ignoreCase = true)
    val isAndroid = platform.contains("Android", ignoreCase = true)

    val step1Instruction = when {
        isAndroid -> stringResource(Res.string.voice_setup_step_android, language.selfName)
        isIos -> stringResource(Res.string.voice_setup_step_ios)
        else -> stringResource(Res.string.voice_setup_step_other, language.selfName)
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
                text = stringResource(Res.string.voice_setup_title),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
            )
        }

        Text(
            text = stringResource(Res.string.voice_setup_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(AppSpacing.sm))

        StepRow(number = 1, text = step1Instruction)
        StepRow(number = 2, text = stringResource(Res.string.voice_setup_step_two))

        Spacer(modifier = Modifier.height(AppSpacing.sm))

        if (isAndroid || isIos) {
            FilledTonalButton(
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(Res.string.common_open_system_settings))
            }
        }

        TextButton(
            onClick = onLater,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(Res.string.common_later))
        }
    }
}

@Composable
internal fun StepRow(number: Int, text: String) {
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
                onLater = {}
            )
        }
    }
}
