package com.slovy.slovymovyapp.ui.search


import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.slovy.slovymovyapp.data.lists.ListsService
import com.slovy.slovymovyapp.data.lists.WordList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slovy.slovymovyapp.analytics.Analytics
import com.slovy.slovymovyapp.analytics.AnalyticsEvent
import com.slovy.slovymovyapp.analytics.PerformanceMonitoring
import com.slovy.slovymovyapp.analytics.putAttributes
import com.slovy.slovymovyapp.analytics.useWithResult
import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.remote.DictionaryClient
import com.slovy.slovymovyapp.data.remote.DictionaryRepository
import com.slovy.slovymovyapp.data.settings.Setting
import com.slovy.slovymovyapp.data.settings.SettingsRepository
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import com.slovy.slovymovyapp.i18n.UiText
import com.slovy.slovymovyapp.i18n.networkErrorUiText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import slovymovyapp.composeapp.generated.resources.*
import kotlin.uuid.Uuid
import com.slovy.slovymovyapp.ui.FeedbackFormState

data class SearchUiState(
    val query: String,
    val results: List<DictionaryRepository.SearchItem>,
    val showNoResults: Boolean,
    val showLanguageIndicators: Boolean = false,
    val scrollState: LazyListState = LazyListState(),
    val availableLanguages: List<Language> = emptyList(),
    val selectedLanguage: Language? = null,
    val isLanguageDropdownExpanded: Boolean = false,
    val isSuggestionsRefreshing: Boolean = false,
    val wordSuggestions: List<String> = emptyList(),
    val favoriteLemmas: List<String> = emptyList(),
    val curatedLists: List<WordList> = emptyList(),
    // Gates the empty-state body so it renders once fully populated instead of flashing
    // the favorites/"start typing" fallback before curated lists finish loading.
    val isEmptyStateLoading: Boolean = false,
    val listSuggestion: FeedbackFormState = FeedbackFormState()
)

