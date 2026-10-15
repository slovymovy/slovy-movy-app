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
import com.slovy.slovymovyapp.data.learning.StudyFormatSelection
import com.slovy.slovymovyapp.data.settings.SettingsRepository
import com.slovy.slovymovyapp.logging.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * The formats page reached from Settings. The study session hosts the same page itself, over the
 * card, through [StudySessionViewModel]; both write the same per-language setting.
 */
class StudyFormatsViewModel(
    private val language: Language,
    private val settingsRepository: SettingsRepository,
    /** Called after a change is stored, so review counts and intake caches can follow. */
    private val onChanged: (Language) -> Unit,
) : ViewModel() {

    /** Null until the stored selection has been read; the page shows nothing before that. */
    var state by mutableStateOf<StudyFormatsUiState?>(null)
        private set

    val scrollState = ScrollState(0)

    private var selection: StudyFormatSelection? = null

    init {
        viewModelScope.launch {
            val loaded = settingsRepository.getStudyFormats(language)
            selection = loaded
            state = studyFormatsUiState(language, loaded)
            Analytics.logEvent(
                AnalyticsEvent.STUDY_FORMATS_OPEN,
                mapOf("lang" to language.code, "source" to "settings"),
            )
        }
    }

    fun toggle(format: StudyFormat) {
        val current = selection ?: return
        val next = current.with(format, enabled = format !in current.enabled) ?: return
        selection = next
        state = studyFormatsUiState(language, next)
        viewModelScope.launch {
            try {
                settingsRepository.setStudyFormats(language, next)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.warn(TAG, "Unable to store study formats lang=${language.code}", e)
                selection = current
                state = studyFormatsUiState(language, current)
                return@launch
            }
            Analytics.logEvent(
                AnalyticsEvent.STUDY_FORMAT_TOGGLED,
                mapOf(
                    "lang" to language.code,
                    "format" to format.name.lowercase(),
                    "enabled" to (format in next.enabled),
                    "source" to "settings",
                ),
            )
            onChanged(language)
        }
    }

    private companion object {
        const val TAG = "StudyFormatsViewModel"
    }
}
