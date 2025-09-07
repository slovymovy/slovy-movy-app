package com.slovy.slovymovyapp.data.dictionary

enum class NameType(val i: Int) {
    NO(0),
    PERSON_NAME(1),
    PLACE_NAME(2),
    GEOGRAPHICAL_FEATURE(3),
    ORGANIZATION_NAME(4),
    FICTIONAL_NAME(5),
    HISTORICAL_NAME(6),
    EVENT_NAME(7),
    WORK_OF_ART_NAME(8),
    OTHER(9);

    companion object {
        fun from(databaseValue: Long): NameType {
            return entries.first { it.i.toLong() == databaseValue }
        }
    }
}