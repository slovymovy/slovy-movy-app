package com.slovy.slovymovyapp.data.export

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.slovy.slovymovyapp.data.remote.AppDataSnapshot
import com.slovy.slovymovyapp.data.remote.PlatformDbSupport
import com.slovy.slovymovyapp.data.remote.PlatformFileOutput
import com.slovy.slovymovyapp.data.remote.withAppDataSnapshots
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.files.Path
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

actual class AppDataExporter actual constructor(androidContext: Any?) {
    private val context: Context = androidContext as? Context
        ?: error("Android Context is required for AppDataExporter on Android")
    private val platform = PlatformDbSupport(context)

    actual val isSupported: Boolean = true
    actual val destinationDescription: String = "Downloads/SlovyMovy"
    actual val canShareExport: Boolean = true

    actual suspend fun exportAppData(): AppDataExportResult = withContext(Dispatchers.IO) {
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val fileName = "slovymovy-db-export-$timestamp.tar"
        withAppDataSnapshots(platform) { snapshots ->
            require(snapshots.isNotEmpty()) { "No database files found to export." }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                exportToDownloads(fileName, snapshots)
            } else {
                exportToAppExternalFiles(fileName, snapshots)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun exportToDownloads(
        fileName: String,
        snapshots: List<AppDataSnapshot>
    ): AppDataExportResult {
        var writeSucceeded = false
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "application/x-tar")
            put(
                MediaStore.Downloads.RELATIVE_PATH,
                "${Environment.DIRECTORY_DOWNLOADS}/SlovyMovy"
            )
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("Failed to create export file in Downloads")
        try {
            context.contentResolver.openOutputStream(uri)?.use { output ->
                writeTarArchive(output, snapshots)
            } ?: error("Failed to open export file in Downloads")
            context.contentResolver.update(
                uri,
                ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) },
                null,
                null
            )
            writeSucceeded = true
            return AppDataExportResult(
                artifactName = fileName,
                destinationLabel = "Downloads/SlovyMovy/$fileName",
                shareReference = uri.toString()
            )
        } finally {
            if (!writeSucceeded) {
                context.contentResolver.delete(uri, null, null)
            }
        }
    }

    private fun exportToAppExternalFiles(
        fileName: String,
        snapshots: List<AppDataSnapshot>
    ): AppDataExportResult {
        val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: error("Failed to access external downloads directory")
        val exportDir = File(
            downloadsDir,
            "SlovyMovy"
        ).apply { mkdirs() }
        val outputFile = File(exportDir, fileName)
        writeTarArchiveToPath(
            platform = platform,
            destinationPath = Path(outputFile.absolutePath),
            entries = archiveEntries(snapshots),
            lastModifiedEpochSeconds = System.currentTimeMillis() / 1000
        )
        return AppDataExportResult(
            artifactName = fileName,
            destinationLabel = outputFile.absolutePath,
            shareReference = outputFile.absolutePath
        )
    }

    actual fun shareExport(result: AppDataExportResult) {
        val shareReference = result.shareReference ?: error("Missing share reference for export.")
        val shareUri = if (shareReference.startsWith("content://")) {
            shareReference.toUri()
        } else {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                File(shareReference)
            )
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/x-tar"
            putExtra(Intent.EXTRA_STREAM, shareUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        context.startActivity(Intent.createChooser(intent, "Share export"))
    }

    private fun writeTarArchive(
        output: OutputStream,
        snapshots: List<AppDataSnapshot>
    ) {
        writeTarArchive(
            output = output.asPlatformFileOutput(),
            entries = archiveEntries(snapshots),
            lastModifiedEpochSeconds = System.currentTimeMillis() / 1000
        )
    }

    private fun archiveEntries(snapshots: List<AppDataSnapshot>): List<AppDataArchiveEntry> = snapshots.map { snapshot ->
        val file = File(snapshot.path.toString())
        AppDataArchiveEntry(
            name = snapshot.archiveName,
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

    private fun OutputStream.asPlatformFileOutput(): PlatformFileOutput = object : PlatformFileOutput {
        override fun write(buffer: ByteArray, offset: Int, length: Int) {
            this@asPlatformFileOutput.write(buffer, offset, length)
        }

        override fun flush() {
            this@asPlatformFileOutput.flush()
        }

        override fun close() {
            this@asPlatformFileOutput.close()
        }
    }
}
