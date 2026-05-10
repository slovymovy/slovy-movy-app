package com.slovy.slovymovyapp

import com.slovy.slovymovyapp.data.Language
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class FavoritesReviewCoordinatorTest {
    private val start = Instant.parse("2026-05-10T00:00:00Z")
    private val clock = MutableClock(start)
    private val coordinator = FavoritesReviewCoordinator(clock)

    @Test
    fun cachesIntakeForFiveMinutesPerLanguage() {
        assertTrue(coordinator.shouldRunIntake(Language.ENGLISH))
        assertTrue(coordinator.shouldRunIntake(Language.POLISH))

        coordinator.markIntakeRun(Language.ENGLISH)

        assertFalse(coordinator.shouldRunIntake(Language.ENGLISH))
        assertTrue(coordinator.shouldRunIntake(Language.POLISH))

        clock.advance(4.minutes + 59.seconds)
        assertFalse(coordinator.shouldRunIntake(Language.ENGLISH))

        clock.advance(1.seconds)
        assertTrue(coordinator.shouldRunIntake(Language.ENGLISH))
    }

    @Test
    fun invalidateIntakeCacheDropsCachedLanguages() {
        coordinator.markIntakeRun(Language.ENGLISH)
        coordinator.markIntakeRun(Language.POLISH)

        assertFalse(coordinator.shouldRunIntake(Language.ENGLISH))
        assertFalse(coordinator.shouldRunIntake(Language.POLISH))

        coordinator.invalidateIntakeCache()

        assertTrue(coordinator.shouldRunIntake(Language.ENGLISH))
        assertTrue(coordinator.shouldRunIntake(Language.POLISH))
    }
}

@OptIn(ExperimentalTime::class)
private class MutableClock(private var current: Instant) : Clock {
    override fun now(): Instant = current

    fun advance(duration: Duration) {
        current += duration
    }
}
