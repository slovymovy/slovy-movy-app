package com.slovy.slovymovyapp.data.export

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.slovy.slovymovyapp.data.remote.PlatformFileInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.InputStream
import kotlin.coroutines.resume
import kotlin.system.exitProcess

actual class AppDataImportPicker actual constructor(androidContext: Any?) {
    private val context: Context = androidContext as? Context
        ?: error("Android Context is required for AppDataImportPicker on Android")
    private val activity: ComponentActivity? = androidContext as? ComponentActivity

    actual val isSupported: Boolean = activity != null
    actual val canQuitToApply: Boolean = true

    actual suspend fun pickArchive(): PickedAppDataArchive? {
        val host = activity ?: error("Importing needs an Activity host.")
        // The picker is bound to the Activity it was created with; a ViewModel that outlived that
        // Activity would otherwise launch through a dead result registry and never get an answer.
        check(host.lifecycle.currentState != Lifecycle.State.DESTROYED) {
            "The app window was recreated; go back and reopen the Developer screen to import."
        }
        val uri = withContext(Dispatchers.Main) { openDocument(host) } ?: return null
        val stream = context.contentResolver.openInputStream(uri)
            ?: error("The selected file could not be opened.")
        val (displayName, sizeBytes) = documentInfo(uri)
        return PickedAppDataArchive(
            displayName = displayName ?: uri.lastPathSegment ?: "archive",
            sizeBytes = sizeBytes,
            input = stream.asPlatformFileInput(),
            release = {},
        )
    }

    actual fun quitToApply() {
        activity?.finishAffinity()
        exitProcess(0)
    }

    /**
     * Opens the system document picker through the host's result registry. The registration is
     * tied to this Activity instance: if the Activity is destroyed while the picker is open (a
     * configuration change outside the manifest's list, or "Don't keep activities"), the result
     * would be delivered to a new registry nobody listens to, so the pick resolves as cancelled
     * instead of leaving the caller suspended forever.
     */
    private suspend fun openDocument(host: ComponentActivity): Uri? = suspendCancellableCoroutine { continuation ->
        var launcher: ActivityResultLauncher<Array<String>>? = null
        var observer: LifecycleEventObserver? = null
        fun finish(uri: Uri?) {
            launcher?.unregister()
            observer?.let { host.lifecycle.removeObserver(it) }
            if (continuation.isActive) continuation.resume(uri)
        }
        launcher = host.activityResultRegistry.register(
            "app-data-import",
            ActivityResultContracts.OpenDocument(),
        ) { uri -> finish(uri) }
        observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_DESTROY) finish(null)
        }
        host.lifecycle.addObserver(observer)
        continuation.invokeOnCancellation { finish(null) }
        launcher.launch(arrayOf("*/*"))
    }

    /** Display name and size from the document provider; either is `null` when the provider omits it. */
    private fun documentInfo(uri: Uri): Pair<String?, Long?> {
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        return context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val name = cursor.getString(0)
            val size = if (cursor.isNull(1)) null else cursor.getLong(1)
            name to size
        } ?: (null to null)
    }

    private fun InputStream.asPlatformFileInput(): PlatformFileInput = object : PlatformFileInput {
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            this@asPlatformFileInput.read(buffer, offset, length)

        override fun close() {
            this@asPlatformFileInput.close()
        }
    }
}
