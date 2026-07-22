package com.slovy.slovymovyapp.data.export

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

actual class WordListFileSaver actual constructor(androidContext: Any?) {
    private val homeDir = File(System.getProperty("user.home") ?: ".")
    private val exportsDir = File(homeDir, "Downloads/SlovyMovy")

    actual val isSupported: Boolean = true
    actual val canShare: Boolean = false

    actual suspend fun save(fileName: String, mimeType: String, content: String): WordListSaveResult =
        withContext(Dispatchers.IO) {
            exportsDir.mkdirs()
            val outputFile = File(exportsDir, fileName)
            outputFile.writeText(content, Charsets.UTF_8)
            WordListSaveResult(
                fileName = fileName,
                destinationLabel = outputFile.absolutePath,
                shareReference = outputFile.absolutePath,
            )
        }

    actual fun share(result: WordListSaveResult, mimeType: String) {
        // Desktop export stops after writing the file, matching AppDataExporter.
    }
}
