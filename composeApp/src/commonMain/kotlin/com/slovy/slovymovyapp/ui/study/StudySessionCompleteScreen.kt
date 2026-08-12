package com.slovy.slovymovyapp.ui.study

import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.slovy.slovymovyapp.data.learning.stats.StatsPipelineStageId
import com.slovy.slovymovyapp.ui.ThemePreviewProvider
import com.slovy.slovymovyapp.ui.ThemedPreview
import com.slovy.slovymovyapp.ui.rememberReduceMotion
import com.slovy.slovymovyapp.ui.icons.ImageOtterSessionComplete
import com.slovy.slovymovyapp.ui.icons.OtterDaysStreakC
import com.slovy.slovymovyapp.ui.icons.OtterPipelineStreakC
import com.slovy.slovymovyapp.ui.icons.SlovyIcons
import com.slovy.slovymovyapp.ui.theme.AppSpacing
import com.slovy.slovymovyapp.ui.theme.serifFontFamily
import com.slovy.slovymovyapp.ui.theme.uiItalic
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import slovymovyapp.composeapp.generated.resources.*

@Composable
internal fun StudySessionCompleteContent(
    reward: StudySessionCompleteUiState,
    scrollState: ScrollState,
    onClose: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
    respectReduceMotion: Boolean = true,
) {
    val reduceMotion = rememberReduceMotion()
    val entrance = rememberRewardEntrance(
        animate = animate && !LocalInspectionMode.current && !(respectReduceMotion && reduceMotion),
    )
    CompositionLocalProvider(LocalRewardEntrance provides entrance) {
        StudySessionCompleteScaffold(
            reward = reward,
            scrollState = scrollState,
            onClose = onClose,
            snackbarHostState = snackbarHostState,
            modifier = modifier,
        )
    }
}

