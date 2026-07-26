package com.slovy.slovymovyapp.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.getPlatform
import com.slovy.slovymovyapp.speech.Text2SpeechLanguage
import com.slovy.slovymovyapp.speech.Text2SpeechVoice
import com.slovy.slovymovyapp.speech.VoiceQuality
import com.slovy.slovymovyapp.ui.components.SpinningProgressIndicator
import com.slovy.slovymovyapp.ui.theme.AppSpacing
import org.jetbrains.compose.resources.stringResource
import slovymovyapp.composeapp.generated.resources.Res
import slovymovyapp.composeapp.generated.resources.common_action_collapse
import slovymovyapp.composeapp.generated.resources.common_action_expand
import slovymovyapp.composeapp.generated.resources.common_open_system_settings
import slovymovyapp.composeapp.generated.resources.common_state_collapsed
import slovymovyapp.composeapp.generated.resources.common_state_expanded
import slovymovyapp.composeapp.generated.resources.voice_download_more_step_android
import slovymovyapp.composeapp.generated.resources.voice_download_more_step_ios
import slovymovyapp.composeapp.generated.resources.voice_download_more_step_other
import slovymovyapp.composeapp.generated.resources.voice_download_more_step_two
import slovymovyapp.composeapp.generated.resources.voice_download_more_title
import slovymovyapp.composeapp.generated.resources.voice_step_two_android
import slovymovyapp.composeapp.generated.resources.voice_enable_label
import slovymovyapp.composeapp.generated.resources.voice_many_enabled
import slovymovyapp.composeapp.generated.resources.voice_network_online
import slovymovyapp.composeapp.generated.resources.voice_no_voices_available
import slovymovyapp.composeapp.generated.resources.voice_no_voices_enabled
import slovymovyapp.composeapp.generated.resources.voice_quality_good
import slovymovyapp.composeapp.generated.resources.voice_quality_high
import slovymovyapp.composeapp.generated.resources.voice_quality_medium
import slovymovyapp.composeapp.generated.resources.voice_test_action
import slovymovyapp.composeapp.generated.resources.voice_unknown_name

val TEST_PHRASES = mapOf(
    Language.ENGLISH to "Hello! This is a test of the text to speech system.",
    Language.RUSSIAN to "Привет! Это тест системы синтеза речи.",
    Language.POLISH to "Cześć! To jest test systemu syntezy mowy.",
    Language.DUTCH to "Hallo! Dit is een test van het tekst-naar-spraak systeem.",
    Language.GERMAN to "Hallo! Dies ist ein Test des Text-zu-Sprache-Systems.",
    Language.FRENCH to "Bonjour ! Ceci est un test du système de synthèse vocale.",
    Language.ITALIAN to "Ciao! Questo è un test del sistema di sintesi vocale.",
    Language.CZECH to "Ahoj! Toto je test systému převodu textu na řeč.",
    Language.TURKISH to "Merhaba! Bu, metinden konuşmaya sisteminin bir testidir.",
    Language.SPANISH to "¡Hola! Esta es una prueba del sistema de texto a voz."
)

@Composable
fun VoiceSectionItem(
    language: Text2SpeechLanguage,
    languageState: LanguageUiState,
    onExpand: () -> Unit,
    onTestVoice: (Text2SpeechVoice) -> Unit,
    onToggleVoiceEnabled: (String) -> Unit,
    testingVoice: Text2SpeechVoice? = null
) {
    val expandedStateDescription = stringResource(Res.string.common_state_expanded)
    val collapsedStateDescription = stringResource(Res.string.common_state_collapsed)
    val collapseAction = stringResource(Res.string.common_action_collapse)
    val expandAction = stringResource(Res.string.common_action_expand)
    val noVoicesEnabled = stringResource(Res.string.voice_no_voices_enabled)
    val unknownVoice = stringResource(Res.string.voice_unknown_name)

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) {
                        stateDescription = if (languageState.isExpanded) {
                            expandedStateDescription
                        } else {
                            collapsedStateDescription
                        }
                    }
                    .clickable(
                        onClick = onExpand,
                        role = Role.Button,
                        onClickLabel = if (languageState.isExpanded) collapseAction else expandAction
                    )
                    .padding(AppSpacing.lg),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )

                Spacer(modifier = Modifier.width(AppSpacing.md))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = language.language.selfName,
                        style = MaterialTheme.typography.titleMedium
                    )
                    // Counting only voices the engine reports keeps the summary honest when a
                    // stored selection belongs to an engine that is no longer bound.
                    val enabledVoices = languageState.enabledInstalledVoices
                    val voiceText = when {
                        languageState.voicesLoaded && languageState.voices.isEmpty() ->
                            stringResource(Res.string.voice_no_voices_available)

                        enabledVoices.isEmpty() -> noVoicesEnabled

                        enabledVoices.size == 1 -> enabledVoices.single().let {
                            "${it.name ?: unknownVoice} (${it.language.code.uppercase()})"
                        }

                        else -> stringResource(Res.string.voice_many_enabled, enabledVoices.size)
                    }
                    Text(
                        text = voiceText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Icon(
                    imageVector = if (languageState.isExpanded) {
                        Icons.Default.KeyboardArrowUp
                    } else {
                        Icons.Default.KeyboardArrowDown
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (languageState.isExpanded) {
                if (languageState.isLoadingVoices) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(AppSpacing.lg),
                        contentAlignment = Alignment.Center
                    ) {
                        SpinningProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                } else if (languageState.voices.isEmpty()) {
                    Text(
                        text = stringResource(Res.string.voice_no_voices_available),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = AppSpacing.lg, vertical = AppSpacing.sm)
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AppSpacing.lg),
                    ) {
                        languageState.voices.forEachIndexed { index, voice ->
                            if (index > 0) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = AppSpacing.sm),
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                            }
                            VoiceItem(
                                voice = voice,
                                onTest = { onTestVoice(voice) },
                                isTesting = (testingVoice == voice),
                                isEnabled = voice.id in languageState.enabledVoiceIds,
                                onToggleEnabled = { onToggleVoiceEnabled(voice.id) }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(AppSpacing.lg))
            }
        }
    }
}

