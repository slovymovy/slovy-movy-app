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
import kotlin.time.Duration.Companion.hours
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
    fun removing_favorite_suspends_learning_cards_and_readding_preserves_delay() = runBlocking {
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
            assertEquals(availableAfter, suspendedCards.first().available_after)
            assertNull(env.session.nextCard("en", start).first())

            env.favorites.add(fixture.senseId.toString(), Language.ENGLISH, fixture.lemma)

            val restoredFavorite = env.app.favoritesQueries
                .selectFavoriteWithActivation(fixture.senseId.toString(), "en")
                .executeAsOne()
            assertNotNull(restoredFavorite.activated_at)
            val restoredCards = env.app.favoritesQueries.selectCardsByFavorite(fixture.senseId, "en").executeAsList()
            assertEquals(1, restoredCards.size)
            assertTrue(restoredCards.none { it.suspended })
            assertEquals(availableAfter, restoredCards.first().available_after)
            assertNull(env.session.nextCard("en", start).first())
        }
    }

    @Test
    fun undoing_removed_favorite_restores_cards_immediately() = runBlocking {
        withEnv(includeTranslation = false) { env ->
            val fixture = env.seedSense(lemma = "undoremovedfavorite")
            env.addFavorite(fixture)
            env.intake.runIntake("en")
            val card = env.app.favoritesQueries.selectCardsByFavorite(fixture.senseId, "en")
                .executeAsOne()
            env.app.favoritesQueries.setCardAvailableAfter(
                availableAfter = (start + 10.minutes).toEpochMilliseconds(),
                id = card.id,
            )

            env.favorites.remove(fixture.senseId.toString(), Language.ENGLISH)
            env.favorites.restoreForUndo(
                senseId = fixture.senseId.toString(),
                language = Language.ENGLISH,
                lemma = fixture.lemma,
                createdAt = start.toEpochMilliseconds(),
            )

            val restoredCards = env.app.favoritesQueries.selectCardsByFavorite(fixture.senseId, "en").executeAsList()
            assertEquals(1, restoredCards.size)
            assertTrue(restoredCards.none { it.suspended })
            assertNull(restoredCards.first().available_after)
            env.nextLoadedCard("en")
        }
    }

    @Test
    fun suspending_word_applies_delay_to_removed_meanings() = runBlocking {
        withEnv(includeTranslation = false) { env ->
            val fixture = env.seedSense(lemma = "delayremovedmeaning")
            env.addFavorite(fixture)
            env.intake.runIntake("en")
            val card = env.app.favoritesQueries.selectCardsByFavorite(fixture.senseId, "en").executeAsOne()

            env.favorites.remove(fixture.senseId.toString(), Language.ENGLISH)
            val availableAfter = (start + 30.days).toEpochMilliseconds()
            env.app.favoritesQueries.setCardsAvailableAfterByLemma(
                availableAfter = availableAfter,
                lang_code = "en",
                lemma_id = card.lemma_id,
            )

            val suspendedCard = env.app.favoritesQueries.selectCardsByFavorite(fixture.senseId, "en").executeAsOne()
            assertTrue(suspendedCard.suspended)
            assertEquals(availableAfter, suspendedCard.available_after)

            env.favorites.add(fixture.senseId.toString(), Language.ENGLISH, fixture.lemma)

            val restoredCard = env.app.favoritesQueries.selectCardsByFavorite(fixture.senseId, "en").executeAsOne()
            assertFalse(restoredCard.suspended)
            assertEquals(availableAfter, restoredCard.available_after)
            assertNull(env.session.nextCard("en", start).first())
        }
    }

    @Test
    fun next_card_excluding_family_applies_filter_before_candidate_limit() = runBlocking {
        withEnv(includeTranslation = false) { env ->
            repeat(60) { index ->
                val fixture = env.seedSense(lemma = "voicecandidate$index")
                env.insertTask(
                    fixture = fixture,
                    family = CardFamily.RECOGNIZE_VOICE,
                    due = (start - 10.minutes).toEpochMilliseconds(),
                )
            }
            val recognition = env.seedSense(lemma = "recognitioncandidate")
            env.addFavorite(recognition)
            val recognitionCard = env.insertTask(
                fixture = recognition,
                family = CardFamily.RECOGNIZE_SENSE,
                due = (start - 1.minutes).toEpochMilliseconds(),
            )

            val next = env.session.nextCardExcludingFamily(
                langCode = "en",
                sessionStartedAt = start,
                excludedFamily = CardFamily.RECOGNIZE_VOICE,
            ).first { it == null || it.loadState() != SessionCardLoadState.LOADING }

            assertEquals(recognitionCard.id, assertNotNull(next).card.id)
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
    fun continue_now_pending_intake_activates_five_queued_lemmas() = runBlocking {
        withEnv(includeTranslation = false) { env ->
            val fixtures = (1..7).map { index ->
                env.seedSense(lemma = "continuepending$index").also { fixture ->
                    env.addFavorite(
                        fixture = fixture,
                        createdAt = (start + index.milliseconds).toEpochMilliseconds(),
                    )
                }
            }

            val result = env.intake.continueWithPendingFavoritesNow("en")

            assertEquals(5, result.cardsCreated)
            assertEquals(fixtures.take(5).map { it.senseId }, result.activated)
            fixtures.take(5).forEach { fixture ->
                assertEquals(
                    1,
                    env.app.favoritesQueries.selectCardsByFavorite(fixture.senseId, "en").executeAsList().size,
                )
                assertNotNull(
                    env.app.favoritesQueries.selectFavoriteWithActivation(fixture.senseId.toString(), "en")
                        .executeAsOne().activated_at,
                )
            }
            fixtures.drop(5).forEach { fixture ->
                assertEquals(
                    0,
                    env.app.favoritesQueries.selectCardsByFavorite(fixture.senseId, "en").executeAsList().size,
                )
                assertNull(
                    env.app.favoritesQueries.selectFavoriteWithActivation(fixture.senseId.toString(), "en")
                        .executeAsOne().activated_at,
                )
            }
        }
    }

    @Test
    fun continue_now_pending_intake_bypasses_daily_new_card_budget() = runBlocking {
        val config = FsrsDefaults.config().copy(dailyNewTaskFamilyBudget = 0)
        withEnv(includeTranslation = false, config = config) { env ->
            val fixture = env.seedSense(lemma = "continueignoresbudget")
            env.addFavorite(fixture)

            val result = env.intake.continueWithPendingFavoritesNow("en")

            assertEquals(1, result.cardsCreated)
            assertTrue(result.skipped.isEmpty(), "unexpected skips: ${result.skipped}")
            assertNotNull(
                env.app.favoritesQueries.selectFavoriteWithActivation(fixture.senseId.toString(), "en")
                    .executeAsOne().activated_at,
            )
        }
    }

    @Test
    fun continue_now_pending_intake_respects_full_queue_pause() = runBlocking {
        val config = FsrsDefaults.config().copy(pauseIntakeIfQueueAbove = 0)
        withEnv(includeTranslation = false, config = config) { env ->
            val queued = env.seedSense(lemma = "continuequeuequeued")
            val due = env.seedSense(lemma = "continuequeuedue")
            env.addFavorite(queued)
            env.addFavorite(due)
            env.insertTask(
                fixture = due,
                family = CardFamily.RECOGNIZE_SENSE,
            )

            assertFalse(env.intake.canContinueWithPendingFavoritesNow("en"))

            val result = env.intake.continueWithPendingFavoritesNow("en")

            assertEquals(0, result.cardsCreated)
            assertEquals(listOf(SkipReason.QUEUE_TOO_FULL), result.skipped)
            assertNull(
                env.app.favoritesQueries.selectFavoriteWithActivation(queued.senseId.toString(), "en")
                    .executeAsOne().activated_at,
            )
        }
    }

    @Test
    fun continue_now_pending_intake_respects_retention_pause() = runBlocking {
        val config = FsrsDefaults.config().copy(pauseIntakeRetentionMinReviews = 1L)
        withEnv(includeTranslation = false, config = config) { env ->
            val reviewed = env.seedSense(lemma = "continueretentionreviewed")
            val queued = env.seedSense(lemma = "continueretentionqueued")
            env.addFavorite(reviewed)
            env.addFavorite(queued, createdAt = (start + 1.milliseconds).toEpochMilliseconds())
            val card = env.insertTask(
                fixture = reviewed,
                family = CardFamily.RECOGNIZE_SENSE,
            )
            env.insertReviewLog(
                cardId = card.id,
                variantKind = CardKind.WORD_TO_SOURCE_DEFINITION,
                rating = Rating.AGAIN,
            )

            assertFalse(env.intake.canContinueWithPendingFavoritesNow("en"))

            val result = env.intake.continueWithPendingFavoritesNow("en")

            assertEquals(0, result.cardsCreated)
            assertEquals(listOf(SkipReason.RETENTION_TOO_LOW), result.skipped)
            assertNull(
                env.app.favoritesQueries.selectFavoriteWithActivation(queued.senseId.toString(), "en")
                    .executeAsOne().activated_at,
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
    fun intake_schedules_new_sense_of_active_lemma_when_daily_budget_exhausted() = runBlocking {
        val config = FsrsDefaults.config().copy(dailyNewTaskFamilyBudget = 0)
        withEnv(includeTranslation = false, config = config) { env ->
            val first = env.seedSense(lemma = "budgetextension")
            val second = env.seedAdditionalSense(first)
            env.addFavorite(first, createdAt = start.toEpochMilliseconds())
            env.insertTask(fixture = first, family = CardFamily.RECOGNIZE_SENSE)
            env.app.favoritesQueries.markFavoriteActivated(
                activated_at = start.toEpochMilliseconds(),
                sense_id = first.senseId.toString(),
                lang_code = "en",
            )
            env.addFavorite(second, createdAt = (start + 1.milliseconds).toEpochMilliseconds())

            val result = env.intake.runIntake("en")

            assertEquals(1, result.cardsCreated)
            assertEquals(listOf(second.senseId), result.activated)
            assertTrue(result.skipped.isEmpty(), "unexpected skips: ${result.skipped}")
            assertEquals(
                1,
                env.app.favoritesQueries.selectCardsByFavorite(second.senseId, "en").executeAsList().size,
            )
            assertNotNull(
                env.app.favoritesQueries.selectFavoriteWithActivation(second.senseId.toString(), "en")
                    .executeAsOne().activated_at,
            )
        }
    }

    @Test
    fun intake_schedules_new_sense_of_active_lemma_when_queue_paused() = runBlocking {
        val config = FsrsDefaults.config().copy(pauseIntakeIfQueueAbove = 0)
        withEnv(includeTranslation = false, config = config) { env ->
            val first = env.seedSense(lemma = "queueextension")
            val second = env.seedAdditionalSense(first)
            val newcomer = env.seedSense(lemma = "queuenewcomer")
            env.addFavorite(first, createdAt = start.toEpochMilliseconds())
            env.insertTask(fixture = first, family = CardFamily.RECOGNIZE_SENSE)
            env.app.favoritesQueries.markFavoriteActivated(
                activated_at = start.toEpochMilliseconds(),
                sense_id = first.senseId.toString(),
                lang_code = "en",
            )
            env.addFavorite(second, createdAt = (start + 1.milliseconds).toEpochMilliseconds())
            env.addFavorite(newcomer, createdAt = (start + 2.milliseconds).toEpochMilliseconds())

            val result = env.intake.runIntake("en")

            assertEquals(1, result.cardsCreated)
            assertEquals(listOf(second.senseId), result.activated)
            assertEquals(listOf(SkipReason.QUEUE_TOO_FULL), result.skipped)
            assertNotNull(
                env.app.favoritesQueries.selectFavoriteWithActivation(second.senseId.toString(), "en")
                    .executeAsOne().activated_at,
            )
            assertNull(
                env.app.favoritesQueries.selectFavoriteWithActivation(newcomer.senseId.toString(), "en")
                    .executeAsOne().activated_at,
            )
        }
    }

    @Test
    fun intake_schedules_new_sense_of_active_lemma_when_retention_paused() = runBlocking {
        val config = FsrsDefaults.config().copy(pauseIntakeRetentionMinReviews = 1L)
        withEnv(includeTranslation = false, config = config) { env ->
            val first = env.seedSense(lemma = "retentionextension")
            val second = env.seedAdditionalSense(first)
            env.addFavorite(first, createdAt = start.toEpochMilliseconds())
            val card = env.insertTask(fixture = first, family = CardFamily.RECOGNIZE_SENSE)
            env.app.favoritesQueries.markFavoriteActivated(
                activated_at = start.toEpochMilliseconds(),
                sense_id = first.senseId.toString(),
                lang_code = "en",
            )
            env.insertReviewLog(
                cardId = card.id,
                variantKind = CardKind.WORD_TO_SOURCE_DEFINITION,
                rating = Rating.AGAIN,
            )
            env.addFavorite(second, createdAt = (start + 1.milliseconds).toEpochMilliseconds())

            val result = env.intake.runIntake("en")

            assertEquals(1, result.cardsCreated)
            assertEquals(listOf(second.senseId), result.activated)
            assertTrue(result.skipped.isEmpty(), "unexpected skips: ${result.skipped}")
            assertNotNull(
                env.app.favoritesQueries.selectFavoriteWithActivation(second.senseId.toString(), "en")
                    .executeAsOne().activated_at,
            )
        }
    }

    @Test
    fun intake_treats_lemma_as_newcomer_after_all_active_favorites_removed() = runBlocking {
        val config = FsrsDefaults.config().copy(pauseIntakeIfQueueAbove = 0)
        withEnv(includeTranslation = false, config = config) { env ->
            val first = env.seedSense(lemma = "removedextension")
            val second = env.seedAdditionalSense(first)
            val pressure = env.seedSense(lemma = "removedpressure")
            env.addFavorite(first, createdAt = start.toEpochMilliseconds())
            env.insertTask(fixture = first, family = CardFamily.RECOGNIZE_SENSE)
            env.app.favoritesQueries.markFavoriteActivated(
                activated_at = start.toEpochMilliseconds(),
                sense_id = first.senseId.toString(),
                lang_code = "en",
            )
            env.addFavorite(pressure, createdAt = (start + 1.milliseconds).toEpochMilliseconds())
            env.insertTask(fixture = pressure, family = CardFamily.RECOGNIZE_SENSE)
            env.app.favoritesQueries.markFavoriteActivated(
                activated_at = start.toEpochMilliseconds(),
                sense_id = pressure.senseId.toString(),
                lang_code = "en",
            )

            env.favorites.remove(first.senseId.toString(), Language.ENGLISH)
            env.addFavorite(second, createdAt = (start + 2.milliseconds).toEpochMilliseconds())

            val result = env.intake.runIntake("en")

            assertEquals(0, result.cardsCreated)
            assertEquals(listOf(SkipReason.QUEUE_TOO_FULL), result.skipped)
            assertNull(
                env.app.favoritesQueries.selectFavoriteWithActivation(second.senseId.toString(), "en")
                    .executeAsOne().activated_at,
            )
        }
    }

    @Test
    fun continue_now_extension_bypasses_pending_lemma_limit() = runBlocking {
        withEnv(includeTranslation = false) { env ->
            val activeFirst = env.seedSense(lemma = "continueextensionactive")
            val activeSecond = env.seedAdditionalSense(activeFirst)
            env.addFavorite(activeFirst, createdAt = start.toEpochMilliseconds())
            env.insertTask(fixture = activeFirst, family = CardFamily.RECOGNIZE_SENSE)
            env.app.favoritesQueries.markFavoriteActivated(
                activated_at = start.toEpochMilliseconds(),
                sense_id = activeFirst.senseId.toString(),
                lang_code = "en",
            )
            env.addFavorite(activeSecond, createdAt = (start + 1.milliseconds).toEpochMilliseconds())
            val newcomers = (1..6).map { index ->
                env.seedSense(lemma = "continueextensionnew$index").also { fixture ->
                    env.addFavorite(
                        fixture = fixture,
                        createdAt = (start + (10 + index).milliseconds).toEpochMilliseconds(),
                    )
                }
            }

            val result = env.intake.continueWithPendingFavoritesNow("en")

            assertEquals(6, result.cardsCreated)
            assertTrue(result.activated.contains(activeSecond.senseId))
            assertEquals(5, result.activated.count { it != activeSecond.senseId })
            assertEquals(
                5,
                newcomers.count {
                    env.app.favoritesQueries.selectFavoriteWithActivation(it.senseId.toString(), "en")
                        .executeAsOne().activated_at != null
                },
            )
            assertNotNull(
                env.app.favoritesQueries.selectFavoriteWithActivation(activeSecond.senseId.toString(), "en")
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
    fun recognition_good_unlocks_production_with_delayed_first_presentation() = runBlocking {
        val config = FsrsDefaults.config().copy(cooldownJitterRatio = 0.0)
        withEnv(includeTranslation = false, config = config) { env ->
            val fixture = env.seedSense(lemma = "forwarddelay")
            env.addFavorite(fixture)
            env.insertTask(
                fixture = fixture,
                family = CardFamily.RECOGNIZE_SENSE,
                state = CardState.NEW,
                stability = 0.0,
                difficulty = 0.0,
                due = start.toEpochMilliseconds(),
            )

            val recognition = env.nextLoadedCard("en")
            val reviewed = env.session.submitReview(
                recognition,
                env.session.previewRatings(recognition).first { it.rating == Rating.GOOD },
                durationMs = 1.seconds.inWholeMilliseconds,
            )

            val production = env.app.favoritesQueries.selectCardsByFavorite(fixture.senseId, "en")
                .executeAsList()
                .single { it.family == CardFamily.PRODUCE_WORD }
            val expectedDue = start + stabilityDelay(
                stability = production.stability,
                ratio = config.forwardCreditDelayRatio,
                floor = config.forwardCreditDelayFloor,
                cap = config.forwardCreditDelayCap,
            )
            val expectedAvailableAfter = start + stabilityDelay(
                stability = reviewed.card.scheduling.stability,
                ratio = config.sameSenseExposureRatio,
                floor = config.sameSenseExposureFloor,
                cap = config.sameSenseExposureCap,
            )
            assertEquals(expectedDue.toEpochMilliseconds(), production.due)
            assertEquals(expectedAvailableAfter.toEpochMilliseconds(), production.available_after)
            assertTrue(production.due > (start + 10.minutes).toEpochMilliseconds())
        }
    }

    @Test
    fun recognition_easy_gives_production_more_credit_and_later_presentation_than_good() = runBlocking {
        val config = FsrsDefaults.config().copy(cooldownJitterRatio = 0.0)
        withEnv(includeTranslation = false, config = config) { env ->
            val goodFixture = env.seedSense(lemma = "forwardgood")
            val easyFixture = env.seedSense(lemma = "forwardeasy")
            env.addFavorite(goodFixture, createdAt = start.toEpochMilliseconds())
            env.addFavorite(easyFixture, createdAt = (start + 1.milliseconds).toEpochMilliseconds())
            env.insertTask(
                fixture = goodFixture,
                family = CardFamily.RECOGNIZE_SENSE,
                state = CardState.NEW,
                stability = 0.0,
                difficulty = 0.0,
                due = start.toEpochMilliseconds(),
            )
            env.insertTask(
                fixture = easyFixture,
                family = CardFamily.RECOGNIZE_SENSE,
                state = CardState.NEW,
                stability = 0.0,
                difficulty = 0.0,
                due = start.toEpochMilliseconds(),
            )

            val first = env.nextLoadedCard("en")
            env.session.submitReview(
                first,
                env.session.previewRatings(first).first { it.rating == Rating.GOOD },
                durationMs = 1.seconds.inWholeMilliseconds,
            )
            val second = env.nextLoadedCard("en")
            env.session.submitReview(
                second,
                env.session.previewRatings(second).first { it.rating == Rating.EASY },
                durationMs = 1.seconds.inWholeMilliseconds,
            )

            val goodProduction = env.app.favoritesQueries
                .selectCardsByFavorite(Uuid.parse(first.senseId), "en")
                .executeAsList()
                .single { it.family == CardFamily.PRODUCE_WORD }
            val easyProduction = env.app.favoritesQueries
                .selectCardsByFavorite(Uuid.parse(second.senseId), "en")
                .executeAsList()
                .single { it.family == CardFamily.PRODUCE_WORD }
            assertTrue(easyProduction.stability > goodProduction.stability)
            assertTrue(easyProduction.due > goodProduction.due)
        }
    }

    @Test
    fun forward_credit_bumps_existing_sibling_and_preserves_later_due() = runBlocking {
        val config = FsrsDefaults.config().copy(cooldownJitterRatio = 0.0)
        withEnv(includeTranslation = false, config = config) { env ->
            val fixture = env.seedSense(lemma = "existingforward")
            val futureDue = (start + 2.days).toEpochMilliseconds()
            env.addFavorite(fixture)
            env.insertTask(
                fixture = fixture,
                family = CardFamily.RECOGNIZE_SENSE,
                state = CardState.NEW,
                stability = 0.0,
                difficulty = 0.0,
                due = start.toEpochMilliseconds(),
            )
            val production = env.insertTask(
                fixture = fixture,
                family = CardFamily.PRODUCE_WORD,
                state = CardState.LEARNING,
                stability = 0.2,
                difficulty = 5.0,
                due = futureDue,
            )

            val recognition = env.nextLoadedCard("en")
            val reviewed = env.session.submitReview(
                recognition,
                env.session.previewRatings(recognition).first { it.rating == Rating.GOOD },
                durationMs = 1.seconds.inWholeMilliseconds,
            )

            val credit = config.crossFamilyCredits.single {
                it.sourceFamily == CardFamily.RECOGNIZE_SENSE && it.targetFamily == CardFamily.PRODUCE_WORD
            }
            val updatedProduction = env.app.favoritesQueries.selectCardById(production.id).executeAsOne()
            assertEquals(CardState.LEARNING, updatedProduction.state)
            assertClose(reviewed.card.scheduling.stability * credit.goodFactor, updatedProduction.stability)
            assertEquals(futureDue, updatedProduction.due)
            assertEquals(0L, updatedProduction.reps)
            assertNull(updatedProduction.last_review)
        }
    }

    @Test
    fun forward_credit_can_graduate_existing_practiced_sibling() = runBlocking {
        val config = FsrsDefaults.config().copy(cooldownJitterRatio = 0.0)
        withEnv(includeTranslation = false, config = config) { env ->
            val fixture = env.seedSense(lemma = "existingpracticed")
            env.addFavorite(fixture)
            env.insertTask(
                fixture = fixture,
                family = CardFamily.RECOGNIZE_SENSE,
                state = CardState.NEW,
                stability = 0.0,
                difficulty = 0.0,
                due = start.toEpochMilliseconds(),
            )
            val production = env.insertTask(
                fixture = fixture,
                family = CardFamily.PRODUCE_WORD,
                state = CardState.LEARNING,
                stability = 0.2,
                difficulty = 5.0,
                due = (start + 2.days).toEpochMilliseconds(),
                lastReview = (start - 1.days).toEpochMilliseconds(),
                reps = 1,
            )

            val recognition = env.nextLoadedCard("en")
            env.session.submitReview(
                recognition,
                env.session.previewRatings(recognition).first { it.rating == Rating.GOOD },
                durationMs = 1.seconds.inWholeMilliseconds,
            )

            val updatedProduction = env.app.favoritesQueries.selectCardById(production.id).executeAsOne()
            assertEquals(CardState.REVIEW, updatedProduction.state)
            assertEquals(1L, updatedProduction.reps)
            assertEquals((start - 1.days).toEpochMilliseconds(), updatedProduction.last_review)
        }
    }

    @Test
    fun forward_credit_keeps_sibling_without_direct_review_timestamp_in_learning() = runBlocking {
        val config = FsrsDefaults.config().copy(cooldownJitterRatio = 0.0)
        withEnv(includeTranslation = false, config = config) { env ->
            val fixture = env.seedSense(lemma = "existinguntimestamped")
            env.addFavorite(fixture)
            env.insertTask(
                fixture = fixture,
                family = CardFamily.RECOGNIZE_SENSE,
                state = CardState.NEW,
                stability = 0.0,
                difficulty = 0.0,
                due = start.toEpochMilliseconds(),
            )
            val production = env.insertTask(
                fixture = fixture,
                family = CardFamily.PRODUCE_WORD,
                state = CardState.LEARNING,
                stability = 0.2,
                difficulty = 5.0,
                due = (start + 2.days).toEpochMilliseconds(),
                reps = 1,
            )

            val recognition = env.nextLoadedCard("en")
            env.session.submitReview(
                recognition,
                env.session.previewRatings(recognition).first { it.rating == Rating.GOOD },
                durationMs = 1.seconds.inWholeMilliseconds,
            )

            val updatedProduction = env.app.favoritesQueries.selectCardById(production.id).executeAsOne()
            assertEquals(CardState.LEARNING, updatedProduction.state)
            assertEquals(1L, updatedProduction.reps)
            assertNull(updatedProduction.last_review)
        }
    }

    @Test
    fun forward_credit_does_not_lower_stronger_existing_sibling() = runBlocking {
        val config = FsrsDefaults.config().copy(cooldownJitterRatio = 0.0)
        withEnv(includeTranslation = false, config = config) { env ->
            val fixture = env.seedSense(lemma = "existingstrong")
            val futureDue = (start + 2.days).toEpochMilliseconds()
            env.addFavorite(fixture)
            env.insertTask(
                fixture = fixture,
                family = CardFamily.RECOGNIZE_SENSE,
                state = CardState.NEW,
                stability = 0.0,
                difficulty = 0.0,
                due = start.toEpochMilliseconds(),
            )
            val production = env.insertTask(
                fixture = fixture,
                family = CardFamily.PRODUCE_WORD,
                state = CardState.REVIEW,
                stability = 5.0,
                difficulty = 4.0,
                due = futureDue,
            )

            val recognition = env.nextLoadedCard("en")
            env.session.submitReview(
                recognition,
                env.session.previewRatings(recognition).first { it.rating == Rating.GOOD },
                durationMs = 1.seconds.inWholeMilliseconds,
            )

            val updatedProduction = env.app.favoritesQueries.selectCardById(production.id).executeAsOne()
            assertEquals(CardState.REVIEW, updatedProduction.state)
            assertEquals(5.0, updatedProduction.stability)
            assertEquals(4.0, updatedProduction.difficulty)
            assertEquals(futureDue, updatedProduction.due)
        }
    }

    @Test
    fun production_easy_advances_recognition_without_reviewing_it() = runBlocking {
        val config = FsrsDefaults.config().copy(cooldownJitterRatio = 0.0)
        withEnv(includeTranslation = false, config = config) { env ->
            val fixture = env.seedSense(lemma = "backwardcredit")
            env.addFavorite(fixture)
            val recognition = env.insertTask(
                fixture = fixture,
                family = CardFamily.RECOGNIZE_SENSE,
                state = CardState.LEARNING,
                stability = 0.5,
                difficulty = 0.0,
                due = (start + 2.days).toEpochMilliseconds(),
            )
            env.insertTask(
                fixture = fixture,
                family = CardFamily.PRODUCE_WORD,
                state = CardState.LEARNING,
                stability = 4.0,
                difficulty = 5.0,
                due = (start - 2.milliseconds).toEpochMilliseconds(),
            )

            val production = env.nextLoadedCard("en")
            assertEquals(CardFamily.PRODUCE_WORD, production.card.family)
            val reviewed = env.session.submitReview(
                production,
                env.session.previewRatings(production).first { it.rating == Rating.EASY },
                durationMs = 1.seconds.inWholeMilliseconds,
            )

            val updatedRecognition = env.app.favoritesQueries.selectCardById(recognition.id).executeAsOne()
            assertEquals(CardState.LEARNING, updatedRecognition.state)
            assertClose(reviewed.card.scheduling.stability, updatedRecognition.stability)
            assertEquals(0L, updatedRecognition.reps)
            assertNull(updatedRecognition.last_review)
            assertEquals(
                0,
                env.app.favoritesQueries.selectReviewLogsByCard(recognition.id, 10).executeAsList().size,
            )
        }
    }

    @Test
    fun hard_review_does_not_propagate_cross_family_credit() = runBlocking {
        val config = FsrsDefaults.config().copy(cooldownJitterRatio = 0.0)
        withEnv(includeTranslation = false, config = config) { env ->
            val fixture = env.seedSense(lemma = "hardnocredit")
            env.addFavorite(fixture)
            val recognition = env.insertTask(
                fixture = fixture,
                family = CardFamily.RECOGNIZE_SENSE,
                state = CardState.NEW,
                stability = 0.0,
                difficulty = 0.0,
                due = (start - 1.milliseconds).toEpochMilliseconds(),
            )
            env.insertTask(
                fixture = fixture,
                family = CardFamily.PRODUCE_WORD,
                state = CardState.LEARNING,
                stability = 4.0,
                difficulty = 5.0,
                due = (start - 2.milliseconds).toEpochMilliseconds(),
            )

            val production = env.nextLoadedCard("en")
            env.session.submitReview(
                production,
                env.session.previewRatings(production).first { it.rating == Rating.HARD },
                durationMs = 1.seconds.inWholeMilliseconds,
            )

            val updatedRecognition = env.app.favoritesQueries.selectCardById(recognition.id).executeAsOne()
            assertEquals(CardState.NEW, updatedRecognition.state)
            assertEquals(0.0, updatedRecognition.stability)
            assertEquals(0L, updatedRecognition.reps)
        }
    }

    @Test
    fun same_lemma_new_senses_wait_for_lightweight_cooldown_after_successful_review() = runBlocking {
        val config = FsrsDefaults.config().copy(
            lemmaCooldownFloor = 2.minutes,
            lemmaCooldownCap = 2.minutes,
            cooldownJitterRatio = 0.0,
        )
        withEnv(includeTranslation = false, config = config) { env ->
            val firstSense = env.seedSense(lemma = "polysemenew")
            val secondSense = env.seedAdditionalSense(firstSense)
            val thirdSense = env.seedAdditionalSense(firstSense)
            val senses = listOf(firstSense, secondSense, thirdSense)
            env.addFavorite(firstSense, createdAt = start.toEpochMilliseconds())
            env.addFavorite(secondSense, createdAt = (start + 1.milliseconds).toEpochMilliseconds())
            env.addFavorite(thirdSense, createdAt = (start + 2.milliseconds).toEpochMilliseconds())
            val result = env.intake.runIntake("en")
            assertEquals(3, result.cardsCreated)

            val firstCard = env.nextLoadedCard("en")
            val expectedSenseIds = setOf(
                firstSense.senseId.toString(),
                secondSense.senseId.toString(),
                thirdSense.senseId.toString(),
            )
            assertTrue(firstCard.senseId in expectedSenseIds)
            env.session.submitReview(
                firstCard,
                env.session.previewRatings(firstCard).first { it.rating == Rating.GOOD },
                durationMs = 1.seconds.inWholeMilliseconds,
            )

            val delayedCards = senses
                .filter { it.senseId.toString() != firstCard.senseId }
                .map { env.app.favoritesQueries.selectCardsByFavorite(it.senseId, "en").executeAsOne() }
            assertEquals(2, delayedCards.size)
            delayedCards.forEach { delayedCard ->
                assertEquals(CardState.NEW, delayedCard.state)
                assertEquals((start + 2.minutes).toEpochMilliseconds(), delayedCard.available_after)
            }
            assertNull(env.session.nextCard("en", start).first())

            env.clock.advance(2.minutes)
            val nextCard = assertNotNull(
                env.session.nextCard("en", start)
                    .first { it == null || it.loadState() != SessionCardLoadState.LOADING }
            )
            assertTrue(nextCard.senseId in expectedSenseIds - firstCard.senseId)
        }
    }

    @Test
    fun same_lemma_started_sense_waits_for_lightweight_cooldown() = runBlocking {
        val config = FsrsDefaults.config().copy(
            sameSenseCooldownRatio = 0.0,
            sameLemmaCooldownRatio = 0.02,
            sameAnswerCooldownRatio = 0.0,
            lemmaCooldownFloor = 2.minutes,
            lemmaCooldownCap = 2.minutes,
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

            val delayedSense = if (firstCard.senseId == firstSense.senseId.toString()) secondSense else firstSense
            val delayedSenseId = delayedSense.senseId.toString()
            val delayedCard = env.app.favoritesQueries.selectCardsByFavorite(delayedSense.senseId, "en")
                .executeAsOne()
            assertEquals((start + 3.minutes).toEpochMilliseconds(), delayedCard.available_after)
            assertNull(env.session.nextCard("en", start).first())

            env.clock.advance(3.minutes)
            val secondCard = assertNotNull(
                env.session.nextCard("en", start)
                    .first { it == null || it.loadState() != SessionCardLoadState.LOADING }
            )
            assertEquals(setOf(delayedSenseId), setOf(secondCard.senseId))
        }
    }

    @Test
    fun continue_delayed_cards_now_clears_available_after_for_limited_lemmas() = runBlocking {
        val config = FsrsDefaults.config().copy(continueNowLemmaLimit = 2)
        withEnv(includeTranslation = false, config = config) { env ->
            val first = env.seedSense(lemma = "continueone")
            val second = env.seedSense(lemma = "continuetwo")
            val third = env.seedSense(lemma = "continuethree")
            val futureDue = env.seedSense(lemma = "continuefuture")
            env.addFavorite(first)
            env.addFavorite(second)
            env.addFavorite(third)
            env.addFavorite(futureDue)

            env.insertTask(
                fixture = first,
                family = CardFamily.RECOGNIZE_SENSE,
                availableAfter = (start + 10.minutes).toEpochMilliseconds(),
            )
            env.insertTask(
                fixture = second,
                family = CardFamily.RECOGNIZE_SENSE,
                availableAfter = (start + 11.minutes).toEpochMilliseconds(),
            )
            env.insertTask(
                fixture = third,
                family = CardFamily.RECOGNIZE_SENSE,
                availableAfter = (start + 12.minutes).toEpochMilliseconds(),
            )
            env.insertTask(
                fixture = futureDue,
                family = CardFamily.RECOGNIZE_SENSE,
                due = (start + 1.hours).toEpochMilliseconds(),
                availableAfter = (start + 10.minutes).toEpochMilliseconds(),
            )

            env.session.continueDelayedCardsNow("en")

            val cards = env.app.favoritesQueries.selectDueCards(
                lang_code = "en",
                now = start.toEpochMilliseconds(),
                new_state = CardState.NEW,
                limit = 10,
            ).executeAsList()
            assertEquals(setOf(first.senseId, second.senseId), cards.mapTo(HashSet()) { it.sense_id })
            assertNotNull(
                env.app.favoritesQueries.selectCardsByFavorite(third.senseId, "en")
                    .executeAsOne().available_after
            )
            assertNotNull(
                env.app.favoritesQueries.selectCardsByFavorite(futureDue.senseId, "en")
                    .executeAsOne().available_after
            )
        }
    }

    @Test
    fun continue_delayed_cards_now_unlocks_next_session_card() = runBlocking {
        withEnv(includeTranslation = false) { env ->
            val fixture = env.seedSense(lemma = "continuesession")
            env.addFavorite(fixture)
            env.insertTask(
                fixture = fixture,
                family = CardFamily.RECOGNIZE_SENSE,
                availableAfter = (start + 10.minutes).toEpochMilliseconds(),
            )

            assertNull(env.session.nextCard("en", start).first())

            env.session.continueDelayedCardsNow("en")

            val card = env.nextLoadedCard("en")
            assertEquals(fixture.senseId.toString(), card.senseId)
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
            sameSenseExposureRatio = 0.0,
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
            val fixture = env.seedSense(lemma = "clozetaggedfixture")
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
            assertEquals("I ${fixture.lemma} every day.", example.text)
            assertEquals(listOf(2..(fixture.lemma.length + 1)), example.clozeRanges)
        }
    }

    @Test
    fun cloze_card_keeps_all_tagged_example_occurrences() = runBlocking {
        withEnv(includeTranslation = true) { env ->
            val fixture = env.seedSense(lemma = "multiclozetaggedfixture", includeExamples = false)
            env.dictionary.dictionaryQueries.insertSenseExample(
                fixture.senseId,
                1,
                "I <w>take</w> it <w>away</w>.",
            )
            env.seedTranslation(fixture.senseId, fixture.lemmaPosId)
            env.addFavorite(fixture)
            env.intake.runIntake("en")
            val clozeCard = env.insertTask(
                fixture = fixture,
                family = CardFamily.PRODUCE_WORD_IN_CONTEXT,
                stability = 8.0,
            )
            env.insertReviewLog(clozeCard.id, CardKind.CLOZE_TRANSLATION, Language.RUSSIAN.code)

            val card = env.nextLoadedCard("en")

            assertEquals(CardKind.CLOZE_SOURCE, card.variant.kind)
            val example = assertNotNull(card.example)
            assertEquals("I take it away.", example.text)
            assertEquals(listOf(2..5, 10..13), example.clozeRanges)
        }
    }

    @Test
    fun cloze_card_strips_nested_tag_markup_from_example_text() = runBlocking {
        withEnv(includeTranslation = true) { env ->
            val fixture = env.seedSense(lemma = "nestedclozetaggedfixture", includeExamples = false)
            env.dictionary.dictionaryQueries.insertSenseExample(
                fixture.senseId,
                1,
                "I <w>take <w>away</w></w> luggage.",
            )
            env.seedTranslation(fixture.senseId, fixture.lemmaPosId)
            env.addFavorite(fixture)
            env.intake.runIntake("en")
            val clozeCard = env.insertTask(
                fixture = fixture,
                family = CardFamily.PRODUCE_WORD_IN_CONTEXT,
                stability = 8.0,
            )
            env.insertReviewLog(clozeCard.id, CardKind.CLOZE_TRANSLATION, Language.RUSSIAN.code)

            val card = env.nextLoadedCard("en")

            assertEquals(CardKind.CLOZE_SOURCE, card.variant.kind)
            val example = assertNotNull(card.example)
            assertEquals("I take away luggage.", example.text)
            assertEquals(listOf(2..10), example.clozeRanges)
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
            assertEquals(listOf(2..6), example.clozeRanges)
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
            val scheduler = FsrsScheduler(
                config.requestRetention,
                config.weights,
                config.maximumInterval,
                enableFuzz = config.enableFuzz,
            )
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
        availableAfter: Long? = null,
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
            available_after = availableAfter,
            answer_key = fixture.lemma.lowercase(),
            suspended = false,
        )
        return app.favoritesQueries.selectCardById(cardId).executeAsOne()
    }

    private fun Env.insertReviewLog(
        cardId: Uuid,
        variantKind: CardKind,
        variantTargetLang: String? = null,
        rating: Rating = Rating.GOOD,
    ) {
        app.favoritesQueries.insertReviewLog(
            id = Uuid.random(),
            card_id = cardId,
            reviewed_at = (start - 1.milliseconds).toEpochMilliseconds(),
            rating = rating,
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
                                app.favoritesQueries.selectNewCards(
                                    lang_code = langCode,
                                    new_state = CardState.NEW,
                                    now = clock.now().toEpochMilliseconds(),
                                    limit = 10,
                                )
                                    .executeAsList().size
                            } " +
                            "due=${
                                app.favoritesQueries.selectDueCards(
                                    lang_code = langCode,
                                    now = clock.now().toEpochMilliseconds(),
                                    new_state = CardState.NEW,
                                    limit = 10,
                                )
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

    private fun stabilityDelay(
        stability: Double,
        ratio: Double,
        floor: Duration,
        cap: Duration,
    ): Duration =
        (stability.toDuration(DurationUnit.DAYS) * ratio).coerceIn(floor, cap)

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
