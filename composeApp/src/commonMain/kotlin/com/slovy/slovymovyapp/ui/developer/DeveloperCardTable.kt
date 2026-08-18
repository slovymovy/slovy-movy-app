package com.slovy.slovymovyapp.ui.developer

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ScrollState
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.slovy.slovymovyapp.data.learning.intake.IntakeRunMode
import com.slovy.slovymovyapp.ui.components.SpinningProgressIndicator
import com.slovy.slovymovyapp.ui.theme.AppSpacing
import org.jetbrains.compose.resources.stringResource
import slovymovyapp.composeapp.generated.resources.*

@Composable
internal fun CardTableCard(
    rows: List<DeveloperCardTableRow>,
    pageInfo: DeveloperCardTablePageInfo,
    isLoading: Boolean,
    errorLabel: String?,
    horizontalScrollState: ScrollState,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
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
            CardTablePager(
                pageInfo = pageInfo,
                isLoading = isLoading,
                onPreviousPage = onPreviousPage,
                onNextPage = onNextPage,
            )
            when {
                isLoading -> LoadingStatusRow(text = stringResource(Res.string.developer_tables_loading))
                rows.isEmpty() -> Text(
                    text = stringResource(Res.string.developer_tables_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                else -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(horizontalScrollState),
                ) {
                    Column(
                        modifier = Modifier.width(CardTableTotalWidth),
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
                    ) {
                        CardTableRow(
                            cells = cardTableHeaders(),
                            isHeader = true,
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        rows.forEach { row ->
                            CardTableRow(
                                cells = row.cells,
                                isHeader = false,
                            )
                        }
                    }
                }
            }
            if (errorLabel != null) {
                Text(
                    text = stringResource(Res.string.developer_tables_error, errorLabel),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun CardTablePager(
    pageInfo: DeveloperCardTablePageInfo,
    isLoading: Boolean,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
    ) {
        Text(
            text = stringResource(
                Res.string.developer_tables_page_status,
                pageInfo.firstVisibleRow,
                pageInfo.lastVisibleRow,
                pageInfo.totalRows,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(
            onClick = onPreviousPage,
            enabled = pageInfo.canGoPrevious && !isLoading,
        ) {
            Text(stringResource(Res.string.developer_tables_previous_page))
        }
        TextButton(
            onClick = onNextPage,
            enabled = pageInfo.canGoNext && !isLoading,
        ) {
            Text(stringResource(Res.string.developer_tables_next_page))
        }
    }
}

@Composable
private fun cardTableHeaders(): List<String> = listOf(
    stringResource(Res.string.developer_card_table_col_lemma),
    stringResource(Res.string.developer_card_table_col_lang_code),
    stringResource(Res.string.developer_card_table_col_family),
    stringResource(Res.string.developer_card_table_col_state),
    stringResource(Res.string.developer_card_table_col_stability),
    stringResource(Res.string.developer_card_table_col_difficulty),
    stringResource(Res.string.developer_card_table_col_due),
    stringResource(Res.string.developer_card_table_col_last_review),
    stringResource(Res.string.developer_card_table_col_reps),
    stringResource(Res.string.developer_card_table_col_lapses),
    stringResource(Res.string.developer_card_table_col_created_at),
    stringResource(Res.string.developer_card_table_col_available_after),
    stringResource(Res.string.developer_card_table_col_answer_key),
    stringResource(Res.string.developer_card_table_col_suspended),
    stringResource(Res.string.developer_card_table_col_sense_id),
)

@Composable
private fun CardTableRow(cells: List<String>, isHeader: Boolean) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        cells.forEachIndexed { index, cell ->
            Text(
                text = cell,
                style = if (isHeader) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodySmall,
                fontWeight = if (isHeader) FontWeight.Medium else FontWeight.Normal,
                color = if (isHeader) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(CardTableColumnWidths[index]),
            )
        }
    }
}

@Composable
private fun LoadingStatusRow(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
    ) {
        SpinningProgressIndicator(modifier = Modifier.size(16.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun intakeModeLabel(mode: IntakeRunMode): String = when (mode) {
    IntakeRunMode.DAILY -> stringResource(Res.string.developer_intake_daily)
    IntakeRunMode.CONTINUE_NOW -> stringResource(Res.string.developer_intake_continue_now)
}
