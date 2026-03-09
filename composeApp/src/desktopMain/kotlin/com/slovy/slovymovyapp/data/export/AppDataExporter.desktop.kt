package com.slovy.slovymovyapp.data.export

import com.slovy.slovymovyapp.data.remote.PlatformDbSupport
import com.slovy.slovymovyapp.data.remote.PlatformFileOutput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.files.Path
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

actual class AppDataExporter actual constructor(androidContext: Any?) {
    private val platform = PlatformDbSupport()
    private val homeDir = File(System.getProperty("user.home") ?: ".")
    private val databasesDir = File(homeDir, ".slovymovyapp/databases")
    private val exportsDir = File(homeDir, "Downloads/SlovyMovy")

    actual val isSupported: Boolean = true
    actual val destinationDescription: String = exportsDir.absolutePath
    actual val canShareExport: Boolean = false

    actual suspend fun exportAppData(): AppDataExportResult = withContext(Dispatchers.IO) {
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val fileName = "slovymovy-db-export-$timestamp.tar"
        val exportFiles = databaseFiles()
        require(exportFiles.isNotEmpty()) { "No database files found to export." }

        exportsDir.mkdirs()
        val outputFile = File(exportsDir, fileName)
        writeTarArchiveToPath(
            platform = platform,
            destinationPath = Path(outputFile.absolutePath),
            entries = archiveEntries(exportFiles),
            lastModifiedEpochSeconds = System.currentTimeMillis() / 1000
        )

        AppDataExportResult(
            artifactName = fileName,
            destinationLabel = outputFile.absolutePath,
            shareReference = outputFile.absolutePath
        )
    }

    actual fun shareExport(result: AppDataExportResult) {
        // Desktop export currently stops after writing the file.
    }

    private fun databaseFiles(): List<File> {
        return standardAppDataFileNamesWithSidecars
            .map { name -> File(databasesDir, name) }
            .filter { it.exists() && it.isFile }
    }

    private fun archiveEntries(files: List<File>): List<AppDataArchiveEntry> = files.map { file ->
        AppDataArchiveEntry(
            name = file.name,
            sizeBytes = file.length(),
            writeContentsTo = { output ->
                file.inputStream().use { input ->
                    copyToPlatformOutput(input, output)
                }
            }
        )
    }

    private fun copyToPlatformOutput(
        input: java.io.InputStream,
        output: PlatformFileOutput
    ) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            output.write(buffer, offset = 0, length = read)
        }
    }
}
