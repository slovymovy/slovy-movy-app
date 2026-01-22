package com.slovy.slovymovyapp.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.slovy.slovymovyapp.ui.ThemePreviewProvider
import com.slovy.slovymovyapp.ui.ThemedPreview
import com.slovy.slovymovyapp.ui.theme.elevationLevel1

/**
 * Themed card component following Material Design 3 principles.
 *
 * Automatically applies:
 * - 28dp rounded corners (extraLarge shape)
 * - Level 1 elevation (1dp with hover at 3dp)
 * - Surface color with outline variant border
 * - Hover and focus states
 *
 * @param modifier Modifier to be applied to the card
 * @param onClick Optional click handler. If provided, card becomes clickable.
 * @param elevation Card elevation level. Defaults to level1.
 * @param shape Card shape. Defaults to MaterialTheme extraLarge (28dp).
 * @param content Content to be displayed inside the card
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    elevation: CardElevation = CardDefaults.elevationLevel1(),
    shape: Shape = MaterialTheme.shapes.small,
    colors: CardColors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surface
    ),
    border: BorderStroke? = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    content: @Composable ColumnScope.() -> Unit
) {
    // Always use non-clickable Card variant for consistent layout,
    // apply clickability via modifier to avoid alignment differences
    // between Card(onClick) and Card() overloads
    Card(
        modifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier,
        elevation = elevation,
        shape = shape,
        colors = colors,
        border = border,
        content = content
    )
}

/**
 * Compact card variant with medium rounded corners (12dp).
 * Used for secondary content, badges, and compact layouts.
 *
 * When clickable (onClick != null):
 * - Adds a subtle border
 * - Increases elevation
 * - Provides visual feedback
 */
@Composable
fun CompactCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    AppCard(
        modifier = modifier,
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        border = if (onClick != null) {
            BorderStroke(1.5.dp, MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.3f))
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        },
        elevation = if (onClick != null) {
            CardDefaults.cardElevation(
                defaultElevation = 2.dp,
                pressedElevation = 4.dp,
                hoveredElevation = 3.dp
            )
        } else {
            CardDefaults.elevationLevel1()
        },
        content = content
    )
}


@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
private fun AppCardPreview(
    @androidx.compose.ui.tooling.preview.PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        AppCard(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Sample Card Content",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun AppCardClickablePreview(
    @androidx.compose.ui.tooling.preview.PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        AppCard(
            modifier = Modifier.padding(16.dp),
            onClick = {}
        ) {
            Text(
                text = "Clickable Card",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun CompactCardPreview(
    @androidx.compose.ui.tooling.preview.PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        CompactCard(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Compact Card",
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
