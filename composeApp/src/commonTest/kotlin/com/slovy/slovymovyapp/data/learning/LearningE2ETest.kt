package com.slovy.slovymovyapp.data.learning

import app.cash.sqldelight.db.SqlDriver
import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.db.DatabaseProvider
import com.slovy.slovymovyapp.data.dictionary.*
import com.slovy.slovymovyapp.data.dictionary.LearnerLevel
import com.slovy.slovymovyapp.data.dictionary.NameType
import com.slovy.slovymovyapp.data.dictionary.SenseFrequency
import com.slovy.slovymovyapp.data.favorites.FavoritesRepository
import com.slovy.slovymovyapp.data.learning.fsrs.FsrsConfig
import com.slovy.slovymovyapp.data.learning.fsrs.FsrsDefaults
import com.slovy.slovymovyapp.data.learning.fsrs.FsrsScheduler
import com.slovy.slovymovyapp.data.learning.intake.IntakeService
import com.slovy.slovymovyapp.data.learning.intake.SkipReason
import com.slovy.slovymovyapp.data.learning.session.ExamplePicker
import com.slovy.slovymovyapp.data.learning.session.SessionCard
import com.slovy.slovymovyapp.data.learning.session.SessionCardLoadState
import com.slovy.slovymovyapp.data.learning.session.SessionService
import com.slovy.slovymovyapp.data.learning.stats.StatsService
import com.slovy.slovymovyapp.data.local.LocalDbManager
import com.slovy.slovymovyapp.data.remote.*
import com.slovy.slovymovyapp.data.settings.Setting
import com.slovy.slovymovyapp.data.settings.SettingsRepository
import com.slovy.slovymovyapp.db.AppDatabase
import com.slovy.slovymovyapp.dictionary.DictionaryDatabase
import com.slovy.slovymovyapp.test.BaseTest
import com.slovy.slovymovyapp.test.TestContext
import com.slovy.slovymovyapp.test.testPlatformDbSupport
import com.slovy.slovymovyapp.test.testRemoteDataProvider
import com.slovy.slovymovyapp.translation.TranslationDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.abs
import kotlin.test.*
import kotlin.time.*
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

@OptIn(ExperimentalTime::class)
class LearningE2ETest : BaseTest() {
    private val start = Instant.fromEpochMilliseconds(1_700_000_000_000L)

    @Test
    fun favorites_repository_stores_user_intake_row() = runBlocking {
        withEnv(includeTranslation = false) { env ->
            val fixture = env.seedSense(lemma = "learn")

            env.favorites.add(fixture.senseId.toString(), Language.ENGLISH, "learn")

            val favorite =
                env.app.favoritesQueries.selectFavoriteWithActivation(fixture.senseId.toString(), "en").executeAsOne()
            assertEquals(fixture.senseId.toString(), favorite.sense_id)
            assertEquals("en", favorite.lang_code)
            assertEquals("learn", favorite.lemma)
            assertNull(favorite.activated_at)
        }
    }

    @Test
    fun intake_without_configured_translation_creates_recognition_task() = runBlocking {
        withEnv(includeTranslation = false) { env ->
            val fixture = env.seedSense(lemma = "sourceonly")
            env.addFavorite(fixture)

            val result = env.intake.runIntake("en")

            assertEquals(1, result.cardsCreated)
            val cards = env.app.favoritesQueries.selectCardsByFavorite(fixture.senseId, "en").executeAsList()
            assertEquals(listOf(CardFamily.RECOGNIZE_SENSE), cards.map { it.family })
            assertNotNull(
                env.app.favoritesQueries.selectFavoriteWithActivation(fixture.senseId.toString(), "en")
                    .executeAsOne().activated_at
            )
        }
    }

    @Test
    fun intake_with_translation_creates_recognition_task() = runBlocking {
        withEnv(includeTranslation = true) { env ->
            val fixture = env.seedSense(lemma = "translated")
            env.seedTranslation(fixture.senseId, fixture.lemmaPosId)
            env.addFavorite(fixture)

            val result = env.intake.runIntake("en")

            assertEquals(1, result.cardsCreated)
            val cards = env.app.favoritesQueries.selectCardsByFavorite(fixture.senseId, "en").executeAsList()
            assertEquals(listOf(CardFamily.RECOGNIZE_SENSE), cards.map { it.family })
        }
    }

    @Test
    fun intake_is_idempotent() = runBlocking {
        withEnv(includeTranslation = true) { env ->
            val fixture = env.seedSense(lemma = "repeatfixtureword")
            env.seedTranslation(fixture.senseId, fixture.lemmaPosId)
            env.addFavorite(fixture)

            val first = env.intake.runIntake("en")
            val second = env.intake.runIntake("en")

            assertEquals(1, first.cardsCreated)
            assertEquals(0, second.cardsCreated)
            assertEquals(1, env.app.favoritesQueries.selectCardsByFavorite(fixture.senseId, "en").executeAsList().size)
        }
    }

