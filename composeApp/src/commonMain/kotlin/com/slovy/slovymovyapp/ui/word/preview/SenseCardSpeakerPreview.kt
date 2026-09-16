package com.slovy.slovymovyapp.ui.word.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.remote.LanguageCardExample
import com.slovy.slovymovyapp.data.remote.LanguageCardResponseSense
import com.slovy.slovymovyapp.data.remote.LearnerLevel
import com.slovy.slovymovyapp.data.remote.SenseFrequency
import com.slovy.slovymovyapp.speech.AudioControl
import com.slovy.slovymovyapp.speech.RowAudioPhase
import com.slovy.slovymovyapp.ui.ThemePreviewProvider
import com.slovy.slovymovyapp.ui.ThemedPreview
import com.slovy.slovymovyapp.ui.theme.AppSpacing
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
            modifier = Modifier.fillMaxWidth().padding(AppSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        ) {
            // Idle / preparing / playing speaker states on a normal-length lemma.
            SenseCard(
                data = speakerRowData("uitgang"),
                state = speakerRowState("uitgang"),
                onToggle = {},
                lemmaAudio = AudioControl(RowAudioPhase.IDLE) {},
            )
            SenseCard(
                data = speakerRowData("ontlading"),
                state = speakerRowState("ontlading"),
                onToggle = {},
                lemmaAudio = AudioControl(RowAudioPhase.PREPARING) {},
            )
            SenseCard(
                data = speakerRowData("duwen"),
                state = speakerRowState("duwen"),
                onToggle = {},
                lemmaAudio = AudioControl(RowAudioPhase.PLAYING) {},
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
            modifier = Modifier.fillMaxWidth().padding(AppSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        ) {
            // Spec §3: the lemma is never truncated; the speaker follows the last fragment.
            SenseCard(
                data = speakerRowData("ontoegankelijkheid"),
                state = speakerRowState("ontoegankelijkheid"),
                onToggle = {},
                lemmaAudio = AudioControl(RowAudioPhase.IDLE) {},
            )
            SenseCard(
                data = speakerRowData("meervoudigepersoonlijkheidsstoornis"),
                state = speakerRowState("meervoudigepersoonlijkheidsstoornis"),
                onToggle = {},
                lemmaAudio = AudioControl(RowAudioPhase.IDLE) {},
            )
        }
    }
}

private const val EXPANDED_SENSE_ID = "sense-gezellig"

private fun exampleSense() = LanguageCardResponseSense(
    senseId = EXPANDED_SENSE_ID,
    senseDefinition = "pleasant and convivial to be around",
    learnerLevel = LearnerLevel.A2,
    frequency = SenseFrequency.HIGH,
    semanticGroupId = "sg-1",
    examples = listOf(
        LanguageCardExample(
            text = "Het was zo <w>gezellig</w> bij jullie thuis.",
            targetLangTranslations = mapOf(Language.ENGLISH to "It was so cosy at your place."),
        ),
        LanguageCardExample(
            // Long enough to wrap, so the speaker is seen following the last fragment rather than
            // detaching to a line of its own.
            text = "Na afloop van de vergadering bleven we nog even napraten, en dat was echt heel " +
                "<w>gezellig</w> met zijn allen.",
            targetLangTranslations = mapOf(
                Language.ENGLISH to "After the meeting we stayed to chat, and it was really cosy all together.",
            ),
        ),
    ),
)

private fun expandedSenseData() = SenseCardData(
    senseId = EXPANDED_SENSE_ID,
    lemma = "gezellig",
    showLemma = true,
    sense = exampleSense(),
)

private fun expandedSenseState() = SenseUiState(
    senseId = EXPANDED_SENSE_ID,
    expanded = true,
    examplesExpanded = true,
    favorite = false,
    showFavoriteToggle = true,
)

/**
 * The example speakers in every phase at once: idle on the first example, playing on the wrapping
 * one. Translations stay silent by design, so only the source sentences carry a glyph.
 */
@Preview
@Composable
private fun SenseCardExampleSpeakersPreview(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(AppSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        ) {
            SenseCard(
                data = expandedSenseData(),
                state = expandedSenseState(),
                onToggle = {},
                lemmaAudio = AudioControl(RowAudioPhase.IDLE) {},
                exampleAudio = { index ->
                    AudioControl(if (index == 1) RowAudioPhase.PLAYING else RowAudioPhase.IDLE) {}
                },
            )
        }
    }
}

/** A preparing example speaker, and a card whose examples are not speakable at all. */
@Preview
@Composable
private fun SenseCardExampleSpeakerEdgeStatesPreview(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(AppSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        ) {
            SenseCard(
                data = expandedSenseData(),
                state = expandedSenseState(),
                onToggle = {},
                lemmaAudio = AudioControl(RowAudioPhase.IDLE) {},
                exampleAudio = { index ->
                    AudioControl(if (index == 0) RowAudioPhase.PREPARING else RowAudioPhase.IDLE) {}
                },
            )
            // No playable voice for the language: examples render exactly as they did before this
            // feature, with no dead control left behind.
            SenseCard(
                data = expandedSenseData(),
                state = expandedSenseState(),
                onToggle = {},
            )
        }
    }
}
