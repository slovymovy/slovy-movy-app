package com.slovy.slovymovyapp.ui.study

import androidx.compose.foundation.ScrollState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import com.slovy.slovymovyapp.data.favorites.Favorite
import com.slovy.slovymovyapp.data.favorites.FavoritesRepository
import com.slovy.slovymovyapp.data.learning.CardFamily
import com.slovy.slovymovyapp.data.learning.GradeOutcome
import com.slovy.slovymovyapp.data.learning.intake.IntakeService
import com.slovy.slovymovyapp.data.learning.session.SessionCard
import com.slovy.slovymovyapp.data.learning.session.SessionCardLoadState
import com.slovy.slovymovyapp.data.learning.session.SessionService
import com.slovy.slovymovyapp.data.learning.stats.StatsPipelineStage
import com.slovy.slovymovyapp.data.learning.stats.StatsService
import com.slovy.slovymovyapp.i18n.UiText
import com.slovy.slovymovyapp.logging.AppLogger
import com.slovy.slovymovyapp.speech.TTSStatus
import com.slovy.slovymovyapp.speech.Text2SpeechVoice
import com.slovy.slovymovyapp.speech.TextToSpeechManager
import com.slovy.slovymovyapp.speech.VoiceFilterHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import slovymovyapp.composeapp.generated.resources.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class StudySessionViewModel(
    private val langCode: String,
    private val favoritesRepository: FavoritesRepository,
    private val intakeService: IntakeService,
    private val sessionService: SessionService,
    private val statsService: StatsService,
    private val clock: Clock,
    private val ttsManager: TextToSpeechManager,
    private val voiceFilterHelper: VoiceFilterHelper,
    private val onReviewSubmitted: () -> Unit,
    private val onFavoriteChanged: (Language) -> Unit,
) : ViewModel() {

    private var currentState by mutableStateOf<StudySessionUiState>(StudySessionUiState.Loading())

    /**
     * Once the session is closing, UI state is frozen. Work already in flight — a review
     * submission, a removal, a suspend, a card load — must not hand the user back an active
     * session, and must not raise an error screen behind the exit. Only the exit path itself
     * writes [currentState] directly.
     */
    var state: StudySessionUiState
        get() = currentState
        private set(value) {
            if (isExitingSession) return
            currentState = value
        }

    /** Set when leaving the session must not stop at the reward screen. Observed by the screen. */
    var exitRequested by mutableStateOf(false)
        private set

    /** How the session ended, recorded at the point of truth rather than derived later. */
    private var completionArm: String? = null
    private var sessionEndLogged: Boolean = false

    val completeScrollState = ScrollState(0)
    val snackbarHostState = SnackbarHostState()

    private val language: Language? = Language.fromCodeOrNull(langCode)
    private val sessionStartedAt: Instant = clock.now()
    private var cardShownAt: Instant = sessionStartedAt
    private var currentCard: SessionCard? = null
    private var currentOutcomes: List<GradeOutcome> = emptyList()
    private var reviewedCount: Int = 0
    private var skippedCount: Int = 0
    private var sessionTotal: Int = 0
    private var sessionStartPipeline: List<StatsPipelineStage> = emptyList()
    private var sessionPrepared: Boolean = false
    private var isStarting: Boolean = false
    private var availableVoices: List<Text2SpeechVoice> = emptyList()
    private var currentVoiceIndex: Int = 0
    private val gradeCounts = mutableMapOf<StudyRating, Int>()
    private var autoplayEnabled: Boolean = false
    private var pendingRemovalFavorite: Favorite? = null
    private var isPreparingRemoval: Boolean = false
    private var postponeListeningCardsForSession: Boolean = false

    /**
     * Set once the session is closing. The session keeps its card on screen while the reward
     * snapshot loads, so work already in flight — a submitted review, a card load, a reveal —
     * must not write state behind the exit and hand the user back an active session.
     */
    private var isExitingSession: Boolean = false
    private var cardLoadJob: Job? = null
    private var reviewJob: Job? = null
    private val snackbarMutex = Mutex()

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

    /**
     * Loads the voices this session plays with. Also called on resume: the ids are only valid for
     * the engine that reported them, and the user may have changed voices or the default TTS engine
     * while away, which the cached list would otherwise outlive for the rest of the session.
     *
     * The cache is dropped before the load, not replaced after it: binding a newly chosen engine
     * takes long enough for the user to tap play first, and [playAudio] resolves voices itself when
     * it finds none rather than handing the previous engine's ids to the new one.
     */
    fun loadVoices() {
        availableVoices = emptyList()
        viewModelScope.launch {
            try {
                loadVoicesSync()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.warn(TAG, "Unable to load study voices for $langCode", e)
                availableVoices = emptyList()
            }
        }
    }

    private suspend fun loadVoicesSync() {
        val lang = language ?: return
        val ttsLanguage = ttsManager.getAvailableLanguages()
            .firstOrNull { it.language == lang } ?: return
        availableVoices = voiceFilterHelper.loadEnabledVoices(ttsManager, ttsLanguage)
        if (availableVoices.isNotEmpty()) {
            currentVoiceIndex = availableVoices.indices.random()
        }
    }

    fun playAudio(text: String) {
        playAudio(text = text, logClick = true)
    }

    private fun playAudio(text: String, logClick: Boolean) {
        val active = state as? StudySessionUiState.Active ?: return
        if (logClick) {
            Analytics.logEvent(
                AnalyticsEvent.WORD_PLAY_CLICK,
                mapOf("lang" to langCode, "source" to "study"),
            )
        }
        state = active.copy(isPreparingAudio = true, isPlayingAudio = false)
        viewModelScope.launch {
            try {
                if (availableVoices.isEmpty()) loadVoicesSync()
                if (availableVoices.isNotEmpty()) {
                    currentVoiceIndex = (currentVoiceIndex + 1) % availableVoices.size
                    ttsManager.setVoice(availableVoices[currentVoiceIndex])
                    ttsManager.speak(text)
                } else {
                    val latest = state as? StudySessionUiState.Active ?: return@launch
                    state = latest.copy(isPreparingAudio = false, isPlayingAudio = false)
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

    fun openOverflowMenu() {
        val active = state as? StudySessionUiState.Active ?: return
        if (active.isSubmittingReview) return
        Analytics.logEvent(
            AnalyticsEvent.STUDY_ACTIONS_MENU_OPEN,
            mapOf("lang" to langCode),
        )
        state = active.copy(isOverflowMenuOpen = true, removeConfirmation = null)
    }

    fun dismissOverflowMenu() {
        val active = state as? StudySessionUiState.Active ?: return
        state = active.copy(isOverflowMenuOpen = false)
    }

    fun toggleAutoplay() {
        val active = state as? StudySessionUiState.Active ?: return
        if (active.isSubmittingReview) return
        autoplayEnabled = !autoplayEnabled
        Analytics.logEvent(
            AnalyticsEvent.STUDY_AUTOPLAY_TOGGLED,
            mapOf("lang" to langCode, "enabled" to autoplayEnabled.toString()),
        )
        state = active.copy(
            isAutoplayEnabled = autoplayEnabled,
        )
        if (autoplayEnabled) {
            autoplayFrontAudioText(active.card)?.let { playAudio(text = it, logClick = false) }
        }
    }

    fun suspendCurrentWord(suspendedMessage: String, undoLabel: String) {
        if (isPreparingRemoval) return
        val active = state as? StudySessionUiState.Active ?: return
        if (active.isSubmittingReview) return
        val card = currentCard ?: return
        val outcomes = currentOutcomes
        currentCard = null
        currentOutcomes = emptyList()
        state = active.copy(isOverflowMenuOpen = false, isSubmittingReview = true)
        viewModelScope.launch {
            val snapshot = try {
                sessionService.suspendWord(card, WORD_SUSPEND_DURATION)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                currentCard = card
                currentOutcomes = outcomes
                val latest = state as? StudySessionUiState.Active ?: active
                state = latest.copy(
                    isOverflowMenuOpen = false,
                    isSubmittingReview = false,
                )
                return@launch
            }
            Analytics.logEvent(
                AnalyticsEvent.STUDY_WORD_SUSPENDED,
                mapOf(
                    "lang" to langCode,
                    "family" to card.card.family.name.lowercase(),
                    "variant" to card.variant.kind.name.lowercase(),
                    "days" to WORD_SUSPEND_DURATION.inWholeDays,
                ),
            )
            skippedCount += 1
            loadNextCard()
            val result = showStudySnackbar(
                message = suspendedMessage,
                actionLabel = if (snapshot.isNotEmpty()) undoLabel else null,
                duration = SnackbarDuration.Short,
            )
            if (result != SnackbarResult.ActionPerformed || snapshot.isEmpty()) return@launch

            try {
                sessionService.restorePausedWord(snapshot)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                return@launch
            }
            skippedCount = (skippedCount - 1).coerceAtLeast(0)
            val activeAfterUndo = state as? StudySessionUiState.Active
            if (activeAfterUndo == null || currentCard == null) {
                currentCard = null
                currentOutcomes = emptyList()
                loadNextCard()
            } else {
                state = activeAfterUndo.copy(progress = nextCardProgress())
            }
        }
    }

    fun postponeListeningCards(postponedMessage: String) {
        if (isPreparingRemoval) return
        val active = state as? StudySessionUiState.Active ?: return
        if (active.isSubmittingReview) return
        if (active.card !is StudyCardUiState.Listening) return
        currentCard ?: return
        Analytics.logEvent(
            AnalyticsEvent.STUDY_LISTENING_POSTPONED,
            mapOf("lang" to langCode),
        )
        postponeListeningCardsForSession = true
        skippedCount += 1
        currentCard = null
        currentOutcomes = emptyList()
        state = active.copy(isPreparingAudio = false, isPlayingAudio = false)
        loadNextCard()
        viewModelScope.launch {
            showStudySnackbar(
                message = postponedMessage,
                actionLabel = null,
                duration = SnackbarDuration.Short,
            )
        }
    }

    fun requestRemoveFromLibrary() {
        val active = state as? StudySessionUiState.Active ?: return
        if (active.isSubmittingReview || isPreparingRemoval) return
        val card = currentCard ?: return
        val lang = Language.fromCodeOrNull(card.card.langCode) ?: return
        isPreparingRemoval = true
        state = active.copy(isOverflowMenuOpen = false, isSubmittingReview = true)
        viewModelScope.launch {
            try {
                val favorite = favoritesRepository.getOne(card.card.senseId.toString(), lang)
                val latest = state as? StudySessionUiState.Active
                isPreparingRemoval = false
                if (latest == null) return@launch
                if (favorite == null) {
                    state = latest.copy(isOverflowMenuOpen = false, isSubmittingReview = false)
                    loadNextCard()
                    return@launch
                }
                pendingRemovalFavorite = favorite
                state = latest.copy(
                    isOverflowMenuOpen = false,
                    isSubmittingReview = false,
                    removeConfirmation = StudyRemoveConfirmationUiState(favorite.lemma),
                )
            } catch (e: CancellationException) {
                isPreparingRemoval = false
                throw e
            } catch (_: Exception) {
                isPreparingRemoval = false
                val latest = state as? StudySessionUiState.Active ?: return@launch
                state = latest.copy(isOverflowMenuOpen = false, isSubmittingReview = false)
            }
        }
    }

    fun dismissRemoveConfirmation() {
        pendingRemovalFavorite = null
        val active = state as? StudySessionUiState.Active ?: return
        state = active.copy(removeConfirmation = null, isSubmittingReview = false)
    }

    fun confirmRemoveFromLibrary(
        removedMessage: String,
        undoLabel: String,
    ) {
        val favorite = pendingRemovalFavorite ?: return
        pendingRemovalFavorite = null
        val active = state as? StudySessionUiState.Active
        if (active != null) {
            state = active.copy(
                removeConfirmation = null,
                isOverflowMenuOpen = false,
                isSubmittingReview = true,
            )
        }
        viewModelScope.launch {
            val snapshot = try {
                favoritesRepository.remove(favorite.senseId, favorite.language)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                val latest = state as? StudySessionUiState.Active ?: active ?: return@launch
                state = latest.copy(
                    removeConfirmation = null,
                    isOverflowMenuOpen = false,
                    isSubmittingReview = false,
                )
                return@launch
            }

            Analytics.logEvent(
                AnalyticsEvent.FAVORITES_REMOVE,
                mapOf("lang" to favorite.language.code, "source" to "study"),
            )
            onFavoriteChanged(favorite.language)
            skippedCount += 1
            currentCard = null
            currentOutcomes = emptyList()
            loadNextCard()

            val result = showStudySnackbar(
                message = removedMessage,
                actionLabel = if (snapshot != null) undoLabel else null,
                duration = SnackbarDuration.Short,
            )
            if (result != SnackbarResult.ActionPerformed || snapshot == null) return@launch

            try {
                favoritesRepository.restoreForUndo(snapshot)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                val latest = state as? StudySessionUiState.Active ?: return@launch
                state = latest.copy(isSubmittingReview = false)
                return@launch
            }

            Analytics.logEvent(
                AnalyticsEvent.FAVORITES_SAVE,
                mapOf("lang" to favorite.language.code, "source" to "study_undo"),
            )
            onFavoriteChanged(favorite.language)
            skippedCount = (skippedCount - 1).coerceAtLeast(0)
            val activeAfterUndo = state as? StudySessionUiState.Active
            if (activeAfterUndo == null || currentCard == null) {
                currentCard = null
                currentOutcomes = emptyList()
                loadNextCard()
            } else {
                state = activeAfterUndo.copy(progress = nextCardProgress())
            }
        }
    }

    fun stopAudio() {
        ttsManager.stop()
    }

    fun retry() {
        if (isExitingSession) return
        val failedCard = currentCard
        if (failedCard == null) {
            if (sessionPrepared) {
                loadNextCard()
            } else {
                start()
            }
            return
        }
        viewModelScope.launch {
            sessionService.putCardForLater(failedCard)
            loadNextCard()
        }
    }

    /**
     * Handles the close (cross) action. A session with real work behind it earns its summary
     * before leaving: the reward hero is a diff between the session's start and end pipeline, so
     * a milestone crossed on card 60 of 90 is exactly as real as one crossed on the last card, and
     * today it is discarded. Below [REWARD_ON_CANCEL_MIN_CARDS] the tap exits straight away — a
     * cross means "get me out", and a summary of a handful of cards is noise.
     */
    fun requestExit() {
        // Close is never disabled, so it must never be a no-op: a second tap while the reward is
        // still being built means leave now. Without this, any state reached after the exit began
        // would have a dead close button.
        if (isExitingSession) {
            exitRequested = true
            return
        }
        val showReward = shouldRewardOnExit(
            reviewedCount = reviewedCount,
            isAlreadyComplete = state is StudySessionUiState.Complete,
        )
        isExitingSession = true
        completionArm = if (showReward) COMPLETION_CANCEL_REWARDED else COMPLETION_CANCEL
        cardLoadJob?.cancel()
        // Logged here, not when the reward screen is dismissed: the session is over at the tap,
        // and a reward left via the system back button would otherwise report nothing.
        logSessionEnd()
        if (!showReward) {
            exitRequested = true
            return
        }
        // Freeze the card that stays on screen while the snapshot loads. Rating is the only
        // control not already gated on isSubmittingReview, and an open sheet or confirmation
        // would outlive the session it belongs to.
        (state as? StudySessionUiState.Active)?.let { active ->
            pendingRemovalFavorite = null
            currentState = active.copy(
                isSubmittingReview = true,
                isOverflowMenuOpen = false,
                removeConfirmation = null,
            )
        }
        viewModelScope.launch {
            ttsManager.stop()
            // Let a review submitted just before the tap finish first, so the summary counts it
            // and its failure path cannot land behind the reward. It is bounded work: the
            // continuation only writes counters and calls loadNextCard, which exits early now.
            reviewJob?.join()
            // The current card stays put rather than flashing a spinner; buildCompleteState
            // absorbs its own failures into a heroless summary, so there is no error path that
            // could strand the user here.
            val reward = buildCompleteState()
            currentState = StudySessionUiState.Complete(reward)
        }
    }

    fun reveal() {
        if (isExitingSession) return
        val active = state as? StudySessionUiState.Active ?: return
        if (active.side == StudyCardSide.BACK) return

        viewModelScope.launch {
            val card = currentCard ?: return@launch
            currentOutcomes = sessionService.previewRatings(card)
            state = active.copy(
                side = StudyCardSide.BACK,
                ratingOptions = currentOutcomes.toStudyRatings(),
            )
            if (autoplayEnabled) {
                autoplayBackAudioText(active.card)?.let { playAudio(text = it, logClick = false) }
            }
        }
    }

    fun revealFirstLetterHint() {
        val active = state as? StudySessionUiState.Active ?: return
        if (active.side != StudyCardSide.FRONT) return
        val updatedCard = when (val card = active.card) {
            is StudyCardUiState.Production -> {
                if (card.firstLetterHint == null || card.firstLetterHintRevealed) return
                card.copy(firstLetterHintRevealed = true)
            }

            is StudyCardUiState.Cloze -> {
                if (card.firstLetterHint == null || card.firstLetterHintRevealed) return
                card.copy(firstLetterHintRevealed = true)
            }

            else -> return
        }

        state = active.copy(card = updatedCard)
        logHintRevealed(updatedCard.id, updatedCard.back.headline, hintKind = "first_letter")
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
        if (isPreparingRemoval) return
        val active = state as? StudySessionUiState.Active ?: return
        if (active.side != StudyCardSide.BACK || active.isSubmittingReview) return

        val card = currentCard ?: return
        val outcome = currentOutcomes.firstOrNull { it.rating == rating.toDomainRating() } ?: return

        val durationMs = (clock.now() - cardShownAt).inWholeMilliseconds
        state = active.copy(isSubmittingReview = true)
        reviewJob = viewModelScope.launch {
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
        if (isStarting) return
        isStarting = true
        state = StudySessionUiState.Loading()
        sessionPrepared = false
        sessionStartPipeline = emptyList()
        viewModelScope.launch {
            try {
                PerformanceMonitoring.startTrace("study_session_start").useWithResult {
                    putAttribute("lang", langCode)
                    try {
                        val intakeResult = intakeService.runIntake(langCode)
                        putMetric("cards_created", intakeResult.cardsCreated.toLong())
                        putMetric("activated_favorites", intakeResult.activated.size.toLong())
                        putMetric("skip_reasons", intakeResult.skipped.size.toLong())
                        sessionStartPipeline = pipelineSnapshot()
                        sessionTotal = statsService.dueNow(langCode, excludedCardFamiliesForSession())
                        sessionPrepared = true
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
            } finally {
                isStarting = false
            }
        }
    }

    private fun loadNextCard() {
        // Catches work that outlived the exit — most importantly a review submitted just before
        // close, whose success path loads the next card.
        if (isExitingSession) return
        ttsManager.stop()
        cardLoadJob?.cancel()
        cardLoadJob = viewModelScope.launch {
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
                    nextSessionCard()
                        .collect { sessionCard ->
                            when (sessionCard?.loadState()) {
                                null -> {
                                    showLoadingJob.cancel()
                                    val completedCount = reviewedCount + skippedCount
                                    markResult(if (completedCount == 0) "empty" else "complete")
                                    state = if (completedCount == 0) {
                                        StudySessionUiState.Empty
                                    } else {
                                        StudySessionUiState.Complete(buildCompleteState())
                                    }
                                    // The queue running out ends the session, whether or not the
                                    // reward screen is ever dismissed.
                                    if (completedCount > 0) {
                                        completionArm = COMPLETION_FINISHED
                                        logSessionEnd()
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
        val uiCard = sessionCard.toStudyCardUiState(loadFavoriteLemmas())
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
            isAutoplayEnabled = autoplayEnabled,
        )
        if (autoplayEnabled) {
            autoplayFrontAudioText(uiCard)?.let { playAudio(text = it, logClick = false) }
        }
    }

    private suspend fun loadFavoriteLemmas(): Set<String> {
        val lang = language ?: return emptySet()
        return try {
            favoritesRepository.getFavoriteLemmas(lang)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.warn(TAG, "Unable to load favorite lemmas for study synonyms lang=$langCode", e)
            emptySet()
        }
    }

    private fun autoplayFrontAudioText(card: StudyCardUiState): String? =
        when (card) {
            is StudyCardUiState.Recognition -> card.promptAudioText
            is StudyCardUiState.Listening -> card.promptAudioText
            is StudyCardUiState.Production,
            is StudyCardUiState.Cloze,
                -> null
        }

    // For cards whose front side has no audio (Production / Cloze) play the back audio
    // when the user reveals it, so autoplay always lets the user hear the word.
    private fun autoplayBackAudioText(card: StudyCardUiState): String? =
        if (autoplayFrontAudioText(card) != null) null else card.back.audioText

    private fun nextSessionCard() =
        sessionService.nextCard(
            langCode = langCode,
            sessionStartedAt = sessionStartedAt,
            excludedFamilies = excludedCardFamiliesForSession(),
        )

    private suspend fun buildCompleteState(): StudySessionCompleteUiState {
        val snapshot = try {
            rewardSnapshot()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.warn(TAG, "Unable to build study reward snapshot for $langCode", e)
            null
        }
        val streakDays = snapshot?.streakDays ?: 0
        // Counted after the snapshot resolves: work finishing during that query still belongs in
        // the summary.
        return StudySessionCompleteUiState(
            cardsReviewed = reviewedCount + skippedCount,
            minutes = (clock.now() - sessionStartedAt).inWholeMinutes.toInt().coerceAtLeast(1),
            streakDays = streakDays,
            message = randomCompletionMessage(language),
            hero = if (snapshot == null || reviewedCount == 0) {
                StudySessionCompleteHero.None
            } else {
                resolveStudySessionCompleteHero(
                    streakDays = streakDays,
                    pipelineBefore = sessionStartPipeline,
                    pipelineAfter = snapshot.pipeline,
                )
            },
        )
    }

    private suspend fun pipelineSnapshot(): List<StatsPipelineStage> =
        withContext(Dispatchers.IO) {
            statsService.pipelineSnapshot(langCode)
        }

    private suspend fun rewardSnapshot() =
        withContext(Dispatchers.IO) {
            statsService.rewardSnapshot(langCode)
        }

    private fun excludedCardFamiliesForSession(): Set<CardFamily> =
        if (postponeListeningCardsForSession) {
            setOf(CardFamily.RECOGNIZE_VOICE)
        } else {
            emptySet()
        }

    private suspend fun nextCardProgress(): StudySessionProgressUiState {
        val completedCount = reviewedCount + skippedCount
        val current = completedCount + 1
        val dueNow = statsService.dueNow(langCode, excludedCardFamiliesForSession())
        val projectedTotal = completedCount + dueNow
        sessionTotal = if (postponeListeningCardsForSession) {
            maxOf(projectedTotal, current)
        } else {
            maxOf(sessionTotal, projectedTotal, current)
        }
        return StudySessionProgressUiState(
            current = current,
            total = sessionTotal,
        )
    }

    private suspend fun showStudySnackbar(
        message: String,
        actionLabel: String?,
        duration: SnackbarDuration,
    ): SnackbarResult = snackbarMutex.withLock {
        snackbarHostState.showSnackbar(
            message = message,
            actionLabel = actionLabel,
            duration = duration,
        )
    }

    /**
     * Reports how the session ended, exactly once, at the moment it ends rather than when the
     * user happens to leave the screen. Tying it to a dismissal would lose every session closed
     * with the system back button, which is also why [onCleared] is a backstop.
     *
     * `completion` arms: `finished` (queue ran out), `cancel_rewarded` (backed out with enough
     * work behind it to earn the summary), `cancel` (backed out before that). One event per
     * session, so the finished-vs-cancel split stays comparable to past data.
     */
    private fun logSessionEnd() {
        if (sessionEndLogged) return
        sessionEndLogged = true
        // No recorded arm means the session was left without the close button — system back.
        val completion = completionArm ?: COMPLETION_CANCEL
        Analytics.logEvent(
            if (completion == COMPLETION_FINISHED) {
                AnalyticsEvent.STUDY_END_SESSION
            } else {
                AnalyticsEvent.STUDY_CANCEL_SESSION
            },
            buildSessionEndParams(completion),
        )
    }

    private fun buildSessionEndParams(completion: String): Map<String, Any> = mapOf(
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
        // Backstop for every exit that never goes through the close button — chiefly the system
        // back button, which pops the destination directly. No-ops when the end was already
        // reported. Process death is still unreported, as before.
        logSessionEnd()
        ttsManager.removeOnStatusChangeListener(this)
        ttsManager.stop()
    }

    companion object {
        private const val TAG = "StudySessionViewModel"
        private const val LOADING_DEBOUNCE_MS = 150L

        // `completion` arms on the session-end events.
        private const val COMPLETION_FINISHED = "finished"
        private const val COMPLETION_CANCEL = "cancel"
        private const val COMPLETION_CANCEL_REWARDED = "cancel_rewarded"

        /**
         * Reviews needed before closing the session shows the reward screen instead of leaving
         * immediately. Roughly five to ten minutes of focused work.
         */
        private const val REWARD_ON_CANCEL_MIN_CARDS = 50
        private val WORD_SUSPEND_DURATION = 30.days

        /**
         * Whether closing the session should stop at the reward screen. Skipped and suspended
         * cards do not count — only reviews are work.
         */
        internal fun shouldRewardOnExit(reviewedCount: Int, isAlreadyComplete: Boolean): Boolean =
            !isAlreadyComplete && reviewedCount >= REWARD_ON_CANCEL_MIN_CARDS
    }
}
