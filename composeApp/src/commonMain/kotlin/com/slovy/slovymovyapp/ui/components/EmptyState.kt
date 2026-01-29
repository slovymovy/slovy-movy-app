package com.slovy.slovymovyapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Brush
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.slovy.slovymovyapp.ui.theme.AppSpacing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import com.slovy.slovymovyapp.ui.ThemedPreview
import com.slovy.slovymovyapp.ui.ThemePreviewProvider

/**
 * Empty state component with icon, title, and description.
 *
 * Follows Material Design 3 empty state patterns with centered layout.
 *
 * @param icon Icon to display
 * @param title Main message title
 * @param description Optional description text
 * @param modifier Modifier to be applied to the empty state
 * @param action Optional action button/composable
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    description: String? = null,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(AppSpacing.xxxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Icon with soft radial glow
        Box(
            modifier = Modifier.size(120.dp),
            contentAlignment = Alignment.Center
        ) {
            val glowColor = MaterialTheme.colorScheme.primary
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                glowColor.copy(alpha = 0.15f),
                                glowColor.copy(alpha = 0.10f),
                                glowColor.copy(alpha = 0.06f),
                                glowColor.copy(alpha = 0.03f),
                                glowColor.copy(alpha = 0.01f),
                                glowColor.copy(alpha = 0f)
                            )
                        )
                    )
            )
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = glowColor
            )
        }

        Spacer(modifier = Modifier.height(AppSpacing.lg))

        // Title
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        // Description (optional)
        if (description != null) {
            Spacer(modifier = Modifier.height(AppSpacing.sm))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 280.dp)
            )
        }

        // Action (optional)
        if (action != null) {
            Spacer(modifier = Modifier.height(AppSpacing.xl))
            action()
        }
    }
}

/**
 * Compact empty state variant for smaller spaces.
 */
@Composable
fun CompactEmptyState(
    icon: ImageVector,
    message: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(AppSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(AppSpacing.md))

        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}


@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun EmptyStatePreview(
    @androidx.compose.ui.tooling.preview.PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        EmptyState(
            icon = Icons.Filled.Search,
            title = "No results found",
            description = "Try adjusting your search or filter to find what you're looking for."
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun EmptyStateWithActionPreview(
    @androidx.compose.ui.tooling.preview.PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        EmptyState(
            icon = Icons.Filled.Search,
            title = "Start exploring",
            description = "Search for words to discover their meanings and usage.",
            action = {
                Button(onClick = {}) {
                    Text("Get Started")
                }
            }
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun CompactEmptyStatePreview(
    @androidx.compose.ui.tooling.preview.PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        CompactEmptyState(
            icon = Icons.Filled.Search,
            message = "No items found"
        )
    }
}
