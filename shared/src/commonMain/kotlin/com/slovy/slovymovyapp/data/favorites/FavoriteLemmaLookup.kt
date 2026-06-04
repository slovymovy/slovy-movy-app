package com.slovy.slovymovyapp.data.favorites

class FavoriteLemmaLookup private constructor(private val normalizedLemmas: Set<String>) {

    fun contains(lemma: String): Boolean = normalizedKey(lemma) in normalizedLemmas

    companion object {
        private val Empty = FavoriteLemmaLookup(emptySet())

        fun empty(): FavoriteLemmaLookup = Empty

        fun fromLemmas(lemmas: Set<String>): FavoriteLemmaLookup {
            if (lemmas.isEmpty()) return Empty
            return FavoriteLemmaLookup(lemmas.mapTo(HashSet()) { normalizedKey(it) })
        }

        fun normalizedKey(lemma: String): String = lemma.trim().lowercase()
    }
}
