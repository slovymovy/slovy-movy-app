package com.slovy.slovymovyapp.ui.favorites

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.slovy.slovymovyapp.data.remote.*
import com.slovy.slovymovyapp.i18n.resolve
import com.slovy.slovymovyapp.speech.LemmaAudioControl
import com.slovy.slovymovyapp.ui.word.SenseCard
import com.slovy.slovymovyapp.ui.word.SenseCardData
import com.slovy.slovymovyapp.ui.word.SenseUiState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import slovymovyapp.composeapp.generated.resources.*
import com.slovy.slovymovyapp.ui.buildDeveloperDiagnosticInfo

@Composable
internal fun FavoriteSenseCard(
    item: FavoriteSenseItem,
    onToggle: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onViewFullDetails: () -> Unit,
    onWordClick: (String) -> Unit = {},
    favoriteLemmas: Set<String> = emptySet(),
    lemmaAudio: LemmaAudioControl? = null,
) {
    val senseState = SenseUiState(
        senseId = item.senseId,
        expanded = item.expanded,
        examplesExpanded = false,
        languageExpanded = emptyMap(),
        favorite = true,
        showFavoriteToggle = item.expanded,
        pos = item.pos
    )
    SenseCard(
        data = SenseCardData(
            senseId = item.senseId,
            lemma = item.lemma,
            showLemma = true,
            sense = item.sense,
            pos = item.pos,
            loading = item.loading,
            error = item.error,
            diagnosticInfoOnError = buildDeveloperDiagnosticInfo(item.senseId, item.createdAt)
        ),
        state = senseState,
        onToggle = onToggle,
        onFavoriteToggle = onFavoriteToggle,
        onViewFullDetails = onViewFullDetails,
        relatedWords = item.relatedWords,
        onWordClick = onWordClick,
        favoriteLemmas = favoriteLemmas,
        lemmaAudio = lemmaAudio,
    )
}

