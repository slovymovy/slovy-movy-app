package com.slovy.slovymovyapp.data.remote

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class LemmaRecoveryController private constructor(
    private val recovery: LemmaRecovery,
    private val acquireKeepAlive: () -> ProcessKeepAlive,
    private val scope: CoroutineScope,
) {
    constructor(
        recovery: LemmaRecovery,
        platform: PlatformDbSupport,
    ) : this(
        recovery = recovery,
        acquireKeepAlive = { platform.acquireProcessKeepAlive() },
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    )

    internal constructor(
        recovery: LemmaRecovery,
        acquireKeepAlive: () -> ProcessKeepAlive,
    ) : this(
        recovery = recovery,
        acquireKeepAlive = acquireKeepAlive,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    )

    private val mutex = Mutex()
    private var currentJob: Job? = null
    private var isClosed = false

    private val _progress = MutableStateFlow<LemmaRecoveryProgress?>(null)
    val progress: StateFlow<LemmaRecoveryProgress?> = _progress.asStateFlow()

    /** Starts a recovery run if none is in flight; otherwise returns the existing Job. */
    suspend fun ensureStarted(): Job = mutex.withLock {
        if (isClosed) throw CancellationException("Lemma recovery controller is closed")
        currentJob?.takeIf { it.isActive }?.let { return@withLock it }
        val job = scope.launch {
            var handle: ProcessKeepAlive? = null
            try {
                handle = acquireKeepAlive()
                recovery.recoverAllInstalled { progress ->
                    _progress.value = progress
                }
            } finally {
                _progress.value = null
                handle?.release()
            }
        }
        currentJob = job
        job
    }

    fun close() {
        isClosed = true
        scope.cancel()
        _progress.value = null
    }
}
