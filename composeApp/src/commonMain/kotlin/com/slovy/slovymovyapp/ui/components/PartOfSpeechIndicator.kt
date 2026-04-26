package com.slovy.slovymovyapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slovy.slovymovyapp.data.remote.PartOfSpeech
import com.slovy.slovymovyapp.ui.ThemePreviewProvider
import com.slovy.slovymovyapp.ui.ThemedPreview
import com.slovy.slovymovyapp.ui.theme.AppSpacing
import com.slovy.slovymovyapp.ui.word.ErrorIcon
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import slovymovyapp.composeapp.generated.resources.Res
import slovymovyapp.composeapp.generated.resources.part_of_speech_generating_definitions_examples
import slovymovyapp.composeapp.generated.resources.part_of_speech_with_sense_count

/**
 * Vertical color bar indicator for parts of speech, matching Figma design.
 *
 * Color mapping is keyed off the [PartOfSpeech] enum so it stays stable across locales:
 * - VERB: Emerald (#10B981)
 * - NOUN: Blue (#3B82F6)
 * - ADJECTIVE: Orange (#F97316)
 * - ADVERB: Purple (#A855F7)
 * - PREPOSITION: Pink (#EC4899)
 * - PRONOUN: Cyan (#06B6D4)
 * - NAME: Pink-ish (#D598AA)
 * - default: Lime (#B4D42A)
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PartOfSpeechIndicator(
    pos: PartOfSpeech,
    modifier: Modifier = Modifier,
    meaningCount: Int? = null,
    cardLoading: Boolean = false,
    cardError: String? = null
) {
    val color = getPartOfSpeechColor(pos)
    val partOfSpeech = stringResource(pos.displayName)

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
    ) {
        if (!cardLoading && cardError == null) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(color, CircleShape)
            )
        } else if (cardLoading) {
            ContainedLoadingIndicator(indicatorColor = color)
        } else if (cardError != null) {
            ErrorIcon(Modifier.size(30.dp))
        }

        if (!cardLoading && cardError == null) {
            val label = if (meaningCount != null) {
                pluralStringResource(
                    Res.plurals.part_of_speech_with_sense_count,
                    meaningCount,
                    partOfSpeech,
                    meaningCount
                )
            } else {
                partOfSpeech
            }.uppercase()
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        } else if (cardLoading) {
            Text(
                text = stringResource(Res.string.part_of_speech_generating_definitions_examples),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        } else if (cardError != null) {
            Text(
                text = cardError,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

/**
 * Returns the color for a given part of speech. Keyed off the enum so it stays stable across locales.
 */
fun getPartOfSpeechColor(pos: PartOfSpeech): Color {
    return when (pos) {
        PartOfSpeech.VERB -> Color(0xFF10B981)
        PartOfSpeech.NOUN -> Color(0xFF3B82F6)
        PartOfSpeech.ADJECTIVE -> Color(0xFFF97316)
        PartOfSpeech.ADVERB -> Color(0xFFA855F7)
        PartOfSpeech.PREPOSITION -> Color(0xFFEC4899)
        PartOfSpeech.PRONOUN -> Color(0xFF06B6D4)
        PartOfSpeech.NAME -> Color(0xFFD598AA)
        else -> Color(0xFFB4D42A)
    }
}

/**
 * Compact part of speech badge without the vertical bar.
 */
@Composable
fun PartOfSpeechBadge(
    pos: PartOfSpeech,
    modifier: Modifier = Modifier
) {
    val color = getPartOfSpeechColor(pos)
    val label = stringResource(pos.displayName)

    Box(
        modifier = modifier
            .background(
                color = color.copy(alpha = 0.1f),
                shape = MaterialTheme.shapes.small
            )
            .padding(horizontal = AppSpacing.md, vertical = 6.dp)
    ) {
        Text(
            text = label.replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}


@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun PartOfSpeechIndicatorPreview(
    @androidx.compose.ui.tooling.preview.PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
        ) {
            PartOfSpeechIndicator(pos = PartOfSpeech.VERB, meaningCount = 3)
            PartOfSpeechIndicator(pos = PartOfSpeech.NOUN, meaningCount = 1)
            PartOfSpeechIndicator(pos = PartOfSpeech.ADJECTIVE, meaningCount = 5)
            PartOfSpeechIndicator(pos = PartOfSpeech.ADVERB, meaningCount = 2)
            PartOfSpeechIndicator(pos = PartOfSpeech.PREPOSITION)
            PartOfSpeechIndicator(pos = PartOfSpeech.PRONOUN)
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun PartOfSpeechIndicatorPreviewLoadingError(
    @androidx.compose.ui.tooling.preview.PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
        ) {
            PartOfSpeechIndicator(pos = PartOfSpeech.VERB, cardLoading = true, cardError = null)
            PartOfSpeechIndicator(pos = PartOfSpeech.NOUN, cardLoading = false, cardError = "Errors")
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun PartOfSpeechBadgePreview(
    @androidx.compose.ui.tooling.preview.PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
            ) {
                PartOfSpeechBadge(pos = PartOfSpeech.VERB)
                PartOfSpeechBadge(pos = PartOfSpeech.NOUN)
                PartOfSpeechBadge(pos = PartOfSpeech.ADJECTIVE)
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
            ) {
                PartOfSpeechBadge(pos = PartOfSpeech.ADVERB)
                PartOfSpeechBadge(pos = PartOfSpeech.PREPOSITION)
                PartOfSpeechBadge(pos = PartOfSpeech.PRONOUN)
            }
        }
    }
}
