package com.slovy.slovymovyapp.data.favorites

class FavoriteLemmaLookup private constructor(private val normalizedLemmas: Set<String>) {

    fun contains(lemma: String): Boolean = normalizedKey(lemma) in normalizedLemmas

    companion object {
        private val Empty = FavoriteLemmaLookup(emptySet())

        fun empty(): FavoriteLemmaLookup = Empty

        fun fromLemmas(lemmas: Iterable<String>): FavoriteLemmaLookup {
            val normalized = lemmas.mapTo(HashSet()) { normalizedKey(it) }
            if (normalized.isEmpty()) return Empty
            return FavoriteLemmaLookup(normalized)
        }

        fun normalizedKey(lemma: String): String = lemma.trim().lowercase()
    }
}
