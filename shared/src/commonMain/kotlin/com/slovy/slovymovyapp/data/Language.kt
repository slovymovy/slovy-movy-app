package com.slovy.slovymovyapp.data

import kotlinx.serialization.Serializable

@Serializable
enum class Language(
    val code: String,
    val selfName: String,
    val flag: String,
    val englishName: String
) {
    ENGLISH("en", "English", "🇬🇧", "English"),
    RUSSIAN("ru", "Русский", "🇷🇺", "Russian"),
    DUTCH("nl", "Nederlands", "🇳🇱", "Dutch"),
    POLISH("pl", "Polski", "🇵🇱", "Polish"),
    GERMAN("de", "Deutsch", "🇩🇪", "German"),
    FRENCH("fr", "Français", "🇫🇷", "French"),
    ITALIAN("it", "Italiano", "🇮🇹", "Italian"),
    CZECH("cs", "Čeština", "🇨🇿", "Czech"),
    TURKISH("tr", "Türkçe", "🇹🇷", "Turkish"),
    SPANISH("es", "Español", "🇪🇸", "Spanish"),

    /**
     * Simplified Chinese only. [englishName] is the sole input to the AI translation prompt's
     * `$TARGET_LANG` placeholder, so it is what keeps the generated corpus in 简体 rather than 繁體.
     * Traditional would join as a separate `zh-Hant` entry; it is not a variant of this one.
     */
    CHINESE("zh-Hans", "简体中文", "🇨🇳", "Simplified Chinese");

    companion object {
        /**
         * Exact match on purpose. Callers that merely validate a code and then keep using their own
         * input string rely on it: the `/word` route checks the requested translation codes here but
         * carries the raw strings on into existing-language checks, db-extract filtering, and the
         * merged translation map keys, none of which ignore case. A lenient match would let `RU`
         * past validation and then miss the existing `ru` data, regenerating it and storing a
         * duplicate `RU` entry the client can never read.
         *
         * Filename segments are the one place that legitimately needs leniency; they have
         * [fromFileNameSegment] instead.
         */
        fun fromCodeOrNull(code: String): Language? {
            return entries.find { it.code == code }
        }

        fun fromCode(code: String): Language {
            return fromCodeOrNull(code)
                ?: throw IllegalArgumentException("Unknown language code: $code")
        }

        /**
         * Resolves a language from a downloaded-DB filename segment, which
         * [com.slovy.slovymovyapp.data.remote.DataDbManager] builds lowercased. A code carrying a
         * script subtag (`zh-Hans`) comes back out as `zh-hans`, so an exact match would drop the
         * pair with no error anywhere - the file would simply never be offered for download nor
         * reported as installed.
         *
         * Kept separate from [fromCodeOrNull] so this leniency stays confined to filename parsing
         * and never widens what a network or storage boundary accepts.
         */
        fun fromFileNameSegment(segment: String): Language? {
            return entries.find { it.code.equals(segment, ignoreCase = true) }
        }
    }
}
