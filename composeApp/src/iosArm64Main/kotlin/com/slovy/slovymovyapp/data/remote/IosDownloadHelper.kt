package com.slovy.slovymovyapp.data.remote

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.io.files.Path
import platform.Foundation.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@OptIn(ExperimentalForeignApi::class)
internal actual suspend fun nsUrlSessionDownload(
    url: String,
    headers: Map<String, String>,
    destPath: Path,
    onProgress: (DownloadProgress) -> Unit,
    cancelToken: CancelToken,
    moveFile: (from: Path, to: Path) -> Boolean,
) = suspendCancellableCoroutine<Unit> { cont ->
    val sessionId = "com.slovy.slovymovyapp.dl.${
        NSURL.fileURLWithPath(destPath.toString()).lastPathComponent ?: destPath.name
    }"
    val config = NSURLSessionConfiguration.backgroundSessionConfigurationWithIdentifier(sessionId)
    config.discretionary = false
    config.allowsCellularAccess = true

    val delegate = object : NSObject(), NSURLSessionDownloadDelegateProtocol {
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
            // The file at didFinishDownloadingToURL is temporary — iOS deletes it when
            // this method returns. Move it directly to the final destination.
            val srcPath = Path(
                didFinishDownloadingToURL.path ?: run {
                    session.finishTasksAndInvalidate()
                    cont.resumeWithException(IllegalStateException("Nil download location URL"))
                    return
                }
            )
            val moved = moveFile(srcPath, destPath)
            session.finishTasksAndInvalidate()
            if (moved) {
                cont.resume(Unit)
            } else {
                cont.resumeWithException(
                    IllegalStateException("Failed to move downloaded file to destination")
                )
            }
        }

        override fun URLSession(
            session: NSURLSession,
            task: NSURLSessionTask,
            didCompleteWithError: NSError?,
        ) {
            if (didCompleteWithError != null && cont.isActive) {
                cont.resumeWithException(
                    IllegalStateException("Download failed: ${didCompleteWithError.localizedDescription}")
                )
            }
        }

        override fun URLSessionDidFinishEventsForBackgroundURLSession(session: NSURLSession) {
            // Called after all pending background session events have been delivered.
            // Signal iOS that we are done processing so it can release background time.
            session.configuration.identifier?.let { BackgroundSessionRegistry.callAndRemove(it) }
        }
    }

    val session = NSURLSession.sessionWithConfiguration(config, delegate = delegate, delegateQueue = null)
    val nsUrl = NSURL.URLWithString(url) ?: run {
        cont.resumeWithException(IllegalArgumentException("Invalid URL: $url"))
        return@suspendCancellableCoroutine
    }
    val request = NSMutableURLRequest.requestWithURL(nsUrl)
    headers.forEach { (k, v) -> request.setValue(v, forHTTPHeaderField = k) }
    val task = session.downloadTaskWithRequest(request)

    cont.invokeOnCancellation {
        task.cancel()
        session.invalidateAndCancel()
    }
    task.resume()
}
