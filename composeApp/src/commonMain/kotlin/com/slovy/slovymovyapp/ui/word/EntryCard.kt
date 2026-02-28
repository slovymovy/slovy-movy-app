package com.slovy.slovymovyapp.ui.word

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.slovy.slovymovyapp.data.forms.GridCell
import com.slovy.slovymovyapp.data.remote.FormsSchemeView
import com.slovy.slovymovyapp.data.remote.LanguageCardPosEntry
import com.slovy.slovymovyapp.data.remote.RelatedWord
import com.slovy.slovymovyapp.ui.components.PartOfSpeechIndicator
import kotlin.math.max
import kotlin.math.min

private data class PlacedGridCell(
    val sourceRow: Int,
    val sourceColumn: Int,
    val anchorRow: Int,
    val anchorColumn: Int,
    val cell: GridCell
)

private fun MutableList<MutableList<PlacedGridCell?>>.ensureSlot(row: Int, column: Int) {
    while (size <= row) add(mutableListOf())
    while (this[row].size <= column) this[row].add(null)
}

private fun buildPlacedGrid(view: FormsSchemeView): List<List<PlacedGridCell?>> {
    val matrix = mutableListOf<MutableList<PlacedGridCell?>>()

    view.view.grid.forEachIndexed { rowIndex, row ->
        var columnIndex = 0
        row.forEachIndexed { sourceColumn, cell ->
            while (matrix.getOrNull(rowIndex)?.getOrNull(columnIndex) != null) {
                columnIndex++
            }

            val placed = PlacedGridCell(
                sourceRow = rowIndex,
                sourceColumn = sourceColumn,
                anchorRow = rowIndex,
                anchorColumn = columnIndex,
                cell = cell
            )

            for (rowOffset in 0 until cell.rowspan) {
                for (columnOffset in 0 until cell.colspan) {
                    val targetRow = rowIndex + rowOffset
                    val targetColumn = columnIndex + columnOffset
                    matrix.ensureSlot(targetRow, targetColumn)
                    matrix[targetRow][targetColumn] = placed
                }
            }
            columnIndex += cell.colspan
        }
    }

    val maxColumns = matrix.maxOfOrNull { it.size } ?: 0
    return matrix.map { row ->
        if (row.size == maxColumns) row else row + List(maxColumns - row.size) { null }
    }
}

private fun resolvedFormValue(view: FormsSchemeView, sourceRow: Int, sourceColumn: Int): String? {
    if (sourceRow < 0 || sourceColumn < 0) return null
    return view.forms.getOrNull(sourceRow)?.getOrNull(sourceColumn)
}

private fun resolvedDataCount(view: FormsSchemeView): Int =
    view.view.grid.mapIndexed { rowIndex, row ->
        row.mapIndexed { sourceColumn, cell ->
            cell is GridCell.Data && resolvedFormValue(view, rowIndex, sourceColumn) != null
        }
            .count { it }
    }.sum()

@Composable
private fun FormsGridCell(cell: GridCell, value: String?) {
    val text = when (cell) {
        is GridCell.RowHeader -> cell.label
        is GridCell.ColHeader -> cell.label
        is GridCell.Data -> value ?: "?"
        is GridCell.Empty -> ""
    }
    val backgroundColor = when (cell) {
        is GridCell.RowHeader,
        is GridCell.ColHeader -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)

        is GridCell.Data -> MaterialTheme.colorScheme.surface
        is GridCell.Empty -> MaterialTheme.colorScheme.surface
    }
    val textColor = when {
        cell is GridCell.Data && value == null -> MaterialTheme.colorScheme.error
        cell is GridCell.Empty -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0f)
        else -> MaterialTheme.colorScheme.onSurface
    }
    val fontStyle = if (cell is GridCell.Data && value?.endsWith("?") == true) {
        FontStyle.Italic
    } else {
        FontStyle.Normal
    }
    val contentAlignment = if (cell is GridCell.ColHeader || cell is GridCell.Data) {
        Alignment.Center
    } else {
        Alignment.CenterStart
    }
    val textAlign = if (cell is GridCell.ColHeader || cell is GridCell.Data) {
        TextAlign.Center
    } else {
        TextAlign.Start
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .defaultMinSize(minHeight = 36.dp),
        contentAlignment = contentAlignment
    ) {
        if (text.isNotEmpty()) {
            Text(
                text = text,
                textAlign = textAlign,
                style = MaterialTheme.typography.bodySmall.copy(fontStyle = fontStyle),
                color = textColor
            )
        }
    }
}

