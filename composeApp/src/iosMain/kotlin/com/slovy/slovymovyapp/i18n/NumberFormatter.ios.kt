package com.slovy.slovymovyapp.i18n

import platform.Foundation.NSLocale
import platform.Foundation.NSNumber
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterDecimalStyle

actual object NumberFormatter {
    actual fun formatInteger(value: Int, languageTag: String): String {
        val formatter = NSNumberFormatter().apply {
            numberStyle = NSNumberFormatterDecimalStyle
            locale = NSLocale(localeIdentifier = languageTag)
        }
        return formatter.stringFromNumber(NSNumber(value)) ?: value.toString()
    }
}