    @Test
    fun removing_favorite_suspends_learning_cards_and_readding_restores_them() = runBlocking {
        withEnv(includeTranslation = false) { env ->
            val fixture = env.seedSense(lemma = "restoresuspendcards")
            env.addFavorite(fixture)
            env.intake.runIntake("en")
            val cards = env.app.favoritesQueries.selectCardsByFavorite(fixture.senseId, "en").executeAsList()
            val availableAfter = (start + 10.minutes).toEpochMilliseconds()
            env.app.favoritesQueries.setCardAvailableAfter(
                availableAfter = availableAfter,
                id = cards.first().id,
            )

            env.favorites.remove(fixture.senseId.toString(), Language.ENGLISH)

            assertFalse(env.favorites.exists(fixture.senseId.toString(), Language.ENGLISH))
            val suspendedCards = env.app.favoritesQueries.selectCardsByFavorite(fixture.senseId, "en").executeAsList()
            assertEquals(1, suspendedCards.size)
            assertTrue(suspendedCards.all { it.suspended })
            assertTrue(suspendedCards.all { it.available_after == null })
            assertNull(env.session.nextCard("en", start).first())

            env.favorites.add(fixture.senseId.toString(), Language.ENGLISH, fixture.lemma)

            val restoredFavorite = env.app.favoritesQueries
                .selectFavoriteWithActivation(fixture.senseId.toString(), "en")
                .executeAsOne()
            assertNotNull(restoredFavorite.activated_at)
            val restoredCards = env.app.favoritesQueries.selectCardsByFavorite(fixture.senseId, "en").executeAsList()
            assertEquals(1, restoredCards.size)
            assertTrue(restoredCards.none { it.suspended })
            assertTrue(env.nextLoadedCard("en").isReady())
        }
    }

    @Test
    fun intake_respects_task_budget() = runBlocking {
        val config = FsrsDefaults.config().copy(dailyNewTaskFamilyBudget = 1)
        withEnv(includeTranslation = true, config = config) { env ->
            val first = env.seedSense(lemma = "firstbudget")
            val second = env.seedSense(lemma = "secondbudget")
            env.addFavorite(first, createdAt = start.toEpochMilliseconds())
            env.addFavorite(second, createdAt = (start + 1.milliseconds).toEpochMilliseconds())

            val result = env.intake.runIntake("en")

            assertEquals(1, result.cardsCreated)
            assertEquals(1, env.app.favoritesQueries.selectCardsByFavorite(first.senseId, "en").executeAsList().size)
            assertEquals(0, env.app.favoritesQueries.selectCardsByFavorite(second.senseId, "en").executeAsList().size)
            assertNotNull(
                env.app.favoritesQueries
                    .selectFavoriteWithActivation(first.senseId.toString(), "en")
                    .executeAsOne()
                    .activated_at,
            )
        }
    }

    @Test
    fun intake_activates_pending_favorites_of_same_lemma_together() = runBlocking {
        withEnv(includeTranslation = false) { env ->
            val first = env.seedSense(lemma = "polysamefirst")
            val second = env.seedAdditionalSense(first)
            env.addFavorite(first, createdAt = start.toEpochMilliseconds())
            env.addFavorite(second, createdAt = (start + 1.milliseconds).toEpochMilliseconds())

            val result = env.intake.runIntake("en")

            assertEquals(2, result.cardsCreated)
            assertTrue(result.skipped.isEmpty(), "unexpected skips: ${result.skipped}")
            assertEquals(setOf(first.senseId, second.senseId), result.activated.toSet())
            assertEquals(
                1,
                env.app.favoritesQueries.selectCardsByFavorite(first.senseId, "en").executeAsList().size,
            )
            assertEquals(
                1,
                env.app.favoritesQueries.selectCardsByFavorite(second.senseId, "en").executeAsList().size,
            )
            assertNotNull(
                env.app.favoritesQueries.selectFavoriteWithActivation(first.senseId.toString(), "en")
                    .executeAsOne().activated_at,
            )
            assertNotNull(
                env.app.favoritesQueries.selectFavoriteWithActivation(second.senseId.toString(), "en")
                    .executeAsOne().activated_at,
            )
        }
    }

