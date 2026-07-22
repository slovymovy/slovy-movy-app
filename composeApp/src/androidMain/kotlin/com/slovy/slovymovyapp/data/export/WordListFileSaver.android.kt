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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

actual class WordListFileSaver actual constructor(androidContext: Any?) {
    private val context: Context = androidContext as? Context
        ?: error("Android Context is required for WordListFileSaver on Android")

    actual val isSupported: Boolean = true
    actual val canShare: Boolean = true

    actual suspend fun save(fileName: String, mimeType: String, content: String): WordListSaveResult =
        withContext(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveToDownloads(fileName, mimeType, content)
            } else {
                saveToAppExternalFiles(fileName, content)
            }
        }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveToDownloads(fileName: String, mimeType: String, content: String): WordListSaveResult {
        var writeSucceeded = false
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(
                MediaStore.Downloads.RELATIVE_PATH,
                "${Environment.DIRECTORY_DOWNLOADS}/OpenWords"
            )
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("Failed to create export file in Downloads")
        try {
            context.contentResolver.openOutputStream(uri)?.use { output ->
                output.write(content.toByteArray(Charsets.UTF_8))
            } ?: error("Failed to open export file in Downloads")
            context.contentResolver.update(
                uri,
                ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) },
                null,
                null
            )
            writeSucceeded = true
            return WordListSaveResult(
                fileName = fileName,
                destinationLabel = "Downloads/OpenWords/$fileName",
                shareReference = uri.toString(),
            )
        } finally {
            if (!writeSucceeded) {
                context.contentResolver.delete(uri, null, null)
            }
        }
    }

    private fun saveToAppExternalFiles(fileName: String, content: String): WordListSaveResult {
        val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: error("Failed to access external downloads directory")
        val exportDir = File(downloadsDir, "OpenWords").apply { mkdirs() }
        val outputFile = File(exportDir, fileName)
        outputFile.writeText(content, Charsets.UTF_8)
        return WordListSaveResult(
            fileName = fileName,
            destinationLabel = outputFile.absolutePath,
            shareReference = outputFile.absolutePath,
        )
    }

    actual fun share(result: WordListSaveResult, mimeType: String) {
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
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, shareUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        context.startActivity(Intent.createChooser(intent, null))
    }
}
