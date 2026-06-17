package com.slovy.slovymovyapp.data.recovery

import com.slovy.slovymovyapp.data.Language

/**
 * A sense whose lemma should be ensured present (with translations) in the local DB by lemma
 * recovery. Implemented directly by the domain types that can be recovered — a favorite and a
 * curated word-list sense — so recovery can process both in one combined, deduped pass without a
 * separate DTO. All fetch/recovery paths are keyed by [language] + [lemma].
 */
interface RecoverableSense {
    val language: Language
    val lemma: String
    val senseId: String
}
