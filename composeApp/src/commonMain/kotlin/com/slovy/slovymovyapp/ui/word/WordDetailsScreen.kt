package com.slovy.slovymovyapp.ui.word

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slovy.slovymovyapp.analytics.Analytics
import com.slovy.slovymovyapp.analytics.AnalyticsEvent
import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.favorites.FavoritesRepository
import com.slovy.slovymovyapp.data.remote.*
import com.slovy.slovymovyapp.speech.*
import com.slovy.slovymovyapp.ui.AppNavigationBar
import com.slovy.slovymovyapp.ui.AppScreen
import com.slovy.slovymovyapp.ui.SpeakerVector
import com.slovy.slovymovyapp.ui.VoiceSetupBottomSheet
import com.slovy.slovymovyapp.ui.theme.serifFontFamily
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import slovymovyapp.composeapp.generated.resources.*
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds

sealed interface WordDetailUiState {
    data class Empty(
        val lemma: String? = null,
        val message: String = "No entries available.",
        val isError: Boolean = false,
        val isLoading: Boolean = false,
        val isRefreshing: Boolean = false
    ) : WordDetailUiState

    data class Content(
        val card: LanguageCard,
        val entries: List<EntryUiState>,
        val wordFamilyExpanded: Boolean = false,
        val cardLoading: Boolean = false,
        val cardError: String? = null,
        val translationLoading: Boolean = false,
        val translationError: String? = null,
        val feedbackDialogVisible: Boolean = false,
        val feedbackComment: String = "",
        val feedbackEmail: String = "",
        val feedbackSubmitting: Boolean = false,
        val feedbackError: String? = null,
        val feedbackIssueUrl: String? = null,
        val isRefreshing: Boolean = false
    ) : WordDetailUiState
}

val WordDetailUiState.isRefreshing: Boolean
    get() = when (this) {
        is WordDetailUiState.Empty -> isRefreshing
        is WordDetailUiState.Content -> isRefreshing
    }

data class EntryUiState(
    val entryId: String,
    val formsExpanded: Boolean = false,
    val selectedFormsViewId: String? = null,
    val senses: List<SenseUiState> = emptyList()
)

data class SenseUiState(
    val senseId: String,
    val expanded: Boolean = true,
    val examplesExpanded: Boolean = true,
    val languageExpanded: Map<Language, Boolean> = emptyMap(),
    val favorite: Boolean,
    val pos: PartOfSpeech? = null,
    val showFavoriteToggle: Boolean = expanded,
    val translationLoading: Boolean = false,
    val translationError: String? = null
)

internal fun LanguageCard.toContentUiState(
    targetSenseId: String? = null,
    wordFamilyExpanded: Boolean = false,
    isSenseFavorite: (String) -> Boolean
): WordDetailUiState.Content {
    // Sort senses by level (A1→C2) then frequency (HIGH→LOW)
    val senseComparator = compareBy<LanguageCardResponseSense>(
        { it.learnerLevel.ordinal },
        { it.frequency.ordinal }
    )

    // Sort entries: by first sense's level/frequency, NAME always last
    val sortedEntries = entries
        .map { entry -> entry.copy(senses = entry.senses.sortedWith(senseComparator)) }
        .sortedWith(
            compareBy<LanguageCardPosEntry> { it.pos == PartOfSpeech.NAME }
                .thenBy { it.senses.firstOrNull()?.learnerLevel?.ordinal ?: Int.MAX_VALUE }
                .thenBy { it.senses.firstOrNull()?.frequency?.ordinal ?: Int.MAX_VALUE }
        )

    return WordDetailUiState.Content(
        card = this.copy(entries = sortedEntries),
        wordFamilyExpanded = wordFamilyExpanded,
        entries = sortedEntries.mapIndexed { index, entry ->
            entry.toEntryUiState(
                index,
                targetSenseId,
                isSenseFavorite,
                sortedEntries.size
            )
        }
    )
}

private fun LanguageCardPosEntry.toEntryUiState(
    index: Int,
    targetSenseId: String? = null,
    isSenseFavorite: (String) -> Boolean,
    numPos: Int
): EntryUiState = EntryUiState(
    entryId = "${pos.name.lowercase()}_$index",
    formsExpanded = false,
    selectedFormsViewId = formsViews.firstOrNull()?.view?.viewId,
    senses = senses.map {
        val expanded = if (targetSenseId != null) {
            it.senseId == targetSenseId
        } else {
            senses.size < 2 && numPos == 1
        }
        it.toSenseUiState(expanded, isSenseFavorite(it.senseId), pos)
    }
)