    @Test
    fun intake_skips_lemma_group_when_total_exceeds_budget() = runBlocking {
        val config = FsrsDefaults.config().copy(dailyNewTaskFamilyBudget = 1)
        withEnv(includeTranslation = false, config = config) { env ->
            val first = env.seedSense(lemma = "groupbudget")
            val second = env.seedAdditionalSense(first)
            env.addFavorite(first, createdAt = start.toEpochMilliseconds())
            env.addFavorite(second, createdAt = (start + 1.milliseconds).toEpochMilliseconds())

            val result = env.intake.runIntake("en")

            assertEquals(0, result.cardsCreated)
            assertTrue(result.activated.isEmpty(), "unexpected activated: ${result.activated}")
            assertEquals(listOf(SkipReason.BUDGET_EXHAUSTED), result.skipped)
            assertEquals(
                0,
                env.app.favoritesQueries.selectCardsByFavorite(first.senseId, "en").executeAsList().size,
            )
            assertEquals(
                0,
                env.app.favoritesQueries.selectCardsByFavorite(second.senseId, "en").executeAsList().size,
            )
            assertNull(
                env.app.favoritesQueries.selectFavoriteWithActivation(first.senseId.toString(), "en")
                    .executeAsOne().activated_at,
            )
            assertNull(
                env.app.favoritesQueries.selectFavoriteWithActivation(second.senseId.toString(), "en")
                    .executeAsOne().activated_at,
            )
        }
    }

    @Test
    fun intake_activates_smaller_group_when_larger_group_overflows_budget() = runBlocking {
        val config = FsrsDefaults.config().copy(dailyNewTaskFamilyBudget = 1)
        withEnv(includeTranslation = false, config = config) { env ->
            val pairFirst = env.seedSense(lemma = "twoseat")
            val pairSecond = env.seedAdditionalSense(pairFirst)
            val solo = env.seedSense(lemma = "oneseat")
            env.addFavorite(pairFirst, createdAt = start.toEpochMilliseconds())
            env.addFavorite(pairSecond, createdAt = (start + 1.milliseconds).toEpochMilliseconds())
            env.addFavorite(solo, createdAt = (start + 2.milliseconds).toEpochMilliseconds())

            val result = env.intake.runIntake("en")

            assertEquals(1, result.cardsCreated)
            assertEquals(listOf(solo.senseId), result.activated)
            assertEquals(listOf(SkipReason.BUDGET_EXHAUSTED), result.skipped)
            assertEquals(
                0,
                env.app.favoritesQueries.selectCardsByFavorite(pairFirst.senseId, "en").executeAsList().size,
            )
            assertEquals(
                0,
                env.app.favoritesQueries.selectCardsByFavorite(pairSecond.senseId, "en").executeAsList().size,
            )
            assertEquals(
                1,
                env.app.favoritesQueries.selectCardsByFavorite(solo.senseId, "en").executeAsList().size,
            )
            assertNull(
                env.app.favoritesQueries.selectFavoriteWithActivation(pairFirst.senseId.toString(), "en")
                    .executeAsOne().activated_at,
            )
            assertNotNull(
                env.app.favoritesQueries.selectFavoriteWithActivation(solo.senseId.toString(), "en")
                    .executeAsOne().activated_at,
            )
        }
    }

    @Test
    fun intake_creates_recognition_task_before_translation_exists() = runBlocking {
        withEnv(includeTranslation = true) { env ->
            val fixture = env.seedSense(lemma = "configured")
            env.addFavorite(fixture)
            val result = env.intake.runIntake("en")

            val card = env.app.favoritesQueries.selectCardsByFavorite(fixture.senseId, "en").executeAsList().single()
            assertEquals(1, result.cardsCreated)
            assertEquals(CardFamily.RECOGNIZE_SENSE, card.family)
        }
    }

    @Test
    fun intake_reports_unavailable_data_when_favorite_card_data_is_missing() = runBlocking {
        withEnv(includeTranslation = false) { env ->
            val missingSenseId = Uuid.parse("00000000-0000-0000-0000-000000000401")
            env.favorites.add(missingSenseId.toString(), Language.ENGLISH, "missingcarddata")

            val result = env.intake.runIntake("en")

            assertEquals(0, result.cardsCreated)
            assertEquals(listOf(SkipReason.CARD_DATA_UNAVAILABLE), result.skipped)
            assertNull(
                env.app.favoritesQueries
                    .selectFavoriteWithActivation(missingSenseId.toString(), "en")
                    .executeAsOne()
                    .activated_at,
            )
        }
    }

