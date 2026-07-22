package com.slovy.slovymovyapp.ui

import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.speech.Text2SpeechVoice
import com.slovy.slovymovyapp.speech.VoiceQuality
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LanguageUiStateTest {

    private fun voice(id: String) = Text2SpeechVoice(
        id = id,
        name = id,
        language = Language.ENGLISH,
        localeTag = "en-US",
        quality = VoiceQuality.BEST,
        networkConnectionRequired = false,
        enabledByDefault = true
    )

    @Test
    fun enabledInstalledVoicesIgnoresIdsTheBoundEngineDoesNotOffer() {
        val state = LanguageUiState(
            voices = listOf(voice("google-v1"), voice("google-v2")),
            voicesLoaded = true,
            enabledVoiceIds = setOf("google-v1", "rhvoice-anna")
        )

        assertEquals(
            listOf("google-v1"),
            state.enabledInstalledVoices.map { it.id },
            "A voice id stored for another engine must not be counted as available"
        )
    }

    @Test
    fun enabledInstalledVoicesIsEmptyWhenTheEngineHasNoVoicesForTheLanguage() {
        // The user downloaded the language, but the bound engine speaks none of it — the
        // selection was stored while a different engine was the system default.
        val state = LanguageUiState(
            voices = emptyList(),
            voicesLoaded = true,
            enabledVoiceIds = setOf("rhvoice-anna", "rhvoice-elena")
        )

        assertTrue(
            state.enabledInstalledVoices.isEmpty(),
            "A language the engine cannot speak must not report enabled voices"
        )
    }
}
