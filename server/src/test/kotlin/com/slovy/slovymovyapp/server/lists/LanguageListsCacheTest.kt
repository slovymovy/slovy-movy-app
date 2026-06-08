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
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class LanguageListsCacheTest {

    private fun bundle(version: String) = LanguageListsBundle(version = version, lists = emptyList())

    @Test
    fun firstCall_invokesLoader() = runBlocking {
        val calls = AtomicInteger(0)
        val cache = LanguageListsCache(
            loader = { calls.incrementAndGet(); bundle("v1") },
            now = { Instant.fromEpochMilliseconds(0) },
        )
        assertEquals("v1", cache.get("en").version)
        assertEquals(1, calls.get())
    }

    @Test
    fun withinTtl_returnsCached() = runBlocking {
        val calls = AtomicInteger(0)
        var clock = Instant.fromEpochMilliseconds(0)
        val cache = LanguageListsCache(
            loader = { calls.incrementAndGet(); bundle("v1") },
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
        val cache = LanguageListsCache(
            loader = { bundle("v${calls.incrementAndGet()}") },
            now = { clock },
        )
        cache.get("en")
        clock = Instant.fromEpochMilliseconds(25.hours.inWholeMilliseconds)
        val second = cache.get("en")
        assertEquals(2, calls.get(), "Loader must be re-invoked after TTL")
        assertEquals("v2", second.version)
    }

    @Test
    fun concurrentCalls_sameLang_coalesceToOneLoad() = runBlocking {
        val calls = AtomicInteger(0)
        val cache = LanguageListsCache(
            loader = {
                calls.incrementAndGet()
                delay(50)
                bundle("v1")
            },
            now = { Instant.fromEpochMilliseconds(0) },
        )
        coroutineScope {
            repeat(10) { launch { cache.get("en") } }
        }
        assertEquals(1, calls.get(), "Concurrent callers for the same language must coalesce into one upstream load")
    }

    @Test
    fun failedLoad_isNotCached() = runBlocking {
        val calls = AtomicInteger(0)
        val cache = LanguageListsCache(
            loader = {
                if (calls.incrementAndGet() == 1) throw IllegalStateException("boom")
                bundle("v-ok")
            },
            now = { Instant.fromEpochMilliseconds(0) },
        )
        assertFailsWith<IllegalStateException> { cache.get("en") }
        assertEquals("v-ok", cache.get("en").version)
        assertEquals(2, calls.get(), "Failure must not be cached")
    }

    @Test
    fun differentLanguages_doNotBlockEachOther() = runBlocking {
        val cache = LanguageListsCache(
            loader = { lang ->
                if (lang == "en") delay(100)
                bundle("v-$lang")
            },
            now = { Instant.fromEpochMilliseconds(0) },
        )
        val (en, ru) = coroutineScope {
            val enJob = async { cache.get("en") }
            val ruJob = async { cache.get("ru") }
            listOf(enJob, ruJob).awaitAll()
        }.let { it[0] to it[1] }
        assertEquals("v-en", en.version)
        assertEquals("v-ru", ru.version)
    }
}
