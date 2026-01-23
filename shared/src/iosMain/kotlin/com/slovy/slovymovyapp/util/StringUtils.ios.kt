package com.slovy.slovymovyapp.util

import platform.Foundation.NSString
import platform.Foundation.decomposedStringWithCanonicalMapping

actual fun String.lowercaseInvariant(): String = lowercase()

actual fun normalizeAndStripAccents(s: String): String {
    // Use Foundation's NFD normalization (decomposedStringWithCanonicalMapping)
    @Suppress("CAST_NEVER_SUCCEEDS")
    val nsString = s as NSString
    val normalized = nsString.decomposedStringWithCanonicalMapping
    // Remove combining diacritical marks (U+0300 to U+036F)
    return normalized.replace(Regex("[\\u0300-\\u036F]"), "")
}
