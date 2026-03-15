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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.slovy.slovymovyapp.data.forms.GridCell
import com.slovy.slovymovyapp.data.remote.FormsSchemeView
import com.slovy.slovymovyapp.data.remote.LanguageCardPosEntry
import com.slovy.slovymovyapp.data.remote.LanguageCardResponseSense
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

@Composable
private fun FormsGridCell(cell: GridCell, value: String?) {
    val text = when (cell) {
        is GridCell.RowHeader -> cell.label
        is GridCell.ColHeader -> cell.label
        is GridCell.Data -> value ?: "?"
        is GridCell.Empty -> ""
    }
    val isHeader = cell is GridCell.RowHeader || cell is GridCell.ColHeader
    val dividerColor = MaterialTheme.colorScheme.outlineVariant
    val headerBackground = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    val textColor = when {
        cell is GridCell.Data && value == null -> MaterialTheme.colorScheme.onSurface
        cell is GridCell.Empty -> Color.Transparent
        isHeader -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }
    val textStyle = when {
        isHeader -> MaterialTheme.typography.labelSmall
        else -> MaterialTheme.typography.bodySmall.copy(
            fontStyle = if (cell is GridCell.Data && value?.endsWith("?") == true) FontStyle.Italic else FontStyle.Normal
        )
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
            .background(if (isHeader) headerBackground else Color.Transparent)
            .drawBehind {
                val stroke = 0.5.dp.toPx()
                val color = dividerColor
                drawLine(color, Offset(0f, size.height), Offset(size.width, size.height), stroke)
                drawLine(color, Offset(size.width, 0f), Offset(size.width, size.height), stroke)
            }
            .padding(horizontal = 8.dp, vertical = 5.dp),
        contentAlignment = contentAlignment
    ) {
        if (text.isNotEmpty()) {
            Text(
                text = text,
                textAlign = textAlign,
                style = textStyle,
                color = textColor
            )
        }
    }
}

internal data class ColumnWidthInput(
    val anchorColumn: Int,
    val colspan: Int,
    val intrinsicWidthPx: Int,
)

/**
 * Computes per-column pixel widths from measured cell intrinsic widths.
 *
 * Pass 1 – single-column cells: each column is at least as wide as its widest cell.
 * Pass 2 – multi-column spans: spans are processed in canonical order (shorter before
 *   longer, ties broken left-to-right by anchorColumn) so results are independent of
 *   declaration order in the grid. Within each span, deficit is distributed evenly
 *   across columns with room; when a column hits [maxColWidthPx] its overflow is
 *   redistributed to remaining uncapped columns rather than being dropped.
 */
internal fun computeColumnWidths(
    maxColumns: Int,
    minColWidthPx: Int,
    maxColWidthPx: Int,
    inputs: List<ColumnWidthInput>,
): IntArray {
    val colWidths = IntArray(maxColumns) { minColWidthPx }

    // Pass 1: single-column cells
    for (input in inputs) {
        if (input.colspan == 1) {
            colWidths[input.anchorColumn] = minOf(
                maxColWidthPx,
                maxOf(colWidths[input.anchorColumn], input.intrinsicWidthPx)
            )
        }
    }

    // Pass 2: multi-column spans — canonical order removes declaration-order dependence.
    // Shorter spans run before longer ones; equal-length spans run left-to-right.
    val spans = inputs.filter { it.colspan > 1 }
        .sortedWith(compareBy({ it.colspan }, { it.anchorColumn }))
    for (input in spans) {
            val spanCols = input.anchorColumn until input.anchorColumn + input.colspan
            var remaining = input.intrinsicWidthPx - spanCols.sumOf { colWidths[it] }
            if (remaining > 0) {
                val growable = spanCols.toMutableList()
                while (remaining > 0 && growable.isNotEmpty()) {
                    val share = remaining / growable.size
                    var leftover = remaining % growable.size
                    var overflow = 0
                    val iter = growable.iterator()
                    while (iter.hasNext()) {
                        val col = iter.next()
                        val add = share + if (leftover > 0) { leftover--; 1 } else 0
                        val proposed = colWidths[col] + add
                        if (proposed >= maxColWidthPx) {
                            overflow += proposed - maxColWidthPx
                            colWidths[col] = maxColWidthPx
                            iter.remove()
                        } else {
                            colWidths[col] = proposed
                        }
                    }
                    remaining = overflow
                }
            }
    }

    return colWidths
}

/** Non-observable holder so that writing from the layout lambda doesn't trigger recomposition. */
private class ColWidthsCache {
    var stamp: Any? = null
    var widths: IntArray? = null
}

