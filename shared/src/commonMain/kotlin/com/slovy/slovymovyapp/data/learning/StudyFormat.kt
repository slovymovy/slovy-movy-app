package com.slovy.slovymovyapp.data.learning

/**
 * The ways a word can come back in a study session, as the learner sees them. The declaration
 * order is the ladder: a word unlocks these from top to bottom as it sticks.
 *
 * The three source-language-only kinds share one format: to the learner they are one idea, "no
 * translation anywhere", and they sit behind the same stability gate.
 */
enum class StudyFormat {
    WORD_TO_TRANSLATION,
    TRANSLATION_TO_WORD,
    FILL_THE_GAP,
    LISTEN,
    SOURCE_ONLY,
}

val CardKind.format: StudyFormat
    get() = when (this) {
        CardKind.WORD_TO_TRANSLATION -> StudyFormat.WORD_TO_TRANSLATION
        CardKind.TRANSLATION_TO_WORD -> StudyFormat.TRANSLATION_TO_WORD
        CardKind.CLOZE_TRANSLATION -> StudyFormat.FILL_THE_GAP
        CardKind.LISTENING_TRANSLATION -> StudyFormat.LISTEN
        CardKind.WORD_TO_SOURCE_DEFINITION,
        CardKind.SOURCE_DEFINITION_TO_WORD,
        CardKind.CLOZE_SOURCE,
            -> StudyFormat.SOURCE_ONLY
    }

/**
 * The formats a learner studies a language with. It filters what a session serves and what the
 * ladder creates; it never touches a card that already exists, so switching a format off and on
 * leaves every card, review and stability exactly as it was.
 *
 * At least one format is always enabled: a selection with none would make a language unstudiable.
 */
data class StudyFormatSelection(val enabled: Set<StudyFormat>) {
    init {
        require(enabled.isNotEmpty()) { "A study format selection needs at least one format" }
    }

    fun allows(kind: CardKind): Boolean = kind.format in enabled

    /** The task families with at least one enabled kind, in ladder order. */
    val families: List<CardFamily> = CardFamily.entries.filter { family ->
        CardKind.entries.any { it.family == family && it.format in enabled }
    }

    val excludedFamilies: Set<CardFamily> = CardFamily.entries.toSet() - families.toSet()

    /** The next enabled family after [family] on the ladder, if any. */
    fun nextFamilyAfter(family: CardFamily): CardFamily? =
        families.firstOrNull { it.ordinal > family.ordinal }

    /**
     * The families a new word starts in: the [preferred] ones that are enabled, or the first
     * enabled family of the ladder when none of them is.
     */
    fun intakeFamilies(preferred: List<CardFamily>): List<CardFamily> =
        preferred.filter { it in families }.ifEmpty { families.take(1) }

    fun with(format: StudyFormat, enabled: Boolean): StudyFormatSelection? {
        val next = if (enabled) this.enabled + format else this.enabled - format
        return if (next.isEmpty()) null else StudyFormatSelection(next)
    }

    companion object {
        val ALL: StudyFormatSelection = StudyFormatSelection(StudyFormat.entries.toSet())
    }
}
