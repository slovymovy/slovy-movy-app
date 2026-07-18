package com.slovy.slovymovyapp.speech

import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.settings.Setting
import com.slovy.slovymovyapp.data.settings.SettingsRepository
import com.slovy.slovymovyapp.test.BaseTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

open class RowAudioControllerTest : BaseTest() {

    private companion object {
        const val SENSE_A = "sense-a"
        const val SENSE_B = "sense-b"
        const val LEMMA_A = "hello"
        const val LEMMA_B = "world"
    }

    private fun settingsRepository() = SettingsRepository(testAppDatabaseHolder().database)

    @BeforeTest
    fun clearVoiceSettings() {
        runBlocking {
            val repo = settingsRepository()
            repo.deleteById(Setting.Name.ENABLED_VOICES)
            repo.deleteById(Setting.Name.VOICE_SETUP_SHOWN)
        }
    }

    private fun ttsLanguage(language: Language, isAvailable: Boolean = true) =
        Text2SpeechLanguage(language = language, isAvailable = isAvailable, missingData = false)

    private fun voice(id: String, quality: VoiceQuality = VoiceQuality.BEST) = Text2SpeechVoice(
        id = id,
        name = id,
        language = Language.ENGLISH,
        localeTag = "en-US",
        quality = quality,
        networkConnectionRequired = false,
        enabledByDefault = true,
    )

    private fun fakeWithEnglishVoices(vararg voices: Text2SpeechVoice) = FakeSpeechPlayer().apply {
        languages = listOf(ttsLanguage(Language.ENGLISH))
        voicesByLanguage = mapOf(Language.ENGLISH to voices.toList())
    }

    /**
     * Runs [block] with a controller whose scope lives on the runBlocking event loop, mirroring the
     * single-threaded viewModelScope the production controller runs on.
     */
    private suspend fun withController(
        fake: FakeSpeechPlayer,
        block: suspend (RowAudioController) -> Unit,
    ) {
        val scope = CoroutineScope(currentCoroutineContext() + SupervisorJob())
        val controller = RowAudioController(
            speechPlayer = fake,
            voiceFilterHelper = VoiceFilterHelper(settingsRepository()),
            scope = scope,
            analyticsSource = "test",
        )
        try {
            block(controller)
        } finally {
            scope.cancel()
        }
    }

    private suspend fun awaitUntil(message: String, condition: suspend () -> Boolean) {
        val completed = withTimeoutOrNull(5.seconds) {
            while (!condition()) delay(5)
        }
        assertNotNull(completed, "Timed out waiting until: $message")
    }

    private suspend fun driveToPlaying(controller: RowAudioController, fake: FakeSpeechPlayer) {
        controller.toggle(SENSE_A, LEMMA_A, Language.ENGLISH)
        awaitUntil("utterance handed to the engine") { fake.spokenTexts.isNotEmpty() }
        fake.emitStatus(TTSStatus.SPEAKING)
        assertEquals(SENSE_A, controller.uiState.playingSenseId, "Row should be playing after SPEAKING")
    }

    @Test
    fun playAdoptsSpeakingAndReleasesOnIdle() = runBlocking {
        val fake = fakeWithEnglishVoices(voice("v1"))
        withController(fake) { controller ->
            controller.toggle(SENSE_A, LEMMA_A, Language.ENGLISH)
            assertEquals(
                SENSE_A,
                controller.uiState.preparingSenseId,
                "Row should show the spinner immediately after the tap"
            )

            awaitUntil("utterance handed to the engine") { fake.spokenTexts.isNotEmpty() }
            assertEquals(listOf(LEMMA_A), fake.spokenTexts, "Exactly the tapped lemma should be spoken")
            assertEquals(1, fake.setVoices.size, "A voice should be selected before speaking")
            assertEquals(
                SENSE_A,
                controller.uiState.preparingSenseId,
                "Row should keep the spinner until the engine reports SPEAKING"
            )

            fake.emitStatus(TTSStatus.SPEAKING)
            assertEquals(SENSE_A, controller.uiState.playingSenseId, "SPEAKING should flip the row to playing")
            assertNull(controller.uiState.preparingSenseId, "Spinner should clear once playing")

            fake.emitStatus(TTSStatus.IDLE)
            assertNull(controller.uiState.playingSenseId, "IDLE should release the playing row")
        }
    }

