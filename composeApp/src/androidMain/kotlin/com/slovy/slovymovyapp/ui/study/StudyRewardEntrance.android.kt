package com.slovy.slovymovyapp.ui.study

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

// Reduced motion ~= the system "Remove animations" toggle, which zeroes the global animation scales.
@Composable
actual fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        val scale = Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.TRANSITION_ANIMATION_SCALE,
            1f,
        )
        scale == 0f
    }
}