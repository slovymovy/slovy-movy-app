package com.slovy.slovymovyapp.data.export

import com.slovy.slovymovyapp.data.remote.PlatformDbSupport
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.io.files.Path
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UniformTypeIdentifiers.UTTypeData
import platform.darwin.NSObject
import kotlin.coroutines.resume

actual class AppDataImportPicker actual constructor(androidContext: Any?) {
    private val platform = PlatformDbSupport()

    /** The presented picker's delegate; UIKit only holds it weakly. */
    private var activeDelegate: PickerDelegate? = null

    actual val isSupported: Boolean = true
    actual val canQuitToApply: Boolean = false

    @OptIn(ExperimentalForeignApi::class)
    actual suspend fun pickArchive(): PickedAppDataArchive? {
        val url = withContext(Dispatchers.Main) { presentPicker() } ?: return null
        // asCopy = true hands us a private copy under tmp/, so no security-scoped access is needed.
        val path = url.path ?: error("The selected file has no path.")
        return PickedAppDataArchive(
            displayName = url.lastPathComponent ?: "archive",
            sizeBytes = platform.getFileSize(Path(path)),
            input = platform.openInput(Path(path)),
            release = { NSFileManager.defaultManager.removeItemAtPath(path, error = null) },
        )
    }

    actual fun quitToApply() {
        error("iOS applications cannot quit themselves; the user relaunches the app.")
    }

    private suspend fun presentPicker(): NSURL? = suspendCancellableCoroutine { continuation ->
        val presenter = IosPresentation.topViewController()
        if (presenter == null) {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }
        val controller = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeData), asCopy = true)
        val delegate = PickerDelegate { url ->
            activeDelegate = null
            if (continuation.isActive) continuation.resume(url)
        }
        activeDelegate = delegate
        controller.delegate = delegate
        controller.allowsMultipleSelection = false
        continuation.invokeOnCancellation {
            activeDelegate = null
            controller.dismissViewControllerAnimated(true, completion = null)
        }
        presenter.presentViewController(controller, animated = true, completion = null)
    }

    private class PickerDelegate(
        private val onResult: (NSURL?) -> Unit,
    ) : NSObject(), UIDocumentPickerDelegateProtocol {
        override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
            onResult(didPickDocumentsAtURLs.firstOrNull() as? NSURL)
        }

        override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
            onResult(null)
        }
    }
}
