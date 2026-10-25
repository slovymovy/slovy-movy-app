package com.slovy.slovymovyapp.data.learning

import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.db.DatabaseProvider
import com.slovy.slovymovyapp.data.favorites.FavoritesRepository
import com.slovy.slovymovyapp.data.learning.fsrs.FsrsDefaults
import com.slovy.slovymovyapp.data.settings.SettingsRepository
import com.slovy.slovymovyapp.db.AppDatabase
import com.slovy.slovymovyapp.test.BaseTest
import com.slovy.slovymovyapp.test.testPlatformDbSupport
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.uuid.Uuid

@OptIn(ExperimentalTime::class)
class SpellingCardBackfillTest : BaseTest() {
    private val lang = Language.DUTCH.code
    private val now = Instant.fromEpochMilliseconds(1_700_000_000_000L)
    private val clock = object : Clock {
        override fun now(): Instant = now
    }

    private fun withDatabase(block: suspend (AppDatabase) -> Unit) = runBlocking {
        val platform = testPlatformDbSupport()
        val driver = platform.createAppDataDriver(platform.getDatabasePath("${Uuid.random()}-backfill-test.db"))
        try {
            block(DatabaseProvider.createAppDatabase(driver))
        } finally {
            driver.close()
        }
    }

    private fun AppDatabase.insert(
        sense: Uuid,
        family: CardFamily,
        stability: Double,
        difficulty: Double = 5.0,
        due: Long = now.toEpochMilliseconds() + 10.days.inWholeMilliseconds,
        suspended: Boolean = false,
    ) {
        favoritesQueries.insertCard(
            id = Uuid.random(),
            sense_id = sense,
            lemma_id = Uuid.random(),
            lang_code = lang,
            family = family,
            state = CardState.REVIEW,
            stability = stability,
            difficulty = difficulty,
            due = due,
            last_review = now.toEpochMilliseconds() - 1.days.inWholeMilliseconds,
            reps = 5,
            lapses = 0,
            created_at = now.toEpochMilliseconds() - 100.days.inWholeMilliseconds,
            available_after = null,
            answer_key = "fiets",
            suspended = suspended,
        )
    }

    private fun AppDatabase.spellingCard(sense: Uuid) =
        favoritesQueries.selectCardBySenseAndFamily(sense, lang, CardFamily.SPELL_WORD).executeAsOneOrNull()

    private fun AppDatabase.backfill() =
        SpellingCardBackfill(FavoritesRepository(this), SettingsRepository(this), FsrsDefaults.UNLOCK_RULES, clock)

    @Test
    fun past_the_gate_senses_get_a_spelling_card_at_their_weakest_stability_due_with_produce_word() = withDatabase { db ->
        val learned = Uuid.random()
        val produceDue = now.toEpochMilliseconds() + 40.days.inWholeMilliseconds
        db.insert(learned, CardFamily.RECOGNIZE_SENSE, stability = 200.0, difficulty = 3.0)
        db.insert(learned, CardFamily.PRODUCE_WORD, stability = 150.0, difficulty = 6.0, due = produceDue)
        db.insert(learned, CardFamily.PRODUCE_WORD_IN_CONTEXT, stability = 120.0, difficulty = 4.0)

        db.backfill().runOnce()

        val spelling = assertNotNull(db.spellingCard(learned), "A sense past the spelling gate gets a spelling card")
        assertEquals(CardState.LEARNING, spelling.state, "A backfilled card starts in LEARNING like an unlock")
        assertEquals(120.0, spelling.stability, "A backfilled card starts at the sense's weakest stability")
        assertEquals(6.0, spelling.difficulty, "A backfilled card takes the produce-word card's difficulty")
        assertEquals(produceDue, spelling.due, "A backfilled card is due with its produce-word card")
        assertEquals(0, spelling.reps, "A backfilled card has never been reviewed")
        assertEquals("fiets", spelling.answer_key, "A backfilled card keeps the sense's answer key")
    }

    @Test
    fun senses_below_the_gate_already_spelled_or_removed_are_left_alone() = withDatabase { db ->
        val young = Uuid.random()
        db.insert(young, CardFamily.RECOGNIZE_SENSE, stability = 4.0)
        db.insert(young, CardFamily.PRODUCE_WORD, stability = 2.0)
        val recognizeOnly = Uuid.random()
        db.insert(recognizeOnly, CardFamily.RECOGNIZE_SENSE, stability = 50.0)
        val spelled = Uuid.random()
        db.insert(spelled, CardFamily.PRODUCE_WORD, stability = 10.0)
        db.insert(spelled, CardFamily.SPELL_WORD, stability = 7.0)
        val removed = Uuid.random()
        db.insert(removed, CardFamily.PRODUCE_WORD, stability = 10.0, suspended = true)

        db.backfill().runOnce()

        assertNull(db.spellingCard(young), "Below the gate spelling unlocks the regular way")
        assertNull(db.spellingCard(recognizeOnly), "A sense without a produce-word card gets no spelling card")
        assertEquals(7.0, db.spellingCard(spelled)?.stability, "An existing spelling card is untouched")
        assertNull(db.spellingCard(removed), "A removed favorite gets no spelling card")
    }

    @Test
    fun the_backfill_runs_only_once() = withDatabase { db ->
        db.backfill().runOnce()
        val later = Uuid.random()
        db.insert(later, CardFamily.PRODUCE_WORD, stability = 10.0)

        db.backfill().runOnce()

        assertNull(db.spellingCard(later), "After the first run, spelling is left to the regular unlock")
    }
}