    @Test
    fun learning_flow_reviews_card_buries_siblings_and_updates_stats() = runBlocking {
        withEnv(includeTranslation = true) { env ->
            val fixture = env.seedSense(lemma = "sessionfixtureword")
            env.seedTranslation(fixture.senseId, fixture.lemmaPosId)
            env.addFavorite(fixture)
            env.intake.runIntake("en")

            val card = env.nextLoadedCard("en")
            val preview = env.session.previewRatings(card)
            assertEquals(Rating.entries.toList(), preview.map { it.rating })
            env.clock.advance(2.minutes)

            env.session.submitReview(
                card,
                preview.first { it.rating == Rating.GOOD },
                durationMs = 2.seconds.inWholeMilliseconds
            )

            val reviewed = env.app.favoritesQueries.selectCardById(card.card.id).executeAsOne()
            assertEquals(1, reviewed.reps)
            assertNotNull(reviewed.last_review)
            assertTrue(reviewed.due > start.toEpochMilliseconds())
            assertEquals(1, env.app.favoritesQueries.selectReviewLogsByCard(card.card.id, 10).executeAsList().size)
            val siblings = env.app.favoritesQueries.selectCardsByFavorite(fixture.senseId, "en").executeAsList()
                .filter { it.id != card.card.id }
            assertTrue(siblings.isNotEmpty())
            assertTrue(siblings.all { assertNotNull(it.available_after) > (start + 2.minutes).toEpochMilliseconds() })

            val global = env.stats.globalStats("en")
            assertEquals(2, global.totalCards)
            assertEquals(1, global.reviewedLast7d)
        }
    }

    @Test
    fun review_log_records_elapsed_and_scheduled_days_from_ms_fields() = runBlocking {
        withEnv(includeTranslation = false) { env ->
            val fixture = env.seedSense(lemma = "elapsedfixture")
            env.addFavorite(fixture)
            env.insertTask(
                fixture = fixture,
                family = CardFamily.RECOGNIZE_SENSE,
                state = CardState.REVIEW,
                stability = 4.0,
                difficulty = 5.0,
                due = start.toEpochMilliseconds(),
                lastReview = (start - 3.days).toEpochMilliseconds(),
                reps = 3,
            )

            val card = env.nextLoadedCard("en")
            env.session.submitReview(
                card,
                env.session.previewRatings(card).first { it.rating == Rating.GOOD },
                durationMs = 1.seconds.inWholeMilliseconds,
            )

            val log = env.app.favoritesQueries.selectReviewLogsByCard(card.card.id, 10).executeAsList().single()
            assertEquals(3L, log.elapsed_days)
            assertEquals(3L, log.scheduled_days)
        }
    }

    @Test
    fun again_review_does_not_bury_siblings() = runBlocking {
        withEnv(includeTranslation = false) { env ->
            val fixture = env.seedSense(lemma = "againfixtureword")
            env.addFavorite(fixture)
            env.insertTask(
                fixture = fixture,
                family = CardFamily.RECOGNIZE_SENSE,
                cardId = Uuid.parse("00000000-0000-0000-0000-000000000201"),
            )
            env.insertTask(
                fixture = fixture,
                family = CardFamily.PRODUCE_WORD,
                cardId = Uuid.parse("00000000-0000-0000-0000-000000000202"),
            )
            env.insertTask(
                fixture = fixture,
                family = CardFamily.PRODUCE_WORD_IN_CONTEXT,
                cardId = Uuid.parse("00000000-0000-0000-0000-000000000203"),
            )

            val card = env.nextLoadedCard("en")
            env.session.submitReview(
                card,
                env.session.previewRatings(card).first { it.rating == Rating.AGAIN },
                durationMs = 1.seconds.inWholeMilliseconds,
            )

            val siblings = env.app.favoritesQueries.selectCardsByFavorite(fixture.senseId, "en").executeAsList()
                .filter { it.id != card.card.id }
            assertEquals(2, siblings.size)
            assertTrue(siblings.all { it.available_after == null })
        }
    }

    @Test
    fun again_review_does_not_repeat_card_before_displayed_interval() = runBlocking {
        withEnv(includeTranslation = false) { env ->
            val fixture = env.seedSense(lemma = "againintervalfixture")
            env.addFavorite(fixture)
            env.insertTask(
                fixture = fixture,
                family = CardFamily.RECOGNIZE_SENSE,
            )

            val card = env.nextLoadedCard("en")
            val again = env.session.previewRatings(card).first { it.rating == Rating.AGAIN }
            env.session.submitReview(card, again, durationMs = 1.seconds.inWholeMilliseconds)

            val reviewed = env.app.favoritesQueries.selectCardById(card.card.id).executeAsOne()
            assertEquals(reviewed.due, reviewed.available_after)
            assertNull(env.session.nextCard("en", start).first())

            env.clock.advance(again.intervalMillis.toDuration(DurationUnit.MILLISECONDS))

            val repeated = assertNotNull(
                env.session.nextCard("en", env.clock.now())
                    .first { it == null || it.loadState() != SessionCardLoadState.LOADING }
            )
            assertEquals(card.card.id, repeated.card.id)
        }
    }

