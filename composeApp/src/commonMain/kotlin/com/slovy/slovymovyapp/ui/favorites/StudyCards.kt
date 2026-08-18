package com.slovy.slovymovyapp.ui.favorites

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slovy.slovymovyapp.data.remote.*
import com.slovy.slovymovyapp.i18n.resolve
import com.slovy.slovymovyapp.ui.theme.AppSpacing
import com.slovy.slovymovyapp.ui.theme.serifFontFamily
import com.slovy.slovymovyapp.ui.theme.uiItalic
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import slovymovyapp.composeapp.generated.resources.*

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
    val checkProgress by animateFloatAsState(
        targetValue = if (visible) 1f else 0.6f,
        animationSpec = tween(durationMillis = 280, delayMillis = 40),
        label = "studyDoneCheck"
    )
    val slidePx = with(LocalDensity.current) { 6.dp.toPx() }

    Surface(
        modifier = modifier
            .graphicsLayer {
                alpha = cardProgress
                translationY = slidePx * (1f - cardProgress)
            }
            .semantics { contentDescription = regionLabel },
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.09f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
                ) {
                    Text(
                        text = stringResource(Res.string.favorites_study_done_title),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.4.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Text(
                            text = studyDone.nextReviewLabel.resolve(),
                            fontFamily = MaterialTheme.serifFontFamily,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.sp,
                            lineHeight = 27.3.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .alignByBaseline()
                                .semantics { contentDescription = nextReviewAccessibilityLabel },
                        )
                        Text(
                            text = stringResource(Res.string.favorites_study_done_until_next_review),
                            fontFamily = MaterialTheme.serifFontFamily,
                            fontSize = 13.sp,
                            fontStyle = MaterialTheme.uiItalic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.alignByBaseline(),
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .graphicsLayer {
                            scaleX = checkProgress
                            scaleY = checkProgress
                        }
                        .background(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (continueLabel != null) {
                HorizontalDivider(
                    modifier = Modifier.padding(top = 13.dp, bottom = 11.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            onClickLabel = continueLabel,
                            role = Role.Button,
                            onClick = onContinueStudyingNow,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = continueLabel,
                        fontFamily = MaterialTheme.serifFontFamily,
                        fontSize = 13.5.sp,
                        fontStyle = MaterialTheme.uiItalic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
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
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(Res.string.favorites_study_due_title),
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.4.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = pluralStringResource(
                            Res.plurals.favorites_study_due_count,
                            study.dueCount,
                            study.dueCount
                        ),
                        fontFamily = MaterialTheme.serifFontFamily,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = (-0.3).sp,
                        lineHeight = 27.3.sp,
                        modifier = Modifier.alignByBaseline(),
                    )
                    Text(
                        text = stringResource(Res.string.favorites_study_due_subtitle, study.estimatedMinutes),
                        fontFamily = MaterialTheme.serifFontFamily,
                        fontSize = 13.sp,
                        fontStyle = MaterialTheme.uiItalic,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                        modifier = Modifier.alignByBaseline(),
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
            )
        }
    }
}

