package com.slovy.slovymovyapp.data


enum class Language(val code: String, val selfName: String) {
    ENGLISH("en", "English"),
    RUSSIAN("ru", "Русский"),
    DUTCH("nl", "Nederlands"),
    POLISH("pl", "Polski");

    companion object {
        fun fromCode(code: String): Language {
            return entries.find { it.code == code }
                ?: throw IllegalArgumentException("Unknown language code: $code")
        }

        fun fromCodeOrNull(code: String): Language? {
            return entries.find { it.code == code }
        }
    }
}