class SearchViewModel(
    private val repository: DictionaryRepository,
    private val settingsRepository: SettingsRepository,
    private val listsService: ListsService,
    private val dictionaryClient: DictionaryClient,
) : ViewModel() {

    data class Search(
        val query: String,
        val language: Language? = null,
        val force: Uuid,
        val resetFocus: Boolean = false
    )

    var state by mutableStateOf(
        run {
            val installed = repository.installedDictionaries()
            SearchUiState(
                query = "",
                results = emptyList(),
                showNoResults = false,
                availableLanguages = installed,
                selectedLanguage = installed.firstOrNull(),
                isEmptyStateLoading = installed.isNotEmpty()
            )
        }
    )
        private set

    private val queryFlow = MutableStateFlow(Search("", state.selectedLanguage, Uuid.random()))
    val noDictionaryScrollState = ScrollState(0)
    private var suggestionsInitialized = false
    private var savedSearchLanguage: Language? = null
    private var listsJob: Job? = null

    private val suggestionsLoadedForLanguage = MutableStateFlow<Language?>(null)

    init {
        viewModelScope.launch {
            @OptIn(kotlinx.coroutines.FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
            queryFlow
                .debounce(200.milliseconds) // Wait ms after last keystroke
                .flatMapLatest { queryState ->
                    flow {
                        val query = queryState.query
                        val results = if (query.isEmpty()) {
                            emptyList()
                        } else {
                            PerformanceMonitoring.startTrace("word_search").useWithResult {
                                putAttributes(
                                    mapOf(
                                        "lang" to (queryState.language?.code ?: "any"),
                                    ),
                                )
                                putMetric("query_length", query.length.toLong())
                                repository.search(query, queryState.language).also { searchResults ->
                                    putMetric("result_count", searchResults.size.toLong())
                                    putAttribute("has_results", searchResults.isNotEmpty().toString())
                                }
                            }
                        }
                        emit(results)
                    }.flowOn(Dispatchers.Default) // Run search on background thread
                }
                .collect { results ->
                    val query = queryFlow.value.query
                    if (query.isNotEmpty()) {
                        Analytics.logEvent(
                            AnalyticsEvent.WORD_SEARCH_QUERY,
                            mapOf(
                                "lang" to (queryFlow.value.language?.code ?: ""),
                                "query_length" to query.length.toLong(),
                                "result_count" to results.size.toLong(),
                                "has_results" to (results.isNotEmpty()).toString(),
                            ),
                        )
                    }
                    state = state.copy(
                        results = results,
                        showNoResults = results.isEmpty() && query.isNotEmpty()
                    )
                    // Reset scroll to top when results change
                    if ((results.isNotEmpty() || query.isEmpty()) && queryFlow.value.resetFocus) {
                        state.scrollState.scrollToItem(0)
                    }
                }
        }
        viewModelScope.launch {
            // Restore saved language before loading suggestions so they load for the right language.
            val savedCode = settingsRepository.getById(Setting.Name.SEARCH_LANGUAGE)
                ?.value?.jsonPrimitive?.content
            val savedLang = savedCode?.let { Language.fromCodeOrNull(it) }
            savedSearchLanguage = savedLang
            if (savedLang != null && savedLang in state.availableLanguages && savedLang != state.selectedLanguage) {
                // This bypasses setSelectedLanguage (the preference must not be re-saved),
                // so drop anything the screen-open refresh loaded for the pre-restore
                // language and reload lists for the restored one.
                state = state.copy(
                    selectedLanguage = savedLang,
                    curatedLists = emptyList(),
                    isEmptyStateLoading = true
                )
                queryFlow.value = queryFlow.value.copy(language = savedLang)
                refreshLists()
            }
            loadSuggestionsForCurrentLanguage()
            suggestionsInitialized = true
        }
    }

    private suspend fun loadSuggestionsForCurrentLanguage() {
        val language = state.selectedLanguage ?: return
        try {
            val (suggestions, favorites) = repository.getSearchEmptyStateData(language)
            state = state.copy(wordSuggestions = suggestions, favoriteLemmas = favorites)
        } finally {
            // Mark loaded even on empty/failed results so lists still render afterwards.
            suggestionsLoadedForLanguage.value = language
        }
    }

    fun refreshRecentFavorites() {
        if (!suggestionsInitialized) return
        viewModelScope.launch {
            val language = state.selectedLanguage ?: return@launch
            val favorites = repository.getRecentFavoriteLemmas(language)
            state = state.copy(favoriteLemmas = favorites)
        }
    }

    fun refreshSuggestionsFromPull() {
        // Skip until initial data is loaded and avoid concurrent refresh jobs.
        if (!suggestionsInitialized || state.isSuggestionsRefreshing) return
        viewModelScope.launch {
            state = state.copy(isSuggestionsRefreshing = true)
            try {
                loadSuggestionsForCurrentLanguage()
            } finally {
                state = state.copy(isSuggestionsRefreshing = false)
            }
        }
    }

    fun refreshLists() {
        listsJob?.cancel()
        listsJob = viewModelScope.launch {
            val language = state.selectedLanguage ?: return@launch
            // Serve whatever the DB already holds, then sync with the server and
            // re-read only when new content was actually stored.
            val cached = listsService.getLists(language)
            // Render suggestions for this language first, then lists below them.
            suggestionsLoadedForLanguage.first { it == language }
            if (state.selectedLanguage == language) {
                // Lift the loading gate as soon as we have cached lists so the empty
                // state renders once, fully populated. With an empty cache (first launch)
                // keep waiting through the server sync so the favorites/"start typing"
                // fallback does not flash before lists arrive.
                state = if (cached.isNotEmpty()) {
                    state.copy(curatedLists = cached, isEmptyStateLoading = false)
                } else {
                    state.copy(curatedLists = cached)
                }
            }
            if (listsService.sync(language)) {
                val updated = listsService.getLists(language)
                if (state.selectedLanguage == language) {
                    state = state.copy(curatedLists = updated)
                }
            }
            if (state.selectedLanguage == language) {
                state = state.copy(isEmptyStateLoading = false)
            }
        }
    }

    fun openListSuggestionDialog() {
        state = state.copy(listSuggestion = state.listSuggestion.opened())
    }

    fun dismissListSuggestionDialog() {
        if (state.listSuggestion.submitting) return
        state = state.copy(listSuggestion = state.listSuggestion.dismissed())
    }

    fun updateListSuggestionComment(comment: String) {
        state = state.copy(listSuggestion = state.listSuggestion.withComment(comment))
    }

    fun updateListSuggestionEmail(email: String) {
        state = state.copy(listSuggestion = state.listSuggestion.withEmail(email))
    }

    fun submitListSuggestion() {
        val form = state.listSuggestion
        if (form.submitting) return
        val language = state.selectedLanguage ?: return

        val comment = form.trimmedComment
        if (comment.isBlank()) return

        state = state.copy(listSuggestion = form.submissionStarted())
        viewModelScope.launch {
            try {
                val response = dictionaryClient.sendListSuggestion(
                    language = language,
                    comment = comment,
                    email = form.trimmedEmail
                )
                state = state.copy(
                    listSuggestion = state.listSuggestion.submissionSucceeded(response.issueUrl)
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state = state.copy(
                    listSuggestion = state.listSuggestion.submissionFailed(
                        networkErrorUiText(e)
                    )
                )
            }
        }
    }

    fun updateQuery(newQuery: String) {
        val normalized = newQuery.trim()
        // Update UI state immediately for responsive typing
        state = if (normalized.isEmpty()) {
            state.copy(query = newQuery, results = emptyList(), showNoResults = false)
        } else {
            state.copy(query = newQuery)
        }
        // Trigger debounced search
        queryFlow.value =
            queryFlow.value.copy(
                query = normalized,
                language = state.selectedLanguage,
                force = Uuid.random(),
                resetFocus = true
            )
    }

    fun refreshResults() {
        // Re-trigger search to update favorite status
        val normalized = state.query.trim()
        if (normalized.isNotEmpty()) {
            queryFlow.value = queryFlow.value.copy(query = normalized, force = Uuid.random(), resetFocus = false)
        }
    }

    fun refreshLanguageIndicators() {
        val installed = repository.installedDictionaries()
        val currentLanguage = state.selectedLanguage
        val preferred = savedSearchLanguage?.takeIf { it in installed }
        val target = when {
            currentLanguage in installed && (preferred == null || preferred == currentLanguage) -> currentLanguage
            currentLanguage in installed && preferred != null -> preferred
            else -> preferred ?: installed.firstOrNull()
        }
        val languageChanged = currentLanguage != target
        // Do not call setSelectedLanguage — that would overwrite the saved preference.
        // On a language switch, drop the previous language's lists and re-gate so the
        // empty state shows a spinner (not stale wrong-language lists) until the caller's
        // refreshLists() repopulates. Safe because the only caller follows with refreshLists().
        state = if (languageChanged) {
            state.copy(
                availableLanguages = installed,
                selectedLanguage = target,
                curatedLists = emptyList(),
                isEmptyStateLoading = target != null
            )
        } else {
            state.copy(availableLanguages = installed, selectedLanguage = target)
        }
        queryFlow.value = queryFlow.value.copy(language = target)
        if (languageChanged) {
            viewModelScope.launch { loadSuggestionsForCurrentLanguage() }
        }
    }

    fun setSelectedLanguage(language: Language?) {
        val langChanged = state.selectedLanguage != language
        state = state.copy(selectedLanguage = language)
        savedSearchLanguage = language
        viewModelScope.launch {
            if (language != null) {
                settingsRepository.insert(Setting(Setting.Name.SEARCH_LANGUAGE, JsonPrimitive(language.code)))
            } else {
                settingsRepository.deleteById(Setting.Name.SEARCH_LANGUAGE)
            }
            Analytics.logEvent(
                AnalyticsEvent.SETTING_CHANGED,
                mapOf("setting" to "search_language", "value" to (language?.code ?: "any")),
            )
        }
        // Re-trigger search with new language filter
        val normalized = state.query.trim()
        if (normalized.isNotEmpty()) {
            queryFlow.value = queryFlow.value.copy(
                query = normalized,
                language = language,
                force = Uuid.random(),
                resetFocus = langChanged
            )
        }
        // Load suggestions and lists for new language
        if (langChanged) {
            // No per-language lists in "All languages" mode, so don't gate on loading there
            // (refreshLists/loadSuggestions early-return for a null language).
            state = state.copy(curatedLists = emptyList(), isEmptyStateLoading = language != null)
            viewModelScope.launch { loadSuggestionsForCurrentLanguage() }
            refreshLists()
        }
    }

    fun setLanguageDropdownExpanded(expanded: Boolean) {
        state = state.copy(isLanguageDropdownExpanded = expanded)
    }

    fun setShowLanguageIndicators(show: Boolean) {
        state = state.copy(showLanguageIndicators = show)
    }
}

