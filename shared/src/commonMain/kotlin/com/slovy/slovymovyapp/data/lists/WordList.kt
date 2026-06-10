package com.slovy.slovymovyapp.data.lists

/**
 * A curated word list as stored in the app DB. [title], [subtitle], and [labels] are
 * keyed by UI locale code (e.g. "en", "ru"); the UI resolves the active locale with an
 * "en" fallback. [senseIds] preserves the server-provided order.
 */
data class WordList(
    val id: String,
    val title: Map<String, String>,
    val subtitle: Map<String, String>,
    val labels: Map<String, List<String>>,
    val senseIds: List<String>,
)