    @Test
    fun again_review_does_not_unlock_next_family_even_when_stability_is_eligible() = runBlocking {
        val config = FsrsDefaults.config().copy(
            productionUnlockStability = 0.minutes,
        )
        withEnv(includeTranslation = false, config = config) { env ->
            val fixture = env.seedSense(lemma = "againunlockfixture")
            env.addFavorite(fixture)
            env.insertTask(
                fixture = fixture,
                family = CardFamily.RECOGNIZE_SENSE,
                state = CardState.REVIEW,
                stability = 30.0,
                due = (start - 1.milliseconds).toEpochMilliseconds(),
                lastReview = (start - 30.minutes).toEpochMilliseconds(),
                reps = 5,
            )

            val card = env.nextLoadedCard("en")
            env.session.submitReview(
                card,
                env.session.previewRatings(card).first { it.rating == Rating.AGAIN },
                durationMs = 1.seconds.inWholeMilliseconds,
            )

            val cards = env.app.favoritesQueries.selectCardsByFavorite(fixture.senseId, "en").executeAsList()
            assertEquals(1, cards.size)
            assertEquals(CardFamily.RECOGNIZE_SENSE, cards.single().family)
        }
    }

    @Test
    fun same_lemma_different_sense_waits_for_session_cooldown() = runBlocking {
        val config = FsrsDefaults.config().copy(
            sameSenseCooldownRatio = 0.0,
            sameLemmaCooldownRatio = 0.15,
            sameAnswerCooldownRatio = 0.0,
            siblingCooldownFloor = 10.minutes,
            siblingCooldownCap = 10.minutes,
            cooldownJitterRatio = 0.0,
        )
        withEnv(includeTranslation = false, config = config) { env ->
            val firstSense = env.seedSense(lemma = "polyseme")
            val secondSense = env.seedAdditionalSense(firstSense)
            env.addFavorite(firstSense, createdAt = start.toEpochMilliseconds())
            env.addFavorite(secondSense, createdAt = (start + 1.milliseconds).toEpochMilliseconds())
            env.insertTask(
                fixture = firstSense,
                family = CardFamily.RECOGNIZE_SENSE,
            )
            env.insertTask(
                fixture = secondSense,
                family = CardFamily.RECOGNIZE_SENSE,
            )

            val firstCard = assertNotNull(
                env.session.nextCard("en", start)
                    .first { it == null || it.loadState() != SessionCardLoadState.LOADING }
            )
            val expectedSenseIds = setOf(firstSense.senseId.toString(), secondSense.senseId.toString())
            assertTrue(firstCard.senseId in expectedSenseIds)
            env.clock.advance(1.minutes)
            env.session.submitReview(
                firstCard,
                env.session.previewRatings(firstCard).first { it.rating == Rating.GOOD },
                durationMs = 1.seconds.inWholeMilliseconds,
            )

            assertNull(env.session.nextCard("en", start).first())

            env.clock.advance(11.minutes)
            val secondCard = assertNotNull(
                env.session.nextCard("en", start)
                    .first { it == null || it.loadState() != SessionCardLoadState.LOADING }
            )
            assertEquals(expectedSenseIds - firstCard.senseId, setOf(secondCard.senseId))
        }
    }

    @Test
    fun same_sense_siblings_get_per_card_jittered_cooldowns() = runBlocking {
        val config = FsrsDefaults.config().copy(
            sameSenseCooldownRatio = 0.15,
            sameLemmaCooldownRatio = 0.0,
            sameAnswerCooldownRatio = 0.0,
            siblingCooldownFloor = 10.minutes,
            siblingCooldownCap = 10.minutes,
            cooldownJitterRatio = 0.5,
        )
        withEnv(includeTranslation = false, config = config) { env ->
            val fixture = env.seedSense(lemma = "jitterfixture")
            env.addFavorite(fixture)
            env.insertTask(
                fixture = fixture,
                family = CardFamily.RECOGNIZE_SENSE,
                cardId = Uuid.parse("00000000-0000-0000-0000-000000000101"),
            )
            env.insertTask(
                fixture = fixture,
                family = CardFamily.PRODUCE_WORD,
                cardId = Uuid.parse("00000000-0000-0000-0000-000000000102"),
            )
            env.insertTask(
                fixture = fixture,
                family = CardFamily.PRODUCE_WORD_IN_CONTEXT,
                cardId = Uuid.parse("00000000-0000-0000-0000-000000000103"),
            )

            val reviewed = env.nextLoadedCard("en")
            env.session.submitReview(
                reviewed,
                env.session.previewRatings(reviewed).first { it.rating == Rating.GOOD },
                durationMs = 1.seconds.inWholeMilliseconds,
            )

            val siblingAvailableAfter = env.app.favoritesQueries.selectCardsByFavorite(fixture.senseId, "en")
                .executeAsList()
                .filter { it.id != reviewed.card.id }
                .map { assertNotNull(it.available_after) }
            assertEquals(2, siblingAvailableAfter.size)
            assertEquals(2, siblingAvailableAfter.toSet().size)
            assertTrue(
                siblingAvailableAfter.all {
                    it in (start + 5.minutes).toEpochMilliseconds()..
                            (start + 15.minutes).toEpochMilliseconds()
                }
            )
        }
    }