private fun LanguageCardResponseSense.toSenseUiState(
    expanded: Boolean,
    favorite: Boolean,
    pos: PartOfSpeech? = null
): SenseUiState {
    val languages = collectLanguages()
    val languageStates = languages.associateWith { false }
    val examplesExpanded = examples.isNotEmpty()
    return SenseUiState(
        senseId = senseId,
        expanded = expanded,
        examplesExpanded = examplesExpanded,
        languageExpanded = languageStates,
        favorite = favorite,
        pos = pos,
        showFavoriteToggle = true
    )
}

private fun WordDetailUiState.Content.toggleForms(entryId: String): WordDetailUiState.Content =
    updateEntry(entryId) { entry -> entry.copy(formsExpanded = !entry.formsExpanded) }

private fun WordDetailUiState.Content.selectFormsView(
    entryId: String,
    viewId: String
): WordDetailUiState.Content =
    updateEntry(entryId) { entry -> entry.copy(selectedFormsViewId = viewId) }

private fun WordDetailUiState.Content.toggleSense(entryId: String, senseId: String): WordDetailUiState.Content =
    updateEntry(entryId) { entry ->
        entry.updateSense(senseId) { sense -> sense.copy(expanded = !sense.expanded) }
    }

internal fun resolveSelectedFormsViewId(
    preferredViewId: String?,
    availableViewIds: List<String>
): String? = preferredViewId?.takeIf { it in availableViewIds } ?: availableViewIds.firstOrNull()

private fun WordDetailUiState.Content.mergeStateFrom(
    previous: WordDetailUiState.Content
): WordDetailUiState.Content {
    val availableFormsViewsByEntryId = card.entries.mapIndexed { index, entry ->
        "${entry.pos.name.lowercase()}_$index" to entry.formsViews.map { it.view.viewId }
    }.toMap()

    var merged = copy(wordFamilyExpanded = previous.wordFamilyExpanded)
    previous.entries.forEach { previousEntry ->
        merged = merged.updateEntry(previousEntry.entryId) { entry ->
            val availableViews = availableFormsViewsByEntryId[entry.entryId].orEmpty()
            var updatedEntry = entry.copy(
                formsExpanded = previousEntry.formsExpanded,
                selectedFormsViewId = resolveSelectedFormsViewId(
                    previousEntry.selectedFormsViewId,
                    availableViews
                )
            )
            previousEntry.senses.forEach { previousSense ->
                updatedEntry = updatedEntry.updateSense(previousSense.senseId) { sense ->
                    sense.copy(
                        expanded = previousSense.expanded,
                        examplesExpanded = previousSense.examplesExpanded,
                        languageExpanded = sense.languageExpanded.mapValues { (language, value) ->
                            previousSense.languageExpanded[language] ?: value
                        }
                    )
                }
            }
            updatedEntry
        }
    }
    return merged
}

private inline fun WordDetailUiState.Content.updateEntry(
    entryId: String,
    transform: (EntryUiState) -> EntryUiState
): WordDetailUiState.Content {
    val updated = entries.map { entry ->
        if (entry.entryId == entryId) transform(entry) else entry
    }
    return copy(entries = updated)
}

private inline fun EntryUiState.updateSense(
    senseId: String,
    transform: (SenseUiState) -> SenseUiState
): EntryUiState {
    var updated = false
    val updatedSenses = senses.map { sense ->
        if (sense.senseId == senseId) {
            updated = true
            transform(sense)
        } else {
            sense
        }
    }
    return if (updated) copy(senses = updatedSenses) else this
}

