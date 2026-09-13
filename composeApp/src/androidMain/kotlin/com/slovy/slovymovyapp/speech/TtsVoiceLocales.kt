package com.slovy.slovymovyapp.speech

import android.speech.tts.Voice
import java.util.Locale

/**
 * Locale matching and reporting for the voices an Android TTS engine advertises. The engine
 * decides how a voice's locale is spelled: most report `en_US`, but engines built on the
 * framework's ISO 639-2 callbacks can hand back `eng_USA`, and `Locale.getLanguage()` keeps a
 * three-letter code as is. A voice must not disappear from Settings over that spelling.
 */
internal object TtsVoiceLocales {

    /**
     * Whether [voice] speaks [wanted]'s language. A country on [wanted] must match too; an empty
     * one accepts every regional variant.
     */
    fun matches(voice: Voice, wanted: Locale): Boolean {
        val spoken = voice.locale
        if (!sameLanguage(spoken, wanted)) return false
        return wanted.country.isEmpty() || spoken.country == wanted.country
    }

    private fun sameLanguage(a: Locale, b: Locale): Boolean {
        if (a.language.isEmpty() || b.language.isEmpty()) return false
        if (a.language == b.language) return true
        val iso3 = iso3Language(a) ?: return false
        return iso3 == iso3Language(b)
    }

    /** The ISO 639-2 code, or null for a language the platform has no code for. */
    private fun iso3Language(locale: Locale): String? =
        runCatching { locale.getISO3Language() }.getOrNull()?.takeIf { it.isNotEmpty() }

    /**
     * One-line inventory of [voices] for the developer log: locale counts, then up to
     * [MAX_NAMED_VOICES] voice names, so a "No voices available" report from a device we cannot
     * touch still shows exactly what the engine said. A Google engine advertises hundreds of
     * voices, so names are capped while the locale summary stays complete.
     */
    fun describe(voices: Set<Voice>?): String {
        if (voices == null) return "engine returned no voice list"
        if (voices.isEmpty()) return "engine returned an empty voice list"
        val sorted = voices.sortedBy { it.name }
        val localeCounts = sorted
            .groupingBy { it.locale.toString() }
            .eachCount()
            .entries
            .sortedBy { it.key }
            .joinToString(", ") { "${it.key}=${it.value}" }
        val named = sorted.take(MAX_NAMED_VOICES).joinToString(", ") { voice ->
            "${voice.name}@${voice.locale}(q=${voice.quality}, net=${voice.isNetworkConnectionRequired}, " +
                    "features=${voice.features?.sorted()})"
        }
        val omitted = sorted.size - MAX_NAMED_VOICES
        val suffix = if (omitted > 0) " and $omitted more" else ""
        return "${sorted.size} voices; locales: $localeCounts; names: $named$suffix"
    }

    private const val MAX_NAMED_VOICES = 30
}
