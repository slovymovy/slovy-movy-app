package com.slovy.slovymovyapp.ui.favorites

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slovy.slovymovyapp.data.remote.*
import com.slovy.slovymovyapp.i18n.resolve
import com.slovy.slovymovyapp.ui.ChevronRightVector
import com.slovy.slovymovyapp.ui.theme.AppSpacing
import com.slovy.slovymovyapp.ui.theme.serifFontFamily
import com.slovy.slovymovyapp.ui.theme.uiItalic
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import slovymovyapp.composeapp.generated.resources.*

private val StudyBarShape = RoundedCornerShape(20.dp)
private val StudyBarVerticalPadding = 18.dp

@Composable
private fun StudyBarHeading(
    text: String,
    color: Color,
) {
    Text(
        text = text,
        fontSize = 10.5.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.4.sp,
        lineHeight = 14.sp,
        color = color,
    )
}

@Composable
private fun StudyBarChevron(tint: Color) {
    Icon(
        imageVector = ChevronRightVector,
        contentDescription = null,
        modifier = Modifier.size(18.dp),
        tint = tint,
    )
}

/**
 * The bar's headline value with its caption on the same baseline. A caption that does not fit beside the
 * value (long translations, large font scales) moves below it as a whole instead of being squeezed.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StudyBarValueLine(
    value: String,
    valueColor: Color,
    valueLetterSpacing: TextUnit,
    caption: String,
    captionColor: Color,
    valueModifier: Modifier = Modifier,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
        Text(
            text = value,
            fontFamily = MaterialTheme.serifFontFamily,
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = valueLetterSpacing,
            lineHeight = 24.sp,
            color = valueColor,
            modifier = valueModifier.alignByBaseline(),
        )
        Text(
            text = caption,
            fontFamily = MaterialTheme.serifFontFamily,
            fontSize = 13.sp,
            fontStyle = MaterialTheme.uiItalic,
            lineHeight = 16.sp,
            color = captionColor,
            modifier = Modifier.alignByBaseline(),
        )
    }
}

@Composable
internal fun StudyDoneCard(
    studyDone: FavoritesStudyDoneUiState,
    onContinueStudyingNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val regionLabel = stringResource(Res.string.favorites_study_done_region)
    val continueLabel = studyDone.action?.let { action ->
        stringResource(
            when (action) {
                FavoritesStudyDoneAction.REVIEW_MORE -> Res.string.favorites_study_done_review_more
                FavoritesStudyDoneAction.STUDY_NEW -> Res.string.favorites_study_done_study_new
            },
        )
    }
    val title = stringResource(Res.string.favorites_study_done_title)
    val nextReviewAccessibilityLabel = stringResource(
        Res.string.favorites_study_done_next_review_a11y,
        studyDone.nextReviewAccessibilityValue.resolve()
    )
    val isPreview = LocalInspectionMode.current
    var visible by remember { mutableStateOf(isPreview) }
    LaunchedEffect(Unit) {
        visible = true
    }
    val cardProgress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 240),
        label = "studyDoneCard"
    )
    val slidePx = with(LocalDensity.current) { 6.dp.toPx() }

    Surface(
        modifier = modifier
            .graphicsLayer {
                alpha = cardProgress
                translationY = slidePx * (1f - cardProgress)
            }
            .semantics { contentDescription = regionLabel },
        shape = StudyBarShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.09f)),
    ) {
        // With an extra session on offer the whole bar starts it, like the due bar.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (continueLabel != null) {
                        // Merged, the value's own description would be all a screen reader announces.
                        val barAccessibilityLabel = stringResource(
                            Res.string.favorites_study_done_bar_a11y,
                            title,
                            nextReviewAccessibilityLabel,
                            stringResource(Res.string.favorites_study_done_more),
                        )
                        Modifier
                            .clickable(
                                onClickLabel = continueLabel,
                                role = Role.Button,
                                onClick = onContinueStudyingNow,
                            )
                            .clearAndSetSemantics {
                                contentDescription = barAccessibilityLabel
                            }
                    } else {
                        Modifier
                    }
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(
                        start = AppSpacing.lgPlus,
                        top = StudyBarVerticalPadding,
                        end = if (continueLabel == null) AppSpacing.lg else 0.dp,
                        bottom = StudyBarVerticalPadding,
                    ),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
            ) {
                StudyBarHeading(
                    text = title,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                StudyBarValueLine(
                    value = studyDone.nextReviewLabel.resolve(),
                    valueColor = MaterialTheme.colorScheme.primary,
                    valueLetterSpacing = 0.sp,
                    caption = stringResource(Res.string.favorites_study_done_until_next_review),
                    captionColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    valueModifier = Modifier.semantics { contentDescription = nextReviewAccessibilityLabel },
                )
            }
            if (continueLabel != null) {
                Row(
                    modifier = Modifier.padding(horizontal = AppSpacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.xxs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(Res.string.favorites_study_done_more),
                        fontFamily = MaterialTheme.serifFontFamily,
                        fontSize = 13.5.sp,
                        fontStyle = MaterialTheme.uiItalic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                    StudyBarChevron(tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                }
            }
        }
    }
}

@Composable
internal fun StudyDueCard(
    study: FavoritesStudyUiState,
    onStartStudy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val actionLabel = stringResource(Res.string.favorites_study_due_action)
    Surface(
        onClick = onStartStudy,
        modifier = modifier.semantics {
            onClick(label = actionLabel, action = null)
        },
        shape = StudyBarShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = AppSpacing.lgPlus,
                    top = StudyBarVerticalPadding,
                    end = AppSpacing.lg,
                    bottom = StudyBarVerticalPadding,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
            ) {
                StudyBarHeading(
                    text = stringResource(Res.string.favorites_study_due_title),
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                )
                StudyBarValueLine(
                    value = pluralStringResource(
                        Res.plurals.favorites_study_due_count,
                        study.dueCount,
                        study.dueCount
                    ),
                    valueColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    valueLetterSpacing = (-0.3).sp,
                    caption = stringResource(Res.string.favorites_study_due_subtitle, study.estimatedMinutes),
                    captionColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                )
            }
            StudyBarChevron(tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
        }
    }
}
