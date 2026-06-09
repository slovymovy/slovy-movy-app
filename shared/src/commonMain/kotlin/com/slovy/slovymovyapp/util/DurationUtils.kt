package com.slovy.slovymovyapp.util

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

fun Duration.roundUpToWholeMinutes(minimumMinutes: Long = 0): Long {
    if (this <= Duration.ZERO) return minimumMinutes
    val wholeMinutes = inWholeMinutes
    val roundedMinutes = if (this == wholeMinutes.minutes) {
        wholeMinutes
    } else {
        wholeMinutes + 1
    }
    return roundedMinutes.coerceAtLeast(minimumMinutes)
}
