package com.slovy.slovymovyapp.data.remote

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class FavoriteRecoveryController private constructor(
    private val recovery: FavoriteLemmaRecovery,
    private val acquireKeepAlive: () -> ProcessKeepAlive,
    private val scope: CoroutineScope,
) {
    constructor(
        recovery: FavoriteLemmaRecovery,
        platform: PlatformDbSupport,
    ) : this(
        recovery = recovery,
        acquireKeepAlive = { platform.acquireProcessKeepAlive() },
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    )

    internal constructor(
        recovery: FavoriteLemmaRecovery,
        acquireKeepAlive: () -> ProcessKeepAlive,
    ) : this(
        recovery = recovery,
        acquireKeepAlive = acquireKeepAlive,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    )

    private val mutex = Mutex()
    private var currentJob: Job? = null

    private val _progress = MutableStateFlow<FavoriteRecoveryProgress?>(null)
    val progress: StateFlow<FavoriteRecoveryProgress?> = _progress.asStateFlow()

    /** Starts a recovery run if none is in flight; otherwise returns the existing Job. */
    suspend fun ensureStarted(): Job = mutex.withLock {
        currentJob?.takeIf { it.isActive }?.let { return@withLock it }
        val handle = acquireKeepAlive()
        val job = scope.launch {
            try {
                recovery.recoverAllInstalledFavorites { progress ->
                    _progress.value = progress
                }
            } finally {
                _progress.value = null
                handle.release()
            }
        }
        currentJob = job
        job
    }
}
