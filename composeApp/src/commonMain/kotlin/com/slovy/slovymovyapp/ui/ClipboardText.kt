package com.slovy.slovymovyapp.ui

import androidx.compose.ui.platform.Clipboard

/**
 * Plain text on the platform clipboard. Compose's common `ClipEntry` exposes no text, so each
 * platform reads its own entry.
 */
expect object ClipboardText {
    /**
     * Whether [clipboard] holds text, asked without reading it, so it shows neither Android's
     * "pasted from clipboard" toast nor the iOS paste prompt.
     */
    suspend fun has(clipboard: Clipboard): Boolean

    /** The text on [clipboard], or null when it holds none or cannot be read. */
    suspend fun read(clipboard: Clipboard): String?
}