@Composable
private fun SpannedFormsGrid(
    formsView: FormsSchemeView,
    matrix: List<List<PlacedGridCell?>>,
    minColWidth: androidx.compose.ui.unit.Dp,
    maxColWidth: androidx.compose.ui.unit.Dp,
    minCellHeight: androidx.compose.ui.unit.Dp,
) {
    val rowCount = matrix.size
    if (rowCount == 0) return
    val maxColumns = matrix.maxOfOrNull { it.size } ?: 0

    // Cache column widths so the layout hot-path skips intrinsic measurement on every pass.
    // A new stamp object is allocated only when inputs that affect column widths change.
    // LocalDensity covers system font-scale changes; typography styles cover any runtime
    // theme or ProvideTextStyle changes that alter header/data text metrics.
    val colWidthsCache = remember { ColWidthsCache() }
    val headerStyle = MaterialTheme.typography.labelSmall
    val dataStyle = MaterialTheme.typography.bodySmall
    val colWidthsStamp = remember(
        formsView, minColWidth, maxColWidth, LocalDensity.current, headerStyle, dataStyle
    ) { Any() }

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

        // Column widths are derived from intrinsic text measurements and cached: recomputed only
        // when formsView/bounds/density change, not on every layout pass during animation.
        val colWidths = if (colWidthsCache.stamp === colWidthsStamp) {
            colWidthsCache.widths!!
        } else {
            // Header cells: maxIntrinsicWidth so labels fit on one line.
            // Data cells: minIntrinsicWidth so individual words never break, but multi-word
            // forms (e.g. "zou uitgewrongen hebben") can wrap at spaces.
            val inputs = anchors.mapIndexed { index, placed ->
                val intrinsic = if (placed.cell is GridCell.Data) {
                    measurables[index].minIntrinsicWidth(Constraints.Infinity)
                } else {
                    measurables[index].maxIntrinsicWidth(Constraints.Infinity)
                }
                ColumnWidthInput(placed.anchorColumn, placed.cell.colspan, intrinsic)
            }
            computeColumnWidths(maxColumns, minColWidth.roundToPx(), maxColWidth.roundToPx(), inputs)
                .also { colWidthsCache.stamp = colWidthsStamp; colWidthsCache.widths = it }
        }

        val colOffsets = IntArray(maxColumns + 1)
        for (col in 0 until maxColumns) colOffsets[col + 1] = colOffsets[col] + colWidths[col]

        val minRowHeightPx = minCellHeight.roundToPx()
        val rowHeights = IntArray(rowCount) { minRowHeightPx }
        val preferredHeights = IntArray(measurables.size)

        measurables.forEachIndexed { index, measurable ->
            val placed = anchors[index]
            val widthPx = (placed.anchorColumn until placed.anchorColumn + placed.cell.colspan)
                .sumOf { colWidths[it] }
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
            val widthPx = (placed.anchorColumn until placed.anchorColumn + placed.cell.colspan)
                .sumOf { colWidths[it] }
            val endRowExclusive = min(rowCount, placed.anchorRow + placed.cell.rowspan)
            val heightPx = rowOffsets[endRowExclusive] - rowOffsets[placed.anchorRow]
            measurable.measure(Constraints.fixed(widthPx, heightPx))
        }

        layout(colOffsets[maxColumns], rowOffsets[rowCount]) {
            placeables.forEachIndexed { index, placeable ->
                val placed = anchors[index]
                placeable.placeRelative(colOffsets[placed.anchorColumn], rowOffsets[placed.anchorRow])
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
    if (matrix.maxOfOrNull { it.size } == 0) return

    val tableShape = RoundedCornerShape(4.dp)
    val dividerColor = MaterialTheme.colorScheme.outlineVariant
    val horizontalScrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(horizontalScrollState)
    ) {
        Box(
            modifier = Modifier
                .clip(tableShape)
                .border(1.dp, dividerColor, tableShape)
        ) {
            SpannedFormsGrid(
                formsView = formsView,
                matrix = matrix,
                minColWidth = 16.dp,  // just the horizontal padding — content drives width
                maxColWidth = 240.dp, // guard against pathological single-line text
                minCellHeight = 28.dp,
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
                    text = "Grammar forms",
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
                val ambiguousTranslations = remember(entry.senses) {
                    val fingerprint = { sense: LanguageCardResponseSense ->
                        sense.translations.entries
                            .flatMap { (lang, translations) -> translations.map { "${lang}:${it.targetLangWord}" } }
                            .sorted()
                            .joinToString(",")
                    }
                    val counts = mutableMapOf<String, Int>()
                    entry.senses.forEach { sense ->
                        val fp = fingerprint(sense)
                        if (fp.isNotEmpty()) counts[fp] = (counts[fp] ?: 0) + 1
                    }
                    val duplicated = counts.filterValues { it > 1 }.keys
                    entry.senses
                        .filter { fingerprint(it) in duplicated }
                        .flatMap { sense ->
                            sense.translations.values.flatten()
                                .filter { !it.targetLangWord.contains(' ') }
                                .map { it.targetLangWord }
                        }
                        .toSet()
                }
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
                            translationError = translationError ?: senseState.translationError,
                            ambiguousTranslations = ambiguousTranslations
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
