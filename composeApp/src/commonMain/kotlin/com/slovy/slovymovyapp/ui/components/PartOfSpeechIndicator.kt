package com.slovy.slovymovyapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.capitalize
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import com.slovy.slovymovyapp.ui.ThemePreviewProvider
import com.slovy.slovymovyapp.ui.ThemedPreview
import com.slovy.slovymovyapp.ui.theme.AppSpacing
import com.slovy.slovymovyapp.ui.word.ErrorIcon
import com.slovy.slovymovyapp.ui.word.pluralEnding

/**
 * Vertical color bar indicator for parts of speech, matching Figma design.
 *
 * Color mapping:
 * - verb: Emerald (#10B981)
 * - noun: Blue (#3B82F6)
 * - adjective: Orange (#F97316)
 * - adverb: Purple (#A855F7)
 * - preposition: Pink (#EC4899)
 * - pronoun: Cyan (#06B6D4)
 * - name: Blue (#3B82F6)
 * - default: Gray (#6B7280)
 *
 * @param partOfSpeech Part of speech label
 * @param modifier Modifier to be applied to the indicator
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PartOfSpeechIndicator(
    partOfSpeech: String,
    modifier: Modifier = Modifier,
    meaningCount: Int? = null,
    cardLoading: Boolean = false,
    cardError: String? = null
) {
    val color = getPartOfSpeechColor(partOfSpeech)

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
    ) {
        if (!cardLoading && cardError == null) {
            // Vertical color bar
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(24.dp)
                    .background(color, RoundedCornerShape(2.dp))
            )
        } else if (cardLoading) {
            ContainedLoadingIndicator(indicatorColor = color)
        } else if (cardError != null) {
            ErrorIcon(Modifier.size(30.dp))
        }

        if (!cardLoading && cardError == null) {
            // Part of speech label
            Text(
                text = partOfSpeech.capitalize(Locale.current),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            // Optional meaning count (secondary)
            if (meaningCount != null) {
                Text(
                    text = "· $meaningCount meaning${pluralEnding(meaningCount)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (cardLoading) {
            Text(
                text = "Generating definitions and examples…",
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
 * Returns the color for a given part of speech.
 */
@Composable
fun getPartOfSpeechColor(partOfSpeech: String): Color {
    return when (partOfSpeech.lowercase()) {
        "verb" -> Color(0xFF10B981)
        "noun" -> Color(0xFF3B82F6)
        "adjective" -> Color(0xFFF97316)
        "adverb" -> Color(0xFFA855F7)
        "preposition" -> Color(0xFFEC4899)
        "pronoun" -> Color(0xFF06B6D4)
        "name" -> Color(0xFFD598AA)
        else -> Color(0xFFB4D42A)
    }
}

/**
 * Compact part of speech badge without the vertical bar.
 */
@Composable
fun PartOfSpeechBadge(
    partOfSpeech: String,
    modifier: Modifier = Modifier
) {
    val color = getPartOfSpeechColor(partOfSpeech)

    Box(
        modifier = modifier
            .background(
                color = color.copy(alpha = 0.1f),
                shape = MaterialTheme.shapes.small
            )
            .padding(horizontal = AppSpacing.md, vertical = 6.dp)
    ) {
        Text(
            text = partOfSpeech.capitalize(Locale.current),
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
            PartOfSpeechIndicator(partOfSpeech = "verb", meaningCount = 3)
            PartOfSpeechIndicator(partOfSpeech = "noun", meaningCount = 1)
            PartOfSpeechIndicator(partOfSpeech = "adjective", meaningCount = 5)
            PartOfSpeechIndicator(partOfSpeech = "adverb", meaningCount = 2)
            PartOfSpeechIndicator(partOfSpeech = "preposition")
            PartOfSpeechIndicator(partOfSpeech = "pronoun")
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
            PartOfSpeechIndicator(partOfSpeech = "verb", cardLoading = true, cardError = null)
            PartOfSpeechIndicator(partOfSpeech = "noun", cardLoading = false, cardError = "Errors")
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
                PartOfSpeechBadge(partOfSpeech = "verb")
                PartOfSpeechBadge(partOfSpeech = "noun")
                PartOfSpeechBadge(partOfSpeech = "adjective")
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
            ) {
                PartOfSpeechBadge(partOfSpeech = "adverb")
                PartOfSpeechBadge(partOfSpeech = "preposition")
                PartOfSpeechBadge(partOfSpeech = "pronoun")
            }
        }
    }
}
