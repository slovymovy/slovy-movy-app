package com.slovy.slovymovyapp.data.learning.review

import com.slovy.slovymovyapp.analytics.PerformanceMonitoring
import com.slovy.slovymovyapp.analytics.PerformanceTrace
import com.slovy.slovymovyapp.analytics.useWithResult
import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.favorites.FavoritesRepository
import com.slovy.slovymovyapp.data.learning.intake.LearningIntake
import com.slovy.slovymovyapp.data.learning.stats.ReviewQueueStats
import com.slovy.slovymovyapp.data.learning.stats.StatsService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.concurrent.Volatile
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

internal data class FavoritesReviewState(
    val reviewByLanguage: Map<Language, FavoriteLanguageReviewState>,
) {
    val hasDueCards: Boolean get() = reviewByLanguage.values.any { it.dueCount > 0 }
}

internal data class FavoriteLanguageReviewState(
    val dueCount: Int,
    val activeCardCount: Int,
    val delayedDueLemmaCount: Int,
    val delayedDueCardCount: Int,
    val pendingFavoriteLemmaCount: Int,
    val canStudyPendingFavoritesNow: Boolean,
    val nextReviewAtEpochMs: Long?,
)

@OptIn(ExperimentalTime::class)
internal class FavoritesReviewCoordinator(
    private val clock: Clock = Clock.System,
) {
    private val refreshMutex = Mutex()
    private var lastIntakeAtByLanguage: Map<Language, Instant> = emptyMap()

    // Intake reads the local dictionary DB. The data-version-mismatch flow wipes that DB
    // (LocalDbManager.deleteAll closes the driver), so we must keep the coordinator disabled
    // until the routing layer has confirmed we're past that check.
    @Volatile
    var enabled: Boolean = false

    suspend fun refresh(
        favoritesRepository: FavoritesRepository,
        intakeService: LearningIntake,
        statsService: StatsService,
    ): FavoritesReviewState = refreshMutex.withLock {
        if (!enabled) return@withLock FavoritesReviewState(emptyMap())
        computeFavoritesReviewState(favoritesRepository, intakeService, statsService)
    }

    /**
     * Re-reads review queue state without running intake. Cheap enough to call right after a
     * favorite toggle: remove/add already mutate `card.suspended` synchronously, so
     * due and delayed-card metadata are accurate. Skipping intake here avoids serializing the dictionary-DB
     * driver against the screen's own queries on iOS.
     */
    suspend fun refreshDueCountsOnly(
        favoritesRepository: FavoritesRepository,
        intakeService: LearningIntake,
        statsService: StatsService,
    ): FavoritesReviewState = refreshMutex.withLock {
        if (!enabled) return@withLock FavoritesReviewState(emptyMap())
        PerformanceMonitoring.startTrace("favorites_review_due_counts").useWithResult {
            withContext(Dispatchers.Default) {
                val languages = favoritesRepository.getAllGroupedByLangAndLemma()
                    .map { it.language }
                    .distinct()
                putMetric("languages", languages.size.toLong())
                val reviewByLanguage = languages.associateWith { language ->
                    statsService.reviewQueueStats(language.code)
                        .toFavoriteLanguageReviewState(language, intakeService)
                }
                putReviewStateMetrics(reviewByLanguage)
                FavoritesReviewState(reviewByLanguage = reviewByLanguage)
            }
        }
    }

    fun invalidateIntakeCacheForLanguage(language: Language) {
        lastIntakeAtByLanguage = lastIntakeAtByLanguage - language
    }

    fun invalidateAllIntakeCache() {
        lastIntakeAtByLanguage = emptyMap()
    }

    private suspend fun computeFavoritesReviewState(
        favoritesRepository: FavoritesRepository,
        intakeService: LearningIntake,
        statsService: StatsService,
    ): FavoritesReviewState = PerformanceMonitoring.startTrace("favorites_review_compute_state").useWithResult {
        withContext(Dispatchers.Default) {
            val favorites = favoritesRepository.getAllGroupedByLangAndLemma()
            val languages = favorites
                .map { it.language }
                .distinct()
            putMetric("favorites", favorites.size.toLong())
            putMetric("languages", languages.size.toLong())
            var intakeRuns = 0L
            languages.forEach { language ->
                if (shouldRunIntake(language)) {
                    intakeService.runIntake(language.code)
                    markIntakeRun(language)
                    intakeRuns += 1
                }
            }
            putMetric("intake_runs", intakeRuns)
            val reviewByLanguage = languages.associateWith { language ->
                statsService.reviewQueueStats(language.code)
                    .toFavoriteLanguageReviewState(language, intakeService)
            }
            putReviewStateMetrics(reviewByLanguage)
            FavoritesReviewState(reviewByLanguage = reviewByLanguage)
        }
    }

    private fun ReviewQueueStats.toFavoriteLanguageReviewState(
        language: Language,
        intakeService: LearningIntake,
    ) =
        FavoriteLanguageReviewState(
            dueCount = dueToday,
            activeCardCount = activeCardCount,
            delayedDueLemmaCount = delayedDueLemmaCount,
            delayedDueCardCount = delayedDueCardCount,
            pendingFavoriteLemmaCount = pendingFavoriteLemmaCount,
            canStudyPendingFavoritesNow = intakeService.canContinueWithPendingFavoritesNow(language.code),
            nextReviewAtEpochMs = nextReviewAtEpochMs,
        )

    internal fun shouldRunIntake(language: Language): Boolean {
        val lastRunAt = lastIntakeAtByLanguage[language] ?: return true
        return clock.now() - lastRunAt >= INTAKE_CACHE_TTL
    }

    internal fun markIntakeRun(language: Language) {
        lastIntakeAtByLanguage += language to clock.now()
    }

    private companion object {
        val INTAKE_CACHE_TTL = 5.minutes
    }
}

private fun PerformanceTrace.putReviewStateMetrics(reviewByLanguage: Map<Language, FavoriteLanguageReviewState>) {
    putMetric("due_cards", reviewByLanguage.values.sumOf { it.dueCount }.toLong())
    putMetric("active_cards", reviewByLanguage.values.sumOf { it.activeCardCount }.toLong())
    putMetric("delayed_due_lemmas", reviewByLanguage.values.sumOf { it.delayedDueLemmaCount }.toLong())
    putMetric("delayed_due_cards", reviewByLanguage.values.sumOf { it.delayedDueCardCount }.toLong())
    putMetric("pending_favorite_lemmas", reviewByLanguage.values.sumOf { it.pendingFavoriteLemmaCount }.toLong())
}
