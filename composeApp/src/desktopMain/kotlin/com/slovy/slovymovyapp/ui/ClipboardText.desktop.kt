package com.slovy.slovymovyapp.ui

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.awtClipboard
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.IOException

@OptIn(ExperimentalComposeUiApi::class)
actual object ClipboardText {
    // AWT throws IllegalStateException while another app holds the system clipboard.
    actual suspend fun has(clipboard: Clipboard): Boolean = try {
        clipboard.awtClipboard?.isDataFlavorAvailable(DataFlavor.stringFlavor) ?: false
    } catch (_: IllegalStateException) {
        false
    }

    actual suspend fun read(clipboard: Clipboard): String? = try {
        clipboard.awtClipboard?.getData(DataFlavor.stringFlavor) as? String
    } catch (_: IllegalStateException) {
        null
    } catch (_: UnsupportedFlavorException) {
        null
    } catch (_: IOException) {
        null
    }
}
