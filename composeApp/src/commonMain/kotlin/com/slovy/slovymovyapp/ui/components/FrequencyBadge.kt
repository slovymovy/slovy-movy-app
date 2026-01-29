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
import androidx.compose.ui.unit.dp
import com.slovy.slovymovyapp.data.remote.SenseFrequency
import com.slovy.slovymovyapp.ui.ThemePreviewProvider
import com.slovy.slovymovyapp.ui.ThemedPreview
import com.slovy.slovymovyapp.ui.theme.AppSpacing
import com.slovy.slovymovyapp.ui.word.colorsForFrequency

/**
 * Badge displaying word frequency level with theme-aware color-coded indicators.
 *
 * Uses [colorsForFrequency] for consistent theming across the app.
 *
 * @param frequency Frequency level from [SenseFrequency]
 * @param modifier Modifier to be applied to the badge
 */
@Composable
fun FrequencyBadge(
    frequency: SenseFrequency,
    modifier: Modifier = Modifier
) {
    val (backgroundColor, textColor) = colorsForFrequency(frequency)

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = backgroundColor,
        border = BorderStroke(0.5.dp, textColor.copy(alpha = 0.15f))
    ) {
        Text(
            text = frequency.label,
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
    frequency: SenseFrequency,
    modifier: Modifier = Modifier
) {
    val (backgroundColor, textColor) = colorsForFrequency(frequency)

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = backgroundColor,
        border = BorderStroke(0.5.dp, textColor.copy(alpha = 0.15f))
    ) {
        Text(
            text = frequency.label,
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
            FrequencyBadge(frequency = SenseFrequency.HIGH)
            FrequencyBadge(frequency = SenseFrequency.MIDDLE)
            FrequencyBadge(frequency = SenseFrequency.LOW)
            FrequencyBadge(frequency = SenseFrequency.VERY_LOW)
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
            CompactFrequencyBadge(frequency = SenseFrequency.HIGH)
            CompactFrequencyBadge(frequency = SenseFrequency.MIDDLE)
            CompactFrequencyBadge(frequency = SenseFrequency.LOW)
            CompactFrequencyBadge(frequency = SenseFrequency.VERY_LOW)
        }
    }
}