class WordDetailViewModel(
    private val repository: DictionaryRepository,
    private val dictionaryClient: DictionaryClient,
    private val wordFetchManager: WordFetchManager,
    private val favoritesRepository: FavoritesRepository,
    private val ttsManager: TextToSpeechManager,
    private val voiceFilterHelper: VoiceFilterHelper,
    val dictionaryLanguage: Language,
    private val lemma: String = "",
    val targetSenseId: String? = null,
    private val translationLanguages: List<Language>? = null,
    private val onFavoriteAdded: (() -> Unit)? = null
) : ViewModel() {
    var state by mutableStateOf<WordDetailUiState>(
        WordDetailUiState.Empty(
            lemma = lemma,
            message = "Loading...",
            isLoading = true
        )
    )
        private set

    val scrollState = ScrollState(0)
    var sensePositions by mutableStateOf<Map<String, Float>>(emptyMap())
        private set

    var favoriteSenses by mutableStateOf<Set<String>>(emptySet())
        private set

    var favoriteLemmas by mutableStateOf<Set<String>>(emptySet())
        private set

    var isPlaying by mutableStateOf(false)
        private set

    var isPreparing by mutableStateOf(false)
        private set

    var availableVoices by mutableStateOf<List<Text2SpeechVoice>>(emptyList())
        private set

    var showVoiceSetupSheet by mutableStateOf(false)
        private set

    val snackbarHostState = SnackbarHostState()

    var pendingIssueUrl by mutableStateOf<String?>(null)
        private set

    fun consumePendingIssueUrl(): String? {
        val url = pendingIssueUrl
        pendingIssueUrl = null
        return url
    }

    private var currentVoiceIndex: Int = 0
    private var hasScrolledToTarget = false
    private var requestedTranslationLanguages: List<Language> =
        translationLanguages?.distinctBy { it.code } ?: emptyList()

    init {
        viewModelScope.launch {
            val resolvedTranslations = translationLanguages?.distinctBy { it.code }
                ?: repository.defaultTranslationTargets(dictionaryLanguage).distinctBy { it.code }
            requestedTranslationLanguages = resolvedTranslations
            wordFetchManager.getWord(
                dictionaryLanguage,
                lemma,
                resolvedTranslations,
                pushToRepo = true
            ).onCompletion { cause ->
                if (cause is CancellationException) {
                    // Flow was cancelled - mark as error so hasErrorOrLoading() triggers recreation
                    markCancelled()
                }
            }.collect { result ->
                updateStateFromResult(result)
            }
        }

    }

    fun attachTtsListener() {
        ttsManager.addOnStatusChangeListener(this) { status ->
            when (status) {
                TTSStatus.SPEAKING -> {
                    isPreparing = false
                    isPlaying = true
                }

                TTSStatus.IDLE -> {
                    isPreparing = false
                    isPlaying = false
                }
            }
        }
    }

    fun detachTtsListener() {
        ttsManager.removeOnStatusChangeListener(this)
        isPlaying = false
        isPreparing = false
    }

    private fun updateStateFromResult(result: WordResult) {
        val card = result.card
        if (card != null) {
            val current = state as? WordDetailUiState.Content
            val wordFamilyExpanded = current?.wordFamilyExpanded ?: false

            // Determine where error occurred based on what was loading before
            val wasWordLoading = current?.cardLoading == true
            val wasTranslationLoading = current?.translationLoading == true
            val errorMessage = result.error?.message

            val nextState = card.toContentUiState(
                targetSenseId = targetSenseId,
                wordFamilyExpanded = wordFamilyExpanded,
                isSenseFavorite = ::isSenseFavorite
            ).let { baseState ->
                if (current != null) baseState.mergeStateFrom(current) else baseState
            }

            state = nextState.copy(
                cardLoading = result.isWordLoading,
                cardError = if (wasWordLoading && errorMessage != null) errorMessage else null,
                translationLoading = result.isTranslationLoading,
                translationError = if (wasTranslationLoading && errorMessage != null) errorMessage else null,
                feedbackDialogVisible = current?.feedbackDialogVisible ?: false,
                feedbackComment = current?.feedbackComment ?: "",
                feedbackEmail = current?.feedbackEmail ?: "",
                feedbackSubmitting = current?.feedbackSubmitting ?: false,
                feedbackError = current?.feedbackError,
                feedbackIssueUrl = current?.feedbackIssueUrl
            )
        } else if (result.error != null) {
            state = WordDetailUiState.Empty(
                lemma = lemma,
                message = result.error.message ?: "Failed to load word",
                isError = true
            )
        }
    }

    fun reload() {
        loadFavorites()
        loadVoices()
    }

    fun refreshFromPull() {
        if (state.isRefreshing) return
        state = when (val s = state) {
            is WordDetailUiState.Empty -> s.copy(isRefreshing = true)
            is WordDetailUiState.Content -> s.copy(isRefreshing = true)
        }
        if (!isLoading()) {
            viewModelScope.launch {
                wordFetchManager.getWord(
                    dictionaryLanguage,
                    lemma,
                    requestedTranslationLanguages,
                    pushToRepo = true
                ).collect { result ->
                    updateStateFromResult(result)
                }
            }
        }
        viewModelScope.launch {
            delay(200.milliseconds)
            state = when (val s = state) {
                is WordDetailUiState.Empty -> s.copy(isRefreshing = false)
                is WordDetailUiState.Content -> s.copy(isRefreshing = false)
            }
        }
    }

    fun hasError(): Boolean {
        return when (val s = state) {
            is WordDetailUiState.Empty -> s.isError
            is WordDetailUiState.Content ->
                s.cardError != null || s.translationError != null
        }
    }

    fun isLoading(): Boolean {
        return when (val s = state) {
            is WordDetailUiState.Empty -> s.isLoading
            is WordDetailUiState.Content ->
                s.cardLoading || s.translationLoading
        }
    }

    private fun markCancelled() {
        state = when (val s = state) {
            is WordDetailUiState.Empty -> s.copy(isError = true)
            is WordDetailUiState.Content -> s.copy(
                cardLoading = false,
                translationLoading = false,
                cardError = s.cardError ?: if (s.cardLoading) "Cancelled" else null,
                translationError = s.translationError ?: if (s.translationLoading) "Cancelled" else null
            )
        }
    }

    private fun loadFavorites() {
        viewModelScope.launch {
            val resolvedTranslations = requestedTranslationLanguages.ifEmpty {
                repository.defaultTranslationTargets(dictionaryLanguage).distinctBy { it.code }
                    .also { requestedTranslationLanguages = it }
            }
            val card = repository.getLanguageCard(
                dictionaryLanguage,
                lemma,
                resolvedTranslations
            )
            val allSenseIds = card?.entries?.flatMap { entry ->
                entry.senses.map { it.senseId }
            } ?: emptyList()

            val favoriteSenseIds = dictionaryLanguage.let { lang ->
                allSenseIds.filter { senseId ->
                    favoritesRepository.exists(senseId, lang)
                }.toSet()
            }

            favoriteSenses = favoriteSenseIds
            favoriteLemmas = favoritesRepository.getDistinctLemmasByLang(dictionaryLanguage)
            val current = state
            if (current is WordDetailUiState.Content) {
                state = current.reloadFavorite(::isSenseFavorite)
            }
        }
    }

    private fun loadVoices() {
        viewModelScope.launch {
            try {
                val languages = ttsManager.getAvailableLanguages()
                val targetLanguage = languages.firstOrNull { it.language == dictionaryLanguage }
                if (targetLanguage != null) {
                    val allVoices = ttsManager.getVoicesForLanguage(targetLanguage)

                    // Initialize default voices if needed
                    if (!voiceFilterHelper.hasEnabledVoices(targetLanguage)) {
                        voiceFilterHelper.initializeDefaultVoices(targetLanguage, allVoices)
                    }

                    // Filter to enabled voices only
                    availableVoices = voiceFilterHelper.filterVoicesByEnabled(allVoices, targetLanguage)
                    // Start from a random voice index
                    if (availableVoices.isNotEmpty()) {
                        currentVoiceIndex = availableVoices.indices.random()
                    }

                }
            } catch (_: Exception) {
                // Failed to load voices, button will be disabled
                availableVoices = emptyList()
            }
        }
    }

    fun dismissVoiceSetup() {
        viewModelScope.launch { voiceFilterHelper.markVoiceSetupShown(dictionaryLanguage) }
        showVoiceSetupSheet = false
    }

    fun dismissVoiceSetupAndPlay() {
        dismissVoiceSetup()
        doPlayWord()
    }

    fun openVoiceSettings() {
        viewModelScope.launch { voiceFilterHelper.markVoiceSetupShown(dictionaryLanguage) }
        showVoiceSetupSheet = false
        ttsManager.openSettings()
    }

    fun isSenseFavorite(senseId: String): Boolean {
        return senseId in favoriteSenses
    }

    fun toggleFavorite(senseId: String) {
        viewModelScope.launch {
            dictionaryLanguage.let { lang ->
                if (senseId in favoriteSenses) {
                    favoritesRepository.remove(senseId, lang)
                    Analytics.logEvent(AnalyticsEvent.WORD_DETAILS_FAVOURITES_REMOVE)


                } else {
                    favoritesRepository.add(senseId, lang, lemma)
                    Analytics.logEvent(AnalyticsEvent.WORD_DETAILS_FAVOURITES_SAVE)
                    onFavoriteAdded?.invoke()
                }
                loadFavorites()
            }
        }
    }

    suspend fun setScrollPosition(position: Int) {
        // This will be called when restoring from saved state
        scrollState.scrollTo(position)
    }

    fun updateSensePosition(senseId: String, yOffset: Float) {
        sensePositions = sensePositions + (senseId to yOffset)
    }

    suspend fun scrollToTargetSenseIfNeeded() {
        if (hasScrolledToTarget) return
        val target = targetSenseId ?: return
        val position = sensePositions[target] ?: return
        scrollState.animateScrollTo(position.toInt())
        hasScrolledToTarget = true
    }

    fun toggleForms(entryId: String) {
        val current = state
        if (current is WordDetailUiState.Content) {
            state = current.toggleForms(entryId)
        }
    }

    fun selectFormsView(entryId: String, viewId: String) {
        val current = state
        if (current is WordDetailUiState.Content) {
            state = current.selectFormsView(entryId, viewId)
        }
    }

    fun toggleSense(entryId: String, senseId: String) {
        val current = state
        if (current is WordDetailUiState.Content) {
            state = current.toggleSense(entryId, senseId)
        }
    }

    fun openFeedbackDialog() {
        val current = state as? WordDetailUiState.Content ?: return
        state = current.copy(
            feedbackDialogVisible = true,
            feedbackComment = "",
            feedbackEmail = "",
            feedbackSubmitting = false,
            feedbackError = null,
            feedbackIssueUrl = null
        )
    }

    fun dismissFeedbackDialog() {
        val current = state as? WordDetailUiState.Content ?: return
        if (current.feedbackSubmitting) return
        val issueUrl = current.feedbackIssueUrl
        state = current.copy(
            feedbackDialogVisible = false,
            feedbackComment = "",
            feedbackEmail = "",
            feedbackError = null,
            feedbackIssueUrl = null
        )
        if (issueUrl != null) {
            viewModelScope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = "Feedback sent",
                    actionLabel = "View",
                    duration = SnackbarDuration.Long
                )
                if (result == SnackbarResult.ActionPerformed) {
                    pendingIssueUrl = issueUrl
                }
            }
        }
    }

    fun updateFeedbackComment(comment: String) {
        val current = state as? WordDetailUiState.Content ?: return
        state = current.copy(feedbackComment = comment, feedbackError = null)
    }

    fun updateFeedbackEmail(email: String) {
        val current = state as? WordDetailUiState.Content ?: return
        state = current.copy(feedbackEmail = email)
    }

    fun submitFeedback() {
        val current = state as? WordDetailUiState.Content ?: return
        if (current.feedbackSubmitting) return

        val comment = current.feedbackComment.trim()
        if (comment.isBlank()) {
            state = current.copy(feedbackError = "Comment is required")
            return
        }

        state = current.copy(feedbackSubmitting = true, feedbackError = null)
        viewModelScope.launch {
            try {
                val feedbackResponse = dictionaryClient.sendFeedback(
                    language = dictionaryLanguage,
                    lemma = lemma,
                    translationTargets = requestedTranslationLanguages,
                    comment = comment,
                    email = current.feedbackEmail.trim().takeIf { it.isNotBlank() }
                )
                val latest = state as? WordDetailUiState.Content
                if (latest != null) {
                    state = latest.copy(
                        feedbackSubmitting = false,
                        feedbackError = null,
                        feedbackIssueUrl = feedbackResponse.issueUrl
                    )
                }
            } catch (e: Exception) {
                val latest = state as? WordDetailUiState.Content ?: return@launch
                state = latest.copy(
                    feedbackSubmitting = false,
                    feedbackError = NetworkErrorClassifier.userMessage(e)
                )
            }
        }
    }

    fun playWord() {
        Analytics.logEvent(AnalyticsEvent.WORD_PLAY_CLICK)
        if (availableVoices.isEmpty()) return

        val hasHighQualityVoice = availableVoices.any { it.quality != VoiceQuality.MEDIUM }
        if (!hasHighQualityVoice) {
            viewModelScope.launch {
                if (!voiceFilterHelper.isVoiceSetupShown(dictionaryLanguage)) {
                    showVoiceSetupSheet = true
                    return@launch
                }
                doPlayWord()
            }
            return
        }

        doPlayWord()
    }

    private fun doPlayWord() {
        try {
            // Rotate to the next voice
            currentVoiceIndex = (currentVoiceIndex + 1) % availableVoices.size
            val selectedVoice = availableVoices[currentVoiceIndex]
            isPreparing = true
            ttsManager.setVoice(selectedVoice)
            ttsManager.speak(lemma)
        } catch (_: Exception) {
            // Failed to play, ignore
            isPreparing = false
        }
    }

    fun stopPlayback() {
        Analytics.logEvent(AnalyticsEvent.WORD_STOP_PLAY_CLICK)
        ttsManager.stop()
    }

    override fun onCleared() {
        super.onCleared()
        ttsManager.removeOnStatusChangeListener(this)
        ttsManager.stop()
    }

    fun dispose() {
        ttsManager.removeOnStatusChangeListener(this)
        ttsManager.stop()
        viewModelScope.cancel()
    }
}

