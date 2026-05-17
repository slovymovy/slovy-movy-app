package com.slovy.slovymovyapp.util

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.milliseconds

/**
 * Tests for [CoroutineReadWriteLock] focused on the property that motivated its introduction:
 * the write side must drain in-flight readers before the writer's block runs, so a close +
 * delete sequence cannot race with active queries.
 */
class CoroutineReadWriteLockTest {

    @Test
    fun read_block_runs_and_releases() = runBlocking {
        val lock = CoroutineReadWriteLock()
        var executed = false
        lock.read { executed = true }
        assertTrue(executed, "read block must execute")

        // After release, a writer must be able to acquire without blocking.
        withTimeout(1_000) {
            lock.write { /* no-op */ }
        }
    }

    @Test
    fun multiple_readers_run_concurrently() = runBlocking(Dispatchers.Default) {
        val lock = CoroutineReadWriteLock()
        val both = CompletableDeferred<Unit>()
        val readersHolding = kotlinx.atomicfu.atomic(0)

        val a = async {
            lock.read {
                readersHolding.incrementAndGet()
                both.await()
            }
        }
        val b = async {
            lock.read {
                readersHolding.incrementAndGet()
                both.await()
            }
        }

        // Wait until both readers are inside the read block; if they were serialized, the second
        // could not have incremented before the first released.
        withTimeout(1_000) {
            while (readersHolding.value < 2) {
                delay(5)
            }
        }
        assertEquals(2, readersHolding.value, "both readers must be active concurrently")
        both.complete(Unit)
        a.await()
        b.await()
    }

    @Test
    fun writer_waits_for_in_flight_reader_to_release() = runBlocking(Dispatchers.Default) {
        val lock = CoroutineReadWriteLock()
        val readerEntered = CompletableDeferred<Unit>()
        val releaseReader = CompletableDeferred<Unit>()
        val writerObservedNoReaders = CompletableDeferred<Unit>()

        val reader = launch {
            lock.read {
                readerEntered.complete(Unit)
                releaseReader.await()
            }
        }

        readerEntered.await()

        val writer = launch {
            lock.write {
                // If the writer entered before the reader released, this would race with the
                // reader's lease. The contract is that the writer must wait.
                writerObservedNoReaders.complete(Unit)
            }
        }

        // Give the writer a chance to (incorrectly) proceed.
        delay(50)
        assertFalse(
            writerObservedNoReaders.isCompleted,
            "writer must not enter while a reader holds the read lock",
        )

        releaseReader.complete(Unit)

        withTimeout(1_000) { writerObservedNoReaders.await() }
        reader.join()
        writer.join()
    }

    @Test
    fun new_readers_wait_while_writer_is_active() = runBlocking(Dispatchers.Default) {
        val lock = CoroutineReadWriteLock()
        val writerEntered = CompletableDeferred<Unit>()
        val releaseWriter = CompletableDeferred<Unit>()
        val readerObserved = CompletableDeferred<Unit>()

        val writer = launch {
            lock.write {
                writerEntered.complete(Unit)
                releaseWriter.await()
            }
        }

        writerEntered.await()

        val reader = launch {
            lock.read {
                readerObserved.complete(Unit)
            }
        }

        delay(50)
        assertFalse(
            readerObserved.isCompleted,
            "reader must not enter while a writer holds the write lock",
        )

        releaseWriter.complete(Unit)
        withTimeout(1_000) { readerObserved.await() }
        writer.join()
        reader.join()
    }

    @Test
    fun nested_read_can_enter_while_writer_waits_for_outer_read() = runBlocking(Dispatchers.Default) {
        val lock = CoroutineReadWriteLock()
        val writerStarted = CompletableDeferred<Unit>()
        val writerEntered = CompletableDeferred<Unit>()
        val releaseWriter = CompletableDeferred<Unit>()
        lateinit var writer: Job
        var nestedReadRan = false

        lock.read {
            writer = launch {
                writerStarted.complete(Unit)
                lock.write {
                    writerEntered.complete(Unit)
                    releaseWriter.await()
                }
            }

            writerStarted.await()
            delay(50)
            assertFalse(
                writerEntered.isCompleted,
                "writer must wait for the outer read before entering",
            )

            withTimeout(1_000) {
                lock.read {
                    nestedReadRan = true
                }
            }
            assertTrue(nestedReadRan, "nested read must not block behind the queued writer")
            assertFalse(
                writerEntered.isCompleted,
                "nested read must not release the outer read early",
            )
        }

        withTimeout(1_000) { writerEntered.await() }
        releaseWriter.complete(Unit)
        writer.join()
    }

    @Test
    fun write_wait_times_out_and_clears_queued_writer_state() = runBlocking(Dispatchers.Default) {
        val lock = CoroutineReadWriteLock(50.milliseconds)
        val readerEntered = CompletableDeferred<Unit>()
        val releaseReader = CompletableDeferred<Unit>()

        val reader = launch {
            lock.read {
                readerEntered.complete(Unit)
                releaseReader.await()
            }
        }
        readerEntered.await()

        assertFailsWith<CoroutineReadWriteLockTimeoutException> {
            lock.write { /* no-op */ }
        }

        val laterReadEntered = CompletableDeferred<Unit>()
        val laterReader = launch {
            lock.read {
                laterReadEntered.complete(Unit)
            }
        }
        withTimeout(1_000) { laterReadEntered.await() }

        releaseReader.complete(Unit)
        reader.join()
        laterReader.join()
    }

    @Test
    fun read_wait_times_out_while_writer_is_active() = runBlocking(Dispatchers.Default) {
        val lock = CoroutineReadWriteLock(50.milliseconds)
        val writerEntered = CompletableDeferred<Unit>()
        val releaseWriter = CompletableDeferred<Unit>()

        val writer = launch {
            lock.write {
                writerEntered.complete(Unit)
                releaseWriter.await()
            }
        }
        writerEntered.await()

        assertFailsWith<CoroutineReadWriteLockTimeoutException> {
            lock.read { /* no-op */ }
        }

        releaseWriter.complete(Unit)
        writer.join()
    }

    @Test
    fun read_lease_close_releases_the_lock() = runBlocking(Dispatchers.Default) {
        val lock = CoroutineReadWriteLock()
        val writerEntered = CompletableDeferred<Unit>()

        lock.lease().use {
            // Inside the lease — the writer launched below must wait.
            val writer = launch {
                lock.write { writerEntered.complete(Unit) }
            }
            delay(50)
            assertFalse(
                writerEntered.isCompleted,
                "writer must wait while .use lease is held",
            )
            writer  // keep ref so the launch isn't GC'd
        }
        // After .use closes, the writer should be able to enter.
        withTimeout(1_000) { writerEntered.await() }
    }

    @Test
    fun release_without_acquire_throws() {
        val lock = CoroutineReadWriteLock()
        try {
            lock.releaseRead()
            fail("expected IllegalStateException")
        } catch (_: IllegalStateException) {
            // expected
        }
    }
}