@Composable
fun DownloadMoreVoicesCard(onOpenSettings: () -> Unit) {
    val platform = getPlatform().name
    val isIos = platform.contains("iOS", ignoreCase = true)
    val isAndroid = platform.contains("Android", ignoreCase = true)

    val step1Instruction = when {
        isAndroid -> stringResource(Res.string.voice_download_more_step_android)
        isIos -> stringResource(Res.string.voice_download_more_step_ios)
        else -> stringResource(Res.string.voice_download_more_step_other)
    }

    val step2Instruction = if (isAndroid) {
        stringResource(Res.string.voice_step_two_android)
    } else {
        stringResource(Res.string.voice_download_more_step_two)
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(AppSpacing.md))
                Text(
                    text = stringResource(Res.string.voice_download_more_title),
                    style = MaterialTheme.typography.titleMedium
                )
            }

            StepRow(number = 1, text = step1Instruction)
            StepRow(number = 2, text = step2Instruction)

            if (isAndroid || isIos) {
                FilledTonalButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(Res.string.common_open_system_settings))
                }
            }
        }
    }
}

@Composable
fun VoiceItem(
    voice: Text2SpeechVoice,
    onTest: () -> Unit = {},
    isTesting: Boolean = false,
    isEnabled: Boolean = true,
    onToggleEnabled: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AppSpacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
        ) {
            if (voice.name == null) {
                Text(
                    text = voice.id,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = voice.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }

            val languagePair = remember(voice.localeTag, voice.id) {
                voice.localeTag?.uppercase() ?: run {
                    val pattern1 = Regex("^([a-zA-Z]{2}-[a-zA-Z]{2})")
                    val pattern2 = Regex("com\\.apple\\.voice\\.[a-zA-Z]+\\.([a-zA-Z]{2}-[a-zA-Z]{2})\\.")

                    (pattern1.find(voice.id)?.groupValues?.get(1)
                        ?: pattern2.find(voice.id)?.groupValues?.get(1))?.uppercase()
                }
            }

            if (languagePair != null) {
                Text(
                    text = languagePair,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val qualityColor = when (voice.quality) {
                    VoiceQuality.BEST -> MaterialTheme.colorScheme.primary
                    VoiceQuality.GOOD -> MaterialTheme.colorScheme.tertiary
                    VoiceQuality.MEDIUM -> MaterialTheme.colorScheme.outline
                }
                Surface(
                    shape = CircleShape,
                    border = BorderStroke(1.dp, qualityColor.copy(alpha = 0.5f)),
                    color = qualityColor.copy(alpha = 0.1f)
                ) {
                    Text(
                        text = when (voice.quality) {
                            VoiceQuality.BEST -> stringResource(Res.string.voice_quality_high)
                            VoiceQuality.GOOD -> stringResource(Res.string.voice_quality_good)
                            VoiceQuality.MEDIUM -> stringResource(Res.string.voice_quality_medium)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = qualityColor,
                        modifier = Modifier.padding(horizontal = AppSpacing.sm, vertical = 2.dp)
                    )
                }
                if (voice.networkConnectionRequired) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                    ) {
                        Text(
                            text = stringResource(Res.string.voice_network_online),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = AppSpacing.sm, vertical = 2.dp)
                        )
                    }
                }
            }
        }

        IconButton(onClick = onTest) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = stringResource(Res.string.voice_test_action),
                tint = if (isTesting) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        CircularToggle(
            isSelected = isEnabled,
            onClick = onToggleEnabled,
            label = stringResource(Res.string.voice_enable_label, voice.name ?: voice.id)
        )
    }
}
