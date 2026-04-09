package com.slovy.slovymovyapp.data.remote

import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.synchronized

/**
 * Callback interface used to bridge iOS background URL session completion handlers
 * from the Swift AppDelegate into KMP code. The AppDelegate implements this via a
 * Swift class wrapping the iOS-provided completion handler.
 */
interface BackgroundDownloadCompletionCallback {
    fun call()
}

/**
 * Registry that maps background URLSession identifiers to their iOS completion handlers.
 * Called from the Swift AppDelegate when iOS relaunches the app after a background download,
 * and consumed by [nsUrlSessionDownload] once all session events have been delivered.
 */
object BackgroundSessionRegistry {
    private val lock = reentrantLock()
    private val handlers = mutableMapOf<String, BackgroundDownloadCompletionCallback>()

    fun registerCompletionHandler(sessionId: String, handler: BackgroundDownloadCompletionCallback) {
        lock.synchronized { handlers[sessionId] = handler }
    }

    internal fun callAndRemove(sessionId: String) {
        val handler = lock.synchronized { handlers.remove(sessionId) }
        handler?.call()
    }
}
