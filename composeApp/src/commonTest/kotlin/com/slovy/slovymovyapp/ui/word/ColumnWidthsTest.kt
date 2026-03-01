package com.slovy.slovymovyapp.ui.word

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ColumnWidthsTest {

    private fun col(anchorColumn: Int, colspan: Int = 1, width: Int) =
        ColumnWidthInput(anchorColumn = anchorColumn, colspan = colspan, intrinsicWidthPx = width)

    // ── Pass 1: single-column cells ──────────────────────────────────────────

    @Test
    fun singleColumn_usesWidestCell() {
        val result = computeColumnWidths(
            maxColumns = 2, minColWidthPx = 10, maxColWidthPx = 300,
            inputs = listOf(col(0, width = 80), col(0, width = 50), col(1, width = 120))
        )
        assertEquals(80, result[0], "col 0 should be max of its cells")
        assertEquals(120, result[1], "col 1 should be max of its cells")
    }

    @Test
    fun singleColumn_respectsMin() {
        val result = computeColumnWidths(
            maxColumns = 2, minColWidthPx = 40, maxColWidthPx = 300,
            inputs = listOf(col(0, width = 10), col(1, width = 5))
        )
        assertEquals(40, result[0], "col 0 below min should be lifted to min")
        assertEquals(40, result[1], "col 1 below min should be lifted to min")
    }

    @Test
    fun singleColumn_cappedAtMax() {
        val result = computeColumnWidths(
            maxColumns = 1, minColWidthPx = 10, maxColWidthPx = 100,
            inputs = listOf(col(0, width = 200))
        )
        assertEquals(100, result[0], "col 0 should be capped at max")
    }

    // ── Pass 2: multi-column span redistribution ─────────────────────────────

    @Test
    fun spanDeficit_evenlyDistributedWhenAllHaveRoom() {
        // Span over 2 fresh columns, each at min=10, intrinsic=100 → each gets 50.
        val result = computeColumnWidths(
            maxColumns = 2, minColWidthPx = 10, maxColWidthPx = 300,
            inputs = listOf(col(0, colspan = 2, width = 100))
        )
        assertEquals(50, result[0])
        assertEquals(50, result[1])
    }

    @Test
    fun spanDeficit_noOpWhenColumnsAlreadySufficient() {
        // Single-column cells already cover the span's intrinsic width — span pass is a no-op.
        val result = computeColumnWidths(
            maxColumns = 2, minColWidthPx = 10, maxColWidthPx = 300,
            inputs = listOf(
                col(0, width = 80),
                col(1, width = 80),
                col(0, colspan = 2, width = 100), // 80+80 = 160 >= 100
            )
        )
        assertEquals(80, result[0])
        assertEquals(80, result[1])
    }

    @Test
    fun spanDeficit_redistributedToUncappedColumnsWhenOneCapped() {
        // col0=90 (10 below max), col1=50. Span needs 200 total (currently 140, deficit=60).
        // Round 1: share=30 each.
        //   col0: 90+30=120 > max=100 → col0=100, overflow=20
        //   col1: 50+30=80 → col1=80
        // Round 2: remaining=20 redistributed to col1 only.
        //   col1: 80+20=100 → col1=100
        // Result: col0=100, col1=100 (total=200 == intrinsic).
        val result = computeColumnWidths(
            maxColumns = 2, minColWidthPx = 10, maxColWidthPx = 100,
            inputs = listOf(
                col(0, width = 90),
                col(1, width = 50),
                col(0, colspan = 2, width = 200),
            )
        )
        assertEquals(100, result[0], "col 0 should reach max")
        assertEquals(100, result[1], "col 1 should absorb redistributed overflow from col 0")
        assertEquals(200, result[0] + result[1], "total must equal span intrinsic width")
    }

    @Test
    fun spanDeficit_redistributedAcrossMultipleCappedColumns() {
        // 3 columns: col0=90, col1=90, col2=50. Span needs 300 (currently 230, deficit=70).
        // Iterative redistribution fills col0 and col1 to 100 each and gives col2 the rest.
        val result = computeColumnWidths(
            maxColumns = 3, minColWidthPx = 10, maxColWidthPx = 100,
            inputs = listOf(
                col(0, width = 90),
                col(1, width = 90),
                col(2, width = 50),
                col(0, colspan = 3, width = 300),
            )
        )
        assertEquals(100, result[0])
        assertEquals(100, result[1])
        assertEquals(100, result[2])
        assertEquals(300, result.sum())
    }

    @Test
    fun spanDeficit_overlappingSpans_laterSpanSeesEarlierResult() {
        // Span 0-1 (intrinsic=100) is processed first: col0 and col1 each grow to 50.
        // Span 1-2 (intrinsic=200) then sees col1=50 (already widened), col2=10 (min).
        //   deficit=140; round1: col1→120 capped to 100 (overflow=20), col2→80
        //   round2: remaining=20 → col2→100.
        // Final: col0=50, col1=100, col2=100.
        // This test documents the order-dependent behavior so that future refactors
        // don't silently change it.
        val result = computeColumnWidths(
            maxColumns = 3, minColWidthPx = 10, maxColWidthPx = 100,
            inputs = listOf(
                col(0, colspan = 2, width = 100),
                col(1, colspan = 2, width = 200),
            )
        )
        assertEquals(50,  result[0], "col 0 set only by span 0-1")
        assertEquals(100, result[1], "col 1 widened first by span 0-1, then by span 1-2")
        assertEquals(100, result[2], "col 2 widened by span 1-2 with redistributed overflow")
    }

    @Test
    fun spanDeficit_droppedOnlyWhenAllColumnsAtMax() {
        // All columns already at max — deficit cannot be absorbed anywhere.
        val result = computeColumnWidths(
            maxColumns = 2, minColWidthPx = 10, maxColWidthPx = 100,
            inputs = listOf(
                col(0, width = 100),
                col(1, width = 100),
                col(0, colspan = 2, width = 300),
            )
        )
        assertTrue(result[0] <= 100, "col 0 must not exceed max")
        assertTrue(result[1] <= 100, "col 1 must not exceed max")
    }
}
