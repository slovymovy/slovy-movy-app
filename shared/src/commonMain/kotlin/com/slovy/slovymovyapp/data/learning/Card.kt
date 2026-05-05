package com.slovy.slovymovyapp.data.learning

import kotlin.uuid.Uuid

data class Card(
    val id: Uuid,
    val senseId: Uuid,
    val lemmaId: Uuid,
    val langCode: String,
    val family: CardFamily,
    val answerKey: String,
    val scheduling: CardScheduling,
)

enum class CardFamily(
    val testsWordRecall: Boolean,
) {
    RECOGNIZE_SENSE(testsWordRecall = false),
    PRODUCE_WORD(testsWordRecall = true),
    PRODUCE_WORD_IN_CONTEXT(testsWordRecall = true),
    RECOGNIZE_VOICE(testsWordRecall = true),
}

enum class CardKind(
    val requiresTranslation: Boolean,
    val isCloze: Boolean,
    val family: CardFamily,
) {
    SOURCE_DEFINITION_TO_WORD(
        requiresTranslation = false,
        isCloze = false,
        family = CardFamily.PRODUCE_WORD,
    ),
    WORD_TO_SOURCE_DEFINITION(
        requiresTranslation = false,
        isCloze = false,
        family = CardFamily.RECOGNIZE_SENSE,
    ),
    CLOZE_SOURCE(
        requiresTranslation = false,
        isCloze = true,
        family = CardFamily.PRODUCE_WORD_IN_CONTEXT,
    ),
    TRANSLATION_TO_WORD(
        requiresTranslation = true,
        isCloze = false,
        family = CardFamily.PRODUCE_WORD,
    ),
    WORD_TO_TRANSLATION(
        requiresTranslation = true,
        isCloze = false,
        family = CardFamily.RECOGNIZE_SENSE,
    ),
    CLOZE_TRANSLATION(
        requiresTranslation = true,
        isCloze = true,
        family = CardFamily.PRODUCE_WORD_IN_CONTEXT,
    ),
    LISTENING_TRANSLATION(
        requiresTranslation = true,
        isCloze = false,
        family = CardFamily.RECOGNIZE_VOICE,
    ),
}

data class CardVariant(
    val kind: CardKind,
    val targetLang: String?,
)

data class CardScheduling(
    val state: CardState,
    val stability: Double,
    val difficulty: Double,
    val dueEpochMs: Long,
    val lastReviewEpochMs: Long?,
    val reps: Long,
    val lapses: Long,
    val createdAtEpochMs: Long,
    val availableAfterEpochMs: Long?,
    val suspended: Boolean,
)

enum class CardState {
    NEW,
    LEARNING,
    REVIEW,
    RELEARNING,
}