    @Test
    fun translation_card_loads_translation_content() = runBlocking {
        withEnv(includeTranslation = true) { env ->
            val fixture = env.seedSense(lemma = "contentfixtureword")
            env.seedTranslation(fixture.senseId, fixture.lemmaPosId)
            env.addFavorite(fixture)
            env.intake.runIntake("en")
            val translationCard = env.app.favoritesQueries.selectCardsByFavorite(fixture.senseId, "en")
                .executeAsList()
                .single()
            env.app.favoritesQueries.updateCardAfterReview(
                state = CardState.REVIEW,
                stability = translationCard.stability,
                difficulty = translationCard.difficulty,
                due = (start - 1.milliseconds).toEpochMilliseconds(),
                last_review = null,
                reps = 0,
                lapses = 0,
                id = translationCard.id,
            )
            env.insertReviewLog(translationCard.id, CardKind.WORD_TO_SOURCE_DEFINITION)

            val card = env.nextLoadedCard("en")

            assertEquals(CardKind.WORD_TO_TRANSLATION, card.variant.kind)
            val sense = assertNotNull(card.loadedSense())
            assertEquals(listOf("учиться"), sense.translations[Language.RUSSIAN].orEmpty().map { it.targetLangWord })
            assertEquals("to gain knowledge", sense.targetLangDefinitions[Language.RUSSIAN])
        }
    }

    @Test
    fun cloze_card_uses_tagged_example_occurrence() = runBlocking {
        withEnv(includeTranslation = true) { env ->
            val fixture = env.seedSense(lemma = "cloze")
            env.seedTranslation(fixture.senseId, fixture.lemmaPosId)
            env.addFavorite(fixture)
            env.intake.runIntake("en")
            // CLOZE_SOURCE is gated below 7 days of stability and ranks behind CLOZE_TRANSLATION;
            // bump stability past the gate and bury the translation variant so the source variant is picked.
            val clozeCard = env.insertTask(
                fixture = fixture,
                family = CardFamily.PRODUCE_WORD_IN_CONTEXT,
                stability = 8.0,
            )
            env.insertReviewLog(clozeCard.id, CardKind.CLOZE_TRANSLATION, Language.RUSSIAN.code)

            val card = env.nextLoadedCard("en")

            assertEquals(CardKind.CLOZE_SOURCE, card.variant.kind)
            val example = assertNotNull(card.example)
            assertEquals("I cloze every day.", example.text)
            assertEquals(2..6, example.clozeRange)
        }
    }

    @Test
    fun translation_cloze_card_uses_target_language_example_translation() = runBlocking {
        withEnv(includeTranslation = true) { env ->
            val fixture = env.seedSense(lemma = "translatedcloze")
            env.seedTranslation(fixture.senseId, fixture.lemmaPosId)
            env.addFavorite(fixture)
            env.intake.runIntake("en")
            val clozeCard = env.insertTask(
                fixture = fixture,
                family = CardFamily.PRODUCE_WORD_IN_CONTEXT,
            )
            env.insertReviewLog(clozeCard.id, CardKind.CLOZE_SOURCE, Language.RUSSIAN.code)

            val card = env.nextLoadedCard("en")

            assertEquals(CardKind.CLOZE_TRANSLATION, card.variant.kind)
            val example = assertNotNull(card.example)
            assertEquals("Я учусь каждый день.", example.text)
            assertEquals(2..6, example.clozeRange)
        }
    }

    @Test
    fun next_card_returns_null_when_favorite_disappears_mid_session() = runBlocking {
        withEnv(includeTranslation = false) { env ->
            val fixture = env.seedSense(lemma = "removedmidfixture")
            env.addFavorite(fixture)
            env.intake.runIntake("en")
            env.app.favoritesQueries.deleteFavorite(fixture.senseId.toString(), "en")

            assertNull(env.session.nextCard("en", start).first())
        }
    }

