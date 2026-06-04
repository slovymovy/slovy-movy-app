package com.slovy.slovymovyapp.data.export

import com.slovy.slovymovyapp.data.remote.PlatformDbSupport
import com.slovy.slovymovyapp.data.remote.PlatformFileOutput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.io.InputStream

actual class AppDataImporter actual constructor(androidContext: Any?) {
    private val platform = PlatformDbSupport()

    actual val isSupported: Boolean = true
    actual val sourceDescription: String = "File picker"

    actual suspend fun stageAppDataImport(): AppDataImportStageResult {
        val selected = withContext(Dispatchers.Swing) {
            FileDialog(null as Frame?, "Select OpenWords export", FileDialog.LOAD).apply {
                file = "*.tar"
                isVisible = true
            }.let { dialog ->
                val directory = dialog.directory
                val file = dialog.file
                if (directory == null || file == null) null else File(directory, file)
            }
        } ?: error("No file was selected.")

        return withContext(Dispatchers.IO) {
            selected.inputStream().use { input ->
                platform.openOutput(AppDataImportApplier.stagedImportPath(platform)).useOutput { output ->
                    copyToPlatformOutput(input, output)
                }
            }
            AppDataImportStageResult(
                artifactName = selected.name,
                sourceLabel = selected.absolutePath,
            )
        }
    }

    private fun copyToPlatformOutput(
        input: InputStream,
        output: PlatformFileOutput,
    ) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            output.write(buffer, offset = 0, length = read)
        }
        output.flush()
    }

    private inline fun PlatformFileOutput.useOutput(block: (PlatformFileOutput) -> Unit) {
        try {
            block(this)
        } finally {
            close()
        }
    }
}
