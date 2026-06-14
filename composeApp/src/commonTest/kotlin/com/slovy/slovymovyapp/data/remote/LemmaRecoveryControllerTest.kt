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
        val controller = LemmaRecoveryController(
            recovery = recovery(
                onItemsLoaded = { recoveryRuns++ },
                onFetch = {
                    started.complete(Unit)
                    releaseFetch.await()
                },
            ),
            acquireKeepAlive = keepAlive::acquire,
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
        val controller = LemmaRecoveryController(
            recovery = recovery(onItemsLoaded = { recoveryRuns++ }),
            acquireKeepAlive = keepAlive::acquire,
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
        val controller = LemmaRecoveryController(
            recovery = recovery(
                onFetch = {
                    started.complete(Unit)
                    releaseFetch.await()
                },
            ),
            acquireKeepAlive = keepAlive::acquire,
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
        val controller = LemmaRecoveryController(
            recovery = recovery(
                onFetch = {
                    started.complete(Unit)
                    releaseFetch.await()
                },
            ),
            acquireKeepAlive = keepAlive::acquire,
        )

        val controllerJob = controller.ensureStarted()
        withTimeout(1.seconds.inWholeMilliseconds) { started.await() }
        controller.close()
        withTimeout(1.seconds.inWholeMilliseconds) { controllerJob.join() }

        assertTrue(controllerJob.isCancelled, "Closing the controller should cancel in-flight recovery")
        assertEquals(1, keepAlive.acquired)
        assertEquals(1, keepAlive.released)
    }

    private fun recovery(
        onItemsLoaded: () -> Unit = {},
        onFetch: suspend () -> Unit = {},
    ): LemmaRecovery {
        return LemmaRecovery(
            itemsProvider = {
                onItemsLoaded()
                listOf(RecoverableSense(Language.ENGLISH, "test", senseId))
            },
            hasDownloadedDictionary = { language -> language == Language.ENGLISH },
            downloadedLemmasNeedingRecovery = { _, lemmas -> lemmas },
            downloadedSensesNeedingTranslationRecovery = { _, _, _ -> emptySet() },
            translationTargetsProvider = { listOf(Language.RUSSIAN) },
            fetchLemma = { _, _, _ -> onFetch() },
        )
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
