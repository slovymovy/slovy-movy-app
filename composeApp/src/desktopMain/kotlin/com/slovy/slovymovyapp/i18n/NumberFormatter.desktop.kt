package com.slovy.slovymovyapp.i18n

import java.text.NumberFormat
import java.util.Locale

actual object NumberFormatter {
    actual fun formatInteger(value: Int, languageTag: String): String {
        val locale = Locale.forLanguageTag(languageTag)
        return NumberFormat.getIntegerInstance(locale).format(value)
    }
}
