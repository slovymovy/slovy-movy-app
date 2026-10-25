package com.slovy.slovymovyapp.data.learning

import com.slovy.slovymovyapp.data.learning.fsrs.FsrsDefaults
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Families are stored by ordinal in app.db (kinds too; their order is frozen in LearningDomainTest).
 * A new family may only be appended, and its place decides the direction of cross-family credit.
 */
class CardPersistenceOrderTest {

    @Test
    fun cardFamilyOrdinals_arePersistedValues() {
        assertEquals(
            listOf(
                CardFamily.RECOGNIZE_SENSE,
                CardFamily.PRODUCE_WORD,
                CardFamily.PRODUCE_WORD_IN_CONTEXT,
                CardFamily.RECOGNIZE_VOICE,
                CardFamily.SPELL_WORD,
            ),
            CardFamily.entries.toList(),
            "CardFamily order is stored in card.family; append only",
        )
    }

    @Test
    fun spellWord_isLastSoCreditIntoItIsForward() {
        // Credit direction is read off the ordinals: into a later family is forward (short delay),
        // out of it is backward. Spelling must therefore sit after every family that unlocks it.
        FsrsDefaults.UNLOCK_RULES
            .filter { it.targetFamily == CardFamily.SPELL_WORD }
            .forEach { rule ->
                assertTrue(
                    rule.sourceFamily.ordinal < CardFamily.SPELL_WORD.ordinal,
                    "${rule.sourceFamily} must precede SPELL_WORD so its unlock credit counts as forward",
                )
            }
    }

    @Test
    fun everyUnlockRule_hasMatchingCredit() {
        FsrsDefaults.UNLOCK_RULES.forEach { rule ->
            assertTrue(
                FsrsDefaults.CROSS_FAMILY_CREDITS.any {
                    it.sourceFamily == rule.sourceFamily && it.targetFamily == rule.targetFamily
                },
                "Unlock ${rule.sourceFamily} -> ${rule.targetFamily} needs a credit entry to inherit stability from",
            )
        }
    }
}
