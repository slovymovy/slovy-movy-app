package com.slovy.slovymovyapp.ui.study

import com.slovy.slovymovyapp.i18n.UiText
import com.slovy.slovymovyapp.speech.RowAudioPhase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * A study card shows several speakers — its word, and one per example — but only one utterance can
 * sound at a time. [StudySessionUiState.Active] holds a single key for that reason, so these tests
 * pin the two properties the UI depends on: exactly one speaker is ever non-idle, and no two
 * speakers can share a key.
 */
class StudyAudioKeysTest {

    private fun active(playing: String? = null, preparing: String? = null) =
        StudySessionUiState.Active(
            progress = StudySessionProgressUiState(current = 1, total = 10),
            card = StudyCardUiState.Recognition(
                id = "card-1",
                chipLabel = UiText.Plain("NL -> EN"),
                promptWord = "gezellig",
                mode = StudyRecognitionMode.BILINGUAL,
                back = StudyCardBackUiState(headline = "cosy"),
            ),
            side = StudyCardSide.FRONT,
            playingAudioKey = playing,
            preparingAudioKey = preparing,
        )

    @Test
    fun onlyTheActiveKeyReportsANonIdlePhase() {
        val exampleKey = StudyAudioKeys.example("back", 0)
        val state = active(playing = exampleKey)

        assertEquals(
            RowAudioPhase.PLAYING,
            state.audioPhase(exampleKey),
            "The key that is playing should report PLAYING"
        )
        assertEquals(
            RowAudioPhase.IDLE,
            state.audioPhase(StudyAudioKeys.WORD),
            "The word speaker must stay idle while an example plays"
        )
        assertEquals(
            RowAudioPhase.IDLE,
            state.audioPhase(StudyAudioKeys.example("back", 1)),
            "A sibling example must stay idle while another one plays"
        )
    }

    @Test
    fun preparingAndPlayingAreDistinctPhases() {
        val key = StudyAudioKeys.example("back", 2)
        assertEquals(
            RowAudioPhase.PREPARING,
            active(preparing = key).audioPhase(key),
            "A key being prepared should report PREPARING"
        )
        assertEquals(
            RowAudioPhase.IDLE,
            active().audioPhase(key),
            "With nothing active every key is idle"
        )
    }

    @Test
    fun examplesOfDifferentSensesDoNotShareAKey() {
        // A multi-sense card renders one back per pager page. Without the sense scope, example 0 of
        // every sense would be the same speaker and playing one would light up the others.
        assertNotEquals(
            StudyAudioKeys.example("sense0", 0),
            StudyAudioKeys.example("sense1", 0),
            "Examples at the same index in different senses must be different speakers"
        )
        assertNotEquals(
            StudyAudioKeys.example("back", 0),
            StudyAudioKeys.WORD,
            "An example must never collide with the card's word speaker"
        )
    }

    @Test
    fun theClozeSentenceIsItsOwnSpeaker() {
        // A CLOZE_SOURCE back shows the filled sentence and no examples, so its speaker needs a key
        // of its own — one that cannot be mistaken for the card's word or for an example.
        val clozeKey = StudyAudioKeys.cloze("back")
        assertNotEquals(StudyAudioKeys.WORD, clozeKey, "The cloze sentence is not the word speaker")
        assertNotEquals(
            StudyAudioKeys.example("back", 0),
            clozeKey,
            "The cloze sentence is not an example speaker"
        )
        assertNotEquals(
            StudyAudioKeys.cloze("sense1"),
            clozeKey,
            "Cloze sentences of different senses must be different speakers"
        )

        val state = active(playing = clozeKey)
        assertEquals(
            RowAudioPhase.PLAYING,
            state.audioPhase(clozeKey),
            "The cloze sentence should own playback while it sounds"
        )
        assertEquals(
            RowAudioPhase.IDLE,
            state.audioPhase(StudyAudioKeys.WORD),
            "The word speaker must stay idle while the cloze sentence plays"
        )
    }
}
