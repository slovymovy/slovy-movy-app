package com.slovy.slovymovyapp.ui.study

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slovy.slovymovyapp.analytics.Analytics
import com.slovy.slovymovyapp.analytics.AnalyticsEvent
import com.slovy.slovymovyapp.analytics.PerformanceMonitoring
import com.slovy.slovymovyapp.analytics.putAttributes
import com.slovy.slovymovyapp.analytics.useWithResult
import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.learning.GradeOutcome
import com.slovy.slovymovyapp.data.learning.intake.IntakeService
import com.slovy.slovymovyapp.data.learning.session.SessionCard
import com.slovy.slovymovyapp.data.learning.session.SessionCardLoadState
import com.slovy.slovymovyapp.data.learning.session.SessionService
import com.slovy.slovymovyapp.data.learning.stats.StatsService
import com.slovy.slovymovyapp.i18n.UiText
import com.slovy.slovymovyapp.logging.AppLogger
import com.slovy.slovymovyapp.speech.TTSStatus
import com.slovy.slovymovyapp.speech.Text2SpeechVoice
import com.slovy.slovymovyapp.speech.TextToSpeechManager
import com.slovy.slovymovyapp.speech.VoiceFilterHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import slovymovyapp.composeapp.generated.resources.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class StudySessionViewModel(
    private val langCode: String,
    private val intakeService: IntakeService,
    private val sessionService: SessionService,
    private val statsService: StatsService,
    private val clock: Clock,
    private val ttsManager: TextToSpeechManager,
    private val voiceFilterHelper: VoiceFilterHelper,
    private val onReviewSubmitted: () -> Unit,
) : ViewModel() {

    var state by mutableStateOf<StudySessionUiState>(StudySessionUiState.Loading())
        private set
    val completeScrollState = ScrollState(0)

    private val language: Language? = Language.fromCodeOrNull(langCode)
    private val sessionStartedAt: Instant = clock.now()
    private var cardShownAt: Instant = sessionStartedAt
    private var currentCard: SessionCard? = null
    private var currentOutcomes: List<GradeOutcome> = emptyList()
    private var reviewedCount: Int = 0
    private var sessionTotal: Int = 0
    private var availableVoices: List<Text2SpeechVoice> = emptyList()
    private var audioMutedByVoiceFilter: Boolean = false
    private var currentVoiceIndex: Int = 0
    private val gradeCounts = mutableMapOf<StudyRating, Int>()

    init {
        ttsManager.addOnStatusChangeListener(this) { status ->
            val active = state as? StudySessionUiState.Active ?: return@addOnStatusChangeListener
            state = when (status) {
                TTSStatus.SPEAKING -> active.copy(isPreparingAudio = false, isPlayingAudio = true)
                TTSStatus.IDLE -> active.copy(isPreparingAudio = false, isPlayingAudio = false)
            }
        }
        loadVoices()
        start()
    }

    private fun loadVoices() {
        viewModelScope.launch {
            try {
                loadVoicesSync()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.warn(TAG, "Unable to load study voices for $langCode", e)
                availableVoices = emptyList()
                audioMutedByVoiceFilter = false
            }
        }
    }

    private suspend fun loadVoicesSync() {
        val lang = language ?: run {
            audioMutedByVoiceFilter = false
            return
        }
        val ttsLanguage = ttsManager.getAvailableLanguages()
            .firstOrNull { it.language == lang } ?: run {
                audioMutedByVoiceFilter = false
                return
            }
        val allVoices = ttsManager.getVoicesForLanguage(ttsLanguage)
        voiceFilterHelper.initializeDefaultVoices(ttsLanguage, allVoices)
        val hasConfiguredSelection = voiceFilterHelper.hasEnabledVoices(ttsLanguage)
        val enabledVoiceIds = voiceFilterHelper.getEnabledVoices(ttsLanguage)
        audioMutedByVoiceFilter = hasConfiguredSelection && enabledVoiceIds.isEmpty()
        availableVoices = if (hasConfiguredSelection) {
            allVoices.filter { it.id in enabledVoiceIds }
        } else {
            allVoices
        }
        if (availableVoices.isNotEmpty()) {
            currentVoiceIndex = availableVoices.indices.random()
        }
    }

    fun playAudio(text: String) {
        val active = state as? StudySessionUiState.Active ?: return
        Analytics.logEvent(
            AnalyticsEvent.WORD_PLAY_CLICK,
            mapOf("lang" to langCode, "source" to "study"),
        )
        state = active.copy(isPreparingAudio = true, isPlayingAudio = false)
        viewModelScope.launch {
            try {
                if (availableVoices.isEmpty()) loadVoicesSync()
                if (audioMutedByVoiceFilter) {
                    val latest = state as? StudySessionUiState.Active ?: return@launch
                    state = latest.copy(isPreparingAudio = false, isPlayingAudio = false)
                } else if (availableVoices.isNotEmpty()) {
                    currentVoiceIndex = (currentVoiceIndex + 1) % availableVoices.size
                    ttsManager.setVoice(availableVoices[currentVoiceIndex])
                    ttsManager.speak(text)
                } else if (language != null) {
                    ttsManager.speak(text, language)
                } else {
                    ttsManager.speak(text)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.warn(TAG, "Unable to play study audio for $langCode", e)
                Analytics.logEvent(
                    AnalyticsEvent.TTS_PLAY_FAILED,
                    mapOf(
                        "lang" to langCode,
                        "source" to "study",
                        "error" to (e.message ?: e::class.simpleName ?: "unknown"),
                    ),
                )
                val latest = state as? StudySessionUiState.Active ?: return@launch
                state = latest.copy(isPreparingAudio = false, isPlayingAudio = false)
            }
        }
    }

    fun stopAudio() {
        ttsManager.stop()
    }

    fun retry() {
        val failedCard = currentCard
        if (failedCard == null) {
            loadNextCard()
            return
        }
        viewModelScope.launch {
            sessionService.putCardForLater(failedCard)
            loadNextCard()
        }
    }

    fun reveal() {
        val active = state as? StudySessionUiState.Active ?: return
        if (active.side == StudyCardSide.BACK) return

        viewModelScope.launch {
            val card = currentCard ?: return@launch
            currentOutcomes = sessionService.previewRatings(card)
            state = active.copy(
                side = StudyCardSide.BACK,
                ratingOptions = currentOutcomes.toStudyRatings(),
            )
        }
    }

    fun revealFirstLetterHint() {
        val active = state as? StudySessionUiState.Active ?: return
        if (active.side != StudyCardSide.FRONT) return
        val production = active.card as? StudyCardUiState.Production ?: return
        if (production.firstLetterHint == null || production.firstLetterHintRevealed) return

        state = active.copy(
            card = production.copy(firstLetterHintRevealed = true),
        )
        logHintRevealed(production.id, production.back.headline, hintKind = "first_letter")
    }

    fun revealTranslationHint() {
        val active = state as? StudySessionUiState.Active ?: return
        if (active.side != StudyCardSide.FRONT) return
        val cloze = active.card as? StudyCardUiState.Cloze ?: return
        if (cloze.translationHint == null || cloze.translationHintRevealed) return

        state = active.copy(
            card = cloze.copy(translationHintRevealed = true),
        )
        logHintRevealed(cloze.id, cloze.back.headline, hintKind = "translation")
    }

    private fun logHintRevealed(cardId: String, lemma: String, hintKind: String) {
        val sessionCard = currentCard
        Analytics.logEvent(
            AnalyticsEvent.STUDY_HINT_REVEALED,
            mapOf(
                "lang" to langCode,
                "card_id" to cardId,
                "lemma" to lemma,
                "family" to (sessionCard?.card?.family?.name?.lowercase() ?: "unknown"),
                "variant" to (sessionCard?.variant?.kind?.name?.lowercase() ?: "unknown"),
                "hint" to hintKind,
            ),
        )
    }

    fun setViewedSense(senseId: String) {
        val active = state as? StudySessionUiState.Active ?: return
        if (active.card.senses.none { it.id == senseId }) return
        if (active.viewedSenseId == senseId) return
        state = active.copy(viewedSenseId = senseId)
    }

    fun rate(rating: StudyRating) {
        val active = state as? StudySessionUiState.Active ?: return
        if (active.side != StudyCardSide.BACK || active.isSubmittingReview) return

        val card = currentCard ?: return
        val outcome = currentOutcomes.firstOrNull { it.rating == rating.toDomainRating() } ?: return

        val durationMs = (clock.now() - cardShownAt).inWholeMilliseconds
        state = active.copy(isSubmittingReview = true)
        viewModelScope.launch {
            runCatching {
                sessionService.submitReview(
                    card = card,
                    outcome = outcome,
                    durationMs = durationMs,
                )
            }.onSuccess {
                Analytics.logEvent(
                    AnalyticsEvent.STUDY_CARD_GRADED,
                    mapOf(
                        "lang" to langCode,
                        "rating" to rating.name.lowercase(),
                        "family" to card.card.family.name.lowercase(),
                        "duration_ms" to durationMs,
                    ),
                )
                gradeCounts[rating] = (gradeCounts[rating] ?: 0) + 1
                onReviewSubmitted()
                reviewedCount += 1
                currentCard = null
                currentOutcomes = emptyList()
                loadNextCard()
            }.onFailure { error ->
                state = StudySessionUiState.Error(
                    message = error.message?.let(UiText::Plain)
                        ?: UiText.Resource(Res.string.study_error_review_save_failed),
                    canRetry = true,
                )
            }
        }
    }

    private fun start() {
        state = StudySessionUiState.Loading()
        viewModelScope.launch {
            PerformanceMonitoring.startTrace("study_session_start").useWithResult {
                putAttribute("lang", langCode)
                try {
                    val intakeResult = intakeService.runIntake(langCode)
                    putMetric("cards_created", intakeResult.cardsCreated.toLong())
                    putMetric("activated_favorites", intakeResult.activated.size.toLong())
                    putMetric("skip_reasons", intakeResult.skipped.size.toLong())
                    sessionTotal = statsService.dueNow(langCode)
                    putMetric("due_now", sessionTotal.toLong())
                    loadNextCard()
                } catch (e: CancellationException) {
                    throw e
                } catch (error: Throwable) {
                    markResult("failed")
                    state = StudySessionUiState.Error(
                        message = error.message?.let(UiText::Plain)
                            ?: UiText.Resource(Res.string.study_error_prepare_failed),
                        canRetry = true,
                    )
                }
            }
        }
    }

    private fun loadNextCard() {
        ttsManager.stop()
        viewModelScope.launch {
            PerformanceMonitoring.startTrace("study_card_load").useWithResult {
                putAttributes(
                    mapOf(
                        "lang" to langCode,
                        "reviewed_count" to reviewedCount,
                    ),
                )
                // Debounce the loading indicator: keep showing the previous card briefly so a
                // fast-loading next card doesn't cause a visible Loading flash.
                val showLoadingJob = launch {
                    delay(LOADING_DEBOUNCE_MS.milliseconds)
                    state = StudySessionUiState.Loading(nextCardProgress())
                }
                try {
                    sessionService.nextCard(langCode, sessionStartedAt)
                        .collect { sessionCard ->
                            when (sessionCard?.loadState()) {
                                null -> {
                                    showLoadingJob.cancel()
                                    markResult(if (reviewedCount == 0) "empty" else "complete")
                                    state = if (reviewedCount == 0) {
                                        StudySessionUiState.Empty
                                    } else {
                                        StudySessionUiState.Complete(
                                            reviewedCount = reviewedCount,
                                            message = randomCompletionMessage(language),
                                        )
                                    }
                                }

                                SessionCardLoadState.LOADING -> {
                                    incrementMetric("loading_emissions")
                                    // Let showLoadingJob switch to Loading after the debounce.
                                }

                                SessionCardLoadState.READY,
                                SessionCardLoadState.ERROR,
                                    -> {
                                    showLoadingJob.cancel()
                                    markResult(sessionCard.loadState().name.lowercase())
                                    putAttributes(
                                        mapOf(
                                            "family" to sessionCard.card.family.name.lowercase(),
                                            "variant" to sessionCard.variant.kind.name.lowercase(),
                                        ),
                                    )
                                    showLoadedCard(sessionCard)
                                }
                            }
                        }
                } catch (e: CancellationException) {
                    showLoadingJob.cancel()
                    throw e
                } catch (error: Throwable) {
                    showLoadingJob.cancel()
                    markResult("failed")
                    state = StudySessionUiState.Error(
                        message = error.message?.let(UiText::Plain)
                            ?: UiText.Resource(Res.string.study_error_next_card_failed),
                        canRetry = true,
                    )
                }
            }
        }
    }

    private suspend fun showLoadedCard(sessionCard: SessionCard) {
        currentCard = sessionCard
        val uiCard = sessionCard.toStudyCardUiState()
        if (uiCard == null) {
            state = StudySessionUiState.Error(
                message = UiText.Resource(Res.string.study_error_card_data_missing),
                canRetry = true,
            )
            return
        }

        cardShownAt = clock.now()
        state = StudySessionUiState.Active(
            progress = nextCardProgress(),
            card = uiCard,
            side = StudyCardSide.FRONT,
            ratingOptions = emptyList(),
            viewedSenseId = uiCard.activeSenseId,
        )
    }

    private suspend fun nextCardProgress(): StudySessionProgressUiState {
        val current = reviewedCount + 1
        val projectedTotal = reviewedCount + statsService.dueNow(langCode)
        sessionTotal = maxOf(sessionTotal, projectedTotal, current)
        return StudySessionProgressUiState(
            current = current,
            total = sessionTotal,
        )
    }

    fun buildSessionEndParams(completion: String): Map<String, Any> = mapOf(
        "lang" to langCode,
        "completion" to completion,
        "cards_reviewed" to reviewedCount.toLong(),
        "again_count" to (gradeCounts[StudyRating.AGAIN] ?: 0).toLong(),
        "hard_count" to (gradeCounts[StudyRating.HARD] ?: 0).toLong(),
        "good_count" to (gradeCounts[StudyRating.GOOD] ?: 0).toLong(),
        "easy_count" to (gradeCounts[StudyRating.EASY] ?: 0).toLong(),
        "duration_ms" to (clock.now() - sessionStartedAt).inWholeMilliseconds,
    )

    override fun onCleared() {
        super.onCleared()
        ttsManager.removeOnStatusChangeListener(this)
        ttsManager.stop()
    }

    companion object {
        private const val TAG = "StudySessionViewModel"
        private const val LOADING_DEBOUNCE_MS = 150L
    }
}
