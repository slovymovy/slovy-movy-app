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
    LANGUAGE_NAME(9),
    ETHNIC_GROUP_NAME(10),
    DEITY_OR_RELIGIOUS_NAME(11),
    RELIGION_OR_PHILOSOPHY_NAME(12),
    ASTRONOMICAL_NAME(13),
    TITLE_OR_HONORIFIC_NAME(14),
    BRAND_OR_PRODUCT_NAME(15),
    TECHNOLOGY_OR_SOFTWARE_NAME(16),
    GAME_OR_SPORT_NAME(17),
    IDEOLOGY_OR_MOVEMENT_NAME(18),
    MYTHOLOGICAL_OR_ASTROLOGICAL_ENTITY(19),
    DOCUMENT_OR_PROGRAM_NAME(20),
    OTHER(21);

    companion object {
        fun from(databaseValue: Long): NameType {
            return entries.first { it.i.toLong() == databaseValue }
        }
    }
}