@Composable
private fun SpannedFormsGrid(
    formsView: FormsSchemeView,
    matrix: List<List<PlacedGridCell?>>,
    cellWidth: androidx.compose.ui.unit.Dp,
    minCellHeight: androidx.compose.ui.unit.Dp,
) {
    val rowCount = matrix.size
    if (rowCount == 0) return

    val anchors = buildList {
        matrix.forEachIndexed { rowIndex, row ->
            row.forEachIndexed { columnIndex, placed ->
                if (placed != null && placed.anchorRow == rowIndex && placed.anchorColumn == columnIndex) {
                    add(placed)
                }
            }
        }
    }

    Layout(
        content = {
            anchors.forEach { placed ->
                FormsGridCell(
                    cell = placed.cell,
                    value = resolvedFormValue(formsView, placed.sourceRow, placed.sourceColumn)
                )
            }
        }
    ) { measurables, _ ->
        if (anchors.isEmpty()) {
            return@Layout layout(0, 0) {}
        }

        val maxColumns = matrix.maxOfOrNull { it.size } ?: 0
        val colWidthPx = cellWidth.roundToPx()
        val minRowHeightPx = minCellHeight.roundToPx()
        val rowHeights = IntArray(rowCount) { minRowHeightPx }
        val preferredHeights = IntArray(measurables.size)

        measurables.forEachIndexed { index, measurable ->
            val placed = anchors[index]
            val widthPx = colWidthPx * placed.cell.colspan
            val preferredHeight = measurable.maxIntrinsicHeight(widthPx)
            preferredHeights[index] = max(minRowHeightPx, preferredHeight)
        }

        var changed = true
        while (changed) {
            changed = false
            anchors.forEachIndexed { index, placed ->
                val startRow = placed.anchorRow
                val endRowExclusive = min(rowCount, startRow + placed.cell.rowspan)
                if (startRow >= endRowExclusive) return@forEachIndexed

                val spanHeight = (startRow until endRowExclusive).sumOf { rowHeights[it] }
                val required = preferredHeights[index]
                if (required > spanHeight) {
                    changed = true
                    val deficit = required - spanHeight
                    val rowsSpanned = endRowExclusive - startRow
                    val base = deficit / rowsSpanned
                    var remainder = deficit % rowsSpanned
                    for (row in startRow until endRowExclusive) {
                        rowHeights[row] += base
                        if (remainder > 0) {
                            rowHeights[row] += 1
                            remainder--
                        }
                    }
                }
            }
        }

        val rowOffsets = IntArray(rowCount + 1)
        for (row in 0 until rowCount) {
            rowOffsets[row + 1] = rowOffsets[row] + rowHeights[row]
        }

        val placeables = measurables.mapIndexed { index, measurable ->
            val placed = anchors[index]
            val widthPx = colWidthPx * placed.cell.colspan
            val endRowExclusive = min(rowCount, placed.anchorRow + placed.cell.rowspan)
            val heightPx = rowOffsets[endRowExclusive] - rowOffsets[placed.anchorRow]
            measurable.measure(Constraints.fixed(widthPx, heightPx))
        }

        val tableWidthPx = colWidthPx * maxColumns
        val tableHeightPx = rowOffsets[rowCount]

        layout(tableWidthPx, tableHeightPx) {
            placeables.forEachIndexed { index, placeable ->
                val placed = anchors[index]
                val x = placed.anchorColumn * colWidthPx
                val y = rowOffsets[placed.anchorRow]
                placeable.placeRelative(x, y)
            }
        }
    }
}

@Composable
private fun FormsModeSelector(
    formsViews: List<FormsSchemeView>,
    selectedViewId: String,
    onFormsViewSelect: (String) -> Unit
) {
    val scrollState = rememberScrollState()
    Row(
        modifier = Modifier.horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        formsViews.forEach { formsView ->
            val viewId = formsView.view.viewId
            FilterChip(
                selected = selectedViewId == viewId,
                onClick = { onFormsViewSelect(viewId) },
                label = {
                    Text(
                        text = formsView.view.description.ifBlank { viewId },
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            )
        }
    }
}

@Composable
private fun FormsGrid(formsView: FormsSchemeView) {
    val matrix = remember(formsView) { buildPlacedGrid(formsView) }
    val maxColumns = matrix.maxOfOrNull { it.size } ?: 0
    if (maxColumns == 0) return

    val cellWidth = 108.dp
    val minCellHeight = 36.dp
    val tableWidth = cellWidth * maxColumns
    val tableShape = RoundedCornerShape(10.dp)
    val horizontalScrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(horizontalScrollState)
    ) {
        Column(
            modifier = Modifier
                .requiredWidth(tableWidth)
                .clip(tableShape)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, tableShape)
        ) {
            SpannedFormsGrid(
                formsView = formsView,
                matrix = matrix,
                cellWidth = cellWidth,
                minCellHeight = minCellHeight
            )
        }
    }
}

