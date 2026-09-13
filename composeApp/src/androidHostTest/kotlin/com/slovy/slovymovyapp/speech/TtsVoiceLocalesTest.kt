package com.slovy.slovymovyapp.speech

import android.speech.tts.Voice
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class TtsVoiceLocalesTest {

    private val english = Locale.Builder().setLanguage("en").build()

    @Test
    fun matchesTwoLetterLocaleWithAnyCountry() {
        assertTrue(TtsVoiceLocales.matches(voice("en-us-x-sfg#female_1", Locale.US), english), "en_US voice must match en")
        assertTrue(TtsVoiceLocales.matches(voice("en-gb", Locale.UK), english), "en_GB voice must match en")
    }

    @Test
    fun matchesThreeLetterLocaleReportedByEngine() {
        @Suppress("DEPRECATION")
        val samsungStyle = Locale("eng", "USA")
        assertTrue(
            TtsVoiceLocales.matches(voice("en-US-SMTf00", samsungStyle), english),
            "an eng_USA voice must match en, since getLanguage keeps three-letter codes as is"
        )
    }

    @Test
    fun rejectsOtherLanguages() {
        assertFalse(TtsVoiceLocales.matches(voice("nl-nl", Locale("nl", "NL")), english), "nl_NL must not match en")
        @Suppress("DEPRECATION")
        val threeLetterDutch = Locale("nld", "NLD")
        assertFalse(TtsVoiceLocales.matches(voice("nl", threeLetterDutch), english), "nld_NLD must not match en")
    }

    @Test
    fun honoursRequestedCountry() {
        val britishEnglish = Locale.UK
        assertFalse(TtsVoiceLocales.matches(voice("en-us", Locale.US), britishEnglish), "en_US must not match en_GB")
        assertTrue(TtsVoiceLocales.matches(voice("en-gb", Locale.UK), britishEnglish), "en_GB must match en_GB")
    }

    @Test
    fun rejectsVoiceWithoutLanguage() {
        assertFalse(TtsVoiceLocales.matches(voice("root", Locale.ROOT), english), "a voice with no language matches nothing")
    }

    @Test
    fun describesMissingAndEmptyLists() {
        assertEquals("engine returned no voice list", TtsVoiceLocales.describe(null))
        assertEquals("engine returned an empty voice list", TtsVoiceLocales.describe(emptySet()))
    }

    @Test
    fun describesLocaleCountsAndNames() {
        val description = TtsVoiceLocales.describe(
            setOf(
                voice("en-us-b", Locale.US),
                voice("en-us-a", Locale.US),
                voice("nl-nl", Locale("nl", "NL"), features = setOf("notInstalled")),
            )
        )
        assertEquals(
            "3 voices; locales: en_US=2, nl_NL=1; names: " +
                    "en-us-a@en_US(q=300, net=false, features=[]), " +
                    "en-us-b@en_US(q=300, net=false, features=[]), " +
                    "nl-nl@nl_NL(q=300, net=false, features=[notInstalled])",
            description
        )
    }

    @Test
    fun describeCapsNamedVoices() {
        val voices = (1..35).map { voice("en-us-%02d".format(it), Locale.US) }.toSet()
        val description = TtsVoiceLocales.describe(voices)
        assertTrue(description.startsWith("35 voices; locales: en_US=35; names: en-us-01@"), description)
        assertTrue(description.endsWith(" and 5 more"), description)
        assertFalse("en-us-31@" in description, "voices past the cap must not be listed")
    }

    private fun voice(name: String, locale: Locale, features: Set<String> = emptySet()): Voice =
        Voice(name, locale, Voice.QUALITY_NORMAL, Voice.LATENCY_NORMAL, false, features)
}
