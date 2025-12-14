package com.slovy.slovymovyapp.util

import java.text.Normalizer

actual fun normalizeAndStripAccents(s: String): String {
    // NFD decomposes accented characters into base + combining marks
    val normalized = Normalizer.normalize(s, Normalizer.Form.NFD)
    // Remove combining diacritical marks (U+0300 to U+036F)
    return normalized.replace(Regex("[\\u0300-\\u036F]"), "")
}
