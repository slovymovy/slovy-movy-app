package com.slovy.slovymovyapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.ui.theme.AppSpacing

@Composable
fun DebugFavoritesSection(
    busy: Boolean,
    activeLanguage: Language?,
    onSeedFavoritesOnly: (Int) -> Unit,
    onSeedWithNewCards: (Int) -> Unit,
    onSeedWithReviewedCards: (Int) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val canSeed = !busy && activeLanguage != null
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
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
            Text(
                text = "Favorites perf seeder",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Bulk-seeds the active learning language (${activeLanguage?.code ?: "none"}) " +
                    "using real sense IDs from the downloaded dictionary.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (busy) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Text(
                        text = "Working…",
                        modifier = Modifier.padding(start = AppSpacing.sm),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            Text(
                text = "Pending intake (favorites only — cards trickle in via IntakeService):",
                style = MaterialTheme.typography.labelLarge
            )
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                FilledTonalButton(onClick = { onSeedFavoritesOnly(500) }, enabled = canSeed) {
                    Text("+500")
                }
                FilledTonalButton(onClick = { onSeedFavoritesOnly(4000) }, enabled = canSeed) {
                    Text("+4000")
                }
            }
            Text(
                text = "NEW cards (activates immediately, bypasses IntakeService — variants " +
                    "are not validated so some cards may surface as load errors in study):",
                style = MaterialTheme.typography.labelLarge
            )
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                FilledTonalButton(onClick = { onSeedWithNewCards(500) }, enabled = canSeed) {
                    Text("+500 new")
                }
                FilledTonalButton(onClick = { onSeedWithNewCards(4000) }, enabled = canSeed) {
                    Text("+4000 new")
                }
            }
            Text(
                text = "REVIEW cards with review_log (half due-now / half due-future, " +
                    "stability 5–40d, reps 1–15):",
                style = MaterialTheme.typography.labelLarge
            )
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                FilledTonalButton(onClick = { onSeedWithReviewedCards(500) }, enabled = canSeed) {
                    Text("+500 reviewed")
                }
                FilledTonalButton(onClick = { onSeedWithReviewedCards(4000) }, enabled = canSeed) {
                    Text("+4000 reviewed")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                OutlinedButton(onClick = onClear, enabled = !busy) {
                    Text("Delete all favorites + cards")
                }
            }
        }
    }
}

@Preview
@Composable
private fun DebugFavoritesSectionPreview(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        DebugFavoritesSection(
            busy = false,
            activeLanguage = Language.ENGLISH,
            onSeedFavoritesOnly = {},
            onSeedWithNewCards = {},
            onSeedWithReviewedCards = {},
            onClear = {}
        )
    }
}

@Preview
@Composable
private fun DebugFavoritesSectionBusyPreview(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        DebugFavoritesSection(
            busy = true,
            activeLanguage = Language.ENGLISH,
            onSeedFavoritesOnly = {},
            onSeedWithNewCards = {},
            onSeedWithReviewedCards = {},
            onClear = {}
        )
    }
}

@Preview
@Composable
private fun DebugFavoritesSectionNoLanguagePreview(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        DebugFavoritesSection(
            busy = false,
            activeLanguage = null,
            onSeedFavoritesOnly = {},
            onSeedWithNewCards = {},
            onSeedWithReviewedCards = {},
            onClear = {}
        )
    }
}
