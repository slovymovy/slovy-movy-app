package com.slovy.slovymovyapp.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class SqlInChunksTest {

    @Test
    fun splits_into_chunks_below_the_variable_limit() {
        val items = (0 until MAX_SQL_IN_VARIABLES * 2 + 1).toList()
        val seenChunks = mutableListOf<List<Int>>()

        val result = queryInChunks(items) { chunk ->
            seenChunks.add(chunk)
            chunk
        }

        assertEquals(3, seenChunks.size, "Expected two full chunks plus a remainder chunk")
        for (chunk in seenChunks) {
            assertTrue(
                chunk.size <= MAX_SQL_IN_VARIABLES,
                "Chunk of size ${chunk.size} exceeds the $MAX_SQL_IN_VARIABLES variable limit"
            )
        }
        assertEquals(items, result, "Concatenated chunk results should cover every item in order")
    }

    @Test
    fun deduplicates_items_before_querying() {
        val items = listOf("a", "b", "a", "c", "b")

        val result = queryInChunks(items) { chunk -> chunk }

        assertEquals(listOf("a", "b", "c"), result, "Duplicate IN values should be dropped before querying")
    }

    @Test
    fun empty_input_runs_no_queries() {
        val result: List<Int> = queryInChunks(emptyList<Int>()) { chunk ->
            fail("No query should run for empty input, got chunk of size ${chunk.size}")
        }

        assertEquals(emptyList(), result)
    }
}
