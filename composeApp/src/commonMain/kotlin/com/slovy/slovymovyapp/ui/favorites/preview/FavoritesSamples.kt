package com.slovy.slovymovyapp.ui.favorites.preview

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.tooling.preview.Preview
import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.remote.*
import com.slovy.slovymovyapp.i18n.UiText
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import slovymovyapp.composeapp.generated.resources.*
import com.slovy.slovymovyapp.ui.favorites.FavoriteSenseItem

// Preview helpers
internal fun createMockSense(
    id: String,
    definition: String,
    level: LearnerLevel = LearnerLevel.B1,
    frequency: SenseFrequency = SenseFrequency.MIDDLE,
    examples: List<LanguageCardExample> = emptyList(),
    synonyms: List<String> = emptyList(),
    antonyms: List<String> = emptyList(),
): LanguageCardResponseSense {
    return LanguageCardResponseSense(
        senseId = id,
        senseDefinition = definition,
        learnerLevel = level,
        frequency = frequency,
        semanticGroupId = "group1",
        nameType = null,
        examples = examples,
        synonyms = synonyms,
        antonyms = antonyms,
        commonPhrases = emptyList(),
        traits = emptyList(),
        targetLangDefinitions = mapOf(Language.ENGLISH to definition),
    )
}

internal fun createSenseItem(
    senseId: String,
    lemma: String,
    targetLang: Language = Language.ENGLISH,
    createdAt: Long = 1704067200L,
    sense: LanguageCardResponseSense? = null,
    pos: PartOfSpeech? = null,
    expanded: Boolean = false,
    loading: Boolean = false,
    error: UiText? = null
) = FavoriteSenseItem(
    senseId = senseId,
    targetLang = targetLang,
    lemma = lemma,
    createdAt = createdAt,
    sense = sense,
    pos = pos,
    expanded = expanded,
    loading = loading,
    error = error
)

