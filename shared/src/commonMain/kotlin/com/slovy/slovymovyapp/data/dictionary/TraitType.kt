package com.slovy.slovymovyapp.data.dictionary

enum class TraitType(val i: Int) {
    DATED(1),
    COLLOQUIAL(2),
    OBSOLETE(3),
    DIALECTAL(4),
    ARCHAIC(5),
    REGIONAL(6),
    SLANG(7),
    FORM(8),
    SURNAME(9);

    companion object {
        fun from(databaseValue: Long): TraitType {
            return entries.first { it.i.toLong() == databaseValue }
        }
    }
}