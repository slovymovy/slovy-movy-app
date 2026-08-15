package com.slovy.slovymovyapp.data.remote

import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.recovery.RecoverableSense
import com.slovy.slovymovyapp.test.BaseTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class LemmaRecoveryControllerTest : BaseTest() {
    private val senseId = "00000000-0000-0000-0000-000000000001"

    @Test
    fun ensureStarted_returns_same_job_when_already_running() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val releaseFetch = CompletableDeferred<Unit>()
        var recoveryRuns = 0
        val keepAlive = CountingKeepAliveFactory()
        val controller = controller(
            recovery = recovery(
                onItemsLoaded = { recoveryRuns++ },
                onFetch = {
                    started.complete(Unit)
                    releaseFetch.await()
                },
            ),
            keepAlive = keepAlive,
        )

        val firstJob = controller.ensureStarted()
        val secondJob = controller.ensureStarted()
        withTimeout(1.seconds.inWholeMilliseconds) { started.await() }
        releaseFetch.complete(Unit)
        withTimeout(1.seconds.inWholeMilliseconds) { firstJob.join() }

        assertTrue(firstJob === secondJob, "An in-flight recovery should be reused")
        assertEquals(1, recoveryRuns)
        assertEquals(1, keepAlive.acquired)
        assertEquals(1, keepAlive.released)
    }

    @Test
    fun ensureStarted_starts_new_job_after_previous_completes() = runBlocking {
        var recoveryRuns = 0
        val keepAlive = CountingKeepAliveFactory()
        val controller = controller(
            recovery = recovery(onItemsLoaded = { recoveryRuns++ }),
            keepAlive = keepAlive,
        )

        val firstJob = controller.ensureStarted()
        withTimeout(1.seconds.inWholeMilliseconds) { firstJob.join() }
        val secondJob = controller.ensureStarted()
        withTimeout(1.seconds.inWholeMilliseconds) { secondJob.join() }

        assertTrue(firstJob !== secondJob, "A completed recovery should not be reused")
        assertEquals(2, recoveryRuns)
        assertEquals(2, keepAlive.acquired)
        assertEquals(2, keepAlive.released)
    }

    @Test
    fun caller_cancellation_does_not_cancel_controller_job() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val releaseFetch = CompletableDeferred<Unit>()
        val keepAlive = CountingKeepAliveFactory()
        val controller = controller(
            recovery = recovery(
                onFetch = {
                    started.complete(Unit)
                    releaseFetch.await()
                },
            ),
            keepAlive = keepAlive,
        )
        val callerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val controllerJobDeferred = CompletableDeferred<kotlinx.coroutines.Job>()

        val callerJob = callerScope.launch {
            controllerJobDeferred.complete(controller.ensureStarted())
        }
        val controllerJob = withTimeout(1.seconds.inWholeMilliseconds) { controllerJobDeferred.await() }
        withTimeout(1.seconds.inWholeMilliseconds) { started.await() }
        callerScope.cancel()
        callerJob.cancel()

        assertTrue(controllerJob.isActive, "Controller-owned recovery should survive caller cancellation")
        releaseFetch.complete(Unit)
        withTimeout(1.seconds.inWholeMilliseconds) { controllerJob.join() }
        assertTrue(controllerJob.isCompleted)
        assertEquals(1, keepAlive.acquired)
        assertEquals(1, keepAlive.released)
    }

    @Test
    fun close_cancels_in_flight_job_and_releases_keep_alive() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val releaseFetch = CompletableDeferred<Unit>()
        val keepAlive = CountingKeepAliveFactory()
        val controller = controller(
            recovery = recovery(
                onFetch = {
                    started.complete(Unit)
                    releaseFetch.await()
                },
            ),
            keepAlive = keepAlive,
        )

        val controllerJob = controller.ensureStarted()
        withTimeout(1.seconds.inWholeMilliseconds) { started.await() }
        controller.close()
        withTimeout(1.seconds.inWholeMilliseconds) { controllerJob.join() }

        assertTrue(controllerJob.isCancelled, "Closing the controller should cancel in-flight recovery")
        assertEquals(1, keepAlive.acquired)
        assertEquals(1, keepAlive.released)
    }

    @Test
    fun pending_marker_is_cleared_when_the_pass_recovers_everything() = runBlocking {
        val marker = FakePendingMarker(pending = true)
        val controller = controller(
            recovery = recovery(),
            keepAlive = CountingKeepAliveFactory(),
            marker = marker,
        )

        withTimeout(1.seconds.inWholeMilliseconds) { controller.ensureStarted().join() }

        assertEquals(1, marker.cleared, "A complete pass must retire the marker it ran for")
        assertFalse(marker.pending)
    }

    @Test
    fun pending_marker_survives_a_pass_that_started_before_it_was_set() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val releaseFetch = CompletableDeferred<Unit>()
        val marker = FakePendingMarker(pending = false)
        val controller = controller(
            recovery = recovery(
                onFetch = {
                    started.complete(Unit)
                    releaseFetch.await()
                },
            ),
            keepAlive = CountingKeepAliveFactory(),
            marker = marker,
        )

        val job = controller.ensureStarted()
        withTimeout(1.seconds.inWholeMilliseconds) { started.await() }
        // The user adds a translation language while the pass is already grouping lemmas for the
        // previous targets, so this pass cannot have covered the new language.
        marker.pending = true
        releaseFetch.complete(Unit)
        withTimeout(1.seconds.inWholeMilliseconds) { job.join() }

        assertEquals(0, marker.cleared, "A pass that predates the marker must leave it for the next run")
        assertTrue(marker.pending)
    }

    @Test
    fun pending_marker_survives_a_failed_lemma() = runBlocking {
        val marker = FakePendingMarker(pending = true)
        val controller = controller(
            recovery = recovery(onFetch = { throw RuntimeException("offline") }),
            keepAlive = CountingKeepAliveFactory(),
            marker = marker,
        )

        withTimeout(1.seconds.inWholeMilliseconds) { controller.ensureStarted().join() }

        assertEquals(0, marker.cleared, "An incomplete pass must keep the marker for the next start")
        assertTrue(marker.pending)
    }

    private fun controller(
        recovery: LemmaRecovery,
        keepAlive: CountingKeepAliveFactory,
        marker: FakePendingMarker = FakePendingMarker(),
    ): LemmaRecoveryController {
        return LemmaRecoveryController(
            recovery = recovery,
            acquireKeepAlive = keepAlive::acquire,
            isRecoveryPending = { marker.pending },
            clearRecoveryPending = { marker.clear() },
        )
    }

    private fun recovery(
        onItemsLoaded: () -> Unit = {},
        onFetch: suspend () -> Unit = {},
    ): LemmaRecovery {
        return LemmaRecovery(
            itemsProvider = {
                onItemsLoaded()
                listOf(TestRecoverableSense(Language.ENGLISH, "test", senseId))
            },
            hasDownloadedDictionary = { language -> language == Language.ENGLISH },
            downloadedLemmasNeedingRecovery = { _, lemmas -> lemmas },
            downloadedSensesNeedingTranslationRecovery = { _, _, _ -> emptySet() },
            translationTargetsProvider = { listOf(Language.RUSSIAN) },
            fetchLemma = { _, _, _ -> onFetch() },
            resolveSenses = { _, _, _ -> emptyMap() },
        )
    }

    private data class TestRecoverableSense(
        override val language: Language,
        override val lemma: String,
        override val senseId: String,
    ) : RecoverableSense

    /** Stands in for the `PENDING_LEMMA_RECOVERY` setting the controller reads and retires. */
    private class FakePendingMarker(var pending: Boolean = false) {
        var cleared = 0
            private set

        fun clear() {
            pending = false
            cleared++
        }
    }

    private class CountingKeepAliveFactory {
        var acquired = 0
            private set
        var released = 0
            private set

        fun acquire(): ProcessKeepAlive {
            acquired++
            return object : ProcessKeepAlive {
                private var isReleased = false

                override fun release() {
                    if (!isReleased) {
                        isReleased = true
                        released++
                    }
                }
            }
        }
    }
}
