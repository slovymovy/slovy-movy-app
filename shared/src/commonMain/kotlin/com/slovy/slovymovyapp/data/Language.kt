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

            Mandarin has no lexical counterpart for many of the function words the source language
            uses, and what you write has to be natural Chinese rather than a word-for-word mirror of
            the source sentence:
            - Prefer wording in which a real Mandarin word carries the sense, and tag that word in
              the slot Chinese grammar gives it rather than the slot the source word held. Usually
              this is a coverb: 我<w>给</w>妈妈买了礼物, 锻炼<w>对</w>你的健康很有益, 他用苹果<w>换</w>了
              一根香蕉.
            - Before choosing what to tag, look at the sentence you have just written and check
              whether a word already in it carries the sense. Usually one does, and it is the word
              to tag - it does not have to be string-identical to a declared translation, a
              contextual synonym is correct. Tag a different word only when nothing already present
              carries the sense.
            - Where the sense is carried by structure alone - a bare duration complement (住了十年),
              了, 的, word order - write the natural sentence and leave it untagged. An untagged
              example translation is a correct answer, and takes precedence over the later
              instruction to tag the word in every example; a filler inserted only so that something
              can be tagged is not (锻炼对你的健康<w>为</w>（有益）, 住了<w>持续</w>十年). Nor is the
              quantity a substitute for the word: 十年, 百分之二十 and 几秒钟 are the amount being
              measured rather than the preposition, so leave those sentences untagged too.
            - That licence is narrow. It applies only where no word in the sentence carries the
              sense. Where a declared translation is already sitting in the sentence you wrote, tag
              it; a name always supplies one (<w>斯普林</w>先生, 德克萨斯州的<w>斯普林</w>).
            - At least one example per sense must still come out with a tagged translation. When the
              rule above would leave every example of a sense untagged, shape one of them so that a
              real word carries the meaning.
            - A translation written with …… is a pattern, not a string: fill the slot (对……来说 ->
              对他来说), split the tag around the argument when the parts separate, and agree with the
              person of the sentence you are writing.
            - Tag the declared translation exactly. Do not pull an added verb inside the tag
              (<w>去健身房</w> where the word is 健身房), and do not leave part of it outside
              (<w>体育</w>课 where the word is 体育课).
            - The tagged span must be able to serve as the same part of speech as pos. If pos is
              noun, the span must be a noun or noun phrase - never a verb, a verb-object compound,
              or a measure word standing in for a noun that is itself declared. Where the declared
              translations are themselves measure words, the measure word is the taught word and
              takes the tag. Before you finalise an example, name the part of speech of the span
              you tagged and confirm it matches pos. These are all one error, a noun sense with a
              verb tagged: 我需要给老师<w>写</w>一封电子邮件 (email - tag 电子邮件),
              她赢得了第一<w>名</w> (prize - 名 is rank), 我们决定<w>开车</w>去度假 (car - 开车 is a
              verb phrase).
            - Chinese counts nouns with a measure word, and which of the two carries the tag is
              decided by the sense's own declared translations. Where those are measure words
              (块, 条, 件), the source word is the counter, so tag the measure word: 一<w>块</w>派,
              一<w>条</w>有用的建议. Where they are nouns (曲子, 文章, 枪), tag the noun and leave the
              measure word outside it: 两首优美的<w>曲子</w>, 一篇关于医学研究的<w>文章</w>,
              带着<w>枪</w>. Never tag the noun when the declared translations are measure words -
              blanking 建议 asks the learner for the thing being counted rather than for the word
              being taught.
            - Do not repeat a morpheme the surrounding words already supply: 正则表达式, 生理盐水 and
              母乳 each already contain the head, so choose wording that fits what is around it.
            - Keep parenthetical glosses out of the example sentences; that belongs in the sense
              clarification.
            - Transliterate foreign names so that they still read as foreign; 韦 is an ordinary
              Chinese surname and cannot stand in for a foreign one.
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
