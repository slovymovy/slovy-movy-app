package com.slovy.slovymovyapp.data.remote

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

enum class DownloadStatus {
    Idle,
    Running,
    Done,
    Failed,
    Cancelled
}

data class DownloadEntry(
    val status: DownloadStatus,
    val progress: DownloadProgress? = null,
    val error: Throwable? = null
)

class DownloadCoordinator(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    private val downloads = MutableStateFlow<Map<String, DownloadEntry>>(emptyMap())
    private val jobs = mutableMapOf<String, Job>()
    private val cancelTokens = mutableMapOf<String, CancelToken>()

    fun downloadEntries(): StateFlow<Map<String, DownloadEntry>> = downloads.asStateFlow()

    fun downloadEntryFlow(key: String): Flow<DownloadEntry?> {
        return downloads.map { it[key] }.distinctUntilChanged()
    }

    fun isRunning(key: String): Boolean = jobs[key]?.isActive == true

    fun startDownload(
        key: String,
        download: suspend (onProgress: (DownloadProgress) -> Unit, cancelToken: CancelToken) -> Unit
    ): Flow<DownloadEntry?> {
        if (jobs[key]?.isActive == true) {
            return downloadEntryFlow(key)
        }

        val cancelToken = CancelToken()
        cancelTokens[key] = cancelToken
        updateEntry(key, DownloadEntry(status = DownloadStatus.Running, progress = null))

        val job = scope.launch {
            try {
                download(
                    { progress -> updateEntry(key, DownloadEntry(DownloadStatus.Running, progress = progress)) },
                    cancelToken
                )
                updateEntry(key, DownloadEntry(status = DownloadStatus.Done))
            } catch (_: CancellationException) {
                updateEntry(key, DownloadEntry(status = DownloadStatus.Cancelled))
            } catch (t: Throwable) {
                updateEntry(key, DownloadEntry(status = DownloadStatus.Failed, error = t))
            } finally {
                jobs.remove(key)
                cancelTokens.remove(key)
            }
        }
        jobs[key] = job
        return downloadEntryFlow(key)
    }

    fun cancel(key: String) {
        cancelTokens[key]?.cancel()
    }

    fun clear(key: String) {
        downloads.update { it - key }
    }

    fun close() {
        scope.cancel()
    }

    private fun updateEntry(key: String, entry: DownloadEntry) {
        downloads.update { it + (key to entry) }
    }
}
