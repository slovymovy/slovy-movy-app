package com.slovy.slovymovyapp.ui.study

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slovy.slovymovyapp.analytics.Analytics
import com.slovy.slovymovyapp.analytics.AnalyticsEvent
import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.learning.StudyFormat
import com.slovy.slovymovyapp.data.learning.StudyFormatsEditor
import com.slovy.slovymovyapp.data.learning.StudyFormatsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The formats page reached from Settings. The study session hosts the same page itself, over the
 * card, through [StudySessionViewModel]; both edit the same per-language setting.
 */
class StudyFormatsViewModel(
    private val language: Language,
    studyFormatsRepository: StudyFormatsRepository,
    /** Outlives this screen, so a toggle made just before leaving the page still lands. */
    appScope: CoroutineScope,
    /** Called after a change is stored, so review counts and intake caches can follow. */
    onChanged: (Language) -> Unit,
) : ViewModel() {

    /** Null until the stored selection has been read; the page shows nothing before that. */
    var state by mutableStateOf<StudyFormatsUiState?>(null)
        private set

    /** Set when the language turns out to offer no choice; the screen leaves. */
    var closed by mutableStateOf(false)
        private set

    val scrollState = ScrollState(0)

    private val editor = StudyFormatsEditor(
        repository = studyFormatsRepository,
        language = language,
        scope = appScope,
        analyticsSource = "settings",
        onSelectionChanged = { selection -> state = studyFormatsUiState(language, selection) },
        onStored = { onChanged(language) },
    )

    init {
        viewModelScope.launch {
            // One resolution of the targets answers both questions.
            val targets = studyFormatsRepository.translationTargetsOf(language)
            if (!studyFormatsRepository.hasChoice(language, targets)) {
                closed = true
                return@launch
            }
            state = studyFormatsUiState(language, editor.load(targets))
            Analytics.logEvent(
                AnalyticsEvent.STUDY_FORMATS_OPEN,
                mapOf("lang" to language.code, "source" to "settings"),
            )
        }
    }

    fun toggle(format: StudyFormat) {
        editor.toggle(format)
    }
}
