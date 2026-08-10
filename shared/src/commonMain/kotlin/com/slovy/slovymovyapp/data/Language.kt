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

            Mandarin has no lexical counterpart for many English function words, and what you write
            has to be natural Chinese rather than a word-for-word mirror of the English:
            - Prefer wording in which a real Mandarin word carries the sense, and tag that word in
              the slot Chinese grammar gives it rather than the slot the English word held. Usually
              this is a coverb: 我<w>给</w>妈妈买了礼物, 锻炼<w>对</w>你的健康很有益, 他用苹果<w>换</w>了
              一根香蕉.
            - Where the sense is carried by structure alone - a bare duration complement (住了十年),
              了, 的, word order - write the natural sentence and leave it untagged. An untagged
              example translation is a correct answer; a filler inserted only so that something can
              be tagged is not (锻炼对你的健康<w>为</w>（有益）, 住了<w>持续</w>十年). Do not fall back
              to tagging the duration itself, since 十年 is the span rather than the preposition.
            - At least one example per sense must still come out with a tagged translation. When the
              rule above would leave every example of a sense untagged, shape one of them so that a
              real word carries the meaning.
            - A translation written with …… is a pattern, not a string: fill the slot (对……来说 ->
              对他来说), split the tag around the argument when the parts separate, and agree with the
              person of the sentence you are writing.
            - Tag the declared translation exactly. Do not pull an added verb inside the tag
              (<w>去健身房</w> where the word is 健身房), and do not leave part of it outside
              (<w>体育</w>课 where the word is 体育课).
            - Do not repeat a morpheme the surrounding words already supply: 正则表达式, 生理盐水 and
              母乳 each already contain the head, so choose wording that fits what is around it.
            - Keep parenthetical glosses out of the example sentences; that belongs in the sense
              clarification.
            - Transliterate foreign names so that they still read as foreign; 韦 is an ordinary
              Chinese surname and cannot stand in for an English one.
        """.trimIndent()
    );

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
