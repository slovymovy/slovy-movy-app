package com.slovy.slovymovyapp.data.export

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import com.slovy.slovymovyapp.data.remote.PlatformDbSupport
import com.slovy.slovymovyapp.data.remote.PlatformFileInput
import com.slovy.slovymovyapp.data.remote.copyTo
import com.slovy.slovymovyapp.data.remote.use
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.InputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

actual class AppDataImporter actual constructor(androidContext: Any?) {
    private val context: Context = androidContext as? Context
        ?: error("Android Context is required for AppDataImporter on Android")
    private val platform = PlatformDbSupport(context)

    actual val isSupported: Boolean = true
    actual val sourceDescription: String = "System file picker"

    actual suspend fun stageAppDataImport(): AppDataImportStageResult? {
        val uri = withContext(Dispatchers.Main) {
            AppDataImportPickerActivity.pick(context)
        } ?: return null
        return withContext(Dispatchers.IO) {
            val artifactName = displayName(uri) ?: uri.lastPathSegment ?: "selected archive"
            val input = context.contentResolver.openInputStream(uri)
                ?: error("Failed to open selected import archive.")
            input.use { stream ->
                platform.openOutput(AppDataImportApplier.stagedImportPath(platform)).use { output ->
                    platformFileInput(stream).copyTo(output)
                }
            }
            AppDataImportStageResult(
                artifactName = artifactName,
                sourceLabel = sourceDescription,
            )
        }
    }

    private fun displayName(uri: Uri): String? {
        val cursor: Cursor = context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        ) ?: return null
        return cursor.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }

    private fun platformFileInput(input: InputStream): PlatformFileInput =
        object : PlatformFileInput {
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
                input.read(buffer, offset, length)

            override fun close() {
                input.close()
            }
        }
}

class AppDataImportPickerActivity : Activity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(
                Intent.EXTRA_MIME_TYPES,
                arrayOf("application/x-tar", "application/octet-stream", "application/x-gtar")
            )
        }
        startActivityForResult(intent, REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE) {
            val uri = if (resultCode == RESULT_OK) data?.data else null
            if (uri != null) {
                AppDataImportPickerBridge.complete(uri)
            } else {
                AppDataImportPickerBridge.dismiss()
            }
        }
        finish()
    }

    companion object {
        private const val REQUEST_CODE = 4817

        /** Returns null when the user dismissed the picker without choosing a file. */
        suspend fun pick(context: Context): Uri? = suspendCancellableCoroutine { continuation ->
            AppDataImportPickerBridge.start(context, continuation)
        }
    }
}

private object AppDataImportPickerBridge {
    private val lock = Any()
    private var continuation: kotlinx.coroutines.CancellableContinuation<Uri?>? = null

    fun start(
        context: Context,
        nextContinuation: kotlinx.coroutines.CancellableContinuation<Uri?>,
    ) {
        synchronized(lock) {
            if (continuation != null) {
                nextContinuation.resumeWithException(IllegalStateException("Import picker is already open."))
                return
            }
            continuation = nextContinuation
            nextContinuation.invokeOnCancellation {
                synchronized(lock) {
                    if (continuation === nextContinuation) {
                        continuation = null
                    }
                }
            }
        }

        val intent = Intent(context, AppDataImportPickerActivity::class.java)
        if (context !is Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (t: Throwable) {
            synchronized(lock) {
                if (continuation === nextContinuation) {
                    continuation = null
                }
            }
            nextContinuation.resumeWithException(t)
        }
    }

    fun complete(uri: Uri) {
        val target = takeContinuation()
        target?.resume(uri)
    }

    fun dismiss() {
        val target = takeContinuation()
        target?.resume(null)
    }

    private fun takeContinuation(): kotlinx.coroutines.CancellableContinuation<Uri?>? =
        synchronized(lock) {
            continuation.also { continuation = null }
        }
}
