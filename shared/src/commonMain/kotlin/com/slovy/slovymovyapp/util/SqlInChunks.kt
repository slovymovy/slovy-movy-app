package com.slovy.slovymovyapp.util

/**
 * Maximum number of values passed into a single SQL `IN ?` expression.
 *
 * SQLite on Android 11 and older caps bound variables per statement at 999
 * (SQLITE_MAX_VARIABLE_NUMBER; raised to 32766 only in SQLite 3.32 / Android 12+).
 * Kept well below the cap to leave room for the query's other bound parameters.
 */
const val MAX_SQL_IN_VARIABLES = 500

/**
 * Runs an `IN ?` query in chunks so the bound-variable count stays under the SQLite limit,
 * concatenating the per-chunk results.
 *
 * Duplicates in [items] are dropped — they cannot change what an `IN` match returns.
 * Each distinct item lands in exactly one chunk, so all rows for a given item stay together
 * and keep the query's ordering; only the ordering *across* items is not preserved. Callers
 * that need a global `ORDER BY` must re-sort the combined result themselves.
 */
fun <T, R> queryInChunks(items: Collection<T>, query: (List<T>) -> List<R>): List<R> =
    items.distinct().chunked(MAX_SQL_IN_VARIABLES).flatMap { chunk -> query(chunk) }
