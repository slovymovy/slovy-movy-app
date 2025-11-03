package com.slovy.slovymovyapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.jetbrains.compose.ui.tooling.preview.PreviewParameter

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
@Composable
fun PartOfSpeechIndicator(
    partOfSpeech: String,
    modifier: Modifier = Modifier
) {
    val color = getPartOfSpeechColor(partOfSpeech)

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
    ) {
        // Vertical color bar
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(24.dp)
                .background(color, RoundedCornerShape(2.dp))
        )

        // Part of speech label
        Text(
            text = partOfSpeech.capitalize(Locale.current),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
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
        "name" -> Color(0xFF3B82F6)
        else -> Color(0xFF6B7280)
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


@Preview
@Composable
private fun PartOfSpeechIndicatorPreview(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
        ) {
            PartOfSpeechIndicator(partOfSpeech = "verb")
            PartOfSpeechIndicator(partOfSpeech = "noun")
            PartOfSpeechIndicator(partOfSpeech = "adjective")
            PartOfSpeechIndicator(partOfSpeech = "adverb")
            PartOfSpeechIndicator(partOfSpeech = "preposition")
            PartOfSpeechIndicator(partOfSpeech = "pronoun")
        }
    }
}

@Preview
@Composable
private fun PartOfSpeechBadgePreview(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
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