    @Test
    fun secondTapOnActiveRowStops() = runBlocking {
        val fake = fakeWithEnglishVoices(voice("v1"))
        withController(fake) { controller ->
            driveToPlaying(controller, fake)

            controller.toggle(SENSE_A, LEMMA_A, Language.ENGLISH)
            assertEquals(1, fake.stopCount, "Tapping the playing row should stop the engine")
            assertNull(controller.uiState.playingSenseId, "Stop should clear the playing row")
            assertNull(controller.uiState.preparingSenseId, "Stop should clear the spinner")
        }
    }

    @Test
    fun staleVoiceLoadCannotHijackNewerRequest() = runBlocking {
        val fake = fakeWithEnglishVoices(voice("v1"))
        val gate = CompletableDeferred<Unit>()
        fake.voiceLoadGate = gate
        withController(fake) { controller ->
            controller.toggle(SENSE_A, LEMMA_A, Language.ENGLISH)
            assertEquals(SENSE_A, controller.uiState.preparingSenseId, "First tap should show row A preparing")

            // Re-tap on another row while row A's voice load is still in flight.
            controller.toggle(SENSE_B, LEMMA_B, Language.ENGLISH)
            assertEquals(SENSE_B, controller.uiState.preparingSenseId, "Second tap should supersede row A")

            gate.complete(Unit)
            awaitUntil("row B's utterance handed to the engine") { fake.spokenTexts.isNotEmpty() }
            delay(100) // Give the superseded row A coroutine time to (wrongly) speak if it could.
            assertEquals(listOf(LEMMA_B), fake.spokenTexts, "Only the newest request may speak")
            assertEquals(SENSE_B, controller.uiState.preparingSenseId, "Row B should still own the spinner")
        }
    }

    @Test
    fun stopDuringVoiceLoadPreventsLateSpeak() = runBlocking {
        val fake = fakeWithEnglishVoices(voice("v1"))
        val gate = CompletableDeferred<Unit>()
        fake.voiceLoadGate = gate
        withController(fake) { controller ->
            controller.toggle(SENSE_A, LEMMA_A, Language.ENGLISH)
            controller.stop()
            assertNull(controller.uiState.preparingSenseId, "Stop should clear the spinner immediately")

            gate.complete(Unit)
            delay(100) // Give the stale load time to (wrongly) speak if it could.
            assertTrue(fake.spokenTexts.isEmpty(), "A load finishing after stop must not speak")
            assertNull(controller.uiState.preparingSenseId, "State must stay idle after the stale load lands")
            assertNull(controller.uiState.playingSenseId, "State must stay idle after the stale load lands")
        }
    }

    @Test
    fun unrelatedIdleWhileLoadingKeepsSpinner() = runBlocking {
        val fake = fakeWithEnglishVoices(voice("v1"))
        val gate = CompletableDeferred<Unit>()
        fake.voiceLoadGate = gate
        withController(fake) { controller ->
            controller.toggle(SENSE_A, LEMMA_A, Language.ENGLISH)
            assertEquals(SENSE_A, controller.uiState.preparingSenseId, "Row A should be preparing")

            // Another screen's utterance finishing must not kill the spinner of a loading row.
            fake.emitStatus(TTSStatus.IDLE)
            assertEquals(SENSE_A, controller.uiState.preparingSenseId, "Unrelated IDLE must not clear the spinner")

            gate.complete(Unit)
            awaitUntil("utterance handed to the engine") { fake.spokenTexts.isNotEmpty() }
        }
    }

