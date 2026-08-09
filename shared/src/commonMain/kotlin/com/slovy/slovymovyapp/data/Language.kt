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
         * Matches case-insensitively because [code] is not only stored verbatim: it is also a
         * `dictionary_{lang}.db` / `translation_{src}_{tgt}.db` filename segment, and those names are
         * built lowercased. A code carrying a script subtag (`zh-Hans`) therefore comes back out of a
         * filename as `zh-hans`, and an exact match would drop the pair with no error anywhere -
         * the file would simply never be offered for download or listed as installed.
         */
        fun fromCodeOrNull(code: String): Language? {
            return entries.find { it.code.equals(code, ignoreCase = true) }
        }

        fun fromCode(code: String): Language {
            return fromCodeOrNull(code)
                ?: throw IllegalArgumentException("Unknown language code: $code")
        }
    }
}
