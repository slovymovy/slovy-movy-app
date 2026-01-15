package com.slovy.slovymovyapp.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.capitalize
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import com.slovy.slovymovyapp.ui.ThemePreviewProvider
import com.slovy.slovymovyapp.ui.ThemedPreview
import com.slovy.slovymovyapp.ui.theme.AppSpacing

/**
 * Badge displaying word frequency level with color-coded indicators.
 *
 * Frequency levels:
 * - High: Green background/text (#10B981/#065F46)
 * - Medium: Amber background/text (#F59E0B/#92400E)
 * - Low: Gray background/text (#6B7280/#374151)
 *
 * @param frequency Frequency level: "high", "medium", or "low"
 * @param modifier Modifier to be applied to the badge
 */
@Composable
fun FrequencyBadge(
    frequency: String,
    modifier: Modifier = Modifier
) {
    val (backgroundColor, textColor, borderColor) = when (frequency.lowercase()) {
        "high" -> Triple(
            Color(0xFF10B981).copy(alpha = 0.1f),
            Color(0xFF065F46),
            Color(0xFF10B981).copy(alpha = 0.2f)
        )

        "medium" -> Triple(
            Color(0xFFF59E0B).copy(alpha = 0.1f),
            Color(0xFF92400E),
            Color(0xFFF59E0B).copy(alpha = 0.2f)
        )

        "low" -> Triple(
            Color(0xFF6B7280).copy(alpha = 0.1f),
            Color(0xFF374151),
            Color(0xFF6B7280).copy(alpha = 0.2f)
        )

        else -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )
    }

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = backgroundColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Text(
            text = frequency.capitalize(Locale.current),
            modifier = Modifier.padding(horizontal = AppSpacing.md, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            color = textColor
        )
    }
}

/**
 * Compact frequency badge for use in dense layouts.
 */
@Composable
fun CompactFrequencyBadge(
    frequency: String,
    modifier: Modifier = Modifier
) {
    val (backgroundColor, textColor, borderColor) = when (frequency.lowercase()) {
        "high" -> Triple(
            Color(0xFF10B981).copy(alpha = 0.1f),
            Color(0xFF065F46),
            Color(0xFF10B981).copy(alpha = 0.2f)
        )

        "medium" -> Triple(
            Color(0xFFF59E0B).copy(alpha = 0.1f),
            Color(0xFF92400E),
            Color(0xFFF59E0B).copy(alpha = 0.2f)
        )

        "low" -> Triple(
            Color(0xFF6B7280).copy(alpha = 0.1f),
            Color(0xFF374151),
            Color(0xFF6B7280).copy(alpha = 0.2f)
        )

        else -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )
    }

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = backgroundColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Text(
            text = frequency.capitalize(Locale.current),
            modifier = Modifier.padding(horizontal = AppSpacing.sm, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = textColor
        )
    }
}


@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun FrequencyBadgePreview(
    @androidx.compose.ui.tooling.preview.PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
        ) {
            FrequencyBadge(frequency = "high")
            FrequencyBadge(frequency = "medium")
            FrequencyBadge(frequency = "low")
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun CompactFrequencyBadgePreview(
    @androidx.compose.ui.tooling.preview.PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
        ) {
            CompactFrequencyBadge(frequency = "high")
            CompactFrequencyBadge(frequency = "medium")
            CompactFrequencyBadge(frequency = "low")
        }
    }
}