    @Test
    fun foreignSpeakingWhilePlayingReleasesRowWithoutStopping() = runBlocking {
        val fake = fakeWithEnglishVoices(voice("v1"))
        withController(fake) { controller ->
            driveToPlaying(controller, fake)

            // Another feature preempts the shared engine; its SPEAKING arrives with no IDLE for our
            // flushed utterance.
            fake.emitStatus(TTSStatus.SPEAKING)
            assertNull(controller.uiState.playingSenseId, "Preempted row must release its playing state")
            assertEquals(0, fake.stopCount, "Releasing the row must not stop the other feature's audio")
        }
    }

    @Test
    fun foreignSpeakingWhileIdleIsIgnored() = runBlocking {
        val fake = fakeWithEnglishVoices(voice("v1"))
        withController(fake) { controller ->
            driveToPlaying(controller, fake)
            fake.emitStatus(TTSStatus.IDLE)

            // Audio started by another screen while no row is active must not light up any row.
            fake.emitStatus(TTSStatus.SPEAKING)
            assertNull(controller.uiState.playingSenseId, "Foreign SPEAKING must not be adopted while idle")
            assertNull(controller.uiState.preparingSenseId, "Foreign SPEAKING must not start a spinner")
        }
    }

    @Test
    fun mediumOnlyVoicesRaiseSetupSheetAndLaterPlays() = runBlocking {
        val fake = fakeWithEnglishVoices(voice("v1", quality = VoiceQuality.MEDIUM))
        withController(fake) { controller ->
            controller.toggle(SENSE_A, LEMMA_A, Language.ENGLISH)
            awaitUntil("voice setup sheet requested") { controller.uiState.voiceSetupLanguage != null }
            assertEquals(Language.ENGLISH, controller.uiState.voiceSetupLanguage)
            assertNull(controller.uiState.preparingSenseId, "Spinner should clear while the sheet is up")
            assertTrue(fake.spokenTexts.isEmpty(), "Nothing should be spoken before the sheet is answered")

            controller.dismissVoiceSetupAndPlay()
            assertNull(controller.uiState.voiceSetupLanguage, "\"Later\" should close the sheet")
            awaitUntil("interrupted request resumes") { fake.spokenTexts.isNotEmpty() }
            assertEquals(listOf(LEMMA_A), fake.spokenTexts, "\"Later\" should play the interrupted request")

            awaitUntil("setup marked as shown") {
                VoiceFilterHelper(settingsRepository()).isVoiceSetupShown(Language.ENGLISH)
            }
        }
    }

    @Test
    fun mediumOnlyVoicesSkipSheetOnceShown() = runBlocking {
        val fake = fakeWithEnglishVoices(voice("v1", quality = VoiceQuality.MEDIUM))
        VoiceFilterHelper(settingsRepository()).markVoiceSetupShown(Language.ENGLISH)
        withController(fake) { controller ->
            controller.toggle(SENSE_A, LEMMA_A, Language.ENGLISH)
            awaitUntil("utterance handed to the engine") { fake.spokenTexts.isNotEmpty() }
            assertNull(controller.uiState.voiceSetupLanguage, "Sheet must not reappear once marked shown")
        }
    }

    @Test
    fun dismissVoiceSetupDoesNotPlay() = runBlocking {
        val fake = fakeWithEnglishVoices(voice("v1", quality = VoiceQuality.MEDIUM))
        withController(fake) { controller ->
            controller.toggle(SENSE_A, LEMMA_A, Language.ENGLISH)
            awaitUntil("voice setup sheet requested") { controller.uiState.voiceSetupLanguage != null }

            controller.dismissVoiceSetup()
            assertNull(controller.uiState.voiceSetupLanguage, "Dismiss should close the sheet")
            delay(100) // Give a wrongly-resumed request time to speak if it could.
            assertTrue(fake.spokenTexts.isEmpty(), "Dismiss must not resume the interrupted request")
        }
    }

