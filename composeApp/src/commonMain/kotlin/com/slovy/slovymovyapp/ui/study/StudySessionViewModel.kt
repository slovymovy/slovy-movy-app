package com.slovy.slovymovyapp.ui.study

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.learning.GradeOutcome
import com.slovy.slovymovyapp.data.learning.intake.IntakeService
import com.slovy.slovymovyapp.data.learning.session.SessionCard
import com.slovy.slovymovyapp.data.learning.session.SessionCardLoadState
import com.slovy.slovymovyapp.data.learning.session.SessionService
import com.slovy.slovymovyapp.data.learning.stats.StatsService
import com.slovy.slovymovyapp.i18n.UiText
import com.slovy.slovymovyapp.speech.TTSStatus
import com.slovy.slovymovyapp.speech.Text2SpeechVoice
import com.slovy.slovymovyapp.speech.TextToSpeechManager
import com.slovy.slovymovyapp.speech.VoiceFilterHelper
import kotlinx.coroutines.launch
import slovymovyapp.composeapp.generated.resources.*
import kotlin.time.Clock
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
    private var currentVoiceIndex: Int = 0

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
        val lang = language ?: return
        viewModelScope.launch {
            try {
                val ttsLanguage = ttsManager.getAvailableLanguages()
                    .firstOrNull { it.language == lang } ?: return@launch
                val allVoices = ttsManager.getVoicesForLanguage(ttsLanguage)
                if (!voiceFilterHelper.hasEnabledVoices(ttsLanguage)) {
                    voiceFilterHelper.initializeDefaultVoices(ttsLanguage, allVoices)
                }
                availableVoices = voiceFilterHelper.filterVoicesByEnabled(allVoices, ttsLanguage)
                if (availableVoices.isNotEmpty()) {
                    currentVoiceIndex = availableVoices.indices.random()
                }
            } catch (_: Exception) {
                availableVoices = emptyList()
            }
        }
    }

    fun playAudio(text: String) {
        val active = state as? StudySessionUiState.Active ?: return
        state = active.copy(isPreparingAudio = true, isPlayingAudio = false)
        try {
            if (availableVoices.isNotEmpty()) {
                currentVoiceIndex = (currentVoiceIndex + 1) % availableVoices.size
                ttsManager.setVoice(availableVoices[currentVoiceIndex])
                ttsManager.speak(text)
            } else if (language != null) {
                ttsManager.speak(text, language)
            } else {
                ttsManager.speak(text)
            }
        } catch (_: Exception) {
            state = active.copy(isPreparingAudio = false, isPlayingAudio = false)
        }
    }

    fun stopAudio() {
        ttsManager.stop()
    }

    fun retry() {
        val failedCard = currentCard
        if (failedCard != null) {
            sessionService.putCardForLater(failedCard)
        }
        loadNextCard()
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

    fun rate(rating: StudyRating) {
        val active = state as? StudySessionUiState.Active ?: return
        if (active.side != StudyCardSide.BACK || active.isSubmittingReview) return

        val card = currentCard ?: return
        val outcome = currentOutcomes.firstOrNull { it.rating == rating.toDomainRating() } ?: return

        state = active.copy(isSubmittingReview = true)
        viewModelScope.launch {
            runCatching {
                sessionService.submitReview(
                    card = card,
                    outcome = outcome,
                    durationMs = (clock.now() - cardShownAt).inWholeMilliseconds,
                )
            }.onSuccess {
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
            runCatching {
                intakeService.runIntake(langCode)
                sessionTotal = statsService.globalStats(langCode).dueToday
                loadNextCard()
            }.onFailure { error ->
                state = StudySessionUiState.Error(
                    message = error.message?.let(UiText::Plain)
                        ?: UiText.Resource(Res.string.study_error_prepare_failed),
                    canRetry = true,
                )
            }
        }
    }

    private fun loadNextCard() {
        ttsManager.stop()
        state = StudySessionUiState.Loading(nextCardProgress())
        viewModelScope.launch {
            runCatching {
                sessionService.nextCard(langCode, sessionStartedAt)
                    .collect { sessionCard ->
                        when (sessionCard?.loadState()) {
                            null -> {
                                state = if (reviewedCount == 0) {
                                    StudySessionUiState.Empty
                                } else {
                                    StudySessionUiState.Complete(reviewedCount)
                                }
                            }

                            SessionCardLoadState.LOADING -> {
                                state = StudySessionUiState.Loading(nextCardProgress())
                            }

                            SessionCardLoadState.READY,
                            SessionCardLoadState.ERROR,
                                -> showLoadedCard(sessionCard)
                        }
                    }
            }.onFailure { error ->
                state = StudySessionUiState.Error(
                    message = error.message?.let(UiText::Plain)
                        ?: UiText.Resource(Res.string.study_error_next_card_failed),
                    canRetry = true,
                )
            }
        }
    }

    private fun showLoadedCard(sessionCard: SessionCard) {
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
        )
    }

    private fun nextCardProgress(): StudySessionProgressUiState =
        StudySessionProgressUiState(
            current = reviewedCount + 1,
            total = maxOf(sessionTotal, reviewedCount + 1),
        )

    override fun onCleared() {
        super.onCleared()
        ttsManager.removeOnStatusChangeListener(this)
        ttsManager.stop()
    }
}
