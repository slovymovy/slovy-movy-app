package com.slovy.slovymovyapp.ui

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.Clipboard

// hasPlainText() asks UIPasteboard.hasStrings, which shows no paste prompt; only
// getPlainText() reads the content.
@OptIn(ExperimentalComposeUiApi::class)
actual object ClipboardText {
    actual suspend fun has(clipboard: Clipboard): Boolean =
        clipboard.getClipEntry()?.hasPlainText() ?: false

    actual suspend fun read(clipboard: Clipboard): String? =
        clipboard.getClipEntry()?.takeIf { it.hasPlainText() }?.getPlainText()
}
