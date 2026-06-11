package com.slovy.slovymovyapp.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import com.slovy.slovymovyapp.data.remote.LanguageCardResponseSense
import com.slovy.slovymovyapp.i18n.UiText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** Max senses loaded per prefetch batch. */
private const val PREFETCH_LIMIT = 16

/** Items below the fold included when prefetching on scroll. */
private const val PREFETCH_LOOKAHEAD = 5

/**
 * A list row whose full sense (with translations) is loaded lazily. Implemented by the
 * favorites and list-detail item models so [SensePrefetcher] can decide what still needs
 * loading.
 */
interface PrefetchableSenseItem {
    val sense: LanguageCardResponseSense?
    val loading: Boolean
    val error: UiText?
}

/**
 * Shared sense-prefetch policy for screens that render lazily loaded sense rows
 * (favorites, list detail): load the first screenful up front, then the visible range as
 * the user scrolls, at most [PREFETCH_LIMIT] senses per batch. Items already loaded,
 * loading, or failed are skipped; failed rows surface a retry instead of being re-fetched.
 */
class SensePrefetcher<T : PrefetchableSenseItem>(
    private val scope: CoroutineScope,
    private val load: suspend (T) -> Unit,
) {
    /** Prefetches the first screenful so rows render without expanding. */
    fun prefetchHead(items: List<T>) {
        prefetch(items.take(PREFETCH_LIMIT))
    }

    /** Prefetches the [range] slice as the user scrolls; out-of-bounds ends are clamped. */
    fun prefetchVisibleRange(items: List<T>, range: IntRange) {
        if (items.isEmpty() || range.isEmpty()) return
        val safeRange = range.first.coerceAtLeast(0)..minOf(range.last, items.lastIndex)
        if (safeRange.isEmpty()) return
        prefetch(items.slice(safeRange).take(PREFETCH_LIMIT))
    }

    private fun prefetch(items: List<T>) {
        val toLoad = items.filter { it.sense == null && !it.loading && it.error == null }
        toLoad.forEach { item ->
            scope.launch { load(item) }
        }
    }
}

/**
 * Emits the visible item range (plus [PREFETCH_LOOKAHEAD] rows below the fold) whenever it
 * changes while scrolling. [headerItemCount] is the number of leading non-content lazy-list
 * items (e.g. a list header) subtracted from the indices. The emitted range may overshoot
 * the content; [SensePrefetcher.prefetchVisibleRange] clamps it.
 */
@Composable
fun <T> PrefetchVisibleRangeEffect(
    items: List<T>,
    scrollState: LazyListState,
    headerItemCount: Int,
    onPrefetchVisible: (List<T>, IntRange) -> Unit,
) {
    LaunchedEffect(items, scrollState, headerItemCount) {
        snapshotFlow { scrollState.layoutInfo.visibleItemsInfo.map { it.index } }
            .distinctUntilChanged()
            .collectLatest { indices ->
                if (indices.isEmpty()) return@collectLatest
                val first = (indices.min() - headerItemCount).coerceAtLeast(0)
                val last = indices.max() - headerItemCount + PREFETCH_LOOKAHEAD
                onPrefetchVisible(items, first..last)
            }
    }
}
