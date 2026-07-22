package com.slovy.slovymovyapp.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Canvas
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.slovy.slovymovyapp.data.export.WordListExportFormat
import com.slovy.slovymovyapp.ui.theme.serifFontFamily
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
import slovymovyapp.composeapp.generated.resources.favorites_export_subtitle

@Composable
fun WordListExportDialog(
    state: WordListExportUiState,
    wordCount: Int,
    meaningCount: Int,
    onFormatChange: (WordListExportFormat) -> Unit,
    onIncludeStatsChange: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.width(330.dp),
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)),
            shadowElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 24.dp, bottom = 18.dp),
            ) {
                Text(
                    text = stringResource(Res.string.favorites_export_dialog_title),
                    fontFamily = MaterialTheme.serifFontFamily,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = (-0.3).sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(Res.string.favorites_export_subtitle, wordCount, meaningCount),
                    fontFamily = MaterialTheme.serifFontFamily,
                    fontStyle = FontStyle.Italic,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(18.dp))
                Column(
                    modifier = Modifier.selectableGroup(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
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
                HorizontalDivider(
                    modifier = Modifier.padding(top = 18.dp, bottom = 6.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = state.includeStats,
                            enabled = !state.inProgress,
                            role = Role.Checkbox,
                            onValueChange = onIncludeStatsChange,
                        )
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ExportCheckbox(checked = state.includeStats)
                    Text(
                        text = stringResource(Res.string.favorites_export_include_stats),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss, enabled = !state.inProgress) {
                        Text(
                            text = stringResource(Res.string.common_cancel),
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Button(
                        onClick = onConfirm,
                        enabled = !state.inProgress,
                        shape = RoundedCornerShape(100),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                        contentPadding = PaddingValues(horizontal = 26.dp, vertical = 12.dp),
                    ) {
                        if (state.inProgress) {
                            CircularProgressIndicator(
                                modifier = Modifier.padding(end = 8.dp).size(16.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                        Text(
                            text = stringResource(Res.string.favorites_export_confirm),
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExportFormatOption(
    title: String,
    subtitle: String,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
) {
    val borderColor by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
        else MaterialTheme.colorScheme.outlineVariant
    )
    val backgroundColor by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.09f)
        else MaterialTheme.colorScheme.primary.copy(alpha = 0f)
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .background(backgroundColor, RoundedCornerShape(14.dp))
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onSelect,
            )
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ExportRadio(selected = selected)
        Column(modifier = Modifier.padding(start = 15.dp)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ExportRadio(selected: Boolean) {
    val borderColor by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    )
    val dotScale by animateFloatAsState(if (selected) 1f else 0f)
    Box(
        modifier = Modifier
            .size(22.dp)
            .border(2.dp, borderColor, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(11.dp)
                .scale(dotScale)
                .background(MaterialTheme.colorScheme.primary, CircleShape)
        )
    }
}

@Composable
private fun ExportCheckbox(checked: Boolean) {
    val shape = RoundedCornerShape(7.dp)
    val borderColor by animateColorAsState(
        if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    )
    val fillColor by animateColorAsState(
        if (checked) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.primary.copy(alpha = 0f)
    )
    val checkScale by animateFloatAsState(if (checked) 1f else 0f)
    val checkColor = MaterialTheme.colorScheme.onPrimary
    Box(
        modifier = Modifier
            .size(22.dp)
            .background(fillColor, shape)
            .border(2.dp, borderColor, shape),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(14.dp).scale(checkScale)) {
            val path = Path().apply {
                moveTo(size.width * 0.14f, size.height * 0.55f)
                lineTo(size.width * 0.42f, size.height * 0.80f)
                lineTo(size.width * 0.88f, size.height * 0.24f)
            }
            drawPath(
                path = path,
                color = checkColor,
                style = Stroke(
                    width = 3.4.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
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
            wordCount = 72,
            meaningCount = 72,
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
            wordCount = 72,
            meaningCount = 72,
            onFormatChange = {},
            onIncludeStatsChange = {},
            onConfirm = {},
            onDismiss = {},
        )
    }
}
