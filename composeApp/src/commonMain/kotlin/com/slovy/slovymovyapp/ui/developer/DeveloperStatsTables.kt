package com.slovy.slovymovyapp.ui.developer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.slovy.slovymovyapp.ui.components.SpinningProgressIndicator
import com.slovy.slovymovyapp.ui.theme.AppSpacing
import org.jetbrains.compose.resources.stringResource
import slovymovyapp.composeapp.generated.resources.*

@Composable
internal fun SchedulingStatsCard(
    stats: DeveloperScheduleStats,
    isLoading: Boolean,
    errorLabel: String?,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        ) {
            if (isLoading) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                ) {
                    SpinningProgressIndicator(modifier = Modifier.size(16.dp))
                    Text(
                        text = stringResource(Res.string.developer_stats_loading),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                SchedulingStatsTable(stats = stats)
                if (stats.familyCounts.isNotEmpty()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    FamilyCountsTable(familyCounts = stats.familyCounts)
                }
            }
            if (errorLabel != null) {
                Text(
                    text = stringResource(Res.string.developer_stats_error, errorLabel),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun FamilyCountsTable(familyCounts: List<DeveloperFamilyCount>) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
    ) {
        FamilyCountsTableRow(
            family = stringResource(Res.string.developer_stats_family_header),
            count = stringResource(Res.string.developer_stats_cards_header),
            isHeader = true,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        familyCounts.forEach { row ->
            FamilyCountsTableRow(
                family = row.family,
                count = row.cardCount.toString(),
                isHeader = false,
            )
        }
    }
}

@Composable
private fun FamilyCountsTableRow(
    family: String,
    count: String,
    isHeader: Boolean,
) {
    val textStyle = if (isHeader) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium
    val valueStyle = if (isHeader) MaterialTheme.typography.labelMedium else MaterialTheme.typography.titleMedium
    val color = if (isHeader) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
    ) {
        Text(
            text = family,
            style = textStyle,
            color = color,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = count,
            style = valueStyle,
            fontWeight = if (isHeader) FontWeight.Medium else FontWeight.SemiBold,
            color = color,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.45f),
        )
    }
}

@Composable
private fun SchedulingStatsTable(stats: DeveloperScheduleStats) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
    ) {
        SchedulingStatsTableRow(
            label = stringResource(Res.string.developer_stats_metric_header),
            cards = stringResource(Res.string.developer_stats_cards_header),
            lemmas = stringResource(Res.string.developer_stats_lemmas_header),
            isHeader = true,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        SchedulingStatsTableRow(
            label = stringResource(Res.string.developer_stats_future_label),
            cards = stats.futureScheduledCards.toString(),
            lemmas = stats.futureScheduledLemmas.toString(),
            isHeader = false,
        )
        SchedulingStatsTableRow(
            label = stringResource(Res.string.developer_stats_suppressed_label),
            cards = stats.availableAfterSuppressedCards.toString(),
            lemmas = stats.availableAfterSuppressedLemmas.toString(),
            isHeader = false,
        )
    }
}

@Composable
private fun SchedulingStatsTableRow(
    label: String,
    cards: String,
    lemmas: String,
    isHeader: Boolean,
) {
    val textStyle = if (isHeader) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium
    val valueStyle = if (isHeader) MaterialTheme.typography.labelMedium else MaterialTheme.typography.titleMedium
    val color = if (isHeader) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
    ) {
        Text(
            text = label,
            style = textStyle,
            color = labelColor,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = cards,
            style = valueStyle,
            fontWeight = if (isHeader) FontWeight.Medium else FontWeight.SemiBold,
            color = color,
            modifier = Modifier.weight(0.45f),
            textAlign = TextAlign.End,
        )
        Text(
            text = lemmas,
            style = valueStyle,
            fontWeight = if (isHeader) FontWeight.Medium else FontWeight.SemiBold,
            color = color,
            modifier = Modifier.weight(0.45f),
            textAlign = TextAlign.End,
        )
    }
}
