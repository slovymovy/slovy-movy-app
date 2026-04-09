package com.slovy.slovymovyapp.data.remote

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CancellableContinuation
import kotlinx.io.files.Path
import platform.Foundation.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@OptIn(ExperimentalForeignApi::class)
internal class IosBackgroundDownloadDelegate(
    private val tempPath: Path,
    private val destPath: Path,
    private val platform: PlatformDbSupport,
    private val onProgress: (DownloadProgress) -> Unit,
    private val cancelToken: CancelToken,
    private val cont: CancellableContinuation<Unit>,
) : NSObject(), NSURLSessionDownloadDelegateProtocol {

    override fun URLSession(
        session: NSURLSession,
        downloadTask: NSURLSessionDownloadTask,
        didWriteData: Long,
        totalBytesWritten: Long,
        totalBytesExpectedToWrite: Long,
    ) {
        if (cancelToken.isCancelled) {
            downloadTask.cancel()
            return
        }
        val total = if (totalBytesExpectedToWrite > 0) totalBytesExpectedToWrite else null
        onProgress(DownloadProgress(totalBytesWritten, total))
    }

    override fun URLSession(
        session: NSURLSession,
        downloadTask: NSURLSessionDownloadTask,
        didFinishDownloadingToURL: NSURL,
    ) {
        // The downloaded file at locationURL is temporary — iOS deletes it when this method returns.
        // Move it synchronously before returning.
        val srcPath = Path(
            didFinishDownloadingToURL.path ?: run {
                session.finishTasksAndInvalidate()
                cont.resumeWithException(IllegalStateException("Nil download location URL"))
                return
            }
        )
        val moved = platform.moveFile(srcPath, tempPath) && platform.moveFile(tempPath, destPath)
        session.finishTasksAndInvalidate()
        if (moved) {
            cont.resume(Unit)
        } else {
            cont.resumeWithException(IllegalStateException("Failed to move downloaded file to destination"))
        }
    }

    override fun URLSession(
        session: NSURLSession,
        task: NSURLSessionTask,
        didCompleteWithError: NSError?,
    ) {
        if (didCompleteWithError != null && cont.isActive) {
            platform.deleteFile(tempPath)
            cont.resumeWithException(
                IllegalStateException("Download failed: ${didCompleteWithError.localizedDescription}")
            )
        }
    }
}
