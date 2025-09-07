package com.slovy.slovymovyapp.data.dictionary

enum class LearnerLevel(val i: Int) {
    A1(1),
    A2(2),
    B1(3),
    B2(4),
    C1(5),
    C2(6);

    companion object {
        fun from(databaseValue: Long): LearnerLevel {
            return entries.first { it.i.toLong() == databaseValue }
        }
    }
}