package com.slovy.slovymovyapp.ui.stats

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slovy.slovymovyapp.analytics.Analytics
import com.slovy.slovymovyapp.analytics.AnalyticsEvent
import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.learning.stats.StatsPipelineStage
import com.slovy.slovymovyapp.data.learning.stats.StatsPracticeDay
import com.slovy.slovymovyapp.data.learning.stats.StatsService
import com.slovy.slovymovyapp.data.learning.stats.StatsYearMonth
import com.slovy.slovymovyapp.data.settings.Setting
import com.slovy.slovymovyapp.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.datetime.LocalDate
import slovymovyapp.composeapp.generated.resources.*
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

data class StatsUiState(
    val learningLanguages: List<Language>,
    val selectedLanguage: Language,
    val languageDropdownExpanded: Boolean = false,
    val today: LocalDate,
    val viewMonth: StatsYearMonth,
    val isLoading: Boolean,
    val streakDays: Int,
    val activeDaysTotal: Int,
    val practiceLog: Set<StatsPracticeDay>,
    val reviewsToday: Int,
    val reviewsWeek: Int,
    val minutesToday: Int,
    val minutesWeek: Int,
    val wordsTotal: Int,
    val pipeline: List<StatsPipelineStage>,
    val delayedDueLemmaCount: Int = 0,
    val delayedDueCardCount: Int = 0,
) {
    val sensesTotal: Int get() = pipeline.sumOf { it.count }
    val showLanguagePicker: Boolean get() = learningLanguages.size > 1
}

@OptIn(ExperimentalTime::class)
class StatsViewModel(
    initialLearningLanguages: List<Language>,
    private val statsService: StatsService,
    private val settingsRepository: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {
    val scrollState = ScrollState(0)

    var state by mutableStateOf(initialStatsState(initialLearningLanguages, clock))
        private set

    private var savedStatsLanguage: Language? = null
    private var savedStatsLanguageLoaded = false
    private var languageSelectedDuringRestore = false
    private var reloadRequestId = 0

    init {
        viewModelScope.launch {
            val savedCode = settingsRepository.getById(Setting.Name.STATS_LANGUAGE)
                ?.value?.jsonPrimitive?.contentOrNull
            if (!languageSelectedDuringRestore) {
                savedStatsLanguage = savedCode?.let { Language.fromCodeOrNull(it) }
            }
            savedStatsLanguageLoaded = true
            val selected = selectedLanguageFor(state.learningLanguages)
            if (selected != state.selectedLanguage) {
                state = state.copy(selectedLanguage = selected)
            }
            scheduleReload()
        }
    }

    fun updateLearningLanguages(languages: List<Language>) {
        val normalized = languages.ifEmpty { listOf(Language.ENGLISH) }.distinct().sortedBy { it.ordinal }
        val selected = selectedLanguageFor(normalized)
        if (normalized == state.learningLanguages && selected == state.selectedLanguage) return
        state = state.copy(learningLanguages = normalized, selectedLanguage = selected)
        scheduleReload()
    }

    fun setSelectedLanguage(language: Language) {
        if (state.selectedLanguage == language) return
        state = state.copy(selectedLanguage = language, languageDropdownExpanded = false)
        savedStatsLanguage = language
        if (!savedStatsLanguageLoaded) {
            languageSelectedDuringRestore = true
        }
        viewModelScope.launch {
            settingsRepository.insert(Setting(Setting.Name.STATS_LANGUAGE, JsonPrimitive(language.code)))
            Analytics.logEvent(
                AnalyticsEvent.SETTING_CHANGED,
                mapOf("setting" to "stats_language", "value" to language.code),
            )
        }
        scheduleReload()
    }

    fun setLanguageDropdownExpanded(expanded: Boolean) {
        state = state.copy(languageDropdownExpanded = expanded)
    }

    fun stepMonth(delta: Int) {
        val current = state.viewMonth
        val next = when (current.monthZeroBased + delta) {
            -1 -> StatsYearMonth(current.year - 1, 11)
            12 -> StatsYearMonth(current.year + 1, 0)
            else -> current.copy(monthZeroBased = current.monthZeroBased + delta)
        }
        val todayMonth = StatsYearMonth(state.today.year, state.today.month.ordinal)
        if (next.year > todayMonth.year || (next.year == todayMonth.year && next.monthZeroBased > todayMonth.monthZeroBased)) return
        state = state.copy(viewMonth = next)
    }

    fun refresh() {
        scheduleReload()
    }

    private fun scheduleReload() {
        if (!savedStatsLanguageLoaded) return
        val today = currentLocalDate(clock)
        val langCode = state.selectedLanguage.code
        val requestId = ++reloadRequestId
        state = state.copy(today = today, isLoading = true)
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.Default) {
                    statsService.statsScreenData(langCode, today)
                }
            }.onSuccess { data ->
                if (!isCurrentReload(requestId)) return@launch
                state = state.copy(
                    today = today,
                    isLoading = false,
                    streakDays = data.streakDays,
                    activeDaysTotal = data.activeDaysTotal,
                    practiceLog = data.practiceLog,
                    reviewsToday = data.reviewsToday,
                    reviewsWeek = data.reviewsWeek,
                    minutesToday = data.minutesToday,
                    minutesWeek = data.minutesWeek,
                    wordsTotal = data.wordsTotal,
                    pipeline = data.pipeline,
                    delayedDueLemmaCount = data.delayedDueLemmaCount,
                    delayedDueCardCount = data.delayedDueCardCount,
                )
            }.onFailure {
                if (isCurrentReload(requestId)) {
                    state = state.copy(isLoading = false)
                }
            }
        }
    }

    private fun isCurrentReload(requestId: Int): Boolean =
        reloadRequestId == requestId

    private fun selectedLanguageFor(languages: List<Language>): Language {
        val saved = savedStatsLanguage
        return when {
            saved != null && saved in languages -> saved
            state.selectedLanguage in languages -> state.selectedLanguage
            else -> languages.first()
        }
    }
}
