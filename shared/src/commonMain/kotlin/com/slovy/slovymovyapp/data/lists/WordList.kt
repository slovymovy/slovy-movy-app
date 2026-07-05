package com.slovy.slovymovyapp.data.lists

import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.recovery.RecoverableSense

/**
 * A curated sense paired with the lemma it belongs to. Implements [RecoverableSense] so a sense
 * missing from the local dictionary can be fed straight to lemma recovery; [language] matches the
 * owning list's language.
 */
data class WordListSense(
    override val senseId: String,
    override val lemma: String,
    override val language: Language,
) : RecoverableSense

/**
 * A curated word list as stored in the app DB. [title], [subtitle], and [labels] are
 * keyed by UI locale code (e.g. "en", "ru"); the UI resolves the active locale with an
 * "en" fallback. [senses] preserves the server-provided order; each entry carries the
 * lemma so a sense missing from the local dictionary can still be fetched.
 */
data class WordList(
    val id: String,
    val title: Map<String, String>,
    val subtitle: Map<String, String>,
    val labels: Map<String, List<String>>,
    val senses: List<WordListSense>,
    val iconSvg: String?,
) {
    val senseIds: List<String> get() = senses.map { it.senseId }
}