@Composable
private fun GrammarSection(
    formsViews: List<FormsSchemeView>,
    selectedViewId: String?,
    expanded: Boolean,
    onToggle: () -> Unit,
    onFormsViewSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedFormsView = formsViews.firstOrNull { it.view.viewId == selectedViewId } ?: formsViews.first()
    val selectedViewIdResolved = selectedFormsView.view.viewId
    val formCount = resolvedDataCount(selectedFormsView)
    val shape = RoundedCornerShape(14.dp)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(
            modifier = Modifier
                .clip(shape)
                .clickable(onClick = onToggle),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            shape = shape
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.LibraryBooks,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                Text(
                    text = "$formCount form${pluralEnding(formCount)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    imageVector = if (expanded) ExpandLessVector else ExpandMoreVector,
                    contentDescription = if (expanded) "Hide forms" else "Show forms",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier.padding(start = 6.dp, top = 2.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (formsViews.size > 1) {
                    FormsModeSelector(
                        formsViews = formsViews,
                        selectedViewId = selectedViewIdResolved,
                        onFormsViewSelect = onFormsViewSelect
                    )
                }
                FormsGrid(selectedFormsView)
            }
        }
    }
}

@Composable
internal fun EntryCard(
    entry: LanguageCardPosEntry,
    entryState: EntryUiState,
    cardLoading: Boolean = false,
    cardError: String? = null,
    translationLoading: Boolean = false,
    translationError: String? = null,
    onEntryToggle: () -> Unit,
    onFormsToggle: () -> Unit,
    onFormsViewSelect: (String) -> Unit,
    onSenseToggle: (String) -> Unit,
    onSensePositioned: (String, Float) -> Unit = { _, _ -> },
    onSenseFavoriteToggle: (String) -> Unit = {},
    relatedWords: Map<String, RelatedWord> = emptyMap(),
    onWordClick: (String) -> Unit = {},
    lemma: String,
    favoriteLemmas: Set<String> = emptySet()
) {
    val expanded = entryState.expanded && !cardLoading && cardError == null
    Column(modifier = Modifier.fillMaxWidth()) {
        // POS Header - kept with its own padding
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.extraLarge)
                .clickable(onClick = onEntryToggle)
                .padding(horizontal = 8.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PartOfSpeechIndicator(
                partOfSpeech = entry.pos.name,
                meaningCount = if (!cardLoading && cardError == null) entry.senses.size else null,
                cardLoading = cardLoading,
                cardError = cardError
            )
            if (!cardLoading && cardError == null) {
                Spacer(modifier = Modifier.weight(1f))

                Icon(
                    imageVector = if (expanded) ExpandLessVector else ExpandMoreVector,
                    contentDescription = if (expanded) {
                        "Collapse ${entry.pos} entry"
                    } else {
                        "Expand ${entry.pos} entry"
                    },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Grammar section - indented under POS header
                if (entry.formsViews.isNotEmpty()) {
                    GrammarSection(
                        formsViews = entry.formsViews,
                        selectedViewId = entryState.selectedFormsViewId,
                        expanded = entryState.formsExpanded,
                        onToggle = onFormsToggle,
                        onFormsViewSelect = onFormsViewSelect,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }

                // Senses - edge-to-edge (no horizontal padding)
                entry.senses.forEach { sense ->
                    val senseState = entryState.senses.find { it.senseId == sense.senseId }
                        ?: throw IllegalStateException("Sense state not found for sense ${sense.senseId}")
                    SenseCard(
                        data = SenseCardData(
                            lemma = lemma,
                            showLemma = false,
                            senseId = sense.senseId,
                            sense = sense,
                            pos = entry.pos,
                            translationLoading = translationLoading || senseState.translationLoading,
                            translationError = translationError ?: senseState.translationError
                        ),
                        state = senseState,
                        onToggle = { onSenseToggle(sense.senseId) },
                        onPositioned = onSensePositioned,
                        onFavoriteToggle = { onSenseFavoriteToggle(sense.senseId) },
                        relatedWords = relatedWords,
                        onWordClick = onWordClick,
                        favoriteLemmas = favoriteLemmas
                    )
                }
            }
        }
    }
}

fun pluralEnding(count: Int): String = if (count == 1) "" else "s"

fun pluralEnding(someList: List<*>): String = pluralEnding(someList.size)
