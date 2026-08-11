package com.slovy.slovymovyapp.data

import kotlinx.serialization.Serializable

@Serializable
enum class Language(
    val code: String,
    val selfName: String,
    val flag: String,
    val englishName: String,
    /**
     * Extra guidance appended to the AI base-processing prompt for this language, substituted for
     * its `$INPUT_NOTES` placeholder. Describes what the Wiktextract input for this language can
     * actually look like, where that differs from what the generic prompt assumes.
     *
     * Empty means the generic prompt is enough, which is the case for every language whose extract
     * is uniform. The default carries that meaning for all of them.
     */
    val basePromptNotes: String = "",
    /** The same, for the AI translation prompt. See [basePromptNotes]. */
    val translationPromptNotes: String = ""
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
     * Simplified only, matching the `zh-extract.jsonl` source and CLDR's own reading of a bare `zh`.
     * Traditional would join as a separate `zh-Hant` entry rather than a variant of this one.
     *
     * [englishName] is the sole input to the AI translation prompt's `$TARGET_LANG` placeholder and
     * is never shown in the UI, so it is what holds the generated corpus to 简体 even though the
     * extract itself is 繁體. Shortening it to "Chinese" would silently change what gets generated.
     */
    CHINESE(
        "zh",
        "简体中文",
        "🇨🇳",
        "Simplified Chinese",
        basePromptNotes = """
            The source extract comes from the Chinese Wiktionary, which lemmatises entries at the
            Traditional spelling and is written largely in Traditional characters; the Simplified
            spelling is usually present only as a tagged form of the headword. Expect Traditional
            input throughout, including in definitions and examples, and expect entries for other
            Sinitic lects (Cantonese, Hokkien, Hakka, Wu, Min) alongside the Mandarin ones.

            Write all output in Mandarin using Simplified characters, converting the input where
            needed. Describe the Mandarin reading of the word, not that of another lect.
        """.trimIndent(),
        translationPromptNotes = """
            The extracted translations are not uniform Mandarin in Simplified characters. Expect any
            of the following in the input:
            - Traditional characters (電腦), which is the prevailing script of the source data.
            - A dual-script pair inside one string, Traditional before the slash and Simplified
              after it: "詞典 /词典". Where the two spellings coincide there is no slash.
            - Rows belonging to a Sinitic lect other than Mandarin - Cantonese, Hokkien, Hakka, Wu,
              Min - usually carrying a tag or lect name that says so.
            - Dungan rows written in Cyrillic (хуадян).

            Produce Mandarin in Simplified characters only: take the Simplified side of a
            dual-script pair, convert Traditional spellings, and skip entries from other lects and
            from non-Han scripts unless the sense has no usable Mandarin entry at all. This applies
            equally to the definitions and example translations you write yourself.
        """.trimIndent()
    );

    /**
     * Separator for enumerating words written in this language, such as the translation glosses
     * that make up a sense title.
     *
     * Chinese enumerates with the ideographic comma and no trailing space; a Latin `", "` there
     * reads as foreign punctuation next to the corpus text, which already uses `、` inside its own
     * definitions. Every other supported language enumerates the Latin/Cyrillic way.
     */
    val wordSeparator: String
        get() = when (this) {
            CHINESE -> "、"
            else -> ", "
        }

    companion object {
        fun fromCode(code: String): Language {
            return entries.find { it.code == code }
                ?: throw IllegalArgumentException("Unknown language code: $code")
        }

        fun fromCodeOrNull(code: String): Language? {
            return entries.find { it.code == code }
        }
    }
}
