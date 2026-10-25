package com.slovy.slovymovyapp.data.learning

import com.slovy.slovymovyapp.data.favorites.FavoritesRepository
import com.slovy.slovymovyapp.data.learning.fsrs.UnlockRule
import com.slovy.slovymovyapp.data.settings.SettingsRepository
import com.slovy.slovymovyapp.logging.AppLogger
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Gives the senses that were already past the spelling unlock when spelling shipped their card,
 * once per install. A runtime unlock starts the card from the cross-family credit, which for a
 * long-learned word would sit far below its other cards and drop the sense to a lower stage; the
 * backfill starts it at the sense's weakest card instead, so existing progress stays where it
 * was until the learner fails to spell the word.
 */
@OptIn(ExperimentalTime::class)
internal class SpellingCardBackfill(
    private val favoritesRepository: FavoritesRepository,
    private val settingsRepository: SettingsRepository,
    unlockRules: List<UnlockRule>,
    private val clock: Clock,
) {
    private val rule: UnlockRule = unlockRules.single { it.targetFamily == CardFamily.SPELL_WORD }

    suspend fun runOnce() {
        if (settingsRepository.isSpellingBackfillDone()) return
        val created = favoritesRepository.backfillUnlockedFamily(rule, clock.now().toEpochMilliseconds())
        settingsRepository.setSpellingBackfillDone()
        AppLogger.info(TAG, "Spelling backfill created $created cards", null)
    }

    private companion object {
        const val TAG = "SpellingCardBackfill"
    }
}
