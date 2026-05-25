package com.slovy.slovymovyapp.i18n

expect object NumberFormatter {
    fun formatInteger(value: Int, languageTag: String): String
}