    @Test
    fun openVoiceSettingsClosesSheetAndOpensSettings() = runBlocking {
        val fake = fakeWithEnglishVoices(voice("v1", quality = VoiceQuality.MEDIUM))
        withController(fake) { controller ->
            controller.toggle(SENSE_A, LEMMA_A, Language.ENGLISH)
            awaitUntil("voice setup sheet requested") { controller.uiState.voiceSetupLanguage != null }

            controller.openVoiceSettings()
            assertNull(controller.uiState.voiceSetupLanguage, "Opening settings should close the sheet")
            assertEquals(1, fake.openSettingsCount, "Platform voice settings should open")
            assertTrue(fake.spokenTexts.isEmpty(), "Opening settings must not play")
        }
    }

    @Test
    fun consecutivePlaysRotateVoices() = runBlocking {
        val fake = fakeWithEnglishVoices(voice("v1"), voice("v2"))
        withController(fake) { controller ->
            repeat(3) { index ->
                controller.toggle(SENSE_A, LEMMA_A, Language.ENGLISH)
                awaitUntil("utterance ${index + 1} handed to the engine") { fake.setVoices.size == index + 1 }
                fake.emitStatus(TTSStatus.SPEAKING)
                fake.emitStatus(TTSStatus.IDLE)
            }
            assertEquals(
                listOf(fake.setVoices[0], fake.setVoices[1]).toSet().size,
                2,
                "Consecutive plays should rotate to a different voice"
            )
            assertEquals(
                fake.setVoices[0],
                fake.setVoices[2],
                "Rotation over two voices should cycle back to the first"
            )
        }
    }

    @Test
    fun emptyVoicesResetToIdle() = runBlocking {
        val fake = FakeSpeechPlayer().apply {
            languages = listOf(ttsLanguage(Language.ENGLISH))
            voicesByLanguage = emptyMap()
        }
        withController(fake) { controller ->
            controller.toggle(SENSE_A, LEMMA_A, Language.ENGLISH)
            awaitUntil("spinner cleared after empty voice load") {
                controller.uiState.preparingSenseId == null
            }
            assertTrue(fake.spokenTexts.isEmpty(), "Nothing can be spoken without voices")
        }
    }

    @Test
    fun availabilityHidesUnplayableLanguages() = runBlocking {
        val fake = FakeSpeechPlayer().apply {
            languages = listOf(
                ttsLanguage(Language.ENGLISH),
                ttsLanguage(Language.DUTCH, isAvailable = false),
            )
        }
        withController(fake) { controller ->
            assertTrue(
                controller.uiState.isPlayable(Language.DUTCH),
                "Before availability resolves every language shows the speaker optimistically"
            )

            controller.ensureAvailabilityLoaded()
            awaitUntil("availability resolved") { controller.uiState.availableLanguages != null }
            assertTrue(controller.uiState.isPlayable(Language.ENGLISH), "Available language should be playable")
            assertFalse(controller.uiState.isPlayable(Language.DUTCH), "Unavailable language should hide the speaker")
            assertFalse(controller.uiState.isPlayable(Language.GERMAN), "Unknown language should hide the speaker")
        }
    }

    @Test
    fun disposeStopsAndDetachesListener() = runBlocking {
        val fake = fakeWithEnglishVoices(voice("v1"))
        withController(fake) { controller ->
            driveToPlaying(controller, fake)

            controller.dispose()
            assertEquals(1, fake.stopCount, "Dispose should stop the engine")
            assertNull(controller.uiState.playingSenseId, "Dispose should clear playback state")
            assertFalse(fake.hasStatusListeners, "Dispose should detach the status listener")

            fake.emitStatus(TTSStatus.SPEAKING)
            assertNull(controller.uiState.playingSenseId, "Events after dispose must not resurrect state")
        }
    }
}
