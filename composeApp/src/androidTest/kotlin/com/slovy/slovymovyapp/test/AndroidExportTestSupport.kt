package com.slovy.slovymovyapp.test

import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.slovy.slovymovyapp.data.export.AppDataExportResult
import com.slovy.slovymovyapp.data.export.standardAppDataFileNamesWithSidecars
import java.io.File

internal object AndroidExportTestSupport {
    suspend fun runInExportTestEnvironment(
        context: Context,
        block: suspend () -> Unit
    ) {
        clearDatabaseFiles(context)
        clearExportArtifacts(context)
        try {
            block()
        } finally {
            clearDatabaseFiles(context)
            clearExportArtifacts(context)
        }
    }

    fun exportArtifactExists(
        context: Context,
        result: AppDataExportResult
    ): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val projection = arrayOf(MediaStore.Downloads._ID)
            val selection = "${MediaStore.Downloads.DISPLAY_NAME} = ?"
            val selectionArgs = arrayOf(result.artifactName)
            return resolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                cursor.moveToFirst()
            } ?: false
        }
        val root = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return false
        return File(File(root, "SlovyMovy"), result.artifactName).exists()
    }

    fun deleteExportArtifact(
        context: Context,
        result: AppDataExportResult
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val projection = arrayOf(MediaStore.Downloads._ID)
            val selection = "${MediaStore.Downloads.DISPLAY_NAME} = ?"
            val selectionArgs = arrayOf(result.artifactName)
            resolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIndex)
                    val uri = MediaStore.Downloads.EXTERNAL_CONTENT_URI.buildUpon()
                        .appendPath(id.toString())
                        .build()
                    resolver.delete(uri, null, null)
                }
            }
        } else {
            val root = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return
            File(File(root, "SlovyMovy"), result.artifactName).delete()
        }
    }

    private fun clearExportArtifacts(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val projection = arrayOf(MediaStore.Downloads._ID)
            val selection = "${MediaStore.Downloads.DISPLAY_NAME} LIKE ?"
            val selectionArgs = arrayOf("slovymovy-db-export-%")
            resolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIndex)
                    val uri = MediaStore.Downloads.EXTERNAL_CONTENT_URI.buildUpon()
                        .appendPath(id.toString())
                        .build()
                    resolver.delete(uri, null, null)
                }
            }
        } else {
            val root = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return
            File(root, "SlovyMovy").deleteRecursively()
        }
    }

    private fun clearDatabaseFiles(context: Context) {
        val dbDir = context.getDatabasePath("app.db").parentFile ?: return
        standardAppDataFileNamesWithSidecars.forEach { name ->
            File(dbDir, name).delete()
        }
    }
}
