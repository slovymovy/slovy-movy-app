package com.slovy.slovymovyapp.data.dictionary

enum class SenseFrequency(val i: Int) {
    HIGH(1),
    MIDDLE(2),
    LOW(3),
    VERY_LOW(4);

    companion object {
        fun from(databaseValue: Long): SenseFrequency {
            return entries.first { it.i.toLong() == databaseValue }
        }
    }
}