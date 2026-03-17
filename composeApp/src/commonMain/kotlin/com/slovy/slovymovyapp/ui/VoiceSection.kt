package com.slovy.slovymovyapp.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.getPlatform
import com.slovy.slovymovyapp.speech.Text2SpeechLanguage
import com.slovy.slovymovyapp.speech.Text2SpeechVoice
import com.slovy.slovymovyapp.speech.VoiceQuality
import com.slovy.slovymovyapp.ui.theme.AppSpacing

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
                        stateDescription = if (languageState.isExpanded) "Expanded" else "Collapsed"
                    }
                    .clickable(
                        onClick = onExpand,
                        role = Role.Button,
                        onClickLabel = if (languageState.isExpanded) "Collapse" else "Expand"
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
                    val enabledVoicesCount = languageState.enabledVoiceIds.size
                    val voiceText = when {
                        enabledVoicesCount == 0 -> "No voices enabled"
                        enabledVoicesCount == 1 -> {
                            languageState.voices.find { it.id in languageState.enabledVoiceIds }?.let {
                                "${it.name ?: "Unknown"} (${it.language.code.uppercase()})"
                            } ?: "1 voice enabled"
                        }

                        else -> "$enabledVoicesCount voices enabled"
                    }
                    Text(
                        text = voiceText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Icon(
                    imageVector = if (languageState.isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
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
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                } else if (languageState.voices.isEmpty()) {
                    Text(
                        text = "No voices available",
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
        isAndroid -> "Open your phone's text-to-speech settings and download a high-quality voice."
        isIos -> "Go to your iPhone Settings → Accessibility → Read & Speak → Voices and download a high-quality voice."
        else -> "Download a high-quality voice from your system settings."
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
                    text = "Download more voices",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            StepRow(number = 1, text = step1Instruction)
            StepRow(number = 2, text = "Come back to Open Words Settings → Voice and enable the new voice.")

            if (isAndroid || isIos) {
                FilledTonalButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Open System Settings")
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

            val languagePair = remember(voice.id) {
                val pattern1 = Regex("^([a-zA-Z]{2}-[a-zA-Z]{2})")
                val pattern2 = Regex("com\\.apple\\.voice\\.[a-zA-Z]+\\.([a-zA-Z]{2}-[a-zA-Z]{2})\\.")

                (pattern1.find(voice.id)?.groupValues?.get(1)
                    ?: pattern2.find(voice.id)?.groupValues?.get(1))?.uppercase()
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
                            VoiceQuality.BEST -> "High"
                            VoiceQuality.GOOD -> "Good"
                            VoiceQuality.MEDIUM -> "Medium"
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
                            text = "Online",
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
                contentDescription = "Test Voice",
                tint = if (isTesting) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        CircularToggle(
            isSelected = isEnabled,
            onClick = onToggleEnabled,
            label = "Enable ${voice.name ?: voice.id}"
        )
    }
}
