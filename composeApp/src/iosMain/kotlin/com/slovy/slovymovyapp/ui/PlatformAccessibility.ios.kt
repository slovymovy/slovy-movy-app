package com.slovy.slovymovyapp.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled

actual class PlatformAccessibility actual constructor(androidContext: Any?) {
    actual fun isReduceMotionEnabled(): Boolean = UIAccessibilityIsReduceMotionEnabled()
}

@Composable
actual fun rememberPlatformAccessibility(): PlatformAccessibility =
    remember { PlatformAccessibility(null) }
