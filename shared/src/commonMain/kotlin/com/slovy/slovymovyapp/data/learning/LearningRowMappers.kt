package com.slovy.slovymovyapp.data.learning

import com.slovy.slovymovyapp.db.Card as DbCard
import kotlin.uuid.Uuid

fun DbCard.toCard(): Card = card(
    id, sense_id, lemma_id, lang_code, family, state, stability,
    difficulty, due, last_review, reps, lapses, created_at,
    available_after, answer_key, suspended,
)

private fun card(
    id: Uuid,
    senseId: Uuid,
    lemmaId: Uuid,
    langCode: String,
    family: CardFamily,
    state: CardState,
    stability: Double,
    difficulty: Double,
    due: Long,
    lastReview: Long?,
    reps: Long,
    lapses: Long,
    createdAt: Long,
    availableAfter: Long?,
    answerKey: String,
    suspended: Boolean,
): Card = Card(
    id = id,
    senseId = senseId,
    lemmaId = lemmaId,
    langCode = langCode,
    family = family,
    answerKey = answerKey,
    scheduling = CardScheduling(
        state = state,
        stability = stability,
        difficulty = difficulty,
        dueEpochMs = due,
        lastReviewEpochMs = lastReview,
        reps = reps,
        lapses = lapses,
        createdAtEpochMs = createdAt,
        availableAfterEpochMs = availableAfter,
        suspended = suspended,
    ),
)
