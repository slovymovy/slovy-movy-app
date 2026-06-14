package com.slovy.slovymovyapp.data.recovery

import com.slovy.slovymovyapp.data.Language

/**
 * A sense whose lemma should be ensured present (with translations) in the local DB by
 * lemma recovery. Both favorites and curated word-list senses map to this shape, so the
 * recovery can process them in one combined, deduped pass.
 */
data class RecoverableSense(
    val language: Language,
    val lemma: String,
    val senseId: String,
)
