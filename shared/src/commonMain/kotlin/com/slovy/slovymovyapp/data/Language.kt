package com.slovy.slovymovyapp.data

import kotlinx.serialization.Serializable

@Serializable
enum class Language(
    val code: String,
    val selfName: String,
    val flag: String,
    val englishName: String,
    /**
     * Whether the corpus is generated *for* this language, so it can be studied. That needs a
     * Wiktextract source mapping and a frequency list, which is why it is the narrower set: it is
     * exactly the keys of [com.slovy.slovymovyapp.ingestion.LANG_TO_SOURCE_FILE].
     *
     * The default is `false` because a language joins as a translation target first; learning
     * support arrives later, with the data.
     */
    val supportedForLearning: Boolean = false,
    /**
     * Whether the corpus is translated *into* this language, so it can be picked as a translation
     * target and requested from `/word`.
     *
     * Defaults to `true`: an entry exists in this enum because something is generated for it, and
     * a target is what a new language is added as. Set it to `false` while a language is being
     * prepared - the extract imported and the prompt notes being iterated - so that nothing offers
     * it before its corpus exists.
     */
    val supportedForTranslation: Boolean = true,
    /**
     * Codes of the learning languages whose corpus is translated into this one, or `null` for every
     * one of them. A target generated for English only names `setOf("en")` here, and then nothing
     * offers it to, or requests it for, a learner of another language: see [isTranslationTargetFor].
     *
     * Codes rather than entries, because an enum constant cannot refer to one declared after it.
     */
    val translationSourceCodes: Set<String>? = null,
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
    ENGLISH("en", "English", "🇬🇧", "English", supportedForLearning = true),
    RUSSIAN("ru", "Русский", "🇷🇺", "Russian", supportedForLearning = true),
    DUTCH("nl", "Nederlands", "🇳🇱", "Dutch", supportedForLearning = true),
    GERMAN(
        "de",
        "Deutsch",
        "🇩🇪",
        "German",
        supportedForLearning = true,
        basePromptNotes = """
            German capitalises every noun and name wherever it stands in a sentence. Keep that capital
            in the examples and common phrases you write, and wrap the word in its <w> tag exactly as
            it is written in the sentence: Ich wohne in einem großen <w>Haus</w>.

            For separable verbs, tag the finite part and the separated prefix separately:
            Der Film <w>fängt</w> um acht Uhr <w>an</w>.

            Keep standard spelling with ß and umlauts; never substitute ss, ae, oe or ue for them.
        """.trimIndent()
    ),
    POLISH(
        "pl", "Polski", "🇵🇱", "Polish", supportedForLearning = true,
        translationPromptNotes = """
            Write Polish in the Latin alphabet only. Never use a Russian word in place of a Polish one,
            neither whole nor with its first letters in Latin. No Cyrillic letter in any translation,
            definition, clarification or example, except the source headword cited in a definition
            about that word. If Polish has no word for the sense, leave the declared translation empty
            and paraphrase in the example.
        """.trimIndent()
    ),
    FRENCH("fr", "Français", "🇫🇷", "French"),
    ITALIAN("it", "Italiano", "🇮🇹", "Italian"),
    CZECH(
        "cs", "Čeština", "🇨🇿", "Czech",
        translationPromptNotes = """
            Write Czech in the Latin alphabet only. Never use a Russian word in place of a Czech one,
            neither whole nor with its first letters in Latin. No Cyrillic letter in any translation,
            definition, clarification or example, except the source headword cited in a definition
            about that word. If Czech has no word for the sense, leave the declared translation empty
            and paraphrase in the example.
        """.trimIndent()
    ),
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
    ),

    /*
     * Generated for English only (issue #973): most learners want a translation into their own
     * language, and translating every learning language into every target does not pay for
     * itself. Both carry translationSourceCodes = setOf("en"), so no learner of another language
     * is offered them and no Russian word is ever requested with them.
     */
    JAPANESE(
        "ja", "日本語", "🇯🇵", "Japanese",
        translationSourceCodes = setOf("en"),
        translationPromptNotes = """
            Write ordinary mixed script - kanji with kana, katakana for loanwords and foreign names
            (スミス). No furigana, readings or romaji anywhere.

            Japanese has no spaces, so tag precisely: the word itself, with the following particle
            outside the tag (私は<w>本</w>を読んだ, not <w>本を</w>). For a verb or adjective, tag the
            whole inflected form in the sentence (<w>走った</w>); the declared translation stays the
            dictionary form (走る).

            For a する-noun, declare and tag the noun (勉強) when pos is noun and the する verb
            (勉強する) when pos is verb.

            Plain form in definitions; natural polite speech (です・ます) in examples.

            Articles, number and some prepositions have no Japanese word. Where the sense is carried
            by structure alone, write the natural sentence and leave it untagged with an empty
            declared translation and a paraphrase in the clarification; never insert a filler just to
            tag something. At least one example per sense must still tag a real word where any
            carries the sense.
        """.trimIndent()
    ),

    /**
     * European Portuguese, decided 2026-10-21. [englishName] is the sole input to the translation
     * prompt's `$TARGET_LANG` and is never shown in the UI; plain "Portuguese" would let the model
     * drift between varieties word by word, because the extracted translations do not pick one
     * (see [translationPromptNotes]). Brazilian would join as a separate entry rather than a
     * variant of this one.
     */
    PORTUGUESE(
        "pt", "Português", "🇵🇹", "European Portuguese",
        translationSourceCodes = setOf("en"),
        translationPromptNotes = """
            Write European Portuguese throughout - definitions, translations, clarifications and
            examples: autocarro, telemóvel, comboio, pequeno-almoço, ecrã, casa de banho; "estou a
            fazer", not "estou fazendo"; clitics after the verb in affirmative clauses (diga-me,
            vi-o); "tu" for informal address. Spelling as Portugal applies the 1990 Agreement:
            facto, registo, económico.

            The extracted translations mix varieties, often both for one sense. Take the European
            form and drop the Brazilian one (registo, not registro beside it); where only a
            Brazilian form is given (trem, celular, sorvete), replace it with the European word
            (comboio, telemóvel, gelado).
        """.trimIndent()
    ),

    /*
     * Declared, not yet offered. Their extracts are imported (see kaikki-parser's
     * ExtractionUtils.langToSourceFile), so their translations already enrich the languages being
     * generated, but no corpus has been generated in them: supportedForTranslation stays false
     * until one has, and translationPromptNotes are written and measured. See
     * docs/ADDING_A_LANGUAGE.md in kaikki-parser.
     *
     * englishName is the sole input to the translation prompt's $TARGET_LANG, so for Kurdish,
     * whose extract covers more than one variety, it is the thing to settle before generating:
     * Kurmanji or Sorani. It currently carries the neutral name, which is only safe while nothing
     * generates from it.
     */
    GREEK("el", "Ελληνικά", "🇬🇷", "Greek", supportedForTranslation = false),
    INDONESIAN("id", "Bahasa Indonesia", "🇮🇩", "Indonesian", supportedForTranslation = false),
    KOREAN("ko", "한국어", "🇰🇷", "Korean", supportedForTranslation = false),

    /** No country flag exists for Kurdish, and every neighbouring one would be wrong. */
    KURDISH("ku", "Kurdî", "🌍", "Kurdish", supportedForTranslation = false),
    MALAY("ms", "Bahasa Melayu", "🇲🇾", "Malay", supportedForTranslation = false),
    THAI("th", "ไทย", "🇹🇭", "Thai", supportedForTranslation = false),
    VIETNAMESE("vi", "Tiếng Việt", "🇻🇳", "Vietnamese", supportedForTranslation = false);

    /**
     * Whether the corpus of [source] is translated into this language, so that this one may be
     * offered to a learner of [source] and requested from `/word` for words of it. A language is
     * never a target for itself.
     *
     * Every place that offers, requests or lists a translation decides through this pair check -
     * onboarding, Settings, word fetches, the remote DB listing and the server's `/word` route - so
     * a target that exists for English only (see [translationSourceCodes]) stays invisible to a
     * learner of Russian.
     */
    fun isTranslationTargetFor(source: Language): Boolean =
        this != source &&
            supportedForTranslation &&
            source.supportedForLearning &&
            (translationSourceCodes?.contains(source.code) ?: true)

    /**
     * Separator for enumerating words written in this language, such as the translation glosses
     * that make up a sense title.
     *
     * Chinese and Japanese enumerate with the ideographic comma and no trailing space; a Latin
     * `", "` there reads as foreign punctuation next to the corpus text, which already uses `、`
     * inside its own definitions. Every other supported language enumerates the Latin/Cyrillic way.
     */
    val wordSeparator: String
        get() = when (this) {
            CHINESE, JAPANESE -> "、"
            else -> ", "
        }

    companion object {
        /** The languages that can be studied. See [supportedForLearning]. */
        val learningLanguages: List<Language> get() = entries.filter { it.supportedForLearning }

        /**
         * The languages that some learning language is translated into. This is the union over all
         * learning languages; a picker for a particular learner uses [translationTargetsFor].
         */
        val translationTargets: List<Language> get() = translationTargetsFor(learningLanguages)

        /** The languages a learner of [source] can be offered as translation targets, in enum order. */
        fun translationTargetsFor(source: Language): List<Language> =
            entries.filter { it.isTranslationTargetFor(source) }

        /** The union of [translationTargetsFor] over [sources], in enum order. */
        fun translationTargetsFor(sources: Collection<Language>): List<Language> =
            entries.filter { target -> sources.any { target.isTranslationTargetFor(it) } }

        fun fromCode(code: String): Language {
            return entries.find { it.code == code }
                ?: throw IllegalArgumentException("Unknown language code: $code")
        }

        fun fromCodeOrNull(code: String): Language? {
            return entries.find { it.code == code }
        }
    }
}
