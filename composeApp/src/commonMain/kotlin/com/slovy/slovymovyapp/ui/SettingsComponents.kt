package com.slovy.slovymovyapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slovy.slovymovyapp.ui.components.SpinningProgressIndicator
import com.slovy.slovymovyapp.ui.theme.AppSpacing
import org.jetbrains.compose.resources.stringResource
import slovymovyapp.composeapp.generated.resources.*

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .padding(vertical = AppSpacing.sm)
            .semantics { heading() }
    )
}

@Composable
fun CancellableProgressIndicator(
    progress: Float,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp
) {
    val iconSize = size * 0.4f
    val strokeWidth = 2.5.dp
    val clampedProgress = progress.coerceIn(0f, 1f)
    val isIndeterminate = progress < 0f
    val progressPercent = if (isIndeterminate) null else (clampedProgress * 100).toInt()
    val stateDescriptionText = if (isIndeterminate) {
        stringResource(Res.string.common_status_downloading)
    } else {
        stringResource(Res.string.common_status_downloading_percent, progressPercent ?: 0)
    }
    val cancelDownloadClickLabel = stringResource(Res.string.common_action_cancel_download)

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .semantics(mergeDescendants = true) {
                progressBarRangeInfo = if (isIndeterminate) {
                    ProgressBarRangeInfo.Indeterminate
                } else {
                    ProgressBarRangeInfo(clampedProgress, 0f..1f)
                }
                stateDescription = stateDescriptionText
            }
            .clickable(
                onClick = onCancel,
                onClickLabel = cancelDownloadClickLabel,
                role = Role.Button
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isIndeterminate) {
            SpinningProgressIndicator(
                modifier = Modifier.size(size),
                strokeWidth = strokeWidth,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                color = MaterialTheme.colorScheme.primary
            )
        } else {
            CircularProgressIndicator(
                progress = { clampedProgress },
                modifier = Modifier.size(size),
                strokeWidth = strokeWidth,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Surface(
            modifier = Modifier.size(iconSize + 6.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = null,
                    modifier = Modifier.size(iconSize),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun DeleteConfirmationDialog(
    title: String,
    message: String,
    warning: String? = null,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.SemiBold
                )
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (warning != null) {
                    Text(
                        text = warning,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(stringResource(Res.string.common_delete))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Text(stringResource(Res.string.common_cancel))
            }
        }
    )
}

@Composable
fun LoadingIndicator(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        SpinningProgressIndicator()
    }
}

@Composable
fun CircularToggle(
    isSelected: Boolean,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    label: String? = null
) {
    val selectedStateDescription = stringResource(Res.string.common_state_selected)
    val notSelectedStateDescription = stringResource(Res.string.common_state_not_selected)

    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .then(
                if (onClick != null) {
                    Modifier
                        .semantics(mergeDescendants = true) {
                            stateDescription = if (isSelected) {
                                selectedStateDescription
                            } else {
                                notSelectedStateDescription
                            }
                            if (label != null) {
                                contentDescription = label
                            }
                        }
                        .clickable(onClick = onClick, role = Role.Checkbox)
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(
                    if (isSelected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
                .border(
                    width = 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
