package com.slovy.slovymovyapp.util

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Read/write lock for coroutines, KMP-compatible.
 *
 * - Multiple readers can hold the read lock concurrently.
 * - A writer waits for all in-flight readers to release before its block runs.
 * - While a writer is queued or active, new unrelated read acquisitions wait.
 * - Nested read acquisitions from the same read owner are allowed while a writer is queued,
 *   avoiding self-deadlock in repository flows that compose read helpers.
 * - Lock waits are bounded by [waitTimeout] and fail with
 *   [CoroutineReadWriteLockTimeoutException] instead of hanging forever.
 *
 * [releaseRead] is **non-suspending** so the read lock can be released from an
 * [AutoCloseable.close] body, enabling the standard `.use { ... }` pattern via [ReadLease].
 *
 * Writers are serialized via an internal mutex — only one writer is active or waiting to enter
 * at a time. The write block itself is suspending so it can perform I/O safely.
 */
class CoroutineReadWriteLock(private val waitTimeout: Duration) {
    constructor() : this(DEFAULT_WAIT_TIMEOUT)

    companion object {
        val DEFAULT_WAIT_TIMEOUT: Duration = 30.seconds
    }

    private val stateLock = SynchronizedObject()
    private val writerMutex = Mutex()
    private val waitTimeoutMillis: Long

    // All fields below are guarded by stateLock.
    private var activeReaders = 0
    private val activeReadOwners = mutableMapOf<Any, Int>()
    private var writerWaiting = false
    private var writerActive = false
    private var activeWriterOwner: Any? = null
    private var readersDrained: CompletableDeferred<Unit>? = null
    private var writerReleased: CompletableDeferred<Unit>? = null

    init {
        require(waitTimeout > Duration.ZERO) { "waitTimeout must be positive" }
        waitTimeoutMillis = waitTimeout.inWholeMilliseconds.coerceAtLeast(1L)
    }

    /** Suspends until the read lock can be acquired. Must be paired with [releaseRead]. */
    suspend fun acquireRead() {
        acquireRead(owner = null)
    }

    private suspend fun acquireRead(owner: Any?) {
        while (true) {
            val waitFor: CompletableDeferred<Unit>? = synchronized(stateLock) {
                if (canAcquireRead(owner)) {
                    registerRead(owner)
                    null
                } else {
                    writerReleased ?: CompletableDeferred<Unit>().also { writerReleased = it }
                }
            }
            if (waitFor == null) return
            awaitSignal(waitFor, "for writer to release")
        }
    }

    /**
     * Releases a previously-acquired read lock. Non-suspending so it can run inside
     * [AutoCloseable.close]. Signals a waiting writer if this was the last reader.
     */
    fun releaseRead() {
        releaseRead(owner = null)
    }

    private fun releaseRead(owner: Any?) {
        val signal: CompletableDeferred<Unit>? = synchronized(stateLock) {
            check(activeReaders > 0) { "releaseRead called without matching acquireRead" }
            unregisterRead(owner)
            activeReaders--
            if (activeReaders == 0 && writerWaiting) {
                readersDrained.also { readersDrained = null }
            } else null
        }
        signal?.complete(Unit)
    }

    /**
     * Block-form helper: `acquireRead(); try { block() } finally { releaseRead() }`.
     */
    suspend fun <T> read(block: suspend () -> T): T {
        val contextOwner = coroutineContext[ReadOwnership]?.ownerFor(this)
        val jobOwner = coroutineContext[Job]
        val owner = contextOwner
            ?: jobOwner?.takeIf { isReadHeldBy(it) || isWriteHeldBy(it) }
            ?: Any()

        acquireRead(owner)
        try {
            if (contextOwner != null) {
                return block()
            }

            val ownership = (coroutineContext[ReadOwnership] ?: ReadOwnership(emptyMap()))
                .withOwner(this, owner)
            return withContext(ownership) { block() }
        } finally {
            releaseRead(owner)
        }
    }

    /**
     * Acquires the read lock and returns a [ReadLease] so the caller can use Kotlin's `.use`:
     *
     * ```
     * rwLock.lease().use {
     *     // ...read work...
     * }
     * ```
     */
    suspend fun lease(): ReadLease {
        val owner = coroutineContext[ReadOwnership]?.ownerFor(this)
            ?: coroutineContext[Job]
            ?: Any()
        acquireRead(owner)
        return ReadLease(this, owner)
    }

