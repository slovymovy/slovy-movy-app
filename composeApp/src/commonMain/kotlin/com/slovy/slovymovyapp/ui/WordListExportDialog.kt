package com.slovy.slovymovyapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.slovy.slovymovyapp.data.export.WordListExportFormat
import org.jetbrains.compose.resources.stringResource
import slovymovyapp.composeapp.generated.resources.Res
import slovymovyapp.composeapp.generated.resources.common_cancel
import slovymovyapp.composeapp.generated.resources.favorites_export_confirm
import slovymovyapp.composeapp.generated.resources.favorites_export_dialog_title
import slovymovyapp.composeapp.generated.resources.favorites_export_format_csv_subtitle
import slovymovyapp.composeapp.generated.resources.favorites_export_format_csv_title
import slovymovyapp.composeapp.generated.resources.favorites_export_format_tsv_subtitle
import slovymovyapp.composeapp.generated.resources.favorites_export_format_tsv_title
import slovymovyapp.composeapp.generated.resources.favorites_export_include_stats

@Composable
fun WordListExportDialog(
    state: WordListExportUiState,
    onFormatChange: (WordListExportFormat) -> Unit,
    onIncludeStatsChange: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.favorites_export_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Column(modifier = Modifier.selectableGroup()) {
                    ExportFormatOption(
                        title = stringResource(Res.string.favorites_export_format_csv_title),
                        subtitle = stringResource(Res.string.favorites_export_format_csv_subtitle),
                        selected = state.format == WordListExportFormat.CSV,
                        enabled = !state.inProgress,
                        onSelect = { onFormatChange(WordListExportFormat.CSV) },
                    )
                    ExportFormatOption(
                        title = stringResource(Res.string.favorites_export_format_tsv_title),
                        subtitle = stringResource(Res.string.favorites_export_format_tsv_subtitle),
                        selected = state.format == WordListExportFormat.TSV,
                        enabled = !state.inProgress,
                        onSelect = { onFormatChange(WordListExportFormat.TSV) },
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = state.includeStats,
                            enabled = !state.inProgress,
                            role = Role.Checkbox,
                            onValueChange = onIncludeStatsChange,
                        )
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = state.includeStats,
                        onCheckedChange = null,
                        enabled = !state.inProgress,
                    )
                    Text(
                        text = stringResource(Res.string.favorites_export_include_stats),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !state.inProgress) {
                if (state.inProgress) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = 8.dp).size(16.dp),
                        strokeWidth = 2.dp,
                    )
                }
                Text(stringResource(Res.string.favorites_export_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.inProgress) {
                Text(stringResource(Res.string.common_cancel))
            }
        },
    )
}

@Composable
private fun ExportFormatOption(
    title: String,
    subtitle: String,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onSelect,
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview
@Composable
private fun WordListExportDialogPreview(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean,
) {
    ThemedPreview(darkTheme = isDark) {
        WordListExportDialog(
            state = WordListExportUiState(),
            onFormatChange = {},
            onIncludeStatsChange = {},
            onConfirm = {},
            onDismiss = {},
        )
    }
}

@Preview
@Composable
private fun WordListExportDialogInProgressPreview(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean,
) {
    ThemedPreview(darkTheme = isDark) {
        WordListExportDialog(
            state = WordListExportUiState(
                format = WordListExportFormat.TSV,
                includeStats = false,
                inProgress = true,
            ),
            onFormatChange = {},
            onIncludeStatsChange = {},
            onConfirm = {},
            onDismiss = {},
        )
    }
}
