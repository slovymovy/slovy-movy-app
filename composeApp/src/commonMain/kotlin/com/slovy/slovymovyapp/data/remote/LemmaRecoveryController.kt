package com.slovy.slovymovyapp.data.remote

import com.slovy.slovymovyapp.data.settings.SettingsRepository
import com.slovy.slovymovyapp.logging.AppLogger
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
    private val isRecoveryPending: suspend () -> Boolean,
    private val clearRecoveryPending: suspend () -> Unit,
    private val scope: CoroutineScope,
) {
    constructor(
        recovery: LemmaRecovery,
        platform: PlatformDbSupport,
        settingsRepository: SettingsRepository,
    ) : this(
        recovery = recovery,
        acquireKeepAlive = { platform.acquireProcessKeepAlive() },
        isRecoveryPending = { settingsRepository.isPendingLemmaRecovery() },
        clearRecoveryPending = { settingsRepository.clearPendingLemmaRecovery() },
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    )

    internal constructor(
        recovery: LemmaRecovery,
        acquireKeepAlive: () -> ProcessKeepAlive,
        isRecoveryPending: suspend () -> Boolean,
        clearRecoveryPending: suspend () -> Unit,
    ) : this(
        recovery = recovery,
        acquireKeepAlive = acquireKeepAlive,
        isRecoveryPending = isRecoveryPending,
        clearRecoveryPending = clearRecoveryPending,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    )

    private val mutex = Mutex()
    private var currentJob: Job? = null
    private var isClosed = false

    private val _progress = MutableStateFlow<LemmaRecoveryProgress?>(null)
    val progress: StateFlow<LemmaRecoveryProgress?> = _progress.asStateFlow()

    /**
     * Starts a recovery run if none is in flight; otherwise returns the existing run.
     *
     * The run owns the pending-recovery marker: whoever sets it only has to ask for a pass, and
     * the marker is retired here once a pass that knew about it recovered everything.
     */
    suspend fun ensureStarted(): Job = mutex.withLock {
        if (isClosed) throw CancellationException("Lemma recovery controller is closed")
        currentJob?.takeIf { it.isActive }?.let { return@withLock it }
        val job = scope.launch {
            var handle: ProcessKeepAlive? = null
            try {
                handle = acquireKeepAlive()
                // Read before the pass, not after: a run already in flight when the marker was set
                // built its groups from the previous translation targets, so its clean result says
                // nothing about the language that was just added and must not retire the marker.
                val wasPending = wasRecoveryPending()
                val outcome = recovery.recoverAllInstalled { progress ->
                    _progress.value = progress
                }
                // Per-lemma failures are counted rather than thrown, so an offline run finishes
                // normally with nothing fetched; only a complete pass may retire the marker.
                if (wasPending && outcome.fullyRecovered) {
                    clearPendingMarker()
                }
            } finally {
                _progress.value = null
                handle?.release()
            }
        }
        currentJob = job
        job
    }

    /** A marker that cannot be read is treated as not set: never retire what was not confirmed. */
    private suspend fun wasRecoveryPending(): Boolean {
        return try {
            isRecoveryPending()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.warn(TAG, "Unable to read the pending recovery marker", e)
            false
        }
    }

    /** Failing to clear leaves the marker set, which costs one extra pass instead of losing data. */
    private suspend fun clearPendingMarker() {
        try {
            clearRecoveryPending()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.warn(TAG, "Unable to clear the pending recovery marker", e)
        }
    }

    fun close() {
        isClosed = true
        scope.cancel()
        _progress.value = null
    }
}

private const val TAG = "LemmaRecoveryController"
