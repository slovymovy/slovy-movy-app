package com.slovy.slovymovyapp.ui

import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.nativeClipboardManager

actual object ClipboardText {
    actual suspend fun has(clipboard: Clipboard): Boolean =
        clipboard.nativeClipboardManager.primaryClipDescription?.hasMimeType("text/*") ?: false

    actual suspend fun read(clipboard: Clipboard): String? {
        val clip = clipboard.getClipEntry()?.clipData ?: return null
        return (0 until clip.itemCount).firstNotNullOfOrNull { clip.getItemAt(it).text?.toString() }
    }
}