    private suspend fun withEnv(
        includeTranslation: Boolean,
        config: FsrsConfig = FsrsDefaults.config(),
        block: suspend (Env) -> Unit,
    ) {
        val platform = testPlatformDbSupport()
        val app = openApp(platform)
        val localDbManager = LocalDbManager(platform)
        localDbManager.deleteAll()
        val dictionary = localDbManager.openLocalDictionary()
        val translation = if (includeTranslation) localDbManager.openLocalTranslation() else null
        val settings = SettingsRepository(app.database)
        settings.insert(
            Setting(
                Setting.Name.LANGUAGE,
                if (includeTranslation) JsonPrimitive("ru") else JsonArray(emptyList()),
            )
        )
        val dataDbManager = DataDbManager(platform, settings, testRemoteDataProvider())
        val favorites = FavoritesRepository(app.database)
        val dictionaryRepository = DictionaryRepository(
            dataDbManager = dataDbManager,
            localDbManager = localDbManager,
            favoritesRepository = favorites,
            settingsRepository = settings,
        )
        val port = TestContext.getCiEnv("TEST_SERVER_PORT") ?: "9090"
        val host = TestContext.testServerHost()
        val dictionaryClient = DictionaryClient(
            platform = platform,
            dictionaryRepository = dictionaryRepository,
            localDbManager = localDbManager,
            dataDbManager = dataDbManager,
            baseUrl = "http://$host:$port",
        )
        val wordFetchManager = WordFetchManager(dictionaryClient)
        val clock = MutableClock(start)
        try {
            val scheduler = FsrsScheduler(config.requestRetention, config.weights, config.maximumInterval)
            val intake = IntakeService(
                learning = app.database.favoritesQueries,
                dictionary = dictionaryRepository,
                config = config,
                clock = clock,
            )
            val session = SessionService(
                learning = app.database.favoritesQueries,
                wordFetchManager = wordFetchManager,
                scheduler = scheduler,
                examplePicker = ExamplePicker(app.database.favoritesQueries),
                config = config,
                clock = clock,
                translationTargets = dictionaryRepository::defaultTranslationTargets,
            )
            val stats = StatsService(
                learning = app.database.favoritesQueries,
                clock = clock,
            )
            block(Env(app.database, dictionary, translation, intake, session, stats, favorites, clock))
        } finally {
            dataDbManager.closeAllReadOnlyDatabases()
            localDbManager.deleteAll()
            app.close()
        }
    }

    private fun Env.seedSense(lemma: String, includeExamples: Boolean = true): SenseFixture {
        val lemmaId = Uuid.random()
        val lemmaPosId = Uuid.random()
        val senseId = Uuid.random()
        val q = dictionary.dictionaryQueries
        q.insertLemma(lemmaId, "en", lemma, lemma, 6.2, false)
        q.insertLemmaPos(lemmaPosId, lemmaId, DictionaryPos.VERB)
        q.insertForm(Uuid.random(), lemmaPosId, lemma, lemma, FormSource.NATIVE)
        q.insertSense(
            sense_id = senseId,
            lemma_pos_id = lemmaPosId,
            sense_definition = "to learn something",
            learner_level = LearnerLevel.A1,
            frequency = SenseFrequency.HIGH,
            semantic_group_id = "sg",
            name_type = NameType.NO,
        )
        if (includeExamples) {
            q.insertSenseExample(senseId, 1, "I <w>$lemma</w> every day.")
            q.insertSenseExample(senseId, 2, "They <w>$lemma</w> quickly.")
        }
        return SenseFixture(lemmaId, lemmaPosId, senseId, lemma)
    }

    private fun Env.seedAdditionalSense(
        existing: SenseFixture,
        includeExamples: Boolean = true,
    ): SenseFixture {
        val senseId = Uuid.random()
        val q = dictionary.dictionaryQueries
        q.insertSense(
            sense_id = senseId,
            lemma_pos_id = existing.lemmaPosId,
            sense_definition = "to learn something",
            learner_level = LearnerLevel.A1,
            frequency = SenseFrequency.HIGH,
            semantic_group_id = "sg",
            name_type = NameType.NO,
        )
        if (includeExamples) {
            q.insertSenseExample(senseId, 1, "I <w>${existing.lemma}</w> every day.")
            q.insertSenseExample(senseId, 2, "They <w>${existing.lemma}</w> quickly.")
        }
        return SenseFixture(existing.lemmaId, existing.lemmaPosId, senseId, existing.lemma)
    }

    private fun Env.seedTranslation(
        senseId: Uuid,
        lemmaPosId: Uuid,
        includeExampleTranslation: Boolean = true,
    ) {
        val q = translation!!.translationQueries
        q.insertSenseTargetDefinition(senseId, "en", "ru", "to gain knowledge")
        q.insertSenseTranslation(
            sense_id = senseId,
            from_lang_code = "en",
            target_lang_code = "ru",
            idx = 0,
            target_lang_word = "учиться",
            target_lang_word_normalized = "учиться",
            target_lang_sense_clarification = null,
            lemma_id = Uuid.random(),
            lemma_pos_id = lemmaPosId,
        )
        if (includeExampleTranslation) {
            q.insertExampleTranslation(senseId, "en", "ru", 1, "Я <w>учусь</w> каждый день.")
        }
    }

