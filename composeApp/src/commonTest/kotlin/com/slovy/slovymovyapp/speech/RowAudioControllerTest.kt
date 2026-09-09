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

        /** As stored: examples carry <w> markup around the lemma they illustrate. */
        const val EXAMPLE_MARKUP = "Say <w>hello</w> to the world."
        const val EXAMPLE_SPOKEN = "Say hello to the world."
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
        controller.toggleLemma(SENSE_A, LEMMA_A, Language.ENGLISH)
        awaitUntil("utterance handed to the engine") { fake.spokenTexts.isNotEmpty() }
        fake.emitStatus(TTSStatus.SPEAKING)
        assertEquals(SENSE_A, controller.uiState.playingKey, "Row should be playing after SPEAKING")
    }

    @Test
    fun playAdoptsSpeakingAndReleasesOnIdle() = runBlocking {
        val fake = fakeWithEnglishVoices(voice("v1"))
        withController(fake) { controller ->
            controller.toggleLemma(SENSE_A, LEMMA_A, Language.ENGLISH)
            assertEquals(
                SENSE_A,
                controller.uiState.preparingKey,
                "Row should show the spinner immediately after the tap"
            )

            awaitUntil("utterance handed to the engine") { fake.spokenTexts.isNotEmpty() }
            assertEquals(listOf(LEMMA_A), fake.spokenTexts, "Exactly the tapped lemma should be spoken")
            assertEquals(1, fake.setVoices.size, "A voice should be selected before speaking")
            assertEquals(
                SENSE_A,
                controller.uiState.preparingKey,
                "Row should keep the spinner until the engine reports SPEAKING"
            )

            fake.emitStatus(TTSStatus.SPEAKING)
            assertEquals(SENSE_A, controller.uiState.playingKey, "SPEAKING should flip the row to playing")
            assertNull(controller.uiState.preparingKey, "Spinner should clear once playing")

            fake.emitStatus(TTSStatus.IDLE)
            assertNull(controller.uiState.playingKey, "IDLE should release the playing row")
        }
    }

    @Test
    fun secondTapOnActiveRowStops() = runBlocking {
        val fake = fakeWithEnglishVoices(voice("v1"))
        withController(fake) { controller ->
            driveToPlaying(controller, fake)
            val stopsBefore = fake.stopCount

            controller.toggleLemma(SENSE_A, LEMMA_A, Language.ENGLISH)
            assertEquals(stopsBefore + 1, fake.stopCount, "Tapping the playing row should stop the engine")
            assertNull(controller.uiState.playingKey, "Stop should clear the playing row")
            assertNull(controller.uiState.preparingKey, "Stop should clear the spinner")
        }
    }

    @Test
    fun switchingRowsSilencesCurrentAudioImmediately() = runBlocking {
        val fake = fakeWithEnglishVoices(voice("v1"))
        withController(fake) { controller ->
            driveToPlaying(controller, fake)
            val stopsBefore = fake.stopCount

            // Hold row B's voice load open: row A's audio must already be silenced while B loads,
            // because A's stop control is gone the moment B starts preparing.
            val gate = CompletableDeferred<Unit>()
            fake.voiceLoadGate = gate
            controller.toggleLemma(SENSE_B, LEMMA_B, Language.ENGLISH)
            assertEquals(stopsBefore + 1, fake.stopCount, "Switching rows must stop the engine before loading voices")
            assertEquals(SENSE_B, controller.uiState.preparingKey, "Row B should be preparing")
            assertNull(controller.uiState.playingKey, "Row A should no longer show as playing")

            gate.complete(Unit)
            awaitUntil("row B's utterance handed to the engine") { fake.spokenTexts.contains(LEMMA_B) }
        }
    }

    @Test
    fun staleVoiceLoadCannotHijackNewerRequest() = runBlocking {
        val fake = fakeWithEnglishVoices(voice("v1"))
        val gate = CompletableDeferred<Unit>()
        fake.voiceLoadGate = gate
        withController(fake) { controller ->
            controller.toggleLemma(SENSE_A, LEMMA_A, Language.ENGLISH)
            assertEquals(SENSE_A, controller.uiState.preparingKey, "First tap should show row A preparing")

            // Re-tap on another row while row A's voice load is still in flight.
            controller.toggleLemma(SENSE_B, LEMMA_B, Language.ENGLISH)
            assertEquals(SENSE_B, controller.uiState.preparingKey, "Second tap should supersede row A")

            gate.complete(Unit)
            awaitUntil("row B's utterance handed to the engine") { fake.spokenTexts.isNotEmpty() }
            delay(100) // Give the superseded row A coroutine time to (wrongly) speak if it could.
            assertEquals(listOf(LEMMA_B), fake.spokenTexts, "Only the newest request may speak")
            assertEquals(SENSE_B, controller.uiState.preparingKey, "Row B should still own the spinner")
        }
    }

    @Test
    fun stopDuringVoiceLoadPreventsLateSpeak() = runBlocking {
        val fake = fakeWithEnglishVoices(voice("v1"))
        val gate = CompletableDeferred<Unit>()
        fake.voiceLoadGate = gate
        withController(fake) { controller ->
            controller.toggleLemma(SENSE_A, LEMMA_A, Language.ENGLISH)
            controller.stop()
            assertNull(controller.uiState.preparingKey, "Stop should clear the spinner immediately")

            gate.complete(Unit)
            delay(100) // Give the stale load time to (wrongly) speak if it could.
            assertTrue(fake.spokenTexts.isEmpty(), "A load finishing after stop must not speak")
            assertNull(controller.uiState.preparingKey, "State must stay idle after the stale load lands")
            assertNull(controller.uiState.playingKey, "State must stay idle after the stale load lands")
        }
    }

    @Test
    fun unrelatedIdleWhileLoadingKeepsSpinner() = runBlocking {
        val fake = fakeWithEnglishVoices(voice("v1"))
        val gate = CompletableDeferred<Unit>()
        fake.voiceLoadGate = gate
        withController(fake) { controller ->
            controller.toggleLemma(SENSE_A, LEMMA_A, Language.ENGLISH)
            assertEquals(SENSE_A, controller.uiState.preparingKey, "Row A should be preparing")

            // Another screen's utterance finishing must not kill the spinner of a loading row.
            fake.emitStatus(TTSStatus.IDLE)
            assertEquals(SENSE_A, controller.uiState.preparingKey, "Unrelated IDLE must not clear the spinner")

            gate.complete(Unit)
            awaitUntil("utterance handed to the engine") { fake.spokenTexts.isNotEmpty() }
        }
    }

    @Test
    fun foreignSpeakingWhilePlayingReleasesRowWithoutStopping() = runBlocking {
        val fake = fakeWithEnglishVoices(voice("v1"))
        withController(fake) { controller ->
            driveToPlaying(controller, fake)
            val stopsBefore = fake.stopCount

            // Another feature preempts the shared engine; its SPEAKING arrives with no IDLE for our
            // flushed utterance.
            fake.emitStatus(TTSStatus.SPEAKING)
            assertNull(controller.uiState.playingKey, "Preempted row must release its playing state")
            assertEquals(stopsBefore, fake.stopCount, "Releasing the row must not stop the other feature's audio")
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
            assertNull(controller.uiState.playingKey, "Foreign SPEAKING must not be adopted while idle")
            assertNull(controller.uiState.preparingKey, "Foreign SPEAKING must not start a spinner")
        }
    }

    @Test
    fun mediumOnlyVoicesRaiseSetupSheetAndLaterPlays() = runBlocking {
        val fake = fakeWithEnglishVoices(voice("v1", quality = VoiceQuality.MEDIUM))
        withController(fake) { controller ->
            controller.toggleLemma(SENSE_A, LEMMA_A, Language.ENGLISH)
            awaitUntil("voice setup sheet requested") { controller.uiState.voiceSetupLanguage != null }
            assertEquals(Language.ENGLISH, controller.uiState.voiceSetupLanguage)
            assertNull(controller.uiState.preparingKey, "Spinner should clear while the sheet is up")
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
            controller.toggleLemma(SENSE_A, LEMMA_A, Language.ENGLISH)
            awaitUntil("utterance handed to the engine") { fake.spokenTexts.isNotEmpty() }
            assertNull(controller.uiState.voiceSetupLanguage, "Sheet must not reappear once marked shown")
        }
    }

    @Test
    fun dismissVoiceSetupDoesNotPlay() = runBlocking {
        val fake = fakeWithEnglishVoices(voice("v1", quality = VoiceQuality.MEDIUM))
        withController(fake) { controller ->
            controller.toggleLemma(SENSE_A, LEMMA_A, Language.ENGLISH)
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
            controller.toggleLemma(SENSE_A, LEMMA_A, Language.ENGLISH)
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
                controller.toggleLemma(SENSE_A, LEMMA_A, Language.ENGLISH)
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
            controller.toggleLemma(SENSE_A, LEMMA_A, Language.ENGLISH)
            awaitUntil("spinner cleared after empty voice load") {
                controller.uiState.preparingKey == null
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
                ttsLanguage(Language.FRENCH), // Engine-available but without any voice.
            )
            voicesByLanguage = mapOf(Language.ENGLISH to listOf(voice("v1")))
        }
        withController(fake) { controller ->
            assertTrue(
                controller.uiState.isPlayable(Language.DUTCH),
                "Before availability resolves every language shows the speaker optimistically"
            )

            controller.refreshAvailability()
            awaitUntil("availability resolved") { controller.uiState.availableLanguages != null }
            assertTrue(controller.uiState.isPlayable(Language.ENGLISH), "Available language should be playable")
            assertFalse(controller.uiState.isPlayable(Language.DUTCH), "Unavailable language should hide the speaker")
            assertFalse(controller.uiState.isPlayable(Language.FRENCH), "Voiceless language should hide the speaker")
            assertFalse(controller.uiState.isPlayable(Language.GERMAN), "Unknown language should hide the speaker")
        }
    }

    @Test
    fun availabilityRefreshPicksUpNewlyInstalledVoices() = runBlocking {
        val fake = FakeSpeechPlayer().apply {
            languages = listOf(ttsLanguage(Language.ENGLISH))
        }
        withController(fake) { controller ->
            controller.refreshAvailability()
            awaitUntil("first availability probe resolved") { controller.uiState.availableLanguages != null }
            assertFalse(
                controller.uiState.isPlayable(Language.ENGLISH),
                "A language without voices must not be playable"
            )

            // The user installs a voice in system settings and comes back; the resume re-probe
            // must pick it up without recreating the controller.
            fake.voicesByLanguage = mapOf(Language.ENGLISH to listOf(voice("v1")))
            controller.refreshAvailability()
            awaitUntil("re-probe picked up the installed voice") {
                controller.uiState.isPlayable(Language.ENGLISH)
            }
        }
    }

    @Test
    fun resumeDuringSlowProbeReprobesAndPublishesTheNewVoices() = runBlocking {
        val fake = FakeSpeechPlayer().apply { languages = listOf(ttsLanguage(Language.ENGLISH)) }
        withController(fake) { controller ->
            // A resume probe stalls on the engine while the user is away in system settings.
            val gate = CompletableDeferred<Unit>()
            fake.voiceLoadGate = gate
            controller.refreshAvailability()
            awaitUntil("first probe reached the voice load") { fake.voiceLoadRequests == 1 }

            // They come back with a different engine, whose voices the stalled probe never saw.
            fake.voicesByLanguage = mapOf(Language.ENGLISH to listOf(voice("new-engine-v1")))
            controller.refreshAvailability()
            awaitUntil("stale probe replaced") { fake.voiceLoadRequests == 2 }

            gate.complete(Unit)
            awaitUntil("re-probe published the new engine's voices") {
                controller.uiState.isPlayable(Language.ENGLISH)
            }
            assertEquals(2, fake.voiceLoadRequests, "The stale probe should be replaced, not repeated")
        }
    }

    @Test
    fun playAfterEngineChangeUsesTheNewEnginesVoices() = runBlocking {
        val fake = fakeWithEnglishVoices(voice("old-engine-v1"))
        withController(fake) { controller ->
            controller.toggleLemma(SENSE_A, LEMMA_A, Language.ENGLISH)
            awaitUntil("first utterance handed to the engine") { fake.setVoices.size == 1 }
            fake.emitStatus(TTSStatus.SPEAKING)
            fake.emitStatus(TTSStatus.IDLE)

            // Switching the default engine replaces every voice id the stored selection holds.
            fake.voicesByLanguage = mapOf(Language.ENGLISH to listOf(voice("new-engine-v1")))

            controller.toggleLemma(SENSE_A, LEMMA_A, Language.ENGLISH)
            awaitUntil("second utterance handed to the engine") { fake.setVoices.size == 2 }
            assertEquals(
                "new-engine-v1",
                fake.setVoices.last().id,
                "A selection stranded on the previous engine must not leave the row silent"
            )
        }
    }

    @Test
    fun allVoicesDisabledHidesSpeaker() = runBlocking {
        val fake = fakeWithEnglishVoices(voice("v1"))
        // The user disabled every English voice in Settings.
        VoiceFilterHelper(settingsRepository()).setEnabledVoices(ttsLanguage(Language.ENGLISH), emptySet())
        withController(fake) { controller ->
            controller.refreshAvailability()
            awaitUntil("availability resolved") { controller.uiState.availableLanguages != null }
            assertFalse(
                controller.uiState.isPlayable(Language.ENGLISH),
                "A language whose voices are all disabled must not show a speaker"
            )
        }
    }

    @Test
    fun exampleAudioSpeaksTheSentenceWithoutHighlightMarkup() = runBlocking {
        val fake = fakeWithEnglishVoices(voice("v1"))
        withController(fake) { controller ->
            controller.toggleExample(SENSE_A, 0, EXAMPLE_MARKUP, Language.ENGLISH)
            assertEquals(
                RowAudioKeys.example(SENSE_A, 0),
                controller.uiState.preparingKey,
                "The tapped example should own the spinner"
            )

            awaitUntil("utterance handed to the engine") { fake.spokenTexts.isNotEmpty() }
            assertEquals(
                listOf(EXAMPLE_SPOKEN),
                fake.spokenTexts,
                "The engine must receive the sentence with <w> markup stripped, not read the tags aloud"
            )

            fake.emitStatus(TTSStatus.SPEAKING)
            assertEquals(
                RowAudioKeys.example(SENSE_A, 0),
                controller.uiState.playingKey,
                "SPEAKING should flip the example to playing"
            )
            assertEquals(
                RowAudioPhase.IDLE,
                controller.uiState.phaseFor(RowAudioKeys.lemma(SENSE_A)),
                "The row's lemma speaker must stay idle while its example plays"
            )
        }
    }

    @Test
    fun secondTapOnPlayingExampleStops() = runBlocking {
        val fake = fakeWithEnglishVoices(voice("v1"))
        withController(fake) { controller ->
            controller.toggleExample(SENSE_A, 0, EXAMPLE_MARKUP, Language.ENGLISH)
            awaitUntil("utterance handed to the engine") { fake.spokenTexts.isNotEmpty() }
            fake.emitStatus(TTSStatus.SPEAKING)
            val stopsBefore = fake.stopCount

            controller.toggleExample(SENSE_A, 0, EXAMPLE_MARKUP, Language.ENGLISH)
            assertEquals(stopsBefore + 1, fake.stopCount, "Tapping the playing example should stop the engine")
            assertNull(controller.uiState.playingKey, "Stop should clear the playing example")
        }
    }

    @Test
    fun examplesOfTheSameSenseAreAddressedIndependently() = runBlocking {
        val fake = fakeWithEnglishVoices(voice("v1"))
        withController(fake) { controller ->
            controller.toggleExample(SENSE_A, 0, EXAMPLE_MARKUP, Language.ENGLISH)
            awaitUntil("first example handed to the engine") { fake.spokenTexts.isNotEmpty() }
            fake.emitStatus(TTSStatus.SPEAKING)

            // A second example of the same sense is a different speaker, so this switches rather
            // than stops — the shared key prefix must not make them the same control.
            val stopsBefore = fake.stopCount
            controller.toggleExample(SENSE_A, 1, "Another <w>hello</w>.", Language.ENGLISH)
            assertEquals(stopsBefore + 1, fake.stopCount, "Switching examples must silence the first")
            assertEquals(
                RowAudioKeys.example(SENSE_A, 1),
                controller.uiState.preparingKey,
                "The second example should take over the spinner"
            )
            assertEquals(
                RowAudioPhase.IDLE,
                controller.uiState.phaseFor(RowAudioKeys.example(SENSE_A, 0)),
                "The first example must release its control"
            )

            awaitUntil("second example handed to the engine") { fake.spokenTexts.size == 2 }
            assertEquals("Another hello.", fake.spokenTexts.last(), "The second example's own text should be spoken")
        }
    }

    @Test
    fun tappingAnExampleTakesOverFromItsOwnLemma() = runBlocking {
        val fake = fakeWithEnglishVoices(voice("v1"))
        withController(fake) { controller ->
            driveToPlaying(controller, fake)
            val stopsBefore = fake.stopCount

            controller.toggleExample(SENSE_A, 0, EXAMPLE_MARKUP, Language.ENGLISH)
            assertEquals(stopsBefore + 1, fake.stopCount, "The lemma's audio must be silenced before the example plays")
            assertEquals(
                RowAudioPhase.IDLE,
                controller.uiState.phaseFor(RowAudioKeys.lemma(SENSE_A)),
                "The lemma speaker must release its stop glyph"
            )

            awaitUntil("example handed to the engine") { fake.spokenTexts.contains(EXAMPLE_SPOKEN) }
        }
    }

    @Test
    fun stopForPauseSilencesPlaybackWithoutEndingTheController() = runBlocking {
        val fake = fakeWithEnglishVoices(voice("v1"))
        withController(fake) { controller ->
            controller.toggleExample(SENSE_A, 0, EXAMPLE_MARKUP, Language.ENGLISH)
            awaitUntil("example handed to the engine") { fake.spokenTexts.isNotEmpty() }
            fake.emitStatus(TTSStatus.SPEAKING)
            val stopsBefore = fake.stopCount

            // The user switches tabs mid-sentence.
            controller.stopForPause()
            assertEquals(stopsBefore + 1, fake.stopCount, "Leaving the screen must silence the engine")
            assertNull(controller.uiState.playingKey, "Pause should clear the playing control")
            assertNull(controller.uiState.preparingKey, "Pause should clear the spinner")

            // Unlike dispose, the controller stays usable when the screen comes back.
            assertTrue(fake.hasStatusListeners, "Pause must not detach the status listener")
            controller.toggleLemma(SENSE_A, LEMMA_A, Language.ENGLISH)
            awaitUntil("playback works again after resuming") { fake.spokenTexts.contains(LEMMA_A) }
        }
    }

    @Test
    fun stopForPauseDuringVoiceLoadPreventsLateSpeak() = runBlocking {
        val fake = fakeWithEnglishVoices(voice("v1"))
        val gate = CompletableDeferred<Unit>()
        fake.voiceLoadGate = gate
        withController(fake) { controller ->
            controller.toggleExample(SENSE_A, 0, EXAMPLE_MARKUP, Language.ENGLISH)
            controller.stopForPause()
            assertNull(controller.uiState.preparingKey, "Pause should clear the spinner immediately")

            gate.complete(Unit)
            delay(100) // Give the stale load time to (wrongly) speak if it could.
            assertTrue(fake.spokenTexts.isEmpty(), "A load finishing after the screen paused must not speak")
        }
    }

    @Test
    fun wordAndExampleSpeakersAreMutuallyExclusive() = runBlocking {
        val fake = fakeWithEnglishVoices(voice("v1"))
        withController(fake) { controller ->
            // Word details' hero speaker and its example speakers share one controller, so playing
            // one must release the other rather than leaving two stop glyphs on screen.
            controller.toggleWord(LEMMA_A, Language.ENGLISH)
            awaitUntil("word handed to the engine") { fake.spokenTexts.contains(LEMMA_A) }
            fake.emitStatus(TTSStatus.SPEAKING)
            assertEquals(
                RowAudioKeys.word(LEMMA_A),
                controller.uiState.playingKey,
                "The hero speaker should own playback"
            )

            // Play an example: the word stops.
            val stopsBeforeExample = fake.stopCount
            controller.toggleExample(SENSE_A, 0, EXAMPLE_MARKUP, Language.ENGLISH)
            assertEquals(stopsBeforeExample + 1, fake.stopCount, "Starting an example must stop the word")
            assertEquals(
                RowAudioPhase.IDLE,
                controller.uiState.phaseFor(RowAudioKeys.word(LEMMA_A)),
                "The hero speaker must release its stop glyph when an example takes over"
            )
            awaitUntil("example handed to the engine") { fake.spokenTexts.contains(EXAMPLE_SPOKEN) }
            fake.emitStatus(TTSStatus.SPEAKING)
            assertEquals(
                RowAudioKeys.example(SENSE_A, 0),
                controller.uiState.playingKey,
                "The example should own playback now"
            )

            // Play the word again: the example stops.
            val stopsBeforeWord = fake.stopCount
            controller.toggleWord(LEMMA_A, Language.ENGLISH)
            assertEquals(stopsBeforeWord + 1, fake.stopCount, "Starting the word must stop the example")
            assertEquals(
                RowAudioPhase.IDLE,
                controller.uiState.phaseFor(RowAudioKeys.example(SENSE_A, 0)),
                "The example must release its stop glyph when the word takes over"
            )
            awaitUntil("word handed to the engine again") { fake.spokenTexts.count { it == LEMMA_A } == 2 }
        }
    }

    @Test
    fun stopForPauseKeepsAnOpenVoiceSetupSheet() = runBlocking {
        val fake = fakeWithEnglishVoices(voice("v1", quality = VoiceQuality.MEDIUM))
        withController(fake) { controller ->
            controller.toggleLemma(SENSE_A, LEMMA_A, Language.ENGLISH)
            awaitUntil("voice setup sheet requested") { controller.uiState.voiceSetupLanguage != null }
            val stopsBefore = fake.stopCount

            // Backgrounding while the first-run sheet is up must not throw the request away: nothing
            // is sounding, so there is nothing to silence, and the user's tap would otherwise vanish.
            controller.stopForPause()
            assertEquals(
                Language.ENGLISH,
                controller.uiState.voiceSetupLanguage,
                "A lifecycle stop must leave the setup sheet up"
            )
            assertEquals(stopsBefore, fake.stopCount, "Nothing is sounding, so nothing should be stopped")

            controller.dismissVoiceSetupAndPlay()
            awaitUntil("the gated request still resumes") { fake.spokenTexts.contains(LEMMA_A) }
        }
    }

    @Test
    fun knownPlayableWaitsForAvailabilityWhilePlayableIsOptimistic() = runBlocking {
        val fake = fakeWithEnglishVoices(voice("v1"))
        withController(fake) { controller ->
            // Before the probe resolves the two disagree by design: a row speaker shows optimistically
            // rather than popping in, while an always-present button stays disabled rather than
            // offering an affordance that does nothing.
            assertTrue(
                controller.uiState.isPlayable(Language.ENGLISH),
                "isPlayable is optimistic before availability resolves"
            )
            assertFalse(
                controller.uiState.isKnownPlayable(Language.ENGLISH),
                "isKnownPlayable must not claim a language is speakable before the probe lands"
            )

            controller.refreshAvailability()
            awaitUntil("availability resolved") { controller.uiState.availableLanguages != null }
            assertTrue(
                controller.uiState.isKnownPlayable(Language.ENGLISH),
                "Once resolved, an available language is known playable"
            )
            assertFalse(
                controller.uiState.isKnownPlayable(Language.GERMAN),
                "A language the engine cannot speak is never known playable"
            )
        }
    }

    @Test
    fun disposeStopsAndDetachesListener() = runBlocking {
        val fake = fakeWithEnglishVoices(voice("v1"))
        withController(fake) { controller ->
            driveToPlaying(controller, fake)
            val stopsBefore = fake.stopCount

            controller.dispose()
            assertEquals(stopsBefore + 1, fake.stopCount, "Dispose should stop the engine")
            assertNull(controller.uiState.playingKey, "Dispose should clear playback state")
            assertFalse(fake.hasStatusListeners, "Dispose should detach the status listener")

            fake.emitStatus(TTSStatus.SPEAKING)
            assertNull(controller.uiState.playingKey, "Events after dispose must not resurrect state")
        }
    }
}