private fun WordDetailUiState.Content.reloadFavorite(isSenseFavorite: (String) -> Boolean): WordDetailUiState {
    return copy(
        entries = this.entries.map { entry ->
            entry.copy(
                senses = entry.senses.map { sense ->
                    sense.copy(favorite = isSenseFavorite(sense.senseId))
                }
            )
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordDetailScreen(
    viewModel: WordDetailViewModel,
    onBack: () -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    onNavigateToFavorites: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToWordDetail: (Language, String) -> Unit = { _, _ -> }
) {
    DisposableEffect(viewModel) {
        viewModel.attachTtsListener()
        onDispose { viewModel.detachTtsListener() }
    }

    // Restore scroll position after process death
    val savedScrollPosition = rememberSaveable { viewModel.scrollState.value }

    LaunchedEffect(savedScrollPosition) {
        if (viewModel.scrollState.value == 0 && savedScrollPosition > 0) {
            viewModel.setScrollPosition(savedScrollPosition)
        }
    }

    // Scroll to target sense once positions are available (only once)
    LaunchedEffect(viewModel.targetSenseId, viewModel.sensePositions) {
        if (viewModel.targetSenseId != null && viewModel.sensePositions.containsKey(viewModel.targetSenseId)) {
            viewModel.scrollToTargetSenseIfNeeded()
        }
    }

    val uriHandler = LocalUriHandler.current

    // Handle pending issue URL from snackbar action
    LaunchedEffect(viewModel.pendingIssueUrl) {
        viewModel.consumePendingIssueUrl()?.let { url ->
            uriHandler.openUri(url)
        }
    }

    WordDetailScreenContent(
        state = viewModel.state,
        scrollState = viewModel.scrollState,
        snackbarHostState = viewModel.snackbarHostState,
        isPlaying = viewModel.isPlaying,
        isPreparing = viewModel.isPreparing,
        canPlay = viewModel.availableVoices.isNotEmpty(),
        favoriteLemmas = viewModel.favoriteLemmas,
        onRefresh = { viewModel.refreshFromPull() },
        onBack = onBack,
        onNavigateToSearch = onNavigateToSearch,
        onNavigateToFavorites = onNavigateToFavorites,
        onNavigateToSettings = onNavigateToSettings,
        onPlayWord = { viewModel.playWord() },
        onStopWord = { viewModel.stopPlayback() },
        dictionaryLanguage = viewModel.dictionaryLanguage,
        showVoiceSetupSheet = viewModel.showVoiceSetupSheet,
        onOpenVoiceSettings = { viewModel.openVoiceSettings() },
        onDismissVoiceSetup = { viewModel.dismissVoiceSetup() },
        onLaterVoiceSetup = { viewModel.dismissVoiceSetupAndPlay() },
        onOpenFeedback = { viewModel.openFeedbackDialog() },
        onDismissFeedback = { viewModel.dismissFeedbackDialog() },
        onFeedbackCommentChange = { viewModel.updateFeedbackComment(it) },
        onFeedbackEmailChange = { viewModel.updateFeedbackEmail(it) },
        onSubmitFeedback = { viewModel.submitFeedback() },
        onFormsToggle = { entryId -> viewModel.toggleForms(entryId) },
        onFormsViewSelect = { entryId, viewId -> viewModel.selectFormsView(entryId, viewId) },
        onSenseToggle = { entryId, senseId -> viewModel.toggleSense(entryId, senseId) },
        onSensePositioned = { senseId, yOffset -> viewModel.updateSensePosition(senseId, yOffset) },
        isSenseFavorite = { senseId -> viewModel.isSenseFavorite(senseId) },
        onSenseFavoriteToggle = { senseId ->
            viewModel.toggleFavorite(senseId)
        },
        onWordClick = { word ->
            onNavigateToWordDetail(viewModel.dictionaryLanguage, word)
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WordDetailScreenContent(
    state: WordDetailUiState,
    scrollState: ScrollState = ScrollState(0),
    snackbarHostState: SnackbarHostState = SnackbarHostState(),
    isPlaying: Boolean = false,
    isPreparing: Boolean = false,
    canPlay: Boolean = false,
    favoriteLemmas: Set<String> = emptySet(),
    onRefresh: () -> Unit = {},
    onBack: () -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    onNavigateToFavorites: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onPlayWord: () -> Unit = {},
    onStopWord: () -> Unit = {},
    dictionaryLanguage: Language = Language.ENGLISH,
    showVoiceSetupSheet: Boolean = false,
    onOpenVoiceSettings: () -> Unit = {},
    onDismissVoiceSetup: () -> Unit = {},
    onLaterVoiceSetup: () -> Unit = {},
    onOpenFeedback: () -> Unit = {},
    onDismissFeedback: () -> Unit = {},
    onFeedbackCommentChange: (String) -> Unit = {},
    onFeedbackEmailChange: (String) -> Unit = {},
    onSubmitFeedback: () -> Unit = {},
    onFormsToggle: (String) -> Unit = {},
    onFormsViewSelect: (String, String) -> Unit = { _, _ -> },
    onSenseToggle: (String, String) -> Unit = { _, _ -> },
    onSensePositioned: (String, Float) -> Unit = { _, _ -> },
    isSenseFavorite: (String) -> Boolean = { false },
    onSenseFavoriteToggle: (String) -> Unit = {},
    onWordClick: (String) -> Unit = {}
) {
    val fallbackTitle = stringResource(Res.string.word_details_title)
    val titleText = when (state) {
        is WordDetailUiState.Content -> state.card.lemma
        is WordDetailUiState.Empty -> state.lemma ?: fallbackTitle
    }
    val density = LocalDensity.current
    val heroThresholdPx = remember(density) { mutableFloatStateOf(with(density) { 80.dp.toPx() }) }
    val isEmptyState = state is WordDetailUiState.Empty
    val showTitleInBar by remember(isEmptyState, scrollState) {
        derivedStateOf { isEmptyState || scrollState.value > heroThresholdPx.value }
    }
    val titleAlpha by animateFloatAsState(
        targetValue = if (showTitleInBar) 1f else 0f,
        animationSpec = tween(durationMillis = 150),
        label = "titleAlpha"
    )

    Scaffold(
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = titleText,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Medium,
                                    letterSpacing = (-0.3).sp,
                                    fontFamily = MaterialTheme.serifFontFamily
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.graphicsLayer { alpha = titleAlpha }
                            )
                            if (showTitleInBar && state is WordDetailUiState.Content) {
                                Spacer(modifier = Modifier.width(8.dp))
                                IconButton(
                                    onClick = { if (isPlaying) onStopWord() else onPlayWord() },
                                    enabled = canPlay && !isPreparing,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    when {
                                        isPreparing -> CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        else -> Icon(
                                            imageVector = if (isPlaying) Icons.Filled.StopCircle else SpeakerVector,
                                            contentDescription = if (isPlaying) {
                                                stringResource(Res.string.word_details_action_stop)
                                            } else {
                                                stringResource(Res.string.word_details_action_play_word)
                                            },
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                .copy(alpha = if (canPlay) 1f else 0.38f)
                                        )
                                    }
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(Res.string.common_back)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = titleAlpha * 0.6f)
                )
            }
        },
        bottomBar = {
            AppNavigationBar(
                currentScreen = AppScreen.WORD_DETAIL,
                onNavigateToSearch = onNavigateToSearch,
                onNavigateToFavorites = onNavigateToFavorites,
                onNavigateToWordDetail = {},
                onNavigateToSettings = onNavigateToSettings,
                wordDetailLabel = titleText
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        PullToRefreshBox(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            isRefreshing = state.isRefreshing,
            onRefresh = onRefresh
        ) {
            when (state) {
                is WordDetailUiState.Content -> {
                    WordDetailContent(
                        card = state.card,
                        entryStates = state.entries,
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(bottom = 20.dp),
                        cardLoading = state.cardLoading,
                        cardError = state.cardError,
                        translationLoading = state.translationLoading,
                        translationError = state.translationError,
                        isPlaying = isPlaying,
                        isPreparing = isPreparing,
                        canPlay = canPlay,
                        onPlayWord = onPlayWord,
                        onStopWord = onStopWord,
                        onFormsToggle = onFormsToggle,
                        onFormsViewSelect = onFormsViewSelect,
                        onSenseToggle = onSenseToggle,
                        onSensePositioned = onSensePositioned,
                        isSenseFavorite = isSenseFavorite,
                        onSenseFavoriteToggle = onSenseFavoriteToggle,
                        onWordClick = onWordClick,
                        favoriteLemmas = favoriteLemmas,
                        onOpenFeedback = onOpenFeedback,
                        onHeroMeasured = { heroThresholdPx.value = it }
                    )
                }

                is WordDetailUiState.Empty -> {
                    Surface(Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier.fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            if (state.isLoading) {
                                LoadingIndicator()
                            } else if (state.isError) {
                                ErrorIcon(Modifier.size(30.dp))
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }

    if (showVoiceSetupSheet) {
        VoiceSetupBottomSheet(
            language = dictionaryLanguage,
            onOpenSettings = onOpenVoiceSettings,
            onDismiss = onDismissVoiceSetup,
            onLater = onLaterVoiceSetup
        )
    }

    if (state is WordDetailUiState.Content && state.feedbackDialogVisible) {
        com.slovy.slovymovyapp.ui.FeedbackDialog(
            title = stringResource(Res.string.word_details_feedback_title),
            commentPlaceholder = stringResource(Res.string.word_details_feedback_placeholder),
            comment = state.feedbackComment,
            email = state.feedbackEmail,
            isSending = state.feedbackSubmitting,
            error = state.feedbackError,
            resultUrl = state.feedbackIssueUrl,
            onCommentChange = onFeedbackCommentChange,
            onEmailChange = onFeedbackEmailChange,
            onDismiss = onDismissFeedback,
            onSend = onSubmitFeedback
        )
    }
}

@Composable
private fun WordDetailContent(
    card: LanguageCard,
    entryStates: List<EntryUiState>,
    modifier: Modifier = Modifier,
    cardLoading: Boolean = false,
    cardError: String? = null,
    translationLoading: Boolean = false,
    translationError: String? = null,
    isPlaying: Boolean = false,
    isPreparing: Boolean = false,
    canPlay: Boolean = false,
    onPlayWord: () -> Unit = {},
    onStopWord: () -> Unit = {},
    onFormsToggle: (String) -> Unit,
    onFormsViewSelect: (String, String) -> Unit,
    onSenseToggle: (String, String) -> Unit,
    onSensePositioned: (String, Float) -> Unit = { _, _ -> },
    isSenseFavorite: (String) -> Boolean = { false },
    onSenseFavoriteToggle: (String) -> Unit = {},
    onWordClick: (String) -> Unit = {},
    favoriteLemmas: Set<String> = emptySet(),
    onOpenFeedback: () -> Unit = {},
    onHeroMeasured: (Float) -> Unit = {}
) {
    var scrollContainerY by remember { mutableStateOf(0f) }

    Column(
        modifier = modifier.onGloballyPositioned { coordinates ->
            scrollContainerY = coordinates.positionInWindow().y
        },
    ) {
        // Hero section — measured so the top bar threshold tracks actual layout height
        Column(
            modifier = Modifier.fillMaxWidth().onSizeChanged { size: IntSize ->
                if (size.height > 0) onHeroMeasured(size.height.toFloat())
            }
        ) {
            val fontScale = LocalDensity.current.fontScale
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val maxWidth = constraints.maxWidth
                var lemmaFontSize by remember(card.lemma, maxWidth, fontScale) { mutableStateOf(42.sp) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = card.lemma,
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontSize = lemmaFontSize,
                            fontWeight = FontWeight.Medium,
                            lineHeight = (lemmaFontSize.value * 1.05f).sp,
                            letterSpacing = (-0.3).sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                        onTextLayout = { result ->
                            if (result.hasVisualOverflow && lemmaFontSize > 22.sp) {
                                lemmaFontSize = (lemmaFontSize.value * 0.9f).sp
                            }
                        }
                    )
                    IconButton(
                        onClick = { if (isPlaying) onStopWord() else onPlayWord() },
                        enabled = canPlay && !isPreparing,
                        modifier = Modifier
                            .padding(bottom = 4.dp)
                            .size(36.dp)
                    ) {
                        when {
                            isPreparing -> CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            else -> Icon(
                                imageVector = if (isPlaying) Icons.Filled.StopCircle else SpeakerVector,
                                contentDescription = if (isPlaying) {
                                    stringResource(Res.string.word_details_action_stop)
                                } else {
                                    stringResource(Res.string.word_details_action_play_word)
                                },
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    .copy(alpha = if (canPlay) 1f else 0.38f)
                            )
                        }
                    }
                }
            } // end BoxWithConstraints

            ChapterRule()
        } // end hero measurement Column

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {

            if (card.entries.isEmpty()) {
                Text(
                    text = stringResource(Res.string.word_details_no_entries),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                card.entries.forEachIndexed { index, entry ->
                    val entryState = entryStates.getOrNull(index) ?: entry.toEntryUiState(
                        index,
                        isSenseFavorite = isSenseFavorite,
                        numPos = card.entries.size
                    )
                    EntryCard(
                        entry = entry,
                        lemma = card.lemma,
                        entryState = entryState,
                        cardLoading = cardLoading,
                        cardError = cardError,
                        translationLoading = translationLoading,
                        translationError = translationError,
                        onFormsToggle = { onFormsToggle(entryState.entryId) },
                        onFormsViewSelect = { viewId -> onFormsViewSelect(entryState.entryId, viewId) },
                        onSenseToggle = { senseId -> onSenseToggle(entryState.entryId, senseId) },
                        onSensePositioned = { senseId, windowY ->
                            // Calculate position relative to scroll container
                            val relativeY = windowY - scrollContainerY
                            onSensePositioned(senseId, relativeY)
                        },
                        onSenseFavoriteToggle = onSenseFavoriteToggle,
                        relatedWords = card.relatedWords,
                        onWordClick = onWordClick,
                        favoriteLemmas = favoriteLemmas
                    )
                }
            }

            if (card.wordFamily.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    EntryList(
                        label = stringResource(Res.string.word_details_word_family),
                        values = card.wordFamily,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        relatedWords = card.relatedWords,
                        onWordClick = onWordClick,
                        favoriteLemmas = favoriteLemmas,
                        chipShape = RoundedCornerShape(50),
                        chipBorder = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        chipSpacing = 8.dp
                    )
                }
            }

            TextButton(
                onClick = onOpenFeedback,
                modifier = Modifier.align(Alignment.CenterHorizontally),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Icon(
                    imageVector = Icons.Outlined.Flag,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(Res.string.word_details_suggest_correction),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } // end inner content Column
    }
}

@Composable
private fun ChapterRule() {
    val color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 40.dp, end = 40.dp, top = 2.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = color.copy(alpha = 0.45f * 0.35f)
        )
        Icon(
            imageVector = ChapterDiamondVector,
            contentDescription = null,
            modifier = Modifier.padding(horizontal = 8.dp),
            tint = color
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = color.copy(alpha = 0.45f * 0.35f)
        )
    }
}