    private fun Env.insertTask(
        fixture: SenseFixture,
        family: CardFamily,
        lemmaId: Uuid = fixture.lemmaId,
        cardId: Uuid = Uuid.random(),
        state: CardState = CardState.LEARNING,
        stability: Double = 1.0,
        difficulty: Double = 1.0,
        due: Long = (start - 1.milliseconds).toEpochMilliseconds(),
        lastReview: Long? = null,
        reps: Long = 0,
    ): com.slovy.slovymovyapp.db.Card {
        app.favoritesQueries.insertCard(
            id = cardId,
            sense_id = fixture.senseId,
            lemma_id = lemmaId,
            lang_code = "en",
            family = family,
            state = state,
            stability = stability,
            difficulty = difficulty,
            due = due,
            last_review = lastReview,
            reps = reps,
            lapses = 0,
            created_at = start.toEpochMilliseconds(),
            available_after = null,
            answer_key = fixture.lemma.lowercase(),
            suspended = false,
        )
        return app.favoritesQueries.selectCardById(cardId).executeAsOne()
    }

    private fun Env.insertReviewLog(
        cardId: Uuid,
        variantKind: CardKind,
        variantTargetLang: String? = null,
    ) {
        app.favoritesQueries.insertReviewLog(
            id = Uuid.random(),
            card_id = cardId,
            reviewed_at = (start - 1.milliseconds).toEpochMilliseconds(),
            rating = Rating.GOOD,
            variant_kind = variantKind,
            variant_target_lang = variantTargetLang,
            example_id = null,
            state_before = CardState.REVIEW,
            stability_before = 1.0,
            difficulty_before = 1.0,
            elapsed_days = 0,
            scheduled_days = 0,
            stability_after = 1.0,
            difficulty_after = 1.0,
            duration_ms = 1.seconds.inWholeMilliseconds,
        )
    }

    private suspend fun Env.addFavorite(fixture: SenseFixture, createdAt: Long? = null) {
        if (createdAt == null) {
            favorites.add(fixture.senseId.toString(), Language.ENGLISH, fixture.lemma)
        } else {
            favorites.add(fixture.senseId.toString(), Language.ENGLISH, fixture.lemma, createdAt)
        }
    }

    private fun openApp(platform: PlatformDbSupport): DbHandle<AppDatabase> {
        val driver = platform.createAppDataDriver(platform.getDatabasePath("${Uuid.random()}-app.db"))
        return DbHandle(driver, DatabaseProvider.createAppDatabase(driver))
    }

    private suspend fun Env.nextLoadedCard(langCode: String) =
        session.nextCard(langCode, start)
            .first { it == null || it.loadState() != SessionCardLoadState.LOADING }
            .let { card ->
                assertNotNull(
                    card,
                    "Expected a session card; cards=${
                        app.favoritesQueries.countCardsByLang(langCode).executeAsOne()
                    } " +
                            "new=${
                                app.favoritesQueries.selectNewCards(langCode, clock.now().toEpochMilliseconds(), 10)
                                    .executeAsList().size
                            } " +
                            "due=${
                                app.favoritesQueries.selectDueCards(langCode, clock.now().toEpochMilliseconds(), 10)
                                    .executeAsList().size
                            }",
                )
            }
            .also {
                assertEquals(
                    SessionCardLoadState.READY,
                    it.loadState(),
                    "Expected ready card, got ${it.loadError()?.reason}",
                )
            }

    private fun SessionCard.loadedSense() =
        wordResult.card?.entries
            ?.flatMap { it.senses }
            ?.firstOrNull { it.senseId == senseId }

    private fun assertClose(expected: Double, actual: Double) {
        assertTrue(abs(expected - actual) < 0.0001, "expected=$expected actual=$actual")
    }

}

private data class SenseFixture(
    val lemmaId: Uuid,
    val lemmaPosId: Uuid,
    val senseId: Uuid,
    val lemma: String,
)

private data class Env(
    val app: AppDatabase,
    val dictionary: DictionaryDatabase,
    val translation: TranslationDatabase?,
    val intake: IntakeService,
    val session: SessionService,
    val stats: StatsService,
    val favorites: FavoritesRepository,
    val clock: MutableClock,
)

private class DbHandle<T>(
    private val driver: SqlDriver,
    val database: T,
) {
    fun close() {
        driver.close()
    }
}

@OptIn(ExperimentalTime::class)
private class MutableClock(private var current: Instant) : Clock {
    override fun now(): Instant = current

    fun advance(duration: Duration) {
        current += duration
    }
}
