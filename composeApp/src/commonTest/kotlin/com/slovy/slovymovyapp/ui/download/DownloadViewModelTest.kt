package com.slovy.slovymovyapp.ui.download

import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.recovery.RecoverableSense
import com.slovy.slovymovyapp.data.remote.DownloadCoordinator
import com.slovy.slovymovyapp.data.remote.LemmaRecovery
import com.slovy.slovymovyapp.data.remote.LemmaRecoveryController
import com.slovy.slovymovyapp.data.remote.ProcessKeepAlive
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.yield
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Covers the finalizing step of the download screen: the user may leave it early, and lemma
 * recovery has to keep running in the background when they do.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DownloadViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    private data class TestSense(
        override val language: Language,
        override val lemma: String,
        override val senseId: String,
    ) : RecoverableSense

    private class TestKeepAlive : ProcessKeepAlive {
        var released = false
            private set

        override fun release() {
            released = true
        }
    }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun skippingFinalizingFinishesOnceAndKeepsRecoveryRunning() = runTest(dispatcher) {
        val coordinator = DownloadCoordinator(CoroutineScope(dispatcher))
        val fetchStarted = CompletableDeferred<Unit>()
        val releaseFetch = CompletableDeferred<Unit>()
        val keepAlive = TestKeepAlive()
        val controller = LemmaRecoveryController(
            recovery = recoveryBlockedOnFetch(fetchStarted, releaseFetch),
            acquireKeepAlive = { keepAlive },
        )
        var successCount = 0

        val viewModel = DownloadViewModel(
            downloadCoordinator = coordinator,
            downloadKey = "test_skip",
            download = { _, _ -> },
            finalize = { onRecoveryProgress, _ -> controller.runToCompletion(onRecoveryProgress) },
            onSuccess = { successCount++ },
            onCancel = { },
            onError = { },
        )

        fetchStarted.await()
        val state = viewModel.state
        assertTrue(state is DownloadUiState.Finalizing, "Expected finalizing state but was $state")
        assertEquals(1, state.recovery?.total, "Recovery progress must reach the screen before skipping")

        viewModel.skipFinalizing()
        // The completion runs in its own coroutine; let it reach onSuccess before asserting.
        yield()

        assertEquals(1, successCount, "Skipping must complete the screen exactly once")
        assertTrue(
            controller.ensureStarted().isActive,
            "Recovery must keep running in the background after a skip",
        )
        assertFalse(keepAlive.released, "The recovery keep-alive must outlive the skipped screen")

        viewModel.skipFinalizing()
        yield()
        assertEquals(1, successCount, "A second skip must not complete the screen again")

        releaseFetch.complete(Unit)
        controller.ensureStarted().join()
        assertTrue(keepAlive.released, "The recovery keep-alive must be released once recovery ends")
        controller.close()
        coordinator.close()
    }

    @Test
    fun finalizingWithoutSkipCompletesAfterTheCountdown() = runTest(dispatcher) {
        val coordinator = DownloadCoordinator(CoroutineScope(dispatcher))
        var successCount = 0
        var finalizeCalls = 0

        val viewModel = DownloadViewModel(
            downloadCoordinator = coordinator,
            downloadKey = "test_no_skip",
            download = { _, _ -> },
            finalize = { _, _ -> finalizeCalls++ },
            onSuccess = { successCount++ },
            onCancel = { },
            onError = { },
        )

        testScheduler.advanceUntilIdle()

        assertEquals(1, finalizeCalls, "Finalizing must run once after the download completes")
        assertEquals(1, successCount, "The screen must complete once after the countdown")

        viewModel.skipFinalizing()
        assertEquals(1, successCount, "Skipping after completion must do nothing")
        coordinator.close()
    }

    /**
     * A recovery that reaches exactly one lemma fetch and then blocks, so a test can observe the
     * finalizing screen while recovery is genuinely in flight.
     */
    private fun recoveryBlockedOnFetch(
        fetchStarted: CompletableDeferred<Unit>,
        releaseFetch: CompletableDeferred<Unit>,
    ): LemmaRecovery = LemmaRecovery(
        itemsProvider = { listOf(TestSense(Language.ENGLISH, "word", "sense-1")) },
        hasDownloadedDictionary = { true },
        downloadedLemmasNeedingRecovery = { _, lemmas -> lemmas },
        downloadedSensesNeedingTranslationRecovery = { _, _, _ -> emptySet() },
        translationTargetsProvider = { emptyList() },
        fetchLemma = { _, _, _ ->
            fetchStarted.complete(Unit)
            releaseFetch.await()
        },
        resolveSenses = { _, _, _ -> emptyMap() },
    )
}
