package com.slovy.slovymovyapp.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * Read access to the platform's accessibility preferences (`androidContext` is only used on
 * Android).
 */
expect class PlatformAccessibility(androidContext: Any?) {
    /** Whether the user asked the OS to reduce or disable animations. */
    fun isReduceMotionEnabled(): Boolean
}

/** The [PlatformAccessibility] backed by the current composition's platform context. */
@Composable
expect fun rememberPlatformAccessibility(): PlatformAccessibility

/** Whether the platform requests reduced motion; read once when entering composition. */
@Composable
fun rememberReduceMotion(): Boolean {
    val accessibility = rememberPlatformAccessibility()
    return remember(accessibility) { accessibility.isReduceMotionEnabled() }
}
