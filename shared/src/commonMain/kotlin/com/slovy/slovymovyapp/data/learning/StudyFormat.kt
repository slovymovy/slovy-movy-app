package com.slovy.slovymovyapp.data.learning

/**
 * The card formats a learner may switch off: the easiest way a word comes back and the hardest.
 * Every other way is always on; see [CardKind.format].
 */
enum class StudyFormat {
    /** The word is shown and its translation recalled: the first card a word gets. */
    WORD_TO_TRANSLATION,

    /**
     * The two definition kinds: explained in the studied language, with no translation anywhere.
     * To the learner they are one idea. The source-sentence cloze is not one of them: it needs a
     * translation target for its hint and its back.
     */
    SOURCE_ONLY,
}

/** The format that switches this kind off, or null for a kind that is always on. */
val CardKind.format: StudyFormat?
    get() = when (this) {
        CardKind.WORD_TO_TRANSLATION -> StudyFormat.WORD_TO_TRANSLATION
        CardKind.WORD_TO_SOURCE_DEFINITION,
        CardKind.SOURCE_DEFINITION_TO_WORD,
            -> StudyFormat.SOURCE_ONLY

        CardKind.TRANSLATION_TO_WORD,
        CardKind.CLOZE_TRANSLATION,
        CardKind.CLOZE_SOURCE,
        CardKind.LISTENING_TRANSLATION,
        CardKind.TRANSLATION_TO_WORD_TYPED,
            -> null
    }

/**
 * The formats a learner studies a language with. It filters what a session serves and what the
 * unlock rules create; it never touches a card that already exists, so switching a format off and
 * on leaves every card, review and stability exactly as it was.
 *
 * Both formats may be off: the always-on kinds keep every language studiable. Without translation
 * targets ([playsTranslationKinds] false) only the source-only kinds can be played, since every
 * other kind cues or answers with a translation; then the source-only format is the whole
 * selection, whatever is stored.
 */
data class StudyFormatSelection(
    val enabled: Set<StudyFormat>,
    val playsTranslationKinds: Boolean,
) {
    fun allows(kind: CardKind): Boolean {
        val format = kind.format
        if (format != null && format !in enabled) return false
        return playsTranslationKinds || format == StudyFormat.SOURCE_ONLY
    }

    /** The task families with at least one playable kind, in ladder order. */
    val families: List<CardFamily> = CardFamily.entries.filter { family ->
        CardKind.entries.any { it.family == family && allows(it) }
    }

    val excludedFamilies: Set<CardFamily> = CardFamily.entries.toSet() - families.toSet()

    /**
     * The families a new word starts in: the [preferred] ones that are studied, or the first
     * studied family of the ladder when none of them is.
     */
    fun intakeFamilies(preferred: List<CardFamily>): List<CardFamily> =
        preferred.filter { it in families }.ifEmpty { families.take(1) }

    fun with(format: StudyFormat, enabled: Boolean): StudyFormatSelection =
        copy(enabled = if (enabled) this.enabled + format else this.enabled - format)

    companion object {
        /** Every format on, for a language with translation targets. */
        val ALL: StudyFormatSelection = StudyFormatSelection(StudyFormat.entries.toSet(), playsTranslationKinds = true)
    }
}
