package com.slovy.slovymovyapp.ui.study

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.slovy.slovymovyapp.ui.ThemePreviewProvider
import com.slovy.slovymovyapp.ui.ThemedPreview
import org.jetbrains.compose.resources.stringResource
import slovymovyapp.composeapp.generated.resources.*

@Composable
internal fun MilestoneCompleteHero(
    streakDays: Int,
    modifier: Modifier = Modifier,
) {
    MilestoneHeroCard(
        medallionTarget = streakDays,
        title = milestoneTitle(streakDays),
        subtitle = milestoneSubtitle(streakDays),
        colors = MilestoneHeroColors(
            container = MaterialTheme.colorScheme.primaryContainer,
            onContainer = MaterialTheme.colorScheme.onPrimaryContainer,
            accent = MaterialTheme.colorScheme.primary,
            onAccent = MaterialTheme.colorScheme.onPrimary,
            confettiSecondary = MaterialTheme.colorScheme.tertiary,
        ),
        modifier = modifier,
    )
}

@Composable
private fun milestoneTitle(days: Int): String = when (days) {
    7 -> stringResource(Res.string.study_complete_milestone_title_7)
    14 -> stringResource(Res.string.study_complete_milestone_title_14)
    30 -> stringResource(Res.string.study_complete_milestone_title_30)
    50 -> stringResource(Res.string.study_complete_milestone_title_50)
    100 -> stringResource(Res.string.study_complete_milestone_title_100)
    else -> stringResource(Res.string.study_complete_milestone_title_generic, days)
}

@Composable
private fun milestoneSubtitle(days: Int): String = when (days) {
    7 -> stringResource(Res.string.study_complete_milestone_subtitle_7)
    14 -> stringResource(Res.string.study_complete_milestone_subtitle_14)
    30 -> stringResource(Res.string.study_complete_milestone_subtitle_30)
    50 -> stringResource(Res.string.study_complete_milestone_subtitle_50)
    100 -> stringResource(Res.string.study_complete_milestone_subtitle_100)
    else -> stringResource(Res.string.study_complete_milestone_subtitle_generic, days)
}

// Run this preview in interactive mode and tap the hero to (re)play the entrance choreography.
@Preview
@Composable
private fun MilestoneCompleteHeroEntrancePreview(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean,
) {
    ThemedPreview(darkTheme = isDark) {
        RewardEntrancePreviewPlayer {
            MilestoneCompleteHero(streakDays = 7)
        }
    }
}
