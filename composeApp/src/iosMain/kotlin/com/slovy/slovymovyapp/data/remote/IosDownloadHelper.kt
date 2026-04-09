package com.slovy.slovymovyapp.data.remote

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.io.files.Path
import platform.Foundation.*
import platform.darwin.NSObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * iOS background-download bridge.
 *
 * The download runs on a background `NSURLSession` so it can continue while
 * the app is suspended. The result is delivered via a Kotlin coroutine
 * continuation captured in this function.
 *
 * **Limitation: app-terminated relaunches are not recovered.**
 *
 * If iOS terminates the app while a download is in flight and later relaunches
 * us specifically to deliver pending background-session events
 * (`application(_:handleEventsForBackgroundURLSession:completionHandler:)`),
 * the original Kotlin continuation has died with the previous process. We do
 * not currently persist destination paths anywhere, so even if we recreated
 * the session and the delegate received the temp file, there would be no way
 * to move it to its final destination.
 *
 * Instead, the Swift `AppDelegate` recreates a session with the matching
 * identifier, attaches a minimal cleanup delegate that lets iOS reclaim the
 * temp file, and calls the iOS-supplied completion handler from
 * `urlSessionDidFinishEvents(forBackgroundURLSession:)`. This unblocks iOS
 * but loses the download — the user has to re-trigger it from inside the app.
 *
 * Proper recovery would require persisting `(sessionId → destPath)` to disk
 * when a download starts, then reading that mapping in the AppDelegate /
 * KMP relaunch path to move the temp file to the right place.
 */
@OptIn(ExperimentalForeignApi::class)
internal suspend fun nsUrlSessionDownload(
    url: String,
    headers: Map<String, String>,
    destPath: Path,
    onProgress: (DownloadProgress) -> Unit,
    cancelToken: CancelToken,
    moveFile: (from: Path, to: Path) -> Boolean,
    getAvailableBytesForDestination: () -> Long?,
) = suspendCancellableCoroutine<Unit> { cont ->
    // Derive a stable, collision-resistant session identifier from the full
    // destination path. lastPathComponent alone collides whenever two
    // destinations share a filename (e.g. dictionary vs. translation DBs with
    // the same name in different dirs, or retries of the same target).
    val destPathStr = destPath.toString()
    val pathHash = destPathStr.hashCode().toUInt().toString(16)
    val filename = NSURL.fileURLWithPath(destPathStr).lastPathComponent ?: destPath.name
    val sessionId = "com.slovy.slovymovyapp.dl.$pathHash.$filename"
    val config = NSURLSessionConfiguration.backgroundSessionConfigurationWithIdentifier(sessionId)
    config.discretionary = false
    config.allowsCellularAccess = true

    val delegate = object : NSObject(), NSURLSessionDownloadDelegateProtocol {
        private var diskSpaceChecked = false
        private var cancelledByUs = false

        override fun URLSession(
            session: NSURLSession,
            downloadTask: NSURLSessionDownloadTask,
            didWriteData: Long,
            totalBytesWritten: Long,
            totalBytesExpectedToWrite: Long,
        ) {
            if (cancelToken.isCancelled) {
                cancelledByUs = true
                downloadTask.cancel()
                return
            }
            if (!diskSpaceChecked && totalBytesExpectedToWrite > 0) {
                diskSpaceChecked = true
                val available = getAvailableBytesForDestination()
                val headroom = 1024L * 1024L
                if (available != null && available < totalBytesExpectedToWrite + headroom) {
                    downloadTask.cancel()
                    session.finishTasksAndInvalidate()
                    if (cont.isActive) {
                        cont.resumeWithException(
                            IllegalStateException(
                                "Not enough free space to download file: " +
                                    "required=${totalBytesExpectedToWrite + headroom}, available=$available"
                            )
                        )
                    }
                    return
                }
            }
            val total = if (totalBytesExpectedToWrite > 0) totalBytesExpectedToWrite else null
            onProgress(DownloadProgress(totalBytesWritten, total))
        }

        override fun URLSession(
            session: NSURLSession,
            downloadTask: NSURLSessionDownloadTask,
            didFinishDownloadingToURL: NSURL,
        ) {
            // NSURLSession download tasks treat HTTP 4xx/5xx as "successful"
            // downloads — the response body becomes a file. Validate status
            // before keeping the result.
            val httpResponse = downloadTask.response as? NSHTTPURLResponse
            val status = httpResponse?.statusCode?.toInt() ?: 0
            if (status !in 200..299) {
                session.finishTasksAndInvalidate()
                if (cont.isActive) {
                    cont.resumeWithException(
                        IllegalStateException("HTTP $status while downloading $url")
                    )
                }
                return
            }
            // The file at didFinishDownloadingToURL is temporary — iOS deletes it when
            // this method returns. Move it directly to the final destination.
            val srcPath = Path(
                didFinishDownloadingToURL.path ?: run {
                    session.finishTasksAndInvalidate()
                    if (cont.isActive) {
                        cont.resumeWithException(IllegalStateException("Nil download location URL"))
                    }
                    return
                }
            )
            val moved = moveFile(srcPath, destPath)
            session.finishTasksAndInvalidate()
            if (cont.isActive) {
                if (moved) {
                    cont.resume(Unit)
                } else {
                    cont.resumeWithException(
                        IllegalStateException("Failed to move downloaded file to destination")
                    )
                }
            }
        }

        override fun URLSession(
            session: NSURLSession,
            task: NSURLSessionTask,
            didCompleteWithError: NSError?,
        ) {
            if (didCompleteWithError != null) {
                // Always tear down the background session on transport errors
                // (DNS, timeout, offline, ...). Otherwise it stays alive and a
                // later download with the same identifier collides with it.
                session.finishTasksAndInvalidate()
                if (cont.isActive) {
                    val isCancellation = cancelledByUs ||
                        cancelToken.isCancelled ||
                        didCompleteWithError.code == NSURLErrorCancelled
                    if (isCancellation) {
                        cont.resumeWithException(DataDbManager.DownloadCancelledException())
                    } else {
                        cont.resumeWithException(
                            IllegalStateException("Download failed: ${didCompleteWithError.localizedDescription}")
                        )
                    }
                }
            }
        }

        // Note: we intentionally do NOT override
        // URLSessionDidFinishEventsForBackgroundURLSession here. That callback
        // only fires for sessions iOS recreates after relaunching the app, and
        // sessions created on this code path live entirely in-process. The
        // app-relaunch case is handled by the Swift AppDelegate cleanup path
        // documented at the top of this file.
    }

    // Validate URL before creating the session so a malformed URL does not
    // leak a live NSURLSession.
    val nsUrl = NSURL.URLWithString(url) ?: run {
        cont.resumeWithException(IllegalArgumentException("Invalid URL: $url"))
        return@suspendCancellableCoroutine
    }
    val session = NSURLSession.sessionWithConfiguration(config, delegate = delegate, delegateQueue = null)
    val request = NSMutableURLRequest.requestWithURL(nsUrl)
    headers.forEach { (k, v) -> request.setValue(v, forHTTPHeaderField = k) }
    val task = session.downloadTaskWithRequest(request)

    cont.invokeOnCancellation {
        task.cancel()
        session.invalidateAndCancel()
    }
    task.resume()
}
