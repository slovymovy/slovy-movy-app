package com.slovy.slovymovyapp.server.lists

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * Per-key in-memory cache with TTL and coalesced concurrent loads.
 *
 * - Lazy refresh: a request after [ttl] triggers one upstream load; concurrent
 *   callers for the same key share that load via a per-key [Mutex].
 * - Failed loads are not cached and do not pin a Mutex in [locks] (the route
 *   accepts arbitrary user input, so we must not grow [locks] unboundedly).
 * - [now] is injectable so tests can advance time without sleeping.
 */
class TtlCache<T : Any>(
    private val loader: suspend (String) -> T,
    private val ttl: Duration = 24.hours,
    private val now: () -> Instant = Clock.System::now,
) {
    private data class Entry<T>(val value: T, val fetchedAt: Instant)

    private val entries = ConcurrentHashMap<String, Entry<T>>()
    private val locks = ConcurrentHashMap<String, Mutex>()

    suspend fun get(key: String): T {
        entries[key]?.let { if (isFresh(it)) return it.value }
        val lock = locks.computeIfAbsent(key) { Mutex() }
        var cached = false
        try {
            return lock.withLock {
                entries[key]?.let {
                    if (isFresh(it)) {
                        cached = true
                        return@withLock it.value
                    }
                }
                val value = loader(key)
                entries[key] = Entry(value, now())
                cached = true
                value
            }
        } finally {
            if (!cached) locks.remove(key, lock)
        }
    }

    /** Returns the cached value if present and fresh; never triggers a load. */
    fun peekFresh(key: String): T? {
        val entry = entries[key] ?: return null
        return if (isFresh(entry)) entry.value else null
    }

    internal fun lockCount(): Int = locks.size

    private fun isFresh(entry: Entry<T>): Boolean = now() - entry.fetchedAt < ttl
}
