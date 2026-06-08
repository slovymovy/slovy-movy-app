package com.slovy.slovymovyapp.server.lists

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * Per-language in-memory cache for [LanguageListsBundle]s.
 *
 * - Lazy refresh: a request after [ttl] triggers one upstream load; concurrent callers
 *   for the same language coalesce on a per-language [Mutex].
 * - Failed loads are not cached — the next call retries.
 * - The [now] clock is injectable so tests can advance time without sleeping.
 */
class LanguageListsCache(
    private val loader: suspend (String) -> LanguageListsBundle,
    private val ttl: Duration = 24.hours,
    private val now: () -> Instant = Clock.System::now,
) {
    private data class Entry(val bundle: LanguageListsBundle, val fetchedAt: Instant)

    private val entries = ConcurrentHashMap<String, Entry>()
    private val locks = ConcurrentHashMap<String, Mutex>()

    suspend fun get(lang: String): LanguageListsBundle {
        entries[lang]?.let { if (isFresh(it)) return it.bundle }
        val lock = locks.computeIfAbsent(lang) { Mutex() }
        return lock.withLock {
            entries[lang]?.let { if (isFresh(it)) return@withLock it.bundle }
            val bundle = loader(lang)
            entries[lang] = Entry(bundle, now())
            bundle
        }
    }

    private fun isFresh(entry: Entry): Boolean = now() - entry.fetchedAt < ttl
}
