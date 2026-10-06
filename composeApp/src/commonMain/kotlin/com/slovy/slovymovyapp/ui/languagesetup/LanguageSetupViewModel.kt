package com.slovy.slovymovyapp.ui.languagesetup

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slovy.slovymovyapp.analytics.Analytics
import com.slovy.slovymovyapp.analytics.AnalyticsEvent
import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.remote.DataDbManager
import com.slovy.slovymovyapp.data.remote.DictionaryClient
import com.slovy.slovymovyapp.i18n.UiText
import com.slovy.slovymovyapp.i18n.networkErrorUiText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import slovymovyapp.composeapp.generated.resources.*

data class LanguageSetupUiState(
    val isLoading: Boolean = true,
    val availableLanguages: List<Language> = emptyList(),
    val learningLanguage: Language? = null,
    val nativeLanguages: Set<Language> = emptySet(),
    val noTranslationSelected: Boolean = false,
    val errorMessage: UiText? = null,
    val languageRequestDialogVisible: Boolean = false,
    val languageRequestLearnLanguage: String = "",
    val languageRequestTranslateLanguage: String = "",
    val languageRequestSubmitting: Boolean = false,
    val languageRequestError: UiText? = null,
    val languageRequestDiscussionUrl: String? = null
)

class LanguageSetupViewModel(
    private val dataDbManager: DataDbManager,
    private val dictionaryClient: DictionaryClient,
    initialLearningLanguage: Language? = null,
    initialNativeLanguages: Set<Language> = emptySet()
) : ViewModel() {
    private var languageRequestJob: Job? = null

    var state by mutableStateOf(
        LanguageSetupUiState(
            learningLanguage = initialLearningLanguage,
            nativeLanguages = initialNativeLanguages - setOfNotNull(initialLearningLanguage),
            // Routing only reaches setup with a learning language after LANGUAGE was persisted,
            // so an empty set here means a saved "no translation" choice (LANGUAGE=[]), not a
            // fresh user who skipped the step.
            noTranslationSelected = initialLearningLanguage != null && initialNativeLanguages.isEmpty()
        )
    )
        private set

    val scrollState = ScrollState(0)

    init {
        loadAvailableLanguages()
    }

    private fun loadAvailableLanguages() {
        viewModelScope.launch {
            state = state.copy(isLoading = true, errorMessage = null)
            try {
                // A dictionary DB can be uploaded before the language is ready to be offered, so
                // discovery is intersected with what the enum declares studiable.
                val available = dataDbManager.fetchAvailableLanguages()
                    .filter { it.dictionarySizeBytes != null }
                    .map { it.language }
                    .filter { it.supportedForLearning }

                state = state.copy(
                    isLoading = false,
                    availableLanguages = available.sortedBy { it.selfName }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state = state.copy(
                    isLoading = false,
                    errorMessage = networkErrorUiText(e)
                )
            }
        }
    }

    fun selectLearningLanguage(language: Language) {
        if (state.learningLanguage == language) return
        state = state.copy(
            learningLanguage = language,
            nativeLanguages = state.nativeLanguages.filter { it != language }.toSet(),
            noTranslationSelected = false
        )
    }

    fun toggleNativeLanguage(language: Language) {
        val current = state.nativeLanguages
        state = state.copy(
            nativeLanguages = if (language in current) {
                current - language
            } else {
                current + language
            },
            noTranslationSelected = false
        )
    }

    fun setNoTranslationSelected(selected: Boolean) {
        state = if (selected) {
            state.copy(nativeLanguages = emptySet(), noTranslationSelected = true)
        } else {
            state.copy(noTranslationSelected = false)
        }
    }

    fun openLanguageRequestDialog() {
        Analytics.logEvent(AnalyticsEvent.LANGUAGE_REQUEST_OPEN)
        cancelLanguageRequestJob()
        state = state.copy(
            languageRequestDialogVisible = true,
            languageRequestLearnLanguage = "",
            languageRequestTranslateLanguage = "",
            languageRequestSubmitting = false,
            languageRequestError = null,
            languageRequestDiscussionUrl = null
        )
    }

    fun dismissLanguageRequestDialog() {
        cancelLanguageRequestJob()
        state = state.copy(
            languageRequestDialogVisible = false,
            languageRequestLearnLanguage = "",
            languageRequestTranslateLanguage = "",
            languageRequestSubmitting = false,
            languageRequestError = null,
            languageRequestDiscussionUrl = null
        )
    }

    fun updateLanguageRequestLearnLanguage(language: String) {
        state = state.copy(languageRequestLearnLanguage = language, languageRequestError = null)
    }

    fun updateLanguageRequestTranslateLanguage(language: String) {
        state = state.copy(languageRequestTranslateLanguage = language, languageRequestError = null)
    }

    fun submitLanguageRequest() {
        if (state.languageRequestSubmitting) return

        val learn = state.languageRequestLearnLanguage.trim()
        val translate = state.languageRequestTranslateLanguage.trim()
        if (learn.isEmpty() && translate.isEmpty()) {
            state = state.copy(
                languageRequestError = UiText.Resource(Res.string.language_setup_request_language_required)
            )
            return
        }

        val comment = buildLanguageRequestComment(learn = learn, translate = translate)
        state = state.copy(languageRequestSubmitting = true, languageRequestError = null)
        cancelLanguageRequestJob()
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                val response = dictionaryClient.sendGeneralFeedback(comment = comment)
                if (isCurrentLanguageRequestJob()) {
                    // Only which slots were filled, never what the user typed: the language names
                    // themselves are free text and already reach maintainers via the discussion.
                    Analytics.logEvent(
                        AnalyticsEvent.LANGUAGE_REQUEST_SENT,
                        mapOf("slots" to languageRequestSlots(learn = learn, translate = translate))
                    )
                    if (state.languageRequestDialogVisible) {
                        state = state.copy(
                            languageRequestSubmitting = false,
                            languageRequestError = null,
                            languageRequestDiscussionUrl = response.discussionUrl
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (isCurrentLanguageRequestJob() && state.languageRequestDialogVisible) {
                    state = state.copy(
                        languageRequestSubmitting = false,
                        languageRequestError = networkErrorUiText(e)
                    )
                }
            } finally {
                if (isCurrentLanguageRequestJob()) {
                    languageRequestJob = null
                }
            }
        }
        languageRequestJob = job
        job.start()
    }

    private fun cancelLanguageRequestJob() {
        languageRequestJob?.cancel()
        languageRequestJob = null
    }

    private suspend fun isCurrentLanguageRequestJob(): Boolean = languageRequestJob == currentCoroutineContext()[Job]

    fun retry() {
        loadAvailableLanguages()
    }

    /**
     * Builds the maintainer-facing feedback body. The labels stay in English on purpose: the
     * discussion is read by maintainers, not by the requester, so a fixed shape keeps requests
     * greppable no matter which UI locale produced them. Blank slots are omitted rather than
     * sent as empty lines.
     */
    private fun languageRequestSlots(learn: String, translate: String): String = when {
        learn.isNotEmpty() && translate.isNotEmpty() -> "both"
        learn.isNotEmpty() -> "learn"
        else -> "translate"
    }

    private fun buildLanguageRequestComment(learn: String, translate: String): String {
        return buildString {
            append(LANGUAGE_REQUEST_PREFIX)
            if (learn.isNotEmpty()) {
                append("\n")
                append(LEARN_LABEL)
                append(learn)
            }
            if (translate.isNotEmpty()) {
                append("\n")
                append(TRANSLATE_LABEL)
                append(translate)
            }
        }
    }

    private companion object {
        // Language requests share the generic /feedback endpoint; the prefix is what lets
        // maintainers tell them apart from general feedback in the GitHub Feedback category.
        const val LANGUAGE_REQUEST_PREFIX = "[Language request]"
        const val LEARN_LABEL = "Language to learn: "
        const val TRANSLATE_LABEL = "Language to translate into: "
    }
}