@Composable
private fun StudySessionCompleteScaffold(
    reward: StudySessionCompleteUiState,
    scrollState: ScrollState,
    onClose: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { StudySessionSnackbarHost(hostState = snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.md),
        ) {
            StudySessionTopBar(
                progress = null,
                onClose = onClose,
                isAutoplayEnabled = false,
                onOpenActions = null,
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.Center)
                        .verticalScroll(scrollState)
                        .padding(vertical = AppSpacing.md),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Image(
                        imageVector = with(SlovyIcons) { completeIllustration(reward.hero) },
                        contentDescription = null,
                        modifier = Modifier
                            .rewardEntrance(RewardElement.OTTER)
                            .size(width = 148.dp, height = 168.dp),
                    )
                    Spacer(Modifier.height(AppSpacing.lg))
                    Text(
                        text = reward.message,
                        style = MaterialTheme.typography.displaySmall,
                        fontFamily = MaterialTheme.serifFontFamily,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .rewardEntrance(RewardElement.HEADLINE)
                            .fillMaxWidth()
                            .padding(horizontal = AppSpacing.xl),
                    )
                    Spacer(Modifier.height(AppSpacing.sm))
                    CompletionSubtext(
                        reward = reward,
                        modifier = Modifier
                            .rewardEntrance(RewardElement.STATS)
                            .fillMaxWidth()
                            .padding(horizontal = AppSpacing.xl),
                    )
                    CompletionHero(
                        hero = reward.hero,
                        modifier = Modifier
                            .rewardEntrance(RewardElement.HERO_TILE)
                            .padding(top = AppSpacing.xxl)
                            .widthIn(max = 332.dp),
                    )
                }
            }
            // The button is fully transparent until its entrance starts, but a
            // graphicsLayer alpha keeps hit target and semantics alive. Gate interaction and
            // semantics on the entrance having begun so an early tap in the bottom-bar area can't
            // dismiss the screen and assistive tech can't focus an invisible action.
            val actionBarProgress = rememberElementProgress(RewardElement.ACTION_BAR)
            val actionBarVisible by remember(actionBarProgress) {
                derivedStateOf { actionBarProgress.value > 0f }
            }
            Button(
                onClick = onClose,
                enabled = actionBarVisible,
                modifier = Modifier
                    .rewardEntrance(RewardElement.ACTION_BAR, actionBarProgress)
                    .then(if (actionBarVisible) Modifier else Modifier.clearAndSetSemantics {})
                    .fillMaxWidth()
                    .height(56.dp),
                shape = MaterialTheme.shapes.extraLarge,
                contentPadding = PaddingValues(horizontal = AppSpacing.lg),
            ) {
                Text(
                    text = stringResource(Res.string.study_action_done_for_now),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

private fun SlovyIcons.completeIllustration(hero: StudySessionCompleteHero): ImageVector =
    when (hero) {
        is StudySessionCompleteHero.Milestone -> OtterDaysStreakC
        is StudySessionCompleteHero.WordsMilestone -> ImageOtterSessionComplete
        is StudySessionCompleteHero.PipelineShift -> OtterPipelineStreakC
        StudySessionCompleteHero.None -> ImageOtterSessionComplete
    }

@Composable
private fun CompletionSubtext(
    reward: StudySessionCompleteUiState,
    modifier: Modifier = Modifier,
) {
    val parts = buildList {
        add(
            pluralStringResource(
                Res.plurals.study_complete_micro_cards_reviewed,
                reward.cardsReviewed,
                reward.cardsReviewed,
            )
        )
        add(
            pluralStringResource(
                Res.plurals.study_complete_micro_minutes,
                reward.minutes,
                reward.minutes,
            )
        )
        if (reward.streakDays > 1 || (reward.streakDays > 0 && reward.hero == StudySessionCompleteHero.None)) {
            add(
                pluralStringResource(
                    Res.plurals.study_complete_micro_streak,
                    reward.streakDays,
                    reward.streakDays,
                )
            )
        }
    }
    Text(
        text = parts.joinToString(separator = " · "),
        style = MaterialTheme.typography.titleMedium,
        fontFamily = MaterialTheme.serifFontFamily,
        fontStyle = MaterialTheme.uiItalic,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier,
    )
}

@Composable
private fun CompletionHero(
    hero: StudySessionCompleteHero,
    modifier: Modifier = Modifier,
) {
    when (hero) {
        is StudySessionCompleteHero.Milestone -> MilestoneCompleteHero(
            streakDays = hero.streakDays,
            modifier = modifier.fillMaxWidth(),
        )

        is StudySessionCompleteHero.WordsMilestone -> WordsMilestoneCompleteHero(
            learnedTotal = hero.learnedTotal,
            modifier = modifier.fillMaxWidth(),
        )

        is StudySessionCompleteHero.PipelineShift -> PipelineShiftCompleteHero(
            stages = hero.stages,
            modifier = modifier.fillMaxWidth(),
        )

        StudySessionCompleteHero.None -> Unit
    }
}

@Preview
@Composable
private fun StudySessionCompleteMilestonePreview(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean,
) {
    ThemedPreview(darkTheme = isDark) {
        StudySessionCompleteContent(
            reward = StudySessionCompleteUiState(
                cardsReviewed = 12,
                minutes = 6,
                streakDays = 7,
                message = "Goed gedaan!",
                hero = StudySessionCompleteHero.Milestone(streakDays = 7),
            ),
            scrollState = ScrollState(0),
            onClose = {},
            snackbarHostState = SnackbarHostState(),
            animate = false,
        )
    }
}

@Preview
@Composable
private fun StudySessionCompleteWordsMilestonePreview(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean,
) {
    ThemedPreview(darkTheme = isDark) {
        StudySessionCompleteContent(
            reward = StudySessionCompleteUiState(
                cardsReviewed = 15,
                minutes = 7,
                streakDays = 5,
                message = "Goed gedaan!",
                hero = StudySessionCompleteHero.WordsMilestone(learnedTotal = 11),
            ),
            scrollState = ScrollState(0),
            onClose = {},
            snackbarHostState = SnackbarHostState(),
            animate = false,
        )
    }
}

@Preview
@Composable
private fun StudySessionCompletePipelineShiftPreview(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean,
) {
    ThemedPreview(darkTheme = isDark) {
        StudySessionCompleteContent(
            reward = StudySessionCompleteUiState(
                cardsReviewed = 18,
                minutes = 8,
                streakDays = 4,
                message = "Goed gedaan!",
                hero = StudySessionCompleteHero.PipelineShift(
                    stages = listOf(
                        PipelineShiftStageUiState(StatsPipelineStageId.NEW, before = 18, after = 12),
                        PipelineShiftStageUiState(StatsPipelineStageId.FRESH, before = 22, after = 28),
                        PipelineShiftStageUiState(StatsPipelineStageId.MIDDLE, before = 16, after = 20),
                        PipelineShiftStageUiState(StatsPipelineStageId.STRONG, before = 11, after = 9),
                        PipelineShiftStageUiState(StatsPipelineStageId.LEARNED, before = 5, after = 6),
                    )
                ),
            ),
            scrollState = ScrollState(0),
            onClose = {},
            snackbarHostState = SnackbarHostState(),
            animate = false,
        )
    }
}