    /**
     * Acquires the write lock exclusively, runs [block], then releases. Waits for any in-flight
     * readers to drain before [block] runs; while [block] is active, new readers and other
     * writers wait.
     */
    suspend fun <T> write(block: suspend () -> T): T {
        awaitWriterMutex()
        var activated = false
        try {
            val owner = coroutineContext[Job] ?: Any()
            val drain = beginWrite(owner)
            if (drain != null) {
                awaitSignal(drain, "for readers to release")
                activateWaitingWriter(owner)
            }
            activated = true
            return block()
        } finally {
            if (activated) {
                finishWriter()
            } else {
                cancelWaitingWriter()
            }
            writerMutex.unlock()
        }
    }

    private fun canAcquireRead(owner: Any?): Boolean {
        if (writerActive) return owner != null && owner == activeWriterOwner
        if (!writerWaiting) return true
        return owner != null && activeReadOwners.containsKey(owner)
    }

    private fun registerRead(owner: Any?) {
        activeReaders++
        if (owner != null) {
            activeReadOwners[owner] = (activeReadOwners[owner] ?: 0) + 1
        }
    }

    private fun unregisterRead(owner: Any?) {
        if (owner == null) return
        val count = activeReadOwners[owner]
            ?: error("releaseRead called for owner without matching acquireRead")
        if (count == 1) {
            activeReadOwners.remove(owner)
        } else {
            activeReadOwners[owner] = count - 1
        }
    }

    private fun isReadHeldBy(owner: Any): Boolean = synchronized(stateLock) {
        activeReadOwners.containsKey(owner)
    }

    private fun isWriteHeldBy(owner: Any): Boolean = synchronized(stateLock) {
        writerActive && activeWriterOwner == owner
    }

    private suspend fun awaitWriterMutex() {
        try {
            withTimeout(waitTimeoutMillis) {
                writerMutex.lock()
            }
        } catch (_: TimeoutCancellationException) {
            throw CoroutineReadWriteLockTimeoutException(
                "Timed out after $waitTimeout waiting for previous writer to release",
            )
        }
    }

    private fun beginWrite(owner: Any): CompletableDeferred<Unit>? = synchronized(stateLock) {
        check(!writerWaiting && !writerActive) { "write lock state is already owned" }
        if (activeReaders == 0) {
            writerActive = true
            activeWriterOwner = owner
            null
        } else {
            writerWaiting = true
            CompletableDeferred<Unit>().also { readersDrained = it }
        }
    }

    private fun activateWaitingWriter(owner: Any) = synchronized(stateLock) {
        check(writerWaiting) { "writer wait completed without a queued writer" }
        check(activeReaders == 0) { "writer wait completed before readers drained" }
        writerWaiting = false
        readersDrained = null
        writerActive = true
        activeWriterOwner = owner
    }

    private fun finishWriter() {
        val readers: CompletableDeferred<Unit>? = synchronized(stateLock) {
            writerActive = false
            activeWriterOwner = null
            writerReleased.also { writerReleased = null }
        }
        readers?.complete(Unit)
    }

    private fun cancelWaitingWriter() {
        val readers: CompletableDeferred<Unit>? = synchronized(stateLock) {
            writerWaiting = false
            readersDrained = null
            writerReleased.also { writerReleased = null }
        }
        readers?.complete(Unit)
    }

    private suspend fun awaitSignal(
        signal: CompletableDeferred<Unit>,
        waitDescription: String,
    ) {
        try {
            withTimeout(waitTimeoutMillis) {
                signal.await()
            }
        } catch (_: TimeoutCancellationException) {
            throw CoroutineReadWriteLockTimeoutException(
                "Timed out after $waitTimeout waiting $waitDescription",
            )
        }
    }

    /**
     * AutoCloseable handle for a held read lock — use with Kotlin's `.use { ... }`.
     */
    class ReadLease internal constructor(
        private val lock: CoroutineReadWriteLock,
        private val owner: Any,
    ) : AutoCloseable {
        private var released = false
        override fun close() {
            if (released) return
            released = true
            lock.releaseRead(owner)
        }
    }
}

class CoroutineReadWriteLockTimeoutException(message: String) : IllegalStateException(message)

private class ReadOwnership(
    private val ownersByLock: Map<CoroutineReadWriteLock, Any>,
) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<ReadOwnership>

    fun ownerFor(lock: CoroutineReadWriteLock): Any? = ownersByLock[lock]

    fun withOwner(lock: CoroutineReadWriteLock, owner: Any): ReadOwnership =
        ReadOwnership(ownersByLock + (lock to owner))
}
