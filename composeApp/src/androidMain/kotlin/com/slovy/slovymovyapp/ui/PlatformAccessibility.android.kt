package com.slovy.slovymovyapp.ui

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

actual class PlatformAccessibility actual constructor(androidContext: Any?) {
    private val context = androidContext as Context

    // Reduced motion ~= the system "Remove animations" toggle, which zeroes the global animation scales.
    actual fun isReduceMotionEnabled(): Boolean {
        val scale = Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.TRANSITION_ANIMATION_SCALE,
            1f,
        )
        return scale == 0f
    }
}

@Composable
actual fun rememberPlatformAccessibility(): PlatformAccessibility {
    val context = LocalContext.current
    return remember(context) { PlatformAccessibility(context) }
}
