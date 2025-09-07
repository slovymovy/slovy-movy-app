package com.slovy.slovymovyapp.data.dictionary

enum class DictionaryPos(val i: Long) {
    ARTICLE(1),
    NOUN(2),
    NAME(3),
    VERB(4),
    ADJECTIVE(5),
    ADVERB(6),
    PRONOUN(7),
    PREPOSITION(8),
    CONJUNCTION(9),
    INTERJECTION(10),
    DETERMINER(11),
    NUMERAL(12);

    companion object {
        fun from(databaseValue: Long): DictionaryPos {
            return entries.first { it.i == databaseValue }
        }
    }
}