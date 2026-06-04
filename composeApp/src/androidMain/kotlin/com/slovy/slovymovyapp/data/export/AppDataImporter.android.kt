package com.slovy.slovymovyapp.data.export

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import com.slovy.slovymovyapp.data.remote.PlatformDbSupport
import com.slovy.slovymovyapp.data.remote.PlatformFileOutput
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

    actual suspend fun stageAppDataImport(): AppDataImportStageResult {
        val uri = withContext(Dispatchers.Main) {
            AppDataImportPickerActivity.pick(context)
        }
        return withContext(Dispatchers.IO) {
            val artifactName = displayName(uri) ?: uri.lastPathSegment ?: "selected archive"
            context.contentResolver.openInputStream(uri)?.use { input ->
                platform.openOutput(AppDataImportApplier.stagedImportPath(platform)).useOutput { output ->
                    copyToPlatformOutput(input, output)
                }
            } ?: error("Failed to open selected import archive.")
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
        if (requestCode == REQUEST_CODE && resultCode == RESULT_OK) {
            val uri = data?.data
            if (uri != null) {
                AppDataImportPickerBridge.complete(uri)
            } else {
                AppDataImportPickerBridge.fail(IllegalStateException("No file was selected."))
            }
        } else {
            AppDataImportPickerBridge.cancel()
        }
        finish()
    }

    companion object {
        private const val REQUEST_CODE = 4817

        suspend fun pick(context: Context): Uri = suspendCancellableCoroutine { continuation ->
            AppDataImportPickerBridge.start(context, continuation)
        }
    }
}

private object AppDataImportPickerBridge {
    private val lock = Any()
    private var continuation: kotlinx.coroutines.CancellableContinuation<Uri>? = null

    fun start(
        context: Context,
        nextContinuation: kotlinx.coroutines.CancellableContinuation<Uri>,
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

    fun fail(error: Throwable) {
        val target = takeContinuation()
        target?.resumeWithException(error)
    }

    fun cancel() {
        val target = takeContinuation()
        target?.cancel()
    }

    private fun takeContinuation(): kotlinx.coroutines.CancellableContinuation<Uri>? =
        synchronized(lock) {
            continuation.also { continuation = null }
        }
}
