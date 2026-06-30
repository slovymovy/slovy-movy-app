package com.slovy.slovymovyapp.ui.word.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.slovy.slovymovyapp.ui.ThemePreviewProvider
import com.slovy.slovymovyapp.ui.ThemedPreview
import com.slovy.slovymovyapp.ui.word.SenseCard
import com.slovy.slovymovyapp.ui.word.SenseCardData
import com.slovy.slovymovyapp.ui.word.SenseUiState

private fun speakerRowData(lemma: String) = SenseCardData(
    senseId = lemma,
    lemma = lemma,
    showLemma = true,
    collapsedDefinition = "exit, way out",
)

private fun speakerRowState(senseId: String) = SenseUiState(
    senseId = senseId,
    expanded = false,
    examplesExpanded = false,
    favorite = false,
    showFavoriteToggle = true,
)

@Preview
@Composable
private fun SenseCardSpeakerStatesPreview(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Idle / preparing / playing speaker states on a normal-length lemma.
            SenseCard(
                data = speakerRowData("uitgang"),
                state = speakerRowState("uitgang"),
                onToggle = {},
                onToggleAudio = {},
                audioPlaying = false,
                audioPreparing = false,
            )
            SenseCard(
                data = speakerRowData("ontlading"),
                state = speakerRowState("ontlading"),
                onToggle = {},
                onToggleAudio = {},
                audioPlaying = false,
                audioPreparing = true,
            )
            SenseCard(
                data = speakerRowData("duwen"),
                state = speakerRowState("duwen"),
                onToggle = {},
                onToggleAudio = {},
                audioPlaying = true,
                audioPreparing = false,
            )
        }
    }
}

@Preview
@Composable
private fun SenseCardSpeakerLongWordsPreview(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Spec §3: the lemma is never truncated; the speaker follows the last fragment.
            SenseCard(
                data = speakerRowData("ontoegankelijkheid"),
                state = speakerRowState("ontoegankelijkheid"),
                onToggle = {},
                onToggleAudio = {},
                audioPlaying = false,
                audioPreparing = false,
            )
            SenseCard(
                data = speakerRowData("meervoudigepersoonlijkheidsstoornis"),
                state = speakerRowState("meervoudigepersoonlijkheidsstoornis"),
                onToggle = {},
                onToggleAudio = {},
                audioPlaying = false,
                audioPreparing = false,
            )
        }
    }
}
