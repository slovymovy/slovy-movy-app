package com.slovy.slovymovyapp.data.export

import com.slovy.slovymovyapp.data.remote.PlatformDbSupport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.files.Path
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import kotlin.system.exitProcess

actual class AppDataImportPicker actual constructor(androidContext: Any?) {
    private val platform = PlatformDbSupport()

    actual val isSupported: Boolean = true
    actual val canQuitToApply: Boolean = true

    actual suspend fun pickArchive(): PickedAppDataArchive? {
        val selected = withContext(Dispatchers.Main) {
            val dialog = FileDialog(null as Frame?, "Select an OpenWords export", FileDialog.LOAD)
            dialog.setFilenameFilter { _, name -> name.endsWith(".tar", ignoreCase = true) }
            dialog.isVisible = true
            val directory = dialog.directory
            val file = dialog.file
            if (directory == null || file == null) null else File(directory, file)
        } ?: return null
        val path = Path(selected.absolutePath)
        return PickedAppDataArchive(
            displayName = selected.name,
            sizeBytes = platform.getFileSize(path),
            input = platform.openInput(path),
            release = {},
        )
    }

    actual fun quitToApply() {
        exitProcess(0)
    }
}
