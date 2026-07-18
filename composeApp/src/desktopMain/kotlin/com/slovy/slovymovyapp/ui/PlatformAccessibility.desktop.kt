package com.slovy.slovymovyapp.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

// Desktop exposes no reduced-motion preference; animations always play.
actual class PlatformAccessibility actual constructor(androidContext: Any?) {
    actual fun isReduceMotionEnabled(): Boolean = false
}

@Composable
actual fun rememberPlatformAccessibility(): PlatformAccessibility =
    remember { PlatformAccessibility(null) }
