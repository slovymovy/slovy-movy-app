package com.slovy.slovymovyapp.data.export

import com.slovy.slovymovyapp.data.remote.PlatformDbSupport
import com.slovy.slovymovyapp.data.remote.copyTo
import com.slovy.slovymovyapp.data.remote.use
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import kotlinx.io.files.Path
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

actual class AppDataImporter actual constructor(androidContext: Any?) {
    private val platform = PlatformDbSupport()

    actual val isSupported: Boolean = true
    actual val sourceDescription: String = "File picker"

    actual suspend fun stageAppDataImport(): AppDataImportStageResult? {
        val selected = withContext(Dispatchers.Swing) {
            FileDialog(null as Frame?, "Select OpenWords export", FileDialog.LOAD).apply {
                file = "*.tar"
                isVisible = true
            }.let { dialog ->
                val directory = dialog.directory
                val file = dialog.file
                if (directory == null || file == null) null else File(directory, file)
            }
        } ?: return null

        return withContext(Dispatchers.IO) {
            platform.openInput(Path(selected.absolutePath)).use { input ->
                platform.openOutput(AppDataImportApplier.stagedImportPath(platform)).use { output ->
                    input.copyTo(output)
                }
            }
            AppDataImportStageResult(
                artifactName = selected.name,
                sourceLabel = selected.absolutePath,
            )
        }
    }
}
