package com.slovy.slovymovyapp.ui.word

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.outlined.Feedback
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.favorites.FavoritesRepository
import com.slovy.slovymovyapp.data.remote.*
import com.slovy.slovymovyapp.speech.TTSStatus
import com.slovy.slovymovyapp.speech.Text2SpeechVoice
import com.slovy.slovymovyapp.speech.TextToSpeechManager
import com.slovy.slovymovyapp.speech.VoiceFilterHelper
import com.slovy.slovymovyapp.ui.AppNavigationBar
import com.slovy.slovymovyapp.ui.AppScreen
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

sealed interface WordDetailUiState {
    data class Empty(
        val lemma: String? = null,
        val message: String = "No entries available.",
        val isError: Boolean = false,
        val isLoading: Boolean = false
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
        val feedbackSubmitting: Boolean = false,
        val feedbackError: String? = null
    ) : WordDetailUiState
}

data class EntryUiState(
    val entryId: String,
    val expanded: Boolean = true,
    val formsExpanded: Boolean = false,
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
    expanded = true,
    formsExpanded = false,
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

private fun WordDetailUiState.Content.toggleEntry(entryId: String): WordDetailUiState.Content =
    updateEntry(entryId) { entry -> entry.copy(expanded = !entry.expanded) }

private fun WordDetailUiState.Content.toggleForms(entryId: String): WordDetailUiState.Content =
    updateEntry(entryId) { entry -> entry.copy(formsExpanded = !entry.formsExpanded) }

private fun WordDetailUiState.Content.toggleSense(entryId: String, senseId: String): WordDetailUiState.Content =
    updateEntry(entryId) { entry ->
        entry.updateSense(senseId) { sense -> sense.copy(expanded = !sense.expanded) }
    }

private fun WordDetailUiState.Content.mergeStateFrom(
    previous: WordDetailUiState.Content
): WordDetailUiState.Content {
    var merged = copy(wordFamilyExpanded = previous.wordFamilyExpanded)
    previous.entries.forEach { previousEntry ->
        merged = merged.updateEntry(previousEntry.entryId) { entry ->
            var updatedEntry = entry.copy(
                expanded = previousEntry.expanded,
                formsExpanded = previousEntry.formsExpanded
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
    private val translationLanguages: List<Language>? = null
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

    var isPlaying by mutableStateOf(false)
        private set

    var isPreparing by mutableStateOf(false)
        private set

    var availableVoices by mutableStateOf<List<Text2SpeechVoice>>(emptyList())
        private set

    val snackbarHostState = SnackbarHostState()

    private var currentVoiceIndex: Int = 0
    private var hasScrolledToTarget = false
    private var requestedTranslationLanguages: List<Language> =
        translationLanguages?.distinctBy { it.code } ?: emptyList()

    init {
        viewModelScope.launch {
            val resolvedTranslations = translationLanguages?.distinctBy { it.code }
                ?: repository.installedTranslationTargets(dictionaryLanguage).distinctBy { it.code }
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

        // Setup TTS status listener
        ttsManager.setOnStatusChangeListener { status ->
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
                feedbackSubmitting = current?.feedbackSubmitting ?: false,
                feedbackError = current?.feedbackError
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

    fun hasError(): Boolean {
        return when (val s = state) {
            is WordDetailUiState.Empty -> s.isError
            is WordDetailUiState.Content ->
                s.cardError != null || s.translationError != null
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
                repository.installedTranslationTargets(dictionaryLanguage).distinctBy { it.code }
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

    fun isSenseFavorite(senseId: String): Boolean {
        return senseId in favoriteSenses
    }

    fun toggleFavorite(senseId: String) {
        viewModelScope.launch {
            dictionaryLanguage.let { lang ->
                if (senseId in favoriteSenses) {
                    favoritesRepository.remove(senseId, lang)
                } else {
                    favoritesRepository.add(senseId, lang, lemma)
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

    fun toggleEntry(entryId: String) {
        val current = state
        if (current is WordDetailUiState.Content) {
            state = current.toggleEntry(entryId)
        }
    }

    fun toggleForms(entryId: String) {
        val current = state
        if (current is WordDetailUiState.Content) {
            state = current.toggleForms(entryId)
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
            feedbackSubmitting = false,
            feedbackError = null
        )
    }

    fun dismissFeedbackDialog() {
        val current = state as? WordDetailUiState.Content ?: return
        if (current.feedbackSubmitting) return
        state = current.copy(
            feedbackDialogVisible = false,
            feedbackComment = "",
            feedbackError = null
        )
    }

    fun updateFeedbackComment(comment: String) {
        val current = state as? WordDetailUiState.Content ?: return
        state = current.copy(feedbackComment = comment, feedbackError = null)
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
                dictionaryClient.sendFeedback(
                    language = dictionaryLanguage,
                    lemma = lemma,
                    translationTargets = requestedTranslationLanguages,
                    comment = comment
                )
                val latest = state as? WordDetailUiState.Content
                if (latest != null) {
                    state = latest.copy(
                        feedbackDialogVisible = false,
                        feedbackComment = "",
                        feedbackSubmitting = false,
                        feedbackError = null
                    )
                }
                snackbarHostState.showSnackbar("Feedback sent")
            } catch (e: Exception) {
                val latest = state as? WordDetailUiState.Content ?: return@launch
                state = latest.copy(
                    feedbackSubmitting = false,
                    feedbackError = e.message ?: "Failed to send feedback"
                )
            }
        }
    }

    fun playWord() {
        if (availableVoices.isEmpty()) return

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
        ttsManager.stop()
    }

    override fun onCleared() {
        super.onCleared()
        ttsManager.stop()
    }

    fun dispose() {
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

    WordDetailScreenContent(
        state = viewModel.state,
        scrollState = viewModel.scrollState,
        snackbarHostState = viewModel.snackbarHostState,
        isPlaying = viewModel.isPlaying,
        isPreparing = viewModel.isPreparing,
        canPlay = viewModel.availableVoices.isNotEmpty(),
        onBack = onBack,
        onNavigateToSearch = onNavigateToSearch,
        onNavigateToFavorites = onNavigateToFavorites,
        onNavigateToSettings = onNavigateToSettings,
        onPlayWord = { viewModel.playWord() },
        onStopWord = { viewModel.stopPlayback() },
        onOpenFeedback = { viewModel.openFeedbackDialog() },
        onDismissFeedback = { viewModel.dismissFeedbackDialog() },
        onFeedbackCommentChange = { viewModel.updateFeedbackComment(it) },
        onSubmitFeedback = { viewModel.submitFeedback() },
        onEntryToggle = { entryId -> viewModel.toggleEntry(entryId) },
        onFormsToggle = { entryId -> viewModel.toggleForms(entryId) },
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
    onBack: () -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    onNavigateToFavorites: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onPlayWord: () -> Unit = {},
    onStopWord: () -> Unit = {},
    onOpenFeedback: () -> Unit = {},
    onDismissFeedback: () -> Unit = {},
    onFeedbackCommentChange: (String) -> Unit = {},
    onSubmitFeedback: () -> Unit = {},
    onEntryToggle: (String) -> Unit = {},
    onFormsToggle: (String) -> Unit = {},
    onSenseToggle: (String, String) -> Unit = { _, _ -> },
    onSensePositioned: (String, Float) -> Unit = { _, _ -> },
    isSenseFavorite: (String) -> Boolean = { false },
    onSenseFavoriteToggle: (String) -> Unit = {},
    onWordClick: (String) -> Unit = {}
) {
    val fallbackTitle = "Word Details"
    val titleText = when (state) {
        is WordDetailUiState.Content -> state.card.lemma
        is WordDetailUiState.Empty -> state.lemma ?: fallbackTitle
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        when (state) {
                            is WordDetailUiState.Content ->
                                Text(
                                    text = state.card.lemma,
                                    style = MaterialTheme.typography.headlineSmall.copy(
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )

                            is WordDetailUiState.Empty -> HighlightedText(
                                text = titleText,
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                textAlign = TextAlign.Center
                            )
                        }

                        if (state is WordDetailUiState.Content) {
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = { if (isPlaying) onStopWord() else onPlayWord() },
                                enabled = canPlay && !isPreparing,
                                modifier = Modifier.size(36.dp)
                            ) {
                                when {
                                    isPreparing -> CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp
                                    )

                                    else -> Icon(
                                        imageVector = if (isPlaying) Icons.Filled.StopCircle else Icons.AutoMirrored.Filled.VolumeUp,
                                        contentDescription = if (isPlaying) "Stop" else "Play word",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            IconButton(
                                onClick = onOpenFeedback,
                                enabled = !state.feedbackSubmitting,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Feedback,
                                    contentDescription = "Send feedback",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
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
        when (state) {
            is WordDetailUiState.Content -> {
                WordDetailContent(
                    card = state.card,
                    entryStates = state.entries,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(innerPadding)
                        .padding(horizontal = 12.dp, vertical = 20.dp),
                    cardLoading = state.cardLoading,
                    cardError = state.cardError,
                    translationLoading = state.translationLoading,
                    translationError = state.translationError,
                    onEntryToggle = onEntryToggle,
                    onFormsToggle = onFormsToggle,
                    onSenseToggle = onSenseToggle,
                    onSensePositioned = onSensePositioned,
                    isSenseFavorite = isSenseFavorite,
                    onSenseFavoriteToggle = onSenseFavoriteToggle,
                    onWordClick = onWordClick
                )
            }

            is WordDetailUiState.Empty -> {
                Surface(Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
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

    if (state is WordDetailUiState.Content && state.feedbackDialogVisible) {
        FeedbackDialog(
            comment = state.feedbackComment,
            isSending = state.feedbackSubmitting,
            error = state.feedbackError,
            onCommentChange = onFeedbackCommentChange,
            onDismiss = onDismissFeedback,
            onSend = onSubmitFeedback
        )
    }
}

@Composable
private fun FeedbackDialog(
    comment: String,
    isSending: Boolean,
    error: String?,
    onCommentChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSend: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {
            if (!isSending) onDismiss()
        },
        title = {
            Text("Send feedback")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = comment,
                    onValueChange = onCommentChange,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 6,
                    singleLine = false,
                    enabled = !isSending,
                    label = { Text("Comment") },
                    isError = error != null
                )
                if (error != null) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onSend,
                enabled = !isSending && comment.isNotBlank()
            ) {
                if (isSending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Send")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isSending
            ) {
                Text("Cancel")
            }
        }
    )
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
    onEntryToggle: (String) -> Unit,
    onFormsToggle: (String) -> Unit,
    onSenseToggle: (String, String) -> Unit,
    onSensePositioned: (String, Float) -> Unit = { _, _ -> },
    isSenseFavorite: (String) -> Boolean = { false },
    onSenseFavoriteToggle: (String) -> Unit = {},
    onWordClick: (String) -> Unit = {}
) {
    var scrollContainerY by remember { mutableStateOf(0f) }

    Column(
        modifier = modifier.onGloballyPositioned { coordinates ->
            scrollContainerY = coordinates.positionInWindow().y
        },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Display word family if available
        if (card.wordFamily.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                EntryList(
                    label = "Word Family",
                    values = card.wordFamily,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    relatedWords = card.relatedWords,
                    onWordClick = onWordClick
                )
            }
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
        }

        if (card.entries.isEmpty()) {
            Text(
                text = "No entries available.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            card.entries.forEachIndexed { index, entry ->
                // Add divider between POS sections (not before the first one)
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }
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
                    onEntryToggle = { onEntryToggle(entryState.entryId) },
                    onFormsToggle = { onFormsToggle(entryState.entryId) },
                    onSenseToggle = { senseId -> onSenseToggle(entryState.entryId, senseId) },
                    onSensePositioned = { senseId, windowY ->
                        // Calculate position relative to scroll container
                        val relativeY = windowY - scrollContainerY
                        onSensePositioned(senseId, relativeY)
                    },
                    onSenseFavoriteToggle = onSenseFavoriteToggle,
                    relatedWords = card.relatedWords,
                    onWordClick = onWordClick
                )
            }
        }
    }
}
