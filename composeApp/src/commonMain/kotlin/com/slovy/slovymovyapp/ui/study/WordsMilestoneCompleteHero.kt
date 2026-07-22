package com.slovy.slovymovyapp.ui.study

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.sp
import com.slovy.slovymovyapp.ui.ThemePreviewProvider
import com.slovy.slovymovyapp.ui.ThemedPreview
import com.slovy.slovymovyapp.ui.theme.LocalIsDarkTheme
import org.jetbrains.compose.resources.stringResource
import slovymovyapp.composeapp.generated.resources.*

// The pipeline-Learned green family, intentionally hardcoded like the pipeline swatches
// (deeper than the 0xFF7CB078 bar swatch so white medallion digits stay legible).
// Copper/primary stays reserved for the streak milestone so the two never look alike.
private val WordsAccent = Color(0xFF4F8A4C)

@Composable
internal fun WordsMilestoneCompleteHero(
    learnedTotal: Int,
    modifier: Modifier = Modifier,
) {
    val dark = LocalIsDarkTheme.current
    MilestoneHeroCard(
        medallionTarget = learnedTotal,
        title = stringResource(Res.string.study_complete_words_milestone_title, learnedTotal),
        subtitle = stringResource(Res.string.study_complete_words_milestone_subtitle),
        colors = MilestoneHeroColors(
            container = if (dark) Color(0xFF26341F) else Color(0xFFE5F0E0),
            onContainer = if (dark) Color(0xFFCFE3C6) else Color(0xFF254A22),
            accent = WordsAccent,
            onAccent = Color.White,
            confettiSecondary = Color(0xFF7CB078),
        ),
        // Unlike the streak medallion (max 3 digits at a flat 34sp), the words count can
        // reach 4 digits — shrink so the digits keep fitting the 76dp disc.
        medallionFontSize = when {
            learnedTotal >= 1000 -> 25.sp
            learnedTotal >= 100 -> 30.sp
            else -> 34.sp
        },
        modifier = modifier,
    )
}

// Run this preview in interactive mode and tap the hero to (re)play the entrance choreography.
@Preview
@Composable
private fun WordsMilestoneCompleteHeroEntrancePreview(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean,
) {
    ThemedPreview(darkTheme = isDark) {
        RewardEntrancePreviewPlayer {
            WordsMilestoneCompleteHero(learnedTotal = 11)
        }
    }
}

@Preview
@Composable
private fun WordsMilestoneCompleteHeroThreeDigitPreview(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean,
) {
    ThemedPreview(darkTheme = isDark) {
        WordsMilestoneCompleteHero(learnedTotal = 154)
    }
}
