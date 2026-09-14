package com.slovy.slovymovyapp.speech

import android.speech.tts.Voice
import java.util.Locale

/**
 * Locale matching and reporting for the voices an Android TTS engine advertises. The engine
 * decides how a voice's locale is spelled: most report `en_US`, but engines built on the
 * framework's ISO 639-2 callbacks can hand back `eng_USA` or the bibliographic `dut_NLD`, and
 * `Locale.getLanguage()` keeps a three-letter code as is. A voice must not disappear from
 * Settings over that spelling.
 */
internal object TtsVoiceLocales {

    /**
     * Whether [voice] speaks [wanted]'s language. A country on [wanted] must match too; an empty
     * one accepts every regional variant.
     */
    fun matches(voice: Voice, wanted: Locale): Boolean {
        val spoken = voice.locale
        val spokenLanguage = canonicalLanguage(spoken) ?: return false
        if (spokenLanguage != canonicalLanguage(wanted)) return false
        return wanted.country.isEmpty() || spoken.country == wanted.country
    }

    /**
     * [locale]'s language as its ISO 639-1 code where one exists: a two-letter code is returned as
     * is, a three-letter code is mapped from its bibliographic to its terminology form and then to
     * the two-letter code the platform knows. A three-letter code without a two-letter equivalent
     * stays as is; an empty language is null.
     */
    fun canonicalLanguage(locale: Locale): String? {
        val language = locale.language
        if (language.isEmpty()) return null
        if (language.length != 3) return language
        val terminology = BIBLIOGRAPHIC_TO_TERMINOLOGY[language] ?: language
        return iso3ToIso2[terminology] ?: terminology
    }

    /**
     * ISO 639-2/B codes that differ from the 639-2/T code the platform maps two-letter codes to.
     * This is the complete list; both spellings are valid for an engine to report.
     */
    private val BIBLIOGRAPHIC_TO_TERMINOLOGY = mapOf(
        "alb" to "sqi",
        "arm" to "hye",
        "baq" to "eus",
        "bur" to "mya",
        "chi" to "zho",
        "cze" to "ces",
        "dut" to "nld",
        "fre" to "fra",
        "geo" to "kat",
        "ger" to "deu",
        "gre" to "ell",
        "ice" to "isl",
        "mac" to "mkd",
        "mao" to "mri",
        "may" to "msa",
        "per" to "fas",
        "rum" to "ron",
        "slo" to "slk",
        "tib" to "bod",
        "wel" to "cym",
    )

    /** Terminology three-letter code to two-letter code for every language the platform knows. */
    private val iso3ToIso2: Map<String, String> by lazy {
        buildMap {
            for (iso2 in Locale.getISOLanguages()) {
                val iso3 = runCatching { Locale.Builder().setLanguage(iso2).build().getISO3Language() }
                    .getOrNull()
                    ?.takeIf { it.isNotEmpty() } ?: continue
                putIfAbsent(iso3, iso2)
            }
        }
    }

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
