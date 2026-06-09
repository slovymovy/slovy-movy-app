package com.slovy.slovymovyapp.server.lists

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class TtlCacheTest {

    @Test
    fun firstCall_invokesLoader() = runBlocking {
        val calls = AtomicInteger(0)
        val cache = TtlCache<String>(
            loader = { calls.incrementAndGet(); "v1" },
            now = { Instant.fromEpochMilliseconds(0) },
        )
        assertEquals("v1", cache.get("en"))
        assertEquals(1, calls.get())
    }

    @Test
    fun withinTtl_returnsCached() = runBlocking {
        val calls = AtomicInteger(0)
        var clock = Instant.fromEpochMilliseconds(0)
        val cache = TtlCache<String>(
            loader = { calls.incrementAndGet(); "v1" },
            now = { clock },
        )
        cache.get("en")
        clock = Instant.fromEpochMilliseconds(23.hours.inWholeMilliseconds)
        cache.get("en")
        assertEquals(1, calls.get(), "Loader must be invoked only once within TTL")
    }

    @Test
    fun afterTtl_refetches() = runBlocking {
        val calls = AtomicInteger(0)
        var clock = Instant.fromEpochMilliseconds(0)
        val cache = TtlCache<String>(
            loader = { "v${calls.incrementAndGet()}" },
            now = { clock },
        )
        cache.get("en")
        clock = Instant.fromEpochMilliseconds(25.hours.inWholeMilliseconds)
        val second = cache.get("en")
        assertEquals(2, calls.get(), "Loader must be re-invoked after TTL")
        assertEquals("v2", second)
    }

    @Test
    fun concurrentCalls_sameKey_coalesceToOneLoad() = runBlocking {
        val calls = AtomicInteger(0)
        val cache = TtlCache<String>(
            loader = {
                calls.incrementAndGet()
                delay(50)
                "v1"
            },
            now = { Instant.fromEpochMilliseconds(0) },
        )
        coroutineScope {
            repeat(10) { launch { cache.get("en") } }
        }
        assertEquals(1, calls.get(), "Concurrent callers for the same key must coalesce into one upstream load")
    }

    @Test
    fun failedLoad_isNotCached() = runBlocking {
        val calls = AtomicInteger(0)
        val cache = TtlCache<String>(
            loader = {
                if (calls.incrementAndGet() == 1) throw IllegalStateException("boom")
                "v-ok"
            },
            now = { Instant.fromEpochMilliseconds(0) },
        )
        assertFailsWith<IllegalStateException> { cache.get("en") }
        assertEquals("v-ok", cache.get("en"))
        assertEquals(2, calls.get(), "Failure must not be cached")
    }

    @Test
    fun repeatedFailedProbes_doNotAccumulateLocks() = runBlocking {
        val cache = TtlCache<String>(
            loader = { throw IllegalStateException("not found") },
            now = { Instant.fromEpochMilliseconds(0) },
        )
        repeat(100) { i ->
            assertFailsWith<IllegalStateException> { cache.get("bogus-$i") }
        }
        assertEquals(0, cache.lockCount(), "Failed loads must not pin Mutex instances per key")
    }

    @Test
    fun successfulLoad_retainsLockForFutureRefreshes() = runBlocking {
        val cache = TtlCache<String>(
            loader = { "v1" },
            now = { Instant.fromEpochMilliseconds(0) },
        )
        cache.get("en")
        assertEquals(1, cache.lockCount(), "Successful load must keep its Mutex so refreshes coalesce")
    }

    @Test
    fun differentKeys_doNotBlockEachOther() = runBlocking {
        val cache = TtlCache<String>(
            loader = { key ->
                if (key == "en") delay(100)
                "v-$key"
            },
            now = { Instant.fromEpochMilliseconds(0) },
        )
        val results = coroutineScope {
            val enJob = async { cache.get("en") }
            val ruJob = async { cache.get("ru") }
            listOf(enJob, ruJob).awaitAll()
        }
        assertEquals("v-en", results[0])
        assertEquals("v-ru", results[1])
    }

    @Test
    fun peekFresh_returnsNullWhenAbsent() = runBlocking {
        val cache = TtlCache<String>(
            loader = { "v1" },
            now = { Instant.fromEpochMilliseconds(0) },
        )
        assertNull(cache.peekFresh("en"), "peekFresh must return null when no entry is cached")
    }

    @Test
    fun peekFresh_returnsValueWhenFresh() = runBlocking {
        val cache = TtlCache<String>(
            loader = { "v1" },
            now = { Instant.fromEpochMilliseconds(0) },
        )
        cache.get("en")
        assertEquals("v1", cache.peekFresh("en"))
    }

    @Test
    fun peekFresh_returnsNullWhenStale() = runBlocking {
        var clock = Instant.fromEpochMilliseconds(0)
        val cache = TtlCache<String>(
            loader = { "v1" },
            now = { clock },
        )
        cache.get("en")
        clock = Instant.fromEpochMilliseconds(25.hours.inWholeMilliseconds)
        assertNull(cache.peekFresh("en"), "peekFresh must return null once the entry is past TTL")
    }

    @Test
    fun peekFresh_doesNotTriggerLoader() = runBlocking {
        val calls = AtomicInteger(0)
        val cache = TtlCache<String>(
            loader = { calls.incrementAndGet(); "v1" },
            now = { Instant.fromEpochMilliseconds(0) },
        )
        cache.peekFresh("en")
        cache.peekFresh("en")
        cache.peekFresh("en")
        assertEquals(0, calls.get(), "peekFresh must never invoke the loader")
    }
}